# Issue #13 — frozen timer notifications, and the four sibling bugs

**Report:** [#13](https://github.com/kirilan/SoloForge/issues/13) — LineageOS 23.2 (Android 15), de-googled.
The fasting notification freezes at the value it had the last time the app was on screen, and the
notification sometimes never appears if the user leaves the app right after starting a fast.
Unrestricted battery usage does not help.

## Root causes

All five share one mistake: **we assumed our own process gets CPU time while the device is asleep.**
A foreground service keeps the *process* from being killed. It does not keep the *CPU* awake. On a
de-googled ROM there is no GMS traffic waking the device every few minutes, so it reaches deep
suspend and stays there — which is why this reproduces on LineageOS and rarely on stock.

| # | Bug | Where |
|---|-----|-------|
| 1 | Fasting notification text is repainted by a `delay(30_000)` loop that stops running in suspend | `FastingNotificationService.kt:98` |
| 2 | Workout notification has the identical `delay(1_000)` loop | `WorkoutNotificationService.kt:243` |
| 3 | Interval beeps `delay()` until the next beep — silent for the whole sleep, then one late beep | `WorkoutNotificationService.kt:260` |
| 4 | Reminders use `OneTimeWorkRequest` + `setInitialDelay`, which Doze defers to the next maintenance window | `ReminderScheduler.kt` |
| 5 | `RECEIVE_BOOT_COMPLETED` is declared with no receiver; nothing restores timers after a reboot | `AndroidManifest.xml:10` |
| 6 | Three `startForegroundService` calls with no `try` — `ForegroundServiceStartNotAllowedException` crashes | `FastingRepository`, `WorkoutRepository`, `BackupManager` |

(#6 is the "notification won't start if I get off the app immediately" half of the report.)

---

## Fixes

### F1 · Let SystemUI render the fasting timer

Delete the ticker. Post the notification once with a chronometer:

```kotlin
.setWhen(startEpoch)
.setShowWhen(true)
.setUsesChronometer(true)
```

SystemUI advances a chronometer without any process of ours running, across suspend, forever.

`contentText` can no longer hold a live "X left", so it changes to a value that never goes stale:
the **target end wall-clock time** — `Target 20:30`, formatted with
`android.text.format.DateFormat.getTimeFormat(context)` so it follows the user's 12/24h setting.

Deliberately **not** `setChronometerCountDown(true)`: fasts routinely run past target, and a
count-down chronometer renders negative time (`-00:14:02`) once it passes zero.

New string `notif_fasting_target` in 7 locales; `notif_fasting_text` is deleted.

Reposts still happen on every real state change (start, restore, edit an active fast) — those all
already restart the service.

### F2 · Same treatment for the workout timer

Chronometer base is not the workout start (paused and pre-start time must not count), it is
`now - activeSeconds(now) * 1000`. On pause, repost with `setUsesChronometer(false)` and the frozen
formatted total.

Every phase transition already routes through the service (`togglePhase`, `togglePause`, `setPhase`,
`handleStart`, `restoreActiveWorkout`), so each of those gets an explicit `notifyTick()` where the
1-second loop used to cover it. The ticker job is deleted.

Side benefit: the app stops waking the CPU once per second for the length of every workout.

### F3 · A wakelock for interval beeps — the one place that genuinely needs one

Audio at an exact instant while the device sleeps cannot be faked. `AlarmManager` is not an option
here: `setExactAndAllowWhileIdle` is throttled to roughly once per 9 minutes in Doze, useless for a
30-second interval, and `USE_EXACT_ALARM` is Play-policy-restricted to alarm/calendar apps.

So: a `PARTIAL_WAKE_LOCK` held **only** while an INTERVAL workout is in the EXERCISE phase with
`intervalSeconds > 0`. Released on pause, phase change, stop, discard, and `onDestroy`. Acquired with
a hard timeout (4 h) so a leak can't drain a battery indefinitely.

Adds `android.permission.WAKE_LOCK` — a normal permission, no F-Droid anti-feature, nothing for the
privacy policy.

### F4 · ~~Exact-ish reminders via `setAndAllowWhileIdle`~~ — dropped on review

The idea was to swap the three `OneTimeWorkRequest`s for `AlarmManager.setAndAllowWhileIdle`, which
needs no permission and fires during Doze.

**It doesn't hold up.** Escaping Doze is only half the deferral story: **App Standby Buckets** defer
inexact alarms independently, by hours, once the app falls to the `frequent`/`rare` bucket — which
is exactly what happens to a fasting app the user doesn't open for a day. That is the same deferral
WorkManager already suffers, so the swap buys close to nothing for a large diff (two receivers,
deleted workers, upgrade dedup, reboot/update/time-set handling).

Real punctuality needs `setExactAndAllowWhileIdle`, and on `targetSdk 36` that means
`SCHEDULE_EXACT_ALARM` (denied by default; the user has to find a toggle in system settings) or
`USE_EXACT_ALARM` (Play policy restricts it to alarm-clock and calendar apps). Neither is
justifiable for a "1 hour left" nudge.

**Decision: keep WorkManager.** The workers already re-check current state before notifying, so a
late reminder is late but never wrong. Revisit only if users actually report bad timing — and if so,
the honest fix is an opt-in exact-alarm toggle, not a silent mechanism swap.

Knock-on: F5 shrinks to restoring foreground services, and the duplicate-reminder-on-upgrade
regression below disappears with it.

### F5 · The boot receiver that's been missing since day one

One `BootReceiver` for `BOOT_COMPLETED` and `TIMEZONE_CHANGED`. If a fast or workout is active in
Room it restarts that foreground service; on a timezone change it also reposts so F1's "Target
20:30" text doesn't lie. Not `directBootAware` — Room lives in credential-encrypted storage, so we
want the post-unlock broadcast. WorkManager restores its own jobs at boot, so reminders need
nothing here.

Whether a `specialUse` foreground service may be started from `BOOT_COMPLETED` is an **inference**,
not something I've run: Android 14+ documents a blocklist of FGS types for boot receivers
(`dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, plus `microphone` in 15) and
`specialUse` isn't on it, which implies the start itself is permitted. **Verify on an API 36
emulator before trusting it.** F6 catches the failure either way, leaving today's behavior.

### F6 · Guard *both* sides of the foreground-service start

The caller side, at all five `startForegroundService` sites:

```kotlin
// notifications/ServiceStart.kt
fun Context.tryStartForegroundService(intent: Intent)
```

And — the part the first draft missed — **the service side**. `startForeground()` itself throws
`ForegroundServiceStartNotAllowedException` when the app has fallen to the background between the
start request and the service running. That is almost certainly the reporter's "the notification
won't start if I immediately get off the app": the crash isn't at our call site, it's inside
`FastingNotificationService.startFast` / `WorkoutNotificationService.startForegroundNotification`.
Both need the same guard plus `stopSelf()` on failure, so we don't leave a started service with no
notification.

Catch `IllegalStateException` (the superclass) rather than the API-31 type — one catch clause, no
API-level guard, and no class-resolution concern on API 26–30 where the subclass doesn't exist.
Failing to show the notification is bad; crashing mid-backup-import is worse, and
`MainActivity.onStart` reconciles the service the next time the app is opened either way.

---

## Regressions considered

**The notification loses its "X left" line.** Real loss of information, accepted: a live remaining
value is exactly the thing that cannot be kept honest without a ticker. "Target 20:30" is strictly
more truthful than a frozen "04:12:33 left". The in-app screen still shows everything live.

**The timer gets smaller — needs a decision.** `setUsesChronometer` renders in the notification
*header*, where the timestamp normally sits: small text, top-right. Today's elapsed time is
normal-size body text. A large live timer would require a custom `RemoteViews` layout containing a
`Chronometer` widget (the Google Clock approach) — much more code, and custom notification layouts
don't inherit Material You theming, so it looks off-brand on some ROMs. Recommendation: take the
header chronometer. It's what AOSP's own ongoing-call notification does, and a small correct timer
beats a large frozen one.

**Notification sort order.** `when` moves to the fast's start time, which is in the past, so the
shade may rank the notification lower than it does today. Cosmetic.

**Chronometer over 24 h.** A 36 h fast renders `36:11:04`. Correct, and what AOSP's Chronometer does.

**12/24h or locale change while a timer runs.** The static target text goes stale until the next
repost. Cosmetic, self-heals on any state change.

**Clock changes during a fast.** Actually improves: SystemUI converts `when` to an
`elapsedRealtime` base once at post time, so a later clock jump no longer distorts the displayed
timer (our Room math is still wall-clock — unchanged, out of scope).

**Workout pause/resume flicker.** Pause reposts a non-chronometer notification, resume reposts a
chronometer one. `setOnlyAlertOnce(true)` is already set, so no sound or heads-up. Must verify no
visible flash on a real device.

**Missing a repost path in the workout service.** The single biggest risk in F2: if any state
transition doesn't repost, the notification silently shows a stale phase. Mitigated by enumerating
the transitions (all five go through `onStartCommand`), and by a manual pass over every mode.

**Wakelock battery drain.** Interval mode with the screen off now keeps the CPU awake. That is the
feature working as advertised, but it must not leak: release on every exit path plus a timeout cap.
Worth a line in the changelog.

**~~Duplicate reminders on upgrade~~** and **~~alarms lost on app update~~** — both were artifacts of
F4. Gone with it. Reminders keep working exactly as they do today, on the mechanism users already
have scheduled.

**Boot receiver overhead.** Fires on every boot for every user. Exits after one Room read when
nothing is active.

**Foreground-service start at boot rejected by a hostile ROM.** Falls back to today's behavior
(notification appears when the app is next opened) instead of crashing.

**Swallowing the FGS exception hides a real failure.** Accepted, narrowly: only that one exception
type, and only where the alternative is a crash.

**`stopService`/`ACTION_STOP` paths.** F1/F2 delete ticker jobs that `onDestroy` currently cancels;
make sure nothing else depended on `tickerJob` being non-null.

---

## Order of work

Independent, each shippable alone. Recommended sequence:

1. **F6** — both sides of the FGS start. Small, removes a crash, unblocks F5. No behavior change.
2. **F1** — fixes the reported bug. Strings in 7 locales.
3. **F2** — same shape as F1, more state transitions to cover.
4. **F5** — boot receiver.
5. **F3** — wakelock.

F4 is dropped (see above). Four fixes, each shippable alone.

## Status — implemented and verified 2026-08-09

All four fixes are in. One extra defect surfaced during device testing and was fixed with them:

**The shared workout state had a write-back race.** `WorkoutRepository.observeActive()` wrote its
reconciled value back into `WorkoutStateHolder`. A Room emission from *before* an insert still
carries `null`, so it could wipe the state the service had just set — after which the service's
`buildNotification()` and `updateWakeLock()` read an empty state and rendered the "Workout / Active"
fallback with no chronometer and no wakelock. The 1-second ticker used to repaint over this within
a second, which is why it was never noticed; deleting the ticker made it permanent.

Fixed at the source by dropping the write-back (the service owns the holder; the UI only derives
from it), and defensively by passing the state into `buildNotification`, `notifyTick`, and
`updateWakeLock` instead of re-reading the singleton. This is the "missing repost path" risk listed
in the regressions above, and it is exactly why the manual pass over every workout mode was on the
list.

Device results (Pixel 7 Pro AVD, API 37):

| Check | Result |
|---|---|
| Fast notification during forced Doze (`mState=IDLE`) | 00:50 → 04:14 over 152 s, still counting |
| Fresh install, first workout ever (the case that failed) | `Workout · Simple` / `Working`, chronometer on |
| Workout paused | chronometer off, `00:00:45 · Paused`, unchanged 25 s later |
| Workout resumed | chronometer on, resumes at 00:50 — paused time not counted |
| Interval workout wakelock | `ACQ SoloForge:interval-beeps` on start → `REL` on end, `Wake Locks: size=0` after |
| Reboot mid-fast, app never opened | notification restored: `Fasting · 16:8 · 01:25 / Goal at 6:25 AM` |

The reboot result also settles the open question above: a `specialUse` foreground service **can** be
started from `BOOT_COMPLETED` on API 37.

## Verification

Automated (`gradlew testDebugUnitTest`):

- new pure test on the workout chronometer base — the property that matters is *a paused timer must
  not advance*, i.e. `base(now) == base(now + 60s)` while `PAUSED`, and advances 1:1 while
  `EXERCISE`/`REST`
- existing `WorkoutStateRecoveryTest` must still pass unchanged (F2 touches no state math)
- `lintDebug` for `MissingTranslation` on the new strings

Manual, on an emulator and ideally on the reporter's ROM class:

```bash
adb shell dumpsys deviceidle force-idle
```

- start a fast → force-idle → wait 10 min → notification is still counting
- start an interval workout → screen off → beeps continue on schedule
- reboot mid-fast → notification returns without opening the app
- start a fast and immediately press Home → notification appears, no crash in `adb logcat`
- import a backup containing an active fast while the app is backgrounded → no crash
- workout: pause / resume / phase toggle / stop in all three modes → notification always matches
- verify with a network monitor that none of this adds an outbound call (it doesn't)

## Follow-ups

- `CLAUDE.md`: add the interval wakelock to the notifications description. (The WorkManager
  tech-stack line stays accurate now that F4 is dropped.)
- Delete `notif_fasting_text` from all seven `strings.xml` files, not just `values/`.
- Version bump to 0.5.1 + `fastlane/metadata/android/en-US/changelogs/12.txt`.
- Reply on #13 and ask the reporter to confirm on the GitHub debug APK before the F-Droid tag.

## Not doing

- Exact alarms (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`) — Play policy restricts them to
  alarm-clock apps, and Doze throttling makes them useless for interval beeps anyway.
- Moving reminders off WorkManager (F4) — App Standby Buckets defer inexact alarms just as hard.
- A custom `RemoteViews` notification layout to get a full-size timer.
- A wakelock for the fasting service. The chronometer removes the need entirely.
- Switching Room timestamps off wall clock. Separate concern, no user-visible symptom today.
