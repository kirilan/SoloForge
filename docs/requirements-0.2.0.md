# Solo Forge 0.2.0 — Requirements

Target: versionName `0.2.0`, versionCode `5`.
Scope: GitHub issues #5, #6, #8 + new text-based AI food entry
+ midnight-rollover bug fix (F5) + APK attached to the GitHub release (#1).
Deferred to 0.3.0: #9 (data export/import), #7 (daily journal).

**No Room schema changes in this release** — no migration work needed.

---

## F1. Text-based AI food entry (new feature)

User types a food description (e.g. "100gr watermelon", "two eggs fried in butter")
and gets the same structured macro estimate as the photo flow.

### Requirements

- **F1.1** New entry point on the Food screen alongside the camera and manual-add
  actions (e.g. a "Describe" button with keyboard/text icon). Opens a text input
  with a single free-text field and an Analyze action.
- **F1.2** Analysis reuses the existing escalation chain in
  `FoodRepository` (`google/gemini-3.1-flash-lite` → `openai/gpt-5.4-mini` →
  `google/gemini-3.1-pro-preview`), including the low-confidence `detailPrompt`
  behavior. New repository method `analyzeText(description)` sharing the
  escalation logic with the image path.
- **F1.3** `OpenRouterClient` gains a text-only request variant: same system
  prompt intent and same JSON response schema as `VisionPrompts`, but the user
  message contains only text (no image part). Prompt should instruct the model
  to assume typical preparation when unspecified and reflect uncertainty in
  `confidence`/`notes`.
- **F1.4** Result lands in the existing Analyze → review/edit → save flow
  (same `NutritionEstimate` editing UI). Saved entry has `imagePath = null`,
  `comment` = the typed description, `modelUsed`/`confidence` from the chain.
- **F1.5** Re-analyze from the edit sheet (currently image-only, gated on
  `imagePath != null`) also works for text-created entries: when `imagePath`
  is null and the entry came from AI analysis, re-analyze via the text path
  using the updated comment.
- **F1.6** Missing API key behaves exactly like the photo flow: same
  "Set your OpenRouter API key in Settings." error.
- **F1.7** Feature is fully hidden when AI features are disabled (see F2).

### Implementation seams

- `FoodUiMode`: either a new `AnalyzeText` mode or generalize
  `Analyze(imageFile: File?)` — decide during implementation; prefer whichever
  keeps `AnalyzeScreen` shared.
- `VisionPrompts` gets a sibling text prompt (same schema object reused).

### Acceptance

- "100gr watermelon" returns ~30 kcal with sensible macros, editable, saves,
  appears in list and today's dashboard totals, appends to CSV when export set.
- Airplane mode / bad key produce the same error UX as photo analysis.

---

## F2. Disable AI features toggle (issue #5)

User who never uses AI wants assurance the app cannot make a network call,
including protection from accidental taps.

### Requirements

- **F2.1** New DataStore pref in `SettingsRepo`: `aiFeaturesEnabled`
  (key `ai_features_enabled`), default `true`.
- **F2.2** Settings screen: toggle "AI food analysis" (subtitle: "When off, the
  photo and text analysis buttons are hidden and the app makes no network
  calls"), placed with the OpenRouter API key section.
- **F2.3** When off, ALL AI entry points disappear (hidden, not just disabled):
  - photo capture button on Food screen
  - text "Describe" entry point (F1) — this feature MUST respect the toggle
    from day one
  - re-analyze action in the edit sheet
- **F2.4** Manual add, presets/quick-add, and editing remain fully functional.
- **F2.5** No background/network code path may fire while off — the only
  network caller is `FoodRepository.analyze*`, so hiding all UI entry points
  is sufficient; no service/worker touches the network.
- **F2.6** Turning the toggle back on restores everything; no data loss.

### Acceptance

- With toggle off: Food screen shows only manual add + presets; edit sheet has
  no re-analyze; a network monitor shows zero outbound traffic during full app
  use.

---

## F3. Edit dates on entries (issue #8)

Users logging after the fact (especially just past midnight) need to set the
date/time on food and weight entries.

### Requirements

- **F3.1** Food edit sheet (`EditFoodSheet`): show the entry's date/time as a
  tappable field; tapping opens Material 3 `DatePickerDialog` then
  `TimePickerDialog`. Saving writes the new `timestampEpoch`
  (`lastModifiedEpoch` keeps its existing "bump on update" behavior).
- **F3.2** Manual add sheet (`ManualFoodSheet`): same date/time field,
  defaulting to now. `FoodRepository.addManual` must stop overwriting
  `timestampEpoch` with `now` when the draft carries an explicit timestamp.
- **F3.3** AI flows (photo + text) keep stamping "now" on save; date is
  corrected afterwards via edit if needed (keeps the analyze flow simple).
- **F3.4** Weight entries: add/edit gets the same date/time picker on
  `timestampEpoch`.
- **F3.5** Future dates are rejected (clamp/disable in picker) — they would
  corrupt today-totals and streaks.
- **F3.6** CSV export: an update with a changed date appends the updated row
  as today (existing lastModified tombstone semantics — no new work, verify
  only).

### Acceptance

- At 00:30, user adds a manual food entry dated yesterday 22:00; it appears
  under yesterday, not today; dashboard today-totals unaffected.
- Editing a weight entry's date reorders it correctly in the list and chart.

---

## F4. Dark splash screen (issue #6, bug)

Cold launch on a dark-themed device flashes a bright white window because
`values/themes.xml` hardcodes `android:Theme.Material.Light.NoActionBar`.

### Requirements

- **F4.1** Add `values-night/themes.xml` overriding `Theme.SpicyCrab` with a
  dark parent (`android:Theme.Material.NoActionBar`), preserving any window
  attributes from the light variant.
- **F4.2** Status/navigation bar colors during launch must not flash light in
  dark mode.

### Acceptance

- Force-stop app, device in dark mode: cold launch shows a dark window from
  first frame. Light mode unchanged.

---

## F5. Midnight rollover in "today" queries (bug, found in code review)

**Resolved during implementation:** the dashboard (`HomeViewModel`) derives
"today" from its 1-second ticker, so it already rolls over correctly at
midnight. The stale-bounds methods (`FoodRepository.observeToday`,
`WorkoutRepository.observeForDay`, and their DAO queries) had no callers —
deleted as dead code instead of fixed.

---

## Release checklist

1. Bump `versionCode = 5`, `versionName = "0.2.0"` in `app/build.gradle.kts`.
2. Fastlane changelog: `fastlane/metadata/android/en-US/changelogs/5.txt`.
3. Privacy verification: network monitor confirms `openrouter.ai` is the only
   outbound host, and only during analysis; zero traffic with AI toggle off.
4. Close #5, #6, #8 with commit references; comment on #9 and #7 that they are
   scheduled for 0.3.0.
5. Attach the signed release APK to the 0.2.0 GitHub release
   (`gh release upload`) — requested in issue #1.

## Suggested implementation order

F4 (one file) → F5 (small bug fix) → F2 (pref + toggle + hide
photo/re-analyze) → F3 → F1 (wired to the F2 toggle at birth).
