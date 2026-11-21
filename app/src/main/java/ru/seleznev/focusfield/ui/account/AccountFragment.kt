package ru.seleznev.focusfield.ui.account

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.databinding.DialogChangePasswordBinding
import ru.seleznev.focusfield.databinding.FragmentAccountBinding
import ru.seleznev.focusfield.ui.common.appContainer
import ru.seleznev.focusfield.ui.profile.ProfileFragment

/**
 * «Аккаунт» — вкладка нижнего меню: аватар, ФИО и email текущего
 * аккаунта, статус режима, компактная статистика, статусы разрешений
 * (переход в системные настройки), смена пароля, удаление аккаунта
 * и выход. Раздел вынесен из настроек.
 *
 * После выхода/удаления MainActivity наблюдает AuthState и уводит
 * на экран входа.
 */
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AccountViewModel by viewModels { appContainer.viewModelFactory }

    /** Диалог смены пароля: удерживается для закрытия после успеха. */
    private var passwordDialog: android.app.Dialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogout.setOnClickListener { confirmLogout() }

        // Правка ФИО/фото: тот же экран профиля в режиме редактирования
        // (без «Пропустить», после сохранения — возврат сюда).
        binding.btnEditProfile.setOnClickListener {
            val args = android.os.Bundle().apply {
                putBoolean(ProfileFragment.ARG_EDIT_MODE, true)
            }
            findNavController().navigate(R.id.action_account_to_profile, args)
        }

        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnDeleteAccount.setOnClickListener { confirmDeleteAccount() }

        // Разрешения: статус и переход в соответствующие системные настройки.
        binding.rowPermCamera.setOnClickListener { openAppSettings() }
        binding.rowPermAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.rowPermOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) bindStats(stats)
        }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                if (message == getString(R.string.account_password_changed)) {
                    passwordDialog?.dismiss()
                    passwordDialog = null
                }
                viewModel.consumeMessage()
            }
        }
    }

    /** Возврат из редактирования профиля / системных настроек — обновляем. */
    override fun onResume() {
        super.onResume()
        bindAccount()
        bindPermissions()
        viewModel.loadStats()
    }

    private fun bindAccount() {
        val avatar = viewModel.avatar
        if (avatar != null) {
            binding.accountAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.accountAvatar.setImageBitmap(avatar)
        } else {
            binding.accountAvatar.scaleType = ImageView.ScaleType.CENTER
            binding.accountAvatar.setImageResource(R.drawable.ic_person)
        }

        val fullName = viewModel.fullName
        binding.accountName.isVisible = fullName.isNotBlank()
        binding.accountName.text = fullName
        binding.accountEmail.text = viewModel.email

        binding.accountStatus.setText(
            if (viewModel.isFirebaseAvailable) R.string.account_status_firebase
            else R.string.account_status_local
        )
    }

    private fun bindStats(stats: AccountStats) {
        binding.accountStatSessions.text = stats.sessions.toString()
        binding.accountStatFocus.text = viewModel.formatMinutes(stats.totalMinutes)
        binding.accountStatStreak.text = viewModel.formatStreak(stats.streakDays)
        binding.accountStatBestDay.text = viewModel.formatBestDay(stats.bestDay)
    }

    /** Статусы трёх разрешений; обновляется при возврате из настроек. */
    private fun bindPermissions() {
        bindPermission(
            binding.accountPermCameraStatus,
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
        val accessibility = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        bindPermission(
            binding.accountPermAccessibilityStatus,
            accessibility.contains(requireContext().packageName)
        )
        bindPermission(binding.accountPermOverlayStatus, Settings.canDrawOverlays(requireContext()))
    }

    private fun bindPermission(view: android.widget.TextView, granted: Boolean) {
        view.setText(
            if (granted) R.string.account_perm_granted else R.string.account_perm_missing
        )
        view.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (granted) R.color.focus_high else R.color.focus_low
            )
        )
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${requireContext().packageName}")
            )
        )
    }

    /** Смена пароля: три поля, валидация до отправки в репозиторий. */
    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_password_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.account_change_password, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val current = dialogBinding.inputCurrentPassword.text?.toString().orEmpty()
                val new = dialogBinding.inputNewPassword.text?.toString().orEmpty()
                val repeat = dialogBinding.inputRepeatPassword.text?.toString().orEmpty()

                dialogBinding.inputCurrentPasswordLayout.error = null
                dialogBinding.inputNewPasswordLayout.error = null
                dialogBinding.inputRepeatPasswordLayout.error = null

                val invalid = getString(R.string.auth_invalid_field)
                when {
                    current.isEmpty() ->
                        dialogBinding.inputCurrentPasswordLayout.error = invalid

                    new.length < MIN_PASSWORD_LENGTH ->
                        dialogBinding.inputNewPasswordLayout.error = invalid

                    new != repeat ->
                        dialogBinding.inputRepeatPasswordLayout.error =
                            getString(R.string.account_password_mismatch)

                    else -> viewModel.changePassword(current, new)
                }
            }
        }
        passwordDialog = dialog
        dialog.show()
    }

    /** Выход — разрушительное действие: подтверждение в диалоге. */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_logout_title)
            .setMessage(R.string.account_logout_message)
            .setPositiveButton(R.string.account_logout_confirm) { _, _ -> viewModel.signOut() }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    /** Удаление аккаунта — двойное подтверждение (необратимо). */
    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_delete_title)
            .setMessage(R.string.account_delete_message)
            .setPositiveButton(R.string.account_delete_confirm) { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.account_delete_second_title)
                    .setMessage(R.string.account_delete_second_message)
                    .setPositiveButton(R.string.account_delete_confirm) { _, _ ->
                        viewModel.deleteAccount()
                    }
                    .setNegativeButton(R.string.common_cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        passwordDialog = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
