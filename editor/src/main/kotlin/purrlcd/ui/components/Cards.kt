package purrlcd.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import purrlcd.resources.Res
import purrlcd.resources.sensor_unavailable
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Ink
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.SubtleInk
import purrlcd.ui.theme.WarningSurface
import purrlcd.ui.theme.editorTextStyle
import purrlcd.ui.theme.temp

@Composable
fun MetricCard(label: String, detail: String, value: Double?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = EditorShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(EditorSpacing.Space16)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Orange, style = editorTextStyle(EditorTypography.MetricLabel))
                Text(temp(value), style = editorTextStyle(EditorTypography.Temperature))
            }
            Spacer(Modifier.height(EditorSpacing.Space8))
            Text(detail, color = Muted, style = editorTextStyle(EditorTypography.Caption))
            if (value == null) {
                Spacer(Modifier.height(EditorSpacing.Space4)); Text(
                    stringResource(Res.string.sensor_unavailable),
                    color = SubtleInk,
                    style = editorTextStyle(EditorTypography.Hint)
                )
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = EditorSpacing.Space8),
        horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space16)
    ) {
        Text(label, Modifier.weight(1f), color = Muted, style = editorTextStyle(EditorTypography.Label))
        Text(
            value,
            Modifier.widthIn(max = EditorDimensions.DetailValueMaxWidth),
            style = editorTextStyle(EditorTypography.Label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun InfoCard(title: String, body: String, warning: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = EditorShapes.Card,
        colors = CardDefaults.cardColors(
            containerColor = if (warning) WarningSurface else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (warning) MaterialTheme.colorScheme.onPrimaryContainer else Ink
        )
    ) {
        Column(Modifier.padding(EditorSpacing.Space16)) {
            Text(title, style = editorTextStyle(EditorTypography.LabelMedium))
            Spacer(Modifier.height(EditorSpacing.Space8))
            Text(body, color = if (warning) MaterialTheme.colorScheme.onPrimaryContainer else Muted, style = editorTextStyle(EditorTypography.CardBody))
        }
    }
}
