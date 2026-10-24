---
title: Persistence and sync
parent: Developer Guide
nav_order: 6
---

# Persistence and sync

Three tiers of state: assets (read-only, shipped), SharedPreferences (local
user state), and Cloud Firestore (optional, signed-in only). There is no
database.

## The preference wrapper pattern

Every persisted setting gets a small class in `preferences/` wrapping exactly
one SharedPreferences file, with one key and a `getValue()`/`setValue()` pair.

```kotlin
class ThemePreference(context: Context) {
    private val prefs = context.getSharedPreferences("Theme_Preference", MODE_PRIVATE)
    fun getValue() = prefs.getInt("theme", 100)
    fun setValue(value: Int) = prefs.edit().putInt("theme", value).apply()
}
```

21 files, some declaring several classes. No DataStore, no migration
infrastructure.

### The preferences

| Class | File name | Controls |
|:--|:--|:--|
| `ProVersion` | `Pro_Preference` | PRO ownership (`1` no, `100` yes) |
| `ProPlusVersion` | `Pro_Plus_Preference` | PRO+ ownership |
| `ThemePreference` | `Theme_Preference` | Light `0` / dark `1` / system `100` |
| `UnitsPreferences` | — | Preferred measurement units |
| `NotesPreference` | `Notes_Preference` | All element notes, one blob |
| `ElementSendAndLoad` | `ElementSendAndLoad` | Selected element key between activities |
| `SearchPreferences` | `Search_Preference` | Search state |
| `SettingPreferences` | `Offline_Preference`, `iso_pref` | Offline mode, isotope display |
| `IsoPreferences` | `Iso_Preference`, `send_Iso_pref` | Isotope table state |
| `BottomBarPref` | `Hide_Preference` | Bottom nav visibility |
| `TableOrderPreference` | — | Saved order of the tables list |
| `ToolOrderPreference` | — | Saved order of the tools list |
| `MostUsedPreference` | — | Table usage scores |
| `MostUsedToolPreference` | — | Tool usage scores |
| `AnalyticsPreference` | — | Firebase Analytics opt-out |
| `FavoriteBarPreferences` | — | ~16 classes, one per property toggle |
| `DictionaryPreferences`, `ConstantsPreference`, `GeologyPreference`, `AlloyPreference`, `PoissonPreferences` | — | Per-table view state |

Several classes in `utils/` also persist directly to SharedPreferences without a
`preferences/` wrapper: `XpManager`, `StreakManager`, `LivesManager`,
`ProPlusTimeUtil`, and `ai/AIRateLimiter` (`ai_rate_limit_prefs`) /
`ai/AILearningManager`.

### Two encodings worth knowing

**Notes** are stored as a single string with inline tags rather than one key per
element:

```
<elementCode>note text</elementCode><elementCode2>another note</elementCode2>
```

`ProgressSyncManager` parses and merges this format.

**Most-used scores** are a regex-parsed string of `id=score` pairs:

```
iso=3.2ele=1.0nuc=0.5
```

matched with `Regex("(\\w{3})=(\\d\\.\\d)")`. Note the fixed one-decimal
assumption — a score of `10.0` would not match.

## Authentication

`auth/AuthManager.kt` — a singleton over `FirebaseAuth`. **Google is the only
provider.**

```kotlin
AuthManager.buildGoogleSignInClient(activity, webClientId)
// → GoogleSignIn intent → idToken
// → GoogleAuthProvider.getCredential(idToken, null)
// → firebaseAuth.signInWithCredential(credential)
```

Exposes `isSignedIn()`, `getUid()`, `getUserDisplayName()`, `getUserEmail()`,
`signOut()`. Sign-out clears both the Firebase session and the Google client
session.

`activities/UserActivity.kt` drives the flow and supports both the legacy
`GoogleSignIn` API and the newer One Tap `Identity`/`BeginSignInRequest`.

The app never handles a password. There is no email/password provider and no
in-app account creation.

## Firestore

One document per user: `users/{uid}`.

```
users/{uid}
├── xp             : Long
├── level          : Int
├── streak         : Long
├── streakLastPlay : String   (ISO date)
├── notes          : String   (the tagged blob)
├── lastUpdated    : Timestamp (server)
├── achievements   : [ { id, title, progress, maxProgress } ]
├── statistics     : { statId: progress }     — String keys
├── chats          : [ ChatSession ]          — capped at 20
└── entitlements   : { android, windows, ultimate }
```

### This document is co-owned with the Windows app

The desktop app (`Atomic-Periodic-Table.Windows`) reads and writes the **same
document**, over the Firestore REST API. That single fact drives the design of
`sync/ProgressDocument.kt`: each platform writes only the fields it understands
and leaves everything else alone.

Windows enforces this with `updateMask.fieldPaths` on its PATCH. The equivalent
here is `SetOptions.merge()`, which touches only the keys present in the map and
**deep-merges nested maps** — which is what lets both apps write into
`entitlements` without deleting each other's key. **There must never be a bare
`set()` on this document.**

Two field names carry cross-platform constraints worth knowing before you touch
them:

- `achievements[].id` is written as a **number**. Windows reads either, but
  writing a string back would leave the type flip-flopping with whichever device
  synced last.
- `statistics` keys are **strings**, which is how Windows reads them.

`chats` is absent from `ProgressSnapshot` on purpose: `ChatHistoryManager` owns
it, and it holds maps here but strings on Windows. Leaving it out of the snapshot
is what keeps `SetOptions.merge()` from touching it.

### `entitlements`

| Key | Written by | Meaning |
|:--|:--|:--|
| `android` | this app | `"free"` / `"pro"` / `"pro+"` |
| `windows` | the desktop app | the same, for the PC |
| `ultimate` | either | PRO+ everywhere, bought once |

Each platform is the authority on its own tier and never writes the other's.
A tier is meaningless without the platform: PRO bought on Android and PRO bought
on Windows are separate purchases from separate stores.

`ultimate` is written **only when true**. A false local flag means "no Ultimate
seen" — the document has not been read yet, or the phone is offline — and writing
that over a true would revoke a purchase made on the PC.

Firestore offline persistence is enabled in `AtomicApplication`, so reads work
without a connection and writes queue.

### The merge — `sync/ProgressMerge.kt`

This is the one place in the app where a bug destroys user data irrecoverably,
so it is **pure**, has no Android imports, and is unit tested
(`app/src/test/java/com/jlindemann/science/sync/ProgressMergeTest.kt`).
`ProgressSyncManager` is transport and local read/write only.

Every rule is monotonic — a merge can only ever move a counter forward — which is
what makes it safe to run on every launch and safe to run twice.

| Field | Rule |
|:--|:--|
| `xp`, `streak` | Higher value wins |
| `level` | **Derived** from merged XP via `XpManager.getLevel`, not max of the stored levels |
| `streakLastPlay` | Later of the two **parsed** dates, independently of the streak count |
| `achievements` | Union by id; higher `progress`, higher `maxProgress`, non-blank title wins |
| `statistics` | Union by id, higher progress |
| `notes` | Union by element code, longer note wins (`sync/NotesCodec.kt`) |
| `entitlements.android` | Local wins — this platform is the authority on its own tier |
| `entitlements.windows` | Cloud wins — read and carried, never written back |
| `entitlements.ultimate` | Grant-only OR |

The rules match the Windows app's `ProgressMerge.Merge` exactly. A disagreement
would not show up as an error; it would show up as a number that flips back and
forth depending on which device synced last.

Two details that look like nitpicks and are not:

- **Dates are parsed, not compared as strings.** String ordering happens to work
  for `yyyy-MM-dd`, but it also silently "works" for malformed input, sorting a
  non-date after every real day — and that value would then be written back to
  both devices.
- **The date is independent of the streak count.** The rule used to take the date
  belonging to whichever side had the higher streak, which disagreed with Windows
  and let the last-play date flip between devices.

After merging, the result is reconciled *back into local state* — writing to
`XpManager`, `Achievement.incrementProgress`, `Statistics.incrementProgress`,
`StreakManager.setCurrentStreakWithDate`, `NotesPreference`, and (when the cloud
carries an Ultimate) `EntitlementManager.grantUltimate`.

The consequence to be aware of: this makes progress loss impossible across
devices, and makes a deliberate reset impossible too. A user who resets on one
device gets their old numbers back from the other.

### `sync/AutoSync.kt`

Signing in is the only thing the user should have to do; there is no sync button,
because a button that must be pressed for progress to be safe is a button that
will eventually not be pressed.

`AtomicApplication` observes `ProcessLifecycleOwner` and calls `syncNow` when the
app comes to the foreground and again when it leaves. `AutoSync` is
**single-flight** (a second caller returns rather than starting a competing
read-merge-write against the same document) and throttled to one unforced run
every two minutes. The explicit sync points in `UserActivity`,
`FlashCardActivity` and `FlashcardFragment` all route through it for the same
guard.

`Outcome.SKIPPED` is deliberately distinct from `Outcome.FAILED`: a caller
showing an error must not show one for a sync that never ran.

### `sync/NotesSyncManager.kt`

Notes sync specifically, debounced by 2 seconds so editing does not write per
keystroke. Gated:

```kotlin
fun canSyncNotes() = AuthManager.isSignedIn() &&
    EntitlementManager.hasAnyPro(context)
```

It writes **only** the notes field and this platform's entitlements. It used to
read the whole document first so it could pass the cloud's `xp`/`level` back into
a full write; since `SetOptions.merge()` touches only the keys present, those
fields were never at risk, and the read was a round trip on every pause in
typing.

It also does not apply anything locally, which matters: the merge rule is
"longer note wins", and running it against a note the user is in the middle of
shortening would undo their deletion as they typed.

### `ai/ChatHistoryManager.kt`

Chat sessions are stored as an **array field** on the user document
(`users/{uid}.chats`), capped at 20, rather than as a subcollection. Only for
signed-in users; otherwise history is in-memory for the session.

## What is *not* synced

Theme, units, favourites, list ordering, offline mode, search state and
most-used scores are all device-local.

PRO and PRO+ ownership is device-local too, restored from Play Billing rather
than from Firestore — they are per-store purchases, and the tier written to
`entitlements.android` is reported for display on the PC, never read back to
grant anything here. **Ultimate is the exception**: it is the one product that
crosses stores, and it *is* granted from the document.

## Testing gap

The merge and the notes codec are now covered — `ProgressMergeTest` asserts that
the merge is commutative, idempotent and monotonic, plus the entitlement and date
rules above.

Still uncovered: the preference wrappers, `AuthManager`, the transport in
`ProgressSyncManager`, `NotesSyncManager`, `AutoSync` and `ElementDataLoader`.
See [Testing](testing).
