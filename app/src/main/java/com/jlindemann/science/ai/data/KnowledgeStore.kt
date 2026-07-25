package com.jlindemann.science.ai.data

import java.util.concurrent.ConcurrentHashMap

/** Lowercase English element name, which is the key used throughout the element JSON. */
typealias ElementKey = String

/** NFPA 704 diamond ratings. */
data class Nfpa(val health: Int?, val flammability: Int?, val instability: Int?, val special: String?)

/**
 * One element, fully parsed and typed.
 *
 * [period] and [groupNumber] are derived from [atomicNumber] rather than read from the JSON,
 * which has no such keys.
 */
data class ElementRecord(
    val key: ElementKey,
    val symbol: String,
    val atomicNumber: Int,
    val period: Int,
    val groupNumber: Int?,
    val series: SeriesId,
    val block: Block,
    val phase: String?,
    val radioactive: Boolean,
    val values: Map<String, FieldValue>,
    val isotopes: List<Isotope>,
    val nfpa: Nfpa?
) {
    fun value(fieldId: String): FieldValue = values[fieldId] ?: FieldValue.Missing
    fun quantity(fieldId: String): Quantity? = value(fieldId).asQuantity()
    val stableIsotopeCount: Int get() = isotopes.count { it.stable }
}

/** The seven fields that differ between language files, for one language. */
data class LocalizedElement(
    val name: String,
    val description: String,
    val seriesName: String,
    val appearance: String,
    val phase: String,
    val electricalType: String,
    val magneticType: String
)

/** A language's display overlay on top of the shared numeric index. */
class LocalizedView(val language: String, val elements: Map<ElementKey, LocalizedElement>) {
    fun name(key: ElementKey): String? = elements[key]?.name
}

/**
 * The agent's typed view of the element data.
 *
 * Only seven of the 124 non-isotope fields differ between the twelve language files
 * (`element`, `element_group`, `description`, `element_appearance`, `element_phase`,
 * `electrical_type`, `magnetic_type`); everything numeric and structural is identical. So the
 * expensive parse runs once against English and each language costs only a 118-row string
 * overlay, instead of holding twelve fully parsed ~1 MB tables.
 */
class KnowledgeStore private constructor(
    val elements: List<ElementRecord>,
    val byKey: Map<ElementKey, ElementRecord>,
    val bySymbol: Map<String, ElementRecord>,
    val byAtomicNumber: Map<Int, ElementRecord>,
    /** Field id to how many of the 118 elements actually have a value, used for honesty notes. */
    val coverage: Map<String, Int>
) {

    val size: Int get() = elements.size

    fun element(key: ElementKey?): ElementRecord? = key?.let { byKey[it.lowercase()] }
    fun bySymbol(symbol: String?): ElementRecord? = symbol?.let { bySymbol[it.lowercase()] }
    fun byNumber(z: Int?): ElementRecord? = z?.let { byAtomicNumber[it] }

    /** Elements in a series, using the canonicalised classification. */
    fun inSeries(series: SeriesId): List<ElementRecord> = elements.filter { it.series == series }

    /** How many of the 118 elements have a value for a field. */
    fun coverageOf(fieldId: String): Int = coverage[fieldId] ?: 0

    /**
     * A field's value converted into a requested unit where possible.
     * Returns the unconverted quantity when no conversion applies.
     */
    fun quantityIn(record: ElementRecord, fieldId: String, targetUnit: String?): Quantity? {
        val spec = FieldRegistry.byId[fieldId] ?: return null
        // Temperature ships all three scales, so prefer the stored one over converting.
        if (targetUnit != null && spec.jsonKeys.size > 1) {
            val wanted = UnitConverter.canonical(targetUnit)
            record.values.entries
                .firstOrNull { it.key == "$fieldId@$wanted" }
                ?.value?.asQuantity()?.let { return it }
        }
        val base = record.quantity(fieldId) ?: return null
        if (targetUnit == null) return base
        return UnitConverter.convert(base, targetUnit) ?: base
    }

    companion object {
        @Volatile
        private var shared: KnowledgeStore? = null
        private val overlays = ConcurrentHashMap<String, LocalizedView>()
        private val lock = Any()

        /** Language whose table carries the canonical numeric and structural values. */
        const val BASE_LANGUAGE = "en"

        /**
         * The process-wide index, built once from the base-language table.
         * Safe to call from any thread; the build runs at most once.
         */
        fun get(source: ElementSource): KnowledgeStore? {
            shared?.let { return it }
            synchronized(lock) {
                shared?.let { return it }
                val table = source.load(BASE_LANGUAGE)
                    ?: source.availableLanguages().firstNotNullOfOrNull { source.load(it) }
                    ?: return null
                val built = build(table)
                shared = built
                return built
            }
        }

        /** The display overlay for a language, built and cached on first use. */
        fun overlay(source: ElementSource, language: String): LocalizedView? {
            overlays[language]?.let { return it }
            val table = source.load(language) ?: return null
            val view = buildOverlay(language, table)
            overlays[language] = view
            return view
        }

        /** Drop all cached state. Used by tests and by a language-data change. */
        fun clear() {
            synchronized(lock) { shared = null }
            overlays.clear()
        }

        /** Build an index directly from a table, bypassing the cache. Used by tests. */
        fun build(table: ElementTable): KnowledgeStore {
            val records = ArrayList<ElementRecord>(table.size)
            val coverage = HashMap<String, Int>()

            for ((key, row) in table.rows) {
                val values = HashMap<String, FieldValue>(FieldRegistry.ALL.size * 2)

                for (spec in FieldRegistry.ALL) {
                    if (spec.ordinalRange != null) {
                        // Banked fields: store every populated slot under "id#n", plus slot 1 as the default.
                        for (slot in spec.ordinalRange) {
                            val parsed = ValueParser.parse(row[spec.jsonKeyForOrdinal(slot)], spec)
                            if (!parsed.isMissing) values["${spec.id}#$slot"] = parsed
                        }
                        values["${spec.id}#1"]?.let { values[spec.id] = it }
                    } else if (spec.jsonKeys.size > 1) {
                        // Multi-scale fields: keep each stored scale so a unit request is a pure
                        // lookup, but always normalise the primary to the canonical unit. Taking
                        // whichever scale happened to be present first would let one element's
                        // Celsius be ranked against another's Kelvin.
                        var primary: FieldValue = FieldValue.Missing
                        var fallback: FieldValue = FieldValue.Missing
                        for (jsonKey in spec.jsonKeys) {
                            val parsed = ValueParser.parse(row[jsonKey], spec)
                            if (parsed.isMissing) continue
                            val unit = UnitConverter.canonical(parsed.asQuantity()?.unit)
                            if (unit != null) values["${spec.id}@$unit"] = parsed
                            if (unit == spec.canonicalUnit) primary = parsed
                            if (fallback.isMissing) fallback = parsed
                        }
                        if (primary.isMissing && !fallback.isMissing) {
                            // No canonical-unit reading stored; convert whatever we do have.
                            primary = fallback.asQuantity()
                                ?.let { UnitConverter.convert(it, spec.canonicalUnit) }
                                ?.let { FieldValue.Num(it) }
                                ?: fallback
                        }
                        if (!primary.isMissing) values[spec.id] = primary
                    } else {
                        val parsed = ValueParser.parse(row[spec.jsonKeys.first()], spec)
                        if (!parsed.isMissing) values[spec.id] = parsed
                    }
                    if (values.containsKey(spec.id)) coverage.merge(spec.id, 1, Int::plus)
                }

                val atomicNumber = values["atomic_number"]?.asQuantity()?.value?.toInt() ?: 0
                val nfpa = Nfpa(
                    health = values["nfpa_health"]?.asQuantity()?.value?.toInt(),
                    flammability = values["nfpa_flammability"]?.asQuantity()?.value?.toInt(),
                    instability = values["nfpa_instability"]?.asQuantity()?.value?.toInt(),
                    special = (values["nfpa_special"] as? FieldValue.Text)?.raw
                )

                records.add(
                    ElementRecord(
                        key = key,
                        symbol = (values["symbol"] as? FieldValue.Text)?.raw.orEmpty(),
                        atomicNumber = atomicNumber,
                        period = FieldRegistry.periodOf(atomicNumber),
                        groupNumber = FieldRegistry.groupOf(atomicNumber),
                        series = SeriesCanon.series(row["element_group"]?.toString()),
                        block = SeriesCanon.block(row["element_block"]?.toString()),
                        phase = SeriesCanon.phase(row["element_phase"]?.toString()),
                        radioactive = SeriesCanon.radioactive(row["radioactive"]?.toString()),
                        values = values,
                        isotopes = IsotopeParser.parse(row),
                        nfpa = if (nfpa.health == null && nfpa.flammability == null &&
                            nfpa.instability == null && nfpa.special == null
                        ) null else nfpa
                    )
                )
            }

            records.sortBy { it.atomicNumber }
            return KnowledgeStore(
                elements = records,
                byKey = records.associateBy { it.key.lowercase() },
                bySymbol = records.filter { it.symbol.isNotEmpty() }.associateBy { it.symbol.lowercase() },
                byAtomicNumber = records.associateBy { it.atomicNumber },
                coverage = coverage
            )
        }

        private fun buildOverlay(language: String, table: ElementTable): LocalizedView {
            val map = HashMap<ElementKey, LocalizedElement>(table.size * 2)
            for ((key, row) in table.rows) {
                map[key] = LocalizedElement(
                    name = row["element"]?.toString().orEmpty(),
                    description = row["description"]?.toString().orEmpty(),
                    seriesName = row["element_group"]?.toString().orEmpty(),
                    appearance = row["element_appearance"]?.toString().orEmpty(),
                    phase = row["element_phase"]?.toString().orEmpty(),
                    electricalType = row["electrical_type"]?.toString().orEmpty(),
                    magneticType = row["magnetic_type"]?.toString().orEmpty()
                )
            }
            return LocalizedView(language, map)
        }
    }
}
