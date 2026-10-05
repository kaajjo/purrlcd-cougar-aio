package purrlcd.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

fun temp(value: Double?) = value?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}°" } ?: "—°"

fun parseColor(value: String) = runCatching { Color(0xFF000000L or value.removePrefix("#").toLong(16)) }.getOrDefault(Ink)
