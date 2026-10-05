package purrlcd.ui.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import purrlcd.ui.preview.ScreenPreview

@Preview(name = "LCD · temperatures", group = "Display", widthDp = 360, heightDp = 360)
@Composable
fun LcdScreenPreview() {
    PreviewFrame(360.dp, 360.dp) {
        ScreenPreview(PreviewSamples.scene, PreviewSamples.status, native = null)
    }
}

@Preview(name = "LCD · missing sensors", group = "Display", widthDp = 360, heightDp = 360)
@Composable
fun LcdMissingSensorsPreview() {
    PreviewFrame(360.dp, 360.dp) {
        ScreenPreview(PreviewSamples.scene, PreviewSamples.missingSensors, native = null)
    }
}

@Preview(name = "LCD · custom layer", group = "Display", widthDp = 360, heightDp = 360)
@Composable
fun LcdCustomLayerPreview() {
    val scene = PreviewSamples.scene.copy(
        backgroundColor = "#1D222B",
        cpu = PreviewSamples.scene.cpu.copy(x = 60, y = 80, fontSize = 68, color = "#FF9B54"),
        gpu = PreviewSamples.scene.gpu.copy(enabled = false)
    )
    PreviewFrame(360.dp, 360.dp) {
        ScreenPreview(scene, PreviewSamples.status, native = null)
    }
}

@Preview(name = "LCD · drag layers", group = "Display", widthDp = 360, heightDp = 360)
@Composable
fun LcdEditablePreview() {
    var scene by remember { mutableStateOf(PreviewSamples.scene) }
    var selectedLayer by remember { mutableStateOf(0) }
    PreviewFrame(360.dp, 360.dp) {
        ScreenPreview(
            scene, PreviewSamples.status, native = null,
            selectedLayer = selectedLayer,
            onSelectLayer = { selectedLayer = it },
            onSceneChange = { scene = it }
        )
    }
}
