package purrlcd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import purrlcd.resources.Res
import purrlcd.resources.sensor_unavailable
import purrlcd.ui.theme.Ink
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Raised
import purrlcd.ui.theme.Shape
import purrlcd.ui.theme.temp

@Composable
fun MetricCard(label: String, detail: String, value: Double?) {
    Column(Modifier.fillMaxWidth().clip(Shape).background(Raised).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(temp(value), fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(7.dp))
        Text(detail, color = Muted, fontSize = 11.sp)
        if (value == null) { Spacer(Modifier.height(5.dp)); Text(stringResource(Res.string.sensor_unavailable), color = Color(0xFF6D7584), fontSize = 10.sp) }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 12.sp)
        Text(value, Modifier.widthIn(max = 155.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun InfoCard(title: String, body: String, warning: Boolean = false) {
    Column(Modifier.fillMaxWidth().clip(Shape).background(if (warning) Color(0xFF29231E) else Raised).padding(16.dp)) {
        Text(title, color = if (warning) Orange else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}
