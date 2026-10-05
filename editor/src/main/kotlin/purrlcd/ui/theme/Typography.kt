package purrlcd.ui.theme

import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Partial styles preserve inherited Material text defaults, including button typography. */
object EditorTypography {
    val Title = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
    val Value = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Medium)
    val Temperature = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light)
    val Body = TextStyle(fontSize = 13.sp)
    val BodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val NavigationSelected = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val Navigation = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontSize = 12.sp)
    val LabelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontSize = 11.sp)
    val Hint = TextStyle(fontSize = 10.sp)
    val FinePrint = TextStyle(fontSize = 9.sp)
    val CardBody = TextStyle(fontSize = 11.sp, lineHeight = 17.sp)
    val Section = TextStyle(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
    val PreviewHeading = TextStyle(fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
    val MetricLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val DeviceLabel = TextStyle(fontSize = 10.sp, letterSpacing = 0.8.sp)
    val Brand = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 17.sp, letterSpacing = -.5.sp)
    val Action = TextStyle(fontWeight = FontWeight.SemiBold)
}

@Composable
fun editorTextStyle(style: TextStyle): TextStyle = LocalTextStyle.current.merge(style)
