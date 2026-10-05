package purrlcd.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val EditorMaterialTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

/** Compact desktop roles drawn from the same type scale as Material components. */
object EditorTypography {
    val Title = EditorMaterialTypography.headlineMedium
    val Value = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Medium)
    val Temperature = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light)
    val Body = EditorMaterialTypography.bodyMedium
    val BodyMedium = EditorMaterialTypography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    val NavigationSelected = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val Navigation = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val Label = EditorMaterialTypography.bodySmall
    val LabelMedium = EditorMaterialTypography.labelMedium
    val Caption = EditorMaterialTypography.bodySmall
    val Hint = EditorMaterialTypography.labelSmall
    val FinePrint = EditorMaterialTypography.labelSmall
    val CardBody = EditorMaterialTypography.bodySmall.copy(lineHeight = 18.sp)
    val Section = TextStyle(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
    val PreviewHeading = TextStyle(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
    val MetricLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val DeviceLabel = TextStyle(fontSize = 10.sp, letterSpacing = 0.8.sp)
    val Brand = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 17.sp, letterSpacing = -.5.sp)
    val Action = TextStyle(fontWeight = FontWeight.SemiBold)
}

@Composable
fun editorTextStyle(style: TextStyle): TextStyle = LocalTextStyle.current.merge(style)
