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
}
