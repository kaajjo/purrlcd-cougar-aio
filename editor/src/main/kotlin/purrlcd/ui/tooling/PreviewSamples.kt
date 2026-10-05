package purrlcd.ui.tooling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import purrlcd.engine.EngineStatus
import purrlcd.model.Scene
import purrlcd.ui.theme.Panel
import purrlcd.ui.theme.PurrLCDTheme

// All sample data is local: previews never connect to the engine or open an image file.
object PreviewSamples {
    val scene = Scene(backgroundColor = "#111318")
    val status = EngineStatus(connected = true, cpuTemp = 47.5, gpuTemp = 38.0)
    val missingSensors = EngineStatus(connected = true)
}

@Composable
fun PreviewFrame(width: Dp, height: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(width, height)) {
        PurrLCDTheme(content)
    }
}

@Composable
fun PanelPreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    PreviewFrame(326.dp, 800.dp) {
        Column(
            Modifier.fillMaxSize().background(Panel)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            content = content
        )
    }
}
