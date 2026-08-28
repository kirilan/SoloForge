# Food analysis model improvement plan

Status: **REOPENED 2026-08-28** by the third sweep at the end of this document. Phases 1–2
shipped in v0.6.0 and stand. What reopened is model choice: a census of every vision model
OpenRouter has carried in the last twelve months found several that beat the shipped default on
accuracy *and* routing, and found that `retry_image` barely fires on the shipped model. The
follow-on design work lives in `curated-model-choice-plan.md`; this document is the evidence.

Twenty-two models now have a full-set run and 108 have been screened. The shipped default still
wins on latency and nothing else — read "Third sweep" before proposing a change.

Shipped models, and they stay until new evidence says otherwise:
`DEFAULT = google/gemini-3.1-flash-lite`, `ON_DEMAND_RETRY = google/gemini-3.1-pro-preview`.

The structural half of this plan — decomposed schema, enumerated uncertainty, deterministic
routing, one call plus a user-tapped retry — is in and is the part that was actually worth
having. The open-weight default is not: the eval below measured Qwen3-VL-8B at more than twice
Gemini's calorie error on our own prompt. License alignment was never worth handing users
double the error in a calorie counter, and that is the trade the measurement revealed.

Decision, final: **Gemini stays the default.** Two open candidates were measured rather than
argued about — the 8B failed outright, the 30B came close enough to be credible and still lost
on multi-component plates, the common real case. Both cost a few cents to test and one of them
overturned a benchmark claim this plan had treated as fact.

**Superseded 2026-08-28 by the third sweep.** The open candidate of record is now
`qwen/qwen3-vl-32b-instruct` (Apache-2.0, verified on HF), displacing `qwen3.8-flash` with
reasoning disabled: the only Qwen3.8 Flash weights published carry the Qwen Community License,
so that row could never have been labelled "open-weight" honestly. Food-R1 remains worth a
glance if it lands on OpenRouter with a compatible licence — checked again 2026-08-28, still
not there.

## Strategy (as shipped)

One model per analysis, no automatic escalation chain. When the result is uncertain, the
**user** chooses the next step per case: add details, or retry once with a stronger model. The
button is the policy — no settings toggle, no silent second call.

- **Default model (photo and text): `google/gemini-3.1-flash-lite`.** One model, one prompt,
  one parsing path.
- **No automatic model escalation.** Escalation cannot recover invisible information (oil,
  fillings, portion mass) — the clarification loop is the escalation. This was the plan's best
  idea and it survived the eval untouched.
- **On-demand retry: `google/gemini-3.1-pro-preview`**, user-tapped only, same OpenRouter key.
  Recorded in `modelUsed` provenance like any other result.

The open-weight default was the plan's original point and the eval killed it; see "Eval result"
below for what replaced the assumption. What survives is worth restating plainly: the accuracy
that matters is not the model's, it's the user's ability to correct it, and that is what phases
1–2 shipped.

Parked candidates (all now need eval evidence before adoption, not just a benchmark citation):

- `qwen/qwen3-vl-30b-a3b-instruct` — **tested 2026-08-09, and it is the only credible open
  candidate.** 36% MAPE, ties Gemini on hidden fat, same price as the 8B, Apache-2.0. Blocked
  on multi-component plates, not on principle. Retest against phone photos before adopting.
- `mistralai/mistral-small-2603` — **rejected 2026-08-28.** Fast (2.4s) and Apache-2.0, and it
  accepted the genuinely blurry dessert plate with zero uncertainty reasons, calling it "fruit
  salad with cottage cheese". Confidently wrong with no uncertainty signal defeats the entire
  response contract; speed does not buy that back.
- **Food-R1** — food-specialized Qwen3-VL-8B derivative with much stronger domain evidence
  (Nutrition5k calorie MAE 27.6). Not on OpenRouter; **check availability and license at every
  release** — if it lands compatibly, it is the natural drop-in default.

## Privacy boundary (unchanged, non-negotiable)

- Only outbound host: `openrouter.ai`, only on explicit user action, with the user's key.
- Disabling AI prevents every request and clears active analysis state.
- No analytics, remote config, or production prompt/output collection.
- Photos and eval results stay local unless already part of a redistributable public dataset.

## Phase 1: structured response contract

Replace the meal-level-confidence-plus-prose response with a decomposed schema:

- `items[]`: name, estimated grams, nutrition, confidence.
- Meal totals.
- Enumerated uncertainty reasons: `image_quality`, `identity_ambiguous`, `portion_unknown`,
  `preparation_unknown`, `hidden_ingredients`.
- A recommended action: `accept`, `ask_user`, `retry_image`.

Use strict JSON Schema through OpenRouter where the provider supports it. Validate values are
finite and non-negative, item totals approximately reconcile with meal totals, and calories are
not grossly inconsistent with macros. Keep prompts direct — DiningBench found generic
chain-of-thought can worsen nutrition regression.

Skipped: the two-stage vision→text pipeline (compounded latency, cost, and failure surface on a
speculative gain).

## Phase 2: routing

Delete `needsEscalation()` and the keyword regex. The rubric collapses to:

| Condition | Action |
|---|---|
| Invalid schema or transient provider failure | Retry at the network/format boundary; not food uncertainty |
| Unusable or obstructed image | Ask for a clearer or second image |
| Anything uncertain | Show the estimate with two actions; user decides |

The uncertain-result sheet offers:

1. **"Add details"** (primary) — clarification flow, then re-analyze. Tailor the prompt to the
   actual uncertainty reason, not one generic question.
2. **"Retry with Gemini"** — one tap, one proprietary call. Labeled honestly (proprietary model,
   via OpenRouter). When the reason is `portion_unknown`/`hidden_ingredients`, hint that a
   smarter model can't see what isn't visible; when `identity_ambiguous`, this is the button
   that helps. Both always shown — emphasis, not enforcement.

Routing lives in a small deterministic policy function independent of Android and networking.

## Phase 3: local eval (right-sized)

A standalone script under `tools/food_eval/` using the app's exact request shape, image prep,
prompt, and parsing. API key from an environment variable. **~40 cases total**, sampled across:
simple foods, multi-component plates, sauces/oils, hidden ingredients, bad angles, text-only.
Ground truth from labels/reputable nutrient data with human review — never another LLM.

Purpose, in order:

1. Sanity-check the new schema/prompt against the current one on the current chain.
2. Run Qwen3-VL-8B through it once before shipping — if it confirms the public gap, record and
   proceed; if it's shockingly bad on our prompt, stop and reassess.
3. Keep as a regression check when models are deprecated or prompts change.

Track calorie/macros MAE, valid-schema rate, and whether the recommended action matches the
case's known uncertainty. Personal photos and raw responses stay out of Git.

Skipped: the frozen 160-case decision set, 8×20 cohorts, triplicate runs, bootstrap confidence
intervals, pre-registered tolerances. That is a research protocol; 40 cases answer every decision
this app actually faces, and DiningBench already exists for model ranking.

## Phase 4: Android implementation

1. Replace the three-constant chain in `FoodRepository` with: default model id, on-demand retry
   model id.
2. Extend `NutritionEstimateDto` with component-level data and enumerated uncertainty.
3. Route by recommended action; wire the two-button uncertain-result sheet.
4. Keep the AI-enabled check immediately before every OpenRouter call.
5. `modelUsed` provenance unchanged (already a string column — no Room migration).
6. New strings (buttons, hints, disclosure line) ship translated in all six locales in the same
   commit, per project convention.

No settings toggle. No second inference host. No remote config.

## Phase 5: tests and verification

Unit tests:

- Every uncertainty reason maps to the intended emphasis/action; prose can no longer control
  routing.
- Numeric/component-total validation handles malformed, negative, non-finite, inconsistent values.
- Gemini retry happens only from the explicit user action, never automatically.

Client and repository tests:

- Ktor `MockEngine` verifies text and image request shapes, strict-schema payloads, parsing,
  timeouts, cancellation, HTTP errors, malformed responses.
- Repository tests assert exact call sequences for accept, ask-user, retry-image, user-tapped
  Gemini retry, provider failure, and AI-disabled paths.

Manual: photo/photo+comment/text flows, clarification → re-analysis, Gemini retry, key
missing/invalid, AI disabled mid-analysis, and network monitoring confirming `openrouter.ai` is
the only outbound host with zero traffic when AI is off.

Run `testDebugUnitTest`, `lintDebug`, `assembleDebug`, relevant connected tests.

## Rollout

Reversible phases in order: schema/routing (on current models — zero model risk), eval script,
model swap, UI copy. A commit revert is the rollback. Update `CLAUDE.md` (model chain section)
when the swap ships. Re-run the eval and re-check licenses/pricing/availability before changing
any model id, prompt, temperature, image preprocessing, or schema. At each release, re-check
whether Food-R1 has landed on OpenRouter with a compatible license.

## Deliverables

- [x] Structured schema + validation on the current chain.
- [x] Deterministic routing + uncertain-result card (tailored hints + user-tapped stronger-model
      retry), with tests. "Add details" is the existing re-analyze button, not a third control.
- [x] `tools/food_eval/` script (`--selftest` runs the scoring offline).
- [x] 44-case local set: 14 text cases + 27 Nutrition5k dish photos (CC BY 4.0, scale-measured
      truth) + 3 own phone photos via `truth.csv`, built reproducibly by `build_n5k_cases.py`.
      The dataset images are rig-framed 640×480 overhead plates, so absolute accuracy from them
      is pessimistic; the phone photos are the only real-conditions cases.
- [x] Baseline vs Qwen3-VL-8B **and** Qwen3-VL-30B eval runs, results recorded below.
- [x] **Model swap: decided against.** Gemini stays; neither open candidate earned the default.
- [x] Strings in all locales (9 new strings × 7 locales, shipped in the same commit);
      `CLAUDE.md` model section rewritten.
- [x] Shipped in v0.6.0 to GitHub, F-Droid, and Google Play on 2026-08-09.

Not done, and deliberately: **no network monitoring pass for 0.6.0.** The release changed model
ids, prompt, and response schema — the endpoint, host, and the AI-enabled check in front of it
are untouched, so there was no new outbound surface to verify. Do run it when the network layer
itself changes.

## Eval result (2026-08-09, 41 cases)

Same case set, same prompt, same request shape, one run each.

| | gemini-3.1-flash-lite | qwen3-vl-8b-instruct | qwen3-vl-30b-a3b |
|---|---|---|---|
| valid schema rate | 1.00 | 1.00 | 1.00 |
| kcal MAPE | 22.9% | 53.0% | 35.9% |
| kcal MAE | 67.9 | 177.9 | 102.4 |
| action match rate | 0.93 | 0.64 | 0.79 |
| false `image_quality` | 0% | 34% | 7% |
| MAPE — text only | 1.1% | 4.5% | 3.6% |
| MAPE — simple photo | 13.0% | 23.9% | 21.2% |
| MAPE — multi-component | 32.8% | 74.9% | 61.8% |
| MAPE — oils/hidden fat | 26.0% | 73.3% | 24.6% |
| oils median bias | −50 kcal | −368 kcal | −1 kcal |

**The plan's reason for skipping the 30B was wrong.** It claimed the 30B improves on the 8B by
only 1.89 pp, citing DiningBench. On our prompt and our cases it improves by **17 pp**, cuts
false blur claims from 34% to 7%, and matches Gemini outright on hidden fat — the bucket that
most affects a calorie counter. Pricing is a wash ($0.13/$0.52 per M vs $0.117/$0.455), and it
is Apache-2.0 like the 8B. A benchmark delta did not survive contact with our own workload;
that is the argument for the eval existing at all.

Where it still loses: multi-component plates (61.8% vs 32.8%) — the common real case — and
action match (0.79 vs 0.93). Overall it lands at 36% vs 23%, which is almost exactly the
39%-vs-25% trade this plan originally accepted in principle before the 8B result soured it.
The open-weight default was therefore a real option, not a foregone conclusion — and it was
declined on 2026-08-09 because a calorie counter that doubles its error on an ordinary dinner
plate is not a better default, only a differently-shaped one.

**On the 8B specifically, the swap was never close.** The public gap the plan accepted was 39%
vs 25% MAPE; the measured gap on our prompt is 53% vs 23% — worse than advertised, not better.
Two specific findings beyond the headline:

- **Qwen-8B blames the photo.** It raised `image_quality` on 14 of 41 cases ("image is blurry"),
  Gemini on none, which is what wrecks the action match rate: eight cases routed to
  `retry_image` where the honest answer was `ask_user`. In the app that is the worst possible
  failure — it tells the user to retake a photo that was fine, instead of asking for the detail
  that would actually help. **Confound resolved (see phone photos below): it is the model, not
  the dataset.**
- **Qwen underestimates fatty food badly** — median bias −368 kcal on the oils bucket, against
  −50 for Gemini. Exactly the dishes where a calorie counter being wrong matters.

Not evidence of anything: the text-only bucket. Both models recite canonical reference values
for "100 g watermelon" to the digit, so that bucket tests JSON validity, not estimation.

Schema and routing (phases 1–2) are validated by this run: 82/82 responses parsed, no invalid
enums, no items/total mismatches, from two different model families. What it did **not** catch:
a trailing comma in a nested `items[]` array, which reached a user's phone. Both models tested
emit clean JSON, so `valid_schema_rate: 1.00` described those models, not the format.

### Phone photos (2026-08-09, 3 cases, own camera)

Three real photos, cropped to the plate, added via `truth.csv`: one genuinely soft-focus
dessert plate, one mildly soft fried-fish plate, one sharp spaghetti plate.

| | action match | called the sharp photos blurry |
|---|---|---|
| gemini-3.1-flash-lite | 3/3 | no |
| qwen3-vl-8b | 1/3 | **yes, both** |
| qwen3-vl-30b-a3b | 3/3 | no |

Two things this settles, on a small sample:

- **`retry_image` works end to end on a real photo.** Gemini raised `image_quality` on the
  blurry dessert plate and routed to retry. Before this the shipped path had unit tests and
  nothing else.
- **The 8B's false-blur behaviour is not a dataset artifact.** At full phone resolution it
  still called two perfectly sharp photos blurry. The confound recorded above is closed against
  it.

Three cases decide nothing on their own; the bad-angle bucket is still one photo deep.

### Full candidate sweep (2026-08-09, 44 cases, after the plan closed)

Ten models, six vendors, same cases, same prompt, one run each. Latency is measured from the
same runs; the app's client times out at 60s and the user is waiting with a plate in hand.

| model | kcal MAPE | action match | photo latency (median) | $/M in | outcome |
|---|---|---|---|---|---|
| **google/gemini-3.1-flash-lite** | 23.3% | 0.935 | **2.2s** | **0.25** | **kept** |
| google/gemini-3.6-flash | 21.9% | 0.968 | 6.4s | 1.50 | 6× price, 3× latency, no real accuracy gain |
| qwen/qwen3.7-flash | 23.0% | 0.806 | 15.4s | 0.03 | accuracy tie, but 7× slower and over-asks |
| minimax/minimax-m3 | 27.2% | 0.839 | 8.8s | 0.30 | worse and slower |
| openai/gpt-5.6-luna | 38.5% | 0.774 | — | 0.10 | multi-component 74.6%; over-asks |
| qwen/qwen3-vl-30b-a3b | 35.9% | 0.786 | — | 0.13 | multi-component 61.8% |
| qwen/qwen3-vl-8b | 53.0% | 0.643 | — | 0.12 | calls sharp photos blurry |
| stepfun/step-3.7-flash | — | — | 27.3s (max 48.2s) | 0.20 | latency |
| moonshotai/kimi-k3 | — | — | 29.2s | 3.00 | latency + 12× price |
| xiaomi/mimo-v2.5 | — | — | ~55s | 0.14 | latency |

**Nothing displaced the shipped model.** The only more accurate candidate, `gemini-3.6-flash`,
wins by 1.4 pp of MAPE — inside run-to-run noise, since two flash-lite runs on the same day
measured 22.9% and 23.3% — while costing 6× per token and tripling the wait.

`qwen3.7-flash` is the one that came closest: an outright accuracy tie (23.0% vs 23.3%,
matching bucket by bucket, slightly better on hidden fat) at a twelfth of the token price. It
loses on the two things a benchmark table hides. It takes 15.4s per photo against 2.2s, and it
over-asks — every one of its routing misses is `ask_user` where `accept` was right, an accept
rate of 25% against Gemini's 36%, so a third more meals get an unnecessary clarification
prompt. The price win is also smaller than it looks: at ~1.5k in / 300 out per analysis, a
thousand analyses cost about $0.08 instead of $0.82. Nobody trades 13 seconds a meal for 74
cents a year.

Two method notes worth keeping:

- **Latency is a first-class metric now.** Three of ten candidates answer a food photo in
  27–55s. The eval previously scored content only, so any of them could have posted a
  respectable MAPE while being unusable in front of a waiting user.
- **Screen new candidates on 5 photos before a full run** (`--tag photo --limit 5`). That caught
  all three slow models in five calls each, for cents, instead of half an hour apiece. Accuracy
  from a screen that size is noise — it is a latency and schema gate, nothing more.

Everything above is single-run at temperature 0.2. Treat gaps under ~2 pp as no difference;
latency and price differences are real.

### Second sweep (2026-08-28, the August reasoning-VLM generation)

The open releases of Aug 10–26 screened against the same cases and prompt. All screens are
5 photos (latency/schema gate, accuracy at that size is noise); `qwen3.8-flash` with reasoning
off survived and got the full 44 cases. "reasoning off/low" rows send OpenRouter's `reasoning`
field via the eval's `--reasoning` flag — **not the app's request shape**; adopting one of
those rows means shipping the same field in `OpenRouterDtos.kt`.

| model | config | kcal MAPE | action match | photo latency med/max | outcome |
|---|---|---|---|---|---|
| z-ai/glm-5.3-flash | default thinking | 22.2% (n=5) | 0.75 | 25.4s / 49.7s | latency |
| z-ai/glm-5.3-flash | effort low | 48.1% (n=5) | 0.50 | 13.6s / 14.7s | slow **and** inaccurate; rejects `enabled: false` outright ("Reasoning is mandatory") |
| qwen/qwen3.8-27b | default thinking | 17.5% (n=5) | 0.50 | 14.9s / 40.4s | best accuracy ever measured here, unusable latency |
| qwen/qwen3.8-27b | reasoning off | 37.9% (n=5) | 0.75 | 6.0s / 13.3s | accuracy collapses |
| qwen/qwen3.8-flash | default thinking | 31.3% (n=5) | 1.00 | 15.3s / 26.3s | same failure as 3.7-flash |
| **qwen/qwen3.8-flash** | **reasoning off** | **26.9% (44 cases)** | **0.903** | **6.4s / 11.8s** | **credible second; not a default** |
| deepseek/deepseek-v4-flash-vision-exp | — | — | — | — | blocked: no OpenRouter endpoint satisfies this account's data policy (and `-exp` ids can vanish) |

Not screened: Meta's Muse-Glimmer-30B (not on OpenRouter, and its vision tuning is
OCR/screenshots); qwen3.8-max and the 2.4T (same price class that cut kimi-k3). Food-R1
checked again 2026-08-28: still not on OpenRouter.

**The generation's pattern, consistent across three vendors: the accuracy lives in the
thinking tokens.** Every candidate is accurate-but-slow with thinking on and fast-but-wrong
with it suppressed — except `qwen3.8-flash`, which keeps most of its accuracy at 6.4s.
Gemini flash-lite's moat is not accuracy; it is 23% MAPE at 2.2s *without* reasoning.

Full-run detail on `qwen3.8-flash` + reasoning off, against the shipped model's 2026-08-09
numbers: text 2.3% vs 1.1%, simple 14.9% vs 13.0%, multi-component 40.0% vs 32.8%, oils
30.6% vs 26.0% with an identical −50 kcal median bias. Every successful photo answered in
under 12s. Two routing regressions: it missed `image_quality` on the genuinely blurry
dessert plate (thinking off appears to under-flag image quality — the inverse of 3.7-flash's
over-asking), and it over-asked on two accepts. At $0.15/M in, the 3.7-flash price argument
is gone.

**Candidate of record is now `qwen3.8-flash` with `reasoning: {"enabled": false}`,**
displacing `qwen3-vl-30b-a3b`. It does not displace Gemini as default — slightly worse on
every bucket and 3× the median wait — but it is the first open-weight configuration close
enough to be *offered*, if a curated model choice ever ships. Note the license: the 3.8
generation's Flash weights carry the Qwen Community License, not Apache-2.0 — check which
variant OpenRouter serves before calling it license-aligned.

### Third sweep (2026-08-28): every vision model of the last twelve months

A census rather than a shortlist. OpenRouter carried 388 models, 223 vision-capable, **169
created in the last twelve months**; 61 were excluded as not-candidates (`:batch` queue
variants, `~…-latest` alias pointers, image-*generation* models, code/safety/search
specialists, `:free` duplicates of a paid id, routers), leaving **108 screened on the same 5
photos** — 540 calls, about $5. Everything below is single-run at temperature 0.2 in the app's
request shape unless stated.

| screen verdict | n | criterion |
|---|---|---|
| passed | 52 | valid schema 5/5, slowest call ≤20s |
| latency-rejected | 35 | slowest call >20s (incl. `meta/muse-glimmer-30b`, which blew a 25-min timeout) |
| schema failure | 2 | `rekaai/reka-edge` (0.40), `bytedance-seed/seed-1.6-flash` (0.80) |
| flaky | 4 | 1–3 of 5 calls failed |
| unusable | 15 | account gates and provider errors, below |

**Latency is bimodal and the field got slower.** Median screened model answers a food photo in
8.6s; p75 is 24.1s; **29% take over 20s**. Only 12 of 92 timed models answer under 3s and three
of those twelve are Gemini flash-lite variants. The shipped default's moat is not accuracy — it
is 23% MAPE at ~2s *without* reasoning, which almost nothing else in the catalog does.

#### Full-set runs (44 cases) — the 2026-08-28 cohort

| config | schema | kcal MAPE | action | accepts | med / max | text | simple | multi | oils |
|---|---|---|---|---|---|---|---|---|---|
| `gemini-3.1-pro-preview` | 1.000 | **17.4%** | 0.935 | 15/44 | 5.4s / 17.7s | 1.0% | 13.9% | **18.6%** | **21.8%** |
| `z-ai/glm-5.3-flash` | 1.000 | 19.5% | 0.806 | 18/44 | 18.0s / 34.0s | 0.4% | **9.5%** | 30.2% | 21.9% |
| `qwen/qwen3.8-flash` thinking | 0.727 | 19.7% | 0.852 | 13/39 | 14.8s / 22.6s | 0.2% | 12.7% | 25.2% | 27.8% |
| **`google/gemini-3.7-flash`** | 1.000 | 21.3% | **0.968** | **23/44** | 7.0s / 12.6s | 0.4% | 12.6% | 26.1% | 29.9% |
| `google/gemini-3.1-flash-lite` *(shipped)* | 1.000 | 23.3% | 0.935 | 16/44 | **2.2s** | 0.9% | 12.3% | 33.5% | 28.3% |
| `qwen/qwen3-vl-235b-a22b-instruct` | 1.000 | 26.0% | 0.871 | 14/44 | 7.8s / **142.6s** | 6.0% | 15.4% | 39.2% | 25.9% |
| **`qwen/qwen3-vl-32b-instruct`** | 0.977 | 26.2% | 0.903 | 14/44 | 6.9s / 13.4s | 7.4% | 20.4% | 32.9% | 27.2% |
| `qwen/qwen3.8-flash` + reasoning off | 0.977 | 26.9% | 0.903 | 15/43 | 6.4s / 11.8s | 2.3% | 14.9% | 40.0% | 30.6% |

Four things this cohort settles:

- **The escalation model had never been measured, and it works.** First full run of
  `gemini-3.1-pro-preview`: 17.4% against the default's 23.3%, better on 13 truthed cases and
  worse on 4, and it nearly halves multi-component error (33.5% → 18.6%) while improving hidden
  fat. The retry button shipped on unit tests and a 5-photo screen; it now has evidence, and it
  improves exactly the buckets where a calorie counter being wrong matters.
- **`gemini-3.7-flash` has the best routing ever measured here** — 0.968 action match and
  **23/44 accepts**, against the default's 16/44 — while also being more accurate (21.3%) and
  reliable (zero failures, 0 calls over 20s). It costs 7.0s against 2.2s and $0.75/M against
  $0.25/M.
- **`qwen3-vl-32b-instruct` is the open candidate of record.** 26.2% ties `qwen3.8-flash`
  + reasoning-off (26.9%) inside noise but wins everything around the number: Apache-2.0
  (verified), a 13.4s worst case, no `reasoning` field needed, and it beats both the shipped
  default and the previous open pick on **multi-component** (32.9%) — the bucket the whole
  open-weight question died on in the August 9 sweep.
- **Bigger is not escalation.** `qwen3-vl-235b-a22b` was tested as an open escalation target for
  the 32B and is a sidegrade: 26.0% vs 26.2% is noise, it is *worse* on multi-component
  (39.2%), worse on routing, and one call took **142.6s** — a real response, not a retry, past
  the client's 60s timeout. There is currently no open-weight escalation target.

#### `retry_image` barely fires on the shipped model

The plan's bad-angle bucket is one photo, so it was run five times per model (the gate
`curated-model-choice-plan.md` asks for):

| model | raised `image_quality` | routed `retry_image` |
|---|---|---|
| `qwen/qwen3-vl-32b-instruct` | **5/5** | **5/5** |
| `google/gemini-3.7-flash` | **5/5** | **5/5** |
| `qwen/qwen3.8-max` | 5/5 | 5/5 |
| `qwen/qwen3.8-flash` + reasoning off | 3/5 | 3/5 |
| `z-ai/glm-5.3-flash` | 3/5 | 3/5 |
| `google/gemini-3.5-flash-lite` | 2/5 | 2/5 |
| `x-ai/grok-4.6` | 1/5 | 1/5 |
| **`google/gemini-3.1-flash-lite`** *(shipped)* | **0/5** | **0/5** |

Counting the August runs, the shipped default flags that photo about **1 time in 9**.

**This corrects the August 9 record, which over-read a single sample.** That run concluded
"`retry_image` works end to end on a real photo" from one hit; the honest statement is that the
path is close to dormant in production. It is not a regression — it was never measured properly.
Note also that only **58 of 99** models flagged the photo at all, so it is genuinely borderline;
the real defect is that one borderline image is the entire bad-angle bucket. **Add 3–4 more
bad-angle photos before anyone trusts a `retry_image` number, the shipped model's included.**

#### What 459 calls across 107 models say about the design

- **The response contract is durable. 99.1% of answered calls were schema-clean** — 107 models,
  ~20 vendors, 300× price spread, none of which have seen this prompt. Phase 1 was validated on
  two model families in August; it now holds across essentially the whole field, so a model swap
  carries near-zero parsing risk and format is not what constrains model choice.
- **Almost no model will say `accept`.** 59 of 86 models (**69%**) accepted *nothing* across 5
  photos; the median model accepts 0 of 5; nobody exceeded 2. Reasons cited across ~460 photo
  analyses: `portion_unknown` **80%**, `hidden_ingredients` 63%, `preparation_unknown` 50%,
  `image_quality` 20%, `identity_ambiguous` 17%, and only **7%** cited nothing at all. Since
  `AnalysisPolicy` routes *any* reason to `ask_user`, the industry's honest answer lands on the
  clarification sheet for ~93% of photos. **The default's real moat is willingness to commit**,
  and that is now a first-class selection metric — a "better" model can easily ship an app that
  asks a follow-up question about every meal.
- **107 models, one dominant failure mode, and it is `portion_unknown`.** That is the plan's
  central thesis confirmed at scale: escalation cannot recover what the photo does not show, and
  the clarification loop is the escalation.

#### The 5-photo screen measures portion mass, not nutrition knowledge

Worth knowing before anyone ranks models on it. Both ground-truthed screen cases resolve to a
single skill, and they are biased in opposite directions:

- `n5k-dish_1558629444` is **a plate of almonds** — 77 of 88 models identified it correctly and
  estimated a median 30 g → 174 kcal. Almonds are 579 kcal/100 g, so their kcal-per-gram is
  exact; the scale says 225.5 kcal, i.e. 39 g. The entire error is 30 g vs 39 g of nuts.
  **84/88 models undershot.**
- `n5k-dish_1563465847` is scrambled eggs with potatoes; models estimate 250 g → ~400 kcal
  against a 267.8 kcal truth. **86/89 overshot**, median +132 kcal.

Consequence: the shipped model scored **39.7% and 32.3% MAPE on identical inputs eight minutes
apart**. Treat the screen as a latency, schema and routing gate only — which is what the README
already says, though now the reason is known rather than assumed. Swapping in cases where mass
is knowable (a labelled package, a weighed portion) would make the number mean something.

#### Not measured, and why

- **`x-ai/grok-4.6` — no valid accuracy number.** It hit `HTTP 402` on 30 of 44 cases; the 14
  that completed were the text-only ones, so its apparent 0.3% MAPE is **11 canonical reference
  lookups and must not be quoted**. Separately disqualified anyway: **1/5** on the bad-angle
  gate, 4 of 14 completed calls over 20s, slowest 47.7s. Re-runnable with `--max-tokens` below.

- **The `HTTP 402` was a token reservation, not an empty account** — the first diagnosis here
  was wrong and is worth correcting, because it nearly wrote off two models for the wrong
  reason. The full message reads *"You requested up to 65536 tokens, but can only afford
  63782"*: the eval never sends `max_tokens`, so OpenRouter reserves the model's full default
  completion against the **key's monthly limit**. The shortfall was 1,754 tokens. Capping the
  completion clears it, and `run_eval.py --max-tokens N` now does that. **It is a flagged run
  like `--reasoning`** — filename-tagged, recorded in the result — but unlike `--reasoning` it
  does not change model behaviour *provided nothing truncated*, which is why `finish_reason` is
  now captured and `summary["truncated"]` names any case that came back `length` instead of
  `stop`. Never trust a capped run without checking that key is absent.

- **`qwen/qwen3.8-max` — measured this way, and disqualified on latency.** 5 photos at
  `--max-tokens 16000`: zero failures, schema 1.00, **nothing truncated** (peak 4,487 completion
  tokens against 16,000 allowed, so the cap never bit). Latency **median 72.0s, max 105.6s**,
  with three of five calls past the client's 60s timeout — in the app most analyses would simply
  fail. The newly captured `usage` explains it: it spends **2,000–4,500 completion tokens** to
  produce a ~350-token JSON answer, and it rejects `reasoning: {"enabled": false}`, so no
  configuration of it fits. No full run was spent on it; a MAPE for a model that cannot ship
  would not inform anything.
- **`deepseek/deepseek-v4-flash-vision-exp` — blocked, by choice.** Its only provider is
  DeepSeek's own first-party endpoint (healthy, 100% uptime), so the account's data policy has
  nothing to route around. Evaluating it means permitting providers that may train on inputs,
  which on this project is a real cost and not obviously worth three cents of curiosity. A
  `cases-public.json` (41 cases: Nutrition5k CC BY 4.0 + text, personal photos excluded) exists
  for anyone who decides to, so the phone photos never need to be part of that trade.
- **Reasoning cannot be disabled on most of the field.** `glm-5.3-flash`, `grok-4.6`,
  `gemini-3.7-flash` and `qwen3.8-max` all reject `reasoning: {"enabled": false}` with
  `HTTP 400: Reasoning is mandatory for this endpoint`. The lever that rescued `qwen3.8-flash`
  (15.3s → 6.4s) works on almost nothing else — which is why the curated-choice plan no longer
  needs a `Reasoning` DTO.

#### Eval harness changes made by this sweep

1. **`seconds_max` no longer counts failed calls.** `qwen3.8-flash` + reasoning-off reported a
   173.9s max from a failed 429 row carrying both `error` and `seconds`; its real max is 11.8s.
   `summarize()` now excludes error rows from every latency figure. Historic result files
   predating this still carry the inflated summary — recompute rather than quote them.
2. **`usage` and `finish_reason` are captured per row**, and `prompt_tokens_median` /
   `completion_tokens_median` are in every summary. This closes the cost-label gate in
   `curated-model-choice-plan.md`: ~1,263 prompt tokens per analysis is consistent across
   models, and completion tokens are what separate a thinking model from a fast one.
3. **`--max-tokens N`** for getting under a key's credit reservation, as above.

Still open: the bad-angle bucket is one borderline photo. Add 3–4 more before any
`retry_image` number is quoted, the shipped model's included.

## Sources

- [DiningBench paper and model results](https://arxiv.org/html/2604.10425)
- [Food-R1 paper and nutrition results](https://arxiv.org/html/2606.04986)
- [Qwen3-VL-8B-Instruct weights and Apache-2.0 model card](https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct)
- [Qwen3-VL-30B-A3B-Instruct weights and Apache-2.0 model card](https://huggingface.co/Qwen/Qwen3-VL-30B-A3B-Instruct)
- [Mistral Small 4 model selection and license](https://docs.mistral.ai/models/model-selection-guide?models=mistral-small-4-0-26-03)
- [WeirdBench text-only nutrition benchmark](https://weirdbench.com/benchmarks/nutrition-prediction)
- [OpenRouter Qwen catalog](https://openrouter.ai/qwen)
- [Study showing improvement from explicit food weight input](https://pubmed.ncbi.nlm.nih.gov/42502812/)
