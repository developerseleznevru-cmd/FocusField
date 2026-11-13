package ru.seleznev.focusfield.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.service.sync.SyncWorker
import ru.seleznev.focusfield.ui.common.appContainer

/** Экран настроек: тема, блокировка, мониторинг, синхронизация, расписание. */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentById(R.id.settings_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsPrefsFragment())
                .commit()
        }
    }

    class SettingsPrefsFragment : PreferenceFragmentCompat() {

        private val viewModel: SettingsViewModel by viewModels(
            { requireParentFragment() }
        ) { appContainer.viewModelFactory }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            bind()
        }

        /**
         * Подписка на состояние — во onViewCreated: viewLifecycleOwner
         * доступен только после создания view фрагмента (в onCreatePreferences
         * он ещё не существует и обращение к нему приводит к крашу).
         */
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.state.collect { state ->
                        findPreference<Preference>(PREF_SCHEDULE_START)?.summary =
                            formatTime(state.scheduleStartMinutes)
                        findPreference<Preference>(PREF_SCHEDULE_END)?.summary =
                            formatTime(state.scheduleEndMinutes)
                    }
                }
            }
        }

        private fun bind() {
            val vm = viewModel

            findPreference<ListPreference>(PREF_THEME)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setTheme((newValue as String).toInt())
                true
            }
            findPreference<SwitchPreferenceCompat>(PREF_BLOCK_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setBlockEnabled(newValue as Boolean); true
            }
            findPreference<SwitchPreferenceCompat>(PREF_BLOCK_ONLY_SESSION)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setBlockOnlyInSession(newValue as Boolean); true
            }
            findPreference<SeekBarPreference>(PREF_THRESHOLD)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setBlockThreshold(newValue as Int); true
            }
            findPreference<SeekBarPreference>(PREF_GRACE)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setGraceMinutes(newValue as Int); true
            }
            findPreference<SwitchPreferenceCompat>(PREF_CAMERA)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setCameraMonitoring(newValue as Boolean); true
            }
            findPreference<SwitchPreferenceCompat>(PREF_NOTIFICATIONS)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setNotificationsEnabled(newValue as Boolean); true
            }
            findPreference<SwitchPreferenceCompat>(PREF_WEAR)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setWearEnabled(newValue as Boolean); true
            }
            findPreference<SwitchPreferenceCompat>(PREF_SYNC)?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                vm.setSyncEnabled(enabled)
                SyncWorker.ensureScheduled(requireContext(), enabled)
                true
            }
            findPreference<EditTextPreference>(PREF_API_URL)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setApiBaseUrl(newValue as String); true
            }
            findPreference<SwitchPreferenceCompat>(PREF_SCHEDULE_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
                vm.setScheduleEnabled(newValue as Boolean); true
            }
            findPreference<Preference>(PREF_SCHEDULE_START)?.setOnPreferenceClickListener {
                pickTime(PREF_SCHEDULE_START)
                true
            }
            findPreference<Preference>(PREF_SCHEDULE_END)?.setOnPreferenceClickListener {
                pickTime(PREF_SCHEDULE_END)
                true
            }

            findPreference<Preference>(PREF_OPEN_BLOCKLIST)?.setOnPreferenceClickListener {
                findNavController().navigate(R.id.action_settings_to_blockedItems)
                true
            }
            findPreference<Preference>(PREF_OPEN_ACCESSIBILITY)?.setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                true
            }
            findPreference<Preference>(PREF_OPEN_OVERLAY)?.setOnPreferenceClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${requireContext().packageName}")
                    )
                )
                true
            }

            findPreference<Preference>(PREF_LOGOUT)?.let { pref ->
                // Разрушительное действие — выделяем цветом ошибки Material 3.
                pref.title = SpannableString(pref.title?.toString().orEmpty()).apply {
                    setSpan(
                        ForegroundColorSpan(LOGOUT_COLOR),
                        0,
                        length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                pref.setOnPreferenceClickListener {
                    confirmLogout()
                    true
                }
            }
        }

        private fun confirmLogout() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Выйти из аккаунта?")
                .setMessage("Вы вернётесь на экран входа.")
                .setPositiveButton("Выйти") { _, _ -> viewModel.signOut() }
                .setNegativeButton("Отмена", null)
                .show()
        }

        private fun pickTime(target: String) {
            val state = viewModel.state.value
            val initial = if (target == PREF_SCHEDULE_START) {
                state.scheduleStartMinutes
            } else {
                state.scheduleEndMinutes
            }
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    val picked = hour * 60 + minute
                    if (target == PREF_SCHEDULE_START) {
                        viewModel.setScheduleTime(picked, state.scheduleEndMinutes)
                    } else {
                        viewModel.setScheduleTime(state.scheduleStartMinutes, picked)
                    }
                },
                initial / 60,
                initial % 60,
                true
            ).show()
        }

        private fun formatTime(minutes: Int): String =
            String.format("%02d:%02d", minutes / 60, minutes % 60)

        private companion object {
            const val PREF_THEME = "theme"
            const val PREF_BLOCK_ENABLED = "block_enabled"
            const val PREF_BLOCK_ONLY_SESSION = "block_only_in_session"
            const val PREF_THRESHOLD = "block_threshold"
            const val PREF_GRACE = "grace_minutes"
            const val PREF_CAMERA = "camera_monitoring"
            const val PREF_NOTIFICATIONS = "notifications_enabled"
            const val PREF_WEAR = "wear_enabled"
            const val PREF_SYNC = "sync_enabled"
            const val PREF_API_URL = "api_base_url"
            const val PREF_SCHEDULE_ENABLED = "schedule_enabled"
            const val PREF_SCHEDULE_START = "schedule_start"
            const val PREF_SCHEDULE_END = "schedule_end"
            const val PREF_OPEN_BLOCKLIST = "open_blocklist"
            const val PREF_OPEN_ACCESSIBILITY = "open_accessibility"
            const val PREF_OPEN_OVERLAY = "open_overlay"
            const val PREF_LOGOUT = "logout"

            /** Цвет ошибки Material 3 (…/colorError) для акцента выхода. */
            const val LOGOUT_COLOR = 0xFFB3261E.toInt()
        }
    }
}
