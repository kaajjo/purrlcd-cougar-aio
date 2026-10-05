package purrlcd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import kotlin.math.roundToInt

private val Bg = Color(0xFF0D0F13)
private val Surface = Color(0xFF15181E)
private val Raised = Color(0xFF1D222B)
private val Line = Color(0xFF2B303A)
private val Ink = Color(0xFFF2F2F4)
private val Muted = Color(0xFF969CA9)
private val Orange = Color(0xFFFF9B54)
private val Good = Color(0xFF87C9AA)
private val Shape = RoundedCornerShape(14.dp)

private data class Options(val engine: File?, val data: File, val startEngine: Boolean)

private fun options(args: Array<String>): Options {
    fun arg(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val launch = System.getProperty("jpackage.app-path")?.let(::File)
    val root = launch?.parentFile?.parentFile ?: File(System.getProperty("user.dir"))
    val engine = arg("--engine")?.let(::File) ?: listOf(
        File(root, "engine/PurrLCD.exe"), File(root, "../engine/PurrLCD.exe"), File(root, "PurrLCD.exe")
    ).firstOrNull { it.isFile }
    return Options(engine, arg("--data")?.let(::File) ?: File(root, "data"), "--no-engine-start" !in args)
}

fun main(args: Array<String>) {
    val config = options(args)
    val client = EngineClient(config.engine, config.data)
    application {
        val state = rememberWindowState(width = 1280.dp, height = 850.dp)
        Window(
            onCloseRequest = { client.close(); exitApplication() },
            title = "PurrLCD · Экран водянки",
            state = state
        ) {
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(1120, 780) }
            MaterialTheme(
                colors = darkColors(primary = Orange, secondary = Orange, background = Bg,
                    surface = Surface, onPrimary = Bg, onSurface = Ink, onBackground = Ink),
                typography = Typography(defaultFontFamily = FontFamily.SansSerif)
            ) {
                Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Ink) {
                    Editor(client, config.startEngine, pickImage = {
                        val dialog = FileDialog(window, "Выберите изображение для экрана", FileDialog.LOAD)
                        dialog.file = "*.png;*.jpg;*.jpeg;*.bmp"
                        dialog.isVisible = true
                        val selected = dialog.file?.let { File(dialog.directory, it).absolutePath }
                        dialog.dispose()
                        selected
                    })
                }
            }
        }
    }
}

@Composable
private fun Editor(client: EngineClient, startEngine: Boolean, pickImage: () -> String?) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(0) }
    var layer by remember { mutableStateOf(0) }
    var scene by remember { mutableStateOf(Scene()) }
    var saved by remember { mutableStateOf(Scene()) }
    var status by remember { mutableStateOf(EngineStatus()) }
    var ready by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var nativePreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var note by remember { mutableStateOf("Подключаемся к фоновому движку…") }
    var noteIsError by remember { mutableStateOf(false) }
    val dirty = scene != saved

    fun showReply(reply: EngineReply, success: String) {
        reply.status?.let { status = it }
        noteIsError = !reply.ok
        note = if (reply.ok) success else reply.error ?: reply.message ?: "Не удалось выполнить действие"
    }

    fun action(command: String) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val current = scene
                if (command == "connect" && current != saved) {
                    val saveReply = client.request("saveScene", current)
                    if (!saveReply.ok) {
                        showReply(saveReply, "")
                        return@launch
                    }
                    val canonical = saveReply.scene ?: current
                    saved = canonical
                    if (scene == current) scene = canonical
                }
                val response = client.request(command, if (command == "saveScene") current else null)
                if (response.ok && command == "saveScene") {
                    val canonical = response.scene ?: current
                    saved = canonical
                    if (scene == current) scene = canonical
                }
                showReply(response, when (command) {
                    "saveScene" -> "Оформление сохранено"
                    "connect" -> if (response.status?.connected == true) "Экран подключён" else "Подключаем экран…"
                    else -> "Передача на экран остановлена"
                })
            } catch (_: Exception) {
                note = "Движок недоступен. Проверьте, что PurrLCD запущен."
                noteIsError = true
            } finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        var sceneLoaded = false
        note = if (startEngine) "Запускаем движок. Windows может запросить разрешение для доступа к датчикам."
            else "Подключаемся к фоновому движку…"
        val startup = if (startEngine) runCatching { client.ensureStarted() }.getOrElse {
            EngineStartup(false, "Не удалось запустить движок. Проверьте, что папка приложения доступна.")
        } else EngineStartup(runCatching { client.request("status").ok }.getOrDefault(false), "Фоновый движок недоступен. Предпросмотр доступен без подключения.")
        ready = startup.ready
        note = if (ready) "Готово. Настройте оформление и нажмите «Применить»." else startup.message
        noteIsError = !ready
        initialized = true
        while (isActive) {
            val wasReady = ready
            runCatching { client.request("status") }
                .onSuccess { reply -> ready = reply.ok; reply.status?.let { status = it } }
                .onFailure { ready = false }
            if (ready && !wasReady) {
                note = "Движок подключён. Можно применить оформление."
                noteIsError = false
            }
            if (ready && !sceneLoaded) {
                runCatching { client.request("getScene") }.onSuccess { reply ->
                    if (reply.ok && reply.scene != null) {
                        val editedDuringStartup = scene != saved
                        saved = reply.scene
                        if (!editedDuringStartup) scene = reply.scene
                        sceneLoaded = true
                    }
                    reply.status?.let { status = it }
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(scene, initialized, ready, status.cpuTemp?.roundToInt(), status.gpuTemp?.roundToInt()) {
        if (!initialized || !ready) { nativePreview = null; return@LaunchedEffect }
        delay(250)
        runCatching { client.request("preview", scene) }.onSuccess { reply ->
            if (reply.ok) {
                reply.previewPath?.let { path ->
                    nativePreview = withContext(Dispatchers.IO) { loadImage(path) }
                }
            } else {
                nativePreview = null
                note = reply.error ?: reply.message ?: "Не удалось подготовить предпросмотр"
                noteIsError = true
            }
        }
    }

    Row(Modifier.fillMaxSize().background(Bg)) {
        Sidebar(page, { page = it }, ready)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 25.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(listOf("Ваш экран", "Редактор оформления", "Настройки экрана")[page], fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(listOf("Маленький экран. Только нужное.", "Создайте своё оформление, слой за слоем.", "Ритм обновления и ориентация изображения.")[page], color = Muted, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (dirty) Text("Есть изменения", color = Orange, fontSize = 12.sp)
                    Button(onClick = { action("saveScene") }, enabled = ready && !busy && dirty,
                        shape = RoundedCornerShape(10.dp), elevation = ButtonDefaults.elevation(0.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                        Text("Применить", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Divider(color = Line)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("ПРЕДПРОСМОТР", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        Tag("720 × 720  ·  IPS", Muted)
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        val previewSize = minOf(maxWidth, maxHeight)
                        Box(Modifier.size(previewSize).padding(12.dp)
                            .clip(RoundedCornerShape(28.dp)).background(Color.Black)
                            .border(1.dp, Color(0xFF363C47), RoundedCornerShape(28.dp))) {
                            ScreenPreview(scene, status, nativePreview)
                        }
                    }
                    Text(if (nativePreview != null) "Изображение от движка · масштабировано для просмотра" else "Макет · точный предпросмотр появится при запуске движка", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth().clip(Shape).background(Surface).padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Dot(if (status.connected && ready) Good else Muted)
                            Column {
                                Text(if (status.connecting) "Подключаем экран…" else if (status.connected && ready) "Экран работает" else "Передача остановлена", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("POSEIDON VISTEK PRO", color = Muted, fontSize = 10.sp, letterSpacing = 0.8.sp)
                            }
                        }
                        OutlinedButton(onClick = { action(if (status.connected) "disconnect" else "connect") },
                            enabled = ready && !busy && !status.connecting, shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                            Text(if (status.connected) "Остановить" else "Подключить", fontSize = 12.sp)
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Line))
                Column(Modifier.width(326.dp).fillMaxHeight().background(Surface).verticalScroll(rememberScrollState()).padding(24.dp)) {
                    when (page) {
                        0 -> {
                            SectionLabel("СЕЙЧАС НА ЭКРАНЕ")
                            Spacer(Modifier.height(20.dp))
                            MetricCard("CPU", "Температура процессора", status.cpuTemp)
                            Spacer(Modifier.height(12.dp))
                            MetricCard("GPU", "Температура видеокарты", status.gpuTemp)
                            Spacer(Modifier.height(24.dp))
                            SectionLabel("ОФОРМЛЕНИЕ")
                            Spacer(Modifier.height(14.dp))
                            SummaryRow("Фон", if (scene.backgroundPath.isBlank()) "Сплошной цвет" else File(scene.backgroundPath).name)
                            SummaryRow("Обновление", "${scene.intervalMs / 1000} сек")
                            SummaryRow("Поворот", "${scene.rotation}°")
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { page = 1 }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) { Text("Изменить оформление", fontSize = 13.sp) }
                            Spacer(Modifier.height(24.dp))
                            if (status.stockRunning) InfoCard("COUGAR LCD Editor запущен", "Закройте штатную программу перед подключением, чтобы программы не отправляли данные одновременно.", true)
                            else InfoCard("Работает самостоятельно", "После закрытия этого окна фоновый движок продолжит обновлять экран.")
                            if (status.message.isNotBlank()) {
                                Spacer(Modifier.height(14.dp)); Text(status.message, color = Muted, fontSize = 12.sp)
                            }
                            if (status.sensorStatus.isNotBlank()) {
                                Spacer(Modifier.height(14.dp)); Text(status.sensorStatus, color = Muted, fontSize = 11.sp)
                            }
                        }
                        1 -> {
                            SectionLabel("ФОН")
                            Spacer(Modifier.height(15.dp))
                            OutlinedButton(onClick = { pickImage()?.let { scene = scene.copy(backgroundPath = it) } },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), contentPadding = PaddingValues(13.dp)) {
                                Text("Выбрать изображение", fontSize = 13.sp)
                            }
                            if (scene.backgroundPath.isNotBlank()) {
                                Spacer(Modifier.height(7.dp))
                                Text(File(scene.backgroundPath).name, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { scene = scene.copy(backgroundPath = "") }, contentPadding = PaddingValues(0.dp)) { Text("Убрать изображение", color = Muted, fontSize = 11.sp) }
                            } else Spacer(Modifier.height(12.dp))
                            ColorField("Цвет фона", scene.backgroundColor) { scene = scene.copy(backgroundColor = it) }
                            Spacer(Modifier.height(22.dp)); Divider(color = Line); Spacer(Modifier.height(22.dp))
                            SectionLabel("СЛОИ ПОКАЗАТЕЛЕЙ")
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Choice("CPU", layer == 0, Modifier.weight(1f)) { layer = 0 }
                                Choice("GPU", layer == 1, Modifier.weight(1f)) { layer = 1 }
                            }
                            val selected = if (layer == 0) scene.cpu else scene.gpu
                            fun update(value: TextLayer) { scene = if (layer == 0) scene.copy(cpu = value) else scene.copy(gpu = value) }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Показывать на экране", fontSize = 12.sp)
                                Switch(selected.enabled, { update(selected.copy(enabled = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Orange, checkedTrackColor = Orange))
                            }
                            Field("Подпись", selected.label, { if (it.length <= 24) update(selected.copy(label = it)) })
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NumberField("X", selected.x, 0..719, Modifier.weight(1f)) { update(selected.copy(x = it)) }
                                NumberField("Y", selected.y, 0..719, Modifier.weight(1f)) { update(selected.copy(y = it)) }
                            }
                            Spacer(Modifier.height(12.dp))
                            NumberField("Размер текста, px", selected.fontSize, 12..120) { update(selected.copy(fontSize = it)) }
                            Spacer(Modifier.height(12.dp))
                            ColorField("Цвет текста", selected.color) { update(selected.copy(color = it)) }
                            Spacer(Modifier.height(18.dp))
                            Text("Координаты задаются в пикселях от левого верхнего угла экрана.", color = Muted, fontSize = 11.sp)
                        }
                        2 -> {
                            SectionLabel("ОБНОВЛЕНИЕ")
                            Spacer(Modifier.height(18.dp))
                            Text("Раз в ${scene.intervalMs / 1000} сек", fontSize = 25.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Slider(value = scene.intervalMs / 1000f, onValueChange = { scene = scene.copy(intervalMs = it.roundToInt() * 1000) }, valueRange = 1f..5f, steps = 3,
                                colors = SliderDefaults.colors(thumbColor = Orange, activeTrackColor = Orange, inactiveTrackColor = Line))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1 сек", color = Muted, fontSize = 11.sp); Text("5 сек", color = Muted, fontSize = 11.sp) }
                            Spacer(Modifier.height(18.dp))
                            Text("Более редкое обновление уменьшает количество обращений к датчикам и экрану.", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(28.dp)); Divider(color = Line); Spacer(Modifier.height(26.dp))
                            SectionLabel("ПОВОРОТ ЭКРАНА")
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(0, 90, 180, 270).forEach { angle -> Choice("$angle°", scene.rotation == angle, Modifier.weight(1f)) { scene = scene.copy(rotation = angle) } }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Выберите ориентацию под положение водоблока. Предпросмотр показывает оформление прямо.", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(28.dp))
                            InfoCard("Редактор открывается по требованию", "После закрытия окна редактор полностью выгружается из памяти. Для работы экрана нужен только фоновый движок.")
                            Spacer(Modifier.height(24.dp))
                            Text("PurrLCD 0.1", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Divider(color = Line)
            Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Dot(if (noteIsError) Orange else Good, 5)
                Text(note, color = if (noteIsError) Orange else Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Sidebar(page: Int, onPage: (Int) -> Unit, ready: Boolean) {
    Column(Modifier.width(187.dp).fillMaxHeight().background(Color(0xFF101318)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Orange), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(17.dp)) {
                    drawRoundRect(Bg, Offset(2f, 2f), Size(size.width - 4f, size.height - 4f), CornerRadius(4f), style = Stroke(2.3f))
                    drawLine(Bg, Offset(size.width * .35f, size.height * .55f), Offset(size.width * .7f, size.height * .55f), 2.3f, StrokeCap.Round)
                }
            }
            Text("purr\nlcd", fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 17.sp, letterSpacing = -.5.sp)
        }
        Spacer(Modifier.height(44.dp))
        listOf("Экран", "Редактор", "Настройки").forEachIndexed { index, label ->
            val selected = index == page
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Color(0xFF2B241F) else Color.Transparent)
                .clickable { onPage(index) }.padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NavIcon(index, if (selected) Orange else Muted)
                Text(label, color = if (selected) Orange else Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.weight(1f))
        Divider(color = Line)
        Spacer(Modifier.height(17.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(if (ready) Good else Muted)
            Text(if (ready) "Движок запущен" else "Движок не найден", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Экран живёт своим ритмом", color = Color(0xFF626A79), fontSize = 9.sp)
    }
}

@Composable
private fun NavIcon(kind: Int, color: Color) {
    Canvas(Modifier.size(17.dp)) {
        val w = size.width; val h = size.height; val s = 1.5.dp.toPx()
        when (kind) {
            0 -> {
                drawRoundRect(color, Offset(1f, h * .1f), Size(w - 2f, h * .67f), CornerRadius(3f), style = Stroke(s))
                drawLine(color, Offset(w * .5f, h * .78f), Offset(w * .5f, h * .98f), s)
                drawLine(color, Offset(w * .3f, h * .98f), Offset(w * .7f, h * .98f), s, StrokeCap.Round)
            }
            1 -> {
                drawLine(color, Offset(w * .2f, h * .8f), Offset(w * .8f, h * .2f), s * 2, StrokeCap.Round)
                drawLine(color, Offset(w * .65f, h * .18f), Offset(w * .82f, h * .35f), s, StrokeCap.Round)
                drawLine(color, Offset(w * .15f, h * .9f), Offset(w * .85f, h * .9f), s, StrokeCap.Round)
            }
            else -> {
                repeat(3) { i ->
                    val y = h * (.2f + i * .3f)
                    drawLine(color, Offset(0f, y), Offset(w, y), s, StrokeCap.Round)
                    drawCircle(Surface, s * 2f, Offset(w * (if (i == 1) .7f else .3f), y))
                    drawCircle(color, s * 2f, Offset(w * (if (i == 1) .7f else .3f), y), style = Stroke(s))
                }
            }
        }
    }
}

private fun loadImage(path: String): ImageBitmap? = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap()
}.getOrNull()

@Composable
private fun ScreenPreview(scene: Scene, status: EngineStatus, native: ImageBitmap?) {
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(scene.backgroundPath) {
        background = withContext(Dispatchers.IO) { if (scene.backgroundPath.isNotBlank()) loadImage(scene.backgroundPath) else null }
    }
    if (native != null) {
        Image(native, "Предпросмотр экрана водянки", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().background(parseColor(scene.backgroundColor))) {
            background?.let { Image(it, "Фон экрана", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            val scale = maxWidth.value / 720f
            listOf(scene.cpu to status.cpuTemp, scene.gpu to status.gpuTemp).forEach { (layer, value) ->
                if (layer.enabled) Text("${layer.label}  ${temp(value)}", modifier = Modifier.offset((layer.x * scale).dp, (layer.y * scale).dp),
                    color = parseColor(layer.color), fontSize = (layer.fontSize * scale).sp, lineHeight = (layer.fontSize * scale).sp)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, detail: String, value: Double?) {
    Column(Modifier.fillMaxWidth().clip(Shape).background(Raised).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(temp(value), fontSize = 28.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(7.dp))
        Text(detail, color = Muted, fontSize = 11.sp)
        if (value == null) { Spacer(Modifier.height(5.dp)); Text("Датчик пока недоступен", color = Color(0xFF6D7584), fontSize = 10.sp) }
    }
}

private fun temp(value: Double?) = value?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}°" } ?: "—°"
private fun parseColor(value: String) = runCatching { Color(0xFF000000L or value.removePrefix("#").toLong(16)) }.getOrDefault(Ink)

@Composable
private fun SectionLabel(text: String) { Text(text, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Bold) }

@Composable
private fun Dot(color: Color, size: Int = 6) { Box(Modifier.size(size.dp).clip(CircleShape).background(color)) }

@Composable
private fun Tag(label: String, color: Color) { Text(label, Modifier.clip(RoundedCornerShape(6.dp)).background(Surface).padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp) }

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 12.sp)
        Text(value, Modifier.widthIn(max = 155.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoCard(title: String, body: String, warning: Boolean = false) {
    Column(Modifier.fillMaxWidth().clip(Shape).background(if (warning) Color(0xFF29231E) else Raised).padding(16.dp)) {
        Text(title, color = if (warning) Orange else Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF3A2B21) else Raised)
        .border(1.dp, if (selected) Orange.copy(alpha = .5f) else Line, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Orange else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, number: Boolean = false) {
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
private fun NumberField(label: String, value: Int, range: IntRange, modifier: Modifier = Modifier, onValue: (Int) -> Unit) {
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
private fun ColorField(label: String, value: String, onValue: (String) -> Unit) {
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
