package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class SeriesCanonTest {

    /**
     * The 15 distinct `element_group` spellings the shipped English data actually uses, with the
     * number of elements carrying each. The legacy agent filtered with
     * `group.contains("Lanthanide")`, which never matched `"Lanthanoids"`, so "densest lanthanide"
     * style queries silently dropped all 15 lanthanoids.
     */
    private val realGroupStrings = listOf(
        "Transition Metals", "Actinide", "Lanthanoids", "Metalloids", "Noble Gases",
        "Post-Transition Metals", "Alkali Metals", "Halogens", "Alkaline Earth Metals",
        "Other Nonmetal", "Other Nonmetals", "Post-transition Metals", "Alkaline earth metal",
        "Reactive Nonmetals", "Reactive Nonmetal"
    )

    @Test
    fun everyRealGroupStringResolves() {
        for (raw in realGroupStrings) {
            assertNotEquals("'$raw' must not fall through to UNKNOWN", SeriesId.UNKNOWN, SeriesCanon.series(raw))
        }
    }

    @Test
    fun lanthanoidAndLanthanideSpellingsAgree() {
        assertEquals(SeriesId.LANTHANOID, SeriesCanon.series("Lanthanoids"))
        assertEquals(SeriesId.LANTHANOID, SeriesCanon.series("Lanthanide"))
        assertEquals(SeriesId.LANTHANOID, SeriesCanon.series("lanthanides"))
    }

    @Test
    fun casingAndPluralVariantsCollapse() {
        assertEquals(SeriesCanon.series("Post-Transition Metals"), SeriesCanon.series("Post-transition Metals"))
        assertEquals(SeriesCanon.series("Other Nonmetal"), SeriesCanon.series("Other Nonmetals"))
        assertEquals(SeriesCanon.series("Alkaline Earth Metals"), SeriesCanon.series("Alkaline earth metal"))
        assertEquals(SeriesCanon.series("Reactive Nonmetal"), SeriesCanon.series("Reactive Nonmetals"))
    }

    @Test
    fun postTransitionIsNotConfusedWithTransition() {
        assertEquals(SeriesId.POST_TRANSITION_METAL, SeriesCanon.series("Post-Transition Metals"))
        assertEquals(SeriesId.TRANSITION_METAL, SeriesCanon.series("Transition Metals"))
    }

    @Test
    fun alkalineEarthIsNotConfusedWithAlkali() {
        assertEquals(SeriesId.ALKALINE_EARTH_METAL, SeriesCanon.series("Alkaline Earth Metals"))
        assertEquals(SeriesId.ALKALI_METAL, SeriesCanon.series("Alkali Metals"))
    }

    @Test
    fun metalAndNonmetalGroupingsAreCoherent() {
        assertTrue(SeriesId.TRANSITION_METAL.isMetal)
        assertTrue(SeriesId.LANTHANOID.isMetal)
        assertFalse(SeriesId.NOBLE_GAS.isMetal)
        assertTrue(SeriesId.HALOGEN.isNonmetal)
        assertTrue(SeriesId.NOBLE_GAS.isNonmetal)
        assertFalse(SeriesId.METALLOID.isMetal)
        assertFalse(SeriesId.METALLOID.isNonmetal)
    }

    @Test
    fun unknownAndBlankFallThrough() {
        assertEquals(SeriesId.UNKNOWN, SeriesCanon.series(null))
        assertEquals(SeriesId.UNKNOWN, SeriesCanon.series(""))
        assertEquals(SeriesId.UNKNOWN, SeriesCanon.series("Frobnicates"))
    }

    @Test
    fun blocksParseFromTheAuthoredFormat() {
        assertEquals(Block.D, SeriesCanon.block("d - block"))
        assertEquals(Block.P, SeriesCanon.block("p - block"))
        assertEquals(Block.F, SeriesCanon.block("f - block"))
        assertEquals(Block.S, SeriesCanon.block("s - block"))
        assertEquals(Block.UNKNOWN, SeriesCanon.block("---"))
    }

    @Test
    fun phaseAndRadioactiveReadTheAuthoredValues() {
        assertEquals("solid", SeriesCanon.phase("Solid"))
        assertEquals("gas", SeriesCanon.phase("Gas"))
        assertEquals("liquid", SeriesCanon.phase("Liquid"))
        assertNull(SeriesCanon.phase("---"))
        assertTrue(SeriesCanon.radioactive("Yes"))
        assertFalse(SeriesCanon.radioactive("No"))
        assertFalse(SeriesCanon.radioactive(null))
    }
}
