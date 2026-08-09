# i18n + Weblate plan (phases 1–2 shipped; phase 3 parked)

Goal: localize Solo Forge into de, es, pt-rBR, fr, ru, tr, with community translations
via Hosted Weblate afterward. Zero runtime impact — translations compile into the APK;
the no-network guarantee is untouched. Origin: GitHub issue #12 (German requested).

**Status as of 2026-07-22:** shipped in v0.3.1 (2026-07-17), commit `e3fa64e`. Weblate was
never set up and is now parked — see phase 3. Phase 4 was reworked into a different process
than originally planned.

## Phase 1 — String extraction (the real work) — DONE

- `res/values/strings.xml` currently has 1 entry; every UI string is hardcoded in Compose.
- Extract all user-visible strings:
  - Compose UI (`ui/**`): `stringResource(R.string.…)`; use `pluralStringResource` where counts appear.
  - Services/workers/notifications (`notifications/**`, repos that build notification text): `context.getString(…)`.
  - Keep OpenRouter prompt strings (`VisionPrompts.kt`) in English — they're model input, not UI.
- Naming: `screen_element` style (`fasting_start_button`), no abbreviations.
- One big mechanical PR, no behavior change. Verify with a full click-through afterward.
- Add `locales_config.xml` + `android:localeConfig` so Android 13+ per-app language
  picker works; list shipped locales in `androidResources.localeFilters` in
  `app/build.gradle.kts` to strip everything else.

Done as described. Verified with a German click-through on the emulator.

## Phase 2 — Initial translations — DONE, except human review

- Machine-translate `values-de`, `values-es`, `values-pt-rBR`, `values-fr`, `values-ru`,
  `values-tr`, then human-review at least German (issue #12 author may help).
- Translate fastlane store metadata too: `fastlane/metadata/android/<locale>/`
  (`short_description.txt`, `full_description.txt`; changelogs can stay en-only and fall back).

Both done. **German human review is deferred** — it was to be sourced through Weblate, which
no longer exists as a route. Revisit if a German speaker turns up in the issues.

## Phase 3 — Weblate hookup — PARKED (2026-07-22)

Never started. Decision: hold until there is actual demand for languages beyond the six
already shipped, rather than pay the setup and maintenance cost speculatively. Everything
below is the plan if that demand appears.

- hosted.weblate.org, free "Libre" plan (requires FOSS license — GPL-3.0 qualifies).
- Two components in one project:
  1. Android strings: `app/src/main/res/values*/strings.xml`
  2. Fastlane metadata (Weblate has a dedicated format for it)
- Integration: GitHub webhook (Weblate pulls new source strings) + Weblate pushes
  translations back as PRs. Enable the "squash commits" addon.
- Add translation badge + "help translate" link to README and the website.

Parked along with it: the German human review, and the README/website "help translate" badge.

## Phase 4 — Process changes — DONE, but not as planned

Originally: a release-time **string freeze**, with post-freeze strings shipping English-fallback.
That was replaced on 2026-07-22, because with no translation platform there is nothing to
freeze *for*, and the fallback allowance quietly produced half-translated screens (the seven
Health Connect strings in v0.4.0 shipped English-only in all six locales).

Current process instead:

- Every new user-visible string is translated into all six locales **in the same commit that
  adds it**. Machine-produced, eyeballed. Product names ("Health Connect") stay untranslated.
- Enforcement is lint, not discipline: `MissingTranslation` stays at **error** severity and CI
  runs `lintDebug`, so a half-translated string set fails the build. When it fires, add the
  translations — do not downgrade the rule to get a build through.
- CLAUDE.md: conventions section carries this rule; the release checklist's string-freeze step
  was removed, since `main` is always release-ready on translations.
- Issue #12 was commented on when German shipped; it remains open for its Health Connect part.

## Acceptance — met

- App fully usable in German with system/per-app locale set; no mixed-language screens
  on core flows.
- `apkanalyzer` / network monitor confirms no new outbound hosts.
- Importing a backup made under another locale works (backup JSON must stay
  locale-independent — no formatted strings persisted).

## Explicitly out of scope

- RTL languages (ar/he) — revisit when requested; needs layout audit.
- In-app language picker UI — Android 13+ system picker covers it; older devices
  follow system locale.
- Translating GitHub release notes.
