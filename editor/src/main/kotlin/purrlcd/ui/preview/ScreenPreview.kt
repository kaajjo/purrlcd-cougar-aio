package purrlcd.ui.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import purrlcd.engine.EngineStatus
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.preview_background_description
import purrlcd.resources.preview_screen_description
import purrlcd.ui.theme.parseColor
import purrlcd.ui.theme.temp

@Composable
fun ScreenPreview(scene: Scene, status: EngineStatus, native: ImageBitmap?) {
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(scene.backgroundPath) {
        background = withContext(Dispatchers.IO) { if (scene.backgroundPath.isNotBlank()) loadImage(scene.backgroundPath) else null }
    }
    if (native != null) {
        Image(native, stringResource(Res.string.preview_screen_description), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().background(parseColor(scene.backgroundColor))) {
            background?.let { Image(it, stringResource(Res.string.preview_background_description), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            val scale = maxWidth.value / 720f
            listOf(scene.cpu to status.cpuTemp, scene.gpu to status.gpuTemp).forEach { (layer, value) ->
                if (layer.enabled) Text("${layer.label}  ${temp(value)}", modifier = Modifier.offset((layer.x * scale).dp, (layer.y * scale).dp),
                    color = parseColor(layer.color), fontSize = (layer.fontSize * scale).sp, lineHeight = (layer.fontSize * scale).sp)
            }
        }
    }
}
