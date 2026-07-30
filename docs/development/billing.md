---
title: Billing
parent: Developer Guide
nav_order: 7
---

# Billing

**Class:** `utils/BillingManager.kt` — note the file sits in `utils/` but
declares `package com.jlindemann.science.billing`.

Google Play Billing 8.0.0 (`com.android.billingclient:billing-ktx`), with the
`com.android.vending.BILLING` permission in the manifest.

## Products

Three **one-time (INAPP) purchases**, not subscriptions:

| Product ID | Grants |
|:--|:--|
| `pro_version` | PRO |
| `pro_version_plus` | PRO+ (and PRO) |
| `pro_version_plus_upgrade` | PRO+ for existing PRO owners |

Owning either PRO+ product implies PRO.

## Flow

On connect, `BillingManager`:

1. Queries `ProductDetails` for all three IDs
2. Queries existing `Purchase`s
3. Acknowledges anything unacknowledged (Play refunds unacknowledged purchases
   after three days, so this is not optional)
4. Derives `ownsProVersion` and `ownsProPlusVersion`

Results are delivered through a `Listener` interface:

```kotlin
interface Listener {
    fun onProductsUpdated(...)
    fun onPurchasesUpdated(...)
    fun onPurchaseCompleted(...)
    fun onError(...)
}
```

`activities/settings/ProActivity.kt` implements it as the purchase UI.
`fragments/ProFragment.kt` is the same content hosted as a bottom-nav tab.
`utils/ProUpgradeDialogFragment.kt` is the inline upsell dialog.

## Entitlement storage

Ownership is cached in SharedPreferences as an `Int`:

| Value | Meaning |
|:--:|:--|
| `1` | Not owned |
| `100` | Owned |

via `preferences/ProVersion.kt` and `preferences/ProPlusVersion.kt`. This is why
every gate in the codebase reads:

```kotlin
if (ProVersion(context).getValue() != 100) {
    (activity as? BaseActivity)?.goToProPage()
    return
}
```

Caching locally means PRO features keep working offline once purchased.

## Where gates are applied

| Surface | Check | Location |
|:--|:--|:--|
| Reference tables | `ProVersion != 100` | `TablesFragment.onTableItemClick` |
| Ideal gas tool | `ProPlusVersion != 100 && isBeforeDeadline` | `ToolsFragment.onToolItemClick` |
| Element fields | `Tier.PRO` on the `FieldSpec` | `InfoExtension`, `ai/core/Entitlements` |
| Notes sync | PRO or PRO+ | `NotesSyncManager.canSyncNotes` |
| AI daily messages | Tier-dependent limit | `ai/AIRateLimiter` |

### The AI engine mirrors the gate

`ai/data/FieldRegistry.kt` tags each `FieldSpec` with a `Tier`
(`FREE`, `PRO`, `PRO_PLUS`), and `ai/core/Entitlements.kt` checks it **before
the value is read** in `QueryExecutor`. That ordering matters: a locked field
returns `ExecutionResult.Locked` and the value never enters the answer text.

Adding a new PRO field means setting `tier = Tier.PRO` in the registry — the
assistant then gates it automatically.

### The expired PRO+ gate

`utils/ProPlusTimeUtil.isBeforeJanuary2026()` was a time-limited free-trial
window for the ideal gas calculator. The condition is
`proPlusValue != 100 && isBeforeDeadline`, so once the date passed the gate
stopped firing and the tool became free for everyone.

The code is still present and still evaluated. It is dead in effect, not in
form. `app/src/test/java/com/jlindemann/science/utils/ProPlusTimeUtilTest.kt`
covers it.

## Adding a gated feature

1. Decide the tier and pick the right preference class.
2. Check `getValue() == 100` at the entry point, not deep inside.
3. On failure call `goToProPage()` from `BaseActivity` — do not build a custom
   upsell.
4. If it is an element field, set `tier` on its `FieldSpec` so the assistant
   gates it too.
5. Add it to the tier table in [PRO tiers](../user-guide/pro-tiers).

## Testing

`BillingManager` has no unit tests. Play Billing needs a real Play connection,
so testing means a signed build with Play Console licence testers configured.
