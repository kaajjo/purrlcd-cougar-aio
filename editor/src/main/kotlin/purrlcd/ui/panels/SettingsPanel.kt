package purrlcd.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Divider
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.duration_seconds
import purrlcd.resources.settings_editor_body
import purrlcd.resources.settings_editor_title
import purrlcd.resources.settings_refresh_heading
import purrlcd.resources.settings_refresh_hint
import purrlcd.resources.settings_refresh_interval
import purrlcd.resources.settings_rotation_heading
import purrlcd.resources.settings_rotation_hint
import purrlcd.ui.components.Choice
import purrlcd.ui.components.InfoCard
import purrlcd.ui.components.SectionLabel
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.editorTextStyle

@Composable
fun SettingsPanel(scene: Scene, onSceneChange: (Scene) -> Unit) {
    SectionLabel(stringResource(Res.string.settings_refresh_heading))
    Spacer(Modifier.height(EditorSpacing.Space16))
    Text(
        stringResource(Res.string.settings_refresh_interval, scene.intervalMs / 1000),
        style = editorTextStyle(EditorTypography.Value)
    )
    Spacer(Modifier.height(EditorSpacing.Space4))
    Slider(
        value = scene.intervalMs / 1000f,
        onValueChange = { onSceneChange(scene.copy(intervalMs = it.roundToInt() * 1000)) },
        valueRange = 1f..5f,
        steps = 3,
        colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Line)
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(Res.string.duration_seconds, 1),
            color = Muted,
            style = editorTextStyle(EditorTypography.Caption)
        ); Text(
        stringResource(Res.string.duration_seconds, 5),
        color = Muted,
        style = editorTextStyle(EditorTypography.Caption)
    )
    }
    Spacer(Modifier.height(EditorSpacing.Space16))
    Text(
        stringResource(Res.string.settings_refresh_hint),
        color = Muted,
        style = editorTextStyle(EditorTypography.Label)
    )
    Spacer(Modifier.height(EditorSpacing.Space32)); Divider(color = Line); Spacer(Modifier.height(EditorSpacing.Space24))
    SectionLabel(stringResource(Res.string.settings_rotation_heading))
    Spacer(Modifier.height(EditorSpacing.Space16))
    Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space8)) {
        listOf(0, 90, 180, 270).forEach { angle ->
            Choice(
                "$angle°",
                scene.rotation == angle,
                Modifier.weight(1f)
            ) { onSceneChange(scene.copy(rotation = angle)) }
        }
    }
    Spacer(Modifier.height(EditorSpacing.Space16))
    Text(
        stringResource(Res.string.settings_rotation_hint),
        color = Muted,
        style = editorTextStyle(EditorTypography.Label)
    )
    Spacer(Modifier.height(EditorSpacing.Space32))
    InfoCard(stringResource(Res.string.settings_editor_title), stringResource(Res.string.settings_editor_body))
    Spacer(Modifier.height(EditorSpacing.Space24))
    Text("PurrLCD 0.1", color = Muted, style = editorTextStyle(EditorTypography.Caption))
}
