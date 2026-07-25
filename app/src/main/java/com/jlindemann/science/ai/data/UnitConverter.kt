package com.jlindemann.science.ai.data

/**
 * Converts between the units the element data actually uses, so a question like
 * "melting point of gold in Fahrenheit" can be answered from whichever scale is stored.
 *
 * Most dimensions are multiplicative and convert through a base unit. Temperature is affine and
 * is handled separately — that distinction is why a single factor table is not enough.
 */
object UnitConverter {

    /** Aliases mapped onto a canonical spelling, so `(g/cm^3)`, `g/cm3` and `g/cm³` all agree. */
    private val ALIASES: Map<String, String> = mapOf(
        "k" to "K", "kelvin" to "K", "kelvins" to "K",
        "c" to "°C", "°c" to "°C", "celsius" to "°C", "centigrade" to "°C", "degc" to "°C",
        "f" to "°F", "°f" to "°F", "fahrenheit" to "°F", "degf" to "°F",
        "g/cm3" to "g/cm³", "g/cm^3" to "g/cm³", "g/cm³" to "g/cm³",
        "kg/m3" to "kg/m³", "kg/m^3" to "kg/m³", "kg/m³" to "kg/m³",
        "u" to "u", "amu" to "u", "da" to "u", "dalton" to "u",
        "pm" to "pm", "picometer" to "pm", "picometre" to "pm",
        "nm" to "nm", "angstrom" to "Å", "å" to "Å", "a" to "Å",
        "ev" to "eV", "kj/mol" to "kJ/mol", "j/mol" to "J/mol",
        "gpa" to "GPa", "mpa" to "MPa", "pa" to "Pa", "kpa" to "kPa",
        "m/s" to "m/s", "km/h" to "km/h",
        "nωm" to "nΩm", "nohmm" to "nΩm", "μωm" to "µΩm", "µωm" to "µΩm", "ωm" to "Ωm",
        "cm3/mol" to "cm³/mol", "cm^3/mol" to "cm³/mol", "cm³/mol" to "cm³/mol",
        "mg/kg" to "mg/kg", "ppm" to "mg/kg", "%" to "%",
        "b" to "b", "barn" to "b", "barns" to "b",
        "w/(m·k)" to "W/(m·K)", "w/(m*k)" to "W/(m·K)", "w/mk" to "W/(m·K)",
        "j/(g·k)" to "J/(g·K)", "j/(g*k)" to "J/(g·K)",
        "j/(mol·k)" to "J/(mol·K)", "j/(mol*k)" to "J/(mol·K)"
    )

    /** Multiplicative units: canonical name to (dimension, factor relative to the base unit). */
    private val FACTORS: Map<String, Pair<Dimension, Double>> = mapOf(
        // Length, base metre
        "pm" to (Dimension.LENGTH to 1e-12),
        "Å" to (Dimension.LENGTH to 1e-10),
        "nm" to (Dimension.LENGTH to 1e-9),
        "m" to (Dimension.LENGTH to 1.0),
        // Density, base kg/m³
        "g/cm³" to (Dimension.DENSITY to 1000.0),
        "kg/m³" to (Dimension.DENSITY to 1.0),
        // Mass, base u
        "u" to (Dimension.MASS to 1.0),
        // Energy, base eV
        "eV" to (Dimension.ENERGY to 1.0),
        // Energy per mole, base kJ/mol
        "kJ/mol" to (Dimension.ENERGY_PER_MOL to 1.0),
        "J/mol" to (Dimension.ENERGY_PER_MOL to 0.001),
        // Pressure, base Pa
        "Pa" to (Dimension.PRESSURE to 1.0),
        "kPa" to (Dimension.PRESSURE to 1e3),
        "MPa" to (Dimension.PRESSURE to 1e6),
        "GPa" to (Dimension.PRESSURE to 1e9),
        // Velocity, base m/s
        "m/s" to (Dimension.VELOCITY to 1.0),
        "km/h" to (Dimension.VELOCITY to 1.0 / 3.6),
        // Resistivity, base Ωm
        "nΩm" to (Dimension.RESISTIVITY to 1e-9),
        "µΩm" to (Dimension.RESISTIVITY to 1e-6),
        "Ωm" to (Dimension.RESISTIVITY to 1.0),
        // Molar volume, base cm³/mol
        "cm³/mol" to (Dimension.VOLUME_PER_MOL to 1.0),
        // Concentration, base mg/kg
        "mg/kg" to (Dimension.CONCENTRATION to 1.0),
        "%" to (Dimension.CONCENTRATION to 10_000.0),
        // Area, base barn
        "b" to (Dimension.AREA to 1.0),
        // Conductivity and heat capacity have one unit each in this data
        "W/(m·K)" to (Dimension.CONDUCTIVITY to 1.0),
        "J/(g·K)" to (Dimension.HEAT_CAPACITY to 1.0),
        "J/(mol·K)" to (Dimension.HEAT_CAPACITY to 1.0)
    )

    private val TEMPERATURE_UNITS = setOf("K", "°C", "°F")

    /**
     * Canonical spelling for a raw unit string, or null when it is blank.
     *
     * NFKC is applied so the two mu characters agree: the element data writes U+00B5 MICRO SIGN,
     * which NFKC folds to U+03BC GREEK SMALL LETTER MU. Both sides of every comparison run
     * through here, so `µm/(m·K)` and `μm/(m·K)` are the same unit.
     */
    fun canonical(raw: String?): String? {
        val normalized = raw?.let {
            java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFKC)
        }
        val trimmed = normalized?.trim()?.trim('(', ')')?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        ALIASES[trimmed.lowercase()]?.let { return it }
        return trimmed
    }

    /** The dimension a unit belongs to, or null when it is unknown. */
    fun dimensionOf(unit: String?): Dimension? {
        val c = canonical(unit) ?: return null
        if (c in TEMPERATURE_UNITS) return Dimension.TEMPERATURE
        return FACTORS[c]?.first
    }

    /**
     * Convert a value between two units.
     *
     * @return the converted value, or null when the units are unknown or of different dimensions
     */
    fun convert(value: Double, from: String?, to: String?): Double? {
        val f = canonical(from) ?: return null
        val t = canonical(to) ?: return null
        if (f == t) return value

        if (f in TEMPERATURE_UNITS && t in TEMPERATURE_UNITS) return convertTemperature(value, f, t)

        val (fromDim, fromFactor) = FACTORS[f] ?: return null
        val (toDim, toFactor) = FACTORS[t] ?: return null
        if (fromDim != toDim) return null
        return value * fromFactor / toFactor
    }

    /** Affine temperature conversion between K, °C and °F. */
    fun convertTemperature(value: Double, from: String, to: String): Double? {
        val kelvin = when (from) {
            "K" -> value
            "°C" -> value + 273.15
            "°F" -> (value - 32.0) * 5.0 / 9.0 + 273.15
            else -> return null
        }
        return when (to) {
            "K" -> kelvin
            "°C" -> kelvin - 273.15
            "°F" -> (kelvin - 273.15) * 9.0 / 5.0 + 32.0
            else -> null
        }
    }

    /** Convert a [Quantity] into a target unit, carrying the range and flags across. */
    fun convert(quantity: Quantity, to: String?): Quantity? {
        val value = convert(quantity.value, quantity.unit, to) ?: return null
        val high = quantity.high?.let { convert(it, quantity.unit, to) }
        val target = canonical(to)
        return quantity.copy(
            value = value,
            high = high,
            unit = target,
            display = formatValue(value) + (target?.let { " $it" } ?: "")
        )
    }

    /** Render a converted number without trailing noise: 2 decimals, trimmed. */
    fun formatValue(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded) && kotlin.math.abs(rounded) < 1e15) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
