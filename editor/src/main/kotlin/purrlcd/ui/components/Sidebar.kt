package purrlcd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import purrlcd.resources.Res
import purrlcd.resources.sidebar_editor
import purrlcd.resources.sidebar_engine_missing
import purrlcd.resources.sidebar_engine_running
import purrlcd.resources.sidebar_screen
import purrlcd.resources.sidebar_settings
import purrlcd.ui.theme.Bg
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Good
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Panel
import purrlcd.ui.theme.SidebarSurface
import purrlcd.ui.theme.editorTextStyle

@Composable
fun Sidebar(page: Int, onPage: (Int) -> Unit, ready: Boolean) {
    Column(
        Modifier.width(EditorDimensions.SidebarWidth).fillMaxHeight().background(SidebarSurface)
            .padding(EditorSpacing.Space24)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space12)
        ) {
            Box(
                Modifier.size(30.dp).clip(EditorShapes.CompactButton).background(Orange),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(EditorDimensions.IconSize)) {
                    drawRoundRect(
                        Bg,
                        Offset(2f, 2f),
                        Size(size.width - 4f, size.height - 4f),
                        CornerRadius(4f),
                        style = Stroke(2.3f)
                    )
                    drawLine(
                        Bg,
                        Offset(size.width * .35f, size.height * .55f),
                        Offset(size.width * .7f, size.height * .55f),
                        2.3f,
                        StrokeCap.Round
                    )
                }
            }
            Text("purr\nlcd", style = editorTextStyle(EditorTypography.Brand))
        }
        Spacer(Modifier.height(EditorSpacing.Space48))
        listOf(
            stringResource(Res.string.sidebar_screen),
            stringResource(Res.string.sidebar_editor),
            stringResource(Res.string.sidebar_settings)
        ).forEachIndexed { index, label ->
            val selected = index == page
            NavigationDrawerItem(
                selected = selected,
                onClick = { onPage(index) },
                label = { Text(label, style = editorTextStyle(EditorTypography.Navigation), maxLines = 1) },
                icon = { NavIcon(index, LocalContentColor.current) },
                colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
            )
            Spacer(Modifier.height(EditorSpacing.Space8))
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(EditorSpacing.Space16))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space8)
        ) {
            Dot(if (ready) Good else Muted)
            Text(
                if (ready) stringResource(Res.string.sidebar_engine_running) else stringResource(Res.string.sidebar_engine_missing),
                color = Muted,
                style = editorTextStyle(EditorTypography.Hint)
            )
        }
    }
}

@Composable
private fun NavIcon(kind: Int, color: Color) {
    Canvas(Modifier.size(EditorDimensions.IconSize)) {
        val w = size.width;
        val h = size.height;
        val s = EditorDimensions.IconStroke.toPx()
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
