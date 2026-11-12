package ru.seleznev.focusfield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.seleznev.focusfield.biometrics.ConcentrationCalculator
import ru.seleznev.focusfield.biometrics.FaceFrame
import ru.seleznev.focusfield.data.model.FocusBand

class ConcentrationCalculatorTest {

    private fun frame(
        facePresent: Boolean = true,
        left: Float? = 1f,
        right: Float? = 1f,
        yaw: Float? = 0f,
        pitch: Float? = 0f,
        smiling: Float? = 0f
    ) = FaceFrame(
        timestamp = 0,
        facePresent = facePresent,
        leftEyeOpen = left,
        rightEyeOpen = right,
        headPitch = pitch,
        headYaw = yaw,
        headRoll = 0f,
        smiling = smiling
    )

    @Test
    fun `empty window returns NO_DATA`() {
        assertEquals(ConcentrationCalculator.NO_DATA, ConcentrationCalculator.score(emptyList()))
    }

    @Test
    fun `no face anywhere returns zero`() {
        val frames = List(5) { FaceFrame.noFace(it.toLong()) }
        assertEquals(ConcentrationCalculator.NO_FACE_SCORE, ConcentrationCalculator.score(frames))
    }

    @Test
    fun `attentive user scores high`() {
        val frames = List(10) { frame() }
        assertTrue("score=${ConcentrationCalculator.score(frames)}", ConcentrationCalculator.score(frames) >= 90)
    }

    @Test
    fun `closed eyes score low`() {
        val frames = List(10) { frame(left = 0.1f, right = 0.1f) }
        assertTrue(ConcentrationCalculator.score(frames) <= 15)
    }

    @Test
    fun `one squinting eye lowers score`() {
        val attentive = ConcentrationCalculator.score(List(10) { frame() })
        val squinting = ConcentrationCalculator.score(List(10) { frame(right = 0.4f) })
        assertTrue(squinting < attentive)
    }

    @Test
    fun `head turned away lowers score`() {
        val straight = ConcentrationCalculator.score(List(10) { frame() })
        val turned = ConcentrationCalculator.score(List(10) { frame(yaw = 30f) })
        assertTrue("straight=$straight turned=$turned", turned < straight - 10)
    }

    @Test
    fun `mixed concentration classifies by bands`() {
        assertEquals(FocusBand.HIGH, FocusBand.fromScore(90))
        assertEquals(FocusBand.MEDIUM, FocusBand.fromScore(60))
        assertEquals(FocusBand.LOW, FocusBand.fromScore(20))
        assertEquals(FocusBand.NO_DATA, FocusBand.fromScore(-1))
    }
}
