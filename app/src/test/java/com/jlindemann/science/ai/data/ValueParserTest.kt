package com.jlindemann.science.ai.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Every case here is a value shape that actually occurs in the shipped `elements_en.json`.
 * If a rule regresses, real elements start answering with wrong numbers or sentinel text.
 */
class ValueParserTest {

    private val density = FieldRegistry.byId.getValue("density")
    private val meltingPoint = FieldRegistry.byId.getValue("melting_point")
    private val vickers = FieldRegistry.byId.getValue("vickers_hardness")
    private val crust = FieldRegistry.byId.getValue("abundance_earth_crust")
    private val humanBody = FieldRegistry.byId.getValue("abundance_human_body")
    private val soundSolid = FieldRegistry.byId.getValue("speed_of_sound_solid")
    private val resistivity = FieldRegistry.byId.getValue("resistivity")
    private val lattice = FieldRegistry.byId.getValue("lattice_constants")
    private val debye = FieldRegistry.byId.getValue("debye_temperature")
    private val series = FieldRegistry.byId.getValue("series")
    private val year = FieldRegistry.byId.getValue("year_discovered")
    private val nfpaHealth = FieldRegistry.byId.getValue("nfpa_health")

    private fun num(raw: Any?, spec: FieldSpec): Quantity {
        val parsed = ValueParser.parse(raw, spec)
        assertTrue("expected Num for $raw but got $parsed", parsed is FieldValue.Num)
        return (parsed as FieldValue.Num).quantity
    }

    // ---- Rule 1: absent-value sentinels ------------------------------------------------

    @Test
    fun bareSentinelsAreMissing() {
        for (raw in listOf(null, "", "   ", "---", "N/A", "?", "???", "unknown")) {
            assertTrue("$raw should be Missing", ValueParser.parse(raw, density).isMissing)
        }
    }

    @Test
    fun sentinelsThatKeptTheirUnitAreStillMissing() {
        // "--- (pm)" occurs 32 times; a plain `== "---"` check would leak it to the user.
        for (raw in listOf("--- (pm)", "--- (K)", "---  (K)", "--- J/(g·K)", "--- (kJ/mol)", "--- (g/cm^3)")) {
            assertTrue("$raw should be Missing", ValueParser.parse(raw, density).isMissing)
        }
    }

    // ---- Rule 2: typed inputs ----------------------------------------------------------

    @Test
    fun nfpaIntegersParseAsNumbers() {
        assertEquals(2.0, num(2, nfpaHealth).value, 0.0)
        assertEquals(0.0, num(0, nfpaHealth).value, 0.0)
    }

    @Test
    fun nestedLatticeConstantsBecomeStruct() {
        val parsed = ValueParser.parse(mapOf("a" to "4.04958 Å"), lattice)
        assertTrue(parsed is FieldValue.Struct)
        val parts = (parsed as FieldValue.Struct).parts
        assertEquals(4.04958, parts.getValue("a").value, 1e-6)
    }

    @Test
    fun debyeTemperatureStructKeepsBothLimits() {
        val parsed = ValueParser.parse(
            mapOf("low_temperature_limit" to "428 K", "room_temperature" to "390 K"), debye
        )
        val parts = (parsed as FieldValue.Struct).parts
        assertEquals(428.0, parts.getValue("low_temperature_limit").value, 0.0)
        assertEquals(390.0, parts.getValue("room_temperature").value, 0.0)
    }

    // ---- Rules 3-4: notes, allotropes, molecular forms ---------------------------------

    @Test
    fun bracketedQualifierBecomesNoteNotPartOfTheNumber() {
        val q = num("5000 (m/s) [room temperature]", soundSolid)
        assertEquals(5000.0, q.value, 0.0)
        assertEquals("room temperature", q.note)
        assertEquals("m/s", q.unit)
    }

    @Test
    fun allotropePrefixBecomesNote() {
        val q = num("Br2: 332.0 (K)", meltingPoint)
        assertEquals(332.0, q.value, 0.0)
        assertEquals("Br2", q.note)
    }

    @Test
    fun negativeCelsiusIsNotMistakenForARange() {
        // melting_point does not allow ranges precisely so this stays negative.
        val q = num("Br2: -7.2 (°C)", meltingPoint)
        assertEquals(-7.2, q.value, 1e-9)
        assertNull(q.high)
    }

    @Test
    fun molecularFormAnnotationIsStripped() {
        val q = num("0.479 (Cl2) (kJ/mol)", FieldRegistry.byId.getValue("fusion_heat"))
        assertEquals(0.479, q.value, 1e-9)
    }

    @Test
    fun multilineAllotropeValuesTakeTheFirstAndNoteTheRest() {
        val q = num("13750 nΩm [Graphite] \n 8 nΩm [Diamond]", resistivity)
        assertEquals(13750.0, q.value, 0.0)
        assertNotNull(q.note)
    }

    // ---- Rules 7-9: uncertainty, approximation, scientific notation --------------------

    @Test
    fun gluedUncertaintyIsDropped() {
        val q = num("7.17(24)×10^5 (Years)", crust)
        assertEquals(7.17e5, q.value, 1.0)
    }

    @Test
    fun approximationMarkersSetTheFlag() {
        assertTrue(num("~4×10^-20 (s)", crust).approximate)
        assertTrue(num("<0.5", crust).approximate)
        assertTrue(num("> 1.2", crust).approximate)
        assertFalse(num("2.5", crust).approximate)
    }

    @Test
    fun scientificNotationWithUnicodeMultiplyAndMinus() {
        // U+00D7 multiplication sign and U+2212 minus sign both occur in the data.
        assertEquals(8.0e4, num("8.0 × 10^4", crust).value, 1.0)
        assertEquals(5.0e-21, num("5.0×10^−21 (s)", crust).value, 1e-24)
        assertEquals(5.7e-22, num("570*10^-24 (s)", crust).value, 1e-25)
    }

    @Test
    fun barePowerWithoutMantissa() {
        assertEquals(1.0e-8, num("10^-8", crust).value, 1e-12)
    }

    // ---- Rule 10: ranges, only where the field allows them -----------------------------

    @Test
    fun hardnessRangeParsesBothEnds() {
        val q = num("160-350 (MPa)", vickers)
        assertEquals(160.0, q.value, 0.0)
        assertEquals(350.0, q.high!!, 0.0)
        assertEquals(255.0, q.mid, 0.0)
        assertTrue(q.isRange)
        assertEquals("MPa", q.unit)
    }

    @Test
    fun rangeIsNotAppliedToFieldsThatDoNotAllowIt() {
        val q = num("2743 (K)", meltingPoint)
        assertFalse(q.isRange)
        assertEquals(2743.0, q.value, 0.0)
    }

    // ---- Rules 11-12: plain numbers, separators, percent, text -------------------------

    @Test
    fun thousandsSeparatorIsHandled() {
        assertEquals(29600.0, num("29,600", crust).value, 0.0)
    }

    @Test
    fun percentBecomesItsOwnUnit() {
        val q = num("0.00007%", crust)
        assertEquals(0.00007, q.value, 1e-12)
        assertEquals("%", q.unit)
    }

    @Test
    fun trailingUnitIsCapturedWithoutParentheses() {
        assertEquals("nΩm", num("22.14 nΩm", resistivity).unit)
        assertEquals("W/(m·K)", num("318 W/(m·K)", FieldRegistry.byId.getValue("thermal_conductivity")).unit)
        // NFKC folds the data's U+00B5 micro sign to U+03BC Greek mu, so units compare consistently.
        assertEquals("μm/(m·K)", num("14.2 µm/(m·K)", FieldRegistry.byId.getValue("thermal_expansion")).unit)
    }

    @Test
    fun caretPowersInUnitsAreNormalised() {
        assertEquals("g/cm³", num("19.3 (g/cm^3)", density).unit)
    }

    @Test
    fun traceIsItsOwnValue() {
        assertEquals(FieldValue.Trace, ValueParser.parse("trace", humanBody))
    }

    @Test
    fun textWithoutANumberStaysText() {
        val parsed = ValueParser.parse("Deep Antiquity", year)
        assertTrue(parsed is FieldValue.Text)
        assertEquals("Deep Antiquity", (parsed as FieldValue.Text).raw)
    }

    @Test
    fun displayAlwaysPreservesTheAuthoredString() {
        assertEquals("19.3 (g/cm^3)", num("19.3 (g/cm^3)", density).display)
        assertEquals("160-350 (MPa)", num("160-350 (MPa)", vickers).display)
    }

    // ---- Enums --------------------------------------------------------------------------

    @Test
    fun enumFieldsCanonicaliseRatherThanParseNumbers() {
        val parsed = ValueParser.parse("Lanthanoids", series)
        assertTrue(parsed is FieldValue.Enum)
        parsed as FieldValue.Enum
        assertEquals(SeriesId.LANTHANOID.name, parsed.canonical)
        assertEquals("Lanthanoids", parsed.localized)
    }

    @Test
    fun enumSentinelIsMissing() {
        assertTrue(ValueParser.parse("---", FieldRegistry.byId.getValue("magnetic_type")).isMissing)
    }
}
