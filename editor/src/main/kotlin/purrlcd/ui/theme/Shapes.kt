package purrlcd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object EditorShapes {
    val Tag = RoundedCornerShape(50)
    val ColorSwatch = RoundedCornerShape(7.dp)
    val Field = RoundedCornerShape(12.dp)
    val CompactButton = RoundedCornerShape(50)
    val Button = RoundedCornerShape(50)
    val Card = RoundedCornerShape(20.dp)
    val Preview = RoundedCornerShape(28.dp)
}

val EditorMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
    medium = EditorShapes.Field, large = EditorShapes.Card, extraLarge = EditorShapes.Preview
)
