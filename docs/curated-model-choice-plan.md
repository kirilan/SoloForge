# Curated model choice — implementation plan

Status: **planned, not started.** Written 2026-08-28; **substantially revised the same day**
after the third sweep in `food-analysis-model-improvement-plan.md` measured 108 models and gave
seven of them full-set runs. The revision changed the decision in four ways: the offered set is
a short list rather than a pair, each row carries **its own escalation model** instead of one
global retry target, `qwen3.8-flash` is out (its weights are not Apache-2.0), and Phase 1's
`reasoning` plumbing is no longer needed by anything we would ship.

This still reverses the recorded "users do not pick from a model list" decision, and the reason
the original decision stands against an *unbounded* list still holds: an accuracy label is only
honest if it comes from our own eval, and an offered model is really an id plus a tested
parameter set. What the sweep added is that curation is not only about label honesty — it is
about keeping actively harmful models out. Two examples, both fast and both Apache-2.0:
`mistral-small-2603` accepted an unreadable dessert plate as "fruit salad with cottage cheese"
with no uncertainty flags, and `qwen3.8-flash` with thinking on returned top-level JSON arrays
on 8 of 44 cases.

## Decision

Settings offers a short list of **configurations**, each a `(main model, escalation model)`
pair with **measured** numbers, plus an advanced free-text escape hatch that is explicitly not
measured.

| row | main model | escalation | kcal MAPE | action | accepts | typical / worst wait |
|---|---|---|---|---|---|---|
| **Fastest** (default) | `google/gemini-3.1-flash-lite` | `gemini-3.1-pro-preview` | 23.3% | 0.935 | 16/44 | **2.2s** |
| **Balanced** | `google/gemini-3.7-flash` | `gemini-3.1-pro-preview` | 21.3% | **0.968** | **23/44** | 7.0s / 12.6s |
| **Open-weight** | `qwen/qwen3-vl-32b-instruct` | *none* | 26.2% | 0.903 | 14/44 | 6.9s / 13.4s |
| **Most accurate** | `google/gemini-3.1-pro-preview` | *none — it is the ceiling* | **17.4%** | 0.935 | 15/44 | 5.4s / 17.7s |
| *Advanced* | user-supplied id | *none* | not evaluated | — | — | unknown |

All numbers are 44-case full runs at the current prompt and schema, 2026-08-28. The default is
unchanged, so doing nothing keeps today's behaviour exactly.

**Escalation is per row, not global.** The sweep killed the single-retry-target assumption:

- `gemini-3.1-pro-preview` is a *real* escalation from the two Gemini mains — better on 13
  truthed cases, worse on 4, and it nearly halves multi-component error (33.5% → 18.6%). This
  is the first evidence the retry button improves anything; it previously shipped on unit tests
  and a 5-photo screen.
- It is **not** an escalation from itself, so the "Most accurate" row hides the retry button.
  That is not a workaround: the user picked the ceiling.
- The obvious open-weight escalation, `qwen3-vl-235b-a22b`, is a **sidegrade** — 26.0% vs 26.2%
  is noise, it is worse on multi-component (39.2%) and routing, and one call took 142.6s, past
  the client timeout. So the open-weight row hides the retry button too, until an Apache-2.0
  model is measured that actually beats the 32B. Escalating it to a proprietary Gemini would
  end the row's only promise at the first tap.

**Rule that keeps this bounded:** a row appears in Settings only with a full-run eval at the
current prompt/schema, and **every offered row plus its escalation** gets re-run before any
release that touches model id, prompt, temperature, image preprocessing, or schema. That is
6 full runs today. Tonight's evidence says this is affordable — seven full runs completed in
about half an hour in parallel for pennies — but it only stays affordable if it is one command,
so commit the sweep driver as `tools/food_eval/run_matrix.py` in Phase 1.

### Labels: lead with wait and ask-rate, not MAPE

Two sweep findings force this. The screen's ground-truthed cases measure portion mass with
opposite-signed bias (the shipped model scored 39.7% and 32.3% on identical inputs minutes
apart), so a percentage error is a shakier number than it looks; and **69% of models across the
field accept nothing at all**, meaning the difference a user actually feels is how often they
get a follow-up question, not four points of calorie error.

So each row shows: **typical wait**, **how often it just answers** (accepts / cases), then kcal
error, then provenance (proprietary / open-weight, via OpenRouter) and ~$/analysis. Beware one
trap when writing the copy: `glm-5.3-flash` had the *highest* accept count and the *worst*
action match, because it over-fires `retry_image` on good photos. "Asks least" and "routes best"
are different axes; the label must not imply otherwise.

### The advanced free-text option

A user may enter any OpenRouter model id. The sweep makes this defensible rather than reckless:
**99.1% of 459 calls across 107 models were schema-clean**, and `AnalysisPolicy` already routes
a malformed response to `ask_user`, so an unknown model degrades into a clarification prompt
rather than a crash. That is a measured property, not an assumption.

Non-negotiable conditions:

- It is behind an **Advanced** disclosure, never a peer of the curated rows in the same list.
- It shows **"not evaluated"** where the curated rows show numbers. The app never states an
  accuracy figure it did not measure.
- It has **no escalation** — the retry button hides, exactly as for the other unpaired rows.
- Copy warns plainly that latency and correctness are unknown: 29% of the models we screened
  take over 20s on a food photo and the client times out at 60s.
- `modelUsed` provenance records the real id, so a user's bug report still says what answered.
- It stores the raw id, so unlike the curated rows it is **not** protected from a model being
  withdrawn. Handle a `404 No endpoints` the same as any other analysis failure.

## Gates before coding

1. ~~**License variant.**~~ **Closed 2026-08-28, negatively.** `Qwen/Qwen3.8-Flash-Next` is
   `license: other / qwen-community-1.0` and is the only published Qwen3.8 Flash weights repo,
   so no variant of that row could honestly be labelled open-weight. Replaced by
   `qwen/qwen3-vl-32b-instruct`, verified `apache-2.0` on HF, as is its 235B sibling.
2. ~~**Blurry-photo under-flagging.**~~ **Closed, and it indicted the wrong model.** Five repeats
   per model: `qwen3-vl-32b-instruct` **5/5**, `gemini-3.7-flash` **5/5**, `qwen3.8-flash`
   + reasoning off 3/5 — and **`gemini-3.1-flash-lite`, the shipped default, 0/5** (about 1 in 9
   counting August). `retry_image` is close to dormant in production today. This is a reason to
   offer rows that flag reliably, not a blocker on any candidate.
3. ~~**Cost labels from measured tokens.**~~ **Closed 2026-08-28.** `run_eval.py` now records
   `usage` per row and reports `prompt_tokens_median` / `completion_tokens_median`. A photo
   analysis costs ~1,263 prompt tokens across every model measured; completion tokens are what
   separate a fast model from a thinking one (~350 vs 2,000–4,500). Derive $/analysis from these
   plus published rates and hardcode per release — no runtime price fetch.
4. ~~**Fix `seconds_max` contamination.**~~ **Closed 2026-08-28.** `summarize()` counted any row
   with a `seconds` key, so a failed 429 inflated one config's worst case to 173.9s against a
   real 11.8s. Latency figures now exclude error rows. Result files written before the fix still
   carry the inflated summary — recompute, don't quote.
5. **Widen the bad-angle bucket.** It is one photo, and only 58 of 99 models flagged it, so it
   is borderline. Add 3–4 more before any row's `retry_image` behaviour is described in Settings.

## Phase 0 — fix the temperature drift (independent, ship first)

`OpenRouterClient`'s `Json` config leaves `encodeDefaults` at its default (**false**), and
`ChatRequest.temperature` has a default of `0.2` — so the app has **never sent `temperature`**;
providers run at their own default. The eval sends `0.2` explicitly, so every recorded number
was measured at a temperature the app doesn't use.

- Fix in the app: send temperature explicitly (e.g. make it non-defaulted in the DTO, or set
  it at the call site with a value that differs from nothing — verify in a golden test that
  the encoded body contains it). Do **not** flip `encodeDefaults = true` globally without
  checking what else starts serializing.
- Per convention this is a live-behavior change: run the full eval on Gemini once *without*
  temperature first. If the numbers don't move beyond run noise (~2 pp), say so in the plan
  doc and proceed; if they do, the fix is even more warranted.

## Phase 1 — network layer

**No `Reasoning` DTO.** The earlier draft added one to serve `qwen3.8-flash` + reasoning-off;
that row is gone, and the sweep found the field is rejected outright by most of the field
anyway (`glm-5.3-flash`, `grok-4.6`, `gemini-3.7-flash`, `qwen3.8-max` all return
`HTTP 400: Reasoning is mandatory for this endpoint`). Every model in the Decision table runs at
its provider default, so the app's current request shape is already correct for all of them.
Add the DTO the day a row needs it, together with the eval-parity rule in the README — not
before.

- `OpenRouterClient.analyzeFood` already takes `model: String` (`OpenRouterClient.kt:57`), so
  the network layer needs **no change**. The work is entirely in how the id is chosen.
- `FoodAnalysisModels` (`FoodRepository.kt:19`) becomes a list of
  `AnalysisConfig(token, mainId, escalationId: String?, provenance, measured…)` plus the
  advanced/free-text case. `escalationId == null` is what hides the retry button.
- The four constant call sites move with it: `FoodRepository.kt:41`, `:50`, `:94`,
  `FoodViewModel.kt:117` and `:120`, and the disclosure string at `AnalyzeScreen.kt:197`, which
  currently hardcodes `ON_DEMAND_RETRY` and must read the selected row's escalation instead.
- Commit the sweep driver as `tools/food_eval/run_matrix.py` so the release re-eval of every
  offered row and escalation is one command. Fix `seconds_max` contamination and capture
  `usage` here (Gates 3 and 4) — the UI numbers come from this script.

## Phase 2 — setting and wiring

- `SettingsRepo`: `stringPreferencesKey("food_analysis_model")` with stable tokens
  `"fast"` / `"balanced"` / `"open"` / `"accurate"` / `"custom"` — **not** raw model ids, so a
  future model swap doesn't invalidate stored prefs or backups. Unknown/missing → `"fast"`
  (file/prefs boundary; allowed).
- A second key `stringPreferencesKey("food_analysis_model_custom")` holds the free-text id, used
  only when the token is `"custom"`. Trim and length-cap it; treat it as opaque — do not
  validate against a model list, since the catalog changes without us.
- Add both fields to `AppSettings` **with defaults** so pre-existing backups still deserialize,
  and wire them through `applyBackup` (`BackupManager` serializes `settingsRepo.settings`
  wholesale — see `BackupManager.kt:59`). A backup carrying `"custom"` restores the id too;
  that is a model preference, not a secret.
- `FoodViewModel.analyze()` (`FoodViewModel.kt:117`) resolves the setting to a config;
  `retryWithStrongerModel()` (`:120`) uses the row's escalation and is **not offered at all**
  when the row has none. `modelUsed` provenance already records the actual id.
- The AI-off boundary is untouched: the chooser lives inside the AI section of Settings and
  disappears with it; the repository's before-every-request check already covers every model.

## Phase 3 — Settings UI and strings

- A radio list in the AI section. Each row shows name, **typical wait**, **how often it just
  answers**, then kcal error, provenance (proprietary / open-weight, via OpenRouter — same
  honesty register as the existing `analysis_retry_disclosure` at `AnalyzeScreen.kt:197`), and
  ~$/analysis. Order the list fastest-first; the default stays first so the common case is the
  top row.
- Rows whose escalation is `null` say so in one short line ("no stronger model to retry with"),
  because the retry button silently disappearing from the Analyze screen otherwise looks like a
  bug.
- **Advanced (free-text)** sits behind an expander below the list: a text field for the model
  id, the "not evaluated" line in place of numbers, and a plain warning that speed and accuracy
  are unknown. Selecting it requires a non-blank id; clearing the id falls back to `"fast"`.
- Keep measured numbers as format args sourced from `FoodAnalysisModels`, not baked into 7
  locale files — release-time re-evals then update one place.
- Every new string ships in all 7 locales in the same commit; lint's `MissingTranslation` is an
  error and CI runs `lintDebug`. Product and model names stay untranslated.

## Phase 4 — docs and release

- Rewrite the "Food analysis models" section of `CLAUDE.md` (it currently states there is no
  escalation chain and that users never pick a model — the first is still true per analysis, the
  second is not).
- Tick this plan off in `food-analysis-model-improvement-plan.md`; changelog entry for the
  release's `versionCode`.
- Privacy: no new host and no new data, so the privacy policy's claims are unaffected. **Do run
  the release network-monitor pass** — Phase 0 changes the request body, and the free-text path
  means the app can now send a model id we have never sent before. Confirm `openrouter.ai`
  remains the only outbound host and that AI-off still produces zero traffic.
- Store listing / website: only if screenshots or feature copy mention model choice.

## Verification

- Unit: request-encoding golden tests (temperature present; no `reasoning` key on any config);
  config resolution for each token including unknown-token fallback and blank custom id;
  `escalationId == null` suppresses the retry path. Routing policy untouched — no
  `AnalysisPolicy` changes anywhere in this plan.
- Manual: switch each row and analyze (check `modelUsed`); confirm the retry button appears only
  on the two Gemini rows; enter a junk model id and confirm the failure surfaces as a normal
  analysis error with the real provider message, per the raw-errors convention; AI toggle off
  hides the chooser and kills the flow; backup round-trip preserves both keys; a pre-feature
  backup imports.
- Eval: full run on every offered row **and** every escalation at the release prompt/schema
  (`run_matrix.py`); update the UI numbers if they moved.

## Out of scope, on purpose

- A model *picker* listing OpenRouter's catalog. The free-text field is an escape hatch
  behind a warning, not a browsable list — the app cannot label what it has not measured.
- Automatic latency-based switching — the sweep showed the variance is thinking tokens, not
  provider congestion; revisit only with contrary evidence.
- Any change to the escalation/retry policy or `AnalysisPolicy`.
