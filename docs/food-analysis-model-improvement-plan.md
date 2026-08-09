# Food analysis model improvement plan

Status: **phases 1–2 shipped 2026-08-09. The model swap is dropped, not deferred.**

Shipped models, and they stay until new evidence says otherwise:
`DEFAULT = google/gemini-3.1-flash-lite`, `ON_DEMAND_RETRY = google/gemini-3.1-pro-preview`.

The structural half of this plan — decomposed schema, enumerated uncertainty, deterministic
routing, one call plus a user-tapped retry — is in and is the part that was actually worth
having. The open-weight default is not: the eval below measured Qwen3-VL-8B at more than twice
Gemini's calorie error on our own prompt. License alignment was never worth handing users
double the error in a calorie counter, and that is the trade the measurement revealed.

Reopen only with evidence, not intent: a Food-R1 landing on OpenRouter, a new open vision
model, or phone-photo cases showing the 8B result was a dataset artifact. The eval harness
makes re-testing an afternoon's work, so there is no cost to waiting.

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

- `qwen/qwen3-vl-30b-a3b-instruct` — documented alternate; add only if the local eval shows a
  real win specifically on visual-identity-ambiguity cases.
- `mistralai/mistral-small-2603` — text path only, and only if the eval shows Qwen-8B failing
  on text descriptions.
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
- [x] 41-case local set: 14 text cases + 27 Nutrition5k dish photos (CC BY 4.0, scale-measured
      truth), built reproducibly by `build_n5k_cases.py`. Caveat recorded in the eval README:
      rig-framed 640×480 overhead plates, so absolute accuracy is pessimistic and the
      `bad-angle`/`retry_image` bucket stays unexercised until real phone photos are added.
- [x] Baseline vs Qwen3-VL-8B eval run, result recorded — see "Eval result" below.
- [ ] **Model swap: blocked by the eval.** Qwen-8B is not shippable as the default on this
      evidence; see below before revisiting.
- [ ] Strings in all locales; privacy/network verification; `CLAUDE.md` updated.

## Eval result (2026-08-09, 41 cases)

Same case set, same prompt, same request shape, one run each.

| | gemini-3.1-flash-lite | qwen3-vl-8b-instruct |
|---|---|---|
| valid schema rate | 1.00 | 1.00 |
| kcal MAPE | 22.9% | 53.0% |
| kcal MAE | 67.9 | 177.9 |
| action match rate | 0.93 | 0.64 |
| MAPE — text only | 1.1% | 4.5% |
| MAPE — simple photo | 13.0% | 23.9% |
| MAPE — multi-component | 32.8% | 74.9% |
| MAPE — oils/hidden fat | 26.0% | 73.3% |

**The swap is off on this evidence.** The public gap the plan accepted was 39% vs 25% MAPE; the
measured gap on our prompt is 53% vs 23% — worse than advertised, not better. Two specific
findings beyond the headline:

- **Qwen blames the photo.** It raised `image_quality` on 14 of 41 cases ("image is blurry"),
  Gemini on none, which is what wrecks the action match rate: eight cases routed to
  `retry_image` where the honest answer was `ask_user`. In the app that is the worst possible
  failure — it tells the user to retake a photo that was fine, instead of asking for the detail
  that would actually help. **Confounded:** Nutrition5k images are 640×480, so "blurry" is
  partly fair comment. Only real phone photos at 1024 px can separate model defect from dataset
  artifact.
- **Qwen underestimates fatty food badly** — median bias −368 kcal on the oils bucket, against
  −50 for Gemini. Exactly the dishes where a calorie counter being wrong matters.

Not evidence of anything: the text-only bucket. Both models recite canonical reference values
for "100 g watermelon" to the digit, so that bucket tests JSON validity, not estimation.

Schema and routing (phases 1–2) are validated by this run: 82/82 responses parsed, no invalid
enums, no items/total mismatches, from two different model families.

## Sources

- [DiningBench paper and model results](https://arxiv.org/html/2604.10425)
- [Food-R1 paper and nutrition results](https://arxiv.org/html/2606.04986)
- [Qwen3-VL-8B-Instruct weights and Apache-2.0 model card](https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct)
- [Qwen3-VL-30B-A3B-Instruct weights and Apache-2.0 model card](https://huggingface.co/Qwen/Qwen3-VL-30B-A3B-Instruct)
- [Mistral Small 4 model selection and license](https://docs.mistral.ai/models/model-selection-guide?models=mistral-small-4-0-26-03)
- [WeirdBench text-only nutrition benchmark](https://weirdbench.com/benchmarks/nutrition-prediction)
- [OpenRouter Qwen catalog](https://openrouter.ai/qwen)
- [Study showing improvement from explicit food weight input](https://pubmed.ncbi.nlm.nih.gov/42502812/)
