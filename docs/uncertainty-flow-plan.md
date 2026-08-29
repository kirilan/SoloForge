# Uncertain-result flow — next-release plan

Status: **proposed, not started.** Written 2026-08-29, after 0.7.0 shipped user-selectable
models. 0.7.0 answered *which model runs*; this is about what happens when that model says it
isn't sure — which, on the evidence below, is most of the time.

## Why this is the next thing worth doing

The 108-model sweep in `food-analysis-model-improvement-plan.md` measured the same shape across
the whole field, not just our default:

- **`portion_unknown` appears in 80% of all photo analyses**, `hidden_ingredients` in 63%.
  Only 7% of analyses cite no uncertainty at all.
- **69% of screened models would not accept a single one of five test photos outright.** Our best
  row accepts 24 of 44; the default accepts 17.

So the uncertain-result path is not an edge case being handled gracefully — **it is the main path
through the feature.** 0.7.0 spent its effort on the model behind the request; the next release
should spend it on the screen the user actually lands on. No model change moves these numbers:
a stronger model cannot see grams or the oil already in the pan.

## Current behaviour, for reference

`AnalysisPolicy.routedAction` decides from enumerated fields only, never model prose, and is
model-agnostic — the routing is identical in all four settings rows:

1. Numbers incoherent (items don't sum, calories contradict macros, negative/non-finite) → `ask_user`
2. `image_quality` flagged → `retry_image`, downgraded to `ask_user` when there is no photo
3. Any other uncertainty reason → `ask_user`
4. `confidence: "low"` → `ask_user`
5. Otherwise the model's own `recommended_action`

Anything other than `accept` renders `UncertaintyCard` **above** the estimate form. The card never
blocks: the estimate stays editable and saveable throughout. It shows one hint per reason, then
either the "Retry with a stronger model" button (rows with an `escalationId`) or a line saying
there is nothing stronger (open-weight, most-accurate, and custom rows).

The user's ways out today: edit the numbers and save, type detail into the comment box and press
"Re-analyze with current comment", or save as-is.

## Problem 1 — `retry_image` is a dead end (highest value, smallest change)

Routing can return `retry_image`, and the UI has **no way to act on it.** The card prints "The
photo is hard to read — retake it closer, in better light" and offers no retake control. The only
route to a new photo is Discard, and `FoodViewModel.cancelAnalyze` then **deletes the capture file
and clears `AnalyzeState`** — so the user also loses the comment they had typed. The app asks for
a better photo and charges the user their work to provide one.

Proposed: a **"Retake photo"** button in the card whenever `routedAction == RETRY_IMAGE`, going
back to `FoodUiMode.Capture` while **preserving `state.comment`**. That is a new `UiMode`
transition plus keeping one field, not a new flow.

Watch the ownership rule: `cancelAnalyze` deletes the capture because the app owns that file. A
retake must delete the *old* capture and keep the new one, without leaking either.

## Problem 2 — the portion affordance exists but is not offered where it is needed

`portion_unknown` is the single most common reason at 80%, and its hint says "add the weight or
serving size". Meanwhile `ScaleByGramsButton` — which rescales every macro proportionally from a
corrected gram weight — already exists and is already on this screen, further down inside
`EstimateForm` (`AnalyzeScreen.kt:148`), alongside the same control in `EditFoodSheet` and
`ManualFoodSheet`.

So for the most common uncertainty in the app, the fix is already built and the user is instead
told to retype their meal into a comment box and spend a second API call.

Proposed: when `portion_unknown` is present, surface the existing grams control **inside the
uncertainty card**, so correcting the portion costs one number and zero requests. Nothing new to
build; this is a placement decision. It should measurably reduce re-analysis calls, which is also
the cheapest thing we can do for users on their own API key.

## Problem 3 — the rows with no escalation offer nothing in its place

Open-weight, Most accurate, and Advanced all print "The model you chose has no stronger model to
retry with. Adding details helps more." That is honest and it is a dead end. Those users get
strictly less than the two Gemini rows at the moment they are most stuck.

Options, in preference order:

1. Give the card a **"Add details"** affordance that focuses the comment field, so the sentence
   points at a control instead of at nothing. Smallest change, applies to every row.
2. For the open-weight row specifically, revisit whether any Apache-2.0 model beats
   `qwen3-vl-32b-instruct` — none did in the 2026-08-29 sweep, so this needs new evidence, not a
   new opinion.

## Problem 4 — `retry_image` rests on a single borderline photo

**Blocks confident work on Problem 1.** The `bad-angle` bucket is one image. Across the sweep only
58 of 99 models flagged it at all, and the shipped default raises `image_quality` on it roughly
**1 time in 9** — so `retry_image` may be close to dormant in production, and we cannot currently
tell the difference between "the path is rare" and "the path is broken".

Do this first: add **3–4 more bad-angle photos** to `truth.csv` — genuinely soft focus, genuine
low light, a genuinely obstructed plate — and re-measure the flag rate for all four offered rows.
If the default really does flag ~1 in 9 real bad photos, that is its own bug and it outranks the
retake button.

## Suggested order

1. **Widen the bad-angle bucket** (Problem 4) — evidence first; it decides how much Problem 1 is worth.
2. **Portion control in the card** (Problem 2) — highest frequency, already built, no new requests.
3. **Retake button** (Problem 1) — needs (1) to size, but is a real dead end regardless.
4. **"Add details" affordance** (Problem 3) — small, and levels up the three rows that currently end in a full stop.

## Out of scope

- Any change to `AnalysisPolicy` thresholds. The routing is not what's wrong; the options offered
  after routing are. Changing thresholds would also invalidate every recorded eval number.
- Automatic escalation, in any form. Reaffirmed by the sweep: the dominant uncertainty is
  `portion_unknown`, which no model can resolve from the same photo.
- Adding rows to the model list. That is `curated-model-choice-plan.md`, and it needs eval
  evidence, not UI work.
