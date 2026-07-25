package com.jlindemann.science.ai.data

/**
 * One element's raw fields, keyed by the JSON key.
 *
 * Values are the plain shapes the JSON actually holds: [String] for nearly everything,
 * [Int] for the NFPA diamond fields, and `Map<String, String>` for the two nested fields
 * (`lattice_constants`, `debye_temperature`). Deliberately plain Kotlin, no `org.json`.
 */
typealias ElementRow = Map<String, Any?>

/** The whole element table for one language, keyed by the lowercase English element name. */
class ElementTable(val rows: Map<String, ElementRow>) {
    val keys: Set<String> get() = rows.keys
    val size: Int get() = rows.size
    operator fun get(key: String): ElementRow? = rows[key]
}

/**
 * Where the element tables come from.
 *
 * This interface is the boundary that keeps `org.json` — which is a throwing stub in JVM unit
 * tests — on the Android side. Everything downstream of it (parsers, index, retrieval, planner)
 * works on plain Kotlin collections and is testable without Robolectric.
 */
interface ElementSource {
    /** Language codes with an element table available. */
    fun availableLanguages(): List<String>

    /** The table for a language, or null when it cannot be loaded. */
    fun load(language: String): ElementTable?
}

/** In-memory [ElementSource] for unit tests and previews. */
class MapElementSource(private val byLanguage: Map<String, ElementTable>) : ElementSource {
    override fun availableLanguages(): List<String> = byLanguage.keys.sorted()
    override fun load(language: String): ElementTable? = byLanguage[language]
}
