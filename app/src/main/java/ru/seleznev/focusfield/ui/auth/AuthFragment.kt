package ru.seleznev.focusfield.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.view.isVisible
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
            val email = binding.inputEmail.text?.toString().orEmpty()
            val password = binding.inputPassword.text?.toString().orEmpty()
            if (isRegisterMode) viewModel.register(email, password) else viewModel.signIn(email, password)
        }

        binding.btnToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateMode()
        }

        binding.btnForgot.setOnClickListener {
            val email = binding.inputEmail.text?.toString().orEmpty()
            viewModel.sendPasswordReset(email)
        }

        binding.btnGoogle.setOnClickListener { viewModel.signInWithGoogle() }
        binding.btnFacebook.setOnClickListener { viewModel.signInWithFacebook() }

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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
