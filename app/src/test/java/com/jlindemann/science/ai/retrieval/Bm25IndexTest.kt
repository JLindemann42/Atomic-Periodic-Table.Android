package com.jlindemann.science.ai.retrieval

import org.junit.Assert.*
import org.junit.Test

class Bm25IndexTest {

    private val corpus = listOf(
        Document("element:gold", "Gold", "Gold is a chemical element with symbol Au and atomic number 79. A dense soft yellow transition metal."),
        Document("element:silver", "Silver", "Silver is a chemical element with symbol Ag and atomic number 47. A soft white lustrous transition metal."),
        Document("element:tungsten", "Tungsten", "Tungsten is a chemical element with symbol W and atomic number 74. It has the highest melting point of all elements."),
        Document("element:helium", "Helium", "Helium is a chemical element with symbol He and atomic number 2. A colourless odourless inert noble gas."),
        Document("dictionary:Density", "Density", "Dictionary entry in categories chemistry physics. The density of a material is its mass per unit volume."),
        Document("dictionary:Melting point", "Melting point", "Dictionary entry. The temperature at which a solid becomes a liquid."),
        Document("constant:Pi", "Pi", "Constant in category mathematics. Value 3.14159265358979323846."),
        Document("poisson:Andesite", "Andesite", "Poisson ratio for rock. Range 0.20 to 0.35."),
        Document("equation:Density", "Density", "Equation in category General. p Density m Mass V Volume."),
        Document("geology:Graphite", "Graphite", "Mineral. Group native element. Colour black. Streak black. Hardness 1.5.")
    )

    private val index = Bm25Index(corpus)

    @Test
    fun findsTheObviousDocument() {
        val hits = index.search("tungsten melting point")
        assertEquals("element:tungsten", hits.first().id)
    }

    @Test
    fun rankingPrefersTheMoreSpecificMatch() {
        val hits = index.search("noble gas")
        assertEquals("element:helium", hits.first().id)
    }

    /**
     * Terms appearing in most documents contribute almost nothing, which is what makes a
     * hand-maintained stopword list in twelve languages unnecessary.
     */
    @Test
    fun highDocumentFrequencyTermsCarryAlmostNoWeight() {
        // "chemical" and "element" appear in four of the ten documents; "tungsten" in one.
        val commonOnly = index.search("chemical element").firstOrNull()?.score ?: 0.0
        val rareOnly = index.search("tungsten").firstOrNull()?.score ?: 0.0
        assertTrue("a rare term must outscore common ones", rareOnly > commonOnly / 2)
    }

    @Test
    fun documentFrequencyIsReported() {
        assertEquals(1, index.documentFrequency("tungsten"))
        assertEquals(0, index.documentFrequency("frobnicate"))
        assertTrue(index.documentFrequency("element") >= 4)
    }

    @Test
    fun prefixFilterRestrictsToOneKind() {
        // "density" matches an element, a dictionary entry and an equation; the filter isolates one.
        val all = index.search("density")
        val onlyDictionary = index.search("density", prefixFilter = "dictionary:")
        assertTrue(all.any { !it.id.startsWith("dictionary:") })
        assertTrue(onlyDictionary.isNotEmpty())
        assertTrue(onlyDictionary.all { it.id.startsWith("dictionary:") })
    }

    @Test
    fun normalizedScoresAreRelativeToTheTopHit() {
        val hits = index.searchNormalized("tungsten melting point")
        assertEquals(1.0, hits.first().score, 1e-9)
        assertTrue(hits.all { it.score in 0.0..1.0 })
    }

    @Test
    fun emptyAndUnmatchedQueriesReturnNothing() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
        assertTrue(index.search("frobnicate xyzzy").isEmpty())
    }

    @Test
    fun limitIsRespected() {
        assertTrue(index.search("element", limit = 2).size <= 2)
    }

    @Test
    fun emptyIndexIsSafe() {
        assertTrue(Bm25Index(emptyList()).search("anything").isEmpty())
    }

    @Test
    fun matchingIsCaseAndAccentInsensitive() {
        assertEquals(index.search("GOLD").firstOrNull()?.id, index.search("gold").firstOrNull()?.id)
    }

    @Test
    fun chineseCorpusIsSearchableWithoutWordSpacing() {
        val zh = Bm25Index(
            listOf(
                Document("element:gold", "金", "金是一种化学元素 符号为Au 原子序数为79 是一种过渡金属"),
                Document("element:helium", "氦", "氦是一种化学元素 符号为He 原子序数为2 是一种惰性气体")
            ),
            language = "zh"
        )
        assertEquals("element:gold", zh.search("过渡金属").first().id)
        assertEquals("element:helium", zh.search("惰性气体").first().id)
    }
}
