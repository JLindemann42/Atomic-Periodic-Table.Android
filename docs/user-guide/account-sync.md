---
title: Account and sync
parent: User Guide
nav_order: 8
---

# Account and sync

**Classes:** `activities/UserActivity.kt`, `auth/AuthManager.kt`,
`sync/ProgressSyncManager.kt`, `sync/ProgressMerge.kt`, `sync/AutoSync.kt`,
`sync/NotesSyncManager.kt`

Signing in is optional. Everything in the app works without an account — signing
in adds cross-device sync and cloud-backed chat history.

Sync is cross-**platform**, not just cross-device: the Windows version of Atomic
reads and writes the same account document, so progress made on a PC shows up on
the phone and the other way round.

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
| Notes | Your per-element notes |
| Chat history | Your last 20 assistant conversations |
| Your tier | Which of PRO / PRO+ you own, per platform, plus Ultimate |

Your favourites, theme, unit preferences, list ordering and other settings stay
local to the device.

Sync happens on its own — when the app opens and when you leave it. There is no
sync button, and nothing to turn on beyond signing in.

## How conflicts are resolved

When you sign in on a second device, local and cloud state are merged rather
than one overwriting the other. The rule is **take the higher value** per field:
the greater XP, the further-along achievement progress, the longer streak, the
most recent day played. For notes, the longer note wins per element.

This means playing on two devices never costs you progress, but it also means a
deliberate reset on one device will be undone by the other device's higher
numbers.

Your tier is the one thing that is *not* merged by taking the higher value. PRO
and PRO+ are purchases from a specific store, so each platform reports its own
and neither can unlock the other. PRO+ Ultimate is the product that does cross
over — see [PRO tiers](pro-tiers).

Implemented in `sync/ProgressMerge.kt`, which the Windows app mirrors exactly.

## Notes sync needs PRO

Cloud sync requires being signed in **and** owning a paid tier — PRO, PRO+ or
Ultimate. The checks are in `NotesSyncManager.canSyncNotes()` and
`AutoSync.canSync()`. Without it, notes still work — they are stored locally in
`preferences/NotesPreference.kt` — they just stay on that device.

Note sync is debounced by two seconds, so editing a note does not fire a write
per keystroke.

## Chat history

Assistant conversations are saved to the same account document as an array,
capped at the 20 most recent sessions. Signed out, chat history exists only for
as long as the app is running.

## Licenses

The account page carries a **Licenses** card, laid out after the Windows account
page's:

| Row | What it says |
|:--|:--|
| Android | the tier bought on Google Play — `PRO+`, `PRO` or FREE |
| Windows | the tier the PC last reported, or *Not synced yet* |
| PRO+ Ultimate | only once owned; the offer below replaces it until then |

Two platform rows rather than one tier, because PRO and PRO+ are purchases from
a specific store: owning PRO+ on a PC says nothing about this phone. That is the
question a shared account makes it possible to ask and a bare tier cannot
answer.

Three details that look like nitpicks and are not:

- **An Ultimate owner sees "PRO+ Ultimate" on both rows**, not PRO+. That is what
  they bought; the PRO+ it grants on each platform is a consequence, not a second
  purchase.
- ***Not synced yet* is not FREE.** A device that has never written to the
  account document has not said it owns nothing — it has said nothing at all.
- **The badge chip shows Ultimate rather than the platform tier** when it is
  owned, for the same reason, and the second chip naming the PC's tier is hidden
  under Ultimate because Ultimate already means both platforms.

While Ultimate is not owned, the card ends with an offer: a line of description
and a button to the PRO page, labelled *Upgrade to Ultimate* when PRO+ is already
owned on **either** platform and *Get PRO+ Ultimate* otherwise — the two products
the PRO page sells.

## Account

Below it, an **Account** card:

| Row | What it says |
|:--|:--|
| Account ID | your Firebase uid — tap to copy |
| Last synced | relative time of the last successful sync |

The **Account ID** is shown deliberately. Both apps write to `users/{uid}`, so it
is the one value that proves this device and the PC landed in the same account —
the precondition the whole feature rests on, and the one that fails silently
rather than loudly. Nothing else in either app can reveal it. It is tappable
because it is 28 characters that nobody should be retyping.

### Licenses are read even on a free account

Sync is a paid feature, but the *licence keys* are read for anyone signed in.
They have to be: Ultimate bought on the PC is exactly how an account with no
Google Play purchase becomes PRO+ here, so requiring PRO before reading would
make the grant unreachable — the phone would be waiting for an entitlement it
could only learn about by reading.

That read writes nothing, so a free account still contributes no progress to the
document. If it finds an Ultimate, the account becomes paid and the full sync
runs immediately after.

## Achievements and profile

`UserActivity` also shows your profile photo, level, and the full achievement
list. Achievements earned while you were mid-session surface as a toast the next
time an activity resumes — handled centrally in `activities/BaseActivity.kt`.

## Signing out

Sign-out clears both the Firebase session and the Google client session. Local
progress is not deleted; it remains on the device and will re-merge if you sign
back in.

Signing out does not remove an Ultimate that has already been granted on this
device.

## Analytics

Firebase Analytics logs screen views. This is separate from sign-in and can be
turned off in [Settings](settings-languages) — the toggle is backed by
`preferences/AnalyticsPreference.kt`.
