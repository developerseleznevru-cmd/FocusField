package ru.seleznev.focusfield.biometrics

/**
 * Детектор моргания по потоку кадров: фиксирует переход «оба глаза закрыты»
 * → «глаза открыты» как одно моргание. Считает суммарное время закрытых глаз.
 */
class BlinkDetector {

    private var eyesClosed = false
    var blinks: Int = 0
        private set

    /** Обновляется на каждом кадре. Возвращает true, если это кадр с закрытыми глазами. */
    fun update(leftOpen: Float?, rightOpen: Float?): Boolean {
        val left = leftOpen ?: 1f
        val right = rightOpen ?: 1f
        val closed = left < CLOSED_THRESHOLD && right < CLOSED_THRESHOLD
        if (closed && !eyesClosed) {
            eyesClosed = true
        } else if (!closed && eyesClosed) {
            eyesClosed = false
            blinks++
        }
        return closed
    }

    /** Признак сонливости: глаза закрыты в течение длительного периода. */
    fun isDrowsinessRisk(closedFrames: Int, totalFrames: Int): Boolean =
        totalFrames >= MIN_FRAMES && closedFrames * 100 / totalFrames > CLOSED_PERCENT_LIMIT

    fun reset() {
        eyesClosed = false
        blinks = 0
    }

    companion object {
        /** Вероятность открытого глаза ниже порога считается «закрытым». */
        const val CLOSED_THRESHOLD = 0.3f
        const val MIN_FRAMES = 20
        /** Больше 40% кадров с закрытыми глазами — риск сонливости. */
        const val CLOSED_PERCENT_LIMIT = 40
    }
}
