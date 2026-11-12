package ru.seleznev.focusfield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.biometrics.BlinkDetector
import ru.seleznev.focusfield.biometrics.FaceFrame

class BlinkDetectorTest {

    private fun frame(left: Float, right: Float, timestamp: Long) = FaceFrame(
        timestamp = timestamp,
        facePresent = true,
        leftEyeOpen = left,
        rightEyeOpen = right,
        headPitch = 0f,
        headYaw = 0f,
        headRoll = 0f,
        smiling = 0f
    )

    @Test
    fun `single blink is detected once`() {
        val detector = BlinkDetector()
        detector.update(1f, 1f)
        detector.update(0.1f, 0.1f)
        detector.update(0.1f, 0.1f)
        detector.update(1f, 1f)
        assertEquals(1, detector.blinks)
    }

    @Test
    fun `long closure does not produce multiple blinks`() {
        val detector = BlinkDetector()
        repeat(10) { detector.update(0.1f, 0.1f) }
        repeat(3) { detector.update(1f, 1f) }
        assertEquals(1, detector.blinks)
    }

    @Test
    fun `one eye squinting does not count as closed`() {
        val detector = BlinkDetector()
        val closedFrame = detector.update(0.2f, 0.9f)
        assertFalse(closedFrame)
    }

    @Test
    fun `drowsiness risk by closed ratio`() {
        val detector = BlinkDetector()
        // 90% кадров с закрытыми глазами
        assertTrue(detector.isDrowsinessRisk(closedFrames = 18, totalFrames = 20))
        assertFalse(detector.isDrowsinessRisk(closedFrames = 4, totalFrames = 20))
        assertFalse(detector.isDrowsinessRisk(closedFrames = 100, totalFrames = 10))
    }
}
