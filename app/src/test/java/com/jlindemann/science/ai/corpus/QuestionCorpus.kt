package com.jlindemann.science.ai.corpus

import java.io.File

/**
 * One expectation from the training corpus.
 *
 * @property session rows sharing a session replay in [turn] order against one `DialogueState`,
 *   which is the only way follow-up slot inheritance can be tested. Null for a standalone row.
 * @property expectIntent an `Intent` name, or null for "any claimed intent is acceptable"
 * @property expectFields field ids that must *all* appear in the plan's `fieldIds`
 * @property expectCard a `ChatCardKind` name, `"none"` for "must produce no card", or null for
 *   "don't care". Ignored until the card layer exists.
 * @property mustDefer the engine must decline, so the personality layer handles it. Greetings,
 *   quizzes and games live here — answering them from the constants table is the bug this catches.
 */
data class CorpusCase(
    val id: String,
    val session: String?,
    val turn: Int,
    val lang: String,
    val query: String,
    val expectIntent: String?,
    val expectFields: List<String>,
    val expectCard: String?,
    val mustDefer: Boolean,
    val notes: String,
    val sourceFile: String,
    val line: Int
) {
    /** How a failure identifies itself, so a report line is enough to find the row. */
    val where: String get() = "$sourceFile:$line [$id]"
}

/**
 * Loads the tab-separated question corpus.
 *
 * TSV rather than JSON for two reasons: `org.json` is a throwing stub under plain JVM tests, and a
 * flat table is something a non-developer can extend by the hundred rows without touching Kotlin.
 *
 * Resolution tries the test classpath first — `src/test/resources` is on it under Gradle — and
 * falls back to probing the filesystem the way [com.jlindemann.science.ai.data.TestAssets] does,
 * so the corpus loads whatever the working directory happens to be.
 */
object QuestionCorpus {

    private const val RESOURCE_ROOT = "/ai/corpus"

    /** Every corpus file, in the order a report should list them. */
    val FILES = listOf(
        "properties",
        "categories",
        "superlatives_filters",
        "comparisons",
        "aggregates",
        "isotopes_nuclides",
        "safety",
        "calculations",
        "unit_convert",
        "balance_equations",
        "decay",
        "solutions",
        "compound",
        "followups",
        "complex",
        "conversations_simple",
        "conversations_complex",
        "datasets",
        "must_defer",
        "capabilities",
        "cards",
        "localized_sv",
        "localized_other",
        "regressions",
        "reported",
        "multilingual",
        "multilingual_wide",
        "multilingual_operators",
        "conversations_multi",
        "conversations_deep",
        "conversations_repair"
    )

    /** True when the corpus is reachable at all. A false here makes every corpus test vacuous. */
    val available: Boolean by lazy { runCatching { read("regressions") != null }.getOrDefault(false) }

    /** Parsed cases for one file, or an empty list when the file is absent. */
    fun cases(name: String): List<CorpusCase> {
        val text = read(name) ?: return emptyList()
        return parse(name, text)
    }

    /** Every case in the corpus, across every file. */
    fun allCases(): List<CorpusCase> = FILES.flatMap { cases(it) }

    /**
     * Cases grouped into replay order.
     *
     * Standalone rows each become a group of one. Rows sharing a session become one group sorted
     * by turn, so the caller can drive them through a single dialogue state.
     */
    fun sessions(name: String): List<List<CorpusCase>> {
        val (multi, single) = cases(name).partition { it.session != null }
        val grouped = multi.groupBy { it.session }.values.map { it.sortedBy { case -> case.turn } }
        return single.map { listOf(it) } + grouped
    }

    private fun read(name: String): String? {
        QuestionCorpus::class.java.getResourceAsStream("$RESOURCE_ROOT/$name.tsv")?.use {
            return it.readBytes().toString(Charsets.UTF_8)
        }
        val candidates = listOf(
            File("src/test/resources/ai/corpus/$name.tsv"),
            File("app/src/test/resources/ai/corpus/$name.tsv"),
            File("../app/src/test/resources/ai/corpus/$name.tsv")
        )
        return candidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
    }

    /**
     * Parse a corpus file.
     *
     * Blank lines and `#` comments are skipped. A malformed row is a hard error rather than a
     * silent skip: a corpus that quietly drops rows would report a pass rate over a set nobody
     * knows the size of.
     */
    private fun parse(name: String, text: String): List<CorpusCase> {
        val cases = ArrayList<CorpusCase>()
        var header: List<String>? = null
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trimEnd('\r', '\n')
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val columns = line.split('\t')
            if (header == null) {
                header = columns.map { it.trim() }
                require(header!!.first() == "id") {
                    "$name.tsv:${index + 1} first column must be 'id', got '${columns.first()}'"
                }
                return@forEachIndexed
            }
            // Strict column count. A row with one tab too many silently shifts every later value
            // into the wrong column — `must_defer` lands in `notes` and a hard expectation quietly
            // becomes a comment. A corpus that mis-parses reports a pass rate for the wrong
            // question, so this is a hard error rather than something to tolerate.
            require(columns.size == header!!.size) {
                "$name.tsv:${index + 1} has ${columns.size} columns, expected ${header!!.size}: $columns"
            }
            val row = header!!.indices.associate { i -> header!![i] to columns[i].trim() }
            val id = row["id"].orEmpty()
            require(id.isNotEmpty()) { "$name.tsv:${index + 1} has no id" }
            val query = row["query"].orEmpty()
            require(query.isNotEmpty()) { "$name.tsv:${index + 1} [$id] has no query" }
            cases.add(
                CorpusCase(
                    id = id,
                    session = row["session"].orEmpty().ifEmpty { null },
                    turn = row["turn"].orEmpty().toIntOrNull() ?: 1,
                    lang = row["lang"].orEmpty().ifEmpty { "en" },
                    query = query,
                    expectIntent = row["expect_intent"].orEmpty().let { if (it.isEmpty() || it == "*") null else it },
                    expectFields = row["expect_fields"].orEmpty()
                        .split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    expectCard = row["expect_card"].orEmpty().ifEmpty { null },
                    mustDefer = row["must_defer"].orEmpty().lowercase() in setOf("y", "yes", "true", "1"),
                    notes = row["notes"].orEmpty(),
                    sourceFile = "$name.tsv",
                    line = index + 1
                )
            )
        }
        return cases
    }
}
