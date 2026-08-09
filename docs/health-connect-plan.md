# Health Connect sync plan (stashed — not started)

GitHub issue #12 (second half): sync workouts from Gadgetbridge and weight from openScale
into Solo Forge via Health Connect. Reporter's real ask is *import* — their devices already
capture the data; retyping it is the friction.

## Direction is user-configurable by design

Health Connect permissions are per record type **and** per direction. We expose two
Settings toggles — one per direction, each covering weight + workouts:

| Toggle                       | Permissions requested                                                                |
|------------------------------|--------------------------------------------------------------------------------------|
| Import from Health Connect   | `android.permission.health.READ_WEIGHT` + `READ_EXERCISE`                             |
| Export to Health Connect     | `android.permission.health.WRITE_WEIGHT` + `WRITE_EXERCISE`                            |

All four permissions are declared in the manifest (declaration ≠ grant); runtime grant happens
via the HC permission sheet when a toggle is switched on (the sheet itemizes both record types;
the user can grant a subset — we sync whatever was granted). Toggle off → we stop syncing
(plus a deep link to HC's app settings for revoking). Default: both off. Read-only users never
see a write permission requested — the OS sheet is the proof.
<!-- ponytail: per-type split (4 toggles) deferred; add only if someone asks to import weight but not workouts -->

## Architecture

- Dependency: `androidx.health.connect:connect-client` (one library, on-device only — the
  no-network guarantee is untouched; nothing changes about "only outbound host: openrouter.ai").
- **Optional at runtime.** `HealthConnectClient.getSdkStatus()` gates everything; on de-Googled
  ROMs / Android < 13 without the HC APK the whole Settings section renders as a single
  explanatory line (or hides). No Play Services dependency — HC's client talks to the HC app,
  which exists on GrapheneOS etc. as an installable APK.
- New `domain/health/HealthConnectRepository` (@Singleton, wraps the client; the only file that
  imports HC types). Sync runs on app foreground (`Lifecycle.Event.ON_START` of MainActivity)
  plus a "Sync now" row in Settings. **No background sync in v1** — background HC reads need
  another permission (Android 15+) and WorkManager plumbing; foreground-on-open covers the
  daily-dashboard use case.

## Import semantics (the real work)

- **Dedup via HC record ids.** Add nullable `healthConnectId: String?` to `WeightEntry` and
  `WorkoutSession` (Room migration → follow the CLAUDE.md migration checklist; two new schema
  jsons + MigrationTestHelper test). Imported rows carry the HC id; sync upserts on it, so
  re-running sync or editing in the source app never duplicates.
- **Don't re-import our own exports**: skip records whose `metadata.dataOrigin` is our package.
- Weight: `WeightRecord` → kg (we store kg internally already).
- Workouts: `ExerciseSessionRecord` → `WorkoutSession` with mode `SIMPLE`,
  `totalSeconds = end - start`, notes = HC title if present. No attempt to map exercise types
  onto interval/exercise-rest modes — imported sessions are duration entries for the dashboard
  and calorie bonus.
- Initial import window: last 30 days on first enable (avoids flooding history), then
  incremental via HC changes API (`getChanges` + stored change token; fall back to
  time-range diff if the token expires).
- Imported rows are editable/deletable locally like any other; local edits win (we don't
  write corrections back to HC even when export is on — one-way per record).

## Export semantics

- On insert/update of a local weight or workout (and only those created in-app, i.e.
  `healthConnectId == null`), upsert to HC with `clientRecordId = "sf-weight-<rowId>"` /
  `"sf-workout-<rowId>"` — idempotent, edits update the same HC record. Delete locally →
  delete in HC by clientRecordId.

## Backup / privacy / store fallout

- `healthConnectId` and toggle states go into the JSON backup like any other column/setting;
  HC ids are meaningless on another device — importer treats them as opaque and keeps them
  (worst case: one duplicate-free re-sync re-links).
- Privacy policy page (soloforge-site) gains a Health Connect paragraph: data flows only
  through Android's on-device Health Connect store, permission-gated, off by default.
- **Google Play**: health permissions require Play Console's Health apps declaration form and
  approval before an AAB using them can ship. Do NOT bundle this feature into a Play release
  until that's approved — and don't start the declaration while the production-access review
  is still pending. F-Droid/GitHub are unaffected. If needed, the Play channel could ship
  later than F-Droid for this one version (same code, approval is per-listing not per-build).
- fastlane full_description gains a Health Connect bullet once shipped.

## Settings UI sketch

New "Health Connect" SectionCard (only when SDK status = available):
explanatory line ("Sync with other health apps on this device — nothing leaves your phone"),
2 SwitchRows (import / export), "Sync now" + last-sync timestamp, link to HC app for
permission management.

## Before starting

**Decided (2026-07-17):** v1 ships **both** directions — import and export — as two independent
toggles, both **off by default**, each requesting its permissions only on enable. Not waiting on
issue #12 to start (Phase 1 schema work is scope-agnostic). Still worth confirming later that
Gadgetbridge/openScale write the record types we read on the reporter's setup.

## Order of work

1. Room migration (`healthConnectId` columns) + migration test.
2. `HealthConnectRepository` + availability gating + Settings toggles/permission flow.
3. Weight import (simplest end-to-end slice, openScale testable in emulator via HC toolbox).
4. Workout import, then exports.
5. Strings → `strings.xml` + 6 locale files (post-i18n convention), privacy page, store metadata.
6. Ship as 0.4.0 on F-Droid/GitHub; Play only after the health declaration is approved.

## Explicitly out of scope (v1)

- Background/periodic sync (needs Android 15 background-read permission).
- Importing steps, calories, sleep, or nutrition; exporting nutrition.
- Mapping HC exercise types to interval/exercise-rest modes.
- Any cloud sync — HC is the only integration point, on-device by definition.
