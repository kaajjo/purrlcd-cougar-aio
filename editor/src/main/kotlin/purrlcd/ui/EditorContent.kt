package purrlcd.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import purrlcd.resources.preview_edit_hint
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
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Good
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Panel
import purrlcd.ui.theme.PreviewBackground
import purrlcd.ui.theme.PreviewBorder
import purrlcd.ui.theme.editorTextStyle

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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EditorSpacing.Space32, vertical = EditorSpacing.Space24),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(
                            listOf(
                                Res.string.screen_title,
                                Res.string.appearance_title,
                                Res.string.settings_title
                            )[page]
                        ), style = editorTextStyle(EditorTypography.Title)
                    )
                    Spacer(Modifier.height(EditorSpacing.Space4))
                    Text(
                        stringResource(
                            listOf(
                                Res.string.screen_subtitle,
                                Res.string.appearance_subtitle,
                                Res.string.settings_subtitle
                            )[page]
                        ), color = Muted, style = editorTextStyle(EditorTypography.Body)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space16)
                ) {
                    if (dirty) Text(
                        stringResource(Res.string.unsaved_changes),
                        color = Orange,
                        style = editorTextStyle(EditorTypography.Label)
                    )
                    Button(
                        onClick = onSave, enabled = ready && !busy && dirty,
                        shape = EditorShapes.Button, elevation = ButtonDefaults.elevation(0.dp),
                        contentPadding = PaddingValues(
                            horizontal = EditorSpacing.Space24,
                            vertical = EditorSpacing.Space12
                        )
                    ) {
                        Text(stringResource(Res.string.apply), style = editorTextStyle(EditorTypography.Action))
                    }
                }
            }
            Divider(color = Line)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(EditorSpacing.Space32),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.preview_heading),
                            color = Muted,
                            style = editorTextStyle(EditorTypography.PreviewHeading)
                        )
                        Tag("720 × 720  ·  IPS", Muted)
                    }
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().padding(vertical = EditorSpacing.Space12),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewSize = minOf(maxWidth, maxHeight)
                        Box(
                            Modifier.size(previewSize).padding(EditorSpacing.Space12)
                                .clip(EditorShapes.Preview).background(PreviewBackground)
                                .border(EditorDimensions.BorderWidth, PreviewBorder, EditorShapes.Preview)
                        ) {
                            ScreenPreview(
                                scene, status, nativePreview,
                                selectedLayer = selectedLayer,
                                onSelectLayer = { selectedLayer = it },
                                onSceneChange = if (page == 1) onSceneChange else null
                            )
                        }
                    }
                    Text(
                        if (page == 1) stringResource(Res.string.preview_edit_hint)
                        else if (nativePreview != null) stringResource(Res.string.preview_native_hint) else stringResource(
                            Res.string.preview_draft_hint
                        ), color = Muted, style = editorTextStyle(EditorTypography.Caption)
                    )
                    Spacer(Modifier.height(EditorSpacing.Space24))
                    Row(
                        Modifier.fillMaxWidth().clip(EditorShapes.Card).background(Panel)
                            .padding(EditorSpacing.Space16),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space12)
                        ) {
                            Dot(if (status.connected && ready) Good else Muted)
                            Column {
                                Text(
                                    if (status.connecting) stringResource(Res.string.display_connecting) else if (status.connected && ready) stringResource(
                                        Res.string.display_running
                                    ) else stringResource(Res.string.display_stopped),
                                    style = editorTextStyle(EditorTypography.BodyMedium)
                                )
                                Text(
                                    "POSEIDON VISTEK PRO",
                                    color = Muted,
                                    style = editorTextStyle(EditorTypography.DeviceLabel)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onToggleConnection,
                            enabled = ready && !busy && !status.connecting,
                            shape = EditorShapes.CompactButton,
                            border = androidx.compose.foundation.BorderStroke(EditorDimensions.BorderWidth, Line)
                        ) {
                            Text(
                                if (status.connected) stringResource(Res.string.stop) else stringResource(Res.string.connect),
                                style = editorTextStyle(EditorTypography.Label)
                            )
                        }
                    }
                }
                Box(Modifier.width(EditorDimensions.BorderWidth).fillMaxHeight().background(Line))
                Column(
                    Modifier.width(EditorDimensions.InspectorWidth).fillMaxHeight().background(Panel)
                        .verticalScroll(rememberScrollState()).padding(EditorSpacing.Space24)
                ) {
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
            Row(
                Modifier.fillMaxWidth().height(EditorDimensions.StatusBarHeight)
                    .padding(horizontal = EditorSpacing.Space24), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space8)
            ) {
                Dot(if (noteIsError) Orange else Good, 5)
                Text(
                    note,
                    color = if (noteIsError) Orange else Muted,
                    style = editorTextStyle(EditorTypography.Caption),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
