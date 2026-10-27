package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.core.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The three morphology rules (roadmap 1.5), each with the words it must leave alone. Windows'
 * `MorphologyTest` asserts the same rewrites.
 */
class MorphologyTest {

    private fun morphology(language: String): Morphology {
        assumeTrue(TestStrings.available)
        return Morphology(FieldResolver(TestStrings(language, fallBackToEnglish = true)))
    }

    @Test
    fun aCompoundIsSplitIntoTheWordsItIsMadeOf() {
        val sv = morphology("sv")
        assertEquals("smaltpunkt skillnaden mellan järn och koppar", sv.expand("smältpunktsskillnaden mellan järn och koppar"))
        assertEquals("medel tatheten för halogenerna", sv.expand("medeltätheten för halogenerna"))
        assertEquals("elektronegativitet vardet för fluor?", sv.expand("elektronegativitetsvärdet för fluor?"))
        assertEquals("durchschnitt dichte der Alkalimetalle", morphology("de").expand("Durchschnittsdichte der Alkalimetalle"))
    }

    @Test
    fun aWordKnownWholeIsNeverTakenApart() {
        val sv = morphology("sv")
        assertEquals("vad är atommassan för guld", sv.expand("vad är atommassan för guld"))
        assertEquals("vilka är alkalimetallerna", sv.expand("vilka är alkalimetallerna"))
        assertEquals("molmassan av natriumklorid", sv.expand("molmassan av natriumklorid"))
    }

    @Test
    fun steIsTheSuperlativeOfItsStem() {
        val af = morphology("af")
        assertEquals("watter edelgas is die lightest?", af.expand("watter edelgas is die ligste?"))
        assertEquals("watter element is die most electronegative", af.expand("watter element is die elektronegatiefste"))
        // Listed whole with its property, so left as it is.
        assertEquals("watter metaal is die swaarste", af.expand("watter metaal is die swaarste"))
        assertEquals("die hoogste smeltpunt", af.expand("die hoogste smeltpunt"))
    }

    @Test
    fun pinakaIsTheSuperlativeOfWhatFollowsIt() {
        val fil = morphology("fil")
        assertEquals("ano ang pinakamababa punto ng pagkatunaw", fil.expand("ano ang pinakamababang punto ng pagkatunaw"))
        assertEquals("aling elemento ang most electronegative", fil.expand("aling elemento ang pinakaelectronegative"))
        assertEquals("ano ang hardest na metal", fil.expand("ano ang pinakamatigas na metal"))
        assertEquals("ano ang pinakamabigat na elemento", fil.expand("ano ang pinakamabigat na elemento"))
    }

    @Test
    fun formulasAndNumbersAreLeftAsWritten() {
        val en = morphology("en")
        assertEquals("balance Fe + O2 -> Fe2O3", en.expand("balance Fe + O2 -> Fe2O3"))
        assertEquals("molar mass of NaCl", en.expand("molar mass of NaCl"))
    }
}
