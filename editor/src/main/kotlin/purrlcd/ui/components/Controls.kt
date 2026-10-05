package purrlcd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Panel
import purrlcd.ui.theme.Raised

@Composable
fun SectionLabel(text: String) { Text(text, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Bold) }

@Composable
fun Dot(color: Color, size: Int = 6) { Box(Modifier.size(size.dp).clip(CircleShape).background(color)) }

@Composable
fun Tag(label: String, color: Color) { Text(label, Modifier.clip(RoundedCornerShape(6.dp)).background(Panel).padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp) }

@Composable
fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF3A2B21) else Raised)
        .border(1.dp, if (selected) Orange.copy(alpha = .5f) else Line, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Orange else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
