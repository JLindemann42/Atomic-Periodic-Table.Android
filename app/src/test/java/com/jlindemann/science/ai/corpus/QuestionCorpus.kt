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
 * @property expectRow format 2: the canonical key of the dataset row the answer must come from, e.g.
 *   `geology/granite`. Routing alone passed while "granite" was answered with basalt.
 * @property expectValue format 2: the number the answer must carry — a property's quantity, a
 *   dataset row's value or an aggregate — to within 0.1%.
 * @property knownFailure format 2: why this row is expected to fail today. A failing known failure
 *   is reported but does not fail the build; one that passes does, so the mark is removed the moment
 *   the fix lands. How the corpus can say what is right before the engine does it.
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
    val line: Int,
    val expectRow: String? = null,
    val expectValue: Double? = null,
    val knownFailure: String? = null
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
 * The files live in the shared-data submodule (`shared/corpus`), which the Windows app runs too, so
 * both platforms are held to the same rows. Found by probing the filesystem the way
 * [com.jlindemann.science.ai.data.TestAssets] does, so it loads whatever the working directory is.
 */
object QuestionCorpus {


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
        "named_compounds",
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

    /**
     * Which rows run (roadmap 0.5). About a fifth of the corpus is frozen into `shared/heldout/` and
     * scored at release only, so a change tuned against the corpus can still be measured on
     * questions nobody tuned against.
     *
     * Set with `-Pcorpus.split=dev|heldout|all`; `dev`, the everyday gate, is the default.
     */
    enum class Split(val folders: List<String>) {
        DEV(listOf("corpus")),
        HELDOUT(listOf("heldout")),
        ALL(listOf("corpus", "heldout"))
    }

    val split: Split = when (System.getProperty("corpus.split")?.trim()?.lowercase()) {
        null, "", "dev" -> Split.DEV
        "heldout" -> Split.HELDOUT
        "all" -> Split.ALL
        else -> error("corpus.split must be dev, heldout or all")
    }

    /**
     * True when the corpus is reachable at all. A false here makes every corpus test vacuous. Checks
     * must_defer, which every split has; the defect files are never held out.
     */
    val available: Boolean by lazy {
        runCatching { split.folders.all { read(it, "must_defer") != null } }.getOrDefault(false)
    }

    /**
     * Parsed cases for one file, or an empty list when the file is absent. The selected [split] by
     * default; a test about the corpus itself rather than the engine passes [Split.ALL].
     */
    fun cases(name: String, split: Split = this.split): List<CorpusCase> =
        split.folders.flatMap { folder -> read(folder, name)?.let { parse(name, it) }.orEmpty() }

    /** Every case in the corpus, across every file. */
    fun allCases(split: Split = this.split): List<CorpusCase> = FILES.flatMap { cases(it, split) }

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

    private fun read(folder: String, name: String): String? {
        val candidates = listOf(
            File("../shared/$folder/$name.tsv"),
            File("shared/$folder/$name.tsv")
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
    /**
     * The columns each corpus format allows (roadmap 0.6). A file declares its format with a
     * `# corpus-format: N` comment before the header; none means 1. A column outside the declared
     * format's set is an error, so a misspelt `expect_rows` cannot pass by being silently ignored,
     * and a format this harness does not know is an error rather than a partial read. The Windows
     * harness applies the same rules to the same files.
     */
    private val FORMAT_COLUMNS: Map<Int, Set<String>> = run {
        val v1 = setOf(
            "id", "session", "turn", "lang", "query", "expect_intent", "expect_fields",
            "expect_card", "must_defer", "notes"
        )
        mapOf(1 to v1, 2 to v1 + setOf("expect_row", "expect_value", "known_failure"))
    }

    private val FORMAT_LINE = Regex("^#\\s*corpus-format:\\s*(\\d+)\\s*$")

    private fun parse(name: String, text: String): List<CorpusCase> {
        val cases = ArrayList<CorpusCase>()
        var header: List<String>? = null
        var format = 1
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trimEnd('\r', '\n')
            if (header == null) FORMAT_LINE.matchEntire(line.trim())?.let { format = it.groupValues[1].toInt() }
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val columns = line.split('\t')
            if (header == null) {
                header = columns.map { it.trim() }
                require(header!!.first() == "id") {
                    "$name.tsv:${index + 1} first column must be 'id', got '${columns.first()}'"
                }
                val allowed = FORMAT_COLUMNS[format]
                    ?: error("$name.tsv declares corpus-format $format, which this harness does not know")
                val unknown = header!!.filterNot { it in allowed }
                require(unknown.isEmpty()) {
                    "$name.tsv: columns $unknown are not in corpus-format $format; declare a newer format or fix the name"
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
                    line = index + 1,
                    expectRow = row["expect_row"].orEmpty().ifEmpty { null },
                    expectValue = row["expect_value"].orEmpty().ifEmpty { null }?.let { v ->
                        requireNotNull(v.toDoubleOrNull()) { "$name.tsv:${index + 1} [$id] expect_value '$v' is not a number" }
                    },
                    knownFailure = row["known_failure"].orEmpty().ifEmpty { null }
                )
            )
        }
        return cases
    }
}
