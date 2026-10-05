package purrlcd.ui.preview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File

fun loadImage(path: String): ImageBitmap? = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap()
}.getOrNull()
