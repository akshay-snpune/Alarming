# Development Progress

## Home Simplification

- Remove the counter shortcut from Home; retain the existing internal counter implementation without adding another entry point.

## Reminder Deletion And Home Synchronization

- Home, Reminders, and Water explicitly share one `RemindersViewModel`.
- Deletion confirmation stays open with repeat actions disabled until storage deletion succeeds and the shared reminder state no longer contains the deleted ID. Failures remain visible in the dialog.
- Four focused tests (the new deletion-state regression plus three persistence tests) pass on the OnePlus Android 13.
- On the updated phone build, create a temporary water reminder, confirm it appears on Home, delete it in Water, and confirm the first return to Home shows "No upcoming reminders". Remove only the test reminder; preserve other data.
- The originally reported stale card was not reproduced before the change. This update closes the asynchronous confirmation/state-update gap; it does not claim a proven diagnosis of every stale-card occurrence.

## Precise Cues, Screen Hold, Settings, And Glass Style

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

## Water And Reminder Redesign

- [x] Add dedicated Water tab and move Counter access to Home.
- [x] Separate ordinary reminder and water schedule lists/editors.
- [x] Add large time/on-off card, weekday circles, water-window/interval, and tone preview/import controls.
- [x] Persist weekday masks with non-destructive Room migration 3-to-4.
- [x] Add two selected-weekday scheduling tests and fix one-time reminder completion on valid delivery.
- [x] Build APK, pass JVM tests, and pass all nine instrumentation tests on OnePlus Android 13 and the API 37 emulator.
- [x] Inspect Water setup layout on the emulator.

This update does not complete durable interval-session recovery or background interval alerts. Physical audible delivery, reboot, DND, and OEM battery behavior remain verification gaps.

## Current Stage

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

## Approved Decisions

- Reboot behavior: continue using the saved wall-clock deadline and reconcile to the correct slot or completion state; do not replay missed transition tones.
- Limits: maximum 5 hours per slot and 50 slots per task.
- Recurring reminder DST behavior: preserve local time, move nonexistent spring-forward times to the next valid instant, and choose the first duplicated fall-back occurrence.
- Minimum SDK: API 26; validate against the available Android toolchain before scaffolding.

## Verification

`assembleDebug`, `assembleDebugAndroidTest`, and `testDebugUnitTest` pass. Twelve JVM tests and ten instrumentation tests pass across the API 37 emulator/OnePlus. `TimerToneCueTest` confirms the start alert and configured next-stage tone are emitted at the automatic one-second boundary on both devices. On the OnePlus, Create showed a blank draft, Add Stage was visible beside the count, two additions produced 3/50, and Save created a separate 15-minute task while preserving Morning Exercise at 4 stages/21 minutes. The temporary test task was removed afterward. The cue test validates event delivery to the active screen, but physical audible output under different volume/channel/DND settings is not certified. Reminder scheduling still lacks physical reboot and OEM power validation. Task definitions and ordered stages are persisted; active timer sessions/deadlines, counters, and stopwatch remain in-memory, so dependable background interval alarms are not implemented.

Own-tone update: the APK was rebuilt and installed without clearing app data. All 12 JVM tests (eight scheduling, four import rules) and six API 37 instrumentation tests (three reminder persistence, three import/migration) pass. The emulator shows Import tone, format limits, and reachable save controls. A copied WAV was decoded after deleting its original, corrupt audio was rejected, and the v1-to-v2 database migration preserved a reminder. Full system file-picker selection and physical-device imported-notification playback remain unverified.

## Next

Proceed to Stage 3: implement the deadline-based interval session engine, persist pause/resume/recovery state, schedule boundary alarms, and add focused tests. Continue physical reminder delivery/reboot validation separately. Do not treat the current interval timer as a dependable background alarm.
