import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL
import org.apache.commons.imaging.formats.tiff.constants.TiffDirectoryType.EXIF_DIRECTORY_EXIF_IFD
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants.TIFF_TAG_DATE_TIME
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

// Not defined in Commons Imaging
private val OFFSET_TIME = TagInfoAscii("OffsetTime", 0x9010, 7, EXIF_DIRECTORY_EXIF_IFD)
private val OFFSET_TIME_ORIGINAL = TagInfoAscii("OffsetTimeOriginal", 0x9011, 7, EXIF_DIRECTORY_EXIF_IFD)

private val EXIF_TAGS = listOf(
    EXIF_TAG_DATE_TIME_ORIGINAL, EXIF_TAG_DATE_TIME_DIGITIZED,
    OFFSET_TIME, OFFSET_TIME_ORIGINAL,
)

private fun exif(file: File) = (Imaging.getMetadata(file) as? JpegImageMetadata)?.exif

fun readDate(file: File): LocalDateTime? =
    exif(file)?.getFieldValue(EXIF_TAG_DATE_TIME_ORIGINAL)?.firstOrNull()
        ?.let { LocalDateTime.parse(it.trim(), FORMAT) }

/** Writes [date] as UTC into the date tags, or removes them if [date] is null. */
fun writeDate(file: File, date: LocalDateTime?) {
    val outputSet = exif(file)?.outputSet ?: TiffOutputSet()
    val root = outputSet.orCreateRootDirectory
    val exif = outputSet.orCreateExifDirectory
    root.removeField(TIFF_TAG_DATE_TIME)
    EXIF_TAGS.forEach(exif::removeField)
    if (date != null) {
        val text = date.format(FORMAT)
        root.add(TIFF_TAG_DATE_TIME, text)
        exif.add(EXIF_TAG_DATE_TIME_ORIGINAL, text)
        listOf(OFFSET_TIME, OFFSET_TIME_ORIGINAL).forEach { exif.add(it, "+00:00") }
    }
    val bytes =
        ByteArrayOutputStream().also { ExifRewriter().updateExifMetadataLossless(file, it, outputSet) }.toByteArray()
    file.writeBytes(bytes)
}
