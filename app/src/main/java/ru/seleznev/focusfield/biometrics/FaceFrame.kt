package ru.seleznev.focusfield.biometrics

/**
 * Один кадр наблюдения за пользователем (ML Kit Face Detection).
 * Вероятности — в диапазоне 0..1, null, если классификация недоступна.
 */
data class FaceFrame(
    val timestamp: Long,
    val facePresent: Boolean,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val headPitch: Float?,
    val headYaw: Float?,
    val headRoll: Float?,
    val smiling: Float?
) {
    companion object {
        /** Кадр, в котором лицо не обнаружено. */
        fun noFace(timestamp: Long) = FaceFrame(
            timestamp = timestamp,
            facePresent = false,
            leftEyeOpen = null,
            rightEyeOpen = null,
            headPitch = null,
            headYaw = null,
            headRoll = null,
            smiling = null
        )
    }
}
