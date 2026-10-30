package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

class UnitConverterTest {

    @Test
    fun temperatureConversionsAreAffineNotMultiplicative() {
        assertEquals(273.15, UnitConverter.convert(0.0, "°C", "K")!!, 1e-9)
        assertEquals(32.0, UnitConverter.convert(0.0, "°C", "°F")!!, 1e-9)
        assertEquals(-40.0, UnitConverter.convert(-40.0, "°C", "°F")!!, 1e-9)
        assertEquals(100.0, UnitConverter.convert(373.15, "K", "°C")!!, 1e-9)
    }

    @Test
    fun goldMeltingPointRoundTripsAcrossAllThreeScales() {
        val kelvin = 1337.33
        val celsius = UnitConverter.convert(kelvin, "K", "°C")!!
        val fahrenheit = UnitConverter.convert(celsius, "°C", "°F")!!
        assertEquals(1064.18, celsius, 0.01)
        assertEquals(1947.52, fahrenheit, 0.01)
        assertEquals(kelvin, UnitConverter.convert(fahrenheit, "°F", "K")!!, 1e-6)
    }

    @Test
    fun densityConvertsBetweenGramsAndKilograms() {
        assertEquals(19300.0, UnitConverter.convert(19.3, "g/cm³", "kg/m³")!!, 1e-6)
        assertEquals(19.3, UnitConverter.convert(19300.0, "kg/m³", "g/cm³")!!, 1e-9)
    }

    @Test
    fun pressureScalesAcrossPaKPaMPaGPa() {
        assertEquals(1e9, UnitConverter.convert(1.0, "GPa", "Pa")!!, 1.0)
        assertEquals(1000.0, UnitConverter.convert(1.0, "GPa", "MPa")!!, 1e-6)
    }

    @Test
    fun lengthScalesAcrossPmAngstromNm() {
        assertEquals(100.0, UnitConverter.convert(1.0, "Å", "pm")!!, 1e-9)
        assertEquals(1000.0, UnitConverter.convert(1.0, "nm", "pm")!!, 1e-9)
    }

    @Test
    fun percentAndPartsPerMillionAreTheSameDimension() {
        assertEquals(10_000.0, UnitConverter.convert(1.0, "%", "mg/kg")!!, 1e-6)
        assertEquals(Dimension.CONCENTRATION, UnitConverter.dimensionOf("ppm"))
    }

    @Test
    fun crossDimensionConversionIsRefused() {
        assertNull(UnitConverter.convert(1.0, "K", "GPa"))
        assertNull(UnitConverter.convert(1.0, "pm", "eV"))
    }

    @Test
    fun unknownUnitsReturnNullRatherThanGuessing() {
        assertNull(UnitConverter.convert(1.0, "flurbles", "K"))
        assertNull(UnitConverter.convert(1.0, "K", null))
    }

    @Test
    fun aliasesResolveToOneCanonicalSpelling() {
        assertEquals("K", UnitConverter.canonical("kelvin"))
        assertEquals("°C", UnitConverter.canonical("Celsius"))
        assertEquals("°F", UnitConverter.canonical("fahrenheit"))
        assertEquals("g/cm³", UnitConverter.canonical("(g/cm^3)"))
        assertEquals("u", UnitConverter.canonical("amu"))
    }

    @Test
    fun microSignAndGreekMuAreTheSameUnit() {
        // The element data writes U+00B5; NFKC folds it to U+03BC. Both must compare equal.
        assertEquals(UnitConverter.canonical("µΩm"), UnitConverter.canonical("μΩm"))
    }

    @Test
    fun convertingAQuantityCarriesTheRangeAcross() {
        val q = Quantity(value = 1.0, high = 2.0, unit = "GPa", display = "1-2 (GPa)")
        val converted = UnitConverter.convert(q, "MPa")!!
        assertEquals(1000.0, converted.value, 1e-6)
        assertEquals(2000.0, converted.high!!, 1e-6)
        assertEquals("MPa", converted.unit)
    }

    @Test
    fun formatValueDropsTrailingZeroes() {
        assertEquals("1000", UnitConverter.formatValue(1000.0))
        assertEquals("1064.18", UnitConverter.formatValue(1064.1800000001))
    }

    /**
     * `M` is molar and `m` is metre. The alias table is case-folded, so without the case-sensitive
     * pre-pass one of the two has to lose — and whichever loses turns some perfectly ordinary
     * question into a different one silently.
     */
    @Test
    fun caseDistinguishesMolarFromMetre() {
        assertEquals("M", UnitConverter.canonical("M"))
        assertEquals("m", UnitConverter.canonical("m"))
        assertEquals(Dimension.MOLARITY, UnitConverter.dimensionOf("M"))
        assertEquals(Dimension.LENGTH, UnitConverter.dimensionOf("m"))
        assertEquals(Dimension.MOLARITY, UnitConverter.dimensionOf("mM"))
    }

    /** `mm` is millimetre to every reader; it must not resolve as millimolar by case-folding. */
    @Test
    fun millimetreIsNotMillimolar() {
        assertNull(UnitConverter.knownUnit("mm"))
    }

    /** Year units are spelled `yr`: `a` was angstrom long before this change and stays angstrom. */
    @Test
    fun theYearUnitsDoNotStealAngstrom() {
        assertEquals("Å", UnitConverter.canonical("a"))
        assertEquals(Dimension.LENGTH, UnitConverter.dimensionOf("a"))
        assertEquals("yr", UnitConverter.canonical("years"))
        assertEquals(Dimension.TIME, UnitConverter.dimensionOf("yr"))
    }

    @Test
    fun timeVolumeAndAmountConvertThroughTheirBaseUnits() {
        assertEquals(3.15576e7, UnitConverter.convert(1.0, "yr", "s")!!, 1.0)
        assertEquals(60.0, UnitConverter.convert(1.0, "min", "s")!!, 1e-9)
        assertEquals(1000.0, UnitConverter.convert(1.0, "L", "mL")!!, 1e-9)
        assertEquals(0.25, UnitConverter.convert(250.0, "mL", "L")!!, 1e-12)
        assertEquals(1.0, UnitConverter.convert(1.0, "dm³", "L")!!, 1e-12)
        assertEquals(1000.0, UnitConverter.convert(1.0, "mol", "mmol")!!, 1e-9)
    }

    /**
     * Molarity must not share a dimension with the mg/kg abundance fields, or a filter could rank
     * a solution's concentration against how much of an element is in the Earth's crust.
     */
    @Test
    fun molarityIsNotTheAbundanceConcentration() {
        assertEquals(Dimension.MOLARITY, UnitConverter.dimensionOf("mol/L"))
        assertEquals(Dimension.CONCENTRATION, UnitConverter.dimensionOf("mg/kg"))
        assertNull(UnitConverter.convert(1.0, "mol/L", "mg/kg"))
    }

    /**
     * The eV/kJ-per-mol bridge is reachable only by an explicit request. `convert` must keep
     * refusing it, because that is what stops ranking and filtering mixing the two.
     */
    @Test
    fun theEnergyBridgeIsOpenOnlyToExplicitConversion() {
        assertNull(UnitConverter.convert(1.0, "eV", "kJ/mol"))
        assertEquals(96.485, UnitConverter.convertAcrossBridges(1.0, "eV", "kJ/mol")!!, 0.01)
        assertEquals(1.0, UnitConverter.convertAcrossBridges(96.48533212, "kJ/mol", "eV")!!, 1e-6)
        assertTrue(UnitConverter.isBridged("eV", "kJ/mol"))
        assertFalse(UnitConverter.isBridged("K", "°C"))
        assertTrue(UnitConverter.convertible("K", "°C"))
        assertFalse(UnitConverter.convertible("K", "L"))
    }

    /**
     * `canonical` echoes anything it does not recognise, which is right for authored data and
     * useless for a query — something has to be able to ask whether a word is a unit at all.
     */
    @Test
    fun knownUnitRecognisesRatherThanEchoes() {
        assertEquals("banana", UnitConverter.canonical("banana"))
        assertNull(UnitConverter.knownUnit("banana"))
        assertEquals("K", UnitConverter.knownUnit("kelvin"))
        assertEquals("mL", UnitConverter.knownUnit("mL"))
        assertNull(UnitConverter.knownUnit(null))
    }
}
