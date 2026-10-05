package purrlcd.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import purrlcd.engine.EngineStatus
import purrlcd.model.Scene
import purrlcd.resources.Res
import purrlcd.resources.preview_background_description
import purrlcd.resources.preview_screen_description
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.parseColor
import purrlcd.ui.theme.temp

@Composable
fun ScreenPreview(
    scene: Scene,
    status: EngineStatus,
    native: ImageBitmap?,
    selectedLayer: Int = 0,
    onSelectLayer: (Int) -> Unit = {},
    onSceneChange: ((Scene) -> Unit)? = null
) {
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(scene.backgroundPath) {
        background = withContext(Dispatchers.IO) {
            if (scene.backgroundPath.isNotBlank()) loadImage(scene.backgroundPath) else null
        }
    }
    val editable = onSceneChange != null
    if (native != null && !editable) {
        Image(native, stringResource(Res.string.preview_screen_description), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        return
    }

    val currentScene by rememberUpdatedState(scene)
    val currentOnChange by rememberUpdatedState(onSceneChange)
    val currentOnSelect by rememberUpdatedState(onSelectLayer)
    val textSizes = remember { mutableMapOf<Int, IntSize>() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val scale = side.value / PreviewCanvasSize
        val density = LocalDensity.current
        val pixelsPerScenePixel = with(density) { side.toPx() } / PreviewCanvasSize

        fun hitLayer(position: Offset): Int? = listOf(1, 0).firstOrNull { index ->
            val layer = if (index == 0) currentScene.cpu else currentScene.gpu
            val size = textSizes[index] ?: IntSize.Zero
            val left = layer.x * pixelsPerScenePixel
            val top = layer.y * pixelsPerScenePixel
            layer.enabled && position.x >= left && position.x <= left + size.width &&
                position.y >= top && position.y <= top + size.height
        }

        // Listen on the stationary canvas so moving the text cannot shift gesture coordinates.
        val gestures = if (editable && pixelsPerScenePixel > 0f) Modifier
            .pointerInput(pixelsPerScenePixel) {
                detectTapGestures { position -> hitLayer(position)?.let(currentOnSelect) }
            }
            .pointerInput(pixelsPerScenePixel) {
                var draggedLayer: Int? = null
                var dragPosition = PreviewDragPosition(0f, 0f)
                detectDragGestures(
                    orientationLock = null,
                    onDragStart = { down, _, _ ->
                        draggedLayer = hitLayer(down.position)
                        draggedLayer?.let { index ->
                            currentOnSelect(index)
                            val layer = if (index == 0) currentScene.cpu else currentScene.gpu
                            dragPosition = PreviewDragPosition(layer.x.toFloat(), layer.y.toFloat())
                        }
                    },
                    onDragEnd = { _ -> draggedLayer = null },
                    onDragCancel = { draggedLayer = null },
                    onDrag = { change, amount ->
                        draggedLayer?.let { index ->
                            change.consume()
                            dragPosition = dragPosition.move(amount.x, amount.y, pixelsPerScenePixel)
                            val latest = currentScene
                            val layer = if (index == 0) latest.cpu else latest.gpu
                            val moved = layer.copy(x = dragPosition.x.roundToInt(), y = dragPosition.y.roundToInt())
                            currentOnChange?.invoke(if (index == 0) latest.copy(cpu = moved) else latest.copy(gpu = moved))
                        }
                    }
                )
            } else Modifier

        Box(Modifier.size(side).clipToBounds().background(parseColor(scene.backgroundColor)).then(gestures)) {
            background?.let {
                Image(it, stringResource(Res.string.preview_background_description), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            listOf(scene.cpu to status.cpuTemp, scene.gpu to status.gpuTemp).forEachIndexed { index, (layer, value) ->
                if (layer.enabled) {
                    val selection = if (editable && selectedLayer == index)
                        Modifier.border(EditorDimensions.BorderWidth, Orange) else Modifier
                    Text(
                        "${layer.label}  ${temp(value)}",
                        modifier = Modifier.absoluteOffset {
                            IntOffset((layer.x * pixelsPerScenePixel).roundToInt(), (layer.y * pixelsPerScenePixel).roundToInt())
                        }.then(selection),
                        color = parseColor(layer.color),
                        fontSize = (layer.fontSize * scale).sp,
                        lineHeight = (layer.fontSize * scale).sp,
                        softWrap = false,
                        onTextLayout = { textSizes[index] = it.size }
                    )
                }
            }
        }
    }
}
