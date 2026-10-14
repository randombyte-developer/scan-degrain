import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File
import javax.swing.JFileChooser

fun main() = application {
    Window(::exitApplication, rememberWindowState(width = 1600.dp, height = 1000.dp), title = "scan-degrain") {
        MaterialTheme { Surface { App() } }
    }
}

@Composable
fun App() {
    var folder by remember { mutableStateOf<File?>(null) }
    var tab by remember { mutableStateOf(0) }
    val labor = remember { LaborState() }
    val batch = remember { BatchState() }
    val scope = rememberCoroutineScope()
    val thumbnails = remember { mutableMapOf<File, ImageBitmap>() }
    Column {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { chooseDir()?.let { folder = it } }) { Text("Ordner öffnen") }
            Text(folder?.path.orEmpty(), Modifier.padding(start = 8.dp))
        }
        PrimaryTabRow(tab) {
            listOf("Labor", "Batch", "Datum").forEachIndexed { i, title ->
                Tab(tab == i, { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> LaborTab(folder, labor)
            1 -> BatchTab(folder, batch, scope)
            2 -> DatumTab(batch.output, thumbnails)
        }
    }
}

fun chooseDir(): File? {
    val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

@Composable
fun PresetMenu(label: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(open, { open = false }) {
            presetNames().forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(name) })
            }
        }
    }
}
