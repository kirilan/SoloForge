# Solo Forge — Project Context

A **local-first Android fitness app**. No backend, no auth, no analytics, no cloud telemetry. The only outbound network call is AI food analysis via the user's own OpenRouter API key — and even that can be switched off entirely in Settings.

## Features

1. **Intermittent fasting timer** — modes: 16:8, 18:6, 20:4, 36h. Smart context-aware reminders (no time-of-day spam):
   - "Almost there" encouragement 1h before fast ends
   - "Eating window closing" 1h before window ends, scheduled when a completed fast ends
   - Cancellation is automatic when the user takes the opposite action
2. **AI calorie counter** — user supplies their OpenRouter key, then either snaps a photo (with optional comment) or types a description ("100 g watermelon"); gets structured macros back, edits if needed, saves locally + appends to CSV in a user-chosen folder (Google Drive / Dropbox synced folder works automatically through SAF). Model choice is automatic through the escalation chain in `FoodAnalysisModels`. Non-AI paths: manual entry and one-tap meal presets ("Quick add"). The `ai_features_enabled` setting (default on) hides every AI entry point.
3. **Weight tracking** — manual entries, line chart, edit/delete, CSV export, weekly weigh-in reminder. Food and weight entries have editable date/time (backdating allowed, future dates blocked).
4. **Workout timer** — simple, interval, and exercise/rest timers with local workout logging and dashboard calorie bonus.
5. **Home dashboard** — at-a-glance tiles for fasting, today's nutrition vs. goals, weight, workout time, and streak.
6. **FOSS/privacy branding** — first-run intro and Settings/About emphasize GPL-3.0, no backend, no accounts, no analytics, and local-first data ownership without adding persistent dashboard clutter.

## Tech stack

- **Kotlin** + **Jetpack Compose** + **Material 3** (dynamic color)
- **Min SDK 26**, **compile/target SDK 35**
- **Hilt** for DI; **Room** for SQLite; **DataStore** for prefs; **EncryptedSharedPreferences** for the API key
- **WorkManager** for one-shot reminder workers; **foreground service** for the active-fast live notification
- **CameraX** for capture; **Ktor + kotlinx.serialization** for OpenRouter; **Coil** for image rendering
- Charts are hand-rolled Compose (`WeightChart`) — no chart library.
- **AGP 8.13.2**, **Gradle 9.0**, **Kotlin 2.0.21**, **KSP** (not kapt)

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
│   └── csv/CsvExporter.kt          // SAF-based append-on-write
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

- **From Android Studio**: open `C:\Users\kiril\mobile`, sync Gradle, run `app` on an emulator (AVD `Pixel_7_Pro`, API 34+).
- **From CLI**: set `JAVA_HOME` first — the system JVM is 8 and Gradle needs 17+:
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`
- SDK tools (adb, apksigner, aapt2) live under `%LOCALAPPDATA%\Android\Sdk`.

## License

Solo Forge is licensed under the **GNU General Public License v3.0**. The full license text is in `LICENSE`. Keep the GPL/local-first/no-analytics trust message visible in first-run onboarding and Settings/About, but avoid overcrowding core task screens.

## Release process

Three distribution channels with different signing keys (builds are not cross-installable):

1. **Version bump**: `versionCode` + `versionName` in `app/build.gradle.kts`, and add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (plain prose, ~1 short paragraph).
2. **Commit + tag** `vX.Y.Z` and push. **The tag push IS the F-Droid release** — fdroiddata uses
   `UpdateCheckMode: Tags` + `AutoUpdateMode: Version`; their bot picks it up and publishes in ~2–7 days.
3. **GitHub release**: `gh release create vX.Y.Z` with notes and a **debug-signed APK** named
   `SoloForge-X.Y.Z-debug.apk`. GitHub releases have always been debug-key-signed; switching keys would
   break in-place updates (and lose local data) for existing GitHub users, so keep the debug key.
4. **Google Play**: separate track, signed AAB via the upload key (`.\gradlew.bat bundleRelease`,
   reads `keystore.properties` at repo root, gitignored). Never upload a new AAB while a Play review
   is pending.

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
