package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.compose.AnswerComposer
import com.jlindemann.science.ai.compose.ChatActionCodec
import com.jlindemann.science.ai.data.DatasetIndex
import com.jlindemann.science.ai.data.KnowledgeStore
import com.jlindemann.science.ai.data.TestAssets
import com.jlindemann.science.ai.data.ValueParser
import com.jlindemann.science.ai.exec.QueryExecutor
import com.jlindemann.science.ai.nlu.FieldResolver
import com.jlindemann.science.ai.nlu.QueryPlanner
import com.jlindemann.science.ai.retrieval.EntityResolver
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Asserts the text users actually see, rendered with the app's real English strings.
 *
 * The chat adapter only understands `###` headings and `**bold**`, so anything else would render
 * as literal characters. And no answer may ever surface the data's `"---"` sentinel.
 */
class AnswerComposerTest {

    private lateinit var planner: QueryPlanner
    private lateinit var executor: QueryExecutor
    private lateinit var composer: AnswerComposer

    @Before
    fun setUp() {
        assumeTrue("real assets not reachable", TestAssets.available())
        assumeTrue("real strings not reachable", TestStrings.available)
        KnowledgeStore.clear()
        val store = KnowledgeStore.build(TestAssets.elementTable("en"))
        val datasets = DatasetIndex.build()
        val aliases = EntityResolver.buildAliases(
            mapOf("en" to store.elements.associate {
                it.key to Triple(it.key, it.symbol, it.atomicNumber.toString())
            })
        )
        val strings = TestStrings()
        planner = QueryPlanner(store, datasets, EntityResolver(aliases, "en"), FieldResolver(strings), null)
        executor = QueryExecutor(store, datasets, null, strings)
        composer = AnswerComposer(store, null, strings)
    }

    private fun answer(query: String, state: DialogueState = DialogueState()): String {
        val plan = planner.plan(query, state)
        val result = executor.execute(plan) ?: return ""
        return composer.compose(result, plan).text
    }

    private val queries = listOf(
        "which transition metals melt above 2000 celsius",
        "top 5 densest nonmetals",
        "average electronegativity of the halogens",
        "how many noble gases are there",
        "what is the melting point of gold in fahrenheit",
        "what is the vickers hardness of helium",
        "which element has the lowest electronegativity",
        "compare gold and silver density"
    )

    @Test
    fun everyAnswerIsNonEmpty() {
        for (query in queries) {
            assertTrue("'$query' produced nothing", answer(query).isNotBlank())
        }
    }

    /** Anything beyond `###` and `**` would render as literal characters in the chat bubble. */
    @Test
    fun answersUseOnlyTheMarkupTheRendererSupports() {
        val unsupported = listOf("](", "```", "| ---", "<b>", "<i>", "__")
        for (query in queries) {
            val text = answer(query)
            for (token in unsupported) {
                assertFalse("'$query' emitted unsupported markup '$token'", text.contains(token))
            }
            // Markdown bullets and setext rules are not rendered; only the literal "• " prefix is.
            for (line in text.lines()) {
                assertFalse("'$query' used a markdown bullet: $line", line.startsWith("* "))
                assertFalse("'$query' used a markdown bullet: $line", line.startsWith("- "))
            }
            // Bold markers must be balanced, or the renderer swallows the rest of the line.
            // n balanced pairs produce 2n markers and therefore 2n+1 split parts.
            assertEquals("'$query' has unbalanced bold markers", 1, text.split("**").size % 2)
        }
    }

    @Test
    fun noAnswerEverLeaksTheAbsentValueSentinel() {
        for (query in queries) {
            val text = answer(query)
            for (line in text.lines()) {
                val value = line.substringAfterLast("— ", "").trim()
                if (value.isNotEmpty()) {
                    assertFalse("'$query' leaked a sentinel in: $line", ValueParser.isNullToken(value))
                }
            }
            assertFalse("'$query' contains a raw sentinel", text.contains("---"))
        }
    }

    @Test
    fun groundedAnswersNameTheirSources() {
        val text = answer("which element has the lowest electronegativity")
        assertTrue("expected a sources section", text.contains("### Sources"))
        assertTrue(text.contains("Electronegativity"))
    }

    @Test
    fun absentDataIsStatedPlainlyWithCoverage() {
        val text = answer("what is the vickers hardness of helium")
        assertTrue("must say it lacks the data", text.contains("don't have the data", ignoreCase = true))
        assertTrue("must report how sparse the field is", text.contains("45 of 118"))
        assertFalse("must not invent a value", Regex("""\d+\s*MPa""").containsMatchIn(text))
    }

    @Test
    fun partialAggregatesDiscloseWhatTheyExcluded() {
        val text = answer("average electronegativity of the halogens")
        assertTrue(text.contains("Average"))
        assertTrue("a partial mean must say how many it covered", text.contains("Calculated over"))
        assertTrue(text.contains("no recorded value"))
    }

    @Test
    fun truncatedRankingsSayTheyAreTruncated() {
        val text = answer("which transition metals melt above 2000 celsius")
        assertTrue("a truncated list must say so", text.contains("Top 10 of"))
    }

    @Test
    fun superlativeAnswersInOneSentenceNotAList() {
        val text = answer("which element has the lowest electronegativity")
        assertTrue(text.contains("Caesium"))
        // One result line, not a bulleted table.
        assertTrue("a superlative should not render as a list", text.substringBefore("### ").count { it == '•' } == 0)
    }

    @Test
    fun convertedValuesShowTheRequestedUnit() {
        val text = answer("what is the melting point of gold in fahrenheit")
        assertTrue(text.contains("°F"))
        assertTrue(text.contains("1947"))
    }

    @Test
    fun comparisonsRenderOneRowPerElement() {
        val text = answer("compare gold and silver density")
        assertTrue(text.contains("Gold"))
        assertTrue(text.contains("Silver"))
        assertTrue(text.contains("###"))
    }

    @Test
    fun actionsAreAttachedAndRoundTrip() {
        val plan = planner.plan("which element has the lowest electronegativity", DialogueState())
        val result = executor.execute(plan)!!
        val composed = composer.compose(result, plan)
        assertTrue("a grounded answer should offer somewhere to go", composed.actions.isNotEmpty())

        val encoded = ChatActionCodec.encode(composed.actions)
        assertNotNull(encoded)
        val decoded = ChatActionCodec.decode(encoded)
        assertEquals(composed.actions.size, decoded.size)
        assertEquals(composed.actions.first().target, decoded.first().target)
        assertEquals(composed.actions.first().args, decoded.first().args)
    }

    @Test
    fun codecIsTotal() {
        assertTrue(ChatActionCodec.decode(null).isEmpty())
        assertTrue(ChatActionCodec.decode("").isEmpty())
        assertTrue(ChatActionCodec.decode("not json").isEmpty())
        assertTrue(ChatActionCodec.decode("""[{"bogus":1}]""").isEmpty())
        assertNull(ChatActionCodec.encode(emptyList()))
    }
}
