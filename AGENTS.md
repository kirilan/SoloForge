# Solo Forge — Project Context

## Session Startup

At the start of every new agent session, read `CLAUDE.md` and compare it with this file. If `CLAUDE.md` contains important project changes, conventions, feature status, branding decisions, migration rules, privacy constraints, or build/run notes that are missing or stale here, replicate the relevant updates into `AGENTS.md` before making code changes.

A **local-first Android fitness app**. No backend, no auth, no analytics, no cloud telemetry. The only outbound network call is the user's own OpenRouter API key for food-photo analysis.

## Features

1. **Intermittent fasting timer** — modes: 16:8, 18:6, 20:4, 36h. Smart context-aware reminders (no time-of-day spam):
   - "Almost there" encouragement 1h before fast ends
   - "Eating window closing" 1h before window ends, scheduled when a completed fast ends
   - Cancellation is automatic when the user takes the opposite action
2. **AI calorie counter** — user supplies their OpenRouter key, snaps a photo with optional comment, gets structured macros back, edits if needed, saves locally + appends to CSV in a user-chosen folder (Google Drive / Dropbox synced folder works automatically through SAF). Model choice is automatic through the escalation chain in `FoodAnalysisModels`.
3. **Weight tracking** — manual entries, line chart, edit/delete, CSV export, weekly weigh-in reminder.
4. **Workout timer** — simple, interval, and exercise/rest timers with local workout logging and dashboard calorie bonus.
5. **Home dashboard** — at-a-glance tiles for fasting, today's nutrition vs. goals, weight, workout time, and streak.
6. **FOSS/privacy branding** — first-run intro and Settings/About emphasize GPL-3.0, no backend, no accounts, no analytics, and local-first data ownership without adding persistent dashboard clutter.

The full plan lives in `~/.Codex/plans/i-want-to-make-spicy-crab.md` (outside the repo).

## Tech stack

- **Kotlin** + **Jetpack Compose** + **Material 3** (dynamic color)
- **Min SDK 26**, **compile/target SDK 35**
- **Hilt** for DI; **Room** for SQLite; **DataStore** for prefs; **EncryptedSharedPreferences** for the API key
- **WorkManager** for one-shot reminder workers; **foreground service** for the active-fast live notification
- **CameraX** for capture; **Ktor + kotlinx.serialization** for OpenRouter; **Coil** for image rendering
- **Vico** for charts (added but not yet used)
- **AGP 8.13.2**, **Gradle 9.0**, **Kotlin 2.0.21**, **KSP** (not kapt)

## Project layout

```
app/src/main/java/com/kbul/spicycrab/
├── MainActivity.kt                 // Single activity, hosts AppNav
├── SpicyCrabApp.kt                 // @HiltAndroidApp, creates notification channels
├── ui/
│   ├── theme/                      // Material 3 theme (Color, Type, Theme.kt)
│   ├── nav/AppNav.kt               // 5-tab bottom nav: Home, Fast, Food, Weight, Settings
│   ├── home/                       // Dashboard (currently placeholders)
│   ├── fasting/                    // FastingScreen + ProgressRing + ViewModel
│   ├── food/                       // List ↔ Capture ↔ Analyze; FoodViewModel
│   ├── weight/                     // Stub
│   └── settings/                   // SettingsScreen + ViewModel
├── data/
│   ├── db/                         // Room AppDatabase, entities, DAOs
│   ├── prefs/SettingsRepo.kt       // DataStore for goals, export URI, units
│   ├── prefs/SecureKeyStore.kt     // EncryptedSharedPreferences for API key
│   └── csv/CsvExporter.kt          // SAF-based append-on-write
├── domain/
│   ├── fasting/                    // FastingMode, FastingRepository, StreakCalculator
│   └── nutrition/                  // FoodRepository, NutritionEstimate, ImageUtils
├── notifications/
│   ├── NotificationChannels.kt
│   ├── FastingNotificationService.kt   // Foreground service with 30s ticking
│   ├── ReminderScheduler.kt            // WorkManager scheduling
│   └── FastingReminderWorker.kt        // CoroutineWorker that posts notifications
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
- **Source of truth for the active fast = the row in Room** (start timestamp). UI ticks every 1s and computes elapsed; killing the app never breaks the timer.
- **Reminders** are *state-driven*, not time-of-day-driven. Schedule when a fast starts/ends; cancel on the opposite event.
- **CSV export** on every save/update/delete tombstone when an export folder is set — user picks once via `ACTION_OPEN_DOCUMENT_TREE` and we persist URI permission.
- **Android system backup is disabled** (`allowBackup=false`). Device migration should use explicit export/import flows, not silent Android cloud backup.
- **API key** is the only secret; it's in EncryptedSharedPreferences and excluded from auto-backup (`backup_rules.xml` / `data_extraction_rules.xml`).
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

- **From Android Studio**: open `C:\Users\kiril\mobile`, sync Gradle, run `app` on an emulator (Pixel 7 / API 34+).
- **From CLI**: `./gradlew.bat assembleDebug` from the project root. The wrapper is committed.

## License

Solo Forge is licensed under the **GNU General Public License v3.0**. The full license text is in `LICENSE`. Keep the GPL/local-first/no-analytics trust message visible in first-run onboarding and Settings/About, but avoid overcrowding core task screens.

## Privacy guarantees (don't break these)

- **Only outbound host:** `openrouter.ai`. Only when a food photo is being analyzed. Verify with `adb shell` + a network monitor before any release.
- No analytics SDKs. No crash reporters. No Firebase. No Google Play Services dependency.
- No `READ_CONTACTS`, no `READ_LOGS`, no broad media permissions.
- `INTERNET` is declared because Ktor needs it; nothing else should use it.

## Useful pointers

- Plan file (full design + verification matrix): `C:\Users\kiril\.Codex\plans\i-want-to-make-spicy-crab.md`
- The active fast persists across process death — verify by force-stopping the app mid-fast.
- To shorten reminder delays for testing, edit the constants in `ReminderScheduler.kt` (use minutes instead of hours), then revert.
