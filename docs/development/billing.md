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

Five **one-time (INAPP) purchases**, not subscriptions:

| Product ID | Grants |
|:--|:--|
| `pro_version` | PRO |
| `pro_version_plus` | PRO+ (and PRO) |
| `pro_version_plus_upgrade` | PRO+ for existing PRO owners |
| `pro_plus_ultimate` | PRO+ on **every** platform (and PRO+, and PRO) |
| `pro_plus_ultimate_upgrade` | the same, priced for existing PRO+ owners |

Owning any PRO+ product implies PRO; owning either Ultimate product implies
PRO+. `EntitlementRules.fromLicences` folds that in, because buying the top tier
outright means owning none of the products below it — a gate reading only its
own flag would lock out the user who paid the most.

### Ultimate

Ultimate is the one product that crosses stores. It is bought once, on whichever
platform the user happens to be holding, and the shared account document carries
it to the others as `entitlements.ultimate`. See
[Persistence and sync](persistence-and-sync).

It is **grant-only**: a missing or false flag means "no Ultimate seen", which is
not the same as "Ultimate revoked". The Windows app applies the identical rule.

Both Ultimate products are live in the Play Console, so `queryProductDetails`
returns them and the card shows real prices. The disabled *Coming soon* state
remains as the fallback for a region or build where the catalogue comes back
without them.

### Ultimate cannot be bought twice

Google Play refuses to sell a one-time product the **Play account** already owns,
and `ITEM_ALREADY_OWNED` is now reconciled by re-querying purchases rather than
surfaced as an error code — telling a user who owns the thing that their purchase
failed is the wrong answer.

That check does not cover Ultimate. An Ultimate bought on the Microsoft Store is
not a Play purchase: Play cannot see it and would take the money again. The
account document is the only place the two stores meet, so it is what gets asked.

`EntitlementManager.verifyUltimateWithAccount` is the gate:

1. The stored flag is trusted when set — it is only ever set by a purchase or by a
   document that said so, never by a guess.
2. Otherwise, if signed in, `ProgressSyncManager.refreshEntitlements` reads the
   document and grants before answering.
3. Signed out there is nothing to ask, so the stored value is the answer.

Both paywall screens call it twice: once when the page opens, so the button is
not live for somebody who already owns Ultimate during the window before the
first sync finishes, and again on tap, with the button disabled in between so a
second tap cannot start a second purchase.

A failed read answers "not owned" and lets the purchase proceed. Refusing to sell
on a dropped connection would be the worse failure, and Play's own duplicate check
still backs it up for anything bought on this store.

**The residual gap is a signed-out user.** Nothing can be verified without an
account, so somebody who owns Ultimate on a PC, is signed out on their phone, and
buys it again on Play will be charged twice. Requiring sign-in before selling
Ultimate would close it, at the cost of putting a sign-in in front of a purchase.

### Purchases are bound to the account

`launchPurchase` sets Play's `obfuscatedAccountId` to a SHA-256 of the Firebase
uid (64 hex characters, exactly the field's limit) whenever the user is signed
in. Hashed rather than raw, as Play's documentation asks.

This matters specifically for Ultimate: it is sold as one purchase for one
account across two stores, and a purchase that does not say which account it
belongs to cannot be checked against one later. The binding is what will let the
planned server-side verification match a purchase token to the uid claiming it,
instead of trusting the client's word.

**It only works going forward.** A purchase made before the binding existed can
never be bound retroactively, which is why it went in alongside the products
rather than with the verification that will eventually use it.

`app/src/test/java/com/jlindemann/science/billing/ObfuscatedAccountIdTest.kt`
pins the digest, because changing it later would orphan every purchase made
under the old one.

## Flow

On connect, `BillingManager`:

1. Queries `ProductDetails` for all three IDs
2. Queries existing `Purchase`s
3. Acknowledges anything unacknowledged (Play refunds unacknowledged purchases
   after three days, so this is not optional)
4. Derives `ownsProVersion`, `ownsProPlusVersion` and `ownsUltimate`, and sets
   `storeAnswered`

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

via `preferences/ProVersion.kt`, `preferences/ProPlusVersion.kt` and
`preferences/UltimateVersion.kt`. This is why every gate in the codebase reads:

```kotlin
if (ProVersion(context).getValue() != 100) {
    (activity as? BaseActivity)?.goToProPage()
    return
}
```

Caching locally means PRO features keep working offline once purchased.

### One place decides, ~100 places read

`utils/EntitlementRules.kt` (pure, unit tested) decides the tier;
`utils/EntitlementManager.kt` stores it and **writes through** to the PRO and
PRO+ preferences. The roughly one hundred existing `getValue() == 100` gates were
deliberately left alone — a user whose PRO+ came from an Ultimate bought on the
PC has no Play purchase at all, and the write-through is what lets every one of
those gates keep working unchanged.

New code should prefer `EntitlementManager.hasAnyPro(context)` /
`.isProPlus(context)` / `.hasUltimate(context)` over reading the preferences.

### A licence check may grant, and may only revoke when it knows

`EntitlementRules.reconcile(stored, answer)` takes a **nullable** answer, where
null means Play has not replied — the query failed, the connection dropped, or it
simply has not come back yet.

This is not a hypothetical. `ProFragment.updateProOptionsUI` runs on view
creation, before the purchase query returns, and its old `else` branch wrote
`setValue(1)` unconditionally. A paying user who opened the PRO tab offline lost
what they had bought. `BillingManager.hasStoreAnswered()` is what makes the two
cases distinguishable, and
`app/src/test/java/com/jlindemann/science/utils/EntitlementRulesTest.kt` has the
regression test.

The asymmetry is deliberate: leaving a refunded user with access until the next
definite answer is a rounding error, while locking a paying customer out of what
they bought is the worst thing this code can do.

## Where gates are applied

| Surface | Check | Location |
|:--|:--|:--|
| Reference tables | `ProVersion != 100` | `TablesFragment.onTableItemClick` |
| Ideal gas tool | `ProPlusVersion != 100 && isBeforeDeadline` | `ToolsFragment.onToolItemClick` |
| Element fields | `Tier.PRO` on the `FieldSpec` | `InfoExtension`, `ai/core/Entitlements` |
| Notes sync | any paid tier | `NotesSyncManager.canSyncNotes` |
| Progress sync | any paid tier | `AutoSync.canSync` |
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

1. Decide the tier.
2. Check `EntitlementManager.hasAnyPro(context)` (or `.isProPlus`) at the entry
   point, not deep inside.
3. On failure call `goToProPage()` from `BaseActivity` — do not build a custom
   upsell.
4. If it is an element field, set `tier` on its `FieldSpec` so the assistant
   gates it too.
5. Add it to the tier table in [PRO tiers](../user-guide/pro-tiers).

## Testing

`EntitlementRules` is covered by
`app/src/test/java/com/jlindemann/science/utils/EntitlementRulesTest.kt` — the
tier arithmetic, the reconcile rule and the Ultimate grant are all pure and
testable without Play.

`BillingManager` itself still has no unit tests. Play Billing needs a real Play
connection, so testing it means a signed build with Play Console licence testers
configured. The debug build cannot transact.
