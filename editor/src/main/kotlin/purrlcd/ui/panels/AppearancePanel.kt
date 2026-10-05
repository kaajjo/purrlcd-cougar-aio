package purrlcd.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.jetbrains.compose.resources.stringResource
import purrlcd.model.Scene
import purrlcd.model.TextLayer
import purrlcd.resources.Res
import purrlcd.resources.appearance_background_color
import purrlcd.resources.appearance_background_heading
import purrlcd.resources.appearance_choose_image
import purrlcd.resources.appearance_coordinates_hint
import purrlcd.resources.appearance_label
import purrlcd.resources.appearance_layers_heading
import purrlcd.resources.appearance_remove_image
import purrlcd.resources.appearance_show_on_screen
import purrlcd.resources.appearance_text_color
import purrlcd.resources.appearance_text_size
import purrlcd.ui.components.Choice
import purrlcd.ui.components.ColorField
import purrlcd.ui.components.Field
import purrlcd.ui.components.NumberField
import purrlcd.ui.components.SectionLabel
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange

@Composable
fun AppearancePanel(
    scene: Scene,
    selectedLayer: Int,
    onSelectLayer: (Int) -> Unit,
    onSceneChange: (Scene) -> Unit,
    onPickImage: () -> Unit
) {
    SectionLabel(stringResource(Res.string.appearance_background_heading))
    Spacer(Modifier.height(15.dp))
    OutlinedButton(onClick = onPickImage,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), contentPadding = PaddingValues(13.dp)) {
        Text(stringResource(Res.string.appearance_choose_image), fontSize = 13.sp)
    }
    if (scene.backgroundPath.isNotBlank()) {
        Spacer(Modifier.height(7.dp))
        Text(File(scene.backgroundPath).name, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { onSceneChange(scene.copy(backgroundPath = "")) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(Res.string.appearance_remove_image), color = Muted, fontSize = 11.sp) }
    } else Spacer(Modifier.height(12.dp))
    ColorField(stringResource(Res.string.appearance_background_color), scene.backgroundColor) { onSceneChange(scene.copy(backgroundColor = it)) }
    Spacer(Modifier.height(22.dp)); Divider(color = Line); Spacer(Modifier.height(22.dp))
    SectionLabel(stringResource(Res.string.appearance_layers_heading))
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("CPU", selectedLayer == 0, Modifier.weight(1f)) { onSelectLayer(0) }
        Choice("GPU", selectedLayer == 1, Modifier.weight(1f)) { onSelectLayer(1) }
    }
    val selected = if (selectedLayer == 0) scene.cpu else scene.gpu
    fun update(value: TextLayer) { onSceneChange(if (selectedLayer == 0) scene.copy(cpu = value) else scene.copy(gpu = value)) }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.appearance_show_on_screen), fontSize = 12.sp)
        Switch(selected.enabled, { update(selected.copy(enabled = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Orange, checkedTrackColor = Orange))
    }
    Field(stringResource(Res.string.appearance_label), selected.label, { if (it.length <= 24) update(selected.copy(label = it)) })
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField("X", selected.x, 0..719, Modifier.weight(1f)) { update(selected.copy(x = it)) }
        NumberField("Y", selected.y, 0..719, Modifier.weight(1f)) { update(selected.copy(y = it)) }
    }
    Spacer(Modifier.height(12.dp))
    NumberField(stringResource(Res.string.appearance_text_size), selected.fontSize, 12..120) { update(selected.copy(fontSize = it)) }
    Spacer(Modifier.height(12.dp))
    ColorField(stringResource(Res.string.appearance_text_color), selected.color) { update(selected.copy(color = it)) }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(Res.string.appearance_coordinates_hint), color = Muted, fontSize = 11.sp)
}
