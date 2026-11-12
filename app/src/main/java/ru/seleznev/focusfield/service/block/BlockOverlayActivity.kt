package ru.seleznev.focusfield.service.block

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.seleznev.focusfield.FocusFieldApp
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.data.state.BlockingStateStore
import ru.seleznev.focusfield.databinding.ActivityBlockOverlayBinding

/**
 * Оверлей «Доступ заблокирован»: показывается поверх отвлекающего приложения/сайта.
 *
 * «Помеха» — пользователь может временно разрешить доступ (на N минут),
 * что учитывается в статистике как прерывание.
 */
class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding
    private lateinit var state: BlockingStateStore
    private lateinit var settings: ru.seleznev.focusfield.data.repo.SettingsRepository

    private val target: String by lazy {
        intent.getStringExtra(EXTRA_TARGET).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = (application as FocusFieldApp).container
        state = container.blockingStateStore
        settings = container.settingsRepository

        val isSite = intent.getBooleanExtra(EXTRA_IS_SITE, false)
        binding.blockTarget.text =
            if (isSite) getString(R.string.block_site_target, target)
            else getAppLabel(target)

        binding.btnAllowInterrupt.text =
            getString(R.string.block_allow_for, settings.state.value.graceMinutes)

        binding.btnBackToWork.setOnClickListener {
            // Возвращаем пользователя на домашний экран устройства
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
            finish()
        }

        binding.btnAllowInterrupt.setOnClickListener {
            state.grantOverride(target, settings.state.value.graceMinutes)
            finish()
        }
    }

    private fun getAppLabel(pkg: String): String = try {
        val appInfo = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        getString(R.string.block_unknown_app, pkg)
    }

    companion object {
        const val EXTRA_TARGET = "extra_target"
        const val EXTRA_IS_SITE = "extra_is_site"
    }
}
