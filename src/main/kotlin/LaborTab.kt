import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_imgproc.INTER_AREA
import org.bytedeco.opencv.global.opencv_imgproc.resize
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val ROI = 1024

enum class View(val label: String) { Split("Split"), Original("Original"), Result("Ergebnis") }

class LaborState {
    var file by mutableStateOf<File?>(null)
    var params by mutableStateOf(Params())
    var roi by mutableStateOf<IntOffset?>(null)
    var zoom by mutableStateOf(1)
    var view by mutableStateOf(View.Split)
    var split by mutableStateOf(0.5f)
    var presetName by mutableStateOf("")
}

@Composable
fun LaborTab(folder: File?, s: LaborState) {
    val files = remember(folder) { folder?.let(::jpgs).orEmpty() }
    var dims by remember { mutableStateOf(IntSize.Zero) }
    var navigator by remember { mutableStateOf<ImageBitmap?>(null) }
    var original by remember { mutableStateOf<ImageBitmap?>(null) }
    var result by remember { mutableStateOf<ImageBitmap?>(null) }
    var millis by remember { mutableStateOf<Long?>(null) }
    val w = min(ROI, dims.width)
    val h = min(ROI, dims.height)

    LaunchedEffect(s.file) {
        navigator = null; original = null; result = null; millis = null
        val file = s.file ?: return@LaunchedEffect
        val image = withContext(Dispatchers.IO) { imread(file.path) }
        image.use { image ->
            if (image.empty()) return@LaunchedEffect
            dims = IntSize(image.cols(), image.rows())
            navigator = withContext(Dispatchers.Default) {
                Mat().use { small ->
                    resize(image, small, org.bytedeco.opencv.opencv_core.Size(), 0.125, 0.125, INTER_AREA)
                    small.toImageBitmap()
                }
            }
            val rw = min(ROI, image.cols())
            val rh = min(ROI, image.rows())
            s.roi = s.roi?.let { IntOffset(it.x.coerceIn(0, image.cols() - rw), it.y.coerceIn(0, image.rows() - rh)) }
                ?: IntOffset((image.cols() - rw) / 2, (image.rows() - rh) / 2)
            snapshotFlow { s.roi!! to s.params }.collectLatest { (roi, params) ->
                delay(300.milliseconds)
                val (o, r, t) = withContext(Dispatchers.Default) {
                    Mat(image, Rect(roi.x, roi.y, rw, rh)).use { crop ->
                        val start = System.nanoTime()
                        denoise(crop, params).use { out ->
                            val t = (System.nanoTime() - start) / 1_000_000
                            Triple(crop.toImageBitmap(), out.toImageBitmap(), t)
                        }
                    }
                }
                original = o; result = r; millis = t
            }
        }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
            LazyColumn(Modifier.height(160.dp).fillMaxWidth()) {
                items(files) { f ->
                    Text(
                        f.name,
                        Modifier.fillMaxWidth().clickable { s.file = f }.padding(4.dp),
                        color = if (f == s.file) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                }
            }
            navigator?.let { nav ->
                Box(
                    Modifier.fillMaxWidth().aspectRatio(dims.width.toFloat() / dims.height)
                        .pointerInput(dims) {
                            detectDragGestures { _, drag ->
                                val k = dims.width / size.width.toFloat()
                                val roi = s.roi!!
                                s.roi = IntOffset(
                                    (roi.x + drag.x * k).roundToInt().coerceIn(0, dims.width - w),
                                    (roi.y + drag.y * k).roundToInt().coerceIn(0, dims.height - h),
                                )
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val k = size.width / dims.width
                            s.roi?.let {
                                drawRect(
                                    Color.Red,
                                    Offset(it.x * k, it.y * k),
                                    Size(w * k, h * k),
                                    style = Stroke(2f)
                                )
                            }
                        }
                ) {
                    Image(nav, null, Modifier.fillMaxSize())
                }
            }
            val p = s.params
            LabeledSlider("h", p.h, 0f..30f) { s.params = p.copy(h = it) }
            LabeledSlider("hColor", p.hColor, 0f..30f) { s.params = p.copy(hColor = it) }
            LabeledSlider("templateWindowSize", p.templateWindowSize.toFloat(), 3f..21f, 8) {
                s.params = p.copy(templateWindowSize = it.roundToInt() or 1)
            }
            LabeledSlider("searchWindowSize", p.searchWindowSize.toFloat(), 5f..51f, 22) {
                s.params = p.copy(searchWindowSize = it.roundToInt() or 1)
            }
            LabeledSlider("Blend Original ↔ Ergebnis (%)", p.blend.toFloat(), 0f..100f) {
                s.params = p.copy(blend = it.roundToInt())
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                View.entries.forEach { v ->
                    RadioButton(s.view == v, { s.view = v })
                    Text(v.label)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 2).forEach { z ->
                    RadioButton(s.zoom == z, { s.zoom = z })
                    Text("${z * 100} %")
                }
            }
            millis?.let { t ->
                val image = t * dims.width.toLong() * dims.height / (w * h)
                Text("ROI: $t ms")
                Text("Bild: ~${image / 1000} s")
                Text("Ordner (${files.size} Bilder): ~${image * files.size / 60_000} min")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(s.presetName, { s.presetName = it }, label = { Text("Preset-Name") }, singleLine = true)
            Row {
                Button(onClick = { savePreset(s.presetName, s.params) }, enabled = s.presetName.isNotBlank()) {
                    Text("Speichern")
                }
                Spacer(Modifier.width(8.dp))
                PresetMenu("Laden") { s.params = loadPreset(it); s.presetName = it }
            }
        }

        val o = original
        val r = result
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            if (o != null && r != null) {
                val density = LocalDensity.current
                Box(
                    Modifier.requiredSize(
                        with(density) { (o.width * s.zoom).toDp() },
                        with(density) { (o.height * s.zoom).toDp() })
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ ->
                                s.split = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    when (s.view) {
                        View.Original -> Pixels(o)
                        View.Result -> Pixels(r)
                        View.Split -> {
                            Pixels(r)
                            Pixels(o, Modifier.drawWithContent {
                                val x = size.width * s.split
                                clipRect(right = x) { this@drawWithContent.drawContent() }
                                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2f)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pixels(bitmap: ImageBitmap, modifier: Modifier = Modifier) =
    Image(bitmap, null, modifier.fillMaxSize(), filterQuality = FilterQuality.None)

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Text("$label: ${if (value % 1f == 0f) value.toInt() else "%.1f".format(value)}")
    Slider(value, onChange, valueRange = range, steps = steps)
}
