package purrlcd.ui

import androidx.compose.runtime.Composable
import purrlcd.engine.EngineClient
import purrlcd.ui.state.rememberEditorState

@Composable
fun EditorScreen(client: EngineClient, startEngine: Boolean, pickImage: () -> String?) {
    val state = rememberEditorState(client, startEngine)
    EditorContent(
        scene = state.scene,
        status = state.status,
        ready = state.ready,
        busy = state.busy,
        dirty = state.dirty,
        nativePreview = state.nativePreview,
        nativeLayers = state.nativeLayers,
        note = state.note,
        noteIsError = state.noteIsError,
        onSave = { state.saveScene() },
        onToggleConnection = { state.toggleConnection() },
        onSceneChange = { state.scene = it },
        onPickImage = { pickImage()?.let { state.scene = state.scene.copy(backgroundPath = it) } }
    )
}
