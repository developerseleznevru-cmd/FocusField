package ru.seleznev.focusfield

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.model.AuthState
import ru.seleznev.focusfield.databinding.ActivityMainBinding
import ru.seleznev.focusfield.ui.profile.ProfileFragment

/**
 * Единая активность: навигация по фрагментам (нижнее меню),
 * гейт по аутентификации и применение темы из настроек.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val container by lazy { (application as FocusFieldApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHost.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.focusHomeFragment,
                R.id.tasksFragment,
                R.id.statsFragment,
                R.id.settingsFragment,
                R.id.accountFragment
            )
        )
        setSupportActionBar(binding.toolbar)
        binding.bottomNav.setupWithNavController(navController)
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showNav = destination.id in appBarConfiguration.topLevelDestinations
            binding.bottomNav.isVisible = showNav
        }

        observeAuthState(navController)
        requestNotificationPermissionIfNeeded()
    }

    private fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            FocusFieldApp.themeOf(container.settingsRepository.state.value.theme)
        )
    }

    /** Переключение между экраном входа и основным содержимым. */
    private fun observeAuthState(navController: androidx.navigation.NavController) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.authRepository.state.collect { state ->
                    val current = navController.currentDestination?.id
                    when (state) {
                        is AuthState.SignedIn -> {
                            if (current != null && current == R.id.authFragment) {
                                // После регистрации — экран редактирования профиля,
                                // при обычном входе — сразу в приложение.
                                val target = if (container.authRepository.consumeProfileSetup()) {
                                    R.id.action_auth_to_profile
                                } else {
                                    R.id.action_auth_to_main
                                }
                                navController.navigate(target)
                            }
                        }

                        else -> {
                            if (current != null && current != R.id.authFragment) {
                                navController.navigate(R.id.authFragment)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHost.navController
        // Экран профиля после регистрации — тупик «назад»: идём вперёд в
        // приложение. В режиме редактирования (из «Аккаунта») — обычный
        // возврат на предыдущий экран.
        if (navController.currentDestination?.id == R.id.profileFragment) {
            val inEditMode = navHost.childFragmentManager.fragments
                .lastOrNull { it is ProfileFragment }
                ?.arguments?.getBoolean(ProfileFragment.ARG_EDIT_MODE, false) == true
            if (!inEditMode) {
                navController.navigate(R.id.action_profile_to_main)
                return true
            }
        }
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
