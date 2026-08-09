# Bug #11 fix plan (stashed — not started)

GitHub issue #11: editing an active fast's start time doesn't update the elapsed/remaining
time in the ongoing notification.

## Root cause

`FastingRepository.updateSession()` (FastingRepository.kt:69) writes the new `startEpoch`
to Room but never notifies `FastingNotificationService`, which cached the epoch from the
original `ACTION_START` intent. The "almost there" reminder is likewise never rescheduled.

## Fix — all in `updateSession()`, service untouched

When `updated.endEpoch == null` (fast still active), after `dao.update(...)`:

1. Re-fire `ContextCompat.startForegroundService(context,
   FastingNotificationService.startIntent(context, updated.startEpoch, mode.fastSeconds,
   mode.displayName))` — `ACTION_START` already overwrites the cached epoch, rebuilds the
   notification immediately, and restarts the ticker. Zero service changes.
2. `reminderScheduler.cancelAlmostThere()` **first**, then re-schedule if
   `settings.current().almostThereEnabled` — mirror of `startFast()`. The explicit cancel
   matters: `scheduleAlmostThere` early-returns when the fire time is already past
   (ReminderScheduler.kt:32), so without it a stale reminder from the old start time
   would survive an edit that moves the reminder into the past.

~10 lines. Handles mode edits too (`updateSession` already recomputes `targetSeconds`
from the mode; the re-fired intent carries the new target).

## Edge cases (no code needed, just verify)

- Editing a **completed** fast (`endEpoch != null`): no service/reminder action — current
  behavior, already correct.
- Edit makes elapsed ≥ target: notification shows `00:00:00 left`; same as a fast running
  over naturally.
- BackupManager import already re-fires the start intent for a restored active fast
  (BackupManager.kt:228) — untouched.

## Verification (manual, on emulator)

1. Start a 16:8 fast → notification appears.
2. Edit start time 5 h earlier → notification elapsed jumps +5 h immediately.
3. Edit start so <1 h remains → confirm no stale "almost there" fires at the old time
   (shorten `ReminderScheduler` constants for testing, then revert).
4. Stop fast → notification clears (regression check).

## Release

Ships alone as **0.3.1** patch: version bump, changelog `fastlane/.../changelogs/<code>.txt`,
tag push (= F-Droid release), GitHub release with debug APK. Play upload only if no review
pending. Reply on issue #11 when tagged.
