package purrlcd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Panel
import purrlcd.ui.theme.Raised
import purrlcd.ui.theme.SelectedBorder
import purrlcd.ui.theme.SelectedSurface
import purrlcd.ui.theme.editorTextStyle

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Muted, style = editorTextStyle(EditorTypography.Section))
}

@Composable
fun Dot(color: Color, size: Int = 6) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun Tag(label: String, color: Color) {
    Text(label,
        Modifier.clip(EditorShapes.Tag).background(Panel)
            .padding(horizontal = EditorSpacing.Space8, vertical = EditorSpacing.Space4),
        color = color,
        style = editorTextStyle(EditorTypography.Hint)
    )
}

@Composable
fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(EditorShapes.Field).background(if (selected) SelectedSurface else Raised)
            .border(EditorDimensions.BorderWidth, if (selected) SelectedBorder else Line, EditorShapes.Field)
            .clickable(onClick = onClick).padding(vertical = EditorSpacing.Space12), contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Orange else Muted, style = editorTextStyle(EditorTypography.LabelMedium))
    }
}
