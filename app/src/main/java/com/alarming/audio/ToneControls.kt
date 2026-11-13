package com.alarming.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alarming.feature.reminders.ReminderTone
import com.alarming.feature.reminders.ReminderTones
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun rememberToneLibrary(): ToneLibrary {
    val context = LocalContext.current
    return remember(context) { ToneLibrary(context.applicationContext) }
}

@Composable
fun toneDisplayName(id: String): String {
    val library = rememberToneLibrary()
    val tones by library.tones.collectAsStateWithLifecycle(initialValue = ReminderTones.all)
    return tones.firstOrNull { it.id == id }?.name ?: if (id == "Silent") "Silent" else "Unavailable tone"
}

@Composable
fun ImportToneButton(onImported: (ReminderTone) -> Unit) {
    val library = rememberToneLibrary()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            try { onImported(library.import(uri)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "This audio could not be imported." }
            finally { importing = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(enabled = !importing, onClick = { chooser.launch(arrayOf("audio/*")) }) {
            Icon(Icons.Outlined.FileUpload, null)
            Text(if (importing) " Importing" else " Import tone")
        }
        Text("MP3, WAV, M4A/AAC, OGG, FLAC · up to 5 seconds · 5 MB max", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun rememberTonePreview(onError: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val library = rememberToneLibrary()
    val scope = rememberCoroutineScope()
    val latestError by rememberUpdatedState(onError)
    val player = remember { MediaPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    return { id ->
        scope.launch {
            try {
                val uri = library.uri(id) ?: throw IllegalArgumentException("This tone is unavailable. Import it again or choose another tone.")
                player.reset()
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                player.setOnErrorListener { _, _, _ -> latestError("This format cannot be played on this device."); true }
                player.setOnPreparedListener { it.start() }
                player.setDataSource(context, uri)
                player.prepareAsync()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { latestError("This tone could not be played. Choose another tone or import it again.") }
        }
    }
}

@Composable
fun TonePickerContent(selectedId: String, onSelect: (String) -> Unit, showImportHint: Boolean = true) {
    val library = rememberToneLibrary()
    val tones by library.tones.collectAsStateWithLifecycle(initialValue = ReminderTones.all)
    var error by remember { mutableStateOf<String?>(null) }
    val preview = rememberTonePreview { error = it }
    if (showImportHint) {
        Text("Import or remove your own tones in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    tones.forEach { tone ->
        Row(Modifier.fillMaxWidth().clickable { onSelect(tone.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selectedId == tone.id, onClick = { onSelect(tone.id) })
            Column(Modifier.weight(1f)) {
                Text(tone.name, style = MaterialTheme.typography.titleMedium)
                Text(tone.description, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { preview(tone.id) }) { Icon(Icons.Default.PlayArrow, "Preview ${tone.name}") }
        }
        HorizontalDivider()
    }
}