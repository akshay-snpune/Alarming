package com.alarming.ui

import android.app.Activity
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.max

private val SoundChoices = com.alarming.feature.reminders.ReminderTones.all

@Composable
internal fun AlarmingApp(viewModel: PrototypeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settingsModel: com.alarming.feature.settings.SettingsViewModel = viewModel()
    val settings by settingsModel.settings.collectAsStateWithLifecycle()
    val remindersModel: com.alarming.feature.reminders.RemindersViewModel = viewModel()
    val reminders by remindersModel.records.collectAsStateWithLifecycle()
    val upcoming = reminders.filter { it.enabled && !it.completed && it.nextTrigger != null }.minByOrNull { it.nextTrigger!! }

    AlarmingTheme(darkTheme = settings.darkTheme, colorTheme = settings.colorTheme) {
        val colors = MaterialTheme.colorScheme
        val useLightSystemBars = colors.background.luminance() > 0.5f
        SideEffect {
            val activity = context as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.surface.toArgb()
                window.decorView.systemUiVisibility = if (useLightSystemBars) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    0
                }
            }
        }

        BackHandler(enabled = state.route != AppRoute.HOME) {
            viewModel.goBack()
        }

        GlassBackground {
        androidx.compose.material3.Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                AnimatedVisibility(
                    visible = state.route == AppRoute.HOME,
                    enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
                ) {
                    AppNavigationBar(
                        selected = state.destination,
                        onSelect = viewModel::selectDestination,
                    )
                }
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = state.route to state.destination,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(260, delayMillis = 60), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "screen",
            ) { (route, destination) ->
            when (route) {
                AppRoute.HOME -> when (destination) {
                    AppDestination.HOME -> HomeScreen(state, viewModel, Modifier.padding(innerPadding), upcoming)
                    AppDestination.REMINDERS -> com.alarming.feature.reminders.RemindersScreen(Modifier.padding(innerPadding), waterOnly = false, model = remindersModel)
                    AppDestination.WATER -> com.alarming.feature.reminders.RemindersScreen(Modifier.padding(innerPadding), waterOnly = true, model = remindersModel)
                    AppDestination.STOPWATCH -> StopwatchScreen(state, viewModel, Modifier.padding(innerPadding))
                    AppDestination.SETTINGS -> com.alarming.feature.settings.SettingsScreen(settingsModel, Modifier.padding(innerPadding))
                }
                AppRoute.EDITOR -> TaskEditorScreen(state, viewModel, Modifier.padding(innerPadding))
                AppRoute.TIMER -> ActiveTimerScreen(state, viewModel, settings.keepScreenOn, Modifier.padding(innerPadding))
                AppRoute.SOUNDS -> SoundPickerScreen(state, viewModel, Modifier.padding(innerPadding))
                AppRoute.COUNTER -> CounterScreen(state, viewModel, Modifier.padding(innerPadding))
            }
            }
        }
        }
    }
}

@Composable
private fun AppNavigationBar(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    val items = listOf(
        Triple(AppDestination.HOME, Icons.Filled.Home, "Home"),
        Triple(AppDestination.REMINDERS, Icons.Filled.NotificationsActive, "Reminders"),
        Triple(AppDestination.WATER, Icons.Outlined.WaterDrop, "Water"),
        Triple(AppDestination.STOPWATCH, Icons.Outlined.Timer, "Stopwatch"),
        Triple(AppDestination.SETTINGS, Icons.Outlined.Settings, "Settings"),
    )
    Box(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(shape = RoundedCornerShape(30.dp), elevation = 16.dp),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
        ) {
            items.forEach { (destination, icon, label) ->
                NavigationBarItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(label, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                        selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: PrototypeState,
    viewModel: PrototypeViewModel,
    modifier: Modifier = Modifier,
    upcoming: com.alarming.feature.reminders.ReminderRecord? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.alarming.R.drawable.ic_stagebell_mark),
                    contentDescription = "StageBell water bell logo",
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("STAGEBELL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TIME, IN STAGES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Text("Make time\nmove with you.", style = MaterialTheme.typography.headlineLarge)
            Text(
                "A focused task, divided into clear intervals.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = viewModel::openEditor,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = CapsuleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create interval task", style = MaterialTheme.typography.titleMedium)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Your intervals", "${state.savedTasks.size.toString().padStart(2, '0')} SAVED")
            com.alarming.feature.intervaltimer.TaskTransferControls(state.savedTasks.size)
            if (state.savedTasks.isEmpty()) {
                Text("Create an interval task to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.savedTasks.forEach { task ->
                GlassCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(task.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${task.slots.size} stages · ${formatTaskDuration(task.totalDurationSeconds)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${task.name}")
                            }
                        }
                        StageTrack(task.slots.size)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.startSavedTask(task.id) }, modifier = Modifier.weight(1f).height(46.dp), shape = CapsuleShape) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Start")
                            }
                            TextButton(onClick = { viewModel.editTask(task.id) }, modifier = Modifier.height(46.dp)) { Text("Edit") }
                            com.alarming.feature.intervaltimer.ShareTaskButton(task.id)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Coming up", "REMINDER")
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(upcoming?.title ?: "No upcoming reminders", style = MaterialTheme.typography.titleMedium)
                        Text(upcoming?.let { com.alarming.feature.reminders.recordSummary(it) } ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { viewModel.selectDestination(AppDestination.REMINDERS) }) {
                        Text("View")
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StageTrack(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count.coerceIn(1, MAX_SLOTS_PER_TASK)) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(if (index == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, meta: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TaskEditorScreen(
    state: PrototypeState,
    viewModel: PrototypeViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DetailHeader("New interval task", onBack = viewModel::goBack)
        OutlinedTextField(
            value = state.taskName,
            onValueChange = viewModel::updateTaskName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Task name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::updateDescription,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description (optional)") },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Stages", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("${state.slots.size} / $MAX_SLOTS_PER_TASK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::addSlot, enabled = state.slots.size < MAX_SLOTS_PER_TASK) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add stage")
            }
        }

        state.slots.forEachIndexed { index, slot ->
            SlotEditorCard(
                slot = slot,
                index = index,
                count = state.slots.size,
                sharedSoundEnabled = state.isSharedSound,
                sharedSound = state.sharedSound,
                onNameChange = { viewModel.updateSlotName(slot.id, it) },
                onMinutesChange = { viewModel.updateSlotDuration(slot.id, minutes = it) },
                onSecondsChange = { viewModel.updateSlotDuration(slot.id, seconds = it) },
                onMoveUp = { viewModel.moveSlot(slot.id, -1) },
                onMoveDown = { viewModel.moveSlot(slot.id, 1) },
                onRemove = { viewModel.removeSlot(slot.id) },
                onChooseSound = { viewModel.openSoundPicker(slot.id) },
            )
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("One sound for every stage", style = MaterialTheme.typography.titleMedium)
                        Text("Turn off to choose per-stage tones", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.isSharedSound, onCheckedChange = viewModel::setSharedSoundEnabled)
                }
                if (state.isSharedSound) {
                    TextButton(onClick = { viewModel.openSoundPicker() }) {
                        Icon(Icons.Outlined.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("${com.alarming.audio.toneDisplayName(state.sharedSound)}  ·  Choose tone")
                    }
                }
            }
        }

        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Start and finish", style = MaterialTheme.typography.titleMedium)
                AlertSoundRow(
                    title = "Start alert",
                    sound = state.startSound,
                    enabled = state.startAlertEnabled,
                    onEnabledChange = viewModel::setStartAlertEnabled,
                    onChooseSound = { viewModel.openSoundPicker(target = SoundTarget.START) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                AlertSoundRow(
                    title = "Completion alert",
                    sound = state.completionSound,
                    enabled = state.completionAlertEnabled,
                    onEnabledChange = viewModel::setCompletionAlertEnabled,
                    onChooseSound = { viewModel.openSoundPicker(target = SoundTarget.COMPLETION) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vibration", style = MaterialTheme.typography.titleMedium)
                Text("Optional haptic feedback at transitions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.vibrationEnabled, onCheckedChange = viewModel::setVibrationEnabled)
        }

        GlassCard(tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TOTAL TIME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f))
                    Text(formatTaskDuration(state.totalDurationSeconds), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
                Text("5 hr max / stage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
        }

        if (!state.taskIsValid) {
            Text(
                "Add a task name and set every stage to a duration from 1 second to 5 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        state.taskSaveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::saveTask, enabled = state.taskIsValid, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Save task")
            }
            Button(onClick = viewModel::startTimer, enabled = state.taskIsValid, modifier = Modifier.weight(1.3f).height(52.dp), shape = CapsuleShape) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Start now")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AlertSoundRow(
    title: String,
    sound: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onChooseSound: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onChooseSound, enabled = enabled) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(com.alarming.audio.toneDisplayName(sound))
            }
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
private fun SlotEditorCard(
    slot: SlotDraft,
    index: Int,
    count: Int,
    sharedSoundEnabled: Boolean,
    sharedSound: String,
    onNameChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onChooseSound: () -> Unit,
) {
    GlassCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = slot.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Stage name") },
                    singleLine = true,
                    shape = CapsuleShape,
                )
                IconButton(onClick = onMoveUp, enabled = index > 0) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move stage up")
                }
                IconButton(onClick = onMoveDown, enabled = index < count - 1) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move stage down")
                }
                IconButton(onClick = onRemove, enabled = count > 1) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove stage")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = slot.minutesText,
                    onValueChange = onMinutesChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Minutes") },
                    suffix = { Text("min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = CapsuleShape,
                )
                OutlinedTextField(
                    value = slot.secondsText,
                    onValueChange = onSecondsChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Seconds") },
                    suffix = { Text("sec") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = CapsuleShape,
                    isError = slot.durationSeconds == null,
                )
            }
            if (slot.durationSeconds == null) {
                Text("Enter a positive duration up to 5 hours.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!sharedSoundEnabled) {
                TextButton(onClick = onChooseSound) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("${com.alarming.audio.toneDisplayName(slot.soundName.ifBlank { sharedSound })}  ·  Change tone")
                }
            }
        }
    }
}

@Composable
private fun SoundPickerScreen(
    state: PrototypeState,
    viewModel: PrototypeViewModel,
    modifier: Modifier = Modifier,
) {
    val selected = when (state.soundTarget) {
        SoundTarget.SHARED -> state.sharedSound
        SoundTarget.START -> state.startSound
        SoundTarget.COMPLETION -> state.completionSound
        SoundTarget.SLOT -> state.slots.firstOrNull { it.id == state.soundTargetSlotId }?.soundName ?: state.sharedSound
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DetailHeader("Choose a tone", onBack = viewModel::goBack)
        com.alarming.audio.TonePickerContent(selected, viewModel::chooseSound)
        Button(onClick = viewModel::goBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CapsuleShape) {
            Text("Done")
        }
    }
}

@Composable
private fun ActiveTimerScreen(
    state: PrototypeState,
    viewModel: PrototypeViewModel,
    keepScreenOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val toneIds = remember(state.slots, state.isSharedSound, state.sharedSound, state.startSound, state.completionSound) {
        buildSet {
            add(state.startSound)
            if (state.isSharedSound) add(state.sharedSound) else state.slots.forEach { add(it.soundName) }
            add(state.completionSound)
        }
    }
    val running = state.timerStatus == TimerStatus.RUNNING
    val cuePlayer = com.alarming.audio.rememberPreciseCuePlayer(toneIds, keepWarm = running)
    LaunchedEffect(cuePlayer) {
        viewModel.timerCues.collect { cue -> cuePlayer.playNow(cue.soundId) }
    }
    LaunchedEffect(cuePlayer) {
        viewModel.upcomingCue.collect { cue -> cuePlayer.schedule(cue) }
    }
    LaunchedEffect(state.timerStatus) {
        while (viewModel.state.value.timerStatus == TimerStatus.RUNNING) {
            viewModel.refreshTimer()
            delay(viewModel.millisUntilNextTick() + 1)
        }
    }
    // The window flag stops the display timeout only; pressing the power button still turns the screen off.
    val window = (LocalContext.current as? Activity)?.window
    val holdScreenOn = keepScreenOn && running
    DisposableEffect(window, holdScreenOn) {
        if (holdScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val currentSlot = state.slots.getOrNull(state.activeSlotIndex)
    val slotDuration = currentSlot?.durationSeconds?.toLong()?.times(1_000L) ?: 1L
    val slotProgress = if (currentSlot == null) 0f else {
        (1f - state.remainingMillis.toFloat() / slotDuration.toFloat()).coerceIn(0f, 1f)
    }
    val completedBefore = state.slots.take(state.activeSlotIndex).sumOf { it.durationSeconds ?: 0 }
    val totalProgress = if (state.totalDurationSeconds == 0) 0f else {
        ((completedBefore + slotProgress * (currentSlot?.durationSeconds ?: 0)) / state.totalDurationSeconds).coerceIn(0f, 1f)
    }
    val nextSlot = state.slots.getOrNull(state.activeSlotIndex + 1)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::stopTimer) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Stop and return")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.taskName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${state.activeSlotIndex + 1} OF ${state.slots.size} STAGES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(48.dp))
        }

        if (state.timerStatus == TimerStatus.COMPLETED) {
            val ringColor = MaterialTheme.colorScheme.secondary
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(ringColor, style = Stroke(width = 14.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("All done", style = MaterialTheme.typography.headlineMedium)
                    Text(state.taskName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("TASK COMPLETE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Button(onClick = viewModel::stopTimer, modifier = Modifier.fillMaxWidth().height(54.dp), shape = CapsuleShape) {
                Text("Finish")
            }
        } else {
            currentSlot?.let { slot ->
                Text(slot.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val progressColor = MaterialTheme.colorScheme.tertiary
            Box(Modifier.size(244.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val strokeWidth = 13.dp.toPx()
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidth),
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = slotProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatClock(state.remainingMillis), style = MaterialTheme.typography.displayLarge)
                    Text("REMAINING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TASK PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${(totalProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { totalProgress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            GlassCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (nextSlot == null) "FINAL STAGE" else "UP NEXT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(nextSlot?.name ?: "Task complete", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(nextSlot?.durationSeconds?.let(::formatTaskDuration) ?: "", style = MaterialTheme.typography.titleMedium)
                }
            }

            Text("Preview mode · alerts and recovery are not scheduled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = {
                        if (state.timerStatus == TimerStatus.PAUSED) viewModel.resumeTimer() else viewModel.pauseTimer()
                    },
                    modifier = Modifier.weight(1.2f).height(52.dp),
                    shape = CapsuleShape,
                ) {
                    Icon(if (state.timerStatus == TimerStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.timerStatus == TimerStatus.PAUSED) "Resume" else "Pause")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = viewModel::skipSlot,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = CapsuleShape,
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Skip")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = viewModel::restartSlot,
                    modifier = Modifier.size(52.dp),
                    shape = CapsuleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = "Restart stage")
                }
            }
            TextButton(onClick = viewModel::stopTimer) {
                Icon(Icons.Filled.Stop, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Stop task")
            }
        }
    }
}

@Composable
private fun CounterScreen(
    state: PrototypeState,
    viewModel: PrototypeViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailHeader("Counter", onBack = viewModel::goBack)
        Spacer(Modifier.weight(0.35f))
        Text("WATER GLASSES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        Text(state.counterValue.toString(), style = MaterialTheme.typography.displayLarge)
        Text("today", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedButton(onClick = viewModel::decrementCounter, modifier = Modifier.size(64.dp), shape = CircleShape) {
                Text("−", style = MaterialTheme.typography.headlineMedium)
            }
            Button(onClick = viewModel::incrementCounter, modifier = Modifier.size(116.dp), shape = CircleShape) {
                Icon(Icons.Filled.Add, contentDescription = "Increment counter", modifier = Modifier.size(34.dp))
            }
        }
        TextButton(onClick = viewModel::resetCounter) { Icon(Icons.Filled.Refresh, contentDescription = null); Spacer(Modifier.width(5.dp)); Text("Reset") }
        Spacer(Modifier.weight(0.65f))
    }
}

@Composable
private fun StopwatchScreen(
    state: PrototypeState,
    viewModel: PrototypeViewModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.stopwatchRunning, state.destination) {
        while (state.stopwatchRunning && state.destination == AppDestination.STOPWATCH) {
            viewModel.refreshStopwatch()
            delay(40)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Stopwatch", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))
        Text(formatStopwatch(state.stopwatchElapsedMillis), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"))
        Text(if (state.stopwatchRunning) "RUNNING" else "READY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { if (state.stopwatchRunning) viewModel.pauseStopwatch() else viewModel.startStopwatch() },
                modifier = Modifier.height(50.dp),
                shape = CapsuleShape,
            ) {
                Icon(if (state.stopwatchRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (state.stopwatchRunning) "Pause" else "Start")
            }
            androidx.compose.material3.OutlinedButton(onClick = viewModel::recordLap, enabled = state.stopwatchRunning, modifier = Modifier.height(50.dp), shape = CapsuleShape) {
                Text("Lap")
            }
            androidx.compose.material3.OutlinedButton(onClick = viewModel::resetStopwatch, modifier = Modifier.height(50.dp), shape = CapsuleShape) {
                Text("Reset")
            }
        }
        if (state.stopwatchLaps.isNotEmpty()) {
            HorizontalDivider()
            state.stopwatchLaps.forEachIndexed { index, lap ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lap ${state.stopwatchLaps.size - index}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatStopwatch(lap), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
        Text("Elapsed time uses a monotonic clock in this preview; persistence is not wired yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatClock(milliseconds: Long): String {
    // Round up so the display reaches 00:00 exactly when the stage boundary (and its tone) fires.
    val seconds = max(0L, (milliseconds + 999L) / 1_000L)
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder) else "%02d:%02d".format(minutes, remainder)
}

private fun formatTaskDuration(seconds: Int): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return when {
        hours > 0 && remainder > 0 -> "${hours}h ${minutes}m ${remainder}s"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 && remainder > 0 -> "${minutes}m ${remainder}s"
        minutes > 0 -> "${minutes} min"
        else -> "${remainder} sec"
    }
}

private fun formatStopwatch(milliseconds: Long): String {
    val totalCentiseconds = max(0L, milliseconds / 10L)
    val hours = totalCentiseconds / 360_000
    val minutes = (totalCentiseconds / 6_000) % 60
    val seconds = (totalCentiseconds / 100) % 60
    val centiseconds = totalCentiseconds % 100
    return if (hours > 0) "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centiseconds)
    else "%02d:%02d.%02d".format(minutes, seconds, centiseconds)
}
