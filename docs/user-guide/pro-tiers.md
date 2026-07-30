---
title: PRO tiers
parent: User Guide
nav_order: 10
---

# PRO tiers

**Classes:** `activities/settings/ProActivity.kt` (purchase screen),
`utils/BillingManager.kt` (Play Billing)

Three one-time purchases, not subscriptions:

| Product ID | What it is |
|:--|:--|
| `pro_version` | PRO |
| `pro_version_plus` | PRO+ |
| `pro_version_plus_upgrade` | Upgrade path from PRO to PRO+ |

Owning PRO+ (or the upgrade) also grants everything PRO grants.

## What each tier unlocks

| | Free | PRO | PRO+ |
|:--|:--:|:--:|:--:|
| Periodic table, search, element pages | ✅ | ✅ | ✅ |
| Free reference tables (6) | ✅ | ✅ | ✅ |
| Molar mass, unit converter, dictionary | ✅ | ✅ | ✅ |
| Learning games, XP, streaks, achievements | ✅ | ✅ | ✅ |
| Home-screen widgets | ✅ | ✅ | ✅ |
| Cloud progress sync | ✅ | ✅ | ✅ |
| **PRO reference tables** (6) | — | ✅ | ✅ |
| **PRO element fields** | — | ✅ | ✅ |
| **Cloud notes sync** | — | ✅ | ✅ |
| **AI messages per day** | 16 | 64 | Unlimited |

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
existing purchases, acknowledges any unacknowledged purchase, and derives two
booleans. Those are persisted locally in `preferences/ProVersion.kt` and
`preferences/ProPlusVersion.kt` as an integer — `1` for not owned, `100` for
owned — which is why every gate in the codebase reads
`getValue() == 100`.

Because ownership is cached locally, PRO features remain available offline once
purchased.

## Restoring a purchase

Purchases are tied to your Google Play account. Reinstalling or moving to a new
device restores them automatically the next time `BillingManager` connects.
