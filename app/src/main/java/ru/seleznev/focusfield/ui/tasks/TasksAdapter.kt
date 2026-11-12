package ru.seleznev.focusfield.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.seleznev.focusfield.data.model.Task
import ru.seleznev.focusfield.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Список задач с отметкой выполнения и удалением. */
class TasksAdapter(
    private val onToggle: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TasksAdapter.TaskViewHolder>(Diff) {

    private val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.title.text = task.title
            binding.description.isVisible = task.description.isNotBlank()
            binding.description.text = task.description
            binding.dueDate.isVisible = task.dueAt != null
            task.dueAt?.let { binding.dueDate.text = dateFormat.format(Date(it)) }
            binding.checkbox.isChecked = task.isDone

            binding.checkbox.setOnClickListener { onToggle(task) }
            binding.btnDelete.setOnClickListener { onDelete(task) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
