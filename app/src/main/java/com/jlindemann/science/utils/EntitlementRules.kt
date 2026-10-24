package com.jlindemann.science.utils

/**
 * What a platform's store has granted.
 *
 * [wire] is the form that goes into the shared account document, so it is a stable identifier
 * rather than anything a user sees. Renaming one silently forks the two apps' data.
 */
enum class Tier(val wire: String) {
    FREE("free"),
    PRO("pro"),
    PRO_PLUS("pro+"),
}

/**
 * Turns store purchases and cloud grants into a tier.
 *
 * Pure and free of Android imports, because the rule that matters here cannot be tested through
 * BillingClient: reproducing "Play answered, but knows nothing about this installation" needs a
 * sideloaded build, a device with no Play account, or no network. It is a port of the Windows
 * app's EntitlementRules and must stay in step with it - see
 * Atomic-Periodic-Table.Windows/src/Atomic.Core/Licensing/Entitlement.cs.
 */
object EntitlementRules {

    /**
     * The platform this build's store purchases belong to.
     *
     * A tier is meaningless without it. PRO bought on Android and PRO bought on Windows are
     * separate purchases from separate stores, and the shared account document has to be able to
     * say which one it is describing.
     */
    const val ANDROID_PLATFORM = "android"

    const val WINDOWS_PLATFORM = "windows"

    /** The badge for Ultimate, which is not a platform tier - it is every platform at once. */
    const val ULTIMATE_BADGE = "Ultimate"

    /**
     * The product's full name, untranslated: what an Ultimate owner is told they own.
     *
     * Matches the Windows app's `EntitlementRules.UltimateProductName` and the `ultimate_version`
     * resource, which are the same literal for the same reason.
     */
    const val ULTIMATE_PRODUCT_NAME = "PRO+ Ultimate"

    /** A tier as a product name: "PRO", "PRO+", or null for free. */
    fun tierName(tier: Tier): String? = when (tier) {
        Tier.PRO_PLUS -> "PRO+"
        Tier.PRO -> "PRO"
        Tier.FREE -> null
    }

    /**
     * The one-badge summary of what the user owns: "PRO+ Ultimate" when they have it, otherwise
     * this platform's tier - "Android PRO+" - or null when free.
     *
     * Ultimate first. It grants PRO+ here, so an Ultimate owner's Android tier is PRO+ too, and
     * labelling them "Android PRO+" names a purchase they never made while hiding the one they did.
     */
    fun summary(ultimate: Boolean, platformTier: Tier): String? =
        if (ultimate) ULTIMATE_PRODUCT_NAME else badgeFor(ANDROID_PLATFORM, platformTier)

    /** Parse a wire tier, treating anything unrecognised as free. */
    fun fromWire(wire: String?): Tier = when (wire) {
        Tier.PRO_PLUS.wire -> Tier.PRO_PLUS
        Tier.PRO.wire -> Tier.PRO
        else -> Tier.FREE
    }

    /**
     * What a set of purchases means.
     *
     * **PRO+ implies PRO.** Buying PRO+ outright means owning neither the plain PRO product nor the
     * PRO-to-PRO+ upgrade, so reading PRO from its own purchase alone would leave those users
     * outside every PRO gate in the app.
     */
    fun fromLicences(
        hasPro: Boolean,
        hasProPlus: Boolean,
        hasProUpgrade: Boolean,
        hasUltimate: Boolean = false,
    ): Tier {
        val isProPlus = hasProPlus || hasProUpgrade || hasUltimate
        return when {
            isProPlus -> Tier.PRO_PLUS
            hasPro -> Tier.PRO
            else -> Tier.FREE
        }
    }

    /**
     * Combine what is stored with what the store said.
     *
     * [answer] is null when Play had nothing to give - the query failed, the connection dropped, or
     * it has not come back yet.
     *
     * **A licence check may grant, and may only revoke when it actually knows.** A query that
     * succeeds and returns no purchases is indistinguishable from one against an installation Play
     * has never heard of. Treating that silence as "not owned" is what made a paying user's PRO
     * disappear the moment they opened the PRO tab.
     *
     * The asymmetry is deliberate and it is not symmetric in cost: leaving a refunded user with
     * access until the next definite answer is a rounding error, while locking a paying customer
     * out of what they bought is the worst thing this code can do.
     */
    fun reconcile(stored: Tier, answer: Tier?): Tier = answer ?: stored

    /**
     * Raise a tier to PRO+ when Ultimate has been bought on any platform.
     *
     * Ultimate is the one product that crosses stores. It is bought once, on whichever platform the
     * user happens to be holding, and the account document carries it to the others - so this only
     * ever grants. A missing or false flag means "no Ultimate seen", which is not the same as
     * "Ultimate revoked", and the local Play purchase remains the authority for this platform's own
     * tier.
     */
    fun withUltimate(platformTier: Tier, ultimate: Boolean): Tier =
        if (ultimate) Tier.PRO_PLUS else platformTier

    /**
     * The badge for a platform's tier - "Windows PRO+", "Android PRO" - or null when free.
     *
     * Named by platform because the tier alone is ambiguous across two stores. A user who owns PRO+
     * on their PC and nothing here, and one who owns PRO+ here, are the same word without it, and
     * they are entitled to different things.
     *
     * Composed rather than translated: "Windows", "Android", "PRO" and "PRO+" all stay in Latin
     * script in every language the app ships.
     */
    fun badgeFor(platform: String, tier: Tier): String? {
        if (tier == Tier.FREE) return null

        val name = when (platform) {
            WINDOWS_PLATFORM -> "Windows"
            ANDROID_PLATFORM -> "Android"
            else -> platform
        }

        return name + " " + if (tier == Tier.PRO_PLUS) "PRO+" else "PRO"
    }
}
