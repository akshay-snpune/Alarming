package com.alarming.feature.intervaltimer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun TaskTransferControls(taskCount: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archive = remember(context) { TaskArchive(context.applicationContext) }
    val store = remember(context) { IntervalTaskStore.get(context.applicationContext) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                val count = context.contentResolver.openInputStream(uri)?.use { archive.import(it) }
                    ?: throw IllegalArgumentException("The selected file could not be opened.")
                message = "Imported $count task${if (count == 1) "" else "s"} with their tones."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "The task archive could not be imported."
            } finally { busy = false }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                val tasks = store.tasks.first()
                context.contentResolver.openOutputStream(uri, "wt")?.use { archive.export(tasks, it) }
                    ?: throw IllegalArgumentException("The selected file could not be written.")
                message = "Exported ${tasks.size} tasks with their tones."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "The tasks could not be exported."
            } finally { busy = false }
        }
    }
    Column {
        Row {
            TextButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import tasks") }
            TextButton(enabled = !busy && taskCount > 0, onClick = { exportLauncher.launch("StageBell-tasks.zip") }) { Text("Export all") }
        }
        message?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun ShareTaskButton(taskId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archive = remember(context) { TaskArchive(context.applicationContext) }
    val store = remember(context) { IntervalTaskStore.get(context.applicationContext) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                error = null
                var file: File? = null
                try {
                    val task = store.get(taskId) ?: throw IllegalArgumentException("This task no longer exists.")
                    val directory = File(context.cacheDir, "task-exports")
                    check(directory.isDirectory || directory.mkdirs()) { "Unable to prepare the task for sharing." }
                    file = File(directory, "stagebell-${UUID.randomUUID()}.zip")
                    file.outputStream().use { archive.export(listOf(task), it) }
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.tones", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("StageBell task", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share task"))
                } catch (cancelled: CancellationException) {
                    file?.delete()
                    throw cancelled
                } catch (failure: Exception) {
                    file?.delete()
                    error = failure.message ?: "The task could not be shared."
                } finally { busy = false }
            }
        }) { Text("Share") }
        error?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
    }
}
