package purrlcd.ui.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewDragPositionTest {
    @Test
    fun movementUsesPreviewScaleAndDisplayDensity() {
        // A 360 dp canvas on a 2x display is 720 physical pixels wide.
        assertEquals(PreviewDragPosition(60f, 610f), PreviewDragPosition(40f, 570f).move(20f, 40f, 1f))
        // The same dp movement on a 1x display produces the same scene position.
        assertEquals(PreviewDragPosition(60f, 610f), PreviewDragPosition(40f, 570f).move(10f, 20f, .5f))
    }

    @Test
    fun smallMovementsAccumulateWithoutRoundingEachEvent() {
        var position = PreviewDragPosition(40f, 570f)
        repeat(4) { position = position.move(.25f, -.25f, 2f) }
        assertEquals(PreviewDragPosition(40.5f, 569.5f), position)
    }

    @Test
    fun movementClampsToEngineLimitsAndReversesImmediatelyAtEdges() {
        val edge = PreviewDragPosition(40f, 570f).move(-1000f, 1000f, .5f)
        assertEquals(PreviewDragPosition(0f, 710f), edge)
        assertEquals(PreviewDragPosition(2f, 708f), edge.move(1f, -1f, .5f))
    }
}
