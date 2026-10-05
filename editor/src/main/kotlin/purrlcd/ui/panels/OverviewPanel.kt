package purrlcd.ui.panels

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File
import org.jetbrains.compose.resources.stringResource
import purrlcd.engine.EngineStatus
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.duration_seconds
import purrlcd.resources.overview_appearance_heading
import purrlcd.resources.overview_background
import purrlcd.resources.overview_cpu_temperature
import purrlcd.resources.overview_edit_appearance
import purrlcd.resources.overview_gpu_temperature
import purrlcd.resources.overview_independent_body
import purrlcd.resources.overview_independent_title
import purrlcd.resources.overview_refresh
import purrlcd.resources.overview_rotation
import purrlcd.resources.overview_screen_heading
import purrlcd.resources.overview_solid_color
import purrlcd.resources.overview_stock_running_body
import purrlcd.resources.overview_stock_running_title
import purrlcd.ui.components.InfoCard
import purrlcd.ui.components.MetricCard
import purrlcd.ui.components.SectionLabel
import purrlcd.ui.components.SummaryRow
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.editorTextStyle

@Composable
fun OverviewPanel(scene: Scene, status: EngineStatus, onEdit: () -> Unit) {
    SectionLabel(stringResource(Res.string.overview_screen_heading))
    Spacer(Modifier.height(EditorSpacing.Space24))
    MetricCard("CPU", stringResource(Res.string.overview_cpu_temperature), status.cpuTemp)
    Spacer(Modifier.height(EditorSpacing.Space12))
    MetricCard("GPU", stringResource(Res.string.overview_gpu_temperature), status.gpuTemp)
    Spacer(Modifier.height(EditorSpacing.Space24))
    SectionLabel(stringResource(Res.string.overview_appearance_heading))
    Spacer(Modifier.height(EditorSpacing.Space16))
    SummaryRow(
        stringResource(Res.string.overview_background),
        if (scene.backgroundPath.isBlank()) stringResource(Res.string.overview_solid_color) else File(scene.backgroundPath).name
    )
    SummaryRow(
        stringResource(Res.string.overview_refresh),
        stringResource(Res.string.duration_seconds, scene.intervalMs / 1000)
    )
    SummaryRow(stringResource(Res.string.overview_rotation), "${scene.rotation}°")
    Spacer(Modifier.height(EditorSpacing.Space12))
    OutlinedButton(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = EditorShapes.Button,
        border = androidx.compose.foundation.BorderStroke(EditorDimensions.BorderWidth, Line)
    ) { Text(stringResource(Res.string.overview_edit_appearance), style = editorTextStyle(EditorTypography.Body)) }
    Spacer(Modifier.height(EditorSpacing.Space24))
    if (status.stockRunning) InfoCard(
        stringResource(Res.string.overview_stock_running_title),
        stringResource(Res.string.overview_stock_running_body),
        true
    )
    else InfoCard(
        stringResource(Res.string.overview_independent_title),
        stringResource(Res.string.overview_independent_body)
    )
    if (status.message.isNotBlank()) {
        Spacer(Modifier.height(EditorSpacing.Space16)); Text(
            status.message,
            color = Muted,
            style = editorTextStyle(EditorTypography.Label)
        )
    }
    if (status.sensorStatus.isNotBlank()) {
        Spacer(Modifier.height(EditorSpacing.Space16)); Text(
            status.sensorStatus,
            color = Muted,
            style = editorTextStyle(EditorTypography.Caption)
        )
    }
}
