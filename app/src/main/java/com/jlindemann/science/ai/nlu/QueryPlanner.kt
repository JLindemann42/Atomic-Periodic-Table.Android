package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.EntityRef
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.FieldKind
import com.jlindemann.science.ai.data.FieldRegistry
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.retrieval.EntityResolver
import com.jlindemann.science.ai.retrieval.HybridRetriever
import com.jlindemann.science.ai.retrieval.RetrievedRef

/**
 * Turns a natural-language question into an executable [QueryPlan].
 *
 * The planner deliberately declines most queries. It only claims one when there is positive
 * evidence that it can do better than the existing handlers — an operator (comparator,
 * superlative, aggregation, unit request), several elements to compare, or an element-and-field
 * pair where the field is resolved and the data is genuinely absent. Everything else returns a
 * plan with [Intent.UNKNOWN] and low confidence so the caller falls through untouched.
 *
 * That is what makes this safe to add alongside the existing router rather than in place of it:
 * a query the planner does not claim behaves exactly as it did before.
 */
class QueryPlanner(
    private val store: KnowledgeStore,
    private val datasets: DatasetIndex,
    private val entities: EntityResolver,
    private val fields: FieldResolver,
    private val retriever: HybridRetriever?
) {

    /** Plans at or above this confidence are used; below it the caller falls back. */
    val threshold: Double get() = CONFIDENCE_THRESHOLD

    fun plan(rawQuery: String, state: DialogueState): QueryPlan {
        // Some concepts have no backing field — reactivity is derived from group and position
        // rather than stored — so planning over them would answer the wrong question. Decline
        // and let the handler that actually models the concept take it.
        val normalized = com.jlindemann.science.ai.retrieval.TextMatching.normalizeForLookup(rawQuery)
        if (Lexicon.UNBACKED_CONCEPTS.any { normalized.contains(it) }) {
            // A question *about* one of these concepts wants it explained, and the dictionary
            // can do that: "why are alkali metals so reactive". A question asking which element
            // is the most reactive wants an element, and only the bespoke scoring rule can
            // answer it — so a superlative disqualifies the explanation.
            val superlative = Lexicon.MOST.any { normalized.contains(it) } ||
                    Lexicon.LEAST.any { normalized.contains(it) }
            if (!superlative) {
                explanationPlan(rawQuery, normalized, hasElement = false)?.let { return it }
            }
            // Mentioning one of these in passing does not block a comparison that also asks
            // about real properties: "compare lithium, sodium and potassium on reactivity,
            // density and applications" answers the parts it has fields for and says which
            // parts it could not.
            //
            // A comparable field is required, not just several elements. Without that condition
            // "compare the reactivity of sodium and gold" and "what happens when sodium and
            // chlorine react" would both be answered as property tables, which is not what
            // either is asking.
            val comparableAspect = fields.resolveAll(rawQuery, limit = 3).isNotEmpty()
            val severalElements = entities.resolveAll(rawQuery, limit = 3).size >= 2
            if (!comparableAspect || !severalElements) {
                return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
            }
        }

        // ---- Calculations ------------------------------------------------------------------
        // Checked first: these questions name a formula or a nuclide, which the element and
        // field resolvers would otherwise pick apart into unrelated matches. "the molar mass of
        // H2SO4" used to retrieve the dictionary definition of molar mass instead of a number.
        calculationPlan(rawQuery, normalized)?.let { return it }


        val operators = OperatorExtractor.extract(rawQuery)
        val elementMatches = entities.resolveAll(rawQuery, limit = 4)
        val fieldMatches = fields.resolveAll(rawQuery, limit = 3)
        val evidence = ArrayList<String>(6)

        // ---- Explanations --------------------------------------------------------------------
        // "Why does atomic radius increase down a group" and "what is a halogen" want prose, not
        // a value or a list of matching elements. Both would otherwise be captured: the first by
        // the plain field definition, the second by a subset list.
        explanationPlan(rawQuery, normalized, elementMatches.isNotEmpty())?.let { return it }

        // Slot-emptiness inheritance: a follow-up supplies one slot and inherits the other.
        // This is what makes "and its density?" work in every language without pronoun lists.
        var elementKeys = elementMatches.map { it.key }
        var fieldIds = fieldMatches.map { it.spec.id }
        if (elementKeys.isEmpty() && fieldIds.isNotEmpty()) {
            state.focusElement?.let { elementKeys = listOf(it); evidence.add("inherited element $it") }
        }
        if (fieldIds.isEmpty() && elementKeys.isNotEmpty() && state.lastFieldIds.isNotEmpty()) {
            fieldIds = state.lastFieldIds
            evidence.add("inherited field ${fieldIds.first()}")
        }

        val subsetFilters = operators.subsetFilters
        val comparatorFilters = operators.comparators.mapNotNull { (op, quantity) ->
            // A comparator needs a field to compare against; fall back to the sortable field.
            val fieldId = fieldIds.firstOrNull() ?: return@mapNotNull null
            com.jlindemann.science.ai.core.Filter.FieldCompare(fieldId, op, quantity)
        }
        if (comparatorFilters.isNotEmpty()) evidence.add("comparator on ${fieldIds.firstOrNull()}")
        if (subsetFilters.isNotEmpty()) evidence.add("subset ${subsetFilters.size}")

        val allFilters = subsetFilters + comparatorFilters
        val targetUnit = operators.targetUnit ?: state.lastTargetUnit.takeIf { fieldIds.isNotEmpty() }

        // ---- Aggregation: "average electronegativity of the halogens" ---------------------
        if (operators.aggregation != Aggregation.NONE && (allFilters.isNotEmpty() || elementKeys.size > 1)) {
            val fieldId = fieldIds.firstOrNull()
            if (operators.aggregation == Aggregation.COUNT || fieldId != null) {
                evidence.add("aggregation ${operators.aggregation}")
                return QueryPlan(
                    intent = Intent.AGGREGATE,
                    entities = elementKeys.map { EntityRef.Element(it) },
                    fieldIds = listOfNotNull(fieldId),
                    filters = allFilters,
                    aggregation = operators.aggregation,
                    sortField = fieldId,
                    targetUnit = targetUnit,
                    confidence = 0.85,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // ---- Superlative and filtered list ------------------------------------------------
        val wantsRanking = operators.superlativeDescending != null || operators.topN != null
        if (wantsRanking || comparatorFilters.isNotEmpty()) {
            val sortField = fieldIds.firstOrNull() ?: inferSortField(rawQuery)
            if (sortField != null) {
                // "which element has the lowest X" is a superlative, not a list, even though it
                // opens with "which". Only an explicit count, a threshold, or a list question
                // with no superlative asks for more than one row.
                val isList = operators.topN != null || comparatorFilters.isNotEmpty() ||
                        (operators.isListQuestion && operators.superlativeDescending == null)
                evidence.add(if (isList) "filtered list on $sortField" else "superlative on $sortField")
                return QueryPlan(
                    intent = if (isList) Intent.FILTER_LIST else Intent.SUPERLATIVE,
                    fieldIds = listOf(sortField),
                    filters = allFilters,
                    sortField = sortField,
                    sortDescending = operators.superlativeDescending ?: true,
                    limit = operators.topN ?: if (isList) DEFAULT_LIST_LIMIT else 1,
                    targetUnit = targetUnit,
                    confidence = if (comparatorFilters.isNotEmpty()) 0.85 else 0.8,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // A subset question: "which elements are radioactive", "list the noble gases".
        // A named field is only used as the displayed column when it is something measurable —
        // "radioactive" resolves to a field but is already expressed by the filter itself, so
        // requiring no field here would make that query unplannable.
        if (subsetFilters.isNotEmpty() && operators.isListQuestion) {
            val displayField = fieldIds.firstOrNull()
                ?.takeIf { FieldRegistry.byId[it]?.kind == FieldKind.NUMERIC }
            evidence.add("subset list")
            return QueryPlan(
                intent = Intent.FILTER_LIST,
                fieldIds = listOfNotNull(displayField),
                filters = subsetFilters,
                sortField = displayField,
                limit = DEFAULT_LIST_LIMIT,
                confidence = 0.75,
                rawQuery = rawQuery,
                evidence = evidence
            )
        }

        // "compare with iron" while the conversation is about gold: the second element is the
        // one already in focus.
        if (elementKeys.size == 1 && state.focusElement != null &&
            elementKeys.first() != state.focusElement &&
            Lexicon.COMPARE.any { comparisonWord ->
                com.jlindemann.science.ai.retrieval.TextMatching
                    .normalizeForLookup(rawQuery).contains(comparisonWord)
            }
        ) {
            elementKeys = listOf(state.focusElement!!, elementKeys.first())
            evidence.add("comparison against focus element")
        }

        // ---- Element versus alloy: "what is the difference between iron and steel" ----------
        // An alloy is not an element, so a side-by-side property table is not available and not
        // what is being asked. The alloy's own entry explains how it differs from its base metal,
        // which is the answer.
        alloyIn(normalized)?.let { alloy ->
            evidence.add("alloy ${alloy.id}")
            return QueryPlan(
                intent = Intent.DATASET_LOOKUP,
                entities = listOf(EntityRef.DatasetRow(DatasetIndex.ALLOY, alloy.id)),
                confidence = 0.8,
                rawQuery = rawQuery,
                evidence = evidence
            )
        }

        // ---- Comparison: two or more elements named together ------------------------------
        if (elementKeys.size >= 2) {
            // "in terms of reactivity, density and applications" names several aspects at once.
            // Every aspect that maps to a field is compared; the rest are reported as not
            // comparable from stored data rather than silently dropped.
            val aspects = fields.resolveAll(rawQuery, limit = 6).map { it.spec.id }
            val unsupported = unsupportedAspects(normalized)
            evidence.add("comparison of ${elementKeys.size} elements")
            if (aspects.size > 1) evidence.add("aspects ${aspects.size}")
            return QueryPlan(
                intent = Intent.COMPARISON,
                entities = elementKeys.map { EntityRef.Element(it) },
                fieldIds = aspects.ifEmpty { fieldIds.ifEmpty { DEFAULT_COMPARISON_FIELDS } },
                targetUnit = targetUnit,
                confidence = 0.8,
                rawQuery = rawQuery,
                evidence = evidence + unsupported.map { "no field for $it" }
            )
        }

        val element = elementKeys.firstOrNull()?.let { store.element(it) }

        // ---- Isotopes and safety: their own shapes, not single fields ----------------------
        // Checked before field resolution because "isotopes" and "half life" both resolve to a
        // field label, which would answer with the common-neutron count instead of the list.
        if (element != null) {
            if (Lexicon.ISOTOPE_WORDS.any { normalized.contains(it) }) {
                evidence.add("isotopes")
                return QueryPlan(
                    intent = Intent.ISOTOPES,
                    entities = listOf(EntityRef.Element(element.key)),
                    limit = operators.topN ?: DEFAULT_ISOTOPE_LIMIT,
                    confidence = 0.85,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
            if (Lexicon.SAFETY_WORDS.any { normalized.contains(it) }) {
                evidence.add("safety")
                return QueryPlan(
                    intent = Intent.SAFETY,
                    entities = listOf(EntityRef.Element(element.key)),
                    confidence = 0.85,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // ---- Category lookup: a whole family of properties for one element -----------------
        if (element != null) {
            categoryIn(normalized)?.let { category ->
                val populated = FieldRegistry.byCategory(category)
                    .filter { !element.value(it.id).isMissing }
                    .map { it.id }
                if (populated.size >= 2) {
                    evidence.add("category $category")
                    return QueryPlan(
                        intent = Intent.CATEGORY_LOOKUP,
                        entities = listOf(EntityRef.Element(element.key)),
                        fieldIds = populated,
                        targetUnit = targetUnit,
                        confidence = 0.8,
                        rawQuery = rawQuery,
                        evidence = evidence
                    )
                }
            }
        }

        // ---- Property lookup ---------------------------------------------------------------
        val fieldId = fieldIds.firstOrNull()
        if (element != null && fieldId != null) {
            // A bare "tell me about gold" is narrative, not a field lookup, and belongs to the
            // personality layer. Only decline when no field was actually named, though —
            // "what is the density of gold" also opens with "what is".
            val overviewOnly = fieldMatches.isEmpty() &&
                    Lexicon.OVERVIEW_WORDS.any { normalized.contains(it) }
            if (!overviewOnly) {
                if (element.value(fieldId).isMissing &&
                    store.quantityIn(element, fieldId, targetUnit) == null
                ) {
                    evidence.add("no data for $fieldId")
                } else {
                    evidence.add("property $fieldId")
                }
                return QueryPlan(
                    intent = Intent.PROPERTY_LOOKUP,
                    entities = listOf(EntityRef.Element(element.key)),
                    fieldIds = listOf(fieldId),
                    fieldOrdinal = operators.ordinal,
                    targetUnit = targetUnit,
                    confidence = 0.8,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // ---- Dataset row, when retrieval is confident and no element was named -------------
        if (elementKeys.isEmpty()) {
            val hit = retriever?.best(rawQuery)
            val ref = hit?.ref
            if (ref is RetrievedRef.Dataset && hit.score >= DATASET_CONFIDENCE) {
                evidence.add("dataset ${ref.dataset}")
                return QueryPlan(
                    intent = Intent.DATASET_LOOKUP,
                    entities = listOf(EntityRef.DatasetRow(ref.dataset, ref.id)),
                    confidence = 0.7,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
    }

    /**
     * Route "why ..." and "what is a ..." to the concept that explains it.
     *
     * A definitional frame is ignored when an element is named, so "what is the atomic mass of
     * gold" gives gold's value rather than the definition of atomic mass. A "why" question still
     * takes the explanation even with an element present, because "why is chromium's electron
     * configuration unusual" is asking about the rule, not about chromium's stored value.
     */
    private fun explanationPlan(rawQuery: String, normalized: String, hasElement: Boolean): QueryPlan? {
        val isWhy = Lexicon.WHY_WORDS.any { normalized.startsWith("$it ") || normalized.contains(" $it ") }
        val isDefinition = !hasElement && Lexicon.DEFINITION_FRAMES.any { normalized.startsWith(it) }
        if (!isWhy && !isDefinition) return null

        // Longest topic first, so "electron configuration unusual" beats "electron".
        val topic = Lexicon.EXPLANATION_TOPICS.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { normalized.contains(it.key) }
            ?: return null

        val row = datasets.row(DatasetIndex.DICTIONARY, topic.value) ?: return null
        return QueryPlan(
            intent = Intent.DATASET_LOOKUP,
            entities = listOf(EntityRef.DatasetRow(DatasetIndex.DICTIONARY, row.id)),
            confidence = 0.85,
            rawQuery = rawQuery,
            evidence = listOf(if (isWhy) "explains ${topic.value}" else "defines ${topic.value}")
        )
    }

    /** A nuclide written as "uranium-238", "U-238" or "carbon 14". */
    private val NUCLIDE = Regex("""([a-z]{1,13})\s*[-\s]\s*(\d{1,3})\b""")

    /** A mole quantity, e.g. "2 moles of carbon". */
    private val MOLES = Regex("""([\d.]+)\s*mol(?:e|es)?\b""")

    /**
     * A candidate chemical formula. Deliberately case-insensitive — users write "h2so4" far more
     * often than "H2SO4" — with ChemistryMath deciding whether it actually parses.
     */
    private val FORMULA = Regex("""\b([A-Za-z][A-Za-z0-9()]{1,15})\b""")

    /**
     * Recognise the arithmetic questions: formula mass, composition, neutron counts and mole
     * conversions. Returns null when the query is not one of those.
     */
    private fun calculationPlan(rawQuery: String, normalized: String): QueryPlan? {
        // --- Moles to particles -------------------------------------------------------------
        if (Lexicon.MOLE_WORDS.any { normalized.contains(it) }) {
            MOLES.find(normalized)?.let { match ->
                return QueryPlan(
                    intent = Intent.MOLE_CONVERSION,
                    rawQuery = rawQuery,
                    confidence = 0.85,
                    evidence = listOf("moles ${match.groupValues[1]}")
                )
            }
        }

        // --- Two nuclides compared: "uranium-235 vs uranium-238" -------------------------------
        val nuclides = NUCLIDE.findAll(normalized)
            .mapNotNull { match ->
                val element = store.element(match.groupValues[1]) ?: store.bySymbol(match.groupValues[1])
                val mass = match.groupValues[2].toIntOrNull()
                if (element != null && mass != null && mass >= element.atomicNumber) {
                    EntityRef.Nuclide(element.key, mass)
                } else null
            }
            .distinct()
            .take(2)
            .toList()
        if (nuclides.size == 2) {
            return QueryPlan(
                intent = Intent.ISOTOPE_COMPARISON,
                entities = nuclides,
                rawQuery = rawQuery,
                confidence = 0.9,
                evidence = listOf("nuclide comparison")
            )
        }

        // --- Neutrons in a nuclide -----------------------------------------------------------
        if (Lexicon.NEUTRON_WORDS.any { normalized.contains(it) }) {
            NUCLIDE.find(normalized)?.let { match ->
                val name = match.groupValues[1]
                val mass = match.groupValues[2].toIntOrNull()
                val element = store.element(name) ?: store.bySymbol(name)
                if (element != null && mass != null) {
                    return QueryPlan(
                        intent = Intent.NUCLIDE_COUNT,
                        entities = listOf(EntityRef.Element(element.key)),
                        limit = mass,
                        rawQuery = rawQuery,
                        confidence = 0.9,
                        evidence = listOf("nuclide $name-$mass")
                    )
                }
            }
        }

        // --- Formula mass and composition ------------------------------------------------------
        val wantsMass = Lexicon.MOLAR_MASS_WORDS.any { normalized.contains(it) }
        val wantsComposition = Lexicon.COMPOSITION_WORDS.any { normalized.contains(it) }
        if (!wantsMass && !wantsComposition) return null

        // Prefer an explicit formula in the raw text, where capitalisation survives.
        val candidate = FORMULA.findAll(rawQuery)
            .map { it.value }
            .filter { it.any { c -> c.isDigit() } || it.length > 2 }
            .firstOrNull { candidate ->
                com.jlindemann.science.ai.data.ChemistryMath
                    .parseFormula(candidate) { symbol -> atomicMassOf(symbol) } != null
            }
            // "percentage composition of water" names a compound rather than writing it.
            ?: Lexicon.COMMON_COMPOUNDS.entries
                .firstOrNull { normalized.contains(it.key) }?.value
            ?: return null

        return QueryPlan(
            intent = Intent.FORMULA_MASS,
            fieldIds = if (wantsComposition) listOf("composition") else emptyList(),
            rawQuery = candidate,
            confidence = 0.9,
            evidence = listOf(if (wantsComposition) "composition of $candidate" else "molar mass of $candidate")
        )
    }

    private fun atomicMassOf(symbol: String): Double? =
        store.bySymbol(symbol)?.quantity("atomic_mass")?.value

    /**
     * Aspects a multi-part comparison asked for that no stored field can supply.
     *
     * Naming them lets the answer say what it could not cover, instead of quietly answering a
     * narrower question than the one asked.
     */
    private fun unsupportedAspects(normalizedQuery: String): List<String> =
        UNCOMPARABLE_ASPECTS.filter { normalizedQuery.contains(it) }

    /** The alloy a query names, longest name first so "stainless steel" beats "steel". */
    private fun alloyIn(normalizedQuery: String): com.jlindemann.science.ai.data.DatasetRow? =
        datasets.dataset(DatasetIndex.ALLOY)
            .sortedByDescending { it.title.length }
            .firstOrNull {
                com.jlindemann.science.ai.retrieval.TextMatching
                    .containsWord(normalizedQuery, it.title.lowercase())
            }

    /** The property family a query names, longest phrase first so "heat" cannot shadow a phrase. */
    private fun categoryIn(normalizedQuery: String): com.jlindemann.science.ai.data.FieldCategory? =
        Lexicon.CATEGORY_WORDS.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { normalizedQuery.contains(it.key) }
            ?.value

    /**
     * Some superlatives name the property implicitly: "densest" already means density.
     * Only used when no field was resolved explicitly.
     */
    private fun inferSortField(rawQuery: String): String? {
        val q = com.jlindemann.science.ai.retrieval.TextMatching.normalizeForLookup(rawQuery)
        return IMPLICIT_SUPERLATIVES.entries.firstOrNull { (word, _) -> q.contains(word) }?.value
    }

    private companion object {
        const val CONFIDENCE_THRESHOLD = 0.7
        const val DATASET_CONFIDENCE = 0.45
        const val DEFAULT_LIST_LIMIT = 10
        const val DEFAULT_ISOTOPE_LIMIT = 8

        /**
         * Aspects users ask to compare that the element data does not hold. Reactivity is
         * derived rather than stored, and applications and uses live in prose descriptions
         * rather than in a comparable field.
         */
        val UNCOMPARABLE_ASPECTS = listOf(
            "reactivity", "reactive", "application", "applications", "uses", "used for"
        )

        /** Fields shown when elements are compared without naming a property. */
        val DEFAULT_COMPARISON_FIELDS = listOf(
            "atomic_number", "atomic_mass", "density", "melting_point", "electronegativity"
        )

        /** Superlative words that imply their own field. */
        val IMPLICIT_SUPERLATIVES = mapOf(
            "densest" to "density",
            "heaviest" to "atomic_mass",
            "lightest" to "atomic_mass",
            "hardest" to "mohs_hardness",
            "softest" to "mohs_hardness",
            "hottest" to "melting_point",
            "tatast" to "density",
            "tyngst" to "atomic_mass",
            "lattast" to "atomic_mass",
            "dichteste" to "density",
            "schwerste" to "atomic_mass",
            "mas denso" to "density",
            "mas pesado" to "atomic_mass",
            "plus dense" to "density",
            "plus lourd" to "atomic_mass",
            "最密" to "density",
            "最重" to "atomic_mass"
        )
    }
}
