package purrlcd.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

@Composable
fun PurrLCDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = darkColors(
            primary = Orange, secondary = Orange, background = Bg,
            surface = Panel, onPrimary = Bg, onSurface = Ink, onBackground = Ink
        ),
        typography = Typography(defaultFontFamily = FontFamily.SansSerif)
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Ink) {
            content()
        }
    }
}
