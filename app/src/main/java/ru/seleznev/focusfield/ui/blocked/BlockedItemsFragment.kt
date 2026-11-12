package ru.seleznev.focusfield.ui.blocked

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.data.model.BlockedItem
import ru.seleznev.focusfield.databinding.FragmentBlockedItemsBinding
import ru.seleznev.focusfield.ui.common.appContainer

/**
 * Список блокировки: приложения и сайты, доступ к которым ограничивается
 * при низкой концентрации.
 */
class BlockedItemsFragment : Fragment() {

    private var _binding: FragmentBlockedItemsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: BlockedItemsViewModel by viewModels { appContainer.viewModelFactory }

    private lateinit var adapter: BlockedItemsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlockedItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BlockedItemsAdapter(
            onToggle = { item, enabled -> viewModel.setEnabled(item, enabled) },
            onDelete = { viewModel.delete(it) }
        )
        binding.items.layoutManager = LinearLayoutManager(requireContext())
        binding.items.adapter = adapter

        viewModel.items.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.empty.isVisible = items.isEmpty()
        }

        binding.btnAddSite.setOnClickListener {
            viewModel.addSite(binding.inputSite.text?.toString().orEmpty())
            binding.inputSite.setText("")
        }

        binding.btnPickApps.setOnClickListener { showAppPicker() }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }
    }

    /** Мультивыбор установленных приложений. */
    private fun showAppPicker() {
        val pm = requireContext().packageManager
        val packages = pm.getInstalledPackages(0)
            .filter { it.packageName != requireContext().packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.applicationInfo != null }
            .map {
                it.packageName to pm.getApplicationLabel(it.applicationInfo!!).toString()
            }
            .sortedBy { it.second }

        val labels = packages.map { it.second }.toTypedArray()
        val checked = BooleanArray(labels.size) { false }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.blocked_pick_apps_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.task_save) { _, _ ->
                val selected = packages.indices.filter { checked[it] }.map { packages[it] }
                viewModel.addApps(selected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
