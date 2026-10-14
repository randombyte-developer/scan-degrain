import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.onClick
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_REDUCED_COLOR_2
import org.bytedeco.opencv.global.opencv_imgcodecs.IMREAD_REDUCED_COLOR_8
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections

class Group(val year: Int, val month: Int?, val day: Int?) {
    val photos = mutableStateListOf<File>()
    val date: LocalDate = LocalDate.of(year, month ?: 1, day ?: 1)
    val label = "%04d".format(year) + month?.let { "-%02d".format(it) }.orEmpty() + day?.let { "-%02d".format(it) }.orEmpty()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DatumTab(output: File?, thumbnails: MutableMap<File, ImageBitmap>) {
    if (output == null) {
        Text("Kein Ausgabeordner gewählt (Tab Batch)", Modifier.padding(16.dp))
        return
    }
    val grid = remember(output) { mutableStateListOf<File>() }
    val groups = remember(output) { mutableStateListOf<Group>() }
    val onDisk = remember(output) { mutableMapOf<File, LocalDateTime?>() }
    var selectedGroup by remember(output) { mutableStateOf<Group?>(null) }
    var marked by remember(output) { mutableStateOf(setOf<File>()) }
    var markedIn by remember(output) { mutableStateOf<List<File>?>(null) }
    var anchor by remember(output) { mutableStateOf<File?>(null) }
    var preview by remember(output) { mutableStateOf<File?>(null) }
    var status by remember(output) { mutableStateOf("") }
    var applying by remember(output) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(output) {
        status = "Lese EXIF …"
        val dates = withContext(Dispatchers.IO) { jpgs(output).associateWith(::readDate) }
        onDisk.putAll(dates)
        grid += dates.filterValues { it == null }.keys
        dates.entries.filter { it.value != null }.sortedBy { it.value }.groupBy { it.value!!.toLocalDate() }
            .forEach { (date, entries) ->
                groups += Group(date.year, date.monthValue, date.dayOfMonth).apply { photos += entries.map { it.key } }
            }
        status = ""
        focus.requestFocus()
    }

    fun click(list: List<File>, file: File, ctrl: Boolean, shift: Boolean) {
        focus.requestFocus()
        if (list !== markedIn) {
            marked = emptySet()
            markedIn = list
        }
        val a = anchor
        marked = when {
            shift && a != null && a in list -> {
                val (i, j) = listOf(list.indexOf(a), list.indexOf(file)).sorted()
                list.subList(i, j + 1).toSet()
            }
            ctrl -> if (file in marked) marked - file else marked + file
            else -> setOf(file)
        }
        anchor = file
    }

    fun Modifier.selectable(list: List<File>, file: File) = this
        .onClick(keyboardModifiers = { isCtrlPressed }) { click(list, file, ctrl = true, shift = false) }
        .onClick(keyboardModifiers = { isShiftPressed }) { click(list, file, ctrl = false, shift = true) }
        .onClick(keyboardModifiers = { !isCtrlPressed && !isShiftPressed }, onDoubleClick = { preview = file }) {
            click(list, file, ctrl = false, shift = false)
        }

    fun backToGrid(files: Collection<File>) {
        grid += files
        grid.sortBy { it.name }
    }

    val markedGroup = groups.find { it.photos === markedIn }

    fun move(delta: Int) {
        val list = markedGroup!!.photos
        val indices = if (delta < 0) list.indices else list.indices.reversed()
        for (i in indices) {
            val j = i + delta
            if (list[i] in marked && j in list.indices && list[j] !in marked) Collections.swap(list, i, j)
        }
    }

    Box(
        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent {
            if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (it.key) {
                Key.Spacebar -> preview = if (preview == null) anchor else null
                Key.Escape -> preview = null
                else -> return@onKeyEvent false
            }
            true
        }
    ) {
        Row(Modifier.fillMaxSize().padding(8.dp)) {
            LazyVerticalGrid(GridCells.Adaptive(128.dp), Modifier.weight(1f)) {
                items(grid, key = { it.path }) { f ->
                    Thumbnail(f, f in marked, thumbnails, Modifier.padding(2.dp).selectable(grid, f))
                }
            }

            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val moving = grid.filter { it in marked }
                        grid.removeAll(moving)
                        selectedGroup!!.photos += moving
                        marked = emptySet()
                    },
                    enabled = markedIn === grid && marked.isNotEmpty() && selectedGroup != null,
                ) { Text("→ Gruppe") }
                Button(
                    onClick = {
                        val moving = markedGroup!!.photos.filter { it in marked }
                        markedGroup.photos.removeAll(moving)
                        backToGrid(moving)
                        marked = emptySet()
                    },
                    enabled = markedGroup != null && marked.isNotEmpty(),
                ) { Text("← Grid") }
                Button(onClick = { move(-1) }, enabled = markedGroup != null && marked.isNotEmpty()) { Text("◀") }
                Button(onClick = { move(1) }, enabled = markedGroup != null && marked.isNotEmpty()) { Text("▶") }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var year by remember { mutableStateOf("") }
                var month by remember { mutableStateOf("") }
                var day by remember { mutableStateOf("") }
                val newGroup = runCatching {
                    Group(year.toInt(), month.ifEmpty { null }?.toInt(), day.ifEmpty { null }?.toInt())
                }.getOrNull()?.takeIf { it.day == null || it.month != null }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(year, { year = it }, Modifier.width(100.dp), label = { Text("Jahr") }, singleLine = true)
                    OutlinedTextField(month, { month = it }, Modifier.width(100.dp), label = { Text("Monat") }, singleLine = true)
                    OutlinedTextField(day, { day = it }, Modifier.width(100.dp), label = { Text("Tag") }, singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { groups += newGroup!!; selectedGroup = newGroup }, enabled = newGroup != null) {
                        Text("Gruppe anlegen")
                    }
                    Button(
                        onClick = {
                            val group = selectedGroup!!
                            backToGrid(group.photos)
                            groups -= group
                            selectedGroup = null
                            if (markedIn === group.photos) marked = emptySet()
                        },
                        enabled = selectedGroup != null,
                    ) { Text("Gruppe löschen") }
                    Button(
                        onClick = {
                            val targets = mutableMapOf<File, LocalDateTime?>()
                            grid.forEach { targets[it] = null }
                            groups.groupBy { it.date }.forEach { (date, sameDay) ->
                                sameDay.flatMap { it.photos }.forEachIndexed { n, f ->
                                    targets[f] = date.atTime(12, 0).plusSeconds(10L * n)
                                }
                            }
                            val changed = targets.filter { (f, d) -> onDisk[f] != d }
                            applying = true
                            status = "Schreibe …"
                            scope.launch {
                                status = try {
                                    withContext(Dispatchers.IO) {
                                        changed.forEach { (f, d) -> writeDate(f, d); onDisk[f] = d }
                                    }
                                    "${changed.size} Dateien geschrieben"
                                } catch (e: Exception) {
                                    "Fehler: ${e.message}"
                                }
                                applying = false
                            }
                        },
                        enabled = !applying,
                    ) { Text("Anwenden") }
                }
                Text(status)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups) { group ->
                        Column(
                            Modifier.fillMaxWidth()
                                .border(2.dp, if (group == selectedGroup) MaterialTheme.colorScheme.primary else Color.LightGray)
                                .onClick { selectedGroup = group }
                                .padding(8.dp)
                        ) {
                            Text("${group.label} (${group.photos.size})")
                            LazyRow(Modifier.height(132.dp)) {
                                items(group.photos, key = { it.path }) { f ->
                                    Thumbnail(f, f in marked, thumbnails, Modifier.padding(2.dp).selectable(group.photos, f))
                                }
                            }
                        }
                    }
                }
            }
        }

        preview?.let { f ->
            val bitmap by produceState<ImageBitmap?>(null, f) {
                value = withContext(Dispatchers.IO) { imread(f.path, IMREAD_REDUCED_COLOR_2).use { it.toImageBitmap() } }
            }
            Box(Modifier.fillMaxSize().background(Color.Black).onClick { preview = null }) {
                bitmap?.let { Image(it, f.name, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun Thumbnail(file: File, marked: Boolean, cache: MutableMap<File, ImageBitmap>, modifier: Modifier) {
    val bitmap by produceState(cache[file], file) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { imread(file.path, IMREAD_REDUCED_COLOR_8).use { it.toImageBitmap() } }
                .also { cache[file] = it }
        }
    }
    Box(modifier.size(128.dp).border(3.dp, if (marked) MaterialTheme.colorScheme.primary else Color.Transparent)) {
        bitmap?.let { Image(it, file.name, Modifier.fillMaxSize().padding(3.dp)) }
    }
}
