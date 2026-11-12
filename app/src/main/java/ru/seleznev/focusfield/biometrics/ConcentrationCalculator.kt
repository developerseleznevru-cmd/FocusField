package ru.seleznev.focusfield.biometrics

/**
 * Чистая логика расчёта уровня концентрации по окну кадров.
 * Используются признаки ML Kit Face Detection:
 *  - открытость глаз (левый/правый),
 *  - наклон и поворот головы (pitch/yaw),
 *  - улыбка (признак отвлечения),
 *  - наличие лица в кадре.
 *
 * Проверяется юнит-тестами (см. ConcentrationCalculatorTest).
 */
object ConcentrationCalculator {

    /** Уровень концентрации, когда биометрические данные недоступны. */
    const val NO_DATA = -1

    /** Оценка, когда лицо не найдено в окне наблюдения. */
    const val NO_FACE_SCORE = 0

    /** Размер окна анализа, кадров (при ~4 кадрах/сек это ~10 секунд). */
    const val WINDOW_SIZE = 40

    fun score(frames: List<FaceFrame>): Int {
        if (frames.isEmpty()) return NO_DATA
        if (frames.none { it.facePresent }) return NO_FACE_SCORE

        // Открытость глаз: берём минимум из двух глаз кадра,
        // чтобы прищур/подмигивание не считались полным вниманием.
        val eyeValues = frames.filter { it.facePresent }.mapNotNull { frame ->
            val l = frame.leftEyeOpen
            val r = frame.rightEyeOpen
            when {
                l != null && r != null -> minOf(l, r)
                l != null -> l
                r != null -> r
                else -> null
            }
        }
        val eyesOpen = if (eyeValues.isEmpty()) 1f else eyeValues.average().toFloat()

        // Отвлечение головы от экрана: берём доминирующее из отклонений yaw/pitch
        val headPenalty = frames.filter { it.facePresent }.averageOf { frame ->
            val yawDeviation = minOf(abs(frame.headYaw ?: 0f), MAX_HEAD_ANGLE_DEGREES) / MAX_HEAD_ANGLE_DEGREES
            val pitchDeviation = minOf(abs(frame.headPitch ?: 0f), MAX_HEAD_ANGLE_DEGREES) / MAX_HEAD_ANGLE_DEGREES
            maxOf(yawDeviation, pitchDeviation) * HEAD_PENALTY
        }

        // Улыбка — признак отвлечения (мессенджеры, юмор и т.п.)
        val smilePenalty = frames.filter { it.facePresent }.averageOf { frame ->
            if ((frame.smiling ?: 0f) > SMILE_THRESHOLD) SMILE_PENALTY else 0f
        }

        val raw = eyesOpen * 100f - headPenalty - smilePenalty + stabilityBonus(frames)
        return raw.coerceIn(0f, 100f).toInt()
    }

    /** Бонус за стабильность взгляда: при ровной позе головы добавляется к score. */
    fun stabilityBonus(frames: List<FaceFrame>): Int {
        if (frames.none { it.facePresent }) return 0
        val stddev = frames.filter { it.facePresent }
            .mapNotNull { frame ->
                val yaw = frame.headYaw ?: 0f
                val pitch = frame.headPitch ?: 0f
                abs(yaw) + abs(pitch)
            }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: return 0
        return if (stddev < 8.0) STABLE_BONUS else 0
    }

    private inline fun <T> List<T>.averageOf(selector: (T) -> Float): Float {
        if (isEmpty()) return 0f
        var sum = 0f
        for (item in this) sum += selector(item)
        return sum / size
    }

    private fun abs(value: Float): Float = if (value < 0) -value else value

    private const val MAX_HEAD_ANGLE_DEGREES = 30f
    private const val HEAD_PENALTY = 15f
    private const val SMILE_THRESHOLD = 0.8f
    private const val SMILE_PENALTY = 10f
    private const val STABLE_BONUS = 5
}
