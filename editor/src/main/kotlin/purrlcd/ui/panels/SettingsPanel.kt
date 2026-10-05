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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange

@Composable
fun SettingsPanel(scene: Scene, onSceneChange: (Scene) -> Unit) {
    SectionLabel(stringResource(Res.string.settings_refresh_heading))
    Spacer(Modifier.height(18.dp))
    Text(stringResource(Res.string.settings_refresh_interval, scene.intervalMs / 1000), fontSize = 25.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
    Slider(value = scene.intervalMs / 1000f, onValueChange = { onSceneChange(scene.copy(intervalMs = it.roundToInt() * 1000)) }, valueRange = 1f..5f, steps = 3,
        colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Line))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(Res.string.duration_seconds, 1), color = Muted, fontSize = 11.sp); Text(stringResource(Res.string.duration_seconds, 5), color = Muted, fontSize = 11.sp) }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(Res.string.settings_refresh_hint), color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(28.dp)); Divider(color = Line); Spacer(Modifier.height(26.dp))
    SectionLabel(stringResource(Res.string.settings_rotation_heading))
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(0, 90, 180, 270).forEach { angle -> Choice("$angle°", scene.rotation == angle, Modifier.weight(1f)) { onSceneChange(scene.copy(rotation = angle)) } }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(Res.string.settings_rotation_hint), color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(28.dp))
    InfoCard(stringResource(Res.string.settings_editor_title), stringResource(Res.string.settings_editor_body))
    Spacer(Modifier.height(24.dp))
    Text("PurrLCD 0.1", color = Muted, fontSize = 11.sp)
}
