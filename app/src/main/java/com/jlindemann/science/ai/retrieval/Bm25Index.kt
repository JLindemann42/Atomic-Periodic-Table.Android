package com.jlindemann.science.ai.retrieval

import kotlin.math.ln

/**
 * A document in the retrieval corpus. [id] is prefixed by kind, e.g. `element:gold`.
 *
 * @property altTitles the title in the index's language, and other wordings of it (roadmap 1.3);
 *   they count as title for coverage, so "neutronens massa" names the "Electron mass" row's
 *   Swedish title as fully as "electron mass" names the English one
 */
data class Document(val id: String, val title: String, val body: String, val altTitles: List<String> = emptyList())

/**
 * A scored search result.
 *
 * Whether [score] is a raw BM25 sum or a value normalised against the top hit depends on which
 * search method produced it — see [Bm25Index.searchNormalized], whose scores are ordinal only.
 */
data class SearchHit(val id: String, val title: String, val score: Double)

/**
 * How well one document covers a query's content (roadmap 1.1). Unlike a BM25 score these are
 * absolute - a fraction of *this query's* weight - so a threshold on them means the same thing for
 * every query, and a query nothing covers scores low instead of winning by default.
 *
 * @property coverage the share of the query's content weight found anywhere in the document
 * @property titleRecall the share of the query's content weight found in the title
 * @property titlePrecision the share of the title's weight the query names; 1.0 when the query
 *   names everything the title says, which is what tells "tell me about quartz" the mineral row
 *   ("Quartz") from the elastic one ("α-Quartz (SiO2)")
 * @property bm25 the raw BM25 score, kept as the tie-break
 * @property words how many content words the query had
 * @property namesTitle whether the query names every word of the title, in one of its wordings;
 *   a formula in brackets is notation, not a word, so "periclase" names "Periclase (MgO)" while
 *   "life" does not name "Half-Life"
 */
data class Coverage(
    val id: String,
    val title: String,
    val coverage: Double,
    val titleRecall: Double,
    val titlePrecision: Double,
    val bm25: Double,
    val words: Int,
    val namesTitle: Boolean = false
)

/**
 * Okapi BM25 over the app's own data.
 *
 * This replaces the previous embedding search, which could never work: its query embedder
 * returned a hash-seeded pseudo-random vector, so cosine scores hovered near zero and the 0.65
 * threshold gating it never fired.
 *
 * `b` is set below the usual 0.75 because the corpus is deliberately heterogeneous — a
 * three-token Poisson row sits beside a two-hundred-token element description — and a lower `b`
 * reduces the length normalisation that would otherwise over-favour the short rows.
 */
class Bm25Index(
    documents: List<Document>,
    private val language: String = "en",
    private val k1: Double = 1.2,
    private val b: Double = 0.6
) {

    private val ids: Array<String> = Array(documents.size) { documents[it].id }
    private val titles: Array<String> = Array(documents.size) { documents[it].title }
    /** Each document's title and alternative titles, tokenized separately. */
    private val titleVariants: Array<List<Set<String>>> = Array(documents.size) { i ->
        (listOf(documents[i].title) + documents[i].altTitles).map { Tokenizer.tokenize(it, language).toHashSet() }
    }

    /**
     * Each title wording as its words, each word as the terms it tokenizes to, with any bracketed
     * formula left out. A hyphen separates words here, as it does for the tokenizer: "Half-Life" is
     * two words, and naming one of them is not naming the title.
     */
    private val titleWords: Array<List<List<Set<String>>>> = Array(documents.size) { i ->
        (listOf(documents[i].title) + documents[i].altTitles).map { title ->
            BRACKETED.replace(title, " ").split(NON_WORD).filter { it.isNotEmpty() }
                .map { Tokenizer.tokenize(it, language).toHashSet() }
                .filter { it.isNotEmpty() }
        }
    }

    /** Every title term of a document, in any variant. */
    private val titleTerms: Array<Set<String>> = Array(documents.size) { i -> titleVariants[i].flatten().toHashSet() }
    private val lengths = IntArray(documents.size)

    /** term -> (document index -> term frequency). */
    private val postings = HashMap<String, MutableMap<Int, Int>>()
    private var averageLength = 0.0

    val size: Int get() = ids.size

    init {
        var total = 0L
        documents.forEachIndexed { docIndex, doc ->
            val tokens = Tokenizer.tokenize("${doc.title} ${doc.body}", language)
            lengths[docIndex] = tokens.size
            total += tokens.size
            for (token in tokens) {
                val bucket = postings.getOrPut(token) { HashMap() }
                bucket[docIndex] = (bucket[docIndex] ?: 0) + 1
            }
        }
        averageLength = if (documents.isEmpty()) 0.0 else total.toDouble() / documents.size
    }

    /**
     * Search the index.
     *
     * @param query raw query text; tokenized exactly as documents were
     * @param limit maximum hits to return
     * @param prefixFilter when set, restricts results to ids starting with it — a large precision
     *   win when the planner already knows it wants an element rather than a dictionary entry
     */
    fun search(query: String, limit: Int = 5, prefixFilter: String? = null): List<SearchHit> {
        val tokens = Tokenizer.tokenize(query, language)
        if (tokens.isEmpty() || ids.isEmpty()) return emptyList()

        val scores = HashMap<Int, Double>()
        for (token in tokens.distinct()) {
            val bucket = postings[token] ?: continue
            val idf = inverseDocumentFrequency(bucket.size)
            if (idf <= 0.0) continue
            for ((docIndex, frequency) in bucket) {
                if (prefixFilter != null && !ids[docIndex].startsWith(prefixFilter)) continue
                val tf = frequency.toDouble()
                val norm = 1.0 - b + b * (lengths[docIndex] / averageLength.coerceAtLeast(1.0))
                scores.merge(docIndex, idf * tf * (k1 + 1.0) / (tf + k1 * norm), Double::plus)
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { SearchHit(ids[it.key], titles[it.key], it.value) }
    }

    /**
     * Search and normalise scores into 0..1 relative to the top hit.
     *
     * **These scores are ordinal, not absolute.** Dividing by the top hit means the best result
     * always scores exactly 1.0 — whether it was an excellent match or the least bad of a corpus
     * that contains nothing relevant. A threshold applied to this number therefore answers nothing
     * about match quality, which is why raising the planner's dataset threshold from 0.45 to 0.55
     * had no measurable effect: every candidate arrived at exactly the same value.
     *
     * Use [search] if you need magnitudes that can be compared across queries.
     */
    fun searchNormalized(query: String, limit: Int = 5, prefixFilter: String? = null): List<SearchHit> {
        val hits = search(query, limit, prefixFilter)
        val top = hits.firstOrNull()?.score ?: return hits
        if (top <= 0.0) return hits
        return hits.map { it.copy(score = it.score / top) }
    }

    /**
     * Score every document that holds any of the query's content terms by how much of that content
     * it covers (roadmap 1.1).
     *
     * [searchNormalized] divides by the top hit, so the best match always scores 1.0 and no
     * threshold can tell a good match from the least bad one. Coverage divides by the *query*: the
     * IDF of the content terms a document contains over the IDF of all of them. A term the corpus
     * has never seen counts at full weight, so "what is the nernst equation" cannot be covered by
     * the Arrhenius equation just because both say "equation".
     *
     * Every document holding a content term is scored, not BM25's top few: "Electron mass" is not
     * in BM25's top six for "what is the mass of an electron", and re-ranking those could never
     * find it.
     *
     * @param stopwords words that are not content, already folded; tokenized here like the query
     * @param accept which document ids may be returned
     * @return candidates in no particular order; ranking is the caller's policy
     */
    fun coverage(query: String, stopwords: Collection<String>, accept: (String) -> Boolean): List<Coverage> {
        val stop = stopwords.flatMapTo(HashSet()) { Tokenizer.tokenize(it, language) }
        val groups = contentWords(query, stop)
        if (groups.isEmpty() || ids.isEmpty()) return emptyList()
        val strictForms = Tokenizer.tokenize(query, language).toHashSet()

        // A word weighs what its most specific form the corpus knows weighs; a word the corpus has
        // never seen in any form weighs the most a term can. Only the tokenizer's own forms set the
        // weight - the looser forms [contentWords] adds may only say the word is present.
        val weights = groups.map { forms ->
            forms.filter { it in strictForms && documentFrequency(it) > 0 }
                .maxOfOrNull { inverseDocumentFrequency(documentFrequency(it)) }
                ?: forms.filter { documentFrequency(it) > 0 }
                    .maxOfOrNull { inverseDocumentFrequency(documentFrequency(it)) }
                ?: inverseDocumentFrequency(0)
        }
        val total = weights.sum()
        if (total <= 0.0) return emptyList()

        val covered = HashMap<Int, Double>()
        val inTitle = HashMap<Int, Double>()
        val bm25 = HashMap<Int, Double>()
        groups.forEachIndexed { g, forms ->
            val w = weights[g]
            val inDoc = HashSet<Int>()
            val inDocTitle = HashSet<Int>()
            for (term in forms) {
                val bucket = postings[term] ?: continue
                for ((docIndex, frequency) in bucket) {
                    if (!accept(ids[docIndex])) continue
                    inDoc.add(docIndex)
                    if (term in titleTerms[docIndex]) inDocTitle.add(docIndex)
                    val tf = frequency.toDouble()
                    val norm = 1.0 - b + b * (lengths[docIndex] / averageLength.coerceAtLeast(1.0))
                    bm25.merge(docIndex, w * tf * (k1 + 1.0) / (tf + k1 * norm), Double::plus)
                }
            }
            for (docIndex in inDoc) covered.merge(docIndex, w, Double::plus)
            for (docIndex in inDocTitle) inTitle.merge(docIndex, w, Double::plus)
        }

        val termSet = groups.flatten().toHashSet()
        return covered.map { (docIndex, sum) ->
            // The best of the title's wordings: a query names one of them, not all at once.
            val titlePrecision = titleVariants[docIndex].maxOfOrNull { variant ->
                val title = variant.filter { it !in stop }
                val titleWeight = title.sumOf { inverseDocumentFrequency(documentFrequency(it)) }
                val named = title.filter { it in termSet }.sumOf { inverseDocumentFrequency(documentFrequency(it)) }
                if (titleWeight > 0.0) named / titleWeight else 0.0
            } ?: 0.0
            Coverage(
                id = ids[docIndex],
                title = titles[docIndex],
                coverage = sum / total,
                titleRecall = (inTitle[docIndex] ?: 0.0) / total,
                titlePrecision = titlePrecision,
                bm25 = bm25[docIndex] ?: 0.0,
                words = groups.size,
                namesTitle = titleWords[docIndex].any { wording ->
                    val content = wording.filter { forms -> forms.any { it !in stop } }
                    content.isNotEmpty() && content.all { forms -> forms.any { it in termSet } }
                }
            )
        }
    }

    /**
     * The query's content words, each as the index terms it tokenizes to.
     *
     * [Tokenizer] emits a word's surface form and its folded stem as separate terms ("joules",
     * "joul"). Counted separately, the surface form the corpus never wrote down weighed as an unknown
     * word. A word and its stem are one word: covered when any form is. Han text is the exception -
     * its terms are single characters and bigrams, not variants of one word - so each stays its own.
     *
     * The tokenizer folds a suffix only off words longer than five letters, so "joules" becomes
     * "joul" while the row's "joule" stays "joule" and the two never meet. Changing that would move
     * every BM25 score and the shared tokenizer golden files, so instead a Latin word also tries
     * itself with one plural or inflection ending removed, whatever its length.
     */
    private fun contentWords(query: String, stop: Set<String>): List<List<String>> {
        val words = ArrayList<List<String>>()
        val seen = HashSet<String>()
        for (word in query.split(WHITESPACE)) {
            val forms = Tokenizer.tokenize(word, language).distinct()
                .filter { it !in stop && it !in seen && it.none(Char::isDigit) }
            if (forms.isEmpty()) continue
            seen.addAll(forms)
            val stem = forms.minBy { it.length }
            if (!forms.all { it.startsWith(stem) }) {
                forms.filterNot { isStraddlingBigram(it, stop) }.forEach { words.add(listOf(it)) }
                continue
            }
            val looser = forms.flatMap { form ->
                LOOSE_ENDINGS.filter { form.endsWith(it) && form.length - it.length >= MIN_LOOSE_STEM }
                    .map { form.dropLast(it.length) }
            }
            words.add((forms + looser).distinct())
        }
        return words
    }

    /**
     * A Han bigram that runs across a function character and that no document contains.
     *
     * Han bigrams are every adjacent pair, so 动能的公式 also yields 能的 and 的公. Those are not
     * words but seams around the particle 的, and counted as unknown content they outweighed the
     * whole question. A bigram the corpus does contain stays, function character or not.
     */
    private fun isStraddlingBigram(term: String, stop: Set<String>): Boolean =
        term.length == 2 && term.all(Tokenizer::isHan) &&
                term.any { it.toString() in stop } && documentFrequency(term) == 0

    /**
     * Probabilistic IDF with the +1 smoothing that keeps it non-negative.
     * A term in every document scores ~0, which is what makes a stopword list unnecessary.
     */
    private fun inverseDocumentFrequency(documentFrequency: Int): Double =
        ln(1.0 + (size - documentFrequency + 0.5) / (documentFrequency + 0.5))

    /**
     * Whether the query contains a content word the corpus has never seen.
     *
     * Tokenised here rather than by the caller, because the answer is only meaningful against the
     * same vocabulary the postings were built from: [Tokenizer] stems, and a query split any other
     * way produces terms that are "absent" for reasons that have nothing to do with the subject.
     *
     * @param minimumLength below this a token is a function word, which is absent or present for
     *   reasons unrelated to the topic.
     */
    fun mentionsUnknownTerm(query: String, minimumLength: Int = 4): Boolean =
        Tokenizer.tokenize(query, language).any { term ->
            term.length >= minimumLength &&
                    term.none { it.isDigit() } &&
                    documentFrequency(term) == 0
        }

    /**
     * Whether a query and a document title have any content word in common.
     *
     * The companion to [mentionsUnknownTerm], and the half that makes it usable. An unknown word on
     * its own does not mean the query is off-topic — real questions are full of ordinary verbs
     * ("define", "convert", "tell") that never appear in a corpus of element data. What separates
     * "define molar mass" from "write me a poem" is that the first shares words with the row it
     * retrieved and the second shares nothing with the electron mass.
     */
    fun sharesTermWithTitle(query: String, title: String): Boolean {
        val titleTerms = Tokenizer.tokenize(title, language).toHashSet()
        return Tokenizer.tokenize(query, language).any {
            it.length >= MIN_SHARED_TERM && it in titleTerms
        }
    }

    private companion object {
        /** Below this a shared token is a coincidence of grammar rather than of subject. */
        const val MIN_SHARED_TERM = 3

        val WHITESPACE = Regex("""\s+""")

        /** A formula or note in brackets: "(SiO2)", "(radioactive decay)". */
        val BRACKETED = Regex("""\([^)]*\)""")

        /** What separates the words of a title. */
        val NON_WORD = Regex("""[^\p{L}\p{M}\p{N}]+""")

        /** Endings a query word may also be tried without, for coverage only. */
        val LOOSE_ENDINGS = listOf("es", "en", "er", "s", "e")

        /** Shortest stem a loose ending may leave: "gas" must not become "ga". */
        const val MIN_LOOSE_STEM = 4
    }

    /** Document frequency of a term, exposed for diagnostics and tests. */
    fun documentFrequency(term: String): Int = postings[term]?.size ?: 0

}
