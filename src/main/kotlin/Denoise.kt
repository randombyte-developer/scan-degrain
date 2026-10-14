import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.opencv.global.opencv_core.addWeighted
import org.bytedeco.opencv.global.opencv_imgcodecs.*
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2BGRA
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_photo.fastNlMeansDenoisingColored
import org.bytedeco.opencv.opencv_core.Mat
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class Params(
    val h: Float = 3f,
    val hColor: Float = 3f,
    val templateWindowSize: Int = 7,
    val searchWindowSize: Int = 21,
    val blend: Int = 100,
)

fun denoise(src: Mat, p: Params): Mat {
    val dst = Mat()
    fastNlMeansDenoisingColored(src, dst, p.h, p.hColor, p.templateWindowSize, p.searchWindowSize)
    addWeighted(src, 1 - p.blend / 100.0, dst, p.blend / 100.0, 0.0, dst)
    return dst
}

fun degrain(source: File, target: File, p: Params) {
    imread(source.path).use { image ->
        if (image.empty()) error("Datei nicht lesbar")
        denoise(image, p).use { result ->
            BytePointer().use { buf ->
                IntPointer(IMWRITE_JPEG_QUALITY, 95, IMWRITE_JPEG_SAMPLING_FACTOR, IMWRITE_JPEG_SAMPLING_FACTOR_444).use { params ->
                    if (!imencode(".jpg", result, buf, params)) error("JPEG-Kodierung fehlgeschlagen")
                }
                val tmp = File(target.path + ".tmp")
                tmp.writeBytes(ByteArray(buf.limit().toInt()).also { buf.get(it) })
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }
        }
    }
}

fun Mat.toImageBitmap(): ImageBitmap = Mat().use { bgra ->
    cvtColor(this, bgra, COLOR_BGR2BGRA)
    val bytes = ByteArray(cols() * rows() * 4).also { bgra.data().get(it) }
    Image.makeRaster(ImageInfo(cols(), rows(), ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, cols() * 4)
        .toComposeImageBitmap()
}

fun jpgs(dir: File): List<File> =
    dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg") }.orEmpty().sortedBy { it.name }

val presetDir = File(System.getProperty("user.home"), ".config/scan-degrain/presets")

fun presetNames(): List<String> =
    presetDir.listFiles { f -> f.extension == "json" }.orEmpty().map { it.nameWithoutExtension }.sorted()

fun loadPreset(name: String): Params = Json.decodeFromString(File(presetDir, "$name.json").readText())

fun savePreset(name: String, p: Params) {
    presetDir.mkdirs()
    File(presetDir, "$name.json").writeText(Json.encodeToString(p))
}
