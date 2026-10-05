package purrlcd.ui.preview

import purrlcd.model.TextLayer

internal const val PreviewCanvasSize = 720f

/** Retain fractional scene pixels across drag events, including on scaled/HiDPI previews. */
internal data class PreviewDragPosition(val x: Float, val y: Float) {
    fun move(deltaX: Float, deltaY: Float, pixelsPerScenePixel: Float): PreviewDragPosition {
        require(pixelsPerScenePixel > 0f && pixelsPerScenePixel.isFinite())
        return PreviewDragPosition(
            (x + deltaX / pixelsPerScenePixel).coerceIn(TextLayer.CoordinateRange.first.toFloat(), TextLayer.CoordinateRange.last.toFloat()),
            (y + deltaY / pixelsPerScenePixel).coerceIn(TextLayer.CoordinateRange.first.toFloat(), TextLayer.CoordinateRange.last.toFloat())
        )
    }
}
