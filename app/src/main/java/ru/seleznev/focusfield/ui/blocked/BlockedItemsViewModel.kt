package ru.seleznev.focusfield.ui.blocked

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.model.BlockedItem
import ru.seleznev.focusfield.data.repo.BlockRepository

class BlockedItemsViewModel(private val blockRepository: BlockRepository) : ViewModel() {

    val items: LiveData<List<BlockedItem>> = blockRepository.observeItems().asLiveData()

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun addSite(rawDomain: String) {
        viewModelScope.launch {
            val added = blockRepository.addSite(rawDomain)
            _message.value = when {
                !added -> "Некорректный домен или уже в списке"
                else -> null
            }
        }
    }

    /** Добавляет выбранные в диалоге приложения (дубликаты пропускаются). */
    fun addApps(selected: List<Pair<String, String>>) {
        viewModelScope.launch {
            var added = 0
            selected.forEach { (pkg, label) ->
                if (blockRepository.addApp(pkg, label)) added++
            }
            _message.value = if (added > 0) "Добавлено приложений: $added" else null
        }
    }

    fun setEnabled(item: BlockedItem, enabled: Boolean) {
        viewModelScope.launch { blockRepository.setEnabled(item.id, enabled) }
    }

    fun delete(item: BlockedItem) {
        viewModelScope.launch { blockRepository.delete(item) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
