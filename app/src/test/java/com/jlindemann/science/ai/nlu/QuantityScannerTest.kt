package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.data.Dimension
import org.junit.Assert.*
import org.junit.Test

class QuantityScannerTest {

    @Test
    fun readsAVolumeAndItsDimension() {
        val q = QuantityScanner.firstOf("how many grams of NaCl for 250 mL of solution", Dimension.VOLUME)!!
        assertEquals(250.0, q.value, 1e-9)
        assertEquals("mL", q.unit)
        assertEquals(0.25, q.inUnit("L")!!, 1e-12)
    }

    /**
     * The reason the scanner reads the raw query rather than the normalised one: normalisation
     * lowercases, and molar and metre are the same letter in two cases.
     */
    @Test
    fun capitalisationSeparatesMolarFromMetre() {
        assertEquals(Dimension.MOLARITY, QuantityScanner.firstOf("0.5 M solution", Dimension.MOLARITY)?.dimension)
        assertNull(QuantityScanner.firstOf("0.5 m wide", Dimension.MOLARITY))
        assertEquals(Dimension.LENGTH, QuantityScanner.firstOf("0.5 m wide", Dimension.LENGTH)?.dimension)
    }

    @Test
    fun readsTimeInWordsAndSymbols() {
        assertEquals(11460.0, QuantityScanner.firstOf("after 11460 years", Dimension.TIME)!!.value, 1e-9)
        assertEquals("yr", QuantityScanner.firstOf("after 11460 years", Dimension.TIME)!!.unit)
        assertEquals("s", QuantityScanner.firstOf("in 30 s", Dimension.TIME)!!.unit)
        assertEquals("min", QuantityScanner.firstOf("20.4 minutes", Dimension.TIME)!!.unit)
    }

    @Test
    fun readsLengthAndTemperature() {
        val angstrom = QuantityScanner.firstOf("1.5 Å in pm", Dimension.LENGTH)!!
        assertEquals(1.5, angstrom.value, 1e-9)
        assertEquals("Å", angstrom.unit)
        assertEquals("K", QuantityScanner.firstOf("convert 500 K to °C", Dimension.TEMPERATURE)!!.unit)
    }

    /** A number with no unit is not a quantity, and must not be invented into one. */
    @Test
    fun bareNumbersAreNotQuantities() {
        assertTrue(QuantityScanner.scan("what about 5").isEmpty())
        assertTrue(QuantityScanner.scan("element 79").isEmpty())
        assertTrue(QuantityScanner.scan("no numbers here at all").isEmpty())
    }

    /**
     * Devanagari, Arabic and Han write no Latin letters and often no space, so the ASCII unit
     * pattern presents nothing for the alias table to fold. These reach the scanner by a second
     * pass or they do not reach it at all.
     */
    @Test
    fun readsUnitsWrittenInNonLatinScripts() {
        assertEquals("yr", QuantityScanner.firstOf("11460 साल के बाद", Dimension.TIME)?.unit)
        assertEquals("yr", QuantityScanner.firstOf("11460 سال بعد", Dimension.TIME)?.unit)
        assertEquals("yr", QuantityScanner.firstOf("11460年后", Dimension.TIME)?.unit)
        assertEquals("mL", QuantityScanner.firstOf("250 毫升", Dimension.VOLUME)?.unit)
    }

    @Test
    fun quantitiesComeBackInTheOrderTheyWereWritten() {
        val found = QuantityScanner.scan("dilute 50 mL of 2 M HCl to 0.5 M")
        assertEquals(listOf("mL", "M", "M"), found.map { it.unit })
        assertEquals(listOf(50.0, 2.0, 0.5), found.map { it.value })
    }

    @Test
    fun aTargetUnitNeedsAPrepositionBesideIt() {
        assertEquals("°C", QuantityScanner.targetUnit("convert 500 K to °C"))
        assertEquals("pm", QuantityScanner.targetUnit("1.5 Å in pm"))
        assertEquals("kJ/mol", QuantityScanner.targetUnit("2 eV in kJ/mol"))
        // Postpositional languages put it after the unit.
        assertEquals("°C", QuantityScanner.targetUnit("500 K सेल्सियस में"))
        // No preposition, no target — otherwise every mention of a unit becomes a request.
        assertNull(QuantityScanner.targetUnit("the melting point is 500 K"))
    }

    @Test
    fun scientificNotationIsRead() {
        val q = QuantityScanner.firstOf("3.5×10^-21 s", Dimension.TIME)
        assertNotNull(q)
        assertEquals(3.5e-21, q!!.value, 1e-30)
    }
}
