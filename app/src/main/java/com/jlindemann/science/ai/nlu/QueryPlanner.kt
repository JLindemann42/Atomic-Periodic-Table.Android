package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.Aggregation
import com.jlindemann.science.ai.core.DialogueState
import com.jlindemann.science.ai.core.EntityRef
import com.jlindemann.science.ai.core.Intent
import com.jlindemann.science.ai.core.QueryPlan
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.Dimension
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

        // ---- Conversation, not chemistry ---------------------------------------------------
        // Checked before anything else. A greeting or a quiz request has no grounded answer, but
        // the dataset fallback further down will happily supply one: "quiz me" scored 0.70 against
        // the constants table and was answered with the mass of an electron. Retrieval cannot tell
        // that a query is not a question, so this has to be an explicit early exit.
        if (Lexicon.DEFER_TO_PERSONALITY.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it) ||
                        (it.contains(' ') && normalized.contains(it))
            }
        ) {
            return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
        }

        // An element number past 118 has a definite answer — there is no such element — and the
        // worst thing to do is guess. Declining lets the personality layer say so; answering from
        // retrieval produced "Electron mass" for "tell me about element 119".
        if (entities.outOfRangeElementNumber(rawQuery) != null) {
            return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
        }

        // "समान" is Hindi for "similar" and also the first word of "समान समूह" — "the same group".
        // The unbacked-concept guard therefore declined every Hindi "which elements are in the same
        // group as carbon", which is a perfectly answerable structural question.
        val namesTheSameGroup = Lexicon.SAME_AS_WORDS.any { normalized.contains(it) }
        // An equation is written, not described. "Balance this reaction: Fe + O2 -> Fe2O3" carries
        // a reaction word, and the unbacked-concept guard declines every sentence that does — but
        // the arrow is far stronger evidence of what the sentence is than the noun beside it.
        val writesAnEquation = EQUATION_ARROW.containsMatchIn(rawQuery)
        if (!namesTheSameGroup && !writesAnEquation &&
            Lexicon.UNBACKED_CONCEPTS.any { normalized.contains(it) }
        ) {
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

        // ---- "How many elements are there" ---------------------------------------------------
        // A plain fact about the table itself. Without this it retrieves whichever dictionary
        // entry happens to score highest, which is not an answer.
        // The aggregation has to actually *be* a count. Chinese asks for every statistic with 多少
        // ("how much"), so "镧系元素的中位原子量是多少" — the median atomic mass of the lanthanides —
        // matched the count word, the word "element" and no subset, and came back as the size of
        // the periodic table.
        if (Lexicon.COUNT.any { normalized.contains(it) } &&
            Lexicon.ELEMENT_WORDS.any { normalized.contains(it) } &&
            entities.resolveAll(rawQuery, limit = 1).isEmpty() &&
            OperatorExtractor.extract(rawQuery).let {
                it.subsetFilters.isEmpty() && it.aggregation == Aggregation.COUNT
            }
        ) {
            return QueryPlan(
                intent = Intent.AGGREGATE,
                aggregation = Aggregation.COUNT,
                confidence = 0.85,
                rawQuery = rawQuery,
                evidence = listOf("count of all elements")
            )
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

        // ---- Standalone unit conversion -------------------------------------------------------
        // Placed here rather than in `calculationPlan`, because the guard that separates this from
        // a property lookup needs the resolved field and element, and neither is available where
        // the calculations run.
        unitConvertPlan(rawQuery, operators)?.let { return it }

        // ---- Narrative questions -------------------------------------------------------------
        // Usage, biological role and etymology are answered from prose, not from a field. They are
        // declined here rather than left to fall through, because falling through is what caused
        // the worst defect in the agent: the query names an element, resolves no field, and the
        // inheritance below hands it the previous turn's field. "Vad används titan till" was
        // answered with titanium's atomic number that way.
        //
        // A resolved field wins over this: "what is titanium used for in aerospace, and its
        // density" still has a real question in it.
        if (fieldMatches.isEmpty() && Lexicon.NARRATIVE_INTENTS.any { normalized.contains(it) }) {
            return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
        }

        // Etymology declines even with a field in hand, because the field is the *subject* of the
        // question rather than the thing being asked for: "where does that symbol come from"
        // resolves `symbol`, and answering it with a value — tin's nine abundance reservoirs, as
        // reported — mistakes what the sentence is about.
        if (Lexicon.ETYMOLOGY_INTENTS.any { normalized.contains(it) }) {
            return QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
        }

        // ---- Explanations --------------------------------------------------------------------
        // "Why does atomic radius increase down a group" and "what is a halogen" want prose, not
        // a value or a list of matching elements. Both would otherwise be captured: the first by
        // the plain field definition, the second by a subset list.
        //
        // An operator disqualifies the definitional reading. "What is the average electronegativity
        // of the halogens" opens with a definition frame and names a topic the dictionary has, so
        // it was answered with the definition of electronegativity — the word "average" and the
        // subset were never reached, because this check runs before aggregation.
        // A subset on its own is deliberately not disqualifying: "what is a halogen" names a family
        // and wants the definition of it, not a list of its members. Only an operator that makes the
        // question a computation over the table — a statistic, a ranking, a threshold, a range —
        // means the definitional reading is the wrong one.
        val carriesOperator = operators.aggregation != Aggregation.NONE ||
                operators.superlativeDescending != null ||
                operators.comparators.isNotEmpty() ||
                operators.topN != null ||
                operators.range != null
        // A follow-up with a subject in play is asking about that subject. "What are its isotopes"
        // opens with a definition frame and names a topic the dictionary holds, so it was answered
        // with the definition of "isotope" instead of the element's isotopes.
        val continuesAThread = elementMatches.isEmpty() &&
                state.focusElement != null &&
                readsAsFollowUp(normalized)
        // A mechanism question is checked even mid-thread. Every reported case was a follow-up —
        // "why does that trend hold", "how does that change above Curie temperature" — so excluding
        // follow-ups here exempted exactly the turns that were failing.
        if (asksHowItWorks(normalized) || (!carriesOperator && !continuesAThread)) {
            explanationPlan(rawQuery, normalized, elementMatches.isNotEmpty())?.let { return it }
        }

        // Slot-emptiness inheritance: a follow-up supplies one slot and inherits the other.
        // This is what makes "and its density?" work in every language without pronoun lists.
        var elementKeys = elementMatches.map { it.key }
        var fieldIds = fieldMatches.map { it.spec.id }

        // A question that ranges over the whole table has no subject to inherit. Without this
        // guard, "which element has the highest melting point" asked three turns into a thread
        // about tungsten inherits tungsten and collapses into a plain property lookup — the
        // opposite failure to a stale field, and just as wrong.
        // Deliberately *not* keyed on `isListQuestion`: Lexicon.WHICH contains "what", so that flag
        // is true for "what is its density" and using it here silently disabled the whole follow-up
        // mechanism. Only the operators that genuinely scan the table count.
        val rangesOverTable = operators.superlativeDescending != null ||
                operators.topN != null ||
                // "How many are there" ranges; "how many neutrons does it have" does not, and the
                // difference is whether a field was named.
                (operators.aggregation != Aggregation.NONE && fieldMatches.isEmpty()) ||
                // "Which elements are radioactive" ranges; "is it radioactive" carries the same
                // subset word but is a question about the subject already in play.
                (operators.subsetFilters.isNotEmpty() && operators.isListQuestion) ||
                // "Noble gases", "the halogens", "ädelgaserna" — a family name and nothing else is
                // a request for that family, not a continuation of whatever came before.
                namesOnlyAFamily(normalized, operators, elementMatches.isNotEmpty())
        if (elementKeys.isEmpty() && !rangesOverTable) {
            // Inherit the subject whenever the turn reads as a continuation, not only when a field
            // resolved. "and in fahrenheit" resolves no field, "what are its isotopes" resolves no
            // field the isotope branch uses, and both were falling through to a dataset match.
            // A plural anaphor inherits the whole set, not the focus. "And their melting points"
            // after comparing gold and silver is a two-element question; taking only the focus
            // answered half of it and gave no sign that half was dropped.
            val plural = Lexicon.PLURAL_ANAPHORS.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
            val previousSet = state.lastResultKeys.takeIf { it.size >= 2 }
                ?: state.recentElements.take(2).takeIf { it.size == 2 }
            // Spanish "sus" and Portuguese "seus" agree with the thing possessed, not the
            // possessor, so "cuáles son sus isótopos" is *its* isotopes — one element with several
            // isotopes — and inheriting the pair answered it as a comparison of two elements.
            if (plural && previousSet != null && !asksForIsotopes(normalized)) {
                elementKeys = previousSet
                evidence.add("inherited ${previousSet.size} elements")
            } else if (fieldIds.isNotEmpty() || continuesTheThread(normalized)) {
                state.focusElement?.let { elementKeys = listOf(it); evidence.add("inherited element $it") }
            }
        }
        // Inheriting a *field* is riskier than inheriting an element, because a full question that
        // resolved no field is usually a new question rather than a continuation — and answering it
        // with the previous field is confidently wrong rather than merely unhelpful. So it is
        // limited to queries that actually read as follow-ups: short ones ("and iron?", "what about
        // silver"), which is the shape the mechanism exists to serve.
        // A follow-up that ranges over the set rather than over one element inherits the field too:
        // "is that the highest" after listing metals by density has no element and no field of its
        // own, and every part of it comes from the turn before.
        val elliptical = continuesTheThread(normalized) &&
                (elementKeys.isNotEmpty() || operators.superlativeDescending != null ||
                        operators.aggregation != Aggregation.NONE)
        if (fieldIds.isEmpty() && state.lastFieldIds.isNotEmpty() && elliptical) {
            fieldIds = state.lastFieldIds
            evidence.add("inherited field ${fieldIds.first()}")
        }

        // A bare "how many are there" after "list the noble gases" is asking about that set. The
        // filters are inherited rather than the question being answered over all 118 elements,
        // which is the only reading that makes the follow-up mean anything.
        val subsetFilters = operators.subsetFilters.ifEmpty {
            // Only a follow-up that itself ranges over a set — "how many are there", "which is the
            // densest" — inherits the previous turn's filters. A follow-up asking for one field of
            // one element must not, or "what is its atomic number" after listing the halogens turns
            // back into a list of halogens.
            val previous = state.lastPlan?.filters.orEmpty()
            val wantsASet = operators.aggregation != Aggregation.NONE ||
                    operators.superlativeDescending != null
            if (previous.isNotEmpty() && elementKeys.isEmpty() && wantsASet && readsAsFollowUp(normalized)) {
                evidence.add("inherited ${previous.size} filter(s)")
                previous
            } else {
                emptyList()
            }
        }
        val comparatorFilters = operators.comparators.mapNotNull { (op, quantity) ->
            // A comparator needs a field to compare against; fall back to the sortable field.
            val fieldId = fieldIds.firstOrNull() ?: return@mapNotNull null
            com.jlindemann.science.ai.core.Filter.FieldCompare(fieldId, op, quantity)
        }.toMutableList()


        // A comparative with only one element named takes the other from the conversation:
        // "is gold denser" after a turn about lead, "which is denser" after comparing two. The
        // adjective is the signal — it is what makes the turn a comparison rather than a lookup,
        // and without this the question fell through to a filtered list of everything denser.
        // Hindi and Urdu have no comparative adjective to key on: the comparison is carried by the
        // particle alone, and the adjective stays in its plain form — "क्या यह चांदी से भारी है" is
        // "is it heavier than silver" with भारी meaning simply "heavy". The particle plus a named
        // field plus a pronoun standing for the other subject is the whole of the evidence there
        // is, and without accepting it the turn was answered as a lookup of the named element.
        val comparativeCue = Lexicon.COMPARATIVE_ADJECTIVES.keys.any { normalized.contains(it) } ||
                (fieldIds.isNotEmpty() &&
                        mentionsAny(normalized, Lexicon.THAN_WORDS) &&
                        mentionsAny(normalized, Lexicon.ANAPHORS))
        if (elementMatches.size == 1 && state.focusElement != null &&
            elementMatches.first().key != state.focusElement && comparativeCue
        ) {
            val pair = listOf(state.focusElement!!, elementMatches.first().key)
            comparativePlan(rawQuery, normalized, pair, fieldIds)?.let {
                evidence.add("comparative against focus element")
                return it
            }
            // `comparativePlan` reads an explicit "X is denser than Y" and needs a comparator, an
            // adjective or a degree word to do it — none of which a Hindi or Urdu comparative has.
            // When the cue said this is a comparison and the sentence cannot be parsed as one, the
            // pair and the field are still enough to answer it.
            if (fieldIds.isNotEmpty()) {
                evidence.add("comparative against focus element")
                return QueryPlan(
                    intent = Intent.COMPARATIVE,
                    entities = pair.map { EntityRef.Element(it) },
                    fieldIds = listOf(fieldIds.first()),
                    targetUnit = operators.targetUnit ?: state.lastTargetUnit,
                    confidence = 0.8,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }
        // Neither element named — "which is denser", "which melts first", "which conducts heat
        // better". Both subjects come from the previous turn, which is the only place they exist.
        //
        // Built here rather than handed to comparativePlan, because that reads explicit structure
        // ("is X denser than Y") and there is none: the turn is elliptical by design. The field
        // comes from the comparative adjective when the query names one, otherwise from whatever
        // the thread was already about.
        // `elementMatches`, not `elementKeys`: by this point inheritance has already put the focus
        // element into elementKeys, which masked every elliptical comparative the thread produced.
        // The two subjects come from the previous answer when it named two, and otherwise from the
        // last two elements discussed — "which is bigger" after looking up sodium and then
        // potassium in separate turns is the same question, and neither turn produced a pair.
        val recentPair = state.lastResultKeys.takeIf { it.size >= 2 }
            ?: state.recentElements.take(2).takeIf { it.size == 2 }
        if (elementMatches.isEmpty() && recentPair != null &&
            operators.comparators.isEmpty() && operators.subsetFilters.isEmpty()
        ) {
            val adjective = Lexicon.COMPARATIVE_ADJECTIVES.entries
                .sortedByDescending { it.key.length }
                .firstOrNull { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it.key) }
            val comparativeField = fieldIds.firstOrNull()
                ?: adjective?.value?.first
                ?: state.lastFieldIds.firstOrNull()
            // An explicit *comparative* adjective, and no superlative. "Which is denser" compares
            // the two subjects already in play; "which is the densest" ranges over the whole set the
            // thread established, and belongs to the superlative branch. Without the second half of
            // this condition the follow-up after a list answer collapsed to a two-element comparison.
            // Either an explicit comparative adjective, or a "which …" question that names a field
            // and no element — "which conducts heat better", "which melts at a lower temperature".
            // A superlative disqualifies both: that ranges over a set rather than over the pair.
            // Or a bare request for the margin — "by how much", "what is the difference" — which
            // names neither subject nor field and is only meaningful as a continuation of the
            // comparison just made.
            val wantsTheMargin = Lexicon.MARGIN_WORDS.any { normalized.contains(it) }
            // With exactly two elements in play, a degree word means "between these two" rather than
            // "of the whole table" — "quale è più denso", "qual é mais denso", "哪个更致密". Those
            // languages build comparative and superlative from the same word, so requiring no
            // superlative marker disabled this branch for every one of them.
            // `recentPair`, not `lastResultKeys`: after "density of gold" then "and silver?" the
            // previous turn named one element, while the pair under discussion is two — which is
            // exactly the thread this branch exists to serve.
            // Exactly a pair, and the turn before must not have been a list. After "which elements
            // are radioactive" the follow-up "which is the heaviest" ranges over that list and is a
            // superlative; only a thread that has two elements in play makes it a comparison.
            // A morphological superlative counts too, once the thread has exactly two elements and
            // has not just produced a list. Swedish and German mark the superlative on the word —
            // "tätast", "dichteste" — so `carriesADegreeWord` cannot see it, and "vilket är tätast"
            // after asking about gold and then silver ranked all 118 instead of choosing between
            // the two under discussion. `lastResultKeys.size <= 2` is what keeps the other reading:
            // after "which elements are radioactive", the same question does range over that list.
            //
            // Naming the word "element" is what says the question ranges over the table rather than
            // over the pair — "which **element** has the highest melting point" is a superlative
            // however deep into a thread it is asked, while the elliptical "which is densest" is
            // not. Without that half, three explicit superlatives collapsed into comparisons.
            val ellipticalSuperlative = operators.superlativeDescending != null &&
                    !mentionsAny(normalized, Lexicon.ELEMENT_WORDS)
            val comparesThePair = recentPair.size == 2 &&
                    state.lastResultKeys.size <= 2 &&
                    operators.subsetFilters.isEmpty() &&
                    (carriesADegreeWord(normalized) || ellipticalSuperlative)
            // An isotope question is not a comparison, however elliptical: "what are its isotopes"
            // was being answered as one.
            val asksSomethingElse = Lexicon.ISOTOPE_WORDS.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
            val readsAsComparison = !asksSomethingElse &&
                    (operators.superlativeDescending == null || comparesThePair) &&
                    (adjective != null || wantsTheMargin ||
                            (comparesThePair && operators.superlativeDescending != null) ||
                            (fieldIds.isNotEmpty() && !namesOneSubject(normalized) &&
                                    Lexicon.SELECTIVE.any {
                                        com.jlindemann.science.ai.retrieval.TextMatching
                                            .containsWord(normalized, it)
                                    }))
            if (comparativeField != null && readsAsComparison) {
                val pair = recentPair.take(2)
                evidence.add("comparative over the previous answer")
                return QueryPlan(
                    intent = Intent.COMPARATIVE,
                    entities = pair.map { EntityRef.Element(it) },
                    fieldIds = listOf(comparativeField),
                    // The shared targetUnit is derived further down; take it from the operators and
                    // the thread directly, so an elliptical turn keeps the unit the user asked for.
                    targetUnit = operators.targetUnit ?: state.lastTargetUnit,
                    confidence = 0.8,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // Two elements and a degree word is a comparison *between those two*. Decided here, ahead
        // of the superlative branch, because the languages that need it mark both with the same
        // word: "l'oro è più denso del piombo" would otherwise rank the whole table.
        // Hindi and Urdu mark the comparative with the particle alone — "सोना सीसे से घना है" has
        // no degree word at all — so the particle counts as evidence in its own right.
        // Chinese writes "compare" as 比较, which *contains* its comparison particle 比 — so
        // "比较这两个" ("compare the two") looked like "this is more … than that" and was answered
        // with a winner instead of a side-by-side table. The verb is masked before the particle is
        // looked for, the same way the degree phrases are in `OperatorExtractor`.
        val withoutCompareVerbs = Lexicon.COMPARE.fold(normalized) { acc, verb ->
            if (verb.length > 1) acc.replace(verb, " ".repeat(verb.length)) else acc
        }
        val marksAComparison = carriesADegreeWord(normalized) ||
                mentionsAny(withoutCompareVerbs, Lexicon.THAN_WORDS)
        if (elementKeys.size == 2 && fieldIds.isNotEmpty() && marksAComparison &&
            operators.comparators.isEmpty() && operators.subsetFilters.isEmpty()
        ) {
            // `comparativePlan` first: it reads the explicit "is X denser than Y" shape and is what
            // produces the ratio for "how much denser is gold than aluminium". Only when it cannot
            // parse the sentence is the plan built here — which is the periphrastic case, where the
            // comparative is a degree word and a particle spread across the sentence with no single
            // adjective to key on and no fixed position for the two operands.
            comparativePlan(rawQuery, normalized, elementKeys, fieldIds)?.let {
                evidence.add("comparative $fieldIds")
                return it
            }
            evidence.add("periphrastic comparative")
            return QueryPlan(
                intent = Intent.COMPARATIVE,
                entities = elementKeys.map { EntityRef.Element(it) },
                fieldIds = listOf(fieldIds.first()),
                targetUnit = operators.targetUnit,
                confidence = 0.8,
                rawQuery = rawQuery,
                evidence = evidence
            )
        }

        // A threshold can name an element instead of a number: "denser than iron" means denser
        // than iron's density. Only when a single element is named — with two, the question is
        // a direct comparison between them ("is gold denser than lead") and the elements are
        // the subject rather than a bound.
        if (comparatorFilters.isEmpty() && elementKeys.size == 1) {
            elementThreshold(normalized, elementKeys, fieldIds)?.let {
                comparatorFilters.add(it)
                // The element supplied the threshold; it is not also a subject of the query.
                elementKeys = emptyList()
                // The adjective also names the property, so the results can be sorted and
                // shown by it: "lighter than aluminium" is about density even though no field
                // was written out.
                if (fieldIds.isEmpty()) fieldIds = listOf(it.fieldId)
                evidence.add("threshold from element")
            }
        }

        // A range: "which elements melt between 1000 and 2000 kelvin".
        operators.range?.let { (low, high) ->
            val fieldId = fieldIds.firstOrNull()
            if (fieldId != null) {
                comparatorFilters.add(
                    com.jlindemann.science.ai.core.Filter.FieldCompare(
                        fieldId, com.jlindemann.science.ai.core.Op.BETWEEN, low, high
                    )
                )
                evidence.add("range on $fieldId")
            }
        }
        if (comparatorFilters.isNotEmpty()) evidence.add("comparator on ${fieldIds.firstOrNull()}")
        if (subsetFilters.isNotEmpty()) evidence.add("subset ${subsetFilters.size}")

        val allFilters = subsetFilters + comparatorFilters
        val targetUnit = operators.targetUnit ?: state.lastTargetUnit.takeIf { fieldIds.isNotEmpty() }

        // ---- "in the same group as carbon" ---------------------------------------------------
        // The named element supplies the group or period; it is the reference, not the answer.
        if (Lexicon.SAME_AS_WORDS.any { normalized.contains(it) }) {
            elementKeys.firstOrNull()?.let { store.element(it) }?.let { reference ->
                val byPeriod = normalized.contains("period") || normalized.contains("row")
                val filter = if (byPeriod) {
                    com.jlindemann.science.ai.core.Filter.InPeriod(reference.period)
                } else {
                    reference.groupNumber?.let { com.jlindemann.science.ai.core.Filter.InGroup(it) }
                        ?: com.jlindemann.science.ai.core.Filter.InSeries(setOf(reference.series))
                }
                evidence.add("same ${if (byPeriod) "period" else "group"} as ${reference.key}")
                return QueryPlan(
                    intent = Intent.FILTER_LIST,
                    filters = listOf(filter),
                    limit = DEFAULT_LIST_LIMIT,
                    confidence = 0.85,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // ---- Direct comparative: "is gold denser than lead" --------------------------------
        // A question with a one-word answer should get one, not a property table. Checked
        // before the general comparison so the answer leads with yes/no or the winner.
        if (elementKeys.size >= 2) {
            comparativePlan(rawQuery, normalized, elementKeys, fieldIds)?.let { return it }
        }


        // ---- Aggregation: "average electronegativity of the halogens" ---------------------
        // A filter is not required: "the average density of all elements" ranges over the whole
        // table, which is a complete question. It previously fell through to a dataset match.
        val aggregatesOverEverything = operators.aggregation != Aggregation.NONE &&
                elementKeys.isEmpty() && fieldIds.isNotEmpty()
        if (operators.aggregation != Aggregation.NONE &&
            (allFilters.isNotEmpty() || elementKeys.size > 1 || aggregatesOverEverything)
        ) {
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
                // "the third densest element" wants one element, the third down the ranking,
                // not the top one. Carried as fieldOrdinal so the executor can skip past.
                val rankOrdinal = operators.rankOrdinal
                if (rankOrdinal != null) evidence.add("rank $rankOrdinal")
                return QueryPlan(
                    intent = if (isList && rankOrdinal == null) Intent.FILTER_LIST else Intent.SUPERLATIVE,
                    fieldIds = listOf(sortField),
                    fieldOrdinal = rankOrdinal,
                    filters = allFilters,
                    sortField = sortField,
                    sortDescending = operators.superlativeDescending ?: true,
                    limit = if (rankOrdinal != null) 1 else operators.topN ?: if (isList) DEFAULT_LIST_LIMIT else 1,
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
        // "compare the halogens on electronegativity" names no elements to compare — the subset is
        // the operand. The honest answer is the family ranked by that field, which is exactly what
        // this branch produces, so a compare cue counts as a list request when a subset is present.
        val comparesASubset = Lexicon.COMPARE.any { normalized.contains(it) } && elementKeys.isEmpty()
        // A bare family name is the request as well as the subject: "noble gases" wants the noble
        // gases. It carries no question word, so it needs naming here as well as in the guard that
        // stops it inheriting the previous turn.
        if (subsetFilters.isNotEmpty() &&
            (operators.isListQuestion || comparesASubset || namesOnlyAFamily(normalized, operators, elementKeys.isNotEmpty()))
        ) {
            val displayField = fieldIds.firstOrNull()
                ?.takeIf { FieldRegistry.byId[it]?.kind == FieldKind.NUMERIC }
            evidence.add("subset list")
            return QueryPlan(
                intent = Intent.FILTER_LIST,
                fieldIds = listOfNotNull(displayField),
                // Every filter, not just the subset: "which metals are lighter than aluminium"
                // is a subset question that also carries a threshold, and dropping it here
                // would list all metals.
                filters = allFilters,
                sortField = displayField,
                limit = DEFAULT_LIST_LIMIT,
                confidence = 0.75,
                rawQuery = rawQuery,
                evidence = evidence
            )
        }

        // "which elements have a recorded curie point" — a list question with a field and no
        // constraint at all. The set is everything with a value, which for the sparse fields is the
        // interesting part of the answer: the composer already reports how many of the pool were
        // missing, so this says "17 of 118" rather than implying the other 101 are zero.
        if (allFilters.isEmpty() && elementKeys.isEmpty() && operators.isListQuestion &&
            Lexicon.HAS_VALUE_WORDS.any { normalized.contains(it) }
        ) {
            val coverageField = fieldIds.firstOrNull()
                ?.takeIf { FieldRegistry.byId[it]?.kind == FieldKind.NUMERIC }
            if (coverageField != null) {
                evidence.add("field coverage")
                return QueryPlan(
                    intent = Intent.FILTER_LIST,
                    fieldIds = listOf(coverageField),
                    sortField = coverageField,
                    sortDescending = true,
                    limit = DEFAULT_LIST_LIMIT,
                    confidence = 0.75,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
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
            // The direct-comparative check ran further up, when only one element was known, so it
            // could not fire. Re-run it now that the pair is complete: "how does that compare to
            // iron" is a request for a winner, and without this it fell through to the side-by-side
            // table the branch below builds.
            comparativePlan(rawQuery, normalized, elementKeys, fieldIds)?.let { return it }
        }


        // ---- Neighbour: "what comes after carbon" -------------------------------------------
        elementKeys.firstOrNull()?.let { store.element(it) }?.let { subject ->
            neighbourDirection(normalized)?.let { direction ->
                store.byNumber(subject.atomicNumber + direction)?.let { target ->
                    evidence.add("neighbour ${if (direction > 0) "after" else "before"}")
                    return QueryPlan(
                        intent = Intent.NEIGHBOUR,
                        entities = listOf(EntityRef.Element(subject.key), EntityRef.Element(target.key)),
                        limit = direction,
                        confidence = 0.85,
                        rawQuery = rawQuery,
                        evidence = evidence
                    )
                }
            }
        }

        // ---- Element versus alloy: "what is the difference between iron and steel" ----------
        // An alloy is not an element, so a side-by-side property table is not available and not
        // what is being asked. The alloy's own entry explains how it differs from its base metal,
        // which is the answer.
        //
        // Unless the query also names an element *and* a field, in which case it is an ordinary
        // property lookup that happens to mention an alloy: "Young's modulus of steel's iron" asks
        // for iron's stiffness, and returning the encyclopaedia entry for steel answered a question
        // nobody asked. The alloy branch exists for queries the property path cannot serve.
        val asksForAFieldOfAnElement = elementKeys.isNotEmpty() && fieldIds.isNotEmpty()
        if (!asksForAFieldOfAnElement) alloyIn(normalized)?.let { alloy ->
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
        // "Which melts first, iron or copper" names two elements and asks to *choose* between them.
        // A side-by-side table technically contains the answer, but the question was a question:
        // when one field is in play and the query selects, answer with the winner.
        if (elementKeys.size == 2 && fieldIds.size == 1 &&
            Lexicon.SELECTIVE.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
        ) {
            comparativePlan(rawQuery, normalized, elementKeys, fieldIds)?.let {
                evidence.add("selective comparison")
                return it
            }
        }

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

        // "In the earth's crust" is one question, and the data answers it in two columns:
        // `earth_crust` covers 47 elements and `crustal_rocks` covers 72, with different elements
        // in each. Gold and oxygen are in the second and not the first, so asking for their crustal
        // abundance resolved a real field, found it empty and replied "no data recorded" — with the
        // number sitting in the sibling column. The two are the same measurement from different
        // sources, so falling through to whichever one the element actually has is not a guess.
        if (element != null) {
            fieldIds = fieldIds.map { fieldId ->
                CRUST_SIBLINGS[fieldId]
                    ?.takeIf { element.value(fieldId).isMissing && !element.value(it).isMissing }
                    ?: fieldId
            }
        }

        // ---- Isotopes and safety: their own shapes, not single fields ----------------------
        // Checked before field resolution because "isotopes" and "half life" both resolve to a
        // field label, which would answer with the common-neutron count instead of the list.
        if (element != null) {
            if (mentionsAny(normalized, Lexicon.ISOTOPE_WORDS)) {
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
            if (mentionsAny(normalized, Lexicon.SAFETY_WORDS)) {
                evidence.add("safety")
                return QueryPlan(
                    intent = Intent.SAFETY,
                    entities = listOf(EntityRef.Element(element.key)),
                    confidence = 0.85,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
            // Also checked before field resolution: "what does the emission spectrum of iron look
            // like" matched `appearance` and answered with the metal's colour, and "emission
            // spectrum of sodium" resolved nothing at all and was declined.
            if (mentionsAny(normalized, Lexicon.SPECTRUM_WORDS)) {
                evidence.add("emission spectrum")
                return QueryPlan(
                    intent = Intent.EMISSION_SPECTRUM,
                    entities = listOf(EntityRef.Element(element.key)),
                    confidence = 0.85,
                    rawQuery = rawQuery,
                    evidence = evidence
                )
            }
        }

        // ---- Category lookup: a whole family of properties for one element -----------------
        // A specific field wins over a family word. Several field names contain one — "thermal
        // conductivity" contains "thermal" — and because this branch sits above property lookup,
        // asking for one figure returned the whole family instead. The family reading is for
        // questions that name no field: "the thermal properties of gold", "how hard is gold",
        // "where is gold found".
        //
        // Abundance is the exception, and it has to be, because the nine abundance fields all carry
        // "abundance" in their labels — so "what is the abundance of gold" resolves a field and would
        // lose the whole-family answer it has always given. It keeps the family reading unless the
        // query narrows to one reservoir, which is what separates it from "how much gold is in sea
        // water".
        if (element != null &&
            (fieldMatches.isEmpty() || abundanceFamilyQuestion(normalized) || asksForAFamily(normalized))
        ) {
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
            // "Was ist mit Kupfer" opens with the same two words as "was ist Kupfer" and means the
            // opposite thing — it asks for the field the thread is already about. Without the
            // exemption the overview guard declined every German and Filipino "what about X".
            val overviewOnly = fieldMatches.isEmpty() &&
                    Lexicon.OVERVIEW_WORDS.any { normalized.contains(it) } &&
                    !asksWhatAbout(normalized)
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
        // A one- or two-word query carries too little signal for a lexical match to mean anything:
        // "sure" retrieved sulfur, "hey" and "42" retrieved dictionary rows. Anything that short and
        // unrecognised is conversational, and the personality layer is the honest owner of it.
        //
        // An element in the query does not disqualify it. "Standard electrode potential of zinc"
        // and "solubility of sodium chloride" both name an element and both are dataset questions —
        // the element alone cannot answer them, because no *field* resolved either, and declining
        // was the worst of the three options. The bar is raised when an element is present, so a
        // bare "tell me about gold" still falls through to the overview rather than retrieving
        // whichever row happens to mention gold.
        if ((elementKeys.isEmpty() || fieldIds.isEmpty()) &&
            com.jlindemann.science.ai.retrieval.TextMatching.splitQueryTokens(normalized).size >= MIN_DATASET_TOKENS
        ) {
            // A query naming something the corpus has never heard of must retrieve a row that is
            // at least *about* it. Neither test works alone: unknown words appear in perfectly good
            // questions ("define molar mass"), and title overlap alone would not have been needed.
            // Together they are what tells "define molar mass" from "write me a poem".
            // The best *dataset* row, not the best row overall. "Standard electrode potential of
            // zinc" ranks zinc itself first — of course it does, the query says zinc — and the
            // element hit is worth nothing here: it is already known, and no field resolved to look
            // up on it. Taking the top hit meant these questions were declined rather than answered
            // from the table that holds them.
            // Searched with the English name of anything the query named in another language. The
            // reference tables are English and BM25 is lexical, so "vad är stål gjort av" matched
            // nothing at all — the row is titled "Steel". Only the *search* string is glossed; the
            // answer is still composed in the user's language.
            val searchQuery = withDatasetGloss(rawQuery, normalized)
            val hit = retriever?.search(searchQuery, DATASET_SEARCH_DEPTH)
                ?.firstOrNull { it.ref is RetrievedRef.Dataset }
                ?.takeUnless { mentionsSomethingUnknown(rawQuery) && !retriever.looksRelevant(searchQuery, it) }
            val ref = hit?.ref
            // The bar stays higher when the query names an element: the element is itself strong
            // evidence the question is about it.
            //
            // Two relaxations were measured and rejected. Dropping this bar to the element-free
            // one recovers five dataset rows and loses four must-defer rows; gating the drop on the
            // retrieved row sharing a word with the query recovers the same five and still loses
            // three. Both are bad trades: declining "solubility of sodium chloride" is a gap, while
            // answering "what is the weather today" from the constants table is a wrong answer, and
            // a wrong answer costs more than a missing one.
            // Naming a *table topic* is the third possibility the two-way choice above missed.
            // "Standard electrode potential of zinc" names an element, so it got the high bar — but
            // it also names a subject no element field covers and that only the electrode table
            // holds. That is evidence of the same kind the element is, pointing the other way, and
            // it admits nothing the rejected relaxations did: chatter names no table.
            val namesATableTopic = Lexicon.DATASET_TOPIC_CUES.any { normalized.contains(it) }
            val minimumScore =
                if (elementKeys.isEmpty() || namesATableTopic) DATASET_CONFIDENCE
                else DATASET_CONFIDENCE_WITH_ELEMENT
            if (ref is RetrievedRef.Dataset && hit.score >= minimumScore) {
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
    /** Whether the query carries a degree word that could mark either degree. */
    private fun carriesADegreeWord(normalized: String): Boolean =
        mentionsAny(normalized, Lexicon.AMBIGUOUS_DEGREE)

    /**
     * Whether the query asks how something works rather than what a value is.
     *
     * A comparison cue disqualifies it. "How does it compare to lead" opens with the same "how
     * does" as "how does that change above the Curie temperature", but it has a grounded answer
     * and declining it would break the compound question it usually arrives in.
     */
    private fun asksHowItWorks(normalized: String): Boolean {
        if (Lexicon.MECHANISM_FRAMES.none { normalized.contains(it) }) return false
        if (Lexicon.COMPARE.any { normalized.contains(it) }) return false
        if (Lexicon.COMPARATIVE_ADJECTIVES.keys.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
        ) return false
        return true
    }

    private fun explanationPlan(rawQuery: String, normalized: String, hasElement: Boolean): QueryPlan? {
        val isWhy = Lexicon.WHY_WORDS.any { normalized.startsWith("$it ") || normalized.contains(" $it ") }
        // A definitional frame over a family named in the *plural* is a request for its members,
        // not for its definition. Deliberately narrowed to `isDefinition`: "why are noble gases
        // unreactive" is still a question the dictionary answers, and `isWhy` is untouched.
        val isDefinition = !hasElement &&
                Lexicon.DEFINITION_FRAMES.any { normalized.startsWith(it) } &&
                Lexicon.PLURAL_SERIES_WORDS.none { normalized.contains(it) }
        val isMechanism = asksHowItWorks(normalized)
        if (!isWhy && !isDefinition && !isMechanism) return null

        // Longest topic first, so "electron configuration unusual" beats "electron".
        val topic = Lexicon.EXPLANATION_TOPICS.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { normalized.contains(it.key) }

        // A "why" question is answered from the dictionary when the topic is genuinely what it is
        // about — "why are alkali metals so reactive" wants the reactivity entry. A *mechanism*
        // frame is not: it asks how one thing follows from another, and any topic it happens to
        // mention is incidental. Returning a definition there looks like an answer and is not one.
        val row = if (isMechanism) null else topic?.let { datasets.row(DatasetIndex.DICTIONARY, it.value) }

        // No explanation to give — so decline, rather than falling through to the property path.
        //
        // This is the single most damaging behaviour reported. A mechanism question mentions a real
        // field in passing, the planner resolves it, and the user gets a confident, cited number
        // answering a question they did not ask: "why does radius decrease left to right, despite
        // adding electrons" came back "Potassium's Electrons is 19". Saying nothing is a far better
        // answer than saying that, and it lets the personality layer offer what it can.
        if (row == null) {
            return if (isWhy || isMechanism) {
                QueryPlan(intent = Intent.UNKNOWN, confidence = 0.0, rawQuery = rawQuery)
            } else null
        }
        return QueryPlan(
            intent = Intent.DATASET_LOOKUP,
            entities = listOf(EntityRef.DatasetRow(DatasetIndex.DICTIONARY, row.id)),
            confidence = 0.85,
            rawQuery = rawQuery,
            evidence = listOf(if (isWhy) "explains ${topic?.value}" else "defines ${topic?.value}")
        )
    }

    /**
     * Whether the query explicitly asks for a *family* of properties.
     *
     * "Magnetic properties of iron" names a family even though `magnetic_type` also resolves, so the
     * specific-field-wins rule has to yield to the word "properties" itself.
     */
    private fun asksForAFamily(normalized: String): Boolean =
        FAMILY_WORDS.any { normalized.contains(it) }

    /**
     * Whether an abundance question is about the whole picture rather than one reservoir.
     *
     * "What is the abundance of gold" wants all nine figures; "how much gold is in sea water" wants
     * one. Both resolve an abundance field, because every one of those labels contains the word
     * "abundance", so the distinction has to come from whether a reservoir is named.
     */
    private fun abundanceFamilyQuestion(normalized: String): Boolean {
        if (categoryIn(normalized) != com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE) return false
        return RESERVOIR_WORDS.none { normalized.contains(it) }
    }

    /**
     * Whether a query reads as a continuation of the previous turn rather than a new question.
     *
     * Deliberately shape-based rather than a list of pronouns and connectives per language. A
     * follow-up that means "the same thing, for this element" is short — "and iron?", "what about
     * silver", "och silver", "und kupfer" — because everything else is carried by context. A query
     * long enough to be self-contained is treated as self-contained, which is what stops
     * "what is titanium used for" inheriting the field from two turns ago.
     */
    private fun readsAsFollowUp(normalized: String, limit: Int = MAX_FOLLOW_UP_TOKENS): Boolean =
        // Counted over whitespace-separated words rather than over tokens, because a token split
        // also breaks on the hyphen and the apostrophe: French "combien y en a-t-il" is four words
        // and six tokens, so a four-word follow-up was over the five-token limit and every French
        // "how many are there" was declined.
        normalized.trim().split(Regex("\\s+")).count { it.isNotEmpty() } <= limit

    /**
     * Whether the query names something the corpus has never heard of.
     *
     * The same signal as [continuesTheThread], turned on the dataset fallback. BM25 always has a
     * best answer however unlike the corpus the query is, and no threshold on its score can say
     * otherwise — the scores are normalised against the top hit, so the best one is always the same
     * number. What *can* be said with certainty is that "poem", "weather" and "dinner" appear in no
     * document, and a question built out of words the corpus has never seen is not a question about
     * the periodic table.
     *
     * An earlier attempt required the match to *share* a rare term with the query, and cost three
     * correct dataset answers to gain one rejection: legitimate matches rest on terms too common to
     * count ("gas" is in 28 of 265 documents). Absence is the reliable half of that idea.
     */
    private fun mentionsSomethingUnknown(rawQuery: String): Boolean =
        retriever?.mentionsUnknownTopic(rawQuery) ?: false

    /**
     * Whether a short turn is continuing the thread rather than changing the subject.
     *
     * Length alone said yes to "what's the weather today", which then inherited iron and its
     * electrical type and answered "Iron's Electrical Type is Conductor" — the thread's own subject,
     * attached to a question that had nothing to do with it.
     *
     * A word the corpus has never seen is the signal. Not that the match is weak — that the word is
     * absent entirely, which is the one thing an index answers with certainty. "Weather" appears in
     * no document; "fahrenheit", "density" and every element name do.
     *
     * The asymmetry is what makes this safe to act on: refusing to inherit produces a decline, which
     * the personality layer handles gracefully, while inheriting wrongly produces a cited answer to
     * a question nobody asked. When no retriever is wired the check cannot run and the old
     * length-only behaviour stands.
     */
    private fun continuesTheThread(normalized: String): Boolean {
        // An explicit anaphor settles it. The unknown-word test exists to catch a turn that changes
        // the subject, and "is it dangerous" plainly does not — while its content word is safety
        // vocabulary, which appears nowhere in a corpus built from element data and dataset rows.
        // Without this exemption the test silently broke localized follow-ups in eight languages.
        val anaphoric = Lexicon.ANAPHORS.any {
            com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
        }
        // An anaphor also buys two more words of room. Hindi and Urdu spell "its" as two words and
        // put the copula in a third, so the same follow-up that is five words in English —
        // "what are its isotopes" — is seven in Urdu, and the length gate declined it while the
        // identical four-word version in the same thread worked.
        if (!readsAsFollowUp(normalized, if (anaphoric) MAX_ANAPHORIC_FOLLOW_UP else MAX_FOLLOW_UP_TOKENS)) {
            return false
        }
        if (anaphoric) return true
        // So does an explicit "what about X" frame, for the same reason and with the same failure
        // if it is missing: "paano naman ang tanso" is built from two Filipino function words that
        // appear in no document, so the unknown-term test read a follow-up as a new subject.
        if (asksWhatAbout(normalized)) return true
        // So does a bare unit. "In celsius" is only ever a request to restate the previous answer,
        // and the transliterated unit names — सेल्सियस, سیلسیس, 摄氏度 — appear in no document, so
        // the unknown-term test read a two-word follow-up as a change of subject and declined it.
        if (Lexicon.UNIT_WORDS.keys.any {
                it.length >= MIN_UNIT_CUE &&
                        com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
        ) return true
        // Safety vocabulary appears in no document — the corpus is element data and dataset rows,
        // not hazard sheets — so "è pericoloso" reads as a change of subject on every measure the
        // index has. It is not one: it is the shortest possible follow-up. Same exemption as the
        // anaphor above, for the languages that ask it without a pronoun.
        if (Lexicon.SAFETY_WORDS.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
        ) return true
        return !mentionsSomethingUnknown(normalized)
    }

    /** Whether the query is the "same question, different subject" frame. */
    private fun asksWhatAbout(normalized: String): Boolean =
        Lexicon.WHAT_ABOUT_FRAMES.any { normalized.contains(it) }

    /**
     * Whether the query points at *one* subject already in play.
     *
     * The Romance interrogatives are all in [Lexicon.SELECTIVE] — "cuál", "quelle", "qual" — and
     * they open an ordinary question as readily as a choosing one: "cuál es su densidad" is "what
     * is its density", not "which of the two is denser". The singular possessive is what settles
     * it, and without this every fourth turn of a Spanish, French, Italian or Portuguese thread
     * turned into a comparison of the two elements before it.
     */
    private fun namesOneSubject(normalized: String): Boolean {
        val plural = Lexicon.PLURAL_ANAPHORS.any {
            com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
        }
        if (plural) return false
        return Lexicon.ANAPHORS.any {
            com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
        }
    }

    /**
     * [rawQuery] with the English name of any glossed topic appended.
     *
     * Appended rather than substituted, so the original words still contribute to the score and a
     * query that happens to contain a glossed word is not rewritten out from under itself.
     */
    private fun withDatasetGloss(rawQuery: String, normalized: String): String {
        // On word boundaries. A plain `contains` found the Swedish word for steel, "stål", inside
        // the English "cry**stal**lographic", so "crystallographic data for iron" was glossed with
        // "steel" and answered from the alloy table.
        val glosses = Lexicon.DATASET_GLOSS
            .filterKeys { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it) }
            .values
            .distinct()
        return if (glosses.isEmpty()) rawQuery else rawQuery + " " + glosses.joinToString(" ")
    }

    /** Whether the query is asking about isotopes, however elliptically. */
    private fun asksForIsotopes(normalized: String): Boolean =
        Lexicon.ISOTOPE_WORDS.any {
            com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
        }


    /**
     * Whether a query mentions one of a lexicon's phrases.
     *
     * Single Latin words match on word boundaries, multi-word phrases and non-spaced scripts by
     * containment. Plain `contains` over the whole list is what let "sure" match the French safety
     * word "sur" and answer a bare acknowledgement with an NFPA hazard rating.
     */
    private fun mentionsAny(normalized: String, phrases: List<String>): Boolean = phrases.any { phrase ->
        if (phrase.contains(' ') || phrase.any { it.code > 0x2000 }) return@any normalized.contains(phrase)
        if (com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, phrase)) return@any true
        // A short grammatical ending is still the same word — Swedish "farligt" for "farlig",
        // German "gefährliche" for "gefährlich". Only for phrases long enough that a suffix match
        // cannot be an accident; that length floor is what keeps "sure" from matching "sur".
        if (phrase.length < MIN_SUFFIX_MATCH) return@any false
        com.jlindemann.science.ai.retrieval.TextMatching.splitQueryTokens(normalized).any { word ->
            word.length > phrase.length &&
                    word.length - phrase.length <= MAX_WORD_SUFFIX &&
                    word.startsWith(phrase)
        }
    }


    /**
     * The element a nuclide names, in any supported language.
     *
     * `store.element` is keyed on the lowercase English name, so Swedish "uran 238" and German
     * "uran 238" resolved nothing and fell through to a property lookup. The entity resolver
     * already carries every language's names and symbols.
     */
    private fun nuclideElement(name: String): com.jlindemann.science.ai.data.ElementRecord? =
        store.element(name)
            ?: store.bySymbol(name)
            ?: entities.resolveAll(name, limit = 1).firstOrNull()?.let { store.element(it.key) }

    /** A nuclide written as "uranium-238", "U-238" or "carbon 14". */
    /**
     * An element name followed by a mass number.
     *
     * `\p{L}` rather than `a-z`: a nuclide is written in whatever script the user writes in —
     * "کاربن-14", "कार्बन-14", "碳-14" — and a Latin-only pattern found none of them, so every
     * non-Latin nuclide question was declined.
     */
    // `\p{M}` alongside `\p{L}` for the same reason the tokenizer needs it: an Indic vowel sign is
    // a mark, not a letter, so a letters-only run stopped at the first matra and "ऑक्सीजन-18" was
    // read as the nuclide "जन-18" — an element the table has never heard of, so the question was
    // declined outright.
    private val NUCLIDE = Regex("""([\p{L}\p{M}]{1,20})\s*[-\s]\s*(\d{1,3})""")

    /** A mole quantity, e.g. "2 moles of carbon". */
    /** A quantity in moles. Shared with the executor, which has to read the same number back. */
    private val MOLES = com.jlindemann.science.ai.data.ChemistryMath.MOLE_QUANTITY

    /** A mass in grams, in the spellings users actually write. */
    private val GRAMS = Regex("""([\d.]+)\s*(?:g|gram|grams|gm|gramm|gramme|grammes)\b""")

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
        // --- Balance an equation --------------------------------------------------------------
        // First, and on the raw query. The arrow is the most specific syntax any of these
        // questions carry, and `normalized` is lowercased, which destroys every formula in the
        // sentence. A plan is produced only when the equation actually parses, so "x -> y" written
        // in prose falls through to everything below untouched.
        if (EQUATION_ARROW.containsMatchIn(rawQuery)) {
            val sides = com.jlindemann.science.ai.data.EquationBalancer
                .parseEquation(rawQuery) { store.bySymbol(it) != null }
            if (sides != null) {
                return QueryPlan(
                    intent = Intent.BALANCE_EQUATION,
                    rawQuery = rawQuery,
                    confidence = 0.9,
                    evidence = listOf("equation ${sides.first.size} -> ${sides.second.size}")
                )
            }
        }

        // --- Solutions: molarity, mass↔moles↔concentration, dilution ---------------------------
        // Above the mole branch, because `MOLE_QUANTITY` matches the "0.5 mol" inside "0.5 mol/L"
        // and would answer a molarity question with a particle count.
        solutionPlan(rawQuery, normalized)?.let { return it }

        // --- Decay over time --------------------------------------------------------------------
        // Above the isotope branch further down: `ISOTOPE_WORDS` holds "decay", "half life" and
        // "how long does", so every one of these questions would otherwise be answered with the
        // isotope table instead of the arithmetic that was asked for.
        decayPlan(rawQuery, normalized)?.let { return it }

        // --- Moles to particles -------------------------------------------------------------
        if (mentionsAny(normalized, Lexicon.MOLE_WORDS)) {
            MOLES.find(normalized)?.let { match ->
                return QueryPlan(
                    intent = Intent.MOLE_CONVERSION,
                    rawQuery = rawQuery,
                    confidence = 0.85,
                    evidence = listOf("moles ${match.groupValues[1]}")
                )
            }
            // The other direction: a mass in grams, converted through the substance's molar mass.
            // "If I have 12 grams of carbon-12, how many moles is that" used to resolve magnesium
            // from the 12 and answer with a two-element list.
            GRAMS.find(normalized)?.let { match ->
                val substance = entities.resolveAll(rawQuery, limit = 1).firstOrNull()
                if (substance != null) {
                    return QueryPlan(
                        intent = Intent.MOLE_CONVERSION,
                        entities = listOf(EntityRef.Element(substance.key)),
                        rawQuery = rawQuery,
                        confidence = 0.85,
                        evidence = listOf("grams ${match.groupValues[1]}")
                    )
                }
            }
        }

        // --- Two nuclides compared: "uranium-235 vs uranium-238" -------------------------------
        val nuclides = NUCLIDE.findAll(normalized)
            .mapNotNull { match ->
                val element = nuclideElement(match.groupValues[1])
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
                val element = nuclideElement(name)
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

        // --- Half-life of a specific nuclide ---------------------------------------------------
        if (mentionsAny(normalized, Lexicon.ISOTOPE_WORDS)) {
            NUCLIDE.find(normalized)?.let { match ->
                val element = nuclideElement(match.groupValues[1])
                val mass = match.groupValues[2].toIntOrNull()
                if (element != null && mass != null && mass >= element.atomicNumber) {
                    return QueryPlan(
                        intent = Intent.ISOTOPES,
                        entities = listOf(EntityRef.Element(element.key)),
                        limit = DEFAULT_ISOTOPE_LIMIT,
                        rawQuery = rawQuery,
                        confidence = 0.9,
                        evidence = listOf("isotope ${element.key}-$mass")
                    )
                }
            }
        }

        // --- Formula mass and composition ------------------------------------------------------
        val wantsMass = Lexicon.MOLAR_MASS_WORDS.any { normalized.contains(it) }
        val wantsComposition = Lexicon.COMPOSITION_WORDS.any { normalized.contains(it) }
        if (!wantsMass && !wantsComposition) return null

        val candidate = compoundIn(rawQuery, normalized) ?: return null

        return QueryPlan(
            intent = Intent.FORMULA_MASS,
            fieldIds = if (wantsComposition) listOf("composition") else emptyList(),
            rawQuery = candidate,
            confidence = 0.9,
            evidence = listOf(if (wantsComposition) "composition of $candidate" else "molar mass of $candidate")
        )
    }

    /**
     * The compound a query is about, written as a formula or named in words.
     *
     * Shared by the formula-mass and solution branches so the two cannot disagree about what
     * "table salt" means.
     */
    private fun compoundIn(rawQuery: String, normalized: String): String? =
        // Prefer an explicit formula in the raw text, where capitalisation survives.
        FORMULA.findAll(rawQuery)
            .map { it.value }
            .filter { it.any { c -> c.isDigit() } || it.length > 2 }
            .firstOrNull { candidate ->
                com.jlindemann.science.ai.data.ChemistryMath
                    .parseFormula(candidate) { symbol -> atomicMassOf(symbol) } != null
            }
            // "percentage composition of water" names a compound rather than writing it.
            // Matched on word boundaries: a plain `contains` found "salt" inside "basalt", so
            // "what is the composition of basalt" was answered with the formula mass of NaCl.
            // Longest name first, so "table salt" is preferred over "salt".
            ?: Lexicon.COMMON_COMPOUNDS.entries
                .sortedByDescending { it.key.length }
                .firstOrNull {
                    com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it.key)
                }?.value

    private fun atomicMassOf(symbol: String): Double? =
        store.bySymbol(symbol)?.quantity("atomic_mass")?.value

    /**
     * A chemical equation's arrow.
     *
     * A bare `=` counts, since plenty of people write one, but never `==`, `<=`, `>=` or `!=` —
     * those turn up in comparisons, and reading one as an equation would claim a query that is not.
     */
    private val EQUATION_ARROW =
        Regex("""(?:<->|-{1,2}>|=>|→|⇌|⇄|⟶|(?<![<>=!])=(?![=>]))""")

    /**
     * A standalone unit conversion, on a number the user supplied.
     *
     * Every one of the conditions below exists to keep this off a query that already has a better
     * answer. Taken together they say: there is a number with a unit, a target was named, this is
     * not an element's property being asked for, nothing is being ranked or filtered, and the two
     * units actually relate.
     *
     * The property-lookup guard is deliberately run against the query with its **units removed**.
     * A unit token resolves a field all by itself — "GPa" reaches the pressure fields, "eV" the
     * ionisation energies, "Pa" even resolves protactinium — so a guard that simply asked whether
     * a field resolved declined "convert 5 GPa to MPa" on the strength of the very units being
     * converted. What actually distinguishes "density of gold in kg/m³" is that a field and an
     * element are named *outside* the unit expression, and that is what is checked here.
     */
    private fun unitConvertPlan(rawQuery: String, operators: Operators): QueryPlan? {
        // Ranking and filtering carry numbers with units too: "elements denser than 5 g/cm³".
        if (operators.comparators.isNotEmpty() || operators.subsetFilters.isNotEmpty()) return null
        if (operators.superlativeDescending != null || operators.topN != null) return null
        if (operators.aggregation != Aggregation.NONE || operators.range != null) return null

        val source = QuantityScanner.scan(rawQuery).firstOrNull() ?: return null
        // The scanner's own reading comes first. `OperatorExtractor.targetUnit` accepts any unit
        // with a preposition on either side, which is right for a property lookup — there is no
        // source quantity there to confuse it — but here it picks the unit being converted *from*:
        // in "convert 5 GPa to MPa" the GPa has "to" on its right, so it was read as the target and
        // the conversion collapsed to a no-op.
        val target = QuantityScanner.targetUnit(rawQuery) ?: operators.targetUnit ?: return null
        if (target == source.unit) return null
        // A dimensional mismatch means the query was misread. Declining is the honest move; the
        // alternative is inventing a relationship between a temperature and a volume.
        if (!com.jlindemann.science.ai.data.UnitConverter.convertible(source.unit, target)) return null

        val residual = withoutUnits(rawQuery, source)
        if (fields.resolveAll(residual, limit = 1).isNotEmpty() &&
            entities.resolveAll(residual, limit = 1).isNotEmpty()
        ) return null

        return QueryPlan(
            intent = Intent.UNIT_CONVERT,
            rawQuery = rawQuery,
            targetUnit = target,
            // Below the 0.9 of the syntax-driven branches, since a unit token is weaker evidence
            // than an arrow or a nuclide, and above PROPERTY_LOOKUP's 0.8 so a phrasing that
            // reaches both resolves toward the conversion.
            confidence = 0.85,
            evidence = listOf("convert ${source.unit} to $target")
        )
    }

    /**
     * The query with its unit expression taken out, for the property-lookup test above.
     *
     * Removes the scanned quantity's own span and every remaining token that is itself a unit, so
     * what is left is the words the sentence used to name something — which is where a genuine
     * property lookup keeps its field and its element.
     */
    private fun withoutUnits(rawQuery: String, source: ScannedQuantity): String {
        val withoutSource = rawQuery.removeRange(source.range)
        return withoutSource.split(' ')
            .filterNot { token ->
                val cleaned = token.trim(',', ';', '.', '(', ')')
                cleaned.isNotEmpty() && (
                        com.jlindemann.science.ai.data.UnitConverter.knownUnit(cleaned) != null ||
                                Lexicon.UNIT_WORDS.containsKey(cleaned.lowercase()) ||
                                Lexicon.SCRIPT_UNIT_WORDS.containsKey(cleaned)
                        )
            }
            .joinToString(" ")
    }

    /**
     * A solution-chemistry question: molarity, a mass from a concentration, or a dilution.
     *
     * Sits above the mole branch on purpose. `MOLE_QUANTITY` matches the "0.5 mol" inside
     * "0.5 mol/L", so left below it "how many grams of NaCl for 250 mL of 0.5 mol/L" would be
     * answered as a particle count.
     */
    private fun solutionPlan(rawQuery: String, normalized: String): QueryPlan? {
        val molarities = QuantityScanner.of(rawQuery, Dimension.MOLARITY)
        val volumes = QuantityScanner.of(rawQuery, Dimension.VOLUME)
        val amounts = QuantityScanner.of(rawQuery, Dimension.AMOUNT)
        val masses = GRAMS.findAll(normalized).count()

        val framed = mentionsAny(normalized, Lexicon.SOLUTION_WORDS)
        val dilutes = mentionsAny(normalized, Lexicon.DILUTION_WORDS)

        // Two independent quantities, or one plus an explicit frame. A single "250 mL" on its own
        // is not a solution question, and claiming it would take volume conversions away from the
        // converter that answers them properly.
        val quantities = molarities.size + volumes.size + amounts.size + masses
        if (quantities < 2) return null
        if (molarities.isEmpty() && !framed && !dilutes) return null

        return QueryPlan(
            intent = Intent.SOLUTION_CALC,
            rawQuery = rawQuery,
            literal = compoundIn(rawQuery, normalized),
            // An explicit molarity unit is unambiguous syntax, on a par with an arrow or a nuclide.
            // A frame word carrying units spelled out in words is weaker, and matches what the
            // mole branch below claims for the same kind of evidence.
            confidence = if (molarities.isNotEmpty()) 0.9 else 0.85,
            evidence = listOf(
                if (dilutes) "dilution" else "solution",
                "quantities $quantities"
            )
        )
    }

    /**
     * A decay-arithmetic question: how much is left after a time, or how long a decay takes.
     *
     * Sits above the isotope branch below, which fires at 0.9 on `ISOTOPE_WORDS` — a list holding
     * "decay", "half life", "stable" and "how long does". Placed after it, this intent would never
     * run at all.
     *
     * Requires a number to work with. Without that gate a bare "half-life of carbon-14" becomes an
     * arithmetic question with no arithmetic in it, and the isotope table answers that better.
     */
    private fun decayPlan(rawQuery: String, normalized: String): QueryPlan? {
        // Every match, not the first. The nuclide pattern is `word gap digits`, and a sentence like
        // "how much of 100 g of carbon-14 remains" matches "of 100" before it reaches the nuclide —
        // so taking the first match declines the question on the strength of the word "of".
        val nuclides = NUCLIDE.findAll(normalized)
            .mapNotNull { match ->
                val resolved = nuclideElement(match.groupValues[1])
                val number = match.groupValues[2].toIntOrNull()
                if (resolved != null && number != null && number >= resolved.atomicNumber) {
                    resolved to number
                } else null
            }
            .distinct()
            .take(2)
            .toList()
        // Two nuclides written out is a comparison — "uranium-235 vs uranium-238" — and the branch
        // below answers it properly. Claiming it here would answer a side-by-side question with
        // arithmetic about one of the two.
        if (nuclides.size != 1) return null
        val (element, mass) = nuclides.single()

        val times = QuantityScanner.of(rawQuery, Dimension.TIME)
        val amounts = GRAMS.findAll(normalized).count() +
                QuantityScanner.of(rawQuery, Dimension.AMOUNT).size
        val asksHowMuch = mentionsAny(normalized, Lexicon.REMAINS_WORDS)

        if (times.isEmpty() && amounts < 2) return null

        return QueryPlan(
            intent = Intent.DECAY_CALC,
            entities = listOf(EntityRef.Element(element.key)),
            // The mass number rides in `limit`, as the neutron-count branch below already does.
            limit = mass,
            rawQuery = rawQuery,
            confidence = if (times.isNotEmpty() && (asksHowMuch || amounts > 0)) 0.9 else 0.85,
            evidence = listOf("decay ${element.key}-$mass")
        )
    }

    /**
     * Aspects a multi-part comparison asked for that no stored field can supply.
     *
     * Naming them lets the answer say what it could not cover, instead of quietly answering a
     * narrower question than the one asked.
     */
    private fun unsupportedAspects(normalizedQuery: String): List<String> =
        UNCOMPARABLE_ASPECTS.filter { normalizedQuery.contains(it) }

    /**
     * Recognise a question about how two elements stand relative to one another.
     *
     * Covers three phrasings that all reduce to the same operation:
     *  - "is gold denser than lead"          -> a claim to confirm or deny
     *  - "which is heavier, gold or silver"  -> pick the winner
     *  - "how much denser is gold than lead" -> the ratio
     *
     * The comparative adjective usually *is* the property, so it is consulted when no field was
     * resolved explicitly — "denser" means density, "heavier" means atomic mass.
     */
    private fun comparativePlan(
        rawQuery: String,
        normalized: String,
        elementKeys: List<String>,
        fieldIds: List<String>
    ): QueryPlan? {
        val adjective = Lexicon.COMPARATIVE_ADJECTIVES.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it.key) }

        val fieldId = adjective?.value?.first ?: fieldIds.firstOrNull() ?: return null
        val greaterWins = adjective?.value?.second ?: true

        // Against the raw query too: the Romance copulas differ from their conjunctions only by an
        // accent, which normalisation folds away.
        val opener = rawQuery.trim().lowercase()
        val isYesNo = Lexicon.YESNO_OPENERS.any { normalized.startsWith(it) || opener.startsWith(it) }
        val isWhich = Lexicon.WHICH_OF_TWO.any { normalized.contains(it) }
        val byHowMuch = Lexicon.BY_HOW_MUCH.any { normalized.contains(it) }
        // Without one of these framings it is an ordinary side-by-side comparison.
        if (!isYesNo && !isWhich && !byHowMuch && adjective == null) return null

        return QueryPlan(
            intent = Intent.COMPARATIVE,
            entities = elementKeys.take(2).map { EntityRef.Element(it) },
            fieldIds = listOf(fieldId),
            sortDescending = greaterWins,
            // limit doubles as the question shape: 1 yes/no, 2 which-of, 3 by-how-much.
            limit = when {
                byHowMuch -> 3
                isYesNo -> 1
                else -> 2
            },
            confidence = 0.85,
            rawQuery = rawQuery,
            evidence = listOf("comparative $fieldId")
        )
    }

    /**
     * A threshold expressed as another element: "denser than iron", "hotter than tungsten".
     *
     * Takes the named element's own value for the field as the bound, so the comparison is
     * against a real figure rather than requiring the user to know it.
     */
    private fun elementThreshold(
        normalized: String,
        elementKeys: List<String>,
        fieldIds: List<String>
    ): com.jlindemann.science.ai.core.Filter.FieldCompare? {
        val greater = Lexicon.GREATER.any { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it) }
        val less = Lexicon.LESS.any { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it) }
        val adjective = Lexicon.COMPARATIVE_ADJECTIVES.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it.key) }

        // Periphrastic comparatives: the Romance languages build "denser than gold" out of three
        // separate words — "plus denses que l'or", "más densos que el oro" — so neither a
        // single comparative adjective nor a GREATER phrase is present to find. The pattern is
        // a degree word plus a comparison particle, with the field carried by the bare adjective
        // ("denses"), which `FieldResolver` already resolves. English "more X than" lands here too.
        val particle = Lexicon.THAN_WORDS.any {
            com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
        }
        val periphrasticGreater = particle &&
                Lexicon.MOST.any { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it) }
        val periphrasticLess = particle &&
                Lexicon.LEAST.any { com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it) }

        if (!greater && !less && adjective == null && !periphrasticGreater && !periphrasticLess) return null

        val fieldId = adjective?.value?.first ?: fieldIds.firstOrNull() ?: return null
        val reference = elementKeys.firstNotNullOfOrNull { store.element(it) } ?: return null
        val bound = reference.quantity(fieldId) ?: return null

        val wantsGreater = when {
            adjective != null -> adjective.value.second
            greater -> true
            less -> false
            else -> periphrasticGreater && !periphrasticLess
        }
        return com.jlindemann.science.ai.core.Filter.FieldCompare(
            fieldId,
            if (wantsGreater) com.jlindemann.science.ai.core.Op.GT else com.jlindemann.science.ai.core.Op.LT,
            bound
        )
    }

    /**
     * Whether the query is a family name and little else.
     *
     * The token budget is what makes this safe: "noble gases" and "the halogens" qualify, while
     * "which noble gas has the highest boiling point" does not and is handled by the superlative
     * branch above.
     */
    private fun namesOnlyAFamily(
        normalized: String,
        operators: Operators,
        hasElement: Boolean
    ): Boolean {
        // An element in the query means it is the subject, however short the query is: "is gold
        // radioactive" is three tokens and carries a subset filter, but it asks about gold.
        if (hasElement) return false
        // An anaphor means the subject is already in play. "Is it radioactive" is the same length
        // as "noble gases" and carries a family word too, but it is a follow-up, not a request for
        // every radioactive element.
        if (Lexicon.ANAPHORS.any {
                com.jlindemann.science.ai.retrieval.TextMatching.containsWord(normalized, it)
            }
        ) return false
        if (operators.subsetFilters.isEmpty()) return false
        if (operators.aggregation != Aggregation.NONE || operators.superlativeDescending != null) return false
        val tokens = com.jlindemann.science.ai.retrieval.TextMatching.splitQueryTokens(normalized)
            .filter { it.isNotBlank() }
        return tokens.size <= MAX_BARE_FAMILY_TOKENS
    }

    /** +1 for the element after, -1 for the one before, null when not a neighbour question. */
    private fun neighbourDirection(normalizedQuery: String): Int? =
        Lexicon.NEIGHBOUR_WORDS.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { normalizedQuery.contains(it.key) }
            ?.value

    /**
     * The alloy a query names, longest name first so "stainless steel" beats "steel".
     *
     * The alloy table is English, so the query is glossed first: "vad är stål gjort av" names Steel
     * as plainly as "what is steel made of" does, and matching only the English title left it to
     * lexical retrieval, which ranked *Stainless steel* above it.
     */
    private fun alloyIn(normalizedQuery: String): com.jlindemann.science.ai.data.DatasetRow? {
        val glossed = withDatasetGloss(normalizedQuery, normalizedQuery)
        return datasets.dataset(DatasetIndex.ALLOY)
            .sortedByDescending { it.title.length }
            .firstOrNull {
                com.jlindemann.science.ai.retrieval.TextMatching
                    .containsWord(glossed, it.title.lowercase())
            }
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
        /**
         * Raising this does not filter off-topic questions, which was tried and measured.
         *
         * "What is the weather today" and "write me a poem" clear 0.55 as readily as 0.45, because
         * BM25 over a 780-document corpus finds some lexical overlap for almost any English
         * sentence. Rejecting off-topic input needs a different signal than a score threshold, so
         * the value stays where it is rather than being tightened for no gain.
         */
        /**
         * Abundance columns that measure the same thing from a different source.
         *
         * Only the crustal pair. The other seven reservoirs — sea water, the Sun, meteorites, the
         * human body — are genuinely different places, and substituting between them would answer
         * a question about one with a figure from another.
         */
        val CRUST_SIBLINGS = mapOf(
            "abundance_earth_crust" to "abundance_crustal_rocks",
            "abundance_crustal_rocks" to "abundance_earth_crust"
        )

        const val DATASET_CONFIDENCE = 0.45
        const val DEFAULT_LIST_LIMIT = 10
        const val DEFAULT_ISOTOPE_LIMIT = 8

        /**
         * Shortest query a lexical dataset match is trusted on.
         *
         * One or two words carry too little signal: "sure" retrieved sulfur and "hey" retrieved a
         * dictionary row, both above the fallback's confidence floor.
         */
        const val MIN_DATASET_TOKENS = 3

        /**
         * How far down the ranking to look for a dataset row.
         *
         * Deep enough to see past the elements a question names — "solubility of sodium chloride"
         * ranks both sodium and chlorine ahead of the solubility row — and shallow enough that a
         * genuinely unrelated row never surfaces.
         */
        const val DATASET_SEARCH_DEPTH = 6

        /**
         * Shortest token treated as naming a topic rather than joining a sentence.
         *
         * Four, so that pronouns and prepositions — which are absent from the corpus for reasons
         * that have nothing to do with the subject — cannot block a legitimate follow-up.
         */
        const val MIN_TOPIC_WORD = 4

        /**
         * Longest query still read as "just a family name".
         *
         * Three, to cover a definite article and a plural in the languages that write them
         * separately ("the noble gases"), while staying well short of a real question.
         */
        const val MAX_BARE_FAMILY_TOKENS = 3

        /**
         * The bar a dataset match must clear when the query also names an element.
         *
         * Higher than [DATASET_CONFIDENCE], because an element name is itself strong evidence that
         * the question is about the element. Only a clearly better lexical match for something else
         * should win, which is what "solubility of sodium chloride" is and "tell me about gold" is not.
         */
        const val DATASET_CONFIDENCE_WITH_ELEMENT = 0.6

        /** Shortest lexicon word that may be matched by stem rather than by whole word. */
        const val MIN_SUFFIX_MATCH = 5

        /** Longest grammatical ending accepted on a lexicon word. */
        const val MAX_WORD_SUFFIX = 3

        /**
         * Longest query that still reads as a follow-up for the purpose of inheriting a field.
         *
         * Five words covers the shapes the mechanism exists for — "and iron?", "what about silver",
         * "and its melting point" — without swallowing a self-contained question that happens to
         * resolve no field.
         */
        const val MAX_FOLLOW_UP_TOKENS = 5

        /** The same, for a turn that carries an explicit pronoun. See [continuesTheThread]. */
        const val MAX_ANAPHORIC_FOLLOW_UP = 7

        /** Shortest unit name that counts as a follow-up cue; below this it is a stray letter. */
        const val MIN_UNIT_CUE = 3

        /** Words that explicitly ask for a family of properties rather than one of them. */
        val FAMILY_WORDS = listOf(
            "properties", "egenskaper", "eigenschaften", "propriedades", "propiedades",
            "proprietes", "proprieta", "eienskappe", "katangian", "गुण", "خواص", "性质"
        )

        /**
         * Reservoirs an abundance question can name, which narrow it to a single field.
         *
         * Pre-folded for diacritics, matching how the query is normalised before it gets here.
         */
        val RESERVOIR_WORDS = listOf(
            "crust", "soil", "urban", "sea water", "seawater", "ocean", "rock", "sun", "solar",
            "meteorite", "human body", "in the body",
            "jordskorpan", "jord", "havsvatten", "berg", "solen", "solsystem", "meteoriter",
            "manniskokroppen", "kroppen",
            "erdkruste", "boden", "meerwasser", "gestein", "sonne", "sonnensystem", "meteoriten",
            "korper", "corteza", "agua de mar", "cuerpo", "croute", "eau de mer", "corps",
            "地壳", "海水", "人体"
        )

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
            // A superlative that names no field takes it from the adjective. The non-English forms
            // matter as much as the English ones: "quale elemento è il più denso" resolved no field
            // at all and was declined outright.
            "tat" to "density", "tatt" to "density",
            "piu denso" to "density", "mais denso" to "density",
            "densidad" to "density", "pinakadensidad" to "density",
            "ghana" to "density", "kasif" to "density",
            "elektronegativt" to "electronegativity", "elektronegativ" to "electronegativity",
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
