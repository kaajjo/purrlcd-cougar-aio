package purrlcd.ui.preview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import purrlcd.model.TextLayer

class NativePreviewLayersTest {
    @Test
    fun movingOrTogglingALayerReusesItsRenderedPixels() {
        val layer = TextLayer()
        assertTrue(sameTextAppearance(layer, layer.copy(x = 710, y = 0, enabled = false)))
    }

    @Test
    fun changingTextAppearanceRequiresNewPixels() {
        val layer = TextLayer()
        assertFalse(sameTextAppearance(layer, layer.copy(label = "Processor")))
        assertFalse(sameTextAppearance(layer, layer.copy(fontSize = 80)))
        assertFalse(sameTextAppearance(layer, layer.copy(color = "#FF0000")))
    }
}
