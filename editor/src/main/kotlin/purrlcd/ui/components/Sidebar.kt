package purrlcd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import purrlcd.resources.Res
import purrlcd.resources.sidebar_editor
import purrlcd.resources.sidebar_engine_missing
import purrlcd.resources.sidebar_engine_running
import purrlcd.resources.sidebar_screen
import purrlcd.resources.sidebar_settings
import purrlcd.resources.sidebar_tagline
import purrlcd.ui.theme.Bg
import purrlcd.ui.theme.Good
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Panel

@Composable
fun Sidebar(page: Int, onPage: (Int) -> Unit, ready: Boolean) {
    Column(Modifier.width(187.dp).fillMaxHeight().background(Color(0xFF101318)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Orange), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(17.dp)) {
                    drawRoundRect(Bg, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), CornerRadius(4f), style = Stroke(2.3f))
                    drawLine(Bg, Offset(size.width * .35f, size.height * .55f), Offset(size.width * .7f, size.height * .55f), 2.3f, StrokeCap.Round)
                }
            }
            Text("purr\nlcd", fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 17.sp, letterSpacing = -.5.sp)
        }
        Spacer(Modifier.height(44.dp))
        listOf(stringResource(Res.string.sidebar_screen), stringResource(Res.string.sidebar_editor), stringResource(Res.string.sidebar_settings)).forEachIndexed { index, label ->
            val selected = index == page
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Color(0xFF2B241F) else Color.Transparent)
                .clickable { onPage(index) }.padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavIcon(index, if (selected) Orange else Muted)
                Text(label, color = if (selected) Orange else Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.weight(1f))
        Divider(color = Line)
        Spacer(Modifier.height(17.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(if (ready) Good else Muted)
            Text(if (ready) stringResource(Res.string.sidebar_engine_running) else stringResource(Res.string.sidebar_engine_missing), color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.sidebar_tagline), color = Color(0xFF626A79), fontSize = 9.sp)
    }
}

@Composable
private fun NavIcon(kind: Int, color: Color) {
    Canvas(Modifier.size(17.dp)) {
        val w = size.width; val h = size.height; val s = 1.5.dp.toPx()
        when (kind) {
            0 -> {
                drawRoundRect(color, Offset(1f, h * .1f), Size(w - 2f, h * .67f), CornerRadius(3f), style = Stroke(s))
                drawLine(color, Offset(w * .5f, h * .78f), Offset(w * .5f, h * .98f), s)
                drawLine(color, Offset(w * .3f, h * .98f), Offset(w * .7f, h * .98f), s, StrokeCap.Round)
            }
            1 -> {
                drawLine(color, Offset(w * .2f, h * .8f), Offset(w * .8f, h * .2f), s * 2, StrokeCap.Round)
                drawLine(color, Offset(w * .65f, h * .18f), Offset(w * .82f, h * .35f), s, StrokeCap.Round)
                drawLine(color, Offset(w * .15f, h * .9f), Offset(w * .85f, h * .9f), s, StrokeCap.Round)
            }
            else -> {
                repeat(3) { i ->
                    val y = h * (.2f + i * .3f)
                    drawLine(color, Offset(0f, y), Offset(w, y), s, StrokeCap.Round)
                    drawCircle(Panel, s * 2f, Offset(w * (if (i == 1) .7f else .3f), y))
                    drawCircle(color, s * 2f, Offset(w * (if (i == 1) .7f else .3f), y), style = Stroke(s))
                }
            }
        }
    }
}
