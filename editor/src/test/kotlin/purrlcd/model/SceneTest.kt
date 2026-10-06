package purrlcd.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneTest {
    @Test
    fun legacyScenesDefaultToFullBrightness() {
        val scene = Json.decodeFromString<Scene>("""{"rotation":90,"intervalMs":3000}""")
        assertEquals(100, scene.brightness)
        assertEquals(90, scene.rotation)
        assertEquals(3000, scene.intervalMs)
    }

    @Test
    fun brightnessSurvivesSaveAndLoad() {
        for (percent in listOf(0, 37, 100)) {
            val scene = Scene(brightness = percent, rotation = 270)
            assertEquals(scene, Json.decodeFromString<Scene>(Json.encodeToString(scene)))
        }
    }
}
