package com.jlindemann.science.utils

import android.content.Context
import com.jlindemann.science.auth.AuthManager
import com.jlindemann.science.preferences.ProPlusVersion
import com.jlindemann.science.preferences.ProVersion
import com.jlindemann.science.preferences.UltimateVersion
import com.jlindemann.science.sync.ProgressSyncManager

/**
 * Reads and writes what the user has paid for.
 *
 * The app gates roughly a hundred call sites on `ProVersion(ctx).getValue() == 100` and
 * `ProPlusVersion(ctx).getValue() == 100`, spread across two dozen files. Rather than rewrite all
 * of them, this is the single place that decides a tier and then **writes it through** to those two
 * preferences. An Ultimate user whose Play account owns nothing therefore passes every existing
 * gate untouched.
 *
 * The decision itself lives in [EntitlementRules], which is pure and tested.
 */
object EntitlementManager {

    const val UNLOCKED = 100
    const val LOCKED = 1

    // ---- Reading -------------------------------------------------------------------------------

    fun hasUltimate(context: Context): Boolean =
        UltimateVersion(context).getValue() == UNLOCKED

    fun isProPlus(context: Context): Boolean =
        ProPlusVersion(context).getValue() == UNLOCKED

    /**
     * Any paid tier: PRO, PRO+ or Ultimate. Ultimate grants PRO+ when it is applied, but a gate
     * should not depend on that having happened.
     */
    fun hasAnyPro(context: Context): Boolean =
        ProVersion(context).getValue() == UNLOCKED || isProPlus(context) || hasUltimate(context)

    /** The tier currently stored, as the rest of the app would read it. */
    fun currentTier(context: Context): Tier = when {
        isProPlus(context) -> Tier.PRO_PLUS
        ProVersion(context).getValue() == UNLOCKED -> Tier.PRO
        else -> Tier.FREE
    }

    // ---- Writing -------------------------------------------------------------------------------

    /**
     * Store a tier, and Ultimate alongside it.
     *
     * PRO+ writes PRO too, because [EntitlementRules.fromLicences] has already folded "PRO+ implies
     * PRO" into the tier and the gates read the two preferences independently.
     *
     * **Ultimate is only ever granted here, never cleared.** A false flag means "no Ultimate seen"
     * - the account document had not been read yet, or the user is offline - and clearing on that
     * would revoke a cross-platform purchase every time the phone opened the PRO tab without a
     * network. Same reason [EntitlementRules.reconcile] exists one level up.
     */
    fun apply(context: Context, tier: Tier, ultimate: Boolean) {
        val pro = ProVersion(context)
        val proPlus = ProPlusVersion(context)

        val effective = EntitlementRules.withUltimate(tier, ultimate || hasUltimate(context))

        pro.setValue(if (effective == Tier.FREE) LOCKED else UNLOCKED)
        proPlus.setValue(if (effective == Tier.PRO_PLUS) UNLOCKED else LOCKED)

        if (ultimate) UltimateVersion(context).setValue(UNLOCKED)
    }

    /**
     * Honour an Ultimate bought on another platform.
     *
     * Called from the sync write-back when the account document carries
     * `entitlements.ultimate == true`. Grant-only, like every other rule in the merge.
     */
    fun grantUltimate(context: Context) {
        apply(context, Tier.PRO_PLUS, ultimate = true)
    }

    /**
     * Ask the account document whether this user already owns Ultimate.
     *
     * **This is what stops a second purchase.** Google Play refuses to sell a one-time product the
     * Play account already owns, but an Ultimate bought on the Microsoft Store is not a Play
     * purchase - Play cannot see it and would take the money again. The account document is the
     * only place both stores meet, so it is the only place that question can be answered.
     *
     * The local flag is checked first and trusted when set, because it is only ever set by a
     * purchase or by a document that said so - it is never a guess. When it is not set and the
     * user is signed in, the document is read before answering.
     *
     * Signed out there is nothing to ask, so this answers with what is stored. A purchase made
     * then is still safe from Play's own duplicate check; what it cannot be protected from is a
     * purchase made on the other store, which is why the caller should prefer a signed-in user.
     *
     * @param onOwned true if Ultimate is owned, on the callers' thread of the Firestore callback.
     */
    fun verifyUltimateWithAccount(context: Context, onOwned: (Boolean) -> Unit) {
        if (hasUltimate(context)) {
            onOwned(true)
            return
        }

        val uid = AuthManager.getUid()
        if (uid == null) {
            onOwned(false)
            return
        }

        ProgressSyncManager.refreshEntitlements(context, uid) {
            // refreshEntitlements has already granted locally if the document said so, so the
            // stored flag is now the answer. A failed read leaves it false, which lets the
            // purchase proceed - refusing to sell on a dropped connection would be worse.
            onOwned(hasUltimate(context))
        }
    }

    // ---- Badges --------------------------------------------------------------------------------

    /** "Ultimate" when it has been bought anywhere, otherwise this platform's own badge. */
    fun badge(context: Context): String? =
        if (hasUltimate(context)) EntitlementRules.ULTIMATE_BADGE
        else EntitlementRules.badgeFor(EntitlementRules.ANDROID_PLATFORM, currentTier(context))
}
