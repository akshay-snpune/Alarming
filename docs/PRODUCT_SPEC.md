# StageBell: Interval Timer Product Specification

## Product Goal

StageBell is a minimalist, offline-first Android productivity tool. Its primary job is to let someone create one task, split it into sequential timed stages, assign a shared or stage-specific short tone, and complete the stages without manually starting each one. The active timer must make the task, current stage, remaining time, progress, next stage, and expected finish immediately understandable.

## Product Principles

- Interval timer first; supporting tools stay simple and secondary.
- Fast, quiet, one-hand-friendly interactions; useful with little content on screen.
- Native Android behavior, dependable timing, and clear permission explanations.
- No account, advertisements, analytics, tracking, cloud dependency, or required network access.
- Restrained, distinctive Material 3 design with deliberate light and dark themes, readable type, generous spacing, and subtle transitions.
- Use a glossy, water-textured bell for the app icon and home mark; retain the simple monochrome notification bell.

## Modules

1. **Home and interval tasks:** create/edit/duplicate tasks, manage ordered slots, save/reuse templates, start a task, and view saved tasks and recent activity. Each slot has a name, positive duration in seconds, optional tone override, and optional vibration. Task name is required; description is optional. Shared tone, start tone, completion tone, and mute are configurable. Total duration is derived from slots.
2. **Active interval timer:** show task, slot position/name, large remaining time, slot and task progress, next slot/duration, and task finish estimate. Support pause/resume, skip, restart current slot, and stop. Advance automatically and alert at each boundary; finish with a distinct completion state and tone.
3. **Reminders:** one-time, daily, or weekly title/description/date-time reminders with tone, vibration, completion, edit, delete, enable/disable, and an upcoming list. Water reminders repeat daily within a same-day start/end window, defaulting to 08:00–20:00 every 120 minutes. End is included only if it falls on an interval boundary. Completing a reminder disables its entire schedule; dismissing a notification leaves recurring schedules enabled. Saved schedules retain their creation time zone.
4. **Counter:** multiple named saved counters with custom initial values, increment, decrement, reset, persistence, and optional haptics.
5. **Stopwatch:** start, pause/resume, reset, laps, and persisted elapsed-time calculation when backgrounded.
6. **Settings:** defaults for sounds, vibration, appearance, and concise notification/exact-alarm status and explanations.

Bottom navigation: Home, Reminders, Water, Stopwatch. The droplet opens dedicated water schedules, not Counter. Counter remains reachable from Home. Ordinary reminders and water schedules are listed separately. Reminder setup includes a prominent time/on-off card, selected repeat weekdays, tone selection/preview/import, and vibration. Water adds editable start/end times and interval, defaulting to 08:00–20:00 every 120 minutes.

## Sound and Feedback

Bundle original two-second tones (soft bell, digital beep, classic tick, double bell, temple bell, Om hum, focus tone, completion chime). Support preview, shared defaults, per-slot overrides, silent mode, and optional vibration. Do not fetch media dynamically or use copyrighted music. Respect system volume, audio focus, notification-channel controls, mute, and Do Not Disturb; never request permission to bypass DND.

The bundled catalog also includes six original feature-phone-style options: Retro dual-tone, Pocket polyphony, Mobile chirp, Retro pager, Keypad cascade, and Pocket pulse. These are not Nokia recordings or reproductions of Nokia melodies. Reminder alerts use notification channels; user channel settings override the app's sound/vibration choice.

Users may import their own MP3, WAV, M4A/AAC, OGG, or FLAC clips through the Android file picker, subject to device decoding support. Limit imports to five seconds and 5 MB; reject empty, unreadable, corrupt, or unsupported files. Copy accepted files into app-private storage and persist metadata, so source-file deletion does not break saved tones. Expose imports in both interval and reminder tone selectors with preview and readable names; do not request broad storage permission or fetch audio dynamically.

Add the 20 supplied two-second clips to the existing catalog. Only Soft bell, Focus tone, Completion chime, supplied Temple bell, and Aarti bells are protected from deletion. Reject deletion of tones referenced by a task, reminder, or default setting. Removing a bundled tone hides it rather than reducing installed package size.

Share individual tasks via the Android share sheet and export/import multiple tasks through the document picker. A versioned archive carries every referenced tone, whether imported or bundled. Import creates new task and tone copies rather than replacing existing data. The archive is limited to 100 tasks, 200 tones, and 32 MB expanded; tone imports retain the five-second/5 MB limits.

## Timing and Platform Expectations

- Remaining time is calculated from a deadline, not a tick counter. UI refresh cadence only affects display smoothness.
- Persist active session state and schedule slot boundaries. Recover after process death; reconcile elapsed boundaries rather than replaying a burst of missed sounds.
- Use monotonic time (`elapsedRealtime`) for precision during one boot. Reboot recovery needs a documented wall-clock policy because monotonic time resets.
- Limit each slot to 5 hours and each task to 50 slots. Reject zero or negative durations and show validation before saving or starting.
- On reboot, continue from the saved wall-clock deadline, reconcile to the correct slot or completion state, and do not replay missed transition tones.
- For recurring reminders, preserve local wall-clock time in the selected time zone; move nonexistent spring-forward times to the next valid instant and use the first occurrence of duplicated fall-back times.
- Android may delay or suppress delivery depending on exact-alarm access, notification permission, power settings, OS version, and manufacturer behavior. Explain limitations and provide a graceful degraded mode; do not promise perfect delivery in all conditions.
- Minimum SDK: API 26; target the latest stable SDK available at implementation time. Validate this against the actual build environment before project scaffolding.

## Quality Bar

Critical automated coverage includes cumulative duration, zero/invalid duration rejection, ordered transitions, per-slot/shared tones, pause/resume, skip/restart, completion, missed-boundary reconciliation, process recovery, reboot policy, reminder scheduling/recurrence, counter persistence, and stopwatch elapsed-time behavior. Verify layouts across small and large screens and light/dark modes. Physical-device testing is needed for alarm delivery, permission flows, audio, reboot recovery, and manufacturer battery restrictions.
