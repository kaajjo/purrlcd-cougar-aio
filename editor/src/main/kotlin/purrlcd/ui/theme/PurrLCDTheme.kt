package purrlcd.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF0D0F13)
val Panel = Color(0xFF15181E)
val Raised = Color(0xFF1D222B)
val Line = Color(0xFF2B303A)
val Ink = Color(0xFFF2F2F4)
val Muted = Color(0xFF969CA9)
val Orange = Color(0xFFFF9B54)
val Good = Color(0xFF87C9AA)
val Shape = RoundedCornerShape(14.dp)

@Composable
fun PurrLCDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(primary = Orange, secondary = Orange, background = Bg,
            surface = Panel, onPrimary = Bg, onSurface = Ink, onBackground = Ink),
        typography = Typography(defaultFontFamily = FontFamily.SansSerif)
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Ink) {
            content()
        }
    }
}
