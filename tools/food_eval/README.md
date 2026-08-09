# Food analysis eval

Answers three questions and nothing else:

1. Does the structured schema + prompt behave at least as well as the old one?
2. Is Qwen3-VL-8B good enough to be the default before we swap it in?
3. Did a model deprecation, prompt edit, or schema change break anything?

It is not a research protocol — ~40 cases, one run each. DiningBench already
exists for ranking models; this checks *our* prompt on *our* request shape.

## Setup

```bash
pip install requests pillow
export OPENROUTER_API_KEY=sk-or-...
cp tools/food_eval/cases.sample.json tools/food_eval/cases.json
python tools/food_eval/run_eval.py --model google/gemini-3.1-flash-lite
```

`cases.json`, `images/`, and `results/` are gitignored: personal photos and raw
model output stay local. Only the sample set is committed.

## Building the case set from Nutrition5k

Photo cases come from [Nutrition5k](https://github.com/google-research-datasets/Nutrition5k)
(CC BY 4.0): overhead plate photos with scale-measured mass and per-ingredient
nutrition — real ground truth, not another model's guess.

```bash
mkdir -p tools/food_eval/n5k
curl -s -o tools/food_eval/n5k/dish_metadata_cafe1.csv https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset/metadata/dish_metadata_cafe1.csv
```

```bash
curl -s -o tools/food_eval/n5k/dish_metadata_cafe2.csv https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset/metadata/dish_metadata_cafe2.csv
```

```bash
python tools/food_eval/build_n5k_cases.py --dishes 32
```

That writes `cases.json`: the text cases plus ~27 dish photos (~14 MB downloaded
one file at a time, deterministic for a given `--seed`).

**Know what this set is not.** Every Nutrition5k image is 640×480, shot straight
down under even light, with the scanning rig — wires, wooden frame, turntable —
visible in frame. That is not a phone photo of dinner. Two consequences:

- Absolute accuracy here is pessimistic and does not predict field accuracy.
- Relative comparison between two models on the same images is still valid, and
  that is the decision the eval exists to make.
- There is no `bad-angle` bucket and there cannot be one. Blurry, dark, and
  obstructed cases need your own photos; until then `retry_image` routing is
  unexercised.

## Adding your own photos

Drop images in `images/`, describe them in `truth.csv`, and rerun the builder — those rows are
merged into `cases.json` alongside the dataset cases. This is the only way to get `bad-angle`
cases, and the only source of real phone-camera conditions.

```
filename,tags,comment,kcal,protein_g,carbs_g,fat_g,action
dessert-plate-blurry.jpg,bad-angle phone,,,,,,retry_image
```

Numbers may be blank — a row with only an `action` still scores routing, which is often the
more useful measurement. **Crop people out before adding a photo:** the eval sends the full
frame to OpenRouter, and a face in the background is not part of what you are testing.

## Adding your own cases

Top the sample up to ~40 cases, spread across the tags the script filters on:

| tag | what it covers | how many |
|---|---|---|
| `simple` | one recognizable food, known weight | ~10 |
| `multi-component` | a plate with several parts | ~8 |
| `oils` | fried or dressed food where fat is invisible | ~5 |
| `hidden` | fillings, sauces, unlabelled restaurant food | ~5 |
| `bad-angle` | blurry, dark, obstructed, or extreme-angle photos | ~5 |
| `text` | text-only descriptions, no photo | ~7 |

Ground truth comes from package labels, a reputable nutrient database, or a
kitchen scale plus a label — **never from another model.** A case with no
reliable numbers is still useful: give it only an `expected.action` and it
scores routing without scoring accuracy.

```json
{
  "id": "photo-chicken-rice-plate",
  "tags": ["multi-component", "photo"],
  "image": "images/chicken-rice.jpg",
  "comment": "",
  "expected": { "kcal": 640, "protein_g": 52, "carbs_g": 68, "fat_g": 14, "action": "accept" }
}
```

`expected.action` is what the app *should* do with that case: `accept` for a
solid estimate, `ask_user` when the case genuinely hides something (portion,
oil, filling), `retry_image` for a photo a person could not read either.

## What it measures

- **valid_schema_rate** — responses the app would accept: finite non-negative
  numbers, known enum values, items summing to the meal, calories consistent
  with the macros. The tolerance mirrors `AnalysisPolicy.closeEnough`; if you
  change one, change the other.
- **kcal_mae / kcal_mape** and per-macro MAE over cases with ground truth.
- **action_match_rate** — how often routing lands where the case says it should.
  This is the number that says whether uncertainty is being reported honestly,
  and it matters more than a couple of MAE points.

The prompt is parsed straight out of `VisionPrompts.kt`, and image prep mirrors
`ImageUtils` (EXIF-rotate, long edge 1024, JPEG 85), so a run here is what the
phone would have sent.

## When to run it

Before changing any model id, prompt, temperature, image preprocessing, or
schema — and once per release while checking whether Food-R1 has landed on
OpenRouter with a compatible license.
