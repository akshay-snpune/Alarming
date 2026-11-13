# Architecture Proposal

## Water And Reminder Update

Room schema version 4 adds `daysOfWeekMask` through migration 3-to-4, preserving previous reminder, tone, and task records. A zero mask retains legacy behavior; new recurring schedules persist explicit Monday-to-Sunday selections. Daily, weekly, and water scheduling honor selected weekdays and the existing DST policy. Valid one-time alarm delivery completes the reminder instead of recomputing a future occurrence.

Bottom destinations are Home, Reminders, Water, and Stopwatch. Counter is a Home utility route. Water and ordinary reminders share storage/scheduling but filter their lists and editors by schedule type. Editors expose a prominent time/enable card, weekday circles, tone preview/import, and vibration; Water also exposes start/end and interval. The updated build and JVM tests pass, and the full nine-test instrumentation suite passed on the OnePlus Android 13 and API 37 emulator. Emulator visual checks confirmed Water time, weekday, interval, and tone controls. Active interval-session persistence/background delivery remains unfinished.

## Status

Stage 1 architecture approved. A single-module Android Compose app now includes persisted reminder schedules and task definitions. Active timer sessions, counters, and stopwatch remain prototype-only; approved choices below remain the implementation baseline.

Reminders have now been implemented at the user's request, ahead of the remaining interval-engine stage. `feature.reminders` owns a Room database, ViewModel, schedule calculator, editor/list, and AlarmManager/notification adapters. Other feature state remains in-memory.

## Interval Task Persistence

- Schema version 3 adds `interval_tasks` and ordered `interval_task_slots`; migration 2-to-3 preserves reminder/tone tables and inserts no destructive reset.
- `IntervalTaskStore` saves a task and all its stages transactionally, validates 1–50 stages and 1-second–5-hour durations, and cascades task deletion to its stages.
- A first-run starter task remains the separate 21-minute Morning Exercise sample. Create opens a blank five-minute, single-stage draft; Add Stage is beside the stage count, and Home lists each saved task independently. Edit loads by task ID; Save replaces only that task's ordered stages.
- Active timer state and its deadline are still process memory only. Task-definition durability does not imply reliable background timing; Stage 3 must implement persisted session deadlines and alarms.
- Two OnePlus API-device instrumentation tests verify independent task/slot persistence and edit isolation. Manual phone testing confirmed adding two stages, saving a 3-stage/15-minute task, and preserving the 4-stage/21-minute starter.

## Reminder Implementation

- `ReminderRecord` stores one-time/daily/weekly/water schedules, local first date, start/end minutes, interval, time zone, tone, vibration, enable/completion state, and next-trigger instant. `ReminderStore` serializes changes and persists before scheduling.
- One PendingIntent per reminder is identified by a unique URI. Each delivery carries its expected trigger; stale and duplicate delivery is ignored. Recurring delivery advances to the next future local-time boundary, not delivery-time plus interval. Recurring alerts over five minutes late are skipped; overdue one-time reminders are delivered on reconciliation.
- Recovery runs on app startup/resume, boot after unlock, clock/time-zone broadcasts, and exact-alarm access grant. Saved time zones are retained, with DST gaps resolved to the next valid instant and overlaps using the earlier occurrence. Android force-stop and exact-alarm revocation can remove scheduled work; open the app again to reconcile it. No foreground service or background tick loop is used.
- Notification permission is requested through an explicit control. Without exact access use `setAndAllowWhileIdle`; with access use `setExactAndAllowWhileIdle`. Doze quotas, especially for short intervals, and OEM restrictions can still delay delivery. Per-tone/vibration notification channels respect DND, system volume, and channel settings; Silent uses no sound.
- Room annotation processing currently uses AGP's legacy kapt with a Kotlin 2.4 metadata reader. The database is version 3, with tested 1-to-2 and 2-to-3 migrations adding imported tones and interval task tables without altering existing reminders. No destructive migrations are enabled.
- Twelve JVM tests and eight API-device instrumentation tests cover scheduling, DST, tone import, reminder persistence/recovery, and interval task/slot persistence. Physical reboot, audible delivery, OEM power management, and older-API behavior remain unverified.

## Home Scope

Home exposes task creation, saved interval tasks, and the upcoming reminder. The counter shortcut is removed; its internal implementation remains unused.

## Shared Reminder State

Home, Reminders, and Water share the activity's `RemindersViewModel` explicitly. Reminder deletion reports success only after the Room deletion completes and the shared records flow excludes the deleted ID. Its confirmation remains open while pending and displays failures, preventing an immediate navigation away from presenting deletion as finished before shared state updates.

## Imported Tones

`audio.ToneLibrary` uses Storage Access Framework `OpenDocument` selection and bounded copies into `files/tones`. Room persists UUID-based IDs, original display names, filenames, and durations. Built-in IDs remain unchanged; imported IDs are stored in the existing reminder sound field. Media metadata, an audio track, and an available decoder are checked before accepting a clip (up to two seconds and 5 MB). Source permissions need not persist after copying.

Import and deletion of own tones live in the Settings tab; task and reminder pickers only select and preview. Both selectors share MediaPlayer preview controls, releasing the player when the screen exits and resetting it for the next preview. A non-exported FileProvider exposes only the tone directory through scoped URIs for notification sounds. Missing imports resolve to silent notifications and show an unavailable label in the selector. Four JVM import-rule tests and three API 37 import/migration tests verify copy limits, corrupt rejection, URI readability/playback after source removal, and reminder-preserving migration. Real-device notification sound playback is not yet certified.

## Foreground Timer Cues And Display

- `PrototypeViewModel` publishes `upcomingCue` (tone ID plus the `elapsedRealtime` deadline of the current stage) whenever the deadline changes: start, resume, skip, restart, automatic advance, pause/stop (cleared). `nextBoundaryCue` is a pure function covered by JVM tests.
- `audio.PreciseCuePlayer` starts boundary cues from an `THREAD_PRIORITY_URGENT_AUDIO` `HandlerThread` at the exact deadline, independent of the UI tick and main-thread work. Samples are preloaded into `SoundPool`; unloaded tones fall back to MediaPlayer. Overdue cues play once; cues more than 1.5 s late (e.g. CPU slept) are dropped rather than replayed. Start and manual-skip cues remain immediate.
- While the timer is running, a looping silent low-latency static `AudioTrack` with the same notification attributes keeps the audio output out of standby, removing wake-up delay before each cue. It stops on pause/stop/completion.
- Bundled tones are generated with their first onset at sample 0 (`tools/generate_tones.mjs`); slow-attack tones such as Om hum still swell in by design.
- When the "Keep screen on" setting is enabled (default), the activity window holds `FLAG_KEEP_SCREEN_ON` only while the timer is running; the power button still turns the screen off, and pausing releases the hold.
- Interval cues still play only while the timer screen is composed and the process is awake; they are not AlarmManager-backed yet.

## Settings And Visual Style

- `feature.settings.SettingsScreen` centralizes keep-screen-on, theme, default stage/start/completion/reminder tones (bottom-sheet picker with preview), the tone library (import, preview, delete imported tones with confirmation; built-in list collapsible), default vibration, and notification/precise-alarm status with actions. Reminders keeps its contextual permission warning.
- `ui/Glass.kt` provides the "liquid glass" style: an ambient water-tinted gradient background, translucent cards with a top-lit rim and soft shadow, and a floating capsule navigation bar. Screen changes use short fade/scale transitions; no continuous animations run, to avoid battery cost while the screen is held on.

## Stack and Shape

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, lifecycle ViewModels, Kotlin coroutines and Flow.
- One Android application module initially. Organize by responsibility/feature packages (`core`, `data`, `domain`, `feature.home`, `feature.intervaltimer`, `feature.reminders`, `feature.counter`, `feature.stopwatch`, `feature.settings`, `notification`, `audio`, `scheduling`). Split Gradle modules only if real build or ownership needs emerge.
- Room is the source of durable tasks, slots, sessions, reminders, counters, and laps. DataStore holds app preferences (theme and defaults). Repositories expose Flow and suspend operations. Domain use cases/state reducers hold timer rules; Compose renders immutable state and sends user actions to ViewModels.
- Prefer constructor injection/lightweight manual wiring initially; add Hilt only if the dependency graph justifies it. Keep dependencies small and no network stack.

## Interval Timing: Core Design

A persisted session is a state machine (`Idle`, `Running`, `Paused`, `Completed`, `Stopped`) with task ID, current slot index, slot start/deadline, pause remainder, session origin/deadline, and boot metadata. The domain engine validates positive slot durations, derives total duration, and applies explicit actions/events for start, pause, resume, skip, restart, stop, and alarm delivery. Transitions are deterministic and independently unit-testable.

Within a boot, `SystemClock.elapsedRealtime()` is authoritative. Store an elapsed-time deadline for accurate remaining time and a corresponding wall-clock deadline plus boot count for scheduling/recovery. The UI derives remaining/progress from the deadline; a lifecycle-aware ticker only requests redraws and is never the source of elapsed time. Persist every state-changing action and alarm boundary transactionally.

Use AlarmManager for user-visible slot boundaries and scheduled reminders; do not use WorkManager for precise delivery. Schedule one next boundary at a time and reconcile the session against its timeline when an alarm arrives, so delayed delivery or process death cannot cause cumulative drift or a rapid replay of old alerts. Persist pending state before scheduling, use stable PendingIntent identities, and reschedule after reboot/time-zone changes as appropriate. Receiver work should be short: reconcile/persist, emit an alert notification on the appropriate channel, and schedule the next boundary.

**Approved reboot semantics.** A timer continues across device power-off according to its persisted wall-clock deadline, then recovers into the correct slot or completion state at boot. Monotonic time is used again after boot. This cannot distinguish a real powered-off interval from manual wall-clock changes, so recovery follows wall time; do not emit every missed transition tone.

## Android Delivery and Audio

- Android 12+ exact alarms may require the user to grant `SCHEDULE_EXACT_ALARM` special access; availability and revocation must be checked. `USE_EXACT_ALARM` is restricted by platform/store policy and should not be assumed. Explain why access improves timer/reminder delivery, request only when user enables precise alerts, and fall back to inexact scheduling with honest UI status.
- Android 13+ notification permission is runtime-controlled. A denied permission means alerts/status notifications may not appear; explain this and keep in-app timing functional. Exact alarm access does not override notification permission, Doze, DND, volume, or manufacturer restrictions.
- Use notification channels, with channel behavior controlled by the user. For sound choices, use bundled original short audio and an audio layer for foreground preview/playback; ensure background alert behavior respects channel settings and audio policies. Never request DND-policy access or force sound through mute.
- Prefer AlarmManager + receiver + notifications over a permanently running service. A foreground service is not currently proposed: timer UI updates need none, and scheduled boundary alarms cover transitions. Revisit only if device testing or platform requirements show a genuine need; Android background-start and foreground-service-type rules apply.
- Request `RECEIVE_BOOT_COMPLETED` only for restoring persisted user-scheduled work. Reconcile boot count and wall-clock deadlines, and handle exact-alarm permission revocation by canceling/rebuilding schedules and informing the user.

## Data Model (Room)

- `IntervalTaskEntity`: id, name, description, created/updated timestamps, default sound ID, start/completion sound IDs, favorite flag.
- `TaskSlotEntity`: id, task ID (foreign key with cascade), position, name, duration seconds, optional sound override, vibration setting. Enforce stable ordering and positive durations in domain validation.
- `TimerSessionEntity`: id, task ID, current slot index, status, elapsed deadline/start fields, wall-clock checkpoint/deadline, boot count, paused remainder, updated timestamp. Keep at most one active session unless product review decides otherwise.
- `ReminderEntity`: id, title, description, scheduled instant, recurrence rule/time zone, sound ID, vibration, enabled/completed flags, next trigger.
- `CounterEntity`: id, name, current value, initial value, updated timestamp.
- `StopwatchEntity` and `LapEntity`: persisted running/paused anchor, accumulated duration, boot metadata, and ordered lap durations/timestamps. Store elapsed durations, not formatted strings.

Store durations as integer seconds (stopwatch precision as milliseconds/long duration). Store instants in UTC epoch form and recurrence zone/rule explicitly. Derive task total duration from slots rather than maintaining a second mutable value. Migrations and foreign-key/index choices are introduced alongside the Room schema.

## Navigation and UI Structure

- Bottom destinations: Home, Reminders, Counter, Stopwatch; task editing and active timer are focused routes, not extra bottom destinations.
- Home: brand/title and settings, dominant new-timer action, saved/recent task list, next reminder, compact links to secondary utilities.
- Task editor: name/description, ordered slot rows with minute/second inputs, add/remove/reorder, common tone control plus per-slot override, preview, vibration/start/completion controls, total duration, save/template/duplicate actions.
- Active timer: high-contrast large remaining time, task and slot context, circular progress, total progress and finish estimate, next slot, pause/skip/restart/stop. Respect system insets and reachable controls.
- Use Material 3 tokens with custom light/dark palettes, semantic color states, restrained motion, and accessibility labels/content descriptions.

## Test Strategy

- Pure JVM tests for domain duration calculations, state transitions, pause/resume, skip/restart, common and individual tones, completion, delayed/missed alarm reconciliation, and invalid input.
- Room/repository tests for task-slot ordering, cascading deletes, session persistence/recovery, reminder recurrence, counter persistence, and stopwatch/lap persistence.
- Android tests for exact-alarm/notification permission states, receiver scheduling, reboot rescheduling, notification channels, and key Compose workflows where practical.
- Manual physical-device matrix for API 26 through current target, Doze/locked screen, process death, reboot, permission denial/revocation, DND/mute, and at least one manufacturer with aggressive battery management. Emulator tests alone cannot establish OEM delivery behavior.

## Main Risks / Implementation Constraints

1. Exact alarm access is special access and can be denied/revoked; degraded delivery must be explicit.
2. Notification denial/channel muting/DND can make an otherwise correct timer silent or invisible.
3. Recurring reminders use the approved daylight-saving policy: preserve local wall-clock time in the selected time zone, move nonexistent spring-forward time to the next valid instant, and use the first occurrence of duplicated fall-back time.
4. Enforce the approved limits of 5 hours per slot and 50 slots per task in domain validation and the task editor.
5. SoundPool and notification-channel behavior differ; the alert path must be tested on-device and must not imply that per-slot sounds override user channel settings.

## Proposed Delivery Stages

1. Product and architecture review (this document and product spec).
2. UI prototype: home, task editor, sound selection, active timer; sample data and both themes. Task definitions now persist; build and OnePlus layout/flow verification pass.
3. Interval session engine, persisted timing/recovery, and background boundary scheduling tests.
4. Reminder, counter, stopwatch, and settings.
5. Alarm/notification integration, exact-alarm permissions, reboot recovery, device validation.
6. Accessibility/responsive QA, full test run, docs, and debug/release APK build instructions and artifact if the environment supports Android builds.
