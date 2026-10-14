import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BatchState {
    var output by mutableStateOf<File?>(null)
    var preset by mutableStateOf<String?>(null)
    var job by mutableStateOf<Job?>(null)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var remainingSeconds by mutableStateOf<Long?>(null)
    val errors = mutableStateListOf<String>()

    fun start(input: File, scope: CoroutineScope) {
        val output = output!!
        val params = loadPreset(preset!!)
        val files = jpgs(input)
        errors.clear()
        done = 0
        total = files.size
        remainingSeconds = null
        job = scope.launch {
            try {
                var processed = 0
                var nanos = 0L
                for (file in files) {
                    val target = File(output, file.name)
                    if (!target.exists()) {
                        val start = System.nanoTime()
                        try {
                            withContext(Dispatchers.IO) { degrain(file, target, params) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errors += "${file.name}: ${e.message}"
                        }
                        processed++
                        nanos += System.nanoTime() - start
                        remainingSeconds = nanos / processed * (total - done - 1) / 1_000_000_000
                    }
                    done++
                }
            } finally {
                job = null
            }
        }
    }
}

@Composable
fun BatchTab(folder: File?, s: BatchState, scope: CoroutineScope) {
    val running = s.job != null
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { chooseDir()?.let { s.output = it } }, enabled = !running) { Text("Ausgabeordner wählen") }
            Text(s.output?.path ?: "–", Modifier.padding(start = 8.dp))
        }
        PresetMenu(s.preset ?: "Preset wählen") { s.preset = it }

        val problem = when {
            folder == null -> "Kein Ordner geöffnet"
            s.output == null -> "Kein Ausgabeordner gewählt"
            s.output!!.canonicalFile == folder.canonicalFile -> "Ausgabeordner muss sich vom Eingabeordner unterscheiden"
            s.preset == null -> "Kein Preset gewählt"
            else -> null
        }
        if (running) {
            Button(onClick = { s.job?.cancel() }) { Text("Abbrechen") }
        } else {
            Button(onClick = { s.start(folder!!, scope) }, enabled = problem == null) { Text("Start") }
        }
        problem?.let { Text(it) }

        LinearProgressIndicator({ if (s.total == 0) 0f else s.done.toFloat() / s.total }, Modifier.fillMaxWidth())
        Text("${s.done} / ${s.total}" + s.remainingSeconds?.let { " · Restzeit ${it / 60} min ${it % 60} s" }.orEmpty())
        LazyColumn {
            items(s.errors) { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
