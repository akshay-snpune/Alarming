package com.alarming.feature.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersScreen(modifier: Modifier = Modifier, waterOnly: Boolean = false, model: RemindersViewModel = viewModel()) {
    val records by model.records.collectAsStateWithLifecycle()
    val storageError by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings by remember(context) { com.alarming.feature.settings.AppSettingsStore.get(context).settings }
        .collectAsStateWithLifecycle(initialValue = com.alarming.feature.settings.AppSettings())
    var edit by remember { mutableStateOf<ReminderRecord?>(null) }
    var deleting by remember { mutableStateOf<ReminderRecord?>(null) }
    var deletionPending by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionRevision++; model.recover() }
    val notificationsAllowed = remember(permissionRevision) { ReminderAlarms(context).notificationsAllowed() }
    val precise = remember(permissionRevision) { ReminderAlarms(context).exactAvailable() }
    val visibleRecords = records.filter { (it.repeat == RepeatRule.WATER.name) == waterOnly }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (waterOnly) "Water" else "Reminders", style = MaterialTheme.typography.headlineLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (waterOnly) {
                Button(onClick = { edit = newReminder(true, settings) }) { Icon(Icons.Outlined.WaterDrop, null); Text(" Water schedule") }
            } else {
                Button(onClick = { edit = newReminder(false, settings) }) { Icon(Icons.Default.Add, null); Text(" Reminder") }
            }
        }
        Text(if (precise) "Precise scheduling available" else "Approximate scheduling: Android may delay alerts", style = MaterialTheme.typography.bodySmall)
        if (!notificationsAllowed) {
            Text("Notifications are off. Reminders are saved, but alerts may not appear.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                if (ReminderAlarms(context).notificationPermissionMissing()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Enable notifications") }
        }
        if (!precise && Build.VERSION.SDK_INT >= 31) {
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Allow precise reminders") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (visibleRecords.isEmpty()) Text(if (waterOnly) "No water schedules" else "No reminders", color = MaterialTheme.colorScheme.onSurfaceVariant)
        visibleRecords.forEach { record ->
            com.alarming.ui.GlassCard {
            Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.title, style = MaterialTheme.typography.titleLarge)
                    if (record.description.isNotBlank()) Text(record.description, style = MaterialTheme.typography.bodyMedium)
                    Text(recordSummary(record), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.repeat == RepeatRule.WATER.name) Text("${clock(record.startMinute)}–${clock(record.endMinute)}, every ${record.intervalMinutes} min", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = record.enabled, onCheckedChange = { model.enable(record.id, it) { message -> error = message } })
            }
            Row {
                TextButton(onClick = { edit = record.copy(completed = false) }) { Icon(Icons.Outlined.Edit, null); Text(" Edit") }
                TextButton(enabled = !record.completed, onClick = { model.complete(record.id) }) { Text("Complete") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { deletionError = null; deleting = record }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${record.title}") }
            }
            }
            }
        }
    }
    edit?.let { record ->
        ReminderEditor(record, waterOnly, onDismiss = { edit = null }, onSave = { updated, result -> model.save(updated) { message -> if (message == null) edit = null; result(message) } })
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { if (!deletionPending) deleting = null },
            title = { Text("Delete reminder?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.title)
                    deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !deletionPending, onClick = {
                    deletionPending = true
                    deletionError = null
                    model.delete(record.id) { message ->
                        deletionPending = false
                        deletionError = message
                        if (message == null) deleting = null
                    }
                }) { Text(if (deletionPending) "Deleting..." else "Delete") }
            },
            dismissButton = { TextButton(enabled = !deletionPending, onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun newReminder(water: Boolean, settings: com.alarming.feature.settings.AppSettings): ReminderRecord {
    val now = ZonedDateTime.now().plusMinutes(5)
    return ReminderRecord(
        title = if (water) "Drink water" else "", description = "",
        repeat = if (water) "WATER" else "ONCE", firstDate = now.toLocalDate().toString(),
        startMinute = if (water) 480 else now.hour * 60 + now.minute,
        endMinute = if (water) 1200 else now.hour * 60 + now.minute,
        intervalMinutes = 120, zoneId = now.zone.id, sound = settings.defaultReminderTone, vibration = settings.defaultVibration,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditor(initial: ReminderRecord, waterOnly: Boolean, onDismiss: () -> Unit, onSave: (ReminderRecord, (String?) -> Unit) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var date by remember { mutableStateOf(LocalDate.parse(initial.firstDate)) }
    var rule by remember { mutableStateOf(if (waterOnly) RepeatRule.WATER else RepeatRule.valueOf(initial.repeat).takeUnless { it == RepeatRule.WATER } ?: RepeatRule.ONCE) }
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var interval by remember { mutableStateOf(initial.intervalMinutes.toString()) }
    val initialDays = initial.daysOfWeekMask.takeIf { it != 0 } ?: when (rule) {
        RepeatRule.ONCE -> 0
        RepeatRule.WEEKLY -> ReminderSchedule.dayBit(date.dayOfWeek)
        else -> 127
    }
    var daysMask by remember { mutableIntStateOf(initialDays) }
    var sound by remember { mutableStateOf(initial.sound) }
    var vibration by remember { mutableStateOf(initial.vibration) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val library = com.alarming.audio.rememberToneLibrary()
    val tones by library.tones.collectAsStateWithLifecycle(initialValue = ReminderTones.all)
    val preview = com.alarming.audio.rememberTonePreview { error = it }
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    fun chooseRule(next: RepeatRule) {
        rule = next
        if (next == RepeatRule.ONCE) daysMask = 0
        else if (daysMask == 0) daysMask = if (next == RepeatRule.WEEKLY) ReminderSchedule.dayBit(date.dayOfWeek) else 127
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (waterOnly) "Water schedule" else if (initial.id == 0L) "New reminder" else "Edit reminder", style = MaterialTheme.typography.headlineSmall)
                        Text(if (waterOnly) "Set when water prompts repeat" else "Choose a time and repeat days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
                }

                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(500) }, label = { Text("Description (optional)") }, maxLines = 2, modifier = Modifier.fillMaxWidth())

                if (!waterOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(RepeatRule.ONCE, RepeatRule.DAILY, RepeatRule.WEEKLY).forEach { option ->
                            FilterChip(selected = rule == option, onClick = { chooseRule(option) }, label = {
                                Text(when (option) { RepeatRule.ONCE -> "Once"; RepeatRule.DAILY -> "Daily"; else -> "Weekly" })
                            })
                        }
                    }
                }
                if (rule == RepeatRule.ONCE) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }) { Text("Date: $date") }
                } else if (rule == RepeatRule.WEEKLY) {
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }) { Text("Starts on: $date") }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showTimePicker(context, start) { start = it } }.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (waterOnly) "FIRST WATER REMINDER" else "ALARM TIME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(clock(start), style = MaterialTheme.typography.displaySmall)
                            Text(if (enabled) "On" else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                if (waterOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeButton("Stop at", end) { end = it }
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { interval = it.filter(Char::isDigit).take(4) },
                            label = { Text("Every (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Text("Water prompts repeat between these times on selected days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (rule != RepeatRule.ONCE) {
                    Text(if (daysMask == 127) "Every day" else "Repeat on", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                            val selected = daysMask and (1 shl index) != 0
                            Surface(
                                modifier = Modifier.size(38.dp).clickable { daysMask = daysMask xor (1 shl index) },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) { Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) } }
                        }
                    }
                }

                if (waterOnly && start > end) {
                    Text("Overnight windows aren’t supported. Set the end time after the start time.", color = MaterialTheme.colorScheme.error)
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = sound != "Silent", onClick = { preview(sound) }) { Icon(Icons.Default.PlayArrow, "Preview tone") }
                        Column(Modifier.weight(1f)) {
                            Text("ALERT TONE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                TextButton(onClick = { menu = true }) { Text(com.alarming.audio.toneDisplayName(sound)) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Silent") }, onClick = { sound = "Silent"; menu = false })
                                    tones.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { sound = option.id; menu = false }) }
                                }
                            }
                        }
                        TextButton(onClick = { menu = true }) { Text("Change") }
                    }
                }
                Text("Import or remove your own tones in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Vibration", Modifier.weight(1f)); Switch(vibration, { vibration = it }) }
                Text("Time zone: ${initial.zoneId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(enabled = !saving, onClick = {
                        error = when {
                            title.isBlank() -> "Enter a reminder title."
                            rule != RepeatRule.ONCE && daysMask == 0 -> "Select at least one repeat day."
                            rule == RepeatRule.WATER && end < start -> "End must be at or after start; overnight windows are not supported."
                            rule == RepeatRule.WATER && interval.toIntOrNull() !in 1..1440 -> "Interval must be 1–1440 minutes."
                            else -> null
                        }
                        if (error == null) {
                            saving = true
                            onSave(initial.copy(
                                title = title.trim(), description = description.trim(), repeat = rule.name,
                                firstDate = date.toString(), startMinute = start,
                                endMinute = if (waterOnly) end else start,
                                intervalMinutes = if (waterOnly) interval.toInt() else 120,
                                daysOfWeekMask = if (rule == RepeatRule.ONCE) 0 else daysMask,
                                sound = sound, vibration = vibration, enabled = enabled, completed = false,
                            )) { message -> error = message; saving = false }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(8.dp)) {
                        Text(if (saving) "Saving" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = { showTimePicker(context, minute, onChange) }) {
        Text("$label: ${clock(minute)}")
    }
}

private fun showTimePicker(context: android.content.Context, minute: Int, onChange: (Int) -> Unit) {
    TimePickerDialog(context, { _, hour, part -> onChange(hour * 60 + part) }, minute / 60, minute % 60, true).show()
}

private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
fun recordSummary(record: ReminderRecord): String = when {
    record.completed -> "Completed"
    !record.enabled -> "Disabled"
    record.nextTrigger == null -> "No next occurrence"
    else -> Instant.ofEpochMilli(record.nextTrigger).atZone(ZoneId.of(record.zoneId)).format(DateTimeFormatter.ofPattern("EEE, d MMM HH:mm")) + " · " + record.repeat.lowercase()
}