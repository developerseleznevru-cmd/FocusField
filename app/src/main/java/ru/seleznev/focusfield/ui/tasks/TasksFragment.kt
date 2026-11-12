package ru.seleznev.focusfield.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.databinding.DialogAddTaskBinding
import ru.seleznev.focusfield.databinding.FragmentTasksBinding
import ru.seleznev.focusfield.ui.common.appContainer
import java.util.Calendar
import java.util.TimeZone

class TasksFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val viewModel: TasksViewModel by activityViewModels { appContainer.viewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = TasksAdapter(
            onToggle = { viewModel.toggleDone(it) },
            onDelete = { viewModel.delete(it) }
        )
        binding.taskList.layoutManager = LinearLayoutManager(requireContext())
        binding.taskList.adapter = adapter

        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            adapter.submitList(tasks)
            binding.empty.isVisible = tasks.isEmpty()
        }

        binding.btnAdd.setOnClickListener { showAddTaskDialog() }
        binding.btnImportApi.setOnClickListener { viewModel.importFromApi() }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                viewModel.consumeMessage()
            }
        }

        viewModel.syncing.observe(viewLifecycleOwner) { syncing ->
            binding.btnImportApi.isEnabled = !syncing
        }
    }

    private fun showAddTaskDialog() {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        var dueAt: Long? = null
        var remindAt: Long? = null
        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())

        dialogBinding.btnDueDate.setOnClickListener {
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.task_pick_due_date)
                .build()
                .apply {
                    addOnPositiveButtonClickListener { picked ->
                        dueAt = picked
                        dialogBinding.btnDueDate.text = dateFormat.format(java.util.Date(picked))
                    }
                }
                .show(childFragmentManager, "due_date")
        }

        dialogBinding.btnReminder.setOnClickListener {
            val now = Calendar.getInstance()
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .setTitleText(R.string.task_pick_reminder)
                .build()
            picker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance(TimeZone.getDefault())
                cal.set(Calendar.HOUR_OF_DAY, picker.hour)
                cal.set(Calendar.MINUTE, picker.minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                remindAt = cal.timeInMillis
                dialogBinding.btnReminder.text = String.format("%02d:%02d", picker.hour, picker.minute)
            }
            picker.show(childFragmentManager, "reminder_time")
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.task_add_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.task_save) { _, _ ->
                val title = dialogBinding.inputTitle.text?.toString()?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    viewModel.addTask(
                        title = title,
                        description = dialogBinding.inputDescription.text?.toString()?.trim().orEmpty(),
                        dueAt = dueAt,
                        remindAt = remindAt,
                        priority = 1
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
