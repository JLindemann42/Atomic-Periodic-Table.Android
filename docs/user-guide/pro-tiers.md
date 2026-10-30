---
title: PRO tiers
parent: User Guide
nav_order: 10
---

# PRO tiers

**Classes:** `activities/settings/ProActivity.kt` (purchase screen),
`utils/BillingManager.kt` (Play Billing)

Five one-time purchases, not subscriptions:

| Product ID | What it is |
|:--|:--|
| `pro_version` | PRO |
| `pro_version_plus` | PRO+ |
| `pro_version_plus_upgrade` | Upgrade path from PRO to PRO+ |
| `pro_plus_ultimate` | PRO+ Ultimate |
| `pro_plus_ultimate_upgrade` | Upgrade path from PRO+ to Ultimate |

Owning PRO+ (or the upgrade) also grants everything PRO grants, and owning
Ultimate grants everything PRO+ grants.

## What each tier unlocks

| | Free | PRO | PRO+ | Ultimate |
|:--|:--:|:--:|:--:|:--:|
| Periodic table, search, element pages | ✅ | ✅ | ✅ | ✅ |
| Free reference tables (6) | ✅ | ✅ | ✅ | ✅ |
| Molar mass, unit converter, dictionary | ✅ | ✅ | ✅ | ✅ |
| Learning games, XP, streaks, achievements | ✅ | ✅ | ✅ | ✅ |
| Home-screen widgets | ✅ | ✅ | ✅ | ✅ |
| **PRO reference tables** (6) | — | ✅ | ✅ | ✅ |
| **PRO element fields** | — | ✅ | ✅ | ✅ |
| **Cloud progress and notes sync** | — | ✅ | ✅ | ✅ |
| **AI messages per day** | 16 | 64 | Unlimited | Unlimited |
| **PRO+ on Windows as well** | — | — | — | ✅ |

### PRO+ Ultimate

Ultimate is not a fourth tier of features — it is PRO+ on **every** platform at
once. Buy it once, on Android or on Windows, and both apps unlock PRO+ for the
Google account you are signed into. If you already own PRO+ here, the PRO page
offers a cheaper upgrade price instead of the full one.

It is the only entitlement that travels through your account. PRO and PRO+ are
purchases from a specific store, so PRO+ bought on Android does not unlock the
Windows app, and vice versa.

Ultimate is granted and never revoked by sync: if the app cannot reach your
account document, it keeps whatever it already knew rather than assuming you no
longer own it.

### PRO reference tables

Poisson's ratio, nuclide chart, physical constants, geology, emission spectra
and alloys. See [Reference tables](reference-tables).

### PRO element fields

Fields on the [element detail](element-detail) page that need PRO:

- **Mechanical** — Young's, bulk and shear moduli, Poisson's ratio, Mohs /
  Vickers / Brinell hardness, speed of sound in solid, liquid and gas
- **Crystal** — space group name and number
- **Electromagnetic** — Curie point, Néel point, refractive index
- **Atomic** — electron affinity
- **Safety** — NFPA 704 health, flammability and instability ratings

The same gating applies in the AI assistant. Asking a free account for Vickers
hardness returns an upgrade prompt rather than the value — the entitlement is
checked before the value is read, in `ai/core/Entitlements.kt`.

### The ideal gas calculator

This tool carried a PRO+ gate that was written to expire on a fixed date
(`utils/ProPlusTimeUtil.isBeforeJanuary2026()`). That date has passed, so the
gate no longer has any effect and the tool is available to everyone.

## How ownership is tracked

`BillingManager` queries Play Billing on connect for both product details and
existing purchases, acknowledges any unacknowledged purchase, and derives its
ownership flags. `utils/EntitlementManager.kt` turns those into a tier and
persists it in `preferences/ProVersion.kt`, `preferences/ProPlusVersion.kt` and
`preferences/UltimateVersion.kt` as an integer — `1` for not owned, `100` for
owned — which is why every gate in the codebase reads `getValue() == 100`.

Because ownership is cached locally, PRO features remain available offline once
purchased. A failed or not-yet-returned Play query never revokes anything: the
app only downgrades a tier when Play definitely answers, which is what stops a
paying user losing access on a device with no connection.

## Restoring a purchase

Purchases are tied to your Google Play account. Reinstalling or moving to a new
device restores them automatically the next time `BillingManager` connects.

Ultimate additionally restores from your **Atomic account** — sign in with the
same Google account and the next sync grants it, even on a device whose Play
account has no purchase on it (a Windows machine, for instance).
