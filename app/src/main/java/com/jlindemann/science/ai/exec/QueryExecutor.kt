package com.jlindemann.science.ai.exec

import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.Citation
import com.jlindemann.science.ai.core.EntityRef
import com.jlindemann.science.ai.core.ExecutionResult
import com.jlindemann.science.ai.core.Filter
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.core.ValuedElement
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.ElementRecord
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.LocalizedView
import com.jlindemann.science.ai.data.Quantity
import com.jlindemann.science.ai.data.UnitConverter

/**
 * Runs a [QueryPlan] against the index.
 *
 * Two rules run through everything here:
 *
 *  - **Absent data is reported, never guessed.** An element with no value for the requested field
 *    yields [ExecutionResult.NoData] carrying the field's coverage, so the answer can say how
 *    sparse the data is instead of printing a sentinel or drifting to another topic.
 *  - **Aggregates always disclose what they missed.** A mean over the halogens states how many
 *    of them actually had a value, rather than quietly averaging whatever was present.
 */
class QueryExecutor(
    private val store: KnowledgeStore,
    private val datasets: DatasetIndex,
    private val localized: LocalizedView?,
    private val strings: StringProvider
) {

    fun execute(plan: QueryPlan): ExecutionResult? = when (plan.intent) {
        Intent.PROPERTY_LOOKUP -> property(plan)
        Intent.CATEGORY_LOOKUP -> category(plan)
        Intent.ISOTOPES -> isotopes(plan)
        Intent.SAFETY -> safety(plan)
        Intent.COMPARISON -> comparison(plan)
        Intent.SUPERLATIVE, Intent.FILTER_LIST -> elementList(plan)
        Intent.AGGREGATE -> aggregate(plan)
        Intent.DATASET_LOOKUP -> dataset(plan)
        Intent.UNKNOWN -> null
    }

    // ---- Property ----------------------------------------------------------------------

    private fun property(plan: QueryPlan): ExecutionResult? {
        val element = plan.elementKeys.firstOrNull()?.let { store.element(it) } ?: return null
        val fieldId = plan.primaryField ?: return null
        val spec = FieldRegistry.byId[fieldId] ?: return null

        // Banked fields such as ionization energy address a specific slot.
        val slotted = plan.fieldOrdinal?.let { "$fieldId#$it" }
        val value = slotted?.let { element.values[it] } ?: element.value(fieldId)

        if (value.isMissing) {
            return ExecutionResult.NoData(fieldId, element, store.coverageOf(fieldId))
        }

        val quantity = value.asQuantity()?.let { q ->
            plan.targetUnit?.let { store.quantityIn(element, fieldId, it) ?: convert(q, it) } ?: q
        }
        return ExecutionResult.Property(
            element = element,
            fieldId = fieldId,
            quantity = quantity,
            display = render(value, quantity, plan.targetUnit),
            citations = listOf(citation(spec.id, element))
        )
    }

    // ---- Category ----------------------------------------------------------------------

    /**
     * Every populated field of one family, for one element.
     *
     * Reuses the comparison result shape with a single element, so the composer renders it as a
     * labelled list without needing a separate branch.
     */
    private fun category(plan: QueryPlan): ExecutionResult? {
        val element = plan.elementKeys.firstOrNull()?.let { store.element(it) } ?: return null
        val values = LinkedHashMap<String, List<ValuedElement>>()
        for (fieldId in plan.fieldIds) {
            val value = element.value(fieldId)
            if (value.isMissing) continue
            val quantity = store.quantityIn(element, fieldId, plan.targetUnit)
            values[fieldId] = listOf(
                ValuedElement(element, quantity, render(value, quantity, plan.targetUnit))
            )
        }
        if (values.isEmpty()) return ExecutionResult.Empty(describeFilters(plan))
        return ExecutionResult.Comparison(
            elements = listOf(element),
            fieldIds = values.keys.toList(),
            values = values,
            citations = listOf(citation(values.keys.first(), element))
        )
    }

    // ---- Isotopes and safety ------------------------------------------------------------

    /** Isotopes, longest-lived first, with stable ones ahead of everything that decays. */
    private fun isotopes(plan: QueryPlan): ExecutionResult {
        val element = plan.elementKeys.firstOrNull()?.let { store.element(it) }
            ?: return ExecutionResult.Empty(emptyList())
        val all = element.isotopes
        if (all.isEmpty()) {
            return ExecutionResult.NoData("isotopes", element, store.elements.count { it.isotopes.isNotEmpty() })
        }
        val ordered = all.sortedWith(
            compareByDescending<com.jlindemann.science.ai.data.Isotope> { it.stable }
                .thenByDescending { it.halfLifeSeconds ?: 0.0 }
        )
        return ExecutionResult.Isotopes(
            element = element,
            shown = ordered.take(plan.limit),
            total = all.size,
            stableCount = all.count { it.stable },
            citations = listOf(citation("common_neutrons", element))
        )
    }

    private fun safety(plan: QueryPlan): ExecutionResult {
        val element = plan.elementKeys.firstOrNull()?.let { store.element(it) }
            ?: return ExecutionResult.Empty(emptyList())
        val nfpa = element.nfpa
        if (nfpa == null) {
            // Radioactivity is still a real hazard answer even with no NFPA diamond recorded.
            if (element.radioactive) {
                return ExecutionResult.Safety(
                    element, com.jlindemann.science.ai.data.Nfpa(null, null, null, null),
                    radioactive = true, citations = listOf(citation("radioactive", element))
                )
            }
            return ExecutionResult.NoData("nfpa_health", element, store.coverageOf("nfpa_health"))
        }
        return ExecutionResult.Safety(
            element, nfpa, element.radioactive, listOf(citation("nfpa_health", element))
        )
    }

    // ---- Comparison --------------------------------------------------------------------

    private fun comparison(plan: QueryPlan): ExecutionResult? {
        val elements = plan.elementKeys.mapNotNull { store.element(it) }
        if (elements.size < 2) return null

        val values = LinkedHashMap<String, List<ValuedElement>>()
        for (fieldId in plan.fieldIds) {
            values[fieldId] = elements.map { element ->
                val quantity = store.quantityIn(element, fieldId, plan.targetUnit)
                ValuedElement(element, quantity, render(element.value(fieldId), quantity, plan.targetUnit))
            }
        }
        return ExecutionResult.Comparison(
            elements = elements,
            fieldIds = plan.fieldIds,
            values = values,
            citations = elements.map { citation(plan.fieldIds.firstOrNull() ?: "name", it) }
        )
    }

    // ---- Superlative and filtered list --------------------------------------------------

    private fun elementList(plan: QueryPlan): ExecutionResult {
        val candidates = plan.candidates(store)
        val fieldId = plan.sortField

        if (fieldId == null) {
            // A subset question with no property: just list what matched.
            return ExecutionResult.ElementList(
                results = candidates.map { ValuedElement(it, null, displayName(it)) },
                fieldId = null,
                matched = candidates.size,
                missing = 0,
                descending = plan.sortDescending,
                citations = emptyList()
            )
        }

        // How many elements were excluded purely because they have no value for this field.
        // A value comparison already drops them, so counting against the post-filter set would
        // always report zero and the answer could never disclose the gap.
        val subsetOnly = store.elements.filter { element ->
            plan.filters.filterNot { it is Filter.FieldCompare && it.fieldId == fieldId }
                .all { it.matches(element, store) }
        }
        val missing = subsetOnly.count { store.quantityIn(it, fieldId, plan.targetUnit) == null }

        val valued = candidates.mapNotNull { element ->
            val quantity = store.quantityIn(element, fieldId, plan.targetUnit) ?: return@mapNotNull null
            ValuedElement(element, quantity, render(element.value(fieldId), quantity, plan.targetUnit))
        }

        if (valued.isEmpty()) {
            return if (subsetOnly.isEmpty()) ExecutionResult.Empty(describeFilters(plan))
            else ExecutionResult.NoData(fieldId, null, store.coverageOf(fieldId))
        }

        val sorted = if (plan.sortDescending) valued.sortedByDescending { it.quantity!!.mid }
        else valued.sortedBy { it.quantity!!.mid }

        return ExecutionResult.ElementList(
            results = sorted.take(plan.limit),
            fieldId = fieldId,
            matched = candidates.size,
            missing = missing,
            descending = plan.sortDescending,
            citations = sorted.take(plan.limit).map { citation(fieldId, it.element) }
        )
    }

    // ---- Aggregate ----------------------------------------------------------------------

    private fun aggregate(plan: QueryPlan): ExecutionResult {
        val explicit = plan.elementKeys.mapNotNull { store.element(it) }
        val candidates = if (explicit.size > 1) explicit else plan.candidates(store)

        if (plan.aggregation == Aggregation.COUNT) {
            return ExecutionResult.Aggregate(
                aggregation = Aggregation.COUNT,
                fieldId = plan.primaryField.orEmpty(),
                value = candidates.size.toDouble(),
                unit = null,
                contributors = candidates.map { ValuedElement(it, null, displayName(it)) },
                missing = 0,
                citations = emptyList()
            )
        }

        val fieldId = plan.primaryField ?: return ExecutionResult.Empty(describeFilters(plan))
        val valued = candidates.mapNotNull { element ->
            val quantity = store.quantityIn(element, fieldId, plan.targetUnit) ?: return@mapNotNull null
            ValuedElement(element, quantity, render(element.value(fieldId), quantity, plan.targetUnit))
        }
        if (valued.isEmpty()) return ExecutionResult.NoData(fieldId, null, store.coverageOf(fieldId))

        val numbers = valued.map { it.quantity!!.mid }.sorted()
        val value = when (plan.aggregation) {
            Aggregation.AVG -> numbers.average()
            Aggregation.SUM -> numbers.sum()
            Aggregation.MIN -> numbers.first()
            Aggregation.MAX -> numbers.last()
            Aggregation.MEDIAN ->
                if (numbers.size % 2 == 1) numbers[numbers.size / 2]
                else (numbers[numbers.size / 2 - 1] + numbers[numbers.size / 2]) / 2.0
            Aggregation.RANGE -> numbers.last() - numbers.first()
            else -> numbers.average()
        }

        return ExecutionResult.Aggregate(
            aggregation = plan.aggregation,
            fieldId = fieldId,
            value = value,
            unit = valued.first().quantity?.unit,
            contributors = valued,
            // Disclosed in the answer, so a partial mean is never presented as a complete one.
            missing = candidates.size - valued.size,
            citations = listOf(citation(fieldId, valued.first().element))
        )
    }

    // ---- Dataset ------------------------------------------------------------------------

    private fun dataset(plan: QueryPlan): ExecutionResult? {
        val ref = plan.entities.filterIsInstance<EntityRef.DatasetRow>().firstOrNull() ?: return null
        val row = datasets.row(ref.dataset, ref.id) ?: return null
        return ExecutionResult.Dataset(
            row = row,
            citations = listOf(Citation(row.title, ref.dataset, row.deepLink, mapOf("id" to row.id)))
        )
    }

    // ---- Rendering helpers ----------------------------------------------------------------

    /** The element's name in the active language, falling back to its English key. */
    fun displayName(element: ElementRecord): String =
        localized?.name(element.key)?.takeIf { it.isNotBlank() }
            ?: element.key.replaceFirstChar { it.uppercase() }

    /**
     * How a value is shown. A value read straight from the data prints exactly as authored;
     * only a converted value is reformatted, so nothing is lost to rounding on a plain lookup.
     */
    private fun render(
        value: com.jlindemann.science.ai.data.FieldValue,
        quantity: Quantity?,
        targetUnit: String?
    ): String = when {
        targetUnit != null && quantity != null ->
            UnitConverter.formatValue(quantity.value) + (quantity.unit?.let { " $it" } ?: "")
        quantity != null -> quantity.display
        value is com.jlindemann.science.ai.data.FieldValue.Text -> value.raw
        value is com.jlindemann.science.ai.data.FieldValue.Enum -> value.localized
        value is com.jlindemann.science.ai.data.FieldValue.Trace -> strings.get(com.jlindemann.science.R.string.ai_abundance_relative)
        value is com.jlindemann.science.ai.data.FieldValue.Struct ->
            value.parts.entries.joinToString(", ") { "${it.key}: ${it.value.display}" }
        else -> ""
    }

    private fun convert(quantity: Quantity, targetUnit: String): Quantity =
        UnitConverter.convert(quantity, targetUnit) ?: quantity

    private fun citation(fieldId: String, element: ElementRecord): Citation {
        val spec = FieldRegistry.byId[fieldId]
        val label = spec?.let { runCatching { strings.get(it.labelRes) }.getOrNull() } ?: fieldId
        return Citation(
            label = label.replace(":", "").trim(),
            source = displayName(element),
            deepLink = spec?.deepLink ?: com.jlindemann.science.ai.data.DeepLinkTarget.ELEMENT_INFO,
            args = mapOf("key" to element.key)
        )
    }

    private fun describeFilters(plan: QueryPlan): List<String> =
        plan.filters.map { it::class.simpleName ?: "filter" }
}
