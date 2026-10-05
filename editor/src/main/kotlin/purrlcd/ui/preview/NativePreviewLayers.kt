package purrlcd.ui.preview

import androidx.compose.ui.graphics.ImageBitmap
import purrlcd.model.Scene
import purrlcd.model.TextLayer

/** Engine-rendered assets retain their text origins and can move without re-rendering. */
data class NativePreviewLayers(
    val scene: Scene,
    val background: ImageBitmap,
    val cpu: ImageBitmap,
    val gpu: ImageBitmap
) {
    fun matches(scene: Scene): Boolean =
        this.scene.backgroundPath == scene.backgroundPath && this.scene.backgroundColor == scene.backgroundColor &&
            sameTextAppearance(this.scene.cpu, scene.cpu) && sameTextAppearance(this.scene.gpu, scene.gpu)
}

internal fun sameTextAppearance(a: TextLayer, b: TextLayer): Boolean =
    a.label == b.label && a.fontSize == b.fontSize && a.color == b.color
