# Solo Forge — Privacy Policy

**Effective date:** 2026-06-19

Solo Forge is a local-first fitness app. This policy describes exactly what data the app handles and how.

---

## What data the app stores

Solo Forge stores all of the following **only on your device**:

| Data | Where |
|---|---|
| Fasting sessions (start/end times, mode) | SQLite database (Room) |
| Food entries (name, macros, photos, comments) | SQLite database (Room) |
| Weight entries | SQLite database (Room) |
| Workout sessions | SQLite database (Room) |
| Nutrition goals, unit preferences, reminder settings | Android DataStore |
| Your OpenRouter API key | Android EncryptedSharedPreferences |

Android cloud backup is **disabled**. None of this data leaves your device automatically.

---

## Network activity

Solo Forge makes **one type of outbound network request**: when you tap **Analyze photo** in the Food tab, the app sends the photo (and any optional comment you added) to [OpenRouter](https://openrouter.ai) using the API key you provided in Settings.

- This request is **always user-initiated** — the app never phones home in the background.
- The only outbound host is `openrouter.ai`.
- OpenRouter forwards the request to an AI vision model to estimate macros and returns a structured result.
- OpenRouter's own privacy policy governs how they handle requests: [openrouter.ai/privacy](https://openrouter.ai/privacy).

If you have not set an API key, no network request is ever made.

---

## Data you export

When you use **Export to CSV**, the app writes a CSV file to a folder on your device that you selected. Once exported, that file is outside the app's control — it goes wherever you placed it (local storage, Google Drive, Dropbox, etc.).

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Required by the HTTP client library; used only for OpenRouter food-photo analysis |
| `CAMERA` | Lets you take a food photo in-app |
| `POST_NOTIFICATIONS` | Sends fasting reminder notifications |
| `FOREGROUND_SERVICE` | Keeps the active-fast live notification running |
| `READ_MEDIA_IMAGES` (optional) | Lets you pick an existing photo instead of taking one |

The app does **not** request `READ_CONTACTS`, `READ_LOGS`, location, microphone, or any broad media permission.

---

## Third-party SDKs

Solo Forge contains no analytics SDKs, no crash reporters, no ad networks, and no Firebase or Google Play Services dependencies.

---

## Children

Solo Forge is not directed at children under 13 and does not knowingly collect any information from children.

---

## Changes

If this policy changes materially, the new version will be committed to the [public repository](https://github.com/kirilan/SoloForge) with the updated effective date.

---

## Contact

Questions or concerns: **kickbul@gmail.com**
