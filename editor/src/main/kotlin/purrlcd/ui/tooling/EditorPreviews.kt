package purrlcd.ui.tooling

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import purrlcd.ui.EditorContent

@Preview(name = "Screen", group = "Editor", widthDp = 1280, heightDp = 850)
@Composable
fun EditorOverviewPreview() {
    EditorPagePreview(page = 0)
}

@Preview(name = "Appearance", group = "Editor", widthDp = 1280, heightDp = 850)
@Composable
fun EditorAppearancePreview() {
    EditorPagePreview(page = 1)
}

@Preview(name = "Settings", group = "Editor", widthDp = 1280, heightDp = 850)
@Composable
fun EditorSettingsPreview() {
    EditorPagePreview(page = 2)
}

@Composable
private fun EditorPagePreview(page: Int) {
    var scene by remember { mutableStateOf(PreviewSamples.scene) }
    var saved by remember { mutableStateOf(scene) }
    var status by remember { mutableStateOf(PreviewSamples.status) }
    PreviewFrame(1280.dp, 850.dp) {
        EditorContent(
            scene = scene,
            status = status,
            ready = true,
            busy = false,
            dirty = scene != saved,
            nativePreview = null,
            note = "Preview",
            noteIsError = false,
            onSave = { saved = scene },
            onToggleConnection = { status = status.copy(connected = !status.connected) },
            onSceneChange = { scene = it },
            onPickImage = {},
            initialPage = page
        )
    }
}
