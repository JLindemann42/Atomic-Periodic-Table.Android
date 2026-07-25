package com.jlindemann.science.ai.retrieval

import org.junit.Assert.*
import org.junit.Test

class EntityResolverTest {

    /** name, symbol, atomic number — the three surface forms per element per language. */
    private val tables = mapOf(
        "en" to mapOf(
            "gold" to Triple("Gold", "Au", "79"),
            "silver" to Triple("Silver", "Ag", "47"),
            "argon" to Triple("Argon", "Ar", "18"),
            "indium" to Triple("Indium", "In", "49"),
            "sulfur" to Triple("Sulfur", "S", "16"),
            "hydrogen" to Triple("Hydrogen", "H", "1"),
            "iron" to Triple("Iron", "Fe", "26")
        ),
        "sv" to mapOf(
            "gold" to Triple("Guld", "Au", "79"),
            "hydrogen" to Triple("Väte", "H", "1"),
            "argon" to Triple("Argon", "Ar", "18")
        ),
        "zh" to mapOf(
            "gold" to Triple("金", "Au", "79"),
            "iron" to Triple("铁", "Fe", "26"),
            "helium" to Triple("氦", "He", "2")
        ),
        "ur" to mapOf("gold" to Triple("سونا", "Au", "79"))
    )

    private val aliases = EntityResolver.buildAliases(tables)

    private fun resolver(language: String = "en") = EntityResolver(aliases, language)

    @Test
    fun resolvesByEnglishName() {
        assertEquals("gold", resolver().resolve("what is the density of gold")?.key)
    }

    @Test
    fun resolvesBySymbol() {
        assertEquals("gold", resolver().resolve("tell me about Au")?.key)
    }

    @Test
    fun resolvesByAtomicNumber() {
        assertEquals("gold", resolver().resolve("element 79")?.key)
        assertEquals("hydrogen", resolver().resolve("atomic number 1")?.key)
    }

    @Test
    fun atomicNumberOutOfRangeIsIgnored() {
        assertNull(resolver().resolve("element 500")?.key)
    }

    @Test
    fun resolvesAcrossLanguages() {
        assertEquals("gold", resolver("sv").resolve("guld")?.key)
        assertEquals("hydrogen", resolver("sv").resolve("väte")?.key)
        assertEquals("gold", resolver("zh").resolve("金")?.key)
        assertEquals("gold", resolver("ur").resolve("سونا")?.key)
    }

    /**
     * The collision cases the legacy agent guarded against. Swedish `är` normalises to `ar`,
     * which is argon's symbol, so "är väte radioaktivt" must resolve to hydrogen, not argon.
     */
    @Test
    fun swedishArDoesNotResolveToArgon() {
        assertEquals("hydrogen", resolver("sv").resolve("är väte radioaktivt")?.key)
    }

    @Test
    fun englishInDoesNotResolveToIndium() {
        val matches = resolver("en").resolveAll("what is in the air")
        assertTrue("'in' must not be read as indium", matches.none { it.key == "indium" })
    }

    @Test
    fun aLoneSymbolStillResolves() {
        // The guard only applies when the short token sits among other words.
        assertEquals("argon", resolver("sv").resolve("ar")?.key)
    }

    @Test
    fun explicitSymbolFramingOverridesTheGuard() {
        assertEquals("indium", resolver("en").resolve("what element has the symbol In")?.key)
    }

    @Test
    fun scientistNamesDoNotResolveToSymbols() {
        // "poisson" contains no standalone S, but a naive symbol scan used to match it.
        assertTrue(resolver().resolveAll("poisson ratio").none { it.key == "sulfur" })
        assertTrue(resolver().resolveAll("planck constant").none { it.key == "sulfur" })
    }

    /**
     * Chinese element names are single characters that also occur inside ordinary words:
     * 金 is gold, but 金属 means "metal". Resolving the compound to gold would be wrong.
     */
    @Test
    fun singleHanCharacterAbsorbedInACompoundIsNotAnElementMention() {
        assertEquals("gold", resolver("zh").resolve("金")?.key)
        val inCompound = resolver("zh").resolveAll("金属的密度")
        assertTrue("金 inside 金属 must not resolve to gold", inCompound.none { it.key == "gold" })
    }

    @Test
    fun resolvesMultipleElementsForComparison() {
        val matches = resolver().resolveAll("compare gold and silver")
        assertEquals(setOf("gold", "silver"), matches.map { it.key }.toSet())
    }

    @Test
    fun longerNameWinsOverAContainedShorterOne() {
        val matches = resolver().resolveAll("hydrogen")
        assertEquals("hydrogen", matches.first().key)
    }

    @Test
    fun typosAreToleratedOnShortQueries() {
        assertEquals("gold", resolver().resolve("golde")?.key)
        assertEquals("silver", resolver().resolve("silvor")?.key)
    }

    @Test
    fun typoToleranceDoesNotApplyToLongQueries() {
        // A long sentence with no element named should resolve to nothing rather than guess.
        assertNull(resolver().resolve("please explain how chemical bonding works in general terms"))
    }

    @Test
    fun unrelatedQueryResolvesToNothing() {
        assertNull(resolver().resolve("what is the weather like"))
    }

    @Test
    fun namesScoreHigherThanSymbols() {
        val byName = resolver().resolve("gold")!!
        val bySymbol = resolver().resolve("au")!!
        assertTrue(byName.score > bySymbol.score)
    }

    // ---- The exact cases asserted by the legacy reflection test --------------------------

    @Test
    fun legacyCollisionCasesArePreserved() {
        assertTrue(EntityResolver.isCommonWordCollision("ar", "är väte radioaktivt", listOf("ar", "väte", "radioaktivt"), "sv"))
        assertFalse(EntityResolver.isCommonWordCollision("ar", "ar", listOf("ar"), "sv"))
        assertTrue(EntityResolver.isCommonWordCollision("be", "to be or not to be", listOf("to", "be", "or", "not", "to", "be"), "en"))
        assertTrue(EntityResolver.isCommonWordCollision("se", "हाइड्रोजन से क्या होता है", listOf("हाइड्रोजन", "se", "क्या", "होता", "है"), "hi"))
        assertTrue(EntityResolver.isCommonWordCollision("ng", "ano ang gamit ng hydrogen", listOf("ano", "ang", "gamit", "ng", "hydrogen"), "fil"))
        assertTrue(EntityResolver.isCommonWordCollision("al", "aggiungi acqua al composto", listOf("aggiungi", "acqua", "al", "composto"), "it"))
        assertTrue(EntityResolver.isCommonWordCollision("as", "wat gebeur as dit warm word", listOf("wat", "gebeur", "as", "dit", "warm", "word"), "af"))
    }

    @Test
    fun tokensLongerThanTwoCharsAreNeverCollisions() {
        assertFalse(EntityResolver.isCommonWordCollision("gold", "gold is dense", listOf("gold", "is", "dense"), "en"))
    }
}
