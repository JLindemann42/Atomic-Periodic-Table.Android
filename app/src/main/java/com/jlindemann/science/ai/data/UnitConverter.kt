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
        // Sea-water abundance. NFKC folds U+00B5 MICRO SIGN onto U+03BC GREEK SMALL MU before this
        // lookup runs, so the greek key is the one that fires; the ASCII spelling is here for
        // typed queries. No FACTORS entry: mass per volume does not convert to mass per mass
        // without a density.
        "μg/l" to "µg/l", "ug/l" to "µg/l",
        "b" to "b", "barn" to "b", "barns" to "b",
        "w/(m·k)" to "W/(m·K)", "w/(m*k)" to "W/(m·K)", "w/mk" to "W/(m·K)",
        "j/(g·k)" to "J/(g·K)", "j/(g*k)" to "J/(g·K)",
        "j/(mol·k)" to "J/(mol·K)", "j/(mol*k)" to "J/(mol·K)",
        // Time. The canonical year token is "yr", not "a": `"a"` is already angstrom above, and a
        // query reading "1.5 a" means 1.5 Å in this app's data far more often than 1.5 years. For
        // the same reason ka/Ma/Ga are absent — they would have to share the collision.
        "s" to "s", "sec" to "s", "second" to "s", "seconds" to "s", "sekund" to "s",
        "sekunder" to "s", "sekunde" to "s", "sekunden" to "s", "segundo" to "s",
        "segundos" to "s", "seconde" to "s", "secondes" to "s", "secondi" to "s",
        "ms" to "ms", "millisecond" to "ms", "milliseconds" to "ms",
        "us" to "µs", "microsecond" to "µs", "microseconds" to "µs",
        "ns" to "ns", "nanosecond" to "ns", "nanoseconds" to "ns",
        "min" to "min", "minute" to "min", "minutes" to "min", "minuter" to "min",
        "minuten" to "min", "minuto" to "min", "minutos" to "min", "minuti" to "min",
        "h" to "h", "hr" to "h", "hrs" to "h", "hour" to "h", "hours" to "h",
        "timme" to "h", "timmar" to "h", "stunde" to "h", "stunden" to "h",
        "hora" to "h", "horas" to "h", "heure" to "h", "heures" to "h", "ora" to "h", "ore" to "h",
        "d" to "d", "day" to "d", "days" to "d", "dag" to "d", "dagar" to "d",
        "tag" to "d", "tage" to "d", "dia" to "d", "dias" to "d", "jour" to "d", "jours" to "d",
        "giorno" to "d", "giorni" to "d",
        // No bare "y" and no bare "an": both are ordinary words. Spanish "y" is "and", so
        // "compara uranio-235 y uranio-238" read as "235 years" and became a decay calculation.
        "yr" to "yr", "yrs" to "yr", "year" to "yr", "years" to "yr",
        // Accented and inflected forms as authored. `canonical` applies NFKC, which leaves å and ñ
        // as single codepoints, so a folded spelling never matches what the user actually types.
        "ar" to "yr", "år" to "yr", "jahr" to "yr", "jahre" to "yr", "jahren" to "yr",
        "ano" to "yr", "anos" to "yr",
        "año" to "yr", "años" to "yr", "ans" to "yr", "annee" to "yr",
        "annees" to "yr", "anno" to "yr", "anni" to "yr", "taon" to "yr",
        "kyr" to "kyr", "myr" to "Myr", "gyr" to "Gyr",
        // Volume, base litre.
        "l" to "L", "litre" to "L", "litres" to "L", "liter" to "L", "liters" to "L",
        "ml" to "mL", "millilitre" to "mL", "millilitres" to "mL", "milliliter" to "mL",
        "milliliters" to "mL", "cc" to "cm³",
        "ul" to "µL", "microlitre" to "µL", "microliter" to "µL",
        "dm3" to "dm³", "dm^3" to "dm³", "dm³" to "dm³",
        "cm3" to "cm³", "cm^3" to "cm³", "cm³" to "cm³",
        "m3" to "m³", "m^3" to "m³", "m³" to "m³",
        // Amount of substance.
        "mole" to "mol", "moles" to "mol", "moli" to "mol", "mols" to "mol",
        "mmol" to "mmol", "millimole" to "mmol", "millimoles" to "mmol",
        "umol" to "µmol", "micromole" to "µmol", "micromoles" to "µmol",
        // Molar concentration. "mm" is deliberately absent — it is millimetre to every reader,
        // and mapping it to millimolar would make "0.5 mm" a concentration without warning.
        "molar" to "M", "mol/l" to "M", "mol/liter" to "M", "mol/litre" to "M",
        "moles per litre" to "M", "moles per liter" to "M", "mol/dm3" to "mol/dm³",
        "mol/m3" to "mol/m³", "millimolar" to "mM", "micromolar" to "µM"
    )

    /**
     * Units whose meaning depends on capitalisation.
     *
     * [canonical] lowercases before consulting [ALIASES], which is right for `Fahrenheit` and
     * `GPa` and fatal for these: `M` is molar and `m` is metre, and case is the only thing that
     * tells them apart. Checked before the fold, so the pair survives.
     */
    private val CASE_SENSITIVE: Map<String, String> = mapOf(
        "M" to "M", "mM" to "mM", "µM" to "µM", "μM" to "µM", "m" to "m", "L" to "L"
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
        "J/(mol·K)" to (Dimension.HEAT_CAPACITY to 1.0),
        // Time, base second. The year is the Julian year, 365.25 days, matching what IsotopeParser
        // uses to normalise half-lives — the two have to agree or a decay answer contradicts the
        // isotope table it cites.
        "s" to (Dimension.TIME to 1.0),
        "ms" to (Dimension.TIME to 1e-3),
        "µs" to (Dimension.TIME to 1e-6),
        "ns" to (Dimension.TIME to 1e-9),
        "min" to (Dimension.TIME to 60.0),
        "h" to (Dimension.TIME to 3600.0),
        "d" to (Dimension.TIME to 86400.0),
        "yr" to (Dimension.TIME to 3.15576e7),
        "kyr" to (Dimension.TIME to 3.15576e10),
        "Myr" to (Dimension.TIME to 3.15576e13),
        "Gyr" to (Dimension.TIME to 3.15576e16),
        // Volume, base litre
        "L" to (Dimension.VOLUME to 1.0),
        "mL" to (Dimension.VOLUME to 1e-3),
        "µL" to (Dimension.VOLUME to 1e-6),
        "dm³" to (Dimension.VOLUME to 1.0),
        "cm³" to (Dimension.VOLUME to 1e-3),
        "m³" to (Dimension.VOLUME to 1000.0),
        // Amount of substance, base mol
        "mol" to (Dimension.AMOUNT to 1.0),
        "mmol" to (Dimension.AMOUNT to 1e-3),
        "µmol" to (Dimension.AMOUNT to 1e-6),
        // Molar concentration, base mol/L
        "M" to (Dimension.MOLARITY to 1.0),
        "mol/dm³" to (Dimension.MOLARITY to 1.0),
        "mM" to (Dimension.MOLARITY to 1e-3),
        "µM" to (Dimension.MOLARITY to 1e-6),
        "mol/m³" to (Dimension.MOLARITY to 1e-3)
    )

    /**
     * Conversions that cross a dimension because a physical constant relates them.
     *
     * One eV per particle is 96.485 kJ per mole — the same energy counted per particle rather than
     * per mole. Kept out of [FACTORS] deliberately: putting the two in one dimension would make an
     * ionisation energy in eV and a fusion enthalpy in kJ/mol look interchangeable to
     * `Filter.FieldCompare`, and a ranking would silently mix them. Applied only when a user asks
     * for the conversion outright, never by comparison or sorting.
     */
    private val BRIDGES: Map<Pair<Dimension, Dimension>, Double> = mapOf(
        (Dimension.ENERGY to Dimension.ENERGY_PER_MOL) to 96.48533212,
        (Dimension.ENERGY_PER_MOL to Dimension.ENERGY) to 1.0 / 96.48533212
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
        CASE_SENSITIVE[trimmed]?.let { return it }
        ALIASES[trimmed.lowercase()]?.let { return it }
        return trimmed
    }

    /**
     * The canonical form of [token], but only when it is a unit this converter actually knows.
     *
     * [canonical] echoes anything it does not recognise straight back, which is right for a value
     * read off the element data — the authored unit is the truth even when the table below has
     * never heard of it. A query is the opposite case: something has to decide whether the word
     * after a number is a unit at all, and an echo answers "yes" to everything.
     */
    fun knownUnit(token: String?): String? {
        val c = canonical(token) ?: return null
        return if (c in TEMPERATURE_UNITS || c in FACTORS) c else null
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

    /**
     * Convert within a dimension, or across one of the [BRIDGES] when the dimensions differ.
     *
     * Used only by the standalone unit-conversion intent, where the user has named both units and
     * asked for the crossing explicitly. Ranking, filtering and property lookups go through
     * [convert], which refuses the crossing.
     *
     * @return the converted value, or null when neither route applies
     */
    fun convertAcrossBridges(value: Double, from: String?, to: String?): Double? {
        convert(value, from, to)?.let { return it }
        val f = canonical(from) ?: return null
        val t = canonical(to) ?: return null
        val fromDim = dimensionOf(f) ?: return null
        val toDim = dimensionOf(t) ?: return null
        val bridge = BRIDGES[fromDim to toDim] ?: return null
        // Through each dimension's base unit, so "96485 J/mol in eV" works as well as kJ/mol.
        val fromFactor = FACTORS[f]?.second ?: return null
        val toFactor = FACTORS[t]?.second ?: return null
        return value * fromFactor * bridge / toFactor
    }

    /** Whether [from] and [to] can be converted, within a dimension or across a bridge. */
    fun convertible(from: String?, to: String?): Boolean {
        val fromDim = dimensionOf(from) ?: return false
        val toDim = dimensionOf(to) ?: return false
        return fromDim == toDim || (fromDim to toDim) in BRIDGES
    }

    /** Whether converting [from] to [to] crosses a dimension through a physical constant. */
    fun isBridged(from: String?, to: String?): Boolean {
        val fromDim = dimensionOf(from) ?: return false
        val toDim = dimensionOf(to) ?: return false
        return fromDim != toDim && (fromDim to toDim) in BRIDGES
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
            display = formatValue(value) + (target?.let { " $it" } ?: ""),
            // The unit is written into `display` right here, so a renderer must not add it again.
            unitAuthored = target != null
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
