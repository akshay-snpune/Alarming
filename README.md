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
