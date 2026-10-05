package purrlcd.ui.tooling

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import purrlcd.ui.components.Choice
import purrlcd.ui.components.ColorField
import purrlcd.ui.components.Dot
import purrlcd.ui.components.Field
import purrlcd.ui.components.InfoCard
import purrlcd.ui.components.MetricCard
import purrlcd.ui.components.NumberField
import purrlcd.ui.components.SectionLabel
import purrlcd.ui.components.Sidebar
import purrlcd.ui.components.SummaryRow
import purrlcd.ui.components.Tag
import purrlcd.ui.theme.Good
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange

@Preview(name = "Sidebar · online / offline", group = "Components", widthDp = 374, heightDp = 600)
@Composable
fun SidebarPreview() {
    var page by remember { mutableStateOf(0) }
    PreviewFrame(374.dp, 600.dp) {
        Row {
            Sidebar(page = page, onPage = { page = it }, ready = true)
            Sidebar(page = 1, onPage = {}, ready = false)
        }
    }
}

@Preview(name = "Metric cards · available / missing", group = "Components", widthDp = 326, heightDp = 310)
@Composable
fun MetricCardsPreview() {
    PreviewFrame(326.dp, 310.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("CPU", "CPU temperature", PreviewSamples.status.cpuTemp)
            MetricCard("GPU", "GPU temperature", null)
        }
    }
}

@Preview(name = "Information cards", group = "Components", widthDp = 326, heightDp = 440)
@Composable
fun InformationCardsPreview() {
    PreviewFrame(326.dp, 440.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryRow("Background", "Solid color")
            InfoCard("Ready", "The display keeps updating after the editor closes.")
            InfoCard("Application conflict", "Close the other LCD application before connecting.", warning = true)
        }
    }
}

@Preview(name = "Fields", group = "Components", widthDp = 326, heightDp = 500)
@Composable
fun FieldsPreview() {
    var label by remember { mutableStateOf("CPU") }
    var position by remember { mutableStateOf(40) }
    var color by remember { mutableStateOf("#FF9B54") }
    PreviewFrame(326.dp, 500.dp) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Label", label, onChange = { label = it })
            NumberField("X", position, 0..719, onValue = { position = it })
            // An out-of-range example makes the validation hint visible without typing.
            NumberField("Y · invalid", 720, 0..719, onValue = {})
            Column {
                ColorField("Text color", color, onValue = { color = it })
            }
        }
    }
}

@Preview(name = "Choices and status labels", group = "Components", widthDp = 326, heightDp = 220)
@Composable
fun ControlsPreview() {
    var selected by remember { mutableStateOf(0) }
    PreviewFrame(326.dp, 220.dp) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel("LAYERS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("CPU", selected == 0, Modifier.weight(1f)) { selected = 0 }
                Choice("GPU", selected == 1, Modifier.weight(1f)) { selected = 1 }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Dot(Good)
                Dot(Muted)
                Dot(Orange)
                Tag("720 × 720 · IPS", Muted)
            }
        }
    }
}
