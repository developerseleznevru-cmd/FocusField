package ru.seleznev.focusfield.ui.focus

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.seleznev.focusfield.biometrics.BlinkDetector
import ru.seleznev.focusfield.biometrics.ConcentrationCalculator
import ru.seleznev.focusfield.biometrics.FaceFrame
import ru.seleznev.focusfield.data.model.FocusSession
import ru.seleznev.focusfield.data.model.Task
import ru.seleznev.focusfield.data.model.WearHealthData
import ru.seleznev.focusfield.data.repo.FocusRepository
import ru.seleznev.focusfield.data.repo.SettingsRepository
import ru.seleznev.focusfield.data.repo.TaskRepository
import ru.seleznev.focusfield.data.state.BlockingStateStore
import ru.seleznev.focusfield.service.wear.WearHealthStore

/**
 * Управление сессией концентрации: приём кадров биометрии, расчёт уровня
 * внимания, публикация состояния для сервиса блокировки, запись метрик.
 */
class FocusViewModel(
    private val focusRepository: FocusRepository,
    private val taskRepository: TaskRepository,
    private val blockingStateStore: BlockingStateStore,
    private val settingsRepository: SettingsRepository,
    wearHealthStore: WearHealthStore
) : ViewModel() {

    val score: LiveData<Int> = blockingStateStore.currentScore.asLiveData()
    val sessionActive: LiveData<Boolean> = blockingStateStore.sessionActive.asLiveData()
    val wearData: LiveData<WearHealthData?> = wearHealthStore.data.asLiveData()
    val tasks: LiveData<List<Task>> = taskRepository.observeTasks().asLiveData()
    val cameraMonitoringEnabled: Boolean get() = settingsRepository.state.value.cameraMonitoring

    private val _elapsedSeconds = MutableLiveData(0L)
    val elapsedSeconds: LiveData<Long> = _elapsedSeconds

    private val _selectedTaskId = MutableLiveData(0L)
    val selectedTaskId: LiveData<Long> = _selectedTaskId

    private val frameBuffer = ArrayDeque<FaceFrame>()
    private val blinkDetector = BlinkDetector()
    private var pendingSamples = mutableListOf<Pair<Long, Int>>()
    private var lastSampleAt = 0L
    private var sessionMin = Int.MAX_VALUE
    private var sessionMax = Int.MIN_VALUE
    private var tickerJob: Job? = null

    init {
        // Восстановление активной сессии после перезапуска процесса
        if (blockingStateStore.sessionActive.value) {
            startTicker()
        }
    }

    fun startSession() {
        if (blockingStateStore.sessionActive.value) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sessionId = focusRepository.startSession(_selectedTaskId.value ?: 0L, now)
            blockingStateStore.startSession(sessionId, now)
            frameBuffer.clear()
            blinkDetector.reset()
            pendingSamples = mutableListOf()
            lastSampleAt = 0L
            sessionMin = Int.MAX_VALUE
            sessionMax = Int.MIN_VALUE
            _elapsedSeconds.value = 0L
            startTicker()
        }
    }

    fun stopSession() {
        if (!blockingStateStore.sessionActive.value) return
        viewModelScope.launch {
            val data = blockingStateStore.sessionData
            val now = System.currentTimeMillis()
            val scores = pendingSamples.map { it.second }.filter { it >= 0 }
            val avg = if (scores.isEmpty()) BlockingStateStore.NO_DATA else scores.average().toInt()
            focusRepository.addSamples(data.id, pendingSamples)
            focusRepository.finishSession(
                sessionId = data.id,
                endedAt = now,
                avgScore = avg,
                minScore = sessionMin.takeIf { it != Int.MAX_VALUE } ?: BlockingStateStore.NO_DATA,
                maxScore = sessionMax.takeIf { it != Int.MIN_VALUE } ?: BlockingStateStore.NO_DATA,
                interruptions = data.interruptions,
                blinks = blinkDetector.blinks
            )
            blockingStateStore.endSession()
            tickerJob?.cancel()
            pendingSamples = mutableListOf()
            _elapsedSeconds.value = 0L
        }
    }

    fun selectTask(taskId: Long) {
        _selectedTaskId.value = taskId
    }

    /**
     * Приём кадра от анализатора камеры. Вызывается на главном потоке.
     */
    fun onFaceFrame(frame: FaceFrame) {
        frameBuffer.addLast(frame)
        while (frameBuffer.size > ConcentrationCalculator.WINDOW_SIZE) {
            frameBuffer.removeFirst()
        }
        blinkDetector.update(frame.leftEyeOpen, frame.rightEyeOpen)

        val newScore = ConcentrationCalculator.score(frameBuffer.toList())
        if (newScore >= 0) {
            blockingStateStore.publishScore(newScore)
            if (sessionMin > newScore) sessionMin = newScore
            if (sessionMax < newScore) sessionMax = newScore
        }

        val active = blockingStateStore.sessionActive.value
        if (active && frame.timestamp - lastSampleAt >= SAMPLE_INTERVAL_MS) {
            lastSampleAt = frame.timestamp
            pendingSamples.add(frame.timestamp to newScore)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && blockingStateStore.sessionActive.value) {
                val startedAt = blockingStateStore.sessionData.startedAt
                if (startedAt > 0) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - startedAt) / 1000
                }
                delay(1000)
            }
        }
    }

    companion object {
        /** Минимальный интервал между сохраняемыми точками измерения. */
        const val SAMPLE_INTERVAL_MS = 10_000L
    }
}
