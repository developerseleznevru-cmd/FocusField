package ru.seleznev.focusfield.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.databinding.FragmentProfileBinding
import ru.seleznev.focusfield.ui.auth.RegistrationValidator
import ru.seleznev.focusfield.ui.common.appContainer

/**
 * Профиль: email аккаунта отображается (не редактируется), ФИО и фото
 * профиля заполняются и сохраняются. Фото выбирается в системном Photo Picker.
 *
 * Два режима (аргумент [ARG_EDIT_MODE]):
 *  - после регистрации: «Пропустить»/«назад» ведут вперёд в приложение,
 *    назад на экран входа путь закрыт (см. nav_graph);
 *  - редактирование из вкладки «Аккаунт»: без «Пропустить», после сохранения
 *    и по «назад» — возврат на экран «Аккаунт» (popBackStack).
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: ProfileViewModel by viewModels { appContainer.viewModelFactory }

    /** true — режим редактирования из вкладки «Аккаунт» (см. KDoc). */
    private val isEditMode: Boolean
        get() = arguments?.getBoolean(ARG_EDIT_MODE, false) == true

    /** Системный Photo Picker: доступ к галерее без запроса разрешений. */
    private val pickAvatar = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.saveAvatar(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefillFields()
        applyMode()

        binding.avatarContainer.setOnClickListener {
            pickAvatar.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        viewModel.avatar.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                binding.profileAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.profileAvatar.setImageBitmap(bitmap)
            } else {
                binding.profileAvatar.scaleType = ImageView.ScaleType.CENTER
                binding.profileAvatar.setImageResource(R.drawable.ic_person)
            }
        }

        binding.btnSave.setOnClickListener {
            if (!validateFields()) return@setOnClickListener
            viewModel.save(collectFullName())
        }

        // «Пропустить»: вход в приложение без редактирования профиля —
        // тот же переход на стартовый экран «Фокус» (action_profile_to_main).
        binding.btnSkip.setOnClickListener { finish() }

        bindErrorClearing(binding.inputFirstName, binding.inputFirstNameLayout)
        bindErrorClearing(binding.inputLastName, binding.inputLastNameLayout)
        bindErrorClearing(binding.inputPatronymic, binding.inputPatronymicLayout)

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progress.isVisible = loading
            binding.btnSave.isEnabled = !loading
            binding.btnSkip.isEnabled = !loading
        }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                viewModel.consumeMessage()
            }
        }

        viewModel.saved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                viewModel.consumeSaved()
                finish()
            }
        }

        // Режим редактирования — обычный возврат; после регистрации «назад»
        // ведёт вперёд в приложение: экран входа закрыт (popUpTo inclusive).
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(!isEditMode) {
                override fun handleOnBackPressed() = finish()
            }
        )
    }

    /**
     * Режим редактирования из вкладки «Аккаунт»: без «Пропустить»,
     * кнопка «Сохранить», подзаголовок о правке данных.
     */
    private fun applyMode() {
        if (!isEditMode) return
        binding.btnSkip.isVisible = false
        binding.btnSave.setText(R.string.profile_save_only)
        binding.profileSubtitle.setText(R.string.profile_subtitle_edit)
    }

    /**
     * Завершение экрана: в режиме редактирования — возврат на «Аккаунт»,
     * после регистрации — переход в приложение.
     */
    private fun finish() {
        if (isEditMode) {
            findNavController().popBackStack()
        } else {
            findNavController().navigate(R.id.action_profile_to_main)
        }
    }

    /** Предзаполнение полей из сохранённого профиля и отображение email. */
    private fun prefillFields() {
        binding.profileEmail.text = viewModel.email
        val parts = ProfileForm.parse(viewModel.fullName)
        binding.inputLastName.setText(parts.lastName)
        binding.inputFirstName.setText(parts.firstName)
        binding.inputPatronymic.setText(parts.patronymic)
    }

    /**
     * Валидация по правилам регистрации (ТЗ): имя/фамилия — русские буквы,
     * первая заглавная, ≥4 букв; отчество необязательное. Ошибка поля —
     * красная граница и текст «Поле заполнено неверно».
     */
    private fun validateFields(): Boolean {
        val invalid = getString(R.string.auth_invalid_field)
        var valid = true

        fun check(layout: TextInputLayout, field: TextInputEditText, isValid: (String) -> Boolean) {
            val value = field.text?.toString().orEmpty().trim()
            if (isValid(value)) {
                layout.error = null
            } else {
                layout.error = invalid
                valid = false
            }
        }

        check(binding.inputFirstNameLayout, binding.inputFirstName, RegistrationValidator::isNameValid)
        check(binding.inputLastNameLayout, binding.inputLastName, RegistrationValidator::isNameValid)
        check(binding.inputPatronymicLayout, binding.inputPatronymic, RegistrationValidator::isPatronymicValid)

        return valid
    }

    /** Ошибка поля сбрасывается при вводе текста. */
    private fun bindErrorClearing(field: TextInputEditText, layout: TextInputLayout) {
        field.doOnTextChanged { _, _, _, _ -> layout.error = null }
    }

    /** ФИО в формате «Фамилия Имя Отчество». */
    private fun collectFullName(): String = ProfileForm.format(
        lastName = binding.inputLastName.text?.toString().orEmpty(),
        firstName = binding.inputFirstName.text?.toString().orEmpty(),
        patronymic = binding.inputPatronymic.text?.toString().orEmpty()
    )

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        /** Аргумент: true — режим редактирования из вкладки «Аккаунт». */
        const val ARG_EDIT_MODE = "edit_mode"
    }
}
