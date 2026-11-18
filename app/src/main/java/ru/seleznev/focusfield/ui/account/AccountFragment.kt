package ru.seleznev.focusfield.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.databinding.FragmentAccountBinding
import ru.seleznev.focusfield.ui.common.appContainer

/**
 * «Аккаунт» — вкладка нижнего меню: аватар, ФИО и email текущего
 * аккаунта, кнопка выхода с подтверждением. Раздел вынесен из настроек.
 *
 * После выхода MainActivity наблюдает AuthState и уводит на экран входа.
 */
class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: AccountViewModel by viewModels { appContainer.viewModelFactory }

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
        bindAccount()

        binding.btnLogout.setOnClickListener { confirmLogout() }
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
