package ru.seleznev.focusfield.service.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.seleznev.focusfield.data.model.WearHealthData

/**
 * Хранилище данных Wear OS: WearListenerService публикует сюда пульс и шаги,
 * FocusViewModel подписывается и учитывает их в анализе состояния.
 */
class WearHealthStore {

    private val _data = MutableStateFlow<WearHealthData?>(null)
    val data: StateFlow<WearHealthData?> = _data.asStateFlow()

    fun update(data: WearHealthData) {
        _data.value = data
    }
}
