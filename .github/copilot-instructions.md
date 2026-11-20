# Copilot Instructions: StageBell: Interval Timer

## Product Priorities

- Build an offline-first, lightweight Android productivity app whose defining feature is a task split into ordered, individually timed slots with per-slot or shared alert tones.
- Prioritize a dependable interval timer before reminders, counters, stopwatch, and settings.
- Keep the interface minimal, glanceable, one-hand friendly, and polished in light and dark themes. Use Jetpack Compose and Material 3.
- Do not add accounts, analytics, ads, network dependencies, or unrelated features.

## Technical Constraints

- Use Kotlin, Compose, Material 3, MVVM, coroutines/Flow, Room for structured local data, and DataStore for preferences. Keep the initial architecture modular by package, not by unnecessary Gradle modules or abstractions.
- Timer truth must come from persisted deadlines, not a repeatedly decremented counter or background one-second loop. Use `SystemClock.elapsedRealtime()` for same-boot timing; persist enough wall-clock/boot metadata to recover after process death and reboot.
- Enforce a maximum slot duration of 5 hours and a maximum of 50 slots per task.
- Schedule timer boundaries and reminders with AlarmManager when precise delivery is available. Do not treat WorkManager as a precise timer. Explain exact-alarm and notification permission requirements, provide a graceful fallback, and never bypass Android Do Not Disturb or audio policies.
- Keep background work short. Add a foreground service only if implementation and platform constraints demonstrate it is necessary; do not keep a service alive just to refresh the UI.
- Validate duration and task inputs. Handle pause/resume, skip, restart, missed boundaries, sound failures, permission denial, process death, and reboot explicitly.
- Keep sounds short, original, bundled, previewable, and optional. Respect mute, audio focus, notification-channel settings, and vibration preferences.

## Implementation Practice

- Before starting a stage, state its scope, files, architecture choices, and verification plan. Implement only after the product/architecture plan has been reviewed.
- Preserve existing behavior and user changes. Make focused edits, follow repository conventions, and do not leave placeholder controls or TODOs in completed features.
- Add focused tests for timer/domain behavior and persistence/recovery before marking features complete. Keep `README.md` aligned with actual product decisions, architecture, development progress, and implementation.
- Do not claim physical-device validation or APK delivery unless actually performed. Record Android-version/device limitations honestly.
