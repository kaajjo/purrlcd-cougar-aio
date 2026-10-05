package purrlcd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import purrlcd.ui.theme.EditorDimensions
import purrlcd.ui.theme.EditorShapes
import purrlcd.ui.theme.EditorSpacing
import purrlcd.ui.theme.EditorTypography
import purrlcd.ui.theme.Ink
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Raised
import purrlcd.ui.theme.SceneColorPresets
import purrlcd.ui.theme.editorTextStyle
import purrlcd.ui.theme.parseColor

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    number: Boolean = false
) {
    Column(modifier) {
        Text(label, color = Muted, style = editorTextStyle(EditorTypography.Caption))
        Spacer(Modifier.height(EditorSpacing.Space8))
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth().height(EditorDimensions.FieldHeight), singleLine = true,
            textStyle = editorTextStyle(EditorTypography.Body), shape = EditorShapes.Field,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Raised, focusedBorderColor = Orange,
                unfocusedBorderColor = Line, cursorColor = Orange, textColor = Ink
            )
        )
    }
}

@Composable
fun NumberField(label: String, value: Int, range: IntRange, modifier: Modifier = Modifier, onValue: (Int) -> Unit) {
    var raw by remember(label) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (raw.toIntOrNull() != value) raw = value.toString() }
    Column(modifier) {
        Field(label, raw, { input ->
            if (input.length <= 4 && input.all(Char::isDigit)) {
                raw = input
                input.toIntOrNull()?.takeIf { it in range }?.let(onValue)
            }
        }, number = true)
        if (raw.toIntOrNull()?.let { it in range } != true) {
            Spacer(Modifier.height(EditorSpacing.Space4))
            Text("${range.first}–${range.last}", color = Orange, style = editorTextStyle(EditorTypography.Hint))
        }
    }
}

@Composable
fun ColorField(label: String, value: String, onValue: (String) -> Unit) {
    var raw by remember(label) { mutableStateOf(value) }
    LaunchedEffect(value) { raw = value }
    Text(label, color = Muted, style = editorTextStyle(EditorTypography.Caption))
    Spacer(Modifier.height(EditorSpacing.Space8))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space12)
    ) {
        Box(
            Modifier.size(EditorDimensions.ColorSwatchSize).clip(EditorShapes.ColorSwatch).background(parseColor(value))
                .border(EditorDimensions.BorderWidth, Line, EditorShapes.ColorSwatch)
        )
        OutlinedTextField(
            raw, {
                if (it.length <= 7) {
                    raw = it.uppercase(); if (raw.matches(Regex("#[0-9A-F]{6}"))) onValue(raw)
                }
            }, Modifier.weight(1f).height(EditorDimensions.FieldHeight), singleLine = true, shape = EditorShapes.Field,
            textStyle = editorTextStyle(EditorTypography.Label),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = Raised,
                focusedBorderColor = Orange,
                unfocusedBorderColor = Line,
                textColor = Ink
            )
        )
    }
    Spacer(Modifier.height(EditorSpacing.Space12))
    Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing.Space12)) {
        SceneColorPresets.forEach { color ->
            Box(
                Modifier.size(EditorDimensions.ColorPresetSize).clip(CircleShape).background(parseColor(color))
                    .border(
                        if (color == value) EditorDimensions.SelectedBorderWidth else EditorDimensions.BorderWidth,
                        if (color == value) Orange else Line,
                        CircleShape
                    )
                    .clickable { onValue(color) })
        }
    }
}
