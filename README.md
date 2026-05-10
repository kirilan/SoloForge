# Solo Forge

Solo Forge is a free and open source, local-first Android fitness app for fasting, nutrition, weight tracking, and workout timing. It is GPL-3.0 licensed and built to keep user data on the device, with no backend, account system, analytics, or cloud telemetry.

The only intentional outbound network request is a user-initiated OpenRouter call for food photo analysis, using an API key supplied by the user.

## Current Features

- Intermittent fasting timer with 16:8, 18:6, 20:4, and 36 hour modes.
- Smart fasting reminders driven by timer state instead of fixed daily spam.
- AI-assisted food photo analysis through an automatic OpenRouter model escalation chain.
- Manual meal entry when the user does not want to use the camera.
- Editable food entries, including proportional macro recalculation by total grams.
- Local nutrition tracking with calorie and macro goals.
- Append-only CSV export through Android's Storage Access Framework.
- Weight tracking with edit/delete support, history, and charting.
- Workout timers:
  - simple session timer with pause/resume
  - interval timer with periodic audio cues
  - exercise/rest toggle timer
- Home dashboard with daily fasting, nutrition, workout, weight, and calendar progress.
- Configurable bottom tabs.
- Local Room migrations with exported schemas and migration tests.

## Privacy Model

Solo Forge is designed around local ownership of fitness data.

- No backend.
- No authentication.
- No analytics SDKs.
- No crash reporting SDKs.
- No Firebase.
- No broad media, contacts, or logs permissions.
- Android cloud backup is disabled by default.
- The OpenRouter API key is stored in encrypted local preferences.
- CSV export is explicit and writes to a user-selected folder.

The app declares `INTERNET` only for food image analysis through OpenRouter. That call happens only when the user starts an analysis.

Food analysis starts with `google/gemini-3.1-flash-lite`, escalates to `openai/gpt-5.4-mini` when confidence is low or hidden/mixed ingredients are likely, and uses `google/gemini-3.1-pro-preview` only if uncertainty remains.

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
- Vico for charts

## Requirements

- Android Studio with Android SDK installed
- JDK 17
- Android emulator or physical Android device
- Min SDK 26
- Compile SDK 35
- Target SDK 35

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

Run the connected Room migration tests with an emulator or device attached:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Run lint:

```powershell
.\gradlew.bat lintDebug
```

## Project Layout

```text
app/src/main/java/com/kbul/spicycrab/
├── MainActivity.kt
├── SpicyCrabApp.kt
├── data/
│   ├── csv/
│   ├── db/
│   └── prefs/
├── di/
├── domain/
│   ├── fasting/
│   ├── nutrition/
│   ├── weight/
│   └── workout/
├── network/
├── notifications/
└── ui/
    ├── fasting/
    ├── food/
    ├── home/
    ├── nav/
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

This is an early development build. Core local functionality is in place, but release hardening, deeper UI polish, import/export flows, and broader device testing are still pending.
