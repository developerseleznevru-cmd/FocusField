package ru.seleznev.focusfield.ui.blocked

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.seleznev.focusfield.R
import ru.seleznev.focusfield.data.db.entity.BlockedItemEntity
import ru.seleznev.focusfield.data.model.BlockedItem
import ru.seleznev.focusfield.databinding.ItemBlockedBinding

/** Список элементов блокировки: приложения и сайты с переключателями. */
class BlockedItemsAdapter(
    private val onToggle: (BlockedItem, Boolean) -> Unit,
    private val onDelete: (BlockedItem) -> Unit
) : ListAdapter<BlockedItem, BlockedItemsAdapter.BlockedViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedViewHolder {
        val binding = ItemBlockedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BlockedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlockedViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BlockedViewHolder(
        private val binding: ItemBlockedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BlockedItem) {
            val isSite = item.type == BlockedItemEntity.TYPE_SITE
            binding.icon.setImageResource(if (isSite) R.drawable.ic_web else R.drawable.ic_block)
            binding.name.text = item.label
            binding.switchEnabled.isChecked = item.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(item, checked)
            }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<BlockedItem>() {
        override fun areItemsTheSame(oldItem: BlockedItem, newItem: BlockedItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BlockedItem, newItem: BlockedItem) =
            oldItem == newItem
    }
}
