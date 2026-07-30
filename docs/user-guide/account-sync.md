---
title: Account and sync
parent: User Guide
nav_order: 8
---

# Account and sync

**Classes:** `activities/UserActivity.kt`, `auth/AuthManager.kt`,
`sync/ProgressSyncManager.kt`, `sync/NotesSyncManager.kt`

Signing in is optional. Everything in the app works without an account — signing
in adds cross-device sync and cloud-backed chat history.

## Signing in

Google is the only sign-in provider. The profile icon in the top bar opens
`UserActivity`, which runs Google Sign-In and exchanges the resulting ID token
for a Firebase credential.

There is no email/password option, no account creation flow inside the app, and
no password is ever handled by the app itself.

## What syncs

Signed in, the following are written to your account document in Cloud
Firestore (`users/{uid}`):

| Data | Notes |
|:--|:--|
| XP and level | From the learning games |
| Achievements | ID, title and progress for each |
| Statistics | Per-category practice counters |
| Streak | Current streak and the last date played |
| Notes | Your per-element notes — **PRO or PRO+ only** |
| Chat history | Your last 20 assistant conversations |

Your favourites, theme, unit preferences, list ordering and other settings stay
local to the device.

## How conflicts are resolved

When you sign in on a second device, local and cloud state are merged rather
than one overwriting the other. The rule is **take the higher value** per field:
the greater XP, the further-along achievement progress, the longer streak. For
notes, the longer note wins per element.

This means playing on two devices never costs you progress, but it also means a
deliberate reset on one device will be undone by the other device's higher
numbers.

Implemented in `ProgressSyncManager.mergeAndUploadLocalProgress`.

## Notes sync needs PRO

Cloud sync of element notes requires being signed in **and** owning PRO or PRO+.
The check is in `NotesSyncManager.canSyncNotes()`. Without it, notes still work
— they are stored locally in `preferences/NotesPreference.kt` — they just stay
on that device.

Note sync is debounced by two seconds, so editing a note does not fire a write
per keystroke.

## Chat history

Assistant conversations are saved to the same account document as an array,
capped at the 20 most recent sessions. Signed out, chat history exists only for
as long as the app is running.

## Achievements and profile

`UserActivity` also shows your profile photo, level, and the full achievement
list. Achievements earned while you were mid-session surface as a toast the next
time an activity resumes — handled centrally in `activities/BaseActivity.kt`.

## Signing out

Sign-out clears both the Firebase session and the Google client session. Local
progress is not deleted; it remains on the device and will re-merge if you sign
back in.

## Analytics

Firebase Analytics logs screen views. This is separate from sign-in and can be
turned off in [Settings](settings-languages) — the toggle is backed by
`preferences/AnalyticsPreference.kt`.
