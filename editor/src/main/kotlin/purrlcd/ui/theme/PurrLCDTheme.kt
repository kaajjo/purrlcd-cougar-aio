package purrlcd.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PurrLCDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EditorColorScheme,
        typography = EditorMaterialTypography,
        shapes = EditorMaterialShapes
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Ink) {
            content()
        }
    }
}
