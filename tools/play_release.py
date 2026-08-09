"""Upload a signed AAB to Google Play and roll it out.

Replaces hand-driving the Play Console for routine releases. Needs a service
account JSON with "Release manager" on the app (Play Console -> Setup -> API
access); point GOOGLE_PLAY_SERVICE_ACCOUNT at it.

    python tools/play_release.py --dry-run     # everything except going live
    python tools/play_release.py
    python tools/play_release.py --status      # what each track is serving

A stale testing track counts against the target API requirement, so --status is
the fastest way to find the build behind a Console warning.

The Console is still the place for policy declarations and App content forms.
"""

import argparse
import os
import re
import sys
from pathlib import Path

from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

PACKAGE = "com.kbul.spicycrab"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]

REPO = Path(__file__).resolve().parent.parent
GRADLE = REPO / "app" / "build.gradle.kts"
AAB = REPO / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
CHANGELOGS = REPO / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
MAX_NOTES_CHARS = 500


def version_code() -> int:
    match = re.search(r"^\s*versionCode\s*=\s*(\d+)", GRADLE.read_text(), re.M)
    if not match:
        sys.exit(f"No versionCode found in {GRADLE}")
    return int(match.group(1))


def check(response, what):
    if not response.ok:
        sys.exit(f"{what} failed ({response.status_code}): {response.text}")
    return response.json() if response.content else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--track", default="production")
    parser.add_argument("--aab", type=Path, default=AAB)
    parser.add_argument("--dry-run", action="store_true",
                        help="do everything but commit; the edit is discarded")
    parser.add_argument("--status", action="store_true",
                        help="list every track's active releases and exit")
    args = parser.parse_args()

    key = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT")
    if not key or not Path(key).is_file():
        sys.exit("Set GOOGLE_PLAY_SERVICE_ACCOUNT to the service account JSON path")

    session = AuthorizedSession(
        service_account.Credentials.from_service_account_file(key, scopes=SCOPES)
    )
    base = f"{API}/applications/{PACKAGE}"

    if args.status:
        edit = check(session.post(f"{base}/edits"), "Creating edit")["id"]
        tracks = check(session.get(f"{base}/edits/{edit}/tracks"), "Listing tracks")
        for track in tracks.get("tracks", []):
            for release in track.get("releases", []):
                codes = ", ".join(release.get("versionCodes", [])) or "—"
                fraction = release.get("userFraction")
                rollout = f" {fraction:.0%}" if fraction else ""
                print(f"{track['track']:<12} {release['status']}{rollout}  "
                      f"versionCode {codes}  {release.get('name', '')}")
        session.delete(f"{base}/edits/{edit}")
        return

    if not args.aab.is_file():
        sys.exit(f"No AAB at {args.aab} — run: gradlew.bat bundleRelease")

    expected = version_code()
    notes_file = CHANGELOGS / f"{expected}.txt"
    if not notes_file.is_file():
        sys.exit(f"No changelog at {notes_file}")
    notes = notes_file.read_text(encoding="utf-8").strip()
    # Play rejects this at commit time, after the AAB has already uploaded. 0.6.0 hit it at 547.
    if len(notes) > MAX_NOTES_CHARS:
        sys.exit(f"{notes_file.name} is {len(notes)} chars; Play's limit is {MAX_NOTES_CHARS}.")

    edit = check(session.post(f"{base}/edits"), "Creating edit")["id"]
    print(f"Edit {edit}")

    uploaded = check(
        session.post(
            f"{UPLOAD_API}/applications/{PACKAGE}/edits/{edit}/bundles?uploadType=media",
            data=args.aab.read_bytes(),
            headers={"Content-Type": "application/octet-stream"},
        ),
        "Uploading bundle",
    )
    got = uploaded["versionCode"]
    if got != expected:
        sys.exit(f"AAB is versionCode {got}, but build.gradle.kts says {expected}")
    print(f"Uploaded versionCode {got} ({args.aab.stat().st_size / 1e6:.2f} MB)")

    check(
        session.put(
            f"{base}/edits/{edit}/tracks/{args.track}",
            json={
                "track": args.track,
                "releases": [{
                    "versionCodes": [str(got)],
                    "status": "completed",
                    # ponytail: en-US only — it's the only changelog locale that exists.
                    "releaseNotes": [{"language": "en-US", "text": notes}],
                }],
            },
        ),
        f"Assigning to {args.track}",
    )
    print(f"Track {args.track}: full rollout, notes from {notes_file.name}")

    if args.dry_run:
        session.delete(f"{base}/edits/{edit}")
        print("Dry run — edit discarded, nothing published.")
        return

    check(session.post(f"{base}/edits/{edit}:commit"), "Committing edit")
    print(f"Submitted {got} to {args.track}. Review status is in the Console.")


if __name__ == "__main__":
    main()
