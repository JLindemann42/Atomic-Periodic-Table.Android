package com.jlindemann.science.model

import com.jlindemann.science.activities.tools.UnitDefinition

/**
 * The app's unit conversion tables, shared by the Unit Conversion tool and the AI agent.
 *
 * Every category except [TEMPERATURE] is purely multiplicative: each unit carries a factor
 * relative to that category's SI base unit, so converting is `value * from.factor / to.factor`.
 * Temperature is affine and therefore handled separately by [convertTemperature].
 */
object UnitCatalog {

    const val TEMPERATURE = "Temperature"

    val categories: Map<String, List<UnitDefinition>> = mapOf(
        "Length" to listOf(
            UnitDefinition("Meter", 1.0),
            UnitDefinition("Kilometer", 1000.0),
            UnitDefinition("Centimeter", 0.01),
            UnitDefinition("Millimeter", 0.001),
            UnitDefinition("Inch", 0.0254),
            UnitDefinition("Foot", 0.3048),
            UnitDefinition("Yard", 0.9144),
            UnitDefinition("Mile", 1609.344)
        ),
        "Mass" to listOf(
            UnitDefinition("Kilogram", 1.0),
            UnitDefinition("Gram", 0.001),
            UnitDefinition("Milligram", 0.000001),
            UnitDefinition("Pound", 0.45359237),
            UnitDefinition("Ounce", 0.0283495231)
        ),
        "Volume" to listOf(
            UnitDefinition("Liter", 1.0),
            UnitDefinition("Milliliter", 0.001),
            UnitDefinition("Cubic meter", 1000.0),
            UnitDefinition("Gallon", 3.78541),
            UnitDefinition("Pint", 0.473176)
        ),
        "Area" to listOf(
            UnitDefinition("Square meter", 1.0),
            UnitDefinition("Square kilometer", 1_000_000.0),
            UnitDefinition("Square centimeter", 0.0001),
            UnitDefinition("Square mile", 2_589_988.11),
            UnitDefinition("Acre", 4046.85642)
        ),
        "Velocity" to listOf(
            UnitDefinition("Meter/second", 1.0), UnitDefinition("Kilometer/hour", 0.277778),
            UnitDefinition("Mile/hour", 0.44704), UnitDefinition("Foot/second", 0.3048)
        ),
        "Energy" to listOf(
            UnitDefinition("Joule", 1.0),
            UnitDefinition("Kilojoule", 1000.0),
            UnitDefinition("Calorie", 4.184),
            UnitDefinition("Kilocalorie", 4184.0),
            UnitDefinition("Watt hour", 3600.0)
        ),
        "Frequency" to listOf(
            UnitDefinition("Hertz", 1.0), UnitDefinition("Kilohertz", 1000.0),
            UnitDefinition("Megahertz", 1_000_000.0), UnitDefinition("Gigahertz", 1_000_000_000.0)
        ),
        TEMPERATURE to listOf(
            UnitDefinition("Celsius", 0.0),
            UnitDefinition("Fahrenheit", 0.0),
            UnitDefinition("Kelvin", 0.0)
        ),
        "Time" to listOf(
            UnitDefinition("Second", 1.0),
            UnitDefinition("Millisecond", 0.001),
            UnitDefinition("Minute", 60.0),
            UnitDefinition("Hour", 3600.0),
            UnitDefinition("Day", 86400.0)
        ),
        "Force" to listOf(
            UnitDefinition("Newton", 1.0), UnitDefinition("Kilonewton", 1000.0),
            UnitDefinition("Dyne", 0.00001), UnitDefinition("Pound-force", 4.4482216),
            UnitDefinition("Kilogram-force", 9.80665)
        ),
        "Power" to listOf(
            UnitDefinition("Watt", 1.0), UnitDefinition("Kilowatt", 1000.0),
            UnitDefinition("Megawatt", 1_000_000.0), UnitDefinition("Horsepower", 745.699872)
        ),
        "Voltage" to listOf(
            UnitDefinition("Volt", 1.0),
            UnitDefinition("Millivolt", 0.001),
            UnitDefinition("Kilovolt", 1000.0)
        ),
        "Resistance" to listOf(
            UnitDefinition("Ohm", 1.0), UnitDefinition("Milliohm", 0.001),
            UnitDefinition("Kiloohm", 1000.0), UnitDefinition("Megaohm", 1_000_000.0)
        ),
        "Pressure" to listOf(
            UnitDefinition("Pascal", 1.0), UnitDefinition("Kilopascal", 1000.0),
            UnitDefinition("Bar", 100_000.0), UnitDefinition("Atmosphere", 101_325.0),
            UnitDefinition("PSI", 6894.757)
        )
    )

    /** Units in a category, or empty if the category is unknown. */
    fun units(category: String): List<UnitDefinition> = categories[category] ?: emptyList()

    /** The category a unit name belongs to, or null if no category defines it. */
    fun categoryOf(unitName: String): String? =
        categories.entries.firstOrNull { entry -> entry.value.any { it.name == unitName } }?.key

    /**
     * Convert between two units of the same category.
     *
     * @return the converted value, or null if the category or either unit is unknown
     */
    fun convert(category: String, from: String, to: String, value: Double): Double? {
        if (category == TEMPERATURE) return convertTemperature(from, to, value)
        val units = categories[category] ?: return null
        val fromDef = units.find { it.name == from } ?: return null
        val toDef = units.find { it.name == to } ?: return null
        return value * fromDef.factor / toDef.factor
    }

    /** Affine temperature conversion. Unknown unit names pass the value through as Celsius. */
    fun convertTemperature(from: String, to: String, value: Double): Double {
        val celsius = when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32) * 5 / 9
            "Kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "Celsius" -> celsius
            "Fahrenheit" -> celsius * 9 / 5 + 32
            "Kelvin" -> celsius + 273.15
            else -> celsius
        }
    }

    /** Human-readable description of a temperature conversion, shown by the tool. */
    fun temperatureFormula(from: String, to: String): String = when {
        from == "Celsius" && to == "Fahrenheit" -> "Multiply with 9/5 and add 32"
        from == "Celsius" && to == "Kelvin" -> "Add 273.15"
        from == "Fahrenheit" && to == "Celsius" -> "Subtract 32, divide with 5/9"
        from == "Fahrenheit" && to == "Kelvin" -> "Subtract 32, multiply with 5/9, add 273.15"
        from == "Kelvin" && to == "Celsius" -> "subtract 273.15"
        from == "Kelvin" && to == "Fahrenheit" -> "subtract 273.15, multiply with 9/5, add 32"
        else -> "no conversion"
    }
}
