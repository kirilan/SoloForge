# Solo Forge — Project Context

A **local-first Android fitness app**. No backend, no auth, no analytics, no cloud telemetry. The only outbound network call is AI food analysis via the user's own OpenRouter API key — and even that can be switched off entirely in Settings.

## Features

1. **Intermittent fasting timer** — modes: 16:8, 18:6, 20:4, 36h. Smart context-aware reminders (no time-of-day spam):
   - "Almost there" encouragement 1h before fast ends
   - "Eating window closing" 1h before window ends, scheduled when a completed fast ends
   - Cancellation is automatic when the user takes the opposite action
2. **AI calorie counter** — user supplies their OpenRouter key, then either snaps a photo (with optional comment) or types a description ("100 g watermelon"); gets structured macros back, edits if needed, saves locally. Model choice is automatic through the escalation chain in `FoodAnalysisModels`. Non-AI paths: manual entry and one-tap meal presets ("Quick add"). The `ai_features_enabled` setting (default on) hides every AI entry point.
3. **Weight tracking** — manual entries, line chart, edit/delete, weekly weigh-in reminder. Food and weight entries have editable date/time (backdating allowed, future dates blocked).
4. **Workout timer** — simple, interval, and exercise/rest timers with local workout logging and dashboard calorie bonus. Active phase, pause state, and accumulated exercise/rest time persist in Room across process or service death.
5. **Home dashboard** — at-a-glance tiles for fasting, today's nutrition vs. goals, weight, workout time, and streak.
6. **FOSS/privacy branding** — first-run intro and Settings/About emphasize GPL-3.0, no backend, no accounts, no analytics, and local-first data ownership without adding persistent dashboard clutter.

## Tech stack

- **Kotlin** + **Jetpack Compose** + **Material 3** (dynamic color)
- **Min SDK 26**, **compile SDK 36**, **target SDK 36**
- **Hilt** for DI; **Room** for SQLite; **DataStore** for prefs; **EncryptedSharedPreferences** for the API key
- **WorkManager** for one-shot reminder workers (including a self-rescheduling weekly weigh-in); **foreground services** for active-fast and active-workout notifications
- **CameraX** for capture; **Ktor + kotlinx.serialization** for OpenRouter; **Coil** for image rendering
- Charts are hand-rolled Compose (`WeightChart`) — no chart library.
- **AGP 9.3.1**, **Gradle 9.5**, **Kotlin 2.3.21** with AGP built-in Kotlin, **KSP 2.3.10** (not kapt)

## Project layout

```
app/src/main/java/com/kbul/spicycrab/
├── MainActivity.kt                 // Single activity, hosts AppNav
├── SpicyCrabApp.kt                 // @HiltAndroidApp, creates notification channels
├── ui/
│   ├── theme/                      // Material 3 theme (Color, Type, Theme.kt)
│   ├── nav/AppNav.kt               // Bottom nav: Home, Fast, Food, Weight, Workout, Settings (tab visibility toggles in Settings)
│   ├── onboarding/                 // First-run intro
│   ├── home/                       // Dashboard tiles + month calendar with per-day summaries
│   ├── fasting/                    // FastingScreen + ProgressRing + ViewModel
│   ├── food/                       // List ↔ Capture ↔ Analyze (photo or text); manual/edit sheets, presets
│   ├── weight/                     // Log/edit sheet, custom chart, range chips
│   ├── workout/                    // Timer modes + logging
│   ├── settings/                   // SettingsScreen + ViewModel
│   └── common/                     // Shared composables (DateTimeField)
├── data/
│   ├── db/                         // Room AppDatabase, entities, DAOs, Migrations
│   ├── prefs/SettingsRepo.kt       // DataStore: goals, export URI, units, AI toggle, tab visibility…
│   ├── prefs/SecureKeyStore.kt     // EncryptedSharedPreferences for API key
│   └── backup/BackupManager.kt     // Versioned JSON backup: export/import (merge or replace) + auto-backup
├── domain/
│   ├── fasting/                    // FastingMode, FastingRepository, StreakCalculator
│   ├── nutrition/                  // FoodRepository (analysis chain), NutritionEstimate, ImageUtils
│   ├── weight/                     // WeightRepository (kg/lb conversion)
│   └── workout/                    // WorkoutRepository, modes, state holder
├── notifications/                  // Channels, fasting/workout foreground services, ReminderScheduler, reminder + weigh-in workers
├── network/
│   ├── OpenRouterClient.kt         // One chat-completions call; image part optional (text-only mode)
│   ├── OpenRouterDtos.kt
│   └── VisionPrompts.kt            // System prompt + JSON schema (covers photo and text)
└── di/DatabaseModule.kt            // Hilt module for Room
```

## Conventions

- **Room migrations are mandatory.** `fallbackToDestructiveMigration()` is gone. To make a schema change:
  1. Edit the `@Entity`.
  2. Bump `version` in `AppDatabase.kt`.
  3. Add a `Migration(oldVersion, newVersion)` to `Migrations.kt` and append it to `ALL_MIGRATIONS`.
  4. Build once — KSP exports the new schema to `app/schemas/<n>.json` (commit it).
  5. Add a `MigrationTestHelper` test under `app/src/androidTest/...` that walks data through the new migration.
  6. **Notes**: SQLite ALTER TABLE ADD COLUMN with `NOT NULL DEFAULT x` produces a column with a recorded default that Room's schema check rejects unless the entity declares the same default. Use the rename-recreate-copy-drop pattern instead (see `MIGRATION_2_3` for a reference).
- **Single-activity** Compose architecture with bottom nav. Sub-flows (Capture → Analyze) live in a sealed `UiMode` inside the feature ViewModel, **not** as nav routes.
- **ViewModels** use `StateFlow` (not LiveData), exposed as `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- **DI**: every repository / DAO / network client is `@Singleton` and constructor-injected via Hilt.
- **Source of truth for active fasts and workouts = their rows in Room.** UI ticks from persisted timestamps, and `MainActivity` reconciles their foreground services on start so force-stop plus relaunch restores live notifications.
- **At most one fast may be active.** All fast mutations are serialized and reject future timestamps or restoring a historical fast while another is active; backup merge and replace enforce the same invariant.
- **Source of truth for the active workout = the row in Room**, including current phase, phase start, and accumulated exercise/rest seconds. `WorkoutStateHolder` is only the live in-process mirror.
- **AI off is a hard privacy boundary.** Disabling it closes and cancels active analysis flows, deletes owned capture-cache files, and the repository checks the setting before every OpenRouter request.
- **Notification permission is requested centrally after onboarding.** Feature screens must not own their own notification-permission prompt.
- **Reminders** are *state-driven*, not time-of-day-driven. Settings changes resynchronize scheduled work, and workers re-check current Room/settings state before notifying or rescheduling.
- **Backup** is one versioned JSON file (`BackupFile`, kotlinx.serialization) holding all Room tables + settings, never the API key or saved food photos. Imported photo paths are cleared; deleting a food entry deletes its owned photo. Manual export/import lives in Settings; import offers **merge** (union deduped on natural keys — timestamps, preset name, journal date; local wins on conflict, journal concatenates, one active fast survives; importing the same file twice is a no-op) or **replace**. When an auto-backup folder is set (`ACTION_OPEN_DOCUMENT_TREE`, persisted URI permission), `BackupManager` stages and rotates `SoloForge-backup.json` on every data change so an interrupted write cannot destroy the last good copy; writes are debounced and report the latest success/failure in Settings — that folder synced to Drive/Dropbox is the continuous off-device backup. CSV export was removed in 0.3.0.
- **Health Connect initial import anchors a change token before reading history**, then consumes changes from that token so records changed during the initial read cannot be missed.
- **Android system backup is disabled** (`allowBackup=false`). Device migration should use explicit export/import flows, not silent Android cloud backup.
- **API key** is the only secret; it's in EncryptedSharedPreferences and excluded from auto-backup (`backup_rules.xml` / `data_extraction_rules.xml`).
- **All user-visible strings live in `res/values/strings.xml`** (`screen_element` naming, e.g. `fasting_start`). Compose uses `stringResource`/`pluralStringResource`; services, workers, and repositories use `context.getString`. Shipped locales: en, de, es, fr, pt-rBR, ru, tr (`locales_config.xml` + `localeFilters` in `app/build.gradle.kts` must list the same set). OpenRouter prompts (`VisionPrompts.kt`) stay English — they're model input, not UI.
- **Every new string ships translated into all six locales in the same commit.** There is no translation platform: Weblate was never set up and is parked until there's demand for more languages, so translations are machine-produced and reviewed by eye. Product names (e.g. "Health Connect") stay untranslated. This is enforced, not remembered — lint's `MissingTranslation` is an error and CI runs `lintDebug`, so a half-translated string set fails the build. Don't downgrade that rule to get a build through; add the translations.
- **No comments unless the *why* is non-obvious.** Prefer well-named functions to docstrings.
- **No barebones fallbacks or "in case X fails" code paths** unless the failure is at a real boundary (network, file I/O, missing key).

## Food analysis model chain

```
google/gemini-3.1-flash-lite  # default
openai/gpt-5.4-mini           # used when the default result is low confidence or suggests mixed/hidden ingredients
google/gemini-3.1-pro-preview # used only if uncertainty remains
```

Users do not choose the model. If confidence is still low after the full chain, the UI prompts the user to add more details such as portion size, cooking oil, sauces, and hidden ingredients before re-analyzing.

## Build & run

- **From Android Studio**: open `C:\Users\kiril\mobile`, sync Gradle, run `app` on an emulator (AVD `Pixel_7_Pro`, API 34+).
- **From CLI**: set `JAVA_HOME` first — the system JVM is 8 and Gradle needs 17+:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`
- SDK tools (adb, apksigner, aapt2) live under `%LOCALAPPDATA%\Android\Sdk`.

## License

Solo Forge is licensed under the **GNU General Public License v3.0**. The full license text is in `LICENSE`. Keep the GPL/local-first/no-analytics trust message visible in first-run onboarding and Settings/About, but avoid overcrowding core task screens.

## Release process

Three distribution channels with different signing keys (builds are not cross-installable):

1. **Translations**: nothing to do at release time — strings are translated as they're added
   (see the strings convention above), so `main` is always release-ready on that front.
2. **Version bump**: `versionCode` + `versionName` in `app/build.gradle.kts`, and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (plain prose, ~1 short paragraph).
3. **Commit + tag** `vX.Y.Z` and push. **The tag push IS the F-Droid release** — fdroiddata uses
   `UpdateCheckMode: Tags` + `AutoUpdateMode: Version`; their bot picks it up and publishes in ~2–7 days.
4. **GitHub release**: `gh release create vX.Y.Z` with notes and a **debug-signed APK** named
   `SoloForge-X.Y.Z-debug.apk`. GitHub releases have always been debug-key-signed; switching keys would
   break in-place updates (and lose local data) for existing GitHub users, so keep the debug key.
5. **Google Play**: live at https://play.google.com/store/apps/details?id=com.kbul.spicycrab.
   Separate track, signed AAB via the upload key (`.\gradlew.bat bundleRelease`,
   reads `keystore.properties` at repo root, gitignored). Never upload a new AAB while a Play review
   is pending. Publish with `python tools/play_release.py` (`--dry-run` first) rather than the
   Console UI — it uploads the AAB, sets the release notes from `changelogs/<versionCode>.txt`, and
   starts a full rollout via the Play Developer API. Needs `GOOGLE_PLAY_SERVICE_ACCOUNT` pointing at
   a service-account JSON with "Release manager"; keep that file out of the repo, like the keystore.
   Policy declarations and App content forms still have to be done in the Console by hand.

### Website

The project site is **https://soloforge.dimitroff.work** — a two-page static site (landing + privacy
policy) in a separate repo, `kirilan/soloforge-site` (local checkout: `C:\Users\kiril\soloforge-site`),
auto-deployed by Cloudflare Pages on push to `main`. The landing page mirrors the fastlane store
listing (copy, screenshots, badges), so when a release changes features, screenshots, or the store
description, update the site too. Website edits happen in that repo, never in this one.

**`https://soloforge.dimitroff.work/privacy` is the one and only privacy policy.** It is the URL
registered in Play Console, so anything it claims is what Google reviews against — keep it in sync
with the privacy guarantees below, and with Health Connect and any other data the app touches.
`PRIVACY.md` in this repo is only a pointer to it; do not grow it back into a second copy. Play
rejected the 0.4.0 update on 25 Jul 2026 because the then-registered policy (`PRIVACY.md`) had gone
stale — no data-deletion section, no Health Connect, and still documenting the CSV export removed in
0.3.0. A policy must state how users delete their data, explicitly, using that word, even when the
answer is "there is no request process because nothing is held".

### F-Droid metadata

F-Droid listing content is pulled from `fastlane/metadata/android/en-US/`. The OpenRouter food feature is disclosed as `NonFreeNet` per-build in the repo's `.fdroid.yml`, but the official fdroiddata recipe deliberately has **no** `AntiFeatures:` — the F-Droid reviewer removed it during inclusion review (fdroiddata MR !38080, "Remove the AF") because the feature is off by default, opt-in, and needs the user's own key. Do not re-add it to fdroiddata.

## Privacy guarantees (don't break these)

- **Only outbound host:** `openrouter.ai`. Only when the user explicitly analyzes a food photo or text description. With the AI toggle off, zero network calls. Verify with `adb shell` + a network monitor before any release.
- No analytics SDKs. No crash reporters. No Firebase. No Google Play Services dependency.
- No `READ_CONTACTS`, no `READ_LOGS`, no broad media permissions.
- `INTERNET` is declared because Ktor needs it; nothing else should use it.

## Useful pointers

- The active fast persists across process death — verify by force-stopping the app mid-fast.
- To shorten reminder delays for testing, edit the constants in `ReminderScheduler.kt` (use minutes instead of hours), then revert.
