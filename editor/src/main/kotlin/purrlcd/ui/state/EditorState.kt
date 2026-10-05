package purrlcd.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import purrlcd.engine.EngineClient
import purrlcd.engine.EngineReply
import purrlcd.engine.EngineStartup
import purrlcd.engine.EngineStatus
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.action_failed
import purrlcd.resources.display_connected
import purrlcd.resources.display_connecting
import purrlcd.resources.display_transmission_stopped
import purrlcd.resources.editor_ready
import purrlcd.resources.engine_connecting
import purrlcd.resources.engine_offline_preview
import purrlcd.resources.engine_reconnected
import purrlcd.resources.engine_start_failed
import purrlcd.resources.engine_starting
import purrlcd.resources.engine_unavailable
import purrlcd.resources.preview_failed
import purrlcd.resources.scene_saved
import purrlcd.ui.preview.loadImage

@Stable
class EditorState(
    private val client: EngineClient,
    private val scope: CoroutineScope,
    initialNote: String
) {
    var scene by mutableStateOf(Scene())
    var saved by mutableStateOf(Scene())
        private set
    var status by mutableStateOf(EngineStatus())
        private set
    var ready by mutableStateOf(false)
        private set
    var initialized by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var nativePreview by mutableStateOf<ImageBitmap?>(null)
        private set
    var note by mutableStateOf(initialNote)
        private set
    var noteIsError by mutableStateOf(false)
        private set
    val dirty: Boolean get() = scene != saved

    fun saveScene() = action("saveScene")
    fun toggleConnection() = action(if (status.connected) "disconnect" else "connect")

    private suspend fun showReply(reply: EngineReply, success: String) {
        reply.status?.let { status = it }
        noteIsError = !reply.ok
        note = if (reply.ok) success else reply.error ?: reply.message ?: getString(Res.string.action_failed)
    }

    private fun action(command: String) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val current = scene
                if (command == "connect" && current != saved) {
                    val saveReply = client.request("saveScene", current)
                    if (!saveReply.ok) {
                        showReply(saveReply, "")
                        return@launch
                    }
                    val canonical = saveReply.scene ?: current
                    saved = canonical
                    if (scene == current) scene = canonical
                }
                val response = client.request(command, if (command == "saveScene") current else null)
                if (response.ok && command == "saveScene") {
                    val canonical = response.scene ?: current
                    saved = canonical
                    if (scene == current) scene = canonical
                }
                showReply(response, when (command) {
                    "saveScene" -> getString(Res.string.scene_saved)
                    "connect" -> if (response.status?.connected == true) getString(Res.string.display_connected) else getString(Res.string.display_connecting)
                    else -> getString(Res.string.display_transmission_stopped)
                })
            } catch (_: Exception) {
                note = getString(Res.string.engine_unavailable)
                noteIsError = true
            } finally { busy = false }
        }
    }

    suspend fun observeEngine(startEngine: Boolean) {
        var sceneLoaded = false
        note = if (startEngine) getString(Res.string.engine_starting)
            else getString(Res.string.engine_connecting)
        val startup = if (startEngine) runCatching { client.ensureStarted() }.getOrElse {
            EngineStartup(false, getString(Res.string.engine_start_failed))
        } else EngineStartup(runCatching { client.request("status").ok }.getOrDefault(false), getString(Res.string.engine_offline_preview))
        ready = startup.ready
        note = if (ready) getString(Res.string.editor_ready) else startup.message
        noteIsError = !ready
        initialized = true
        while (currentCoroutineContext().isActive) {
            val wasReady = ready
            runCatching { client.request("status") }
                .onSuccess { reply -> ready = reply.ok; reply.status?.let { status = it } }
                .onFailure { ready = false }
            if (ready && !wasReady) {
                note = getString(Res.string.engine_reconnected)
                noteIsError = false
            }
            if (ready && !sceneLoaded) {
                runCatching { client.request("getScene") }.onSuccess { reply ->
                    if (reply.ok && reply.scene != null) {
                        val editedDuringStartup = scene != saved
                        saved = reply.scene
                        if (!editedDuringStartup) scene = reply.scene
                        sceneLoaded = true
                    }
                    reply.status?.let { status = it }
                }
            }
            delay(1000)
        }
    }

    suspend fun updatePreview() {
        if (!initialized || !ready) { nativePreview = null; return }
        delay(250)
        runCatching { client.request("preview", scene) }.onSuccess { reply ->
            if (reply.ok) {
                reply.previewPath?.let { path ->
                    nativePreview = withContext(Dispatchers.IO) { loadImage(path) }
                }
            } else {
                nativePreview = null
                note = reply.error ?: reply.message ?: getString(Res.string.preview_failed)
                noteIsError = true
            }
        }
    }
}

@Composable
fun rememberEditorState(client: EngineClient, startEngine: Boolean): EditorState {
    val scope = rememberCoroutineScope()
    val initialNote = stringResource(Res.string.engine_connecting)
    val state = remember(client, scope) { EditorState(client, scope, initialNote) }
    LaunchedEffect(state, startEngine) { state.observeEngine(startEngine) }
    LaunchedEffect(
        state, state.scene, state.initialized, state.ready,
        state.status.cpuTemp?.roundToInt(), state.status.gpuTemp?.roundToInt()
    ) { state.updatePreview() }
    return state
}
