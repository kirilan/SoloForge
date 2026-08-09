# Play release-dashboard advisories + AGP 9 upgrade — plan for 0.5.0

Status: **planned, not started.** Written 2026-07-25, against `v0.4.1` (versionCode 9).

Four "recommended actions" have been attached to every production release since 0.4.0.
None of them gate publishing — they are advisories, not policy blockers. This plan clears
all four in one release, bundled with the AGP 9 upgrade (decision: bundle rather than
split, taken 2026-07-25).

---

## Why bundled

AGP 9's `android.r8.optimizedResourceShrinking` defaults to `true`, so **the R8 advisory is
fixed by the upgrade itself** — there is no separate code change for it. The other three are
small and independent. Doing them together means one release, one round of manual device
testing, one Play submission.

The cost is that the release carries real toolchain risk (Kotlin 2.0 → 2.2, built-in Kotlin,
new DSL) alongside two trivial fixes. Mitigation: land the upgrade **first and alone** on the
branch, verify a clean release build, and only then stack the small fixes on top. If the
upgrade goes badly it can be dropped without losing the rest.

---

## Current state (audited 2026-07-25)

| Component | Current | AGP 9.0 needs | Action |
|---|---|---|---|
| AGP | 8.13.2 | — | → 9.0.1+ |
| Gradle wrapper | **9.0.0** | **≥ 9.1.0** | → 9.1.0+ |
| JDK (Studio jbr) | 21.0.10 | ≥ 17 | none |
| Kotlin (KGP) | **2.0.21** | **≥ 2.2.10** | → 2.2.10+ |
| KSP | 2.0.21-1.0.28 | must match KGP | → matching 2.2.x |
| compileSdk / targetSdk | 36 / 36 | max 36.1 | none |
| Build tools | 37.0.0 installed | ≥ 36.0.0 | none |
| `proguard-rules.pro` | exists | must exist | none |
| Custom variant API use | none | — | none |
| `applicationVariants` / `variantFilter` | not used | — | none |

Two things already true that reduce risk: no custom build logic touching `BaseExtension`,
and no variant-API usage. The `android.newDsl` default flip should therefore be a no-op here.

---

## The four advisories — exact Play text and real cause

### 1. "Edge-to-edge may not display for all users" (User experience)

> In Android 15, apps targeting SDK 35 will display edge-to-edge by default. Apps targeting
> SDK 35 should handle insets to make sure their app displays correctly on Android 15 and
> later. […] Alternatively, call `enableEdgeToEdge()` for Kotlin […] for backward compatibility.

**Partly a false positive.** `enableEdgeToEdge()` is already called at `MainActivity.kt:22`;
Play's static analysis cannot see it.

**Real gap:** no inset handling anywhere in the codebase — zero occurrences of `WindowInsets`,
`systemBars`, `safeDrawing`, or `contentWindowInsets`. Material3 `Scaffold` + `NavigationBar`
apply sensible defaults, which is why nothing is visibly broken today. Exposure is any screen
that draws outside the Scaffold's padding.

**Approach: verify before changing.** Do not sprinkle inset modifiers speculatively.

### 2. "Your app uses deprecated APIs or parameters for edge-to-edge" (User experience)

Play names exactly three:
- `android.view.Window.setStatusBarColor`
- `android.view.Window.setNavigationBarColor`
- `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`

**Two sources:**
1. `androidx.activity 1.9.3` — `enableEdgeToEdge()` calls those deprecated setters internally.
   Removed in 1.10.0+ when targeting SDK 35+.
2. `res/values/themes.xml:3` and `res/values-night/themes.xml:3` both set
   `android:statusBarColor`, which is a no-op under edge-to-edge anyway.

### 3. "Improve your app's performance with bitmap downsampling" (Technical quality)

> Your app is using `BitmapFactory` without downsampling in the following places: `.s.C`
> — Issue type: missing `BitmapFactory.Options` parameter. Decoding bitmaps at full resolution
> may lead to excessive memory usage […] use `BitmapFactory.Options.inSampleSize`.

`.s.C` is the R8-obfuscated `ImageUtils`. At `ImageUtils.kt:28`,
`BitmapFactory.decodeFile(file.absolutePath)` decodes the camera photo at **full resolution**
before `resize()` scales it to 1024 px. A 50 MP capture is roughly 200 MB of ARGB_8888 held
at once.

**This is the only advisory that is a genuine bug**, and it sits on the food-photo path —
the most-used feature. It is an OOM waiting to happen on mid-range devices.

### 4. "Improve your app's memory and performance with R8 optimisation" (Technical quality)

> Your R8 configuration could be causing higher memory usage and lower performance. […]
> Optimised resource shrinking isn't enabled — Upgrade your Android Gradle plug-in to
> version 9.0 or higher.

**Not what the title suggests.** R8, `isMinifyEnabled`, and `isShrinkResources` are already on.
The only missing piece is `android.r8.optimizedResourceShrinking`, which does not exist before
AGP 9 and defaults to `true` in it. **No code change — the upgrade is the fix.**

---

## Work plan

Branch: `agp9-and-advisories` off `main`. Commit each phase separately so any one can be
reverted alone.

### Phase 1 — AGP 9 upgrade (do first, alone, verify before continuing)

1. Gradle wrapper 9.0.0 → 9.1.0+ (`gradlew wrapper --gradle-version 9.1.0`; commit both the
   properties file and the wrapper jar/scripts).
2. Kotlin 2.0.21 → 2.2.10+ in `libs.versions.toml`. The `kotlin.compose` plugin follows the
   same version.
3. KSP → the release matching the chosen Kotlin version (`<kotlin>-<ksp>` scheme).
4. AGP 8.13.2 → 9.0.1+.
5. **Built-in Kotlin migration** (enabled by default in AGP 9):
   - Remove `alias(libs.plugins.kotlin.android)` from `app/build.gradle.kts` and the
     `apply false` line in the root `build.gradle.kts`.
   - `kotlinOptions { jvmTarget = "17" }` is **removed** from the `android` block in AGP 9 —
     replace with the `compilerOptions` equivalent.
   - Escape hatch if it fights back: `android.builtInKotlin=false` in `gradle.properties`.
     This works in 9.0 but is gone in AGP 10, so treat it as temporary, and write a
     `ponytail:` comment naming it as deferred work if used.
6. **Dependency compatibility** — resolve at execution time against the plugins' own
   compatibility matrices; do not guess versions:
   - **Hilt 2.52** predates Kotlin 2.2 and is the most likely to need a bump.
   - **Room 2.6.1** likewise (also used by `room-testing:2.6.1`, pinned separately in
     `app/build.gradle.kts` — keep both in lockstep).
   - **Compose BOM 2024.10.01** is old; bump alongside Kotlin.
   - `activityCompose` is bumped anyway in Phase 2.
7. Default-flip review — all become `true` in AGP 9:
   - `android.newDsl` — expected no-op (no custom build logic here).
   - `android.proguard.failOnMissingFiles` — `app/proguard-rules.pro` exists. Fine.
   - `android.sdk.defaultTargetSdkToCompileSdkIfUnset` — `targetSdk` is explicit. Fine.
   - `android.enableAppCompileTimeRClass` — non-final R class. No reflection on R here. Fine.
   - `android.r8.optimizedResourceShrinking` — **this is the advisory-4 fix.**
   - `android.onlyEnableUnitTestForTheTestedBuildType` — check the test tasks still run.

**Gate before Phase 2:**
- `assembleDebug`, `lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`
  (the Room migration tests matter most — KSP and Room both moved).
- `bundleRelease` succeeds **and** is signed with the same upload key.
- Install the release-variant build and exercise the app — R8 with more aggressive resource
  shrinking is exactly where missing keep rules surface, and only the minified build shows it.
  Pay attention to: Hilt injection, Room, kotlinx.serialization (backup JSON round-trip),
  and Ktor/OpenRouter.

### Phase 2 — deprecated edge-to-edge APIs (advisory 2)

1. `activityCompose` 1.9.3 → 1.10.1+ in `libs.versions.toml`.
2. Delete the `android:statusBarColor` line from **both** `res/values/themes.xml` and
   `res/values-night/themes.xml`. Leave `windowLightStatusBar`.

Small, but it only fully clears once Play re-analyses the next bundle.

### Phase 3 — bitmap downsampling (advisory 3)

Rewrite the decode in `ImageUtils.rotateIfNeeded` as a two-pass decode:

1. Pass 1: `BitmapFactory.Options { inJustDecodeBounds = true }` to read dimensions without
   allocating.
2. Compute `inSampleSize` as the largest power of two keeping the long edge ≥ `MAX_LONG_EDGE`
   (1024).
3. Pass 2: decode with that `inSampleSize`, then the existing `resize()` handles the exact
   final scale.

Peak memory drops 16–64× on modern camera sensors. Keep `MAX_LONG_EDGE` and `JPEG_QUALITY`
as they are — the wire format to OpenRouter should not change.

**Leaves a check behind** (non-trivial logic): one unit test asserting `inSampleSize` selection
for representative dimensions (e.g. 8000×6000 → 1024 target), so the power-of-two logic can't
silently regress. Pure function, no Android framework needed — plain `testDebugUnitTest`.

### Phase 4 — edge-to-edge insets (advisory 1) — verification-led

1. Install on the Pixel 8 Pro (adb over wireless, already paired) with **gesture navigation**
   and again with **3-button navigation** — they produce different inset sets.
2. Walk every screen: Home, Fast, Food (list → **capture** → analyze), Weight, Workout,
   Settings, plus the first-run onboarding.
3. Note anything drawing under the status bar or nav bar, or any control that cannot be
   reached because the nav bar sits on it. **Prime suspect: the CameraX capture screen**,
   which is full-bleed by nature.
4. Fix only what is actually broken — `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`
   or a `contentWindowInsets` on the relevant Scaffold. If nothing is broken, change nothing
   and let the advisory stand; it is a static-analysis guess, not an observed defect.

Also re-check the two `themes.xml` files after Phase 2 — removing `statusBarColor` changes
how the bars render, so this pass must happen **after** Phase 2, not before.

---

## Release

Follow the standard process in `CLAUDE.md`:

1. Version bump to **0.5.0** (versionCode 10) + `fastlane/metadata/android/en-US/changelogs/10.txt`.
   User-visible content is thin — the honest changelog is about performance/memory on photo
   analysis, not the toolchain.
2. No new user-facing strings expected. If any appear, all six locales in the same commit
   (lint `MissingTranslation` is an error and CI runs `lintDebug`).
3. Commit + tag `v0.5.0`, push. **Tag push = the F-Droid release.**
   - F-Droid builds from source with its own toolchain — an AGP/Gradle jump is exactly the
     kind of change that can break their builder. Watch the F-Droid build result rather than
     assuming the tag published cleanly.
4. GitHub release with the debug-signed APK, named `SoloForge-0.5.0-debug.apk`.
   Verify the signing cert still matches `a9a1cd92…` before publishing, or existing GitHub
   users lose in-place updates.
5. Play: `bundleRelease`, upload by hand (Chrome MCP cannot drive the file picker), full
   rollout. **Do not upload while another Play review is pending.**
6. Confirm on the next release dashboard that all four advisories are gone. Advisories are
   evaluated per-bundle, so they only clear after Play analyses versionCode 10.

## Rollback

Each phase is its own commit, so any can be reverted independently. The AGP upgrade is the
only one with real blast radius; if it destabilises the build, drop Phase 1 and ship
Phases 2–4 as 0.4.2 — they are independent of the toolchain and clear three of the four
advisories on their own.

## Sources

- [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [Upgrading to Gradle 9](https://docs.gradle.org/current/userguide/upgrading_major_version_9.html)
- [JetBrains: update your Kotlin projects for AGP 9](https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/)
