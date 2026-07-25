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
        Intent.FORMULA_MASS -> formulaMass(plan)
        Intent.NUCLIDE_COUNT -> nuclide(plan)
        Intent.ISOTOPE_COMPARISON -> isotopeComparison(plan)
        Intent.COMPARATIVE -> comparative(plan)
        Intent.NEIGHBOUR -> neighbour(plan)
        Intent.MOLE_CONVERSION -> moleConversion(plan)
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
        val ranking = quantity?.let { rankOf(element, fieldId, it) }
        return ExecutionResult.Property(
            element = element,
            fieldId = fieldId,
            quantity = quantity,
            display = render(value, quantity, plan.targetUnit),
            citations = listOf(citation(spec.id, element)),
            rank = ranking?.first,
            rankedOutOf = ranking?.second ?: 0
        )
    }

    /**
     * Where an element sits among all elements that have a value for a field, 1 being highest.
     *
     * A bare figure carries little meaning on its own — 19.3 g/cm³ says nothing unless you
     * already know the range. Only computed for numeric fields where ordering is meaningful:
     * ranking atomic numbers or CAS registry numbers would be noise.
     */
    private fun rankOf(
        element: ElementRecord,
        fieldId: String,
        quantity: com.jlindemann.science.ai.data.Quantity
    ): Pair<Int, Int>? {
        val spec = FieldRegistry.byId[fieldId] ?: return null
        if (spec.kind != com.jlindemann.science.ai.data.FieldKind.NUMERIC) return null
        if (fieldId in UNRANKABLE_FIELDS) return null
        val values = store.elements.mapNotNull { it.quantity(fieldId)?.mid }
        if (values.size < MIN_FOR_RANK) return null
        val rank = values.count { it > quantity.mid } + 1
        return rank to values.size
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

    // ---- Comparatives and neighbours --------------------------------------------------------

    private fun comparative(plan: QueryPlan): ExecutionResult? {
        val elements = plan.elementKeys.mapNotNull { store.element(it) }
        if (elements.size < 2) return null
        val fieldId = plan.primaryField ?: return null
        val first = store.quantityIn(elements[0], fieldId, plan.targetUnit)
            ?: return ExecutionResult.NoData(fieldId, elements[0], store.coverageOf(fieldId))
        val second = store.quantityIn(elements[1], fieldId, plan.targetUnit)
            ?: return ExecutionResult.NoData(fieldId, elements[1], store.coverageOf(fieldId))

        // The subject of the question is the element named first; "is gold denser than lead"
        // holds when gold is on the winning side of the comparison the adjective describes.
        val firstWins = if (plan.sortDescending) first.mid > second.mid else first.mid < second.mid
        val winner = if (firstWins) elements[0] else elements[1]
        val loser = if (firstWins) elements[1] else elements[0]
        val winnerValue = if (firstWins) first else second
        val loserValue = if (firstWins) second else first

        return ExecutionResult.Comparative(
            winner = winner,
            loser = loser,
            fieldId = fieldId,
            winnerValue = winnerValue,
            loserValue = loserValue,
            claimHolds = if (plan.limit == 1) firstWins else null,
            ratio = if (plan.limit == 3 && loserValue.mid != 0.0) {
                winnerValue.mid / loserValue.mid
            } else null,
            citations = elements.map { citation(fieldId, it) }
        )
    }

    private fun neighbour(plan: QueryPlan): ExecutionResult? {
        val keys = plan.elementKeys
        if (keys.size < 2) return null
        val from = store.element(keys[0]) ?: return null
        val to = store.element(keys[1]) ?: return null
        return ExecutionResult.Neighbour(from, to, plan.limit > 0, listOf(citation("atomic_number", to)))
    }

    // ---- Calculations ---------------------------------------------------------------------

    private fun formulaMass(plan: QueryPlan): ExecutionResult? {
        val result = com.jlindemann.science.ai.data.ChemistryMath
            .parseFormula(plan.rawQuery) { symbol -> store.bySymbol(symbol)?.quantity("atomic_mass")?.value }
            ?: return null
        return ExecutionResult.Formula(
            result = result,
            wantsComposition = "composition" in plan.fieldIds,
            citations = result.parts.mapNotNull { part ->
                store.bySymbol(part.symbol)?.let { citation("atomic_mass", it) }
            }.take(3)
        )
    }

    private fun nuclide(plan: QueryPlan): ExecutionResult? {
        val element = plan.elementKeys.firstOrNull()?.let { store.element(it) } ?: return null
        val massNumber = plan.limit
        val neutrons = com.jlindemann.science.ai.data.ChemistryMath
            .neutronsIn(massNumber, element.atomicNumber) ?: return null
        return ExecutionResult.Nuclide(
            element = element,
            massNumber = massNumber,
            protons = element.atomicNumber,
            neutrons = neutrons,
            citations = listOf(citation("atomic_number", element))
        )
    }

    private fun isotopeComparison(plan: QueryPlan): ExecutionResult? {
        val refs = plan.entities.filterIsInstance<EntityRef.Nuclide>()
        if (refs.size < 2) return null
        val left = nuclideFacts(refs[0]) ?: return null
        val right = nuclideFacts(refs[1]) ?: return null
        return ExecutionResult.IsotopeComparison(
            left, right,
            listOf(citation("common_neutrons", left.element), citation("common_neutrons", right.element))
                .distinctBy { it.source }
        )
    }

    /**
     * Everything known about one nuclide.
     *
     * Proton and neutron counts are always derivable from the element and the mass number; the
     * decay mode and half-life come from the isotope table when that nuclide is listed there.
     */
    private fun nuclideFacts(ref: EntityRef.Nuclide): ExecutionResult.NuclideFacts? {
        val element = store.element(ref.key) ?: return null
        val neutrons = com.jlindemann.science.ai.data.ChemistryMath
            .neutronsIn(ref.massNumber, element.atomicNumber) ?: return null
        return ExecutionResult.NuclideFacts(
            element = element,
            massNumber = ref.massNumber,
            protons = element.atomicNumber,
            neutrons = neutrons,
            isotope = element.isotopes.firstOrNull {
                it.nucleons == ref.massNumber || it.massNumber == ref.massNumber
            }
        )
    }

    private fun moleConversion(plan: QueryPlan): ExecutionResult? {
        val moles = Regex("""([\d.]+)\s*mol""")
            .find(plan.rawQuery.lowercase())?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return ExecutionResult.MoleConversion(
            moles = moles,
            particles = com.jlindemann.science.ai.data.ChemistryMath.molesToParticles(moles),
            substance = plan.elementKeys.firstOrNull()?.let { store.element(it) }?.let { displayName(it) },
            toParticles = true
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

        // "the third densest element" asks for one element, three down the ranking.
        val offset = ((plan.fieldOrdinal ?: 1) - 1).coerceAtLeast(0)
        val shown = sorted.drop(offset).take(plan.limit)
        if (shown.isEmpty()) return ExecutionResult.Empty(describeFilters(plan))

        return ExecutionResult.ElementList(
            results = shown,
            fieldId = fieldId,
            matched = candidates.size,
            missing = missing,
            descending = plan.sortDescending,
            citations = shown.map { citation(fieldId, it.element) },
            rankOffset = offset,
            // The next element down gives a superlative something to be measured against.
            runnerUp = if (plan.limit == 1) sorted.getOrNull(offset + 1) else null
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
            citations = listOf(
                Citation(row.title, ref.dataset, row.deepLink, mapOf("id" to row.id), fromTable = true)
            )
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

    private companion object {
        /** Fields where an ordering exists but means nothing to a reader. */
        val UNRANKABLE_FIELDS = setOf(
            "atomic_number", "protons", "electrons", "period", "group_number",
            "space_group_number", "year_discovered", "valence_electrons",
            "nfpa_health", "nfpa_flammability", "nfpa_instability", "common_neutrons"
        )

        /** Below this many recorded values a rank is more misleading than useful. */
        const val MIN_FOR_RANK = 20
    }
}
