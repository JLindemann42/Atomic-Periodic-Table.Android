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
            return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
        }

        val operators = OperatorExtractor.extract(rawQuery)
        val elementMatches = entities.resolveAll(rawQuery, limit = 4)
        val fieldMatches = fields.resolveAll(rawQuery, limit = 3)
        val evidence = ArrayList<String>(6)

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

        // ---- Comparison: two or more elements named together ------------------------------
        if (elementKeys.size >= 2) {
            evidence.add("comparison of ${elementKeys.size} elements")
            return QueryPlan(
                intent = Intent.COMPARISON,
                entities = elementKeys.map { EntityRef.Element(it) },
                fieldIds = fieldIds.ifEmpty { DEFAULT_COMPARISON_FIELDS },
                targetUnit = targetUnit,
                confidence = 0.8,
                rawQuery = rawQuery,
                evidence = evidence
            )
        }

        val element = elementKeys.firstOrNull()?.let { store.element(it) }

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
