package purrlcd.model

import kotlinx.serialization.Serializable

@Serializable
data class TextLayer(
    val enabled: Boolean = true,
    val x: Int = 40,
    val y: Int = 570,
    val fontSize: Int = 44,
    val color: String = "#FFFFFF",
    val label: String = "CPU"
) {
    companion object {
        // The engine validates text origins against this range.
        val CoordinateRange = 0..710
    }
}

@Serializable
data class Scene(
    val backgroundPath: String = "",
    val backgroundColor: String = "#111318",
    val rotation: Int = 180,
    val intervalMs: Int = 1000,
    val cpu: TextLayer = TextLayer(),
    val gpu: TextLayer = TextLayer(y = 630, label = "GPU")
)
