package com.jlindemann.science.ai.cards

import com.jlindemann.science.ai.core.ExecutionResult
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldCategory
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView

/**
 * Runtime switches, kept apart from the mapping so they can be changed without touching it.
 *
 * PRO gating is deliberately **not** here. Cards are selected for every user and locked at render
 * time by `ProCardGate`, because the upsell only works if the user can see there is something to
 * unlock — suppressing the card at selection would leave nothing to advertise, and would also mean
 * an upgrade could not take effect without recomposing the whole answer.
 *
 * @property allowNetworkCards false in offline mode. Only the emission spectrum needs the network,
 *   and Picasso fails silently, so an offline user would get an empty box with no explanation.
 */
data class ChatCardPolicy(
    val allowNetworkCards: Boolean = true
) {
    companion object { val DEFAULT = ChatCardPolicy() }
}

/**
 * Chooses the visual that goes with an answer, or none.
 *
 * **The card is derived from the typed [ExecutionResult], never from the query text.** That is what
 * keeps the picture and the prose in agreement: a card can only show something the answer has
 * already asserted, and when the engine declines there is no card at all. Deriving it from the
 * question would let the two drift apart, which is the failure this whole layer exists to avoid.
 *
 * It returns null far more often than not. A wrong card is worse than no card, so every mapping is
 * guarded by its reducer actually producing data — and a test asserts, over all 118 elements, that
 * no kind is ever emitted whose reducer then returns null.
 */
object CardSelector {

    fun select(
        result: ExecutionResult,
        plan: QueryPlan,
        store: KnowledgeStore,
        strings: StringProvider,
        localized: LocalizedView? = null,
        policy: ChatCardPolicy = ChatCardPolicy.DEFAULT
    ): ChatCard? = when (result) {
        is ExecutionResult.Property -> forField(result.element, result.fieldId, store, strings, localized)
        is ExecutionResult.Safety -> forSafety(result, strings, localized)
        is ExecutionResult.Isotopes -> forIsotopes(result, strings, localized)
        // The one card that is not drawn from the app's own numbers: the spectrum is an image on the
        // author's server, so it is the one the policy can withhold when there is no network.
        is ExecutionResult.EmissionSpectrum ->
            if (policy.allowNetworkCards) card(ChatCardKind.EMISSION_SPECTRUM, result.element, strings, localized)
            else null
        // A single-element Comparison is what a category lookup produces. More than one element and
        // any single-element card would misattribute half the answer, so those get nothing.
        is ExecutionResult.Comparison ->
            result.elements.singleOrNull()?.let { forCategory(it, result.fieldIds, store, strings, localized) }
        // A withheld answer gets the card its unlocked form would have got. This is the case the
        // whole render-time locking scheme exists for: `ProCardGate` blurs it and offers the upgrade,
        // so the user can see there is something to unlock. Suppressing it here — which is what
        // happened while `Locked` fell through to null — meant the Poisson band and the hazard
        // diamond, both PRO, were the two cards a free user could never reach by any question.
        is ExecutionResult.Locked ->
            result.element?.let { forLocked(it, result.fieldIds, store, strings, localized) }
        else -> null
    }

    /** The card that goes with one field of one element, or none. */
    private fun forField(
        element: ElementRecord,
        fieldId: String,
        store: KnowledgeStore,
        strings: StringProvider,
        localized: LocalizedView?
    ): ChatCard? = when (fieldId) {
        "electron_configuration", "electron_shells" -> electronShell(element, strings, localized)
        "crystal_structure", "lattice_constants", "space_group_name", "space_group_number" ->
            crystal(element, strings, localized)
        "poisson_ratio" -> poisson(element, store, strings, localized)
        "ionization_energy" -> ionization(element, strings, localized)
        "nfpa_health", "nfpa_flammability", "nfpa_instability", "nfpa_special" -> nfpa(element, strings, localized)
        else -> if (fieldId.startsWith("abundance_")) abundance(element, strings, localized) else null
    }

    /**
     * The card for an answer held back by the paywall.
     *
     * One gated field is a property lookup by another name, so it maps by field; several are a
     * family — the three NFPA ratings a safety question locks — and map by category.
     */
    private fun forLocked(
        element: ElementRecord,
        fieldIds: List<String>,
        store: KnowledgeStore,
        strings: StringProvider,
        localized: LocalizedView?
    ): ChatCard? =
        // Not an elvis between the two: a single field that maps to no card must stay without one
        // rather than borrowing its family's, which would illustrate a different property than the
        // one the question asked about.
        if (fieldIds.size == 1) forField(element, fieldIds.first(), store, strings, localized)
        else forCategory(element, fieldIds, store, strings, localized)

    private fun forCategory(
        element: ElementRecord,
        fieldIds: List<String>,
        store: KnowledgeStore,
        strings: StringProvider,
        localized: LocalizedView?
    ): ChatCard? {
        // The dominant family decides. A category answer is a family of fields, so the card should
        // illustrate the family rather than whichever field happened to sort first.
        val dominant = fieldIds
            .mapNotNull { FieldRegistry.byId[it]?.category }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?: return null
        return when (dominant) {
            FieldCategory.ABUNDANCE -> abundance(element, strings, localized)
            FieldCategory.CRYSTAL -> crystal(element, strings, localized)
            FieldCategory.ATOMIC -> electronShell(element, strings, localized)
            FieldCategory.SAFETY -> nfpa(element, strings, localized)
            else -> null
        }
    }

    private fun forSafety(
        result: ExecutionResult.Safety,
        strings: StringProvider,
        localized: LocalizedView?
    ): ChatCard? {
        if (!NfpaLabeller.hasAnyRating(result.nfpa)) return null
        return card(ChatCardKind.NFPA_DIAMOND, result.element, strings, localized)
    }

    private fun forIsotopes(
        result: ExecutionResult.Isotopes,
        strings: StringProvider,
        localized: LocalizedView?
    ): ChatCard? =
        IsotopeSeriesReducer.of(result.element)
            ?.let { card(ChatCardKind.ISOTOPE_DECAY, result.element, strings, localized) }

    // ---- Per-kind guards ------------------------------------------------------------------------

    private fun electronShell(e: ElementRecord, s: StringProvider, l: LocalizedView?): ChatCard? {
        val shells = (e.value("electron_shells") as? com.jlindemann.science.ai.data.FieldValue.Text)?.raw
        if (shells.isNullOrBlank() || !shells.any { it.isDigit() }) return null
        return card(ChatCardKind.ELECTRON_SHELL, e, s, l)
    }

    private fun crystal(e: ElementRecord, s: StringProvider, l: LocalizedView?): ChatCard? {
        val raw = when (val value = e.value("crystal_structure")) {
            is com.jlindemann.science.ai.data.FieldValue.Enum -> value.localized
            is com.jlindemann.science.ai.data.FieldValue.Text -> value.raw
            else -> null
        }
        // Null rather than the view's silent "Cubic" fallback: in chat the card is the answer.
        CrystalSystemResolver.resolve(raw) ?: return null
        return card(ChatCardKind.CRYSTAL_STRUCTURE, e, s, l)
    }

    private fun poisson(e: ElementRecord, store: KnowledgeStore, s: StringProvider, l: LocalizedView?): ChatCard? =
        PoissonBandReducer.of(e, store)?.let { card(ChatCardKind.POISSON_BAND, e, s, l) }

    private fun ionization(e: ElementRecord, s: StringProvider, l: LocalizedView?): ChatCard? =
        IonizationSeriesReducer.of(e)?.let { card(ChatCardKind.IONIZATION_SERIES, e, s, l) }

    private fun abundance(e: ElementRecord, s: StringProvider, l: LocalizedView?): ChatCard? =
        AbundanceReducer.of(e, s)?.let { card(ChatCardKind.ABUNDANCE, e, s, l) }

    private fun nfpa(e: ElementRecord, s: StringProvider, l: LocalizedView?): ChatCard? =
        e.nfpa?.takeIf { NfpaLabeller.hasAnyRating(it) }
            ?.let { card(ChatCardKind.NFPA_DIAMOND, e, s, l) }

    private fun card(
        kind: ChatCardKind,
        element: ElementRecord,
        strings: StringProvider,
        localized: LocalizedView?
    ): ChatCard {
        val name = localized?.name(element.key)?.takeIf { it.isNotBlank() }
            ?: element.key.replaceFirstChar { it.uppercase() }
        return ChatCard(kind = kind, elementKey = element.key, title = name)
    }
}
