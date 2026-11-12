package ru.seleznev.focusfield.biometrics

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * Анализатор кадров CameraX: распознавание лица, глаз и позы головы через ML Kit.
 * Результаты отдаются через колбэк [onFrame].
 */
class FaceAnalyzer(
    private val onFrame: (FaceFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.firstOrNull()
                val frame = if (face == null) {
                    FaceFrame.noFace(System.currentTimeMillis())
                } else {
                    FaceFrame(
                        timestamp = System.currentTimeMillis(),
                        facePresent = true,
                        leftEyeOpen = face.leftEyeOpenProbability,
                        rightEyeOpen = face.rightEyeOpenProbability,
                        headPitch = face.headEulerAngleX,
                        headYaw = face.headEulerAngleY,
                        headRoll = face.headEulerAngleZ,
                        smiling = face.smilingProbability
                    )
                }
                onFrame(frame)
            }
            .addOnFailureListener { /* Кадр пропускается, ошибка не фатальна */ }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun release() {
        detector.close()
    }
}
