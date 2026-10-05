package purrlcd.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import purrlcd.ui.theme.Ink
import purrlcd.ui.theme.Line
import purrlcd.ui.theme.Muted
import purrlcd.ui.theme.Orange
import purrlcd.ui.theme.Raised
import purrlcd.ui.theme.parseColor

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, number: Boolean = false) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(value, onChange, Modifier.fillMaxWidth().height(49.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp), shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = Raised, focusedBorderColor = Orange,
                unfocusedBorderColor = Line, cursorColor = Orange, textColor = Ink))
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
            Spacer(Modifier.height(4.dp))
            Text("${range.first}–${range.last}", color = Orange, fontSize = 10.sp)
        }
    }
}

@Composable
fun ColorField(label: String, value: String, onValue: (String) -> Unit) {
    var raw by remember(label) { mutableStateOf(value) }
    LaunchedEffect(value) { raw = value }
    Text(label, color = Muted, fontSize = 11.sp)
    Spacer(Modifier.height(7.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(parseColor(value)).border(1.dp, Line, RoundedCornerShape(7.dp)))
        OutlinedTextField(raw, {
            if (it.length <= 7) { raw = it.uppercase(); if (raw.matches(Regex("#[0-9A-F]{6}"))) onValue(raw) }
        }, Modifier.weight(1f).height(49.dp), singleLine = true, shape = RoundedCornerShape(8.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = Raised, focusedBorderColor = Orange, unfocusedBorderColor = Line, textColor = Ink))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("#FFFFFF", "#FF9B54", "#87C9AA", "#88B9EE", "#111318", "#000000").forEach { color ->
            Box(Modifier.size(22.dp).clip(CircleShape).background(parseColor(color))
                .border(if (color == value) 2.dp else 1.dp, if (color == value) Orange else Line, CircleShape)
                .clickable { onValue(color) })
        }
    }
}
