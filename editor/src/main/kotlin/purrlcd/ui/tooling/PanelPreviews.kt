package purrlcd.ui.tooling

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import purrlcd.ui.panels.AppearancePanel
import purrlcd.ui.panels.OverviewPanel
import purrlcd.ui.panels.SettingsPanel

@Preview(name = "Overview", group = "Panels", widthDp = 326, heightDp = 800)
@Composable
fun OverviewPanelPreview() {
    PanelPreviewFrame {
        OverviewPanel(PreviewSamples.scene, PreviewSamples.status, onEdit = {})
    }
}

@Preview(name = "Overview · missing sensors", group = "Panels", widthDp = 326, heightDp = 800)
@Composable
fun OverviewMissingSensorsPreview() {
    PanelPreviewFrame {
        OverviewPanel(PreviewSamples.scene, PreviewSamples.missingSensors, onEdit = {})
    }
}

@Preview(name = "Overview · conflicting application", group = "Panels", widthDp = 326, heightDp = 800)
@Composable
fun OverviewWarningPreview() {
    PanelPreviewFrame {
        OverviewPanel(
            PreviewSamples.scene,
            PreviewSamples.status.copy(connected = false, stockRunning = true),
            onEdit = {}
        )
    }
}

@Preview(name = "Appearance", group = "Panels", widthDp = 326, heightDp = 800)
@Composable
fun AppearancePanelPreview() {
    var scene by remember { mutableStateOf(PreviewSamples.scene) }
    var selectedLayer by remember { mutableStateOf(0) }
    PanelPreviewFrame {
        AppearancePanel(
            scene = scene,
            selectedLayer = selectedLayer,
            onSelectLayer = { selectedLayer = it },
            onSceneChange = { scene = it },
            onPickImage = {}
        )
    }
}

@Preview(name = "Settings", group = "Panels", widthDp = 326, heightDp = 800)
@Composable
fun SettingsPanelPreview() {
    var scene by remember { mutableStateOf(PreviewSamples.scene) }
    PanelPreviewFrame {
        SettingsPanel(scene, onSceneChange = { scene = it })
    }
}
