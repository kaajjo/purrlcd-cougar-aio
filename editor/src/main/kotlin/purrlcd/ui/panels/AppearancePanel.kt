package purrlcd.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.editorTextStyle

@Composable
fun AppearancePanel(
    scene: Scene,
    selectedLayer: Int,
    onSelectLayer: (Int) -> Unit,
    onSceneChange: (Scene) -> Unit,
    onPickImage: () -> Unit
) {
    SectionLabel(stringResource(Res.string.appearance_background_heading))
    Spacer(Modifier.height(EditorSpacing.Space16))
    OutlinedButton(
        onClick = onPickImage,
        modifier = Modifier.fillMaxWidth(),
        shape = EditorShapes.Button,
        border = androidx.compose.foundation.BorderStroke(EditorDimensions.BorderWidth, Line),
        contentPadding = PaddingValues(EditorSpacing.Space12)
    ) {
        Text(stringResource(Res.string.appearance_choose_image), style = editorTextStyle(EditorTypography.Body))
    }
    if (scene.backgroundPath.isNotBlank()) {
        Spacer(Modifier.height(EditorSpacing.Space8))
        Text(
            File(scene.backgroundPath).name,
            color = Muted,
            style = editorTextStyle(EditorTypography.Caption),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = { onSceneChange(scene.copy(backgroundPath = "")) },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                stringResource(Res.string.appearance_remove_image),
                color = Muted,
                style = editorTextStyle(EditorTypography.Caption)
            )
        }
    } else Spacer(Modifier.height(EditorSpacing.Space12))
    ColorField(
        stringResource(Res.string.appearance_background_color),
        scene.backgroundColor
    ) { onSceneChange(scene.copy(backgroundColor = it)) }
    Spacer(Modifier.height(EditorSpacing.Space24)); Divider(color = Line); Spacer(Modifier.height(EditorSpacing.Space24))
    SectionLabel(stringResource(Res.string.appearance_layers_heading))
    Spacer(Modifier.height(EditorSpacing.Space16))
    Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space8)) {
        Choice("CPU", selectedLayer == 0, Modifier.weight(1f)) { onSelectLayer(0) }
        Choice("GPU", selectedLayer == 1, Modifier.weight(1f)) { onSelectLayer(1) }
    }
    val selected = if (selectedLayer == 0) scene.cpu else scene.gpu
    fun update(value: TextLayer) {
        onSceneChange(if (selectedLayer == 0) scene.copy(cpu = value) else scene.copy(gpu = value))
    }
    Spacer(Modifier.height(EditorSpacing.Space16))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(Res.string.appearance_show_on_screen), style = editorTextStyle(EditorTypography.Label))
        Switch(
            selected.enabled,
            { update(selected.copy(enabled = it)) },
            colors = SwitchDefaults.colors(checkedThumbColor = Orange, checkedTrackColor = Orange)
        )
    }
    Field(
        stringResource(Res.string.appearance_label),
        selected.label,
        { if (it.length <= 24) update(selected.copy(label = it)) })
    Spacer(Modifier.height(EditorSpacing.Space12))
    Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space12)) {
        NumberField("X", selected.x, TextLayer.CoordinateRange, Modifier.weight(1f)) { update(selected.copy(x = it)) }
        NumberField("Y", selected.y, TextLayer.CoordinateRange, Modifier.weight(1f)) { update(selected.copy(y = it)) }
    }
    Spacer(Modifier.height(EditorSpacing.Space12))
    NumberField(stringResource(Res.string.appearance_text_size), selected.fontSize, 12..120) {
        update(
            selected.copy(
                fontSize = it
            )
        )
    }
    Spacer(Modifier.height(EditorSpacing.Space12))
    ColorField(stringResource(Res.string.appearance_text_color), selected.color) { update(selected.copy(color = it)) }
    Spacer(Modifier.height(EditorSpacing.Space16))
    Text(
        stringResource(Res.string.appearance_coordinates_hint),
        color = Muted,
        style = editorTextStyle(EditorTypography.Caption)
    )
}
