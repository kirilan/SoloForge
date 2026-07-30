# Solo Forge — Project Context

## Session Startup

At the start of every new agent session, read `CLAUDE.md` and compare it with this file. If `CLAUDE.md` contains important project changes, conventions, feature status, branding decisions, migration rules, privacy constraints, or build/run notes that are missing or stale here, replicate the relevant updates into `AGENTS.md` before making code changes.

A **local-first Android fitness app**. No backend, no auth, no analytics, no cloud telemetry. The only outbound network call is AI food analysis via the user's own OpenRouter API key, and that can be switched off entirely in Settings.

## Features

1. **Intermittent fasting timer** — modes: 16:8, 18:6, 20:4, 36h. Smart context-aware reminders (no time-of-day spam):
   - "Almost there" encouragement 1h before fast ends
   - "Eating window closing" 1h before window ends, scheduled when a completed fast ends
   - Cancellation is automatic when the user takes the opposite action
2. **AI calorie counter** — user supplies their OpenRouter key, then either snaps a photo with an optional comment or types a description; gets structured macros back, edits if needed, and saves locally. Model choice is automatic through the escalation chain in `FoodAnalysisModels`. Non-AI paths are manual entry and one-tap meal presets. The `ai_features_enabled` setting (default on) hides every AI entry point.
3. **Weight tracking** — manual entries, line chart, edit/delete, weekly weigh-in reminder. Food and weight entries have editable date/time; backdating is allowed and future dates are blocked.
4. **Workout timer** — simple, interval, and exercise/rest timers with local workout logging and dashboard calorie bonus. Active phase, pause state, and accumulated exercise/rest time persist in Room across process or service death.
5. **Home dashboard** — at-a-glance tiles for fasting, today's nutrition vs. goals, weight, workout time, and streak.
6. **FOSS/privacy branding** — first-run intro and Settings/About emphasize GPL-3.0, no backend, no accounts, no analytics, and local-first data ownership without adding persistent dashboard clutter.

## Tech stack

- **Kotlin** + **Jetpack Compose** + **Material 3** (dynamic color)
- **Min SDK 26**, **compile SDK 36**, **target SDK 36**
- **Hilt** for DI; **Room** for SQLite; **DataStore** for prefs; **EncryptedSharedPreferences** for the API key
- **WorkManager** for one-shot reminder workers (including a self-rescheduling weekly weigh-in); **foreground services** for active-fast and active-workout notifications
- **CameraX** for capture; **Ktor + kotlinx.serialization** for OpenRouter; **Coil** for image rendering
- Charts are hand-rolled Compose (`WeightChart`); no chart library is used.
- **AGP 8.13.2**, **Gradle 9.0**, **Kotlin 2.0.21**, **KSP** (not kapt)

## Project layout

```
app/src/main/java/com/kbul/spicycrab/
├── MainActivity.kt                 // Single activity, hosts AppNav
├── SpicyCrabApp.kt                 // @HiltAndroidApp, creates notification channels
├── ui/
│   ├── theme/                      // Material 3 theme (Color, Type, Theme.kt)
│   ├── nav/AppNav.kt               // Home, Fast, Food, Weight, Workout, Settings; visibility toggles in Settings
│   ├── onboarding/                 // First-run intro
│   ├── home/                       // Dashboard tiles + month calendar with per-day summaries
│   ├── fasting/                    // FastingScreen + ProgressRing + ViewModel
│   ├── food/                       // List ↔ Capture ↔ Analyze; manual/edit sheets and presets
│   ├── weight/                     // Log/edit sheet, custom chart, range chips
│   ├── workout/                    // Timer modes + logging
│   ├── settings/                   // SettingsScreen + ViewModel
│   └── common/                     // Shared composables
├── data/
│   ├── db/                         // Room AppDatabase, entities, DAOs
│   ├── prefs/SettingsRepo.kt       // DataStore: goals, export URI, units, AI toggle, tab visibility…
│   ├── prefs/SecureKeyStore.kt     // EncryptedSharedPreferences for API key
│   └── backup/BackupManager.kt     // Versioned JSON backup: export/import (merge or replace) + auto-backup
├── domain/
│   ├── fasting/                    // FastingMode, FastingRepository, StreakCalculator
│   ├── nutrition/                  // FoodRepository, NutritionEstimate, ImageUtils
│   ├── weight/                     // WeightRepository
│   ├── workout/                    // WorkoutRepository, modes, state holder
│   └── health/                     // Health Connect integration
├── notifications/                  // Fasting/workout services, reminders, weigh-in worker
├── network/
│   ├── OpenRouterClient.kt
│   ├── OpenRouterDtos.kt
│   └── VisionPrompts.kt            // System prompt + JSON schema
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
- **Backup** is one versioned JSON file (`BackupFile`, kotlinx.serialization) holding all Room tables + settings, never the API key or saved food photos. Imported photo paths are cleared; deleting a food entry deletes its owned photo. Import supports merge (union deduped on natural keys — timestamps, preset name, journal date; local wins on conflict, journal text concatenates, one active fast survives, repeated import is a no-op) or replace. With an auto-backup folder set through `ACTION_OPEN_DOCUMENT_TREE` and a persisted URI permission, `BackupManager` stages and rotates `SoloForge-backup.json` on every data change so an interrupted write cannot destroy the last good copy; writes are debounced and report the latest success/failure in Settings. CSV export was removed in 0.3.0.
- **Health Connect initial import anchors a change token before reading history**, then consumes changes from that token so records changed during the initial read cannot be missed.
- **Android system backup is disabled** (`allowBackup=false`). Device migration should use explicit export/import flows, not silent Android cloud backup.
- **API key** is the only secret; it's in EncryptedSharedPreferences and excluded from auto-backup (`backup_rules.xml` / `data_extraction_rules.xml`).
- **All user-visible strings live in `res/values/strings.xml`** (`screen_element` naming). Compose uses `stringResource`/`pluralStringResource`; services, workers, and repositories use `context.getString`. Shipped locales are en, de, es, fr, pt-rBR, ru, and tr; `locales_config.xml` and Gradle `localeFilters` must agree. OpenRouter prompts stay English because they are model input.
- **Every new string ships translated into all six non-English locales in the same commit.** There is no translation platform. Lint's `MissingTranslation` is an error and CI runs `lintDebug`; do not downgrade the rule.
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

- **From Android Studio**: open `C:\Users\kiril\mobile`, sync Gradle, run `app` on AVD `Pixel_7_Pro` (API 34+).
- **From CLI**: set `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr`, then run `.\gradlew.bat assembleDebug`. The system JVM is 8 and Gradle requires 17+.
- SDK tools live under `%LOCALAPPDATA%\Android\Sdk`.

## License

Solo Forge is licensed under the **GNU General Public License v3.0**. The full license text is in `LICENSE`. Keep the GPL/local-first/no-analytics trust message visible in first-run onboarding and Settings/About, but avoid overcrowding core task screens.

## Release and distribution

Version bumps update `versionCode` and `versionName` plus `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Tags drive F-Droid releases. GitHub releases remain debug-key-signed for upgrade compatibility. Google Play releases use `tools/play_release.py` and the upload key; never upload while a Play review is pending, and run `--dry-run` first. The three distribution channels use different signing keys and are not cross-installable. The official fdroiddata recipe deliberately has no `AntiFeatures:` entry after reviewer removal; do not re-add it there.

The public site and canonical privacy policy are at `https://soloforge.dimitroff.work` and `/privacy`; the site lives in the separate `C:\Users\kiril\soloforge-site` repository. Website edits happen there, never in this repository. `PRIVACY.md` here is only a pointer. Keep the canonical policy synchronized with every data flow and explicitly describe deletion.

## Privacy guarantees (don't break these)

- **Only outbound host:** `openrouter.ai`. Only when the user explicitly analyzes a food photo or text description. With AI disabled, there are zero network calls. Verify with `adb shell` + a network monitor before any release.
- No analytics SDKs. No crash reporters. No Firebase. No Google Play Services dependency.
- No `READ_CONTACTS`, no `READ_LOGS`, no broad media permissions.
- `INTERNET` is declared because Ktor needs it; nothing else should use it.

## Useful pointers

- The active fast persists across process death — verify by force-stopping the app mid-fast.
- To shorten reminder delays for testing, edit the constants in `ReminderScheduler.kt` (use minutes instead of hours), then revert.
