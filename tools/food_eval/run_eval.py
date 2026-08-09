"""Run the app's food-analysis request against a local case set and score it.

Uses the app's exact prompt (parsed out of VisionPrompts.kt so it cannot drift),
image preparation (long edge 1024, JPEG q85, EXIF-corrected), request shape, and
routing policy. Nothing here talks to Android; it talks to OpenRouter the same
way the app does.

    export OPENROUTER_API_KEY=sk-or-...
    python tools/food_eval/run_eval.py --model google/gemini-3.1-flash-lite
    python tools/food_eval/run_eval.py --model qwen/qwen3-vl-8b-instruct

Cases live in tools/food_eval/cases.json (gitignored, along with images/ and
results/ — personal photos and raw model output stay off GitHub). See README.md
for the case format and how the ~40-case set is built.
"""

import argparse
import base64
import io
import json
import math
import os
import re
import sys
import textwrap
import time
from pathlib import Path

import requests
from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
PROMPT_FILE = REPO / "app/src/main/java/com/kbul/spicycrab/network/VisionPrompts.kt"
CASES = HERE / "cases.json"
RESULTS = HERE / "results"

ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
HEADERS_EXTRA = {
    "HTTP-Referer": "https://github.com/kirilan/SoloForge",
    "X-Title": "Solo Forge",
}
MAX_LONG_EDGE = 1024
JPEG_QUALITY = 85
TEMPERATURE = 0.2

REASONS = {
    "image_quality",
    "identity_ambiguous",
    "portion_unknown",
    "preparation_unknown",
    "hidden_ingredients",
}
ACTIONS = {"accept", "ask_user", "retry_image"}
MACROS = ("protein_g", "carbs_g", "fat_g")


def system_prompt() -> str:
    """The literal SYSTEM string from VisionPrompts.kt, trimIndent applied."""
    source = PROMPT_FILE.read_text(encoding="utf-8")
    match = re.search(r'SYSTEM\s*=\s*"""(.*?)"""', source, re.S)
    if not match:
        sys.exit(f"Could not find VisionPrompts.SYSTEM in {PROMPT_FILE}")
    return textwrap.dedent(match.group(1)).strip()


def image_part(path: Path) -> dict:
    """Mirror ImageUtils.fileToBase64Jpeg: EXIF-rotate, long edge 1024, JPEG 85."""
    with Image.open(path) as img:
        img = ImageOps.exif_transpose(img).convert("RGB")
        longest = max(img.size)
        if longest > MAX_LONG_EDGE:
            scale = MAX_LONG_EDGE / longest
            img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=JPEG_QUALITY)
    encoded = base64.b64encode(buf.getvalue()).decode()
    return {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{encoded}"}}


def analyze(key: str, model: str, prompt: str, case: dict) -> dict:
    comment = case.get("comment") or "Please estimate calories and macros for this food."
    parts = [{"type": "text", "text": comment}]
    if case.get("image"):
        parts.append(image_part(HERE / case["image"]))

    response = requests.post(
        ENDPOINT,
        headers={"Authorization": f"Bearer {key}", **HEADERS_EXTRA},
        json={
            "model": model,
            "messages": [
                {"role": "system", "content": prompt},
                {"role": "user", "content": parts},
            ],
            "response_format": {"type": "json_object"},
            "temperature": TEMPERATURE,
        },
        timeout=120,
    )
    response.raise_for_status()
    content = response.json()["choices"][0]["message"]["content"]
    cleaned = content.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    return json.loads(cleaned)


def schema_errors(dto: dict) -> list[str]:
    """Same contract the app enforces, so a pass here means the app would accept it."""
    errors = []
    numbers = ["estimated_grams", "calories", *MACROS, "fiber_g"]
    for field in ["item_name", *numbers]:
        if field not in dto and field != "fiber_g":
            errors.append(f"missing {field}")
    for field in numbers:
        value = dto.get(field, 0)
        if not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
            errors.append(f"bad {field}={value!r}")
    for item in dto.get("items", []):
        for field in ["estimated_grams", "calories"]:
            value = item.get(field)
            if not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
                errors.append(f"bad item {field}={value!r}")
    bad_reasons = set(map(str.lower, dto.get("uncertainty_reasons", []))) - REASONS
    if bad_reasons:
        errors.append(f"unknown reasons {sorted(bad_reasons)}")
    if str(dto.get("recommended_action", "accept")).lower() not in ACTIONS:
        errors.append(f"unknown action {dto.get('recommended_action')!r}")
    if not errors:
        items_kcal = sum(i["calories"] for i in dto.get("items", []))
        if dto.get("items") and not close_enough(items_kcal, dto["calories"]):
            errors.append(f"items sum {items_kcal:.0f} != meal {dto['calories']:.0f}")
        atwater = 4 * dto["protein_g"] + 4 * dto["carbs_g"] + 9 * dto["fat_g"]
        if not close_enough(atwater, dto["calories"]):
            errors.append(f"macros imply {atwater:.0f} kcal, said {dto['calories']:.0f}")
    return errors


def close_enough(a: float, b: float) -> bool:
    """Mirrors AnalysisPolicy.closeEnough — keep the two in step."""
    diff = abs(a - b)
    return diff <= 100.0 or diff <= 0.4 * max(a, b)


def routed_action(dto: dict, has_image: bool) -> str:
    """Mirrors AnalysisPolicy.routedAction."""
    reasons = set(map(str.lower, dto.get("uncertainty_reasons", [])))
    if schema_errors(dto):
        action = "ask_user"
    elif "image_quality" in reasons:
        action = "retry_image"
    elif reasons or str(dto.get("confidence", "")).lower() == "low":
        action = "ask_user"
    else:
        action = str(dto.get("recommended_action", "accept")).lower()
    return "ask_user" if action == "retry_image" and not has_image else action


def score(cases: list[dict], key: str, model: str, prompt: str, pause: float) -> list[dict]:
    rows = []
    for index, case in enumerate(cases, 1):
        print(f"[{index}/{len(cases)}] {case['id']}", flush=True)
        row = {
            "id": case["id"],
            "tags": case.get("tags", []),
            "expected": case.get("expected", {}),
            "image_case": bool(case.get("image")),
        }
        started = time.monotonic()
        try:
            dto = analyze(key, model, prompt, case)
        except Exception as exc:  # a failed call is a data point, not a crash
            row["error"] = f"{type(exc).__name__}: {exc}"
            row["seconds"] = round(time.monotonic() - started, 1)
            rows.append(row)
            continue
        # The user is standing there holding a plate; a correct answer at 55s is a broken feature.
        row["seconds"] = round(time.monotonic() - started, 1)
        row["response"] = dto
        row["schema_errors"] = schema_errors(dto)
        row["action"] = routed_action(dto, bool(case.get("image")))
        rows.append(row)
        if pause:
            time.sleep(pause)
    return rows


def summarize(rows: list[dict]) -> dict:
    timed = sorted(r["seconds"] for r in rows if "seconds" in r)
    ok = [r for r in rows if "response" in r and not r["schema_errors"]]
    truthed = [r for r in ok if "kcal" in r["expected"]]
    summary = {
        "cases": len(rows),
        "call_failures": sum("error" in r for r in rows),
        "valid_schema_rate": round(len(ok) / len(rows), 3) if rows else 0.0,
    }
    if timed:
        photo = sorted(r["seconds"] for r in rows if r.get("image_case") and "seconds" in r)
        summary["seconds_median"] = timed[len(timed) // 2]
        summary["seconds_max"] = timed[-1]
        if photo:
            summary["seconds_median_photo"] = photo[len(photo) // 2]
    if truthed:
        errors = [abs(r["response"]["calories"] - r["expected"]["kcal"]) for r in truthed]
        summary["kcal_mae"] = round(sum(errors) / len(truthed), 1)
        summary["kcal_mape"] = round(
            100 * sum(e / r["expected"]["kcal"] for e, r in zip(errors, truthed)) / len(truthed), 1
        )
        for macro in MACROS:
            paired = [r for r in truthed if macro in r["expected"]]
            if paired:
                summary[f"{macro}_mae"] = round(
                    sum(abs(r["response"][macro] - r["expected"][macro]) for r in paired) / len(paired), 1
                )
    judged = [r for r in rows if "action" in r and r["expected"].get("action")]
    if judged:
        matches = sum(r["action"] == r["expected"]["action"] for r in judged)
        summary["action_match_rate"] = round(matches / len(judged), 3)
        summary["action_judged"] = len(judged)
    return summary


def selftest() -> None:
    """No network: checks the scoring mirrors AnalysisPolicy and the prompt still parses."""
    prompt = system_prompt()
    assert prompt.startswith("You are a precise nutrition estimator"), prompt[:60]
    assert "uncertainty_reasons" in prompt

    good = {
        "item_name": "Rice", "estimated_grams": 100.0, "calories": 130.0,
        "protein_g": 2.7, "carbs_g": 28.0, "fat_g": 0.3, "fiber_g": 0.4,
        "confidence": "high", "uncertainty_reasons": [], "recommended_action": "accept",
    }
    assert schema_errors(good) == []
    assert routed_action(good, has_image=True) == "accept"

    # prose can't route; enums can
    chatty = {**good, "item_name": "mixed meal, hidden sauces, portion unclear"}
    assert routed_action(chatty, has_image=True) == "accept"
    assert routed_action({**good, "uncertainty_reasons": ["portion_unknown"]}, True) == "ask_user"
    assert routed_action({**good, "uncertainty_reasons": ["image_quality"]}, True) == "retry_image"
    assert routed_action({**good, "uncertainty_reasons": ["image_quality"]}, False) == "ask_user"
    assert routed_action({**good, "confidence": "low"}, True) == "ask_user"

    assert schema_errors({**good, "calories": -5})
    assert schema_errors({**good, "calories": float("nan")})
    assert schema_errors({**good, "uncertainty_reasons": ["vibes"]})
    assert schema_errors({**good, "calories": 2000.0})  # macros say ~130
    split = {**good, "calories": 900.0, "protein_g": 45.0, "carbs_g": 90.0, "fat_g": 40.0,
             "items": [{"name": "a", "estimated_grams": 50, "calories": 100}]}
    assert any("items sum" in e for e in schema_errors(split)), schema_errors(split)
    assert routed_action(split, has_image=True) == "ask_user"

    rows = [
        {"id": "a", "expected": {"kcal": 100, "action": "accept"}, "response": {**good, "calories": 120.0},
         "schema_errors": [], "action": "accept"},
        {"id": "b", "expected": {"action": "ask_user"}, "error": "boom"},
    ]
    summary = summarize(rows)
    assert summary["call_failures"] == 1
    assert summary["valid_schema_rate"] == 0.5
    assert summary["kcal_mae"] == 20.0
    assert summary["action_match_rate"] == 1.0
    print("selftest ok")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--selftest", action="store_true", help="offline scoring check, no API calls")
    if "--selftest" in sys.argv:
        selftest()
        return
    parser.add_argument("--model", required=True)
    parser.add_argument("--cases", type=Path, default=CASES)
    parser.add_argument("--tag", help="only run cases carrying this tag")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--pause", type=float, default=0.5, help="seconds between calls")
    args = parser.parse_args()

    key = os.environ.get("OPENROUTER_API_KEY")
    if not key:
        sys.exit("Set OPENROUTER_API_KEY (the eval never reads the app's stored key).")
    if not args.cases.exists():
        sys.exit(f"No case file at {args.cases} — copy cases.sample.json and build yours.")

    cases = json.loads(args.cases.read_text(encoding="utf-8"))
    if args.tag:
        cases = [c for c in cases if args.tag in c.get("tags", [])]
    if args.limit:
        cases = cases[: args.limit]
    if not cases:
        sys.exit("No cases selected.")

    rows = score(cases, key, args.model, system_prompt(), args.pause)
    summary = summarize(rows)

    RESULTS.mkdir(exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    out = RESULTS / f"{args.model.replace('/', '_')}-{stamp}.json"
    out.write_text(
        json.dumps({"model": args.model, "summary": summary, "rows": rows}, indent=2),
        encoding="utf-8",
    )

    print()
    for k, v in summary.items():
        print(f"{k:>20}: {v}")
    for row in rows:
        if row.get("error"):
            print(f"  FAIL {row['id']}: {row['error']}")
        elif row.get("schema_errors"):
            print(f"  SCHEMA {row['id']}: {'; '.join(row['schema_errors'])}")
        elif row["expected"].get("action") and row["action"] != row["expected"]["action"]:
            print(f"  ACTION {row['id']}: got {row['action']}, expected {row['expected']['action']}")
    print(f"\nwrote {out}")


if __name__ == "__main__":
    main()
