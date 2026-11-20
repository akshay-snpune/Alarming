package com.alarming.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alarming.audio.ImportToneButton
import com.alarming.audio.TonePickerContent
import com.alarming.audio.rememberToneLibrary
import com.alarming.audio.rememberTonePreview
import com.alarming.audio.toneDisplayName
import com.alarming.feature.reminders.ReminderAlarms
import com.alarming.feature.reminders.ReminderTone
import com.alarming.feature.reminders.ReminderTones
import com.alarming.ui.GlassCard
import com.alarming.ui.GlassSectionLabel

private fun isImported(tone: ReminderTone) = tone.id.startsWith("user:")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(model: SettingsViewModel, modifier: Modifier = Modifier) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val tones by rememberToneLibrary().tones.collectAsStateWithLifecycle(initialValue = ReminderTones.all)
    val deletionError by model.toneDeletionError.collectAsStateWithLifecycle()
    var previewError by remember { mutableStateOf<String?>(null) }
    val preview = rememberTonePreview { previewError = it }
    var choosingDefault by remember { mutableStateOf<DefaultToneSlot?>(null) }
    var choosingTheme by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ReminderTone?>(null) }
    var showBuiltIn by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp))
        Text(
            "Tones, timer behaviour and alerts in one place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        GlassSectionLabel("Timer")
        GlassCard {
            SwitchRow(
                icon = Icons.Outlined.ScreenLockPortrait,
                title = "Keep screen on",
                subtitle = "While a task is running. The power button still turns the screen off.",
                checked = settings.keepScreenOn,
                onCheckedChange = model::setKeepScreenOn,
            )
        }

        Spacer(Modifier.height(6.dp))
        GlassSectionLabel("Appearance")
        GlassCard {
            SwitchRow(
                icon = if (settings.darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                title = "Dark theme",
                subtitle = if (settings.darkTheme) "Deep glass for low light" else "Bright glass for daylight",
                checked = settings.darkTheme,
                onCheckedChange = model::setDarkTheme,
            )
            RowDivider()
            SettingRow(
                icon = Icons.Outlined.Palette,
                title = "Color theme",
                subtitle = "Choose a palette",
                onClick = { choosingTheme = true },
            ) {
                Text(settings.colorTheme.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 16.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        GlassSectionLabel("Default tones")
        GlassCard {
            val rows = listOf(
                Triple(DefaultToneSlot.STAGE, Icons.Outlined.SwapHoriz, "Stage change"),
                Triple(DefaultToneSlot.START, Icons.Outlined.PlayCircle, "Task start"),
                Triple(DefaultToneSlot.COMPLETION, Icons.Outlined.Flag, "Task complete"),
                Triple(DefaultToneSlot.REMINDER, Icons.Outlined.AccessAlarm, "Reminders"),
            )
            rows.forEachIndexed { index, (slot, icon, title) ->
                if (index > 0) RowDivider()
                val toneId = settings.tone(slot)
                SettingRow(
                    icon = icon,
                    title = title,
                    subtitle = toneDisplayName(toneId),
                    onClick = { choosingDefault = slot },
                ) {
                    IconButton(onClick = { preview(toneId) }) { Icon(Icons.Filled.PlayArrow, "Preview $title tone") }
                }
            }
        }
        Text(
            "Used for new tasks and reminders. Existing ones keep their own tones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        Spacer(Modifier.height(6.dp))
        GlassSectionLabel("Tone library")
        GlassCard(Modifier.animateContentSize()) {
            val imported = tones.filter(::isImported)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your tones", style = MaterialTheme.typography.titleMedium)
                if (imported.isEmpty()) {
                    Text("Import short clips to use as stage, start, completion or reminder tones.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ImportToneButton { previewError = null }
            }
            imported.forEach { tone ->
                RowDivider()
                ToneRow(tone, onPreview = { preview(tone.id) }, onDelete = { deleting = tone })
            }
            RowDivider()
            val arrow by animateFloatAsState(if (showBuiltIn) 180f else 0f, label = "builtInArrow")
            SettingRow(
                icon = Icons.Outlined.LibraryMusic,
                title = "Built-in tones",
                subtitle = "${tones.count { !isImported(it) }} bundled sounds",
                onClick = { showBuiltIn = !showBuiltIn },
            ) {
                Icon(Icons.Outlined.ExpandMore, if (showBuiltIn) "Hide built-in tones" else "Show built-in tones", Modifier.padding(12.dp).rotate(arrow))
            }
            AnimatedVisibility(showBuiltIn, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    tones.filterNot(::isImported).forEach { tone ->
                        RowDivider()
                        ToneRow(tone, onPreview = { preview(tone.id) }, onDelete = if (tone.id in ReminderTones.protectedIds) null else ({ deleting = tone }))
                    }
                }
            }
        }
        previewError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 6.dp)) }
        deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 6.dp)) }

        Spacer(Modifier.height(6.dp))
        GlassSectionLabel("Alerts")
        AlertsCard(settings, model)

        Text(
            "StageBell works fully offline. No accounts, ads or analytics.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    choosingDefault?.let { slot ->
        ModalBottomSheet(
            onDismissRequest = { choosingDefault = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    when (slot) {
                        DefaultToneSlot.STAGE -> "Stage change tone"
                        DefaultToneSlot.START -> "Task start tone"
                        DefaultToneSlot.COMPLETION -> "Task complete tone"
                        DefaultToneSlot.REMINDER -> "Reminder tone"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TonePickerContent(settings.tone(slot), onSelect = { id ->
                    model.setDefaultTone(slot, id)
                    choosingDefault = null
                }, showImportHint = false)
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (choosingTheme) {
        ModalBottomSheet(
            onDismissRequest = { choosingTheme = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Color theme", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                AppColorTheme.entries.forEach { theme ->
                    SettingRow(
                        icon = Icons.Outlined.Palette,
                        title = theme.label,
                        subtitle = if (theme == settings.colorTheme) "Selected" else "Choose this palette",
                        onClick = {
                            model.setColorTheme(theme)
                            choosingTheme = false
                        },
                    ) {
                        RadioButton(
                            selected = theme == settings.colorTheme,
                            onClick = {
                                model.setColorTheme(theme)
                                choosingTheme = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    deleting?.let { tone ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${tone.name}”?") },
            text = { Text("Tones used by tasks, reminders or default settings cannot be deleted. Bundled tones are removed from the library, not from the installed app.") },
            confirmButton = { TextButton(onClick = { model.deleteTone(tone.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AlertsCard(settings: AppSettings, model: SettingsViewModel) {
    val context = LocalContext.current
    val alarms = remember(context) { ReminderAlarms(context) }
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    val notificationsOn = remember(revision) { alarms.notificationsAllowed() }
    val precise = remember(revision) { alarms.exactAvailable() }

    GlassCard {
        SwitchRow(
            icon = Icons.Outlined.Vibration,
            title = "Vibrate by default",
            subtitle = "For new tasks and reminders",
            checked = settings.defaultVibration,
            onCheckedChange = model::setDefaultVibration,
        )
        RowDivider()
        SettingRow(
            icon = Icons.Outlined.Notifications,
            title = "Notifications",
            subtitle = if (notificationsOn) "On" else "Off · reminders can't alert you",
            onClick = null,
        ) {
            if (notificationsOn) StatusDot(true) else TextButton(onClick = {
                if (alarms.notificationPermissionMissing()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Turn on") }
        }
        RowDivider()
        SettingRow(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "Precise reminders",
            subtitle = if (precise) "Alerts arrive on time" else "Off · Android may delay reminders",
            onClick = null,
        ) {
            if (precise || Build.VERSION.SDK_INT < 31) StatusDot(precise) else TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Allow") }
        }
    }
    Text(
        "Do Not Disturb, silent mode and notification-channel settings still apply.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onCheckedChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun ToneRow(tone: ReminderTone, onPreview: () -> Unit, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tone.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tone.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onPreview) { Icon(Icons.Filled.PlayArrow, "Preview ${tone.name}") }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete ${tone.name}") }
    }
}

@Composable
private fun StatusDot(on: Boolean) {
    Box(
        Modifier.padding(end = 18.dp).size(10.dp)
            .background(if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary, CircleShape),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}
