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
├── statistics     : { statId: progress }
└── chats          : [ ChatSession ]  — capped at 20
```

Writes use `SetOptions.merge()`, so a partial write never clears other fields.

Firestore offline persistence is enabled in `BaseActivity`, so reads work
without a connection and writes queue.

### `sync/ProgressSyncManager.kt`

`saveFullProgressToCloud` writes; `mergeAndUploadLocalProgress` is the
interesting one.

The merge is **take-the-maximum per field**:

| Field | Rule |
|:--|:--|
| XP, level, streak | Higher value wins |
| Achievements | Higher `progress` per achievement id |
| Statistics | Higher progress per stat id |
| Notes | Longer note wins, per element |

After merging, the result is reconciled *back into local state* — writing to
`XpManager`, `Achievement.incrementProgress`, `Statistics.incrementProgress`,
`StreakManager.setCurrentStreakWithDate` and `NotesPreference`.

The consequence to be aware of: this makes progress loss impossible across
devices, and makes a deliberate reset impossible too. A user who resets on one
device gets their old numbers back from the other.

### `sync/NotesSyncManager.kt`

Notes sync specifically, debounced by 2 seconds so editing does not write per
keystroke. Gated:

```kotlin
fun canSyncNotes() = AuthManager.isSignedIn() &&
    (ProVersion(ctx).getValue() == 100 || ProPlusVersion(ctx).getValue() == 100)
```

Before writing it re-reads cloud `xp`/`level` so a notes write cannot clobber
progress.

### `ai/ChatHistoryManager.kt`

Chat sessions are stored as an **array field** on the user document
(`users/{uid}.chats`), capped at 20, rather than as a subcollection. Only for
signed-in users; otherwise history is in-memory for the session.

## What is *not* synced

Theme, units, favourites, list ordering, offline mode, search state, most-used
scores, and PRO ownership are all device-local. PRO ownership is restored from
Play Billing rather than from Firestore.

## Testing gap

None of this layer has unit tests — no coverage for the preference wrappers,
`AuthManager`, either sync manager, or `ElementDataLoader`. The merge logic in
`ProgressSyncManager` in particular is intricate enough to deserve some. See
[Testing](testing).
