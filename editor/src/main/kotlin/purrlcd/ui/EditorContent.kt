package purrlcd.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import purrlcd.engine.EngineStatus
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.appearance_subtitle
import purrlcd.resources.appearance_title
import purrlcd.resources.apply
import purrlcd.resources.connect
import purrlcd.resources.display_connecting
import purrlcd.resources.display_running
import purrlcd.resources.display_stopped
import purrlcd.resources.preview_draft_hint
import purrlcd.resources.preview_heading
import purrlcd.resources.preview_native_hint
import purrlcd.resources.screen_subtitle
import purrlcd.resources.screen_title
import purrlcd.resources.settings_subtitle
import purrlcd.resources.settings_title
import purrlcd.resources.stop
import purrlcd.resources.unsaved_changes
import purrlcd.ui.components.Dot
import purrlcd.ui.components.Sidebar
import purrlcd.ui.components.Tag
import purrlcd.ui.panels.AppearancePanel
import purrlcd.ui.panels.OverviewPanel
import purrlcd.ui.panels.SettingsPanel
import purrlcd.ui.preview.ScreenPreview
import purrlcd.ui.theme.Bg
import purrlcd.ui.theme.Good
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Panel
import purrlcd.ui.theme.Shape

/** UI-only entry point: previews use sample data without starting the engine or IPC. */
@Composable
fun EditorContent(
    scene: Scene,
    status: EngineStatus,
    ready: Boolean,
    busy: Boolean,
    dirty: Boolean,
    nativePreview: ImageBitmap?,
    note: String,
    noteIsError: Boolean,
    onSave: () -> Unit,
    onToggleConnection: () -> Unit,
    onSceneChange: (Scene) -> Unit,
    onPickImage: () -> Unit,
    initialPage: Int = 0
) {
    var page by remember { mutableStateOf(initialPage) }
    var selectedLayer by remember { mutableStateOf(0) }

    Row(Modifier.fillMaxSize().background(Bg)) {
        Sidebar(page, { page = it }, ready)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 25.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(listOf(Res.string.screen_title, Res.string.appearance_title, Res.string.settings_title)[page]), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(listOf(Res.string.screen_subtitle, Res.string.appearance_subtitle, Res.string.settings_subtitle)[page]), color = Muted, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (dirty) Text(stringResource(Res.string.unsaved_changes), color = Orange, fontSize = 12.sp)
                    Button(onClick = onSave, enabled = ready && !busy && dirty,
                        shape = RoundedCornerShape(10.dp), elevation = ButtonDefaults.elevation(0.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                        Text(stringResource(Res.string.apply), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Divider(color = Line)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.preview_heading), color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        Tag("720 × 720  ·  IPS", Muted)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        val previewSize = minOf(maxWidth, maxHeight)
                        Box(Modifier.size(previewSize).padding(12.dp)
                            .clip(RoundedCornerShape(28.dp)).background(Color.Black)
                            .border(1.dp, Color(0xFF363C47), RoundedCornerShape(28.dp))) {
                            ScreenPreview(scene, status, nativePreview)
                        }
                    }
                    Text(if (nativePreview != null) stringResource(Res.string.preview_native_hint) else stringResource(Res.string.preview_draft_hint), color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth().clip(Shape).background(Panel).padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Dot(if (status.connected && ready) Good else Muted)
                            Column {
                                Text(if (status.connecting) stringResource(Res.string.display_connecting) else if (status.connected && ready) stringResource(Res.string.display_running) else stringResource(Res.string.display_stopped), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("POSEIDON VISTEK PRO", color = Muted, fontSize = 10.sp, letterSpacing = 0.8.sp)
                            }
                        }
                        OutlinedButton(onClick = onToggleConnection,
                            enabled = ready && !busy && !status.connecting, shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                            Text(if (status.connected) stringResource(Res.string.stop) else stringResource(Res.string.connect), fontSize = 12.sp)
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
                Column(Modifier.width(326.dp).fillMaxHeight().background(Panel).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    when (page) {
                        0 -> OverviewPanel(scene, status, onEdit = { page = 1 })
                        1 -> AppearancePanel(
                            scene, selectedLayer, { selectedLayer = it },
                            onSceneChange = onSceneChange,
                            onPickImage = onPickImage
                        )
                        2 -> SettingsPanel(scene, onSceneChange = onSceneChange)
                    }
                }
            }
            Divider(color = Line)
            Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Dot(if (noteIsError) Orange else Good, 5)
                Text(note, color = if (noteIsError) Orange else Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
