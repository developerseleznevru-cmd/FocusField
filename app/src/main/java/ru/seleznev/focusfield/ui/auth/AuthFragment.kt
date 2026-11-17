package ru.seleznev.focusfield.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.databinding.FragmentAuthBinding
import ru.seleznev.focusfield.ui.common.appContainer

class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AuthViewModel by viewModels { appContainer.viewModelFactory }

    private var isRegisterMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.isFirebaseAvailable) {
            binding.offlineNotice.isVisible = true
        }

        updateMode()

        binding.btnPrimary.setOnClickListener {
            if (isRegisterMode) {
                if (!validateRegistrationFields()) return@setOnClickListener
                viewModel.register(
                    binding.inputEmail.text?.toString().orEmpty().trim(),
                    binding.inputPassword.text?.toString().orEmpty(),
                    registerFullName()
                )
            } else {
                viewModel.signIn(
                    binding.inputEmail.text?.toString().orEmpty(),
                    binding.inputPassword.text?.toString().orEmpty()
                )
            }
        }

        binding.btnToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateMode()
        }

        binding.btnForgot.setOnClickListener {
            val email = binding.inputEmail.text?.toString().orEmpty()
            viewModel.sendPasswordReset(email)
        }

        bindErrorClearing(binding.inputEmail, binding.inputEmailLayout)
        bindErrorClearing(binding.inputPassword, binding.inputPasswordLayout)
        bindErrorClearing(binding.inputConfirmPassword, binding.inputConfirmPasswordLayout)
        bindErrorClearing(binding.inputFirstName, binding.inputFirstNameLayout)
        bindErrorClearing(binding.inputLastName, binding.inputLastNameLayout)
        bindErrorClearing(binding.inputPatronymic, binding.inputPatronymicLayout)

        binding.btnGoogle.setOnClickListener { viewModel.signInWithGoogle() }
        binding.btnVk.setOnClickListener { viewModel.signInWithVk() }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                viewModel.consumeMessage()
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progress.isVisible = loading
            binding.btnPrimary.isEnabled = !loading
        }
    }

    private fun updateMode() {
        registerOnlyLayouts().forEach { layout ->
            layout.isVisible = isRegisterMode
            if (!isRegisterMode) layout.error = null
        }
        if (!isRegisterMode) {
            binding.inputEmailLayout.error = null
            binding.inputPasswordLayout.error = null
            binding.inputFirstName.text?.clear()
            binding.inputLastName.text?.clear()
            binding.inputPatronymic.text?.clear()
            binding.inputConfirmPassword.text?.clear()
            // После выхода email последнего аккаунта подставляется автоматически.
            prefillLastAccountEmail()
        }
        if (isRegisterMode) {
            binding.title.text = getString(R.string.auth_register_title)
            binding.btnPrimary.text = getString(R.string.auth_register)
            binding.btnToggleMode.text = getString(R.string.auth_switch_to_login)
        } else {
            binding.title.text = getString(R.string.auth_login_title)
            binding.btnPrimary.text = getString(R.string.auth_login)
            binding.btnToggleMode.text = getString(R.string.auth_switch_to_register)
        }
    }

    /** Предзаполнение email последнего аккаунта, если поле пустое. */
    private fun prefillLastAccountEmail() {
        val current = binding.inputEmail.text?.toString().orEmpty()
        if (current.isNotBlank()) return
        viewModel.lastAccount?.let { binding.inputEmail.setText(it.email) }
    }

    /**
     * Валидация полей регистрации по ТЗ. Каждое некорректное поле получает
     * красную границу и текст «Поле заполнено неверно»; все проблемы
     * подсвечиваются одновременно. Возвращает true, если форма валидна.
     */
    private fun validateRegistrationFields(): Boolean {
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

        val password = binding.inputPassword.text?.toString().orEmpty()

        check(binding.inputEmailLayout, binding.inputEmail, RegistrationValidator::isEmailValid)
        check(binding.inputPasswordLayout, binding.inputPassword, RegistrationValidator::isPasswordValid)
        check(binding.inputConfirmPasswordLayout, binding.inputConfirmPassword) { it == password }
        check(binding.inputFirstNameLayout, binding.inputFirstName, RegistrationValidator::isNameValid)
        check(binding.inputLastNameLayout, binding.inputLastName, RegistrationValidator::isNameValid)
        check(binding.inputPatronymicLayout, binding.inputPatronymic, RegistrationValidator::isPatronymicValid)

        return valid
    }

    /** Ошибка поля сбрасывается при вводе текста. */
    private fun bindErrorClearing(field: TextInputEditText, layout: TextInputLayout) {
        field.doOnTextChanged { _, _, _, _ -> layout.error = null }
    }

    /** ФИО в формате «Фамилия Имя Отчество»; пропускает пустые поля. */
    private fun registerFullName(): String = listOf(
        binding.inputLastName.text?.toString().orEmpty().trim(),
        binding.inputFirstName.text?.toString().orEmpty().trim(),
        binding.inputPatronymic.text?.toString().orEmpty().trim()
    ).filter { it.isNotBlank() }.joinToString(" ")

    /** Поля, отображаемые только в режиме регистрации. */
    private fun registerOnlyLayouts() = listOf(
        binding.inputFirstNameLayout,
        binding.inputLastNameLayout,
        binding.inputPatronymicLayout,
        binding.inputConfirmPasswordLayout
    )

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
