# StageBell: Interval Timer

Navigation now includes a dedicated **Water** tab with start/end times, an interval (default two hours), and repeat weekdays. **Reminders** lists ordinary reminders separately. Both editors offer a prominent time/on-off card and tone selection, preview, and import. Home shows saved interval tasks and the next reminder, without a counter shortcut.

StageBell is an offline-first Android interval timer prototype. The main flow lets you edit ordered task stages, choose shared or per-stage tones, and exercise an active timer with pause, resume, skip, and restart controls.

## Prototype Boundary

Interval task definitions and ordered stages are saved in Room. The starter Morning Exercise task is 21 minutes; **Create interval task** opens a separate blank draft with one five-minute stage. In the editor, **Add stage** is beside the stage count; each task supports up to 50 stages, each up to five hours. Saving creates or updates only that task, and Home lists tasks separately. Counters and stopwatch still use in-memory state.

While a task is running the screen stays on (Settings → Keep screen on); pausing or pressing the power button releases it. Stage-boundary tones are started on an audio-priority thread at the exact boundary. Active interval sessions are not persisted yet. The preview uses `elapsedRealtime()` while the process is alive but does not schedule background stage alarms or recover sessions after process death/reboot. Do not rely on the interval timer for real alarms.

Reminders are persisted in Room and support one-time, daily, weekly, and water schedules with notification alerts, edit/delete/complete/enable controls, and recovery. Water defaults to 08:00–20:00 every two hours; both times and the interval are editable. Completing a recurring reminder disables the whole schedule. The original 14 tones and 20 supplied tones are bundled; retro sounds are original, not Nokia melodies.

Enable notifications in the Reminders tab. Optionally grant precise-alarm access there; otherwise alerts use inexact scheduling and can be delayed. DND, mute, notification-channel settings, Doze quotas, and OEM restrictions still apply. Force-stopping the app or revoking exact-alarm access may remove its alarms; reopen it to reconcile. No DND bypass or always-running service is used. Physical-device delivery/reboot behavior has not been certified.

## Own Tones

Use **Settings → Tone library → Import tone**. Settings also manages default tones, deletes tones, and holds keep-screen-on, theme, vibration, and notification/precise-alarm controls. Soft bell, Focus tone, Completion chime, the supplied Temple bell, and Aarti bells are protected. Other tones cannot be deleted while used by a task, reminder, or default setting. Removing a bundled tone hides it from the library but does not reduce the installed app size; imported tone files are deleted. The Android file picker grants access only to the chosen file; no broad storage permission is requested.

Supported extensions: MP3, WAV, M4A, AAC, OGG, and FLAC, subject to the device's audio decoder. Clips must be playable, nonempty, no longer than five seconds, and no larger than 5 MB. Longer tracks must be shortened before importing; the app does not trim them automatically. Corrupt, unreadable, unsupported, or oversized files are rejected. The supplied clips are resampled to 22.05 kHz to reduce bundled size; imported files are kept in their original format.

The app copies each import into private storage and saves its metadata in Room. Deleting or moving the original file does not remove the imported copy. Imports are lost if app data is cleared or the app is uninstalled. Reminder notification playback uses a scoped content URI and remains subject to Android's sound/channel settings; physical-device playback with imported sounds still needs validation.

## Sharing and backup

On Home, use **Share** on a task to send a ZIP through an available sharing app, or **Export all** to save an archive. Use **Import tasks** to select an archive on the receiving device. The recipient needs StageBell and imports the file manually. Each archive contains the task definitions and every referenced tone, including bundled and imported audio. Import creates new tasks and private tone copies; it does not overwrite existing tasks. Repeated imports create additional copies.

An archive supports up to 100 tasks, 200 distinct tones, and 32 MB of uncompressed content; each tone remains limited to 5 MB and five seconds. Missing, invalid, oversized, or unsupported audio causes import or export to fail rather than silently dropping a tone. Transferring a file through another app is subject to that app's privacy and network behavior.

## Build Requirements

- JDK 17 or newer
- Android Gradle Plugin 9.5.0, which requires Gradle 9.6.0
- Android SDK Platform 37 and Android SDK Build Tools 36.0.0
- Android Studio with Android support for API 37, or the equivalent command-line SDK packages

The Gradle wrapper is pinned to 9.6.0. Install JDK 17 and Android SDK Platform 37.0 with Build Tools 36.0.0, set `JAVA_HOME`, and point `local.properties` (`sdk.dir=...`) or `ANDROID_HOME` at the SDK. Then build from the project root:

```sh
./gradlew assembleDebug
```

The debug APK will be written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Run the scheduling tests with `./gradlew testDebugUnitTest`. With an emulator/device connected, run `./gradlew connectedDebugAndroidTest` for Room persistence and delivery-idempotency tests. Regenerate original WAV files with `node tools/generate_tones.mjs`.

## Product Specification

### Product Goal

StageBell is a minimalist, offline-first Android productivity tool. Its primary job is to let someone create one task, split it into sequential timed stages, assign a shared or stage-specific short tone, and complete the stages without manually starting each one. The active timer must make the task, current stage, remaining time, progress, next stage, and expected finish immediately understandable.

### Product Principles

- Interval timer first; supporting tools stay simple and secondary.
- Fast, quiet, one-hand-friendly interactions; useful with little content on screen.
- Native Android behavior, dependable timing, and clear permission explanations.
- No account, advertisements, analytics, tracking, cloud dependency, or required network access.
- Restrained, distinctive Material 3 design with deliberate light and dark themes, readable type, generous spacing, and subtle transitions.
- Use a glossy, water-textured bell for the app icon and home mark; retain the simple monochrome notification bell.

### Modules

1. **Home and interval tasks:** create/edit/duplicate tasks, manage ordered slots, save/reuse templates, start a task, and view saved tasks and recent activity. Each slot has a name, positive duration in seconds, optional tone override, and optional vibration. Task name is required; description is optional. Shared tone, start tone, completion tone, and mute are configurable. Total duration is derived from slots.
2. **Active interval timer:** show task, slot position/name, large remaining time, slot and task progress, next slot/duration, and task finish estimate. Support pause/resume, skip, restart current slot, and stop. Advance automatically and alert at each boundary; finish with a distinct completion state and tone.
3. **Reminders:** one-time, daily, or weekly title/description/date-time reminders with tone, vibration, completion, edit, delete, enable/disable, and an upcoming list. Water reminders repeat daily within a same-day start/end window, defaulting to 08:00–20:00 every 120 minutes. End is included only if it falls on an interval boundary. Completing a reminder disables its entire schedule; dismissing a notification leaves recurring schedules enabled. Saved schedules retain their creation time zone.
4. **Counter:** multiple named saved counters with custom initial values, increment, decrement, reset, persistence, and optional haptics.
5. **Stopwatch:** start, pause/resume, reset, laps, and persisted elapsed-time calculation when backgrounded.
6. **Settings:** defaults for sounds, vibration, appearance, and concise notification/exact-alarm status and explanations. Appearance offers Teal (default), Lavender, Rose, and Graphite palettes, each with independent light/dark mode.

Bottom navigation: Home, Reminders, Water, Stopwatch. The droplet opens dedicated water schedules, not Counter. Counter remains reachable from Home. Ordinary reminders and water schedules are listed separately. Reminder setup includes a prominent time/on-off card, selected repeat weekdays, tone selection/preview/import, and vibration. Water adds editable start/end times and interval, defaulting to 08:00–20:00 every 120 minutes.

### Sound and Feedback

Bundle original two-second tones (soft bell, digital beep, classic tick, double bell, temple bell, Om hum, focus tone, completion chime). Support preview, shared defaults, per-slot overrides, silent mode, and optional vibration. Do not fetch media dynamically or use copyrighted music. Respect system volume, audio focus, notification-channel controls, mute, and Do Not Disturb; never request permission to bypass DND.

The bundled catalog also includes six original feature-phone-style options: Retro dual-tone, Pocket polyphony, Mobile chirp, Retro pager, Keypad cascade, and Pocket pulse. These are not Nokia recordings or reproductions of Nokia melodies. Reminder alerts use notification channels; user channel settings override the app's sound/vibration choice.

Users may import their own MP3, WAV, M4A/AAC, OGG, or FLAC clips through the Android file picker, subject to device decoding support. Limit imports to five seconds and 5 MB; reject empty, unreadable, corrupt, or unsupported files. Copy accepted files into app-private storage and persist metadata, so source-file deletion does not break saved tones. Expose imports in both interval and reminder tone selectors with preview and readable names; do not request broad storage permission or fetch audio dynamically.

Add the 20 supplied two-second clips to the existing catalog. Only Soft bell, Focus tone, Completion chime, supplied Temple bell, and Aarti bells are protected from deletion. Reject deletion of tones referenced by a task, reminder, or default setting. Removing a bundled tone hides it rather than reducing installed package size.

Share individual tasks via the Android share sheet and export/import multiple tasks through the document picker. A versioned archive carries every referenced tone, whether imported or bundled. Import creates new task and tone copies rather than replacing existing data. The archive is limited to 100 tasks, 200 tones, and 32 MB expanded; tone imports retain the five-second/5 MB limits.

### Timing and Platform Expectations

- Remaining time is calculated from a deadline, not a tick counter. UI refresh cadence only affects display smoothness.
- Persist active session state and schedule slot boundaries. Recover after process death; reconcile elapsed boundaries rather than replaying a burst of missed sounds.
- Use monotonic time (`elapsedRealtime`) for precision during one boot. Reboot recovery needs a documented wall-clock policy because monotonic time resets.
- Limit each slot to 5 hours and each task to 50 slots. Reject zero or negative durations and show validation before saving or starting.
- On reboot, continue from the saved wall-clock deadline, reconcile to the correct slot or completion state, and do not replay missed transition tones.
- For recurring reminders, preserve local wall-clock time in the selected time zone; move nonexistent spring-forward times to the next valid instant and use the first occurrence of duplicated fall-back times.
- Android may delay or suppress delivery depending on exact-alarm access, notification permission, power settings, OS version, and manufacturer behavior. Explain limitations and provide a graceful degraded mode; do not promise perfect delivery in all conditions.
- Minimum SDK: API 26; target the latest stable SDK available at implementation time. Validate this against the actual build environment before project scaffolding.

### Quality Bar

Critical automated coverage includes cumulative duration, zero/invalid duration rejection, ordered transitions, per-slot/shared tones, pause/resume, skip/restart, completion, missed-boundary reconciliation, process recovery, reboot policy, reminder scheduling/recurrence, counter persistence, and stopwatch elapsed-time behavior. Verify layouts across small and large screens and light/dark modes. Physical-device testing is needed for alarm delivery, permission flows, audio, reboot recovery, and manufacturer battery restrictions.

## Architecture

### Task Transfer And Tone Catalog

`TaskArchive` writes a versioned ZIP containing task fields, ordered stages, and bytes for every referenced bundled or imported tone. Sharing uses a scoped FileProvider cache URI; export/import use the Android document picker. Import validates entry paths, counts, sizes, task durations, tone references and device decoding, then creates new private tone copies and tasks inside a Room transaction. Files created before a failed transaction are cleaned up. Limits are 100 tasks, 200 distinct tones, 32 MB expanded and 5 MB/five seconds per audio file. Repeated imports create additional copies. The receiver manually imports with StageBell.

The supplied 20 WAV files are resampled to 22.05 kHz; existing tone IDs are unchanged and supplied IDs are distinct, including the two Temple bells. Five selected tones are protected. Other bundled tones are hidden through DataStore, while imported files are deleted; both are blocked when referenced by stored tasks, reminders or default settings. Hiding bundled resources does not shrink the installed APK. The application provides Teal (default), Lavender, Rose, and Graphite palettes with light and dark variants, plus a glossy water-textured bell adaptive icon.

### Water And Reminder Update

Room schema version 4 adds `daysOfWeekMask` through migration 3-to-4, preserving previous reminder, tone, and task records. A zero mask retains legacy behavior; new recurring schedules persist explicit Monday-to-Sunday selections. Daily, weekly, and water scheduling honor selected weekdays and the existing DST policy. Valid one-time alarm delivery completes the reminder instead of recomputing a future occurrence.

Bottom destinations are Home, Reminders, Water, and Stopwatch. Counter is a Home utility route. Water and ordinary reminders share storage/scheduling but filter their lists and editors by schedule type. Editors expose a prominent time/enable card, weekday circles, tone preview/import, and vibration; Water also exposes start/end and interval. The updated build and JVM tests pass, and the full nine-test instrumentation suite passed on the OnePlus Android 13 and API 37 emulator. Emulator visual checks confirmed Water time, weekday, interval, and tone controls. Active interval-session persistence/background delivery remains unfinished.

### Status

Stage 1 architecture approved. A single-module Android Compose app now includes persisted reminder schedules and task definitions. Active timer sessions, counters, and stopwatch remain prototype-only; approved choices below remain the implementation baseline.

Reminders have now been implemented at the user's request, ahead of the remaining interval-engine stage. `feature.reminders` owns a Room database, ViewModel, schedule calculator, editor/list, and AlarmManager/notification adapters. Other feature state remains in-memory.

### Interval Task Persistence

- Schema version 3 adds `interval_tasks` and ordered `interval_task_slots`; migration 2-to-3 preserves reminder/tone tables and inserts no destructive reset.
- `IntervalTaskStore` saves a task and all its stages transactionally, validates 1–50 stages and 1-second–5-hour durations, and cascades task deletion to its stages.
- A first-run starter task remains the separate 21-minute Morning Exercise sample. Create opens a blank five-minute, single-stage draft; Add Stage is beside the stage count, and Home lists each saved task independently. Edit loads by task ID; Save replaces only that task's ordered stages.
- Active timer state and its deadline are still process memory only. Task-definition durability does not imply reliable background timing; Stage 3 must implement persisted session deadlines and alarms.
- Two OnePlus API-device instrumentation tests verify independent task/slot persistence and edit isolation. Manual phone testing confirmed adding two stages, saving a 3-stage/15-minute task, and preserving the 4-stage/21-minute starter.

### Reminder Implementation

- `ReminderRecord` stores one-time/daily/weekly/water schedules, local first date, start/end minutes, interval, time zone, tone, vibration, enable/completion state, and next-trigger instant. `ReminderStore` serializes changes and persists before scheduling.
- One PendingIntent per reminder is identified by a unique URI. Each delivery carries its expected trigger; stale and duplicate delivery is ignored. Recurring delivery advances to the next future local-time boundary, not delivery-time plus interval. Recurring alerts over five minutes late are skipped; overdue one-time reminders are delivered on reconciliation.
- Recovery runs on app startup/resume, boot after unlock, clock/time-zone broadcasts, and exact-alarm access grant. Saved time zones are retained, with DST gaps resolved to the next valid instant and overlaps using the earlier occurrence. Android force-stop and exact-alarm revocation can remove scheduled work; open the app again to reconcile it. No foreground service or background tick loop is used.
- Notification permission is requested through an explicit control. Without exact access use `setAndAllowWhileIdle`; with access use `setExactAndAllowWhileIdle`. Doze quotas, especially for short intervals, and OEM restrictions can still delay delivery. Per-tone/vibration notification channels respect DND, system volume, and channel settings; Silent uses no sound.
- Room annotation processing currently uses AGP's legacy kapt with a Kotlin 2.4 metadata reader. The database is version 3, with tested 1-to-2 and 2-to-3 migrations adding imported tones and interval task tables without altering existing reminders. No destructive migrations are enabled.
- Twelve JVM tests and eight API-device instrumentation tests cover scheduling, DST, tone import, reminder persistence/recovery, and interval task/slot persistence. Physical reboot, audible delivery, OEM power management, and older-API behavior remain unverified.

### Home Scope

Home exposes task creation, saved interval tasks, and the upcoming reminder. The counter shortcut is removed; its internal implementation remains unused.

### Shared Reminder State

Home, Reminders, and Water share the activity's `RemindersViewModel` explicitly. Reminder deletion reports success only after the Room deletion completes and the shared records flow excludes the deleted ID. Its confirmation remains open while pending and displays failures, preventing an immediate navigation away from presenting deletion as finished before shared state updates.

### Imported Tones

`audio.ToneLibrary` uses Storage Access Framework `OpenDocument` selection and bounded copies into `files/tones`. Room persists UUID-based IDs, original display names, filenames, and durations. Built-in IDs remain unchanged; imported IDs are stored in the existing reminder sound field. Media metadata, an audio track, and an available decoder are checked before accepting a clip (up to five seconds and 5 MB). Source permissions need not persist after copying.

Import and deletion of tones live in the Settings tab; task and reminder pickers only select and preview. Both selectors share MediaPlayer preview controls, releasing the player when the screen exits and resetting it for the next preview. A non-exported FileProvider exposes the tone directory and task-export cache through scoped URIs. Missing imports resolve to silent notifications and show an unavailable label in the selector. Real-device notification sound playback is not yet certified.

### Foreground Timer Cues And Display

- `PrototypeViewModel` publishes `upcomingCue` (tone ID plus the `elapsedRealtime` deadline of the current stage) whenever the deadline changes: start, resume, skip, restart, automatic advance, pause/stop (cleared). `nextBoundaryCue` is a pure function covered by JVM tests.
- `audio.PreciseCuePlayer` starts boundary cues from an `THREAD_PRIORITY_URGENT_AUDIO` `HandlerThread` at the exact deadline, independent of the UI tick and main-thread work. Samples are preloaded into `SoundPool`; unloaded tones fall back to MediaPlayer. Overdue cues play once; cues more than 1.5 s late (e.g. CPU slept) are dropped rather than replayed. Start and manual-skip cues remain immediate.
- While the timer is running, a looping silent low-latency static `AudioTrack` with the same notification attributes keeps the audio output out of standby, removing wake-up delay before each cue. It stops on pause/stop/completion.
- Bundled tones are generated with their first onset at sample 0 (`tools/generate_tones.mjs`); slow-attack tones such as Om hum still swell in by design.
- When the "Keep screen on" setting is enabled (default), the activity window holds `FLAG_KEEP_SCREEN_ON` only while the timer is running; the power button still turns the screen off, and pausing releases the hold.
- Interval cues still play only while the timer screen is composed and the process is awake; they are not AlarmManager-backed yet.

### Settings And Visual Style

- `feature.settings.SettingsScreen` centralizes keep-screen-on, light/dark mode and the persisted Teal/Lavender/Rose/Graphite palette choice, default stage/start/completion/reminder tones (bottom-sheet picker with preview), the tone library (import, preview, delete imported tones with confirmation; built-in list collapsible), default vibration, and notification/precise-alarm status with actions. Reminders keeps its contextual permission warning.
- `ui/Glass.kt` provides the "liquid glass" style: an ambient water-tinted gradient background, translucent cards with a top-lit rim and soft shadow, and a floating capsule navigation bar. Screen changes use short fade/scale transitions; no continuous animations run, to avoid battery cost while the screen is held on.

### Stack and Shape

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, lifecycle ViewModels, Kotlin coroutines and Flow.
- One Android application module initially. Organize by responsibility/feature packages (`core`, `data`, `domain`, `feature.home`, `feature.intervaltimer`, `feature.reminders`, `feature.counter`, `feature.stopwatch`, `feature.settings`, `notification`, `audio`, `scheduling`). Split Gradle modules only if real build or ownership needs emerge.
- Room is the source of durable tasks, slots, sessions, reminders, counters, and laps. DataStore holds app preferences (theme and defaults). Repositories expose Flow and suspend operations. Domain use cases/state reducers hold timer rules; Compose renders immutable state and sends user actions to ViewModels.
- Prefer constructor injection/lightweight manual wiring initially; add Hilt only if the dependency graph justifies it. Keep dependencies small and no network stack.

### Interval Timing: Core Design

A persisted session is a state machine (`Idle`, `Running`, `Paused`, `Completed`, `Stopped`) with task ID, current slot index, slot start/deadline, pause remainder, session origin/deadline, and boot metadata. The domain engine validates positive slot durations, derives total duration, and applies explicit actions/events for start, pause, resume, skip, restart, stop, and alarm delivery. Transitions are deterministic and independently unit-testable.

Within a boot, `SystemClock.elapsedRealtime()` is authoritative. Store an elapsed-time deadline for accurate remaining time and a corresponding wall-clock deadline plus boot count for scheduling/recovery. The UI derives remaining/progress from the deadline; a lifecycle-aware ticker only requests redraws and is never the source of elapsed time. Persist every state-changing action and alarm boundary transactionally.

Use AlarmManager for user-visible slot boundaries and scheduled reminders; do not use WorkManager for precise delivery. Schedule one next boundary at a time and reconcile the session against its timeline when an alarm arrives, so delayed delivery or process death cannot cause cumulative drift or a rapid replay of old alerts. Persist pending state before scheduling, use stable PendingIntent identities, and reschedule after reboot/time-zone changes as appropriate. Receiver work should be short: reconcile/persist, emit an alert notification on the appropriate channel, and schedule the next boundary.

**Approved reboot semantics.** A timer continues across device power-off according to its persisted wall-clock deadline, then recovers into the correct slot or completion state at boot. Monotonic time is used again after boot. This cannot distinguish a real powered-off interval from manual wall-clock changes, so recovery follows wall time; do not emit every missed transition tone.

### Android Delivery and Audio

- Android 12+ exact alarms may require the user to grant `SCHEDULE_EXACT_ALARM` special access; availability and revocation must be checked. `USE_EXACT_ALARM` is restricted by platform/store policy and should not be assumed. Explain why access improves timer/reminder delivery, request only when user enables precise alerts, and fall back to inexact scheduling with honest UI status.
- Android 13+ notification permission is runtime-controlled. A denied permission means alerts/status notifications may not appear; explain this and keep in-app timing functional. Exact alarm access does not override notification permission, Doze, DND, volume, or manufacturer restrictions.
- Use notification channels, with channel behavior controlled by the user. For sound choices, use bundled original short audio and an audio layer for foreground preview/playback; ensure background alert behavior respects channel settings and audio policies. Never request DND-policy access or force sound through mute.
- Prefer AlarmManager + receiver + notifications over a permanently running service. A foreground service is not currently proposed: timer UI updates need none, and scheduled boundary alarms cover transitions. Revisit only if device testing or platform requirements show a genuine need; Android background-start and foreground-service-type rules apply.
- Request `RECEIVE_BOOT_COMPLETED` only for restoring persisted user-scheduled work. Reconcile boot count and wall-clock deadlines, and handle exact-alarm permission revocation by canceling/rebuilding schedules and informing the user.

### Data Model (Room)

- `IntervalTaskEntity`: id, name, description, created/updated timestamps, default sound ID, start/completion sound IDs, favorite flag.
- `TaskSlotEntity`: id, task ID (foreign key with cascade), position, name, duration seconds, optional sound override, vibration setting. Enforce stable ordering and positive durations in domain validation.
- `TimerSessionEntity`: id, task ID, current slot index, status, elapsed deadline/start fields, wall-clock checkpoint/deadline, boot count, paused remainder, updated timestamp. Keep at most one active session unless product review decides otherwise.
- `ReminderEntity`: id, title, description, scheduled instant, recurrence rule/time zone, sound ID, vibration, enabled/completed flags, next trigger.
- `CounterEntity`: id, name, current value, initial value, updated timestamp.
- `StopwatchEntity` and `LapEntity`: persisted running/paused anchor, accumulated duration, boot metadata, and ordered lap durations/timestamps. Store elapsed durations, not formatted strings.

Store durations as integer seconds (stopwatch precision as milliseconds/long duration). Store instants in UTC epoch form and recurrence zone/rule explicitly. Derive task total duration from slots rather than maintaining a second mutable value. Migrations and foreign-key/index choices are introduced alongside the Room schema.

### Navigation and UI Structure

- Bottom destinations: Home, Reminders, Counter, Stopwatch; task editing and active timer are focused routes, not extra bottom destinations.
- Home: brand/title and settings, dominant new-timer action, saved/recent task list, next reminder, compact links to secondary utilities.
- Task editor: name/description, ordered slot rows with minute/second inputs, add/remove/reorder, common tone control plus per-slot override, preview, vibration/start/completion controls, total duration, save/template/duplicate actions.
- Active timer: high-contrast large remaining time, task and slot context, circular progress, total progress and finish estimate, next slot, pause/skip/restart/stop. Respect system insets and reachable controls.
- Use Material 3 tokens with custom light/dark palettes, semantic color states, restrained motion, and accessibility labels/content descriptions.

### Test Strategy

- Pure JVM tests for domain duration calculations, state transitions, pause/resume, skip/restart, common and individual tones, completion, delayed/missed alarm reconciliation, and invalid input.
- Room/repository tests for task-slot ordering, cascading deletes, session persistence/recovery, reminder recurrence, counter persistence, and stopwatch/lap persistence.
- Android tests for exact-alarm/notification permission states, receiver scheduling, reboot rescheduling, notification channels, and key Compose workflows where practical.
- Manual physical-device matrix for API 26 through current target, Doze/locked screen, process death, reboot, permission denial/revocation, DND/mute, and at least one manufacturer with aggressive battery management. Emulator tests alone cannot establish OEM delivery behavior.

### Main Risks / Implementation Constraints

1. Exact alarm access is special access and can be denied/revoked; degraded delivery must be explicit.
2. Notification denial/channel muting/DND can make an otherwise correct timer silent or invisible.
3. Recurring reminders use the approved daylight-saving policy: preserve local wall-clock time in the selected time zone, move nonexistent spring-forward time to the next valid instant, and use the first occurrence of duplicated fall-back time.
4. Enforce the approved limits of 5 hours per slot and 50 slots per task in domain validation and the task editor.
5. SoundPool and notification-channel behavior differ; the alert path must be tested on-device and must not imply that per-slot sounds override user channel settings.

### Proposed Delivery Stages

1. Product and architecture review (this document and product spec).
2. UI prototype: home, task editor, sound selection, active timer; sample data and both themes. Task definitions now persist; build and OnePlus layout/flow verification pass.
3. Interval session engine, persisted timing/recovery, and background boundary scheduling tests.
4. Reminder, counter, stopwatch, and settings.
5. Alarm/notification integration, exact-alarm permissions, reboot recovery, device validation.
6. Accessibility/responsive QA, full test run, docs, and debug/release APK build instructions and artifact if the environment supports Android builds.

## Development Progress

### Selectable Color Palettes

- Added Lavender, Rose, and Graphite palettes alongside the existing Teal default. Palette selection persists in DataStore and remains independent of the light/dark setting; system bars and glass background accents follow the active palette.
- Added focused palette selection tests; the unit-test suite and debug APK build pass using the available JDK 17.

### Water-Bell Icon

- Reshaped the shared launcher/home mark into a bell while retaining the blue water gradient and highlights; the monochrome notification icon is unchanged.
- Debug build passed and the updated home mark was inspected on the OnePlus IN2021 (Android 13). The launcher screen itself was not inspected.

### Tone Catalog, Task Transfer, And Appearance

- Added the 20 supplied tones, resampled from 44.1 kHz to 22.05 kHz, alongside the existing catalog. Soft bell, Focus tone, Completion chime, supplied Temple bell, and Aarti bells are protected; deletion of a tone referenced by saved tasks, reminders or defaults is blocked. Bundled deletions hide the resource and do not shrink the APK.
- Raised own-tone imports to five seconds, keeping the existing 5 MB limit and device-decoder checks.
- Added individual task sharing and all-task export/import with a versioned ZIP that carries all referenced audio. Import creates new copies; archives have bounded size/counts and reject invalid or missing audio. Room rolls back failed imports.
- Updated the light/dark palette and adaptive launcher mark to a glossy water drop.
- Debug and instrumentation APKs build; focused Android tests exercise five-second boundaries, tone deletion, task/audio round-trip, invalid ZIP entries and rollback. Emulator share-sheet controls were checked.
- Installed the updated app without clearing data on the OnePlus IN2021 (Android 13). All nine focused tone/archive instrumentation tests passed, including all five protected tones, supplied-tone decoding and task/audio round-trip. Checked the 34-tone catalog, five-second import label, dark and light screens, and the task share sheet; restored the original dark setting. Audible playback and delivery through a sharing app were not verified.

### Home Simplification

- Remove the counter shortcut from Home; retain the existing internal counter implementation without adding another entry point.

### Reminder Deletion And Home Synchronization

- Home, Reminders, and Water explicitly share one `RemindersViewModel`.
- Deletion confirmation stays open with repeat actions disabled until storage deletion succeeds and the shared reminder state no longer contains the deleted ID. Failures remain visible in the dialog.
- Four focused tests (the new deletion-state regression plus three persistence tests) pass on the OnePlus Android 13.
- On the updated phone build, create a temporary water reminder, confirm it appears on Home, delete it in Water, and confirm the first return to Home shows "No upcoming reminders". Remove only the test reminder; preserve other data.
- The originally reported stale card was not reproduced before the change. This update closes the asynchronous confirmation/state-update gap; it does not claim a proven diagnosis of every stale-card occurrence.

### Precise Cues, Screen Hold, Settings, And Glass Style

- [x] Keep the screen on only while an interval task is running (window `FLAG_KEEP_SCREEN_ON`, controlled from Settings, default on).
- [x] Remove 50–80 ms of leading silence from all 14 bundled tones; all now cross −40 dBFS within 7 ms.
- [x] Start boundary and completion cues on an audio-priority thread at the exact `elapsedRealtime` deadline; keep the audio output warm with a silent low-latency track while running.
- [x] Add the Settings tab: keep screen on, theme, default tones, tone library import/delete, default vibration, notification and precise-alarm status.
- [x] Apply the glass visual style (ambient background, translucent cards, floating navigation bar, smooth transitions).
- [x] Add four `BoundaryCueTest` JVM tests and update `TimerToneCueTest` for scheduled boundary cues.
- [x] Build app and instrumentation APKs; pass 18 JVM tests and nine instrumentation tests on the API 37 emulator (2026-11-06).
- [x] Inspect Settings in light/dark themes, tone picker, tone library, and running timer. Fix the ConcurrentHashMap compile error, dark-screen heading contrast, selected-tab label contrast, and cramped restart icon.
- [x] Verify on the emulator that a running task stays awake past a 15-second display timeout with charging stay-awake disabled; pause clears the screen-hold flag, resume restores it, and the power button puts the display to sleep. Restore the emulator's timeout and charging settings afterward.
- [x] Install the latest app without clearing data and pass all nine instrumentation tests on the OnePlus IN2021 (Android 13). Inspect Settings in light/dark themes. Confirm pause/stop clear `KEEP_SCREEN_ON`, resume restores it, and the power button lets the display doze.
- [x] Observe an automatic stage transition on the OnePlus with audio playback logged and no reported audio error; the user confirmed hearing the stage-change tone on time. Stop the test timer afterward and preserve saved tasks and settings.
- [ ] Measure actual audible boundary latency on a physical device. The user's listening check passed, but speaker onset and audio-driver latency have not been instrumentally measured.

### Water And Reminder Redesign

- [x] Add dedicated Water tab and move Counter access to Home.
- [x] Separate ordinary reminder and water schedule lists/editors.
- [x] Add large time/on-off card, weekday circles, water-window/interval, and tone preview/import controls.
- [x] Persist weekday masks with non-destructive Room migration 3-to-4.
- [x] Add two selected-weekday scheduling tests and fix one-time reminder completion on valid delivery.
- [x] Build APK, pass JVM tests, and pass all nine instrumentation tests on OnePlus Android 13 and the API 37 emulator.
- [x] Inspect Water setup layout on the emulator.

This update does not complete durable interval-session recovery or background interval alerts. Physical audible delivery, reboot, DND, and OEM battery behavior remain verification gaps.

### Current Stage

**Stage 3 foundation: persisted interval tasks**

- [x] Complete and approve product/architecture plan and persistent workspace guidance.
- [x] Scaffold a single-module Kotlin/Compose Android app.
- [x] Build Home, task editor, sound selection, active timer, and light/dark themes.
- [x] Replace reminder samples with Room-backed create/edit/delete/complete/enable workflows.
- [x] Add one-time, daily, weekly, and daily water-window schedules.
- [x] Add notification permissions, exact/inexact alarm scheduling, notification actions, and recovery receivers.
- [x] Connect Home to the earliest persisted upcoming reminder.
- [x] Bundle 14 original two-second tones, including six retro feature-phone-style choices.
- [x] Add own-tone import, device-format validation, private copies, persistent metadata, and scoped notification URIs.
- [x] Add import controls to interval/reminder pickers and verify save-control reachability on the emulator.
- [x] Make Create start a blank task draft instead of reopening the starter sample.
- [x] Move Add Stage beside the stage count so it is reachable before scrolling through all stages/settings.
- [x] Persist tasks and ordered stages independently in Room; Home now lists them by task.
- [x] Validate 1–50 stages, 1-second–5-hour durations, and save/edit/delete behavior.
- [x] Reproduce on a OnePlus: add two stages, save a 3-stage task, and confirm the 21-minute starter remains unchanged.
- [x] Add two device tests for separate task persistence and independent editing.
- [x] Emit configured start, automatic stage-transition, skip, and completion tone cues.
- [x] Add a device regression test for the start and next-stage tone IDs.
- [x] Run 12 JVM tests and ten API-device instrumentation tests, including task isolation and tone transitions.
- [x] Build a debug APK.
- [x] Inspect reminder list and water-editor layout on the API 37 emulator.
- [ ] Validate real-time audible notification delivery, physical reboot, DND, and OEM battery behavior on devices.

### Approved Decisions

- Reboot behavior: continue using the saved wall-clock deadline and reconcile to the correct slot or completion state; do not replay missed transition tones.
- Limits: maximum 5 hours per slot and 50 slots per task.
- Recurring reminder DST behavior: preserve local time, move nonexistent spring-forward times to the next valid instant, and choose the first duplicated fall-back occurrence.
- Minimum SDK: API 26; validate against the available Android toolchain before scaffolding.

### Verification

`assembleDebug`, `assembleDebugAndroidTest`, and `testDebugUnitTest` pass. Twelve JVM tests and ten instrumentation tests pass across the API 37 emulator/OnePlus. `TimerToneCueTest` confirms the start alert and configured next-stage tone are emitted at the automatic one-second boundary on both devices. On the OnePlus, Create showed a blank draft, Add Stage was visible beside the count, two additions produced 3/50, and Save created a separate 15-minute task while preserving Morning Exercise at 4 stages/21 minutes. The temporary test task was removed afterward. The cue test validates event delivery to the active screen, but physical audible output under different volume/channel/DND settings is not certified. Reminder scheduling still lacks physical reboot and OEM power validation. Task definitions and ordered stages are persisted; active timer sessions/deadlines, counters, and stopwatch remain in-memory, so dependable background interval alarms are not implemented.

Own-tone update: the APK was rebuilt and installed without clearing app data. All 12 JVM tests (eight scheduling, four import rules) and six API 37 instrumentation tests (three reminder persistence, three import/migration) pass. The emulator shows Import tone, format limits, and reachable save controls. A copied WAV was decoded after deleting its original, corrupt audio was rejected, and the v1-to-v2 database migration preserved a reminder. Full system file-picker selection and physical-device imported-notification playback remain unverified.

### Next

Proceed to Stage 3: implement the deadline-based interval session engine, persist pause/resume/recovery state, schedule boundary alarms, and add focused tests. Continue physical reminder delivery/reboot validation separately. Do not treat the current interval timer as a dependable background alarm.
