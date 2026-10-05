package purrlcd.ui.panels

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted

@Composable
fun OverviewPanel(scene: Scene, status: EngineStatus, onEdit: () -> Unit) {
    SectionLabel(stringResource(Res.string.overview_screen_heading))
    Spacer(Modifier.height(20.dp))
    MetricCard("CPU", stringResource(Res.string.overview_cpu_temperature), status.cpuTemp)
    Spacer(Modifier.height(12.dp))
    MetricCard("GPU", stringResource(Res.string.overview_gpu_temperature), status.gpuTemp)
    Spacer(Modifier.height(24.dp))
    SectionLabel(stringResource(Res.string.overview_appearance_heading))
    Spacer(Modifier.height(14.dp))
    SummaryRow(
        stringResource(Res.string.overview_background),
        if (scene.backgroundPath.isBlank()) stringResource(Res.string.overview_solid_color) else File(scene.backgroundPath).name
    )
    SummaryRow(
        stringResource(Res.string.overview_refresh),
        stringResource(Res.string.duration_seconds, scene.intervalMs / 1000)
    )
    SummaryRow(stringResource(Res.string.overview_rotation), "${scene.rotation}°")
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) { Text(stringResource(Res.string.overview_edit_appearance), fontSize = 13.sp) }
    Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(14.dp)); Text(status.message, color = Muted, fontSize = 12.sp)
    }
    if (status.sensorStatus.isNotBlank()) {
        Spacer(Modifier.height(14.dp)); Text(status.sensorStatus, color = Muted, fontSize = 11.sp)
    }
}
