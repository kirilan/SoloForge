"""Build cases.json from the text cases plus a sample of Nutrition5k dishes.

Nutrition5k (CC BY 4.0, Google Research) ships overhead plate photos with
scale-measured mass and per-ingredient nutrition, which is exactly the ground
truth the plan refuses to get from another model. It does not replace your own
photos: every dish is a cafeteria plate shot straight down under even light, so
there is no bad-angle bucket in here and never will be.

    python tools/food_eval/build_n5k_cases.py            # metadata already local
    python tools/food_eval/build_n5k_cases.py --dishes 24

Downloads one ~400 KB rgb.png per selected dish into images/n5k/. Rerunning with
the same --seed picks the same dishes and re-uses images already on disk.
"""

import argparse
import csv
import json
import random
import sys
from pathlib import Path

import requests

HERE = Path(__file__).resolve().parent
META = HERE / "n5k"
IMAGES = HERE / "images" / "n5k"
SAMPLE = HERE / "cases.sample.json"
OUT = HERE / "cases.json"
BUCKET = "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset"

FIELDS_PER_INGREDIENT = 7
HIDDEN_FAT = ("oil", "butter", "dressing", "mayo", "sauce", "gravy", "margarine")


def dishes() -> list[dict]:
    """One record per dish; the CSV has no header and variable-length rows."""
    out = []
    for name in ("dish_metadata_cafe1.csv", "dish_metadata_cafe2.csv"):
        path = META / name
        if not path.exists():
            sys.exit(f"Missing {path} — see README.md for the two curl commands.")
        with path.open(encoding="utf-8") as handle:
            for row in csv.reader(handle):
                if len(row) < 6:
                    continue
                try:
                    dish = {
                        "id": row[0],
                        "kcal": float(row[1]),
                        "mass": float(row[2]),
                        "fat_g": float(row[3]),
                        "carbs_g": float(row[4]),
                        "protein_g": float(row[5]),
                    }
                except ValueError:
                    continue
                names = row[7::FIELDS_PER_INGREDIENT]
                dish["ingredients"] = [n.strip().lower() for n in names if n.strip()]
                out.append(dish)
    return out


def plausible(dish: dict) -> bool:
    """Drop rig noise: empty plates, impossible masses, macros that contradict calories."""
    if not 50 <= dish["kcal"] <= 1500 or not 30 <= dish["mass"] <= 1500:
        return False
    if not dish["ingredients"]:
        return False
    if any(dish[k] < 0 for k in ("fat_g", "carbs_g", "protein_g")):
        return False
    atwater = 4 * dish["protein_g"] + 4 * dish["carbs_g"] + 9 * dish["fat_g"]
    diff = abs(atwater - dish["kcal"])
    return diff <= 100.0 or diff <= 0.4 * max(atwater, dish["kcal"])


def bucket_of(dish: dict) -> str | None:
    if any(h in ingredient for ingredient in dish["ingredients"] for h in HIDDEN_FAT):
        return "oils"
    if len(dish["ingredients"]) <= 2:
        return "simple"
    if len(dish["ingredients"]) >= 4:
        return "multi-component"
    return None


def expected_action(bucket: str, dish: dict) -> str | None:
    """Only assert an action where a person would agree without argument.

    Invisible fat genuinely warrants asking. A single recognizable ingredient
    genuinely does not. Everything in between is left unscored on purpose —
    an overhead photo of a mixed plate has a defensible answer either way, and
    scoring a coin flip would just add noise to action_match_rate.
    """
    if bucket == "oils":
        return "ask_user"
    if bucket == "simple" and len(dish["ingredients"]) == 1:
        return "accept"
    return None


def fetch_image(dish_id: str) -> Path | None:
    IMAGES.mkdir(parents=True, exist_ok=True)
    dest = IMAGES / f"{dish_id}.png"
    if dest.exists():
        return dest
    url = f"{BUCKET}/imagery/realsense_overhead/{dish_id}/rgb.png"
    response = requests.get(url, timeout=60)
    if response.status_code != 200:
        return None
    dest.write_bytes(response.content)
    return dest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dishes", type=int, default=24)
    parser.add_argument("--seed", type=int, default=5000)
    args = parser.parse_args()

    pool = [d for d in dishes() if plausible(d)]
    buckets: dict[str, list[dict]] = {"simple": [], "multi-component": [], "oils": []}
    for dish in pool:
        name = bucket_of(dish)
        if name:
            buckets[name].append(dish)
    for group in buckets.values():
        group.sort(key=lambda d: d["id"])

    share = {"simple": 0.3, "multi-component": 0.42, "oils": 0.28}
    rng = random.Random(args.seed)
    picked = []
    for name, group in buckets.items():
        want = round(args.dishes * share[name])
        picked += [(name, d) for d in rng.sample(group, min(want, len(group)))]

    cases = [c for c in json.loads(SAMPLE.read_text(encoding="utf-8")) if not c.get("image")]
    skipped = 0
    for name, dish in picked:
        image = fetch_image(dish["id"])
        if not image:
            skipped += 1
            continue
        expected = {
            "kcal": round(dish["kcal"], 1),
            "protein_g": round(dish["protein_g"], 1),
            "carbs_g": round(dish["carbs_g"], 1),
            "fat_g": round(dish["fat_g"], 1),
        }
        action = expected_action(name, dish)
        if action:
            expected["action"] = action
        cases.append({
            "id": f"n5k-{dish['id']}",
            "tags": [name, "photo", "nutrition5k"],
            "image": str(image.relative_to(HERE)).replace("\\", "/"),
            "comment": "",
            "expected": expected,
            "source": "Nutrition5k (CC BY 4.0), scale-measured ground truth",
        })

    OUT.write_text(json.dumps(cases, indent=2), encoding="utf-8")
    photo = sum(1 for c in cases if c.get("image"))
    print(f"{len(cases)} cases -> {OUT}  ({photo} photo, {len(cases) - photo} text, {skipped} skipped)")


if __name__ == "__main__":
    main()
