<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="Solo Forge icon">
</p>

<h1 align="center">Solo Forge</h1>

<p align="center">Local-first intermittent fasting, calorie, weight &amp; workout tracker</p>

<p align="center">
  <a href="https://f-droid.org/packages/com.kbul.spicycrab/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="75" alt="Get it on F-Droid"></a>
  <a href="https://play.google.com/store/apps/details?id=com.kbul.spicycrab"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="75" alt="Get it on Google Play"></a>
</p>

Solo Forge is a free and open source, local-first Android fitness app for fasting, nutrition, weight tracking, and workout timing. It is GPL-3.0 licensed and built to keep user data on the device, with no backend, account system, analytics, or cloud telemetry.

The only intentional outbound network request is a user-initiated OpenRouter call for food analysis (photo or text description), using an API key supplied by the user. AI features can be switched off entirely in Settings.

**Website:** [soloforge.dimitroff.work](https://soloforge.dimitroff.work) — landing page and privacy policy. Maintained in the separate [soloforge-site](https://github.com/kirilan/soloforge-site) repo (Cloudflare Pages, deploys on push); update it when a release changes features, screenshots, or the store description.

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png" width="19%" alt="Home dashboard">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_fast.png" width="19%" alt="Fasting timer">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_food.png" width="19%" alt="Food tracking">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_workout.png" width="19%" alt="Workout timers">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_settings.png" width="19%" alt="Settings">
</p>

## Current Features

- Intermittent fasting timer with 16:8, 18:6, 20:4, and 36 hour modes.
- Smart fasting reminders driven by timer state instead of fixed daily spam.
- AI-assisted food analysis (photo or text description) through OpenRouter — one request per analysis, with an off switch in Settings.
- Manual meal entry and one-tap meal presets when the user does not want to use AI.
- Editable food entries, including proportional macro recalculation by total grams.
- Local nutrition tracking with calorie and macro goals.
- Versioned JSON backup: export, import (merge or replace), and an optional auto-backup folder that rewrites the backup file on every data change.
- Weight tracking with edit/delete support, history, charting, and a weekly weigh-in reminder.
- Workout timers:
  - simple session timer with pause/resume
  - interval timer with periodic audio cues
  - exercise/rest toggle timer
- Home dashboard with daily fasting, nutrition, workout, weight, and calendar progress, plus a per-day journal note.
- Optional Health Connect sync for weight and exercise (import and export toggled separately, both off by default).
- Configurable bottom tabs.
- Localized in English, German, Spanish, French, Brazilian Portuguese, Russian, and Turkish.
- Local Room migrations with exported schemas and migration tests.

## Privacy Model

Solo Forge is designed around local ownership of fitness data.

- No backend.
- No authentication.
- No analytics SDKs.
- No crash reporting SDKs.
- No Firebase.
- No broad media, contacts, or logs permissions.
- Android system cloud backup is disabled (`allowBackup=false`); device migration uses the explicit export/import flow.
- The OpenRouter API key is stored in encrypted local preferences and is never included in backups.
- Backup is explicit: a versioned JSON file exported on demand, or auto-written to a user-selected folder (which the user can sync to Drive/Dropbox themselves).

The app declares `INTERNET` only for food analysis through OpenRouter. That call happens only when the user starts an analysis; with AI features off, the app makes zero network calls.

Health Connect is opt-in and off by default. It is an on-device integration: weight and exercise records are read from and written to the local Health Connect store, never uploaded anywhere by Solo Forge.

Every food analysis is a single request to `google/gemini-3.1-flash-lite`. There is no automatic escalation; `google/gemini-3.1-pro-preview` is used only when the user explicitly taps "Retry with a stronger model". Uncertain results are surfaced to the user by `AnalysisPolicy` instead of being silently re-sent to a bigger model.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Single-activity architecture
- Hilt dependency injection
- Room SQLite storage
- DataStore preferences
- EncryptedSharedPreferences for the OpenRouter API key
- WorkManager for scheduled reminders
- Foreground services for active timers
- CameraX for food capture
- Ktor and kotlinx.serialization for OpenRouter
- Coil for image loading
- Hand-rolled Compose charts (no chart library)

## Requirements

- Android Studio with Android SDK installed
- JDK 17
- Android emulator or physical Android device
- Min SDK 26
- Compile SDK 36
- Target SDK 36

## Build

Clone the repo and open it in Android Studio, or build from the command line:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install on a connected emulator or device:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Run the unit tests (backup format, calendar math, streaks, analysis policy, workout state recovery, OpenRouter parsing):

```powershell
.\gradlew.bat testDebugUnitTest
```

Run the connected Room migration tests with an emulator or device attached:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Run lint (`MissingTranslation` is an error — every new string ships in all seven locales):

```powershell
.\gradlew.bat lintDebug
```

Before changing a model id, prompt, temperature, image preprocessing, or the response schema, replay the request against the local case set with `tools/food_eval/` (see its README).

## F-Droid

Solo Forge is published on F-Droid as [`com.kbul.spicycrab`](https://f-droid.org/packages/com.kbul.spicycrab/). Listing content comes from:

```text
.fdroid.yml
fastlane/metadata/android/en-US/
```

The fdroiddata recipe uses `UpdateCheckMode: Tags` with `AutoUpdateMode: Version`, so pushing a `vX.Y.Z` release tag is what triggers an F-Droid release (published in roughly 2–7 days). The official recipe deliberately has no `AntiFeatures:` — the F-Droid reviewer removed the `NonFreeNet` flag during inclusion review (fdroiddata MR !38080) because the OpenRouter feature is off by default, opt-in, and requires the user's own key. Do not re-add it.

## Google Play

Solo Forge is also live on Google Play as [`com.kbul.spicycrab`](https://play.google.com/store/apps/details?id=com.kbul.spicycrab). The Play build is a release AAB signed with the upload key (`.\gradlew.bat bundleRelease`), so it is a separate signing identity from the F-Droid and GitHub builds and the three are not cross-installable. The store listing text and screenshots come from the same `fastlane/metadata/android/en-US/` content.

## Project Layout

```text
app/src/main/java/com/kbul/spicycrab/
├── MainActivity.kt
├── SpicyCrabApp.kt
├── data/
│   ├── backup/
│   ├── db/
│   └── prefs/
├── di/
├── domain/
│   ├── fasting/
│   ├── health/
│   ├── nutrition/
│   ├── weight/
│   └── workout/
├── network/
├── notifications/
└── ui/
    ├── common/
    ├── fasting/
    ├── food/
    ├── home/
    ├── nav/
    ├── onboarding/
    ├── settings/
    ├── theme/
    ├── weight/
    └── workout/
```

The package name still uses the original `com.kbul.spicycrab` namespace. The app-facing brand is Solo Forge.

## Room Migrations

Room migrations are mandatory. Do not use destructive migration fallbacks.

For schema changes:

1. Edit the entity.
2. Bump the Room database version.
3. Add a migration in `Migrations.kt`.
4. Append it to `ALL_MIGRATIONS`.
5. Build once to export the new schema under `app/schemas/`.
6. Add or update an androidTest migration test.

## Branding

Brand notes and early SVG assets live in:

```text
BRAND-BRIEF.md
brand/
```

## License

Solo Forge is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for the full license text.

## Status

Solo Forge is released and published on F-Droid and Google Play, with debug-signed APKs on GitHub releases. Core features — fasting, food tracking, weight, workouts, dashboard, and JSON backup export/import — are in place; development continues on UI polish and broader device testing.
