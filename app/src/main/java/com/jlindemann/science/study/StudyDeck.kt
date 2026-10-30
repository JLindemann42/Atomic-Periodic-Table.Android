package com.jlindemann.science.study

/**
 * The study coach's cards (roadmap 2.6): one per skill and element, keyed `<skill>:<element>`, each
 * with its FSRS schedule. Pure and thread-safe; the app persists it and syncs it through the
 * progress document. Specified in the shared data's `sync/study.md`.
 */
class StudyDeck(cards: Map<String, CardState> = emptyMap()) {

    private val cards = LinkedHashMap(cards)

    /** Every card, as the sync writes them. */
    @Synchronized
    fun snapshot(): Map<String, CardState> = LinkedHashMap(cards)

    @Synchronized
    fun card(key: String): CardState? = cards[key]

    /**
     * Review the card for [skill] about [elementKey] after an answer. A skill the coach does not
     * schedule is ignored, so a caller can report every answer without checking.
     *
     * @return the card's key, or null when the question does not feed a card
     */
    @Synchronized
    fun record(skill: String?, elementKey: String?, correct: Boolean, now: Long): String? {
        if (skill == null || elementKey.isNullOrBlank() || skill !in SKILLS) return null
        val key = key(skill, elementKey)
        cards[key] = Fsrs.review(cards[key], if (correct) Fsrs.GOOD else Fsrs.AGAIN, now)
        return key
    }

    /**
     * The due cards most overdue first - by how far past due they are, relative to their interval -
     * at most [limit].
     */
    @Synchronized
    fun due(now: Long, limit: Int = REVIEW_SIZE): List<String> =
        cards.entries
            .filter { it.value.due <= now }
            .sortedByDescending { (_, card) ->
                (now - card.due).toDouble() / maxOf(card.due - card.last, Fsrs.DAY_MS)
            }
            .take(limit)
            .map { it.key }

    /** Fold in cards from elsewhere (the cloud), keeping each card's later review. */
    @Synchronized
    fun mergeFrom(other: Map<String, CardState>) {
        for ((key, card) in other) cards[key] = newer(cards[key], card) ?: card
    }

    companion object {
        /** The Learning Games categories whose questions are about one given element. */
        val SKILLS = setOf(
            "element_classifications", "periodic_period", "periodic_group", "valence_electrons", "isotope_neutrons"
        )

        const val REVIEW_SIZE = 10

        fun key(skill: String, elementKey: String): String = "$skill:${elementKey.lowercase()}"

        /** The skill and element of a key, or null for one this build cannot read. */
        fun parse(key: String): Pair<String, String>? {
            val at = key.indexOf(':')
            if (at <= 0 || at == key.length - 1) return null
            return key.substring(0, at) to key.substring(at + 1)
        }

        /**
         * The card with the later review; on a tie the one with more reps, then [cloud]. Shared by
         * the merge in [com.jlindemann.science.sync.ProgressMerge].
         */
        fun newer(local: CardState?, cloud: CardState?): CardState? = when {
            local == null -> cloud
            cloud == null -> local
            local.last != cloud.last -> if (local.last > cloud.last) local else cloud
            local.reps != cloud.reps -> if (local.reps > cloud.reps) local else cloud
            else -> cloud
        }

        /** One card per line, `key\ts\td\tdue\tlast\treps\tlapses`, for the app's local file. */
        fun encode(cards: Map<String, CardState>): String = cards.entries.joinToString("\n") { (key, c) ->
            listOf(key, c.stability, c.difficulty, c.due, c.last, c.reps, c.lapses).joinToString("\t")
        }

        fun decode(text: String?): Map<String, CardState> = text.orEmpty().lines().mapNotNull { line ->
            val cells = line.split('\t')
            if (cells.size != 7) return@mapNotNull null
            val card = CardState(
                stability = cells[1].toDoubleOrNull() ?: return@mapNotNull null,
                difficulty = cells[2].toDoubleOrNull() ?: return@mapNotNull null,
                due = cells[3].toLongOrNull() ?: return@mapNotNull null,
                last = cells[4].toLongOrNull() ?: return@mapNotNull null,
                reps = cells[5].toIntOrNull() ?: return@mapNotNull null,
                lapses = cells[6].toIntOrNull() ?: return@mapNotNull null
            )
            cells[0] to card
        }.toMap()
    }
}
