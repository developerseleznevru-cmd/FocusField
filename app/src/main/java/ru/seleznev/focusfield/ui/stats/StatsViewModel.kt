package ru.seleznev.focusfield.ui.stats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.data.model.DayAverage
import ru.seleznev.focusfield.data.model.FocusBand
import ru.seleznev.focusfield.data.model.FocusSummary
import ru.seleznev.focusfield.data.model.StatsAggregator
import ru.seleznev.focusfield.data.repo.FocusRepository
import ru.seleznev.focusfield.util.DayMath
import java.util.TimeZone

/** Диапазон отчёта: день / неделя / месяц. */
enum class StatsRange(val days: Int, val title: String) {
    DAY(1, "День"),
    WEEK(7, "Неделя"),
    MONTH(30, "Месяц")
}

/** Данные для отрисовки графиков. */
data class StatsUi(
    val dayAverages: List<DayAverage>,
    val distribution: Map<FocusBand, Int>,
    val summary: FocusSummary
)

class StatsViewModel(private val focusRepository: FocusRepository) : ViewModel() {

    private val _range = MutableLiveData(StatsRange.WEEK)
    val range: LiveData<StatsRange> = _range

    private val _stats = MutableLiveData<StatsUi?>(null)
    val stats: LiveData<StatsUi?> = _stats

    init {
        // Автообновление при появлении новых сессий
        viewModelScope.launch {
            focusRepository.observeSessions()
                .onEach { load() }
                .collect { }
        }
    }

    fun setRange(range: StatsRange) {
        _range.value = range
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val days = _range.value?.days ?: StatsRange.WEEK.days
            val from = DayMath.startOfDay(System.currentTimeMillis()) - (days - 1) * StatsAggregator.MILLIS_PER_DAY
            val sessions = focusRepository.sessionsSince(from)
            val samples = focusRepository.samplesSince(from)
            _stats.value = StatsUi(
                dayAverages = StatsAggregator.dailyAverages(
                    sessions, days, System.currentTimeMillis(), TimeZone.getDefault()
                ),
                distribution = StatsAggregator.distribution(samples),
                summary = StatsAggregator.summary(sessions)
            )
        }
    }
}
