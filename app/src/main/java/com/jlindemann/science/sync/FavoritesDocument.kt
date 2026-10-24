package com.jlindemann.science.sync

/**
 * The four tools whose saved items sync, with their wire names under `favorites`.
 *
 * [keyField] identifies an item within its tool, [valueField] is what it saved. Wire identifiers,
 * shared with the Windows app's `FavoritesDocument` - renaming one forks the two apps' data.
 */
enum class FavoriteKind(val array: String, val keyField: String, val valueField: String) {
    /** The molar mass calculator: a formula and its molar mass, as a plain number. */
    COMPOUND("compounds", "formula", "molarMass"),

    /** The equation balancer: what was typed and what it balanced to. */
    EQUATION("equations", "input", "balanced"),

    /** The ideal gas calculator: the inputs and the result. */
    IDEAL_GAS("idealGas", "summary", "result"),

    /**
     * The unit converter: a category, two units and an input, and what it converted to. No single
     * key field - stored as `category`, `fromUnit`, `toUnit`, and `inputValue` and `convertedValue`
     * as numbers; see [FavoritesDocument.unitConversionKey].
     */
    UNIT_CONVERSION("unitConversions", "", ""),
}

/**
 * One saved item, or the record that it was removed.
 *
 * @param updatedAt Epoch milliseconds of the last save or removal; the newer one wins.
 * @param deleted A removal. Kept, so the removal reaches the other devices instead of being undone
 *   by them.
 * @param raw The map this was read from, so fields this build does not know survive a write.
 */
data class SyncedFavorite(
    val key: String,
    val value: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val raw: Map<String, Any?>? = null,
)

/**
 * `users/{uid}.favorites`: the calculator's, equation balancer's, ideal gas calculator's and unit
 * converter's saved items, synced for PRO, PRO+ and Ultimate users.
 *
 * A port of the Windows app's `FavoritesDocument`, rule for rule. A disagreement between the two
 * would not show up as an error; it would show up as a favourite that comes back, or vanishes,
 * depending on which device synced last.
 *
 * **Removals are records, not absences.** Merging two lists by union would bring a removed
 * favourite straight back from the other device on the next sync. So a removal is kept as a
 * [SyncedFavorite.deleted] record with the time it happened, and per item the newer of the two sides
 * wins: removing beats an older save, and saving again beats an older removal. Removal records
 * expire after [TOMBSTONE_LIFETIME_MS].
 *
 * **Nothing already saved is lost.** Items this device held before sync existed have no record in
 * the cloud, so the merge simply adds them; the cloud's items are added here the same way.
 *
 * Pure - no Android imports - because this is a place where a bug deletes a user's data on their
 * other device.
 */
object FavoritesDocument {

    const val FAVORITES = "favorites"

    /** The most saved items kept per tool, newest first. */
    const val MAX_PER_KIND = 200

    /**
     * How long a removal is remembered. A device that stays offline for longer than this and still
     * holds the item will bring it back - the price of not keeping every removal forever.
     */
    const val TOMBSTONE_LIFETIME_MS = 180L * 24 * 60 * 60 * 1000

    private const val UPDATED_AT = "updatedAt"
    private const val DELETED = "deleted"

    private const val SEPARATOR = "\u001F"

    /**
     * A unit conversion's key: what identifies it, as both apps' converters decide "already saved" -
     * the category, the two units and the input value.
     *
     * Only ever compared on this platform, against keys this platform built - so the number's text
     * needs to be stable here, not identical to the PC's ("1.0" here, "1" there).
     */
    fun unitConversionKey(category: String, fromUnit: String, toUnit: String, inputValue: Double): String =
        listOf(category, fromUnit, toUnit, inputValue.toString()).joinToString(SEPARATOR)

    /** The parts of [unitConversionKey], or null for a key that is not one. */
    fun parseUnitConversionKey(key: String): UnitConversionKey? {
        val parts = key.split(SEPARATOR)
        if (parts.size != 4) return null
        val input = parts[3].toDoubleOrNull() ?: return null
        return UnitConversionKey(parts[0], parts[1], parts[2], input)
    }

    data class UnitConversionKey(val category: String, val fromUnit: String, val toUnit: String, val inputValue: Double)

    // ---- Reading -----------------------------------------------------------------------------

    /**
     * Read the `favorites` field of the document.
     *
     * A missing field, a missing list or an item of the wrong shape is empty rather than an error:
     * a document written before favourites synced is a normal thing to meet.
     */
    fun fromField(favorites: Any?): Map<FavoriteKind, List<SyncedFavorite>> {
        val map = favorites as? Map<*, *>
        return FavoriteKind.values().associateWith { kind ->
            (map?.get(kind.array) as? List<*>).orEmpty().mapNotNull {
                if (kind == FavoriteKind.UNIT_CONVERSION) readUnitConversion(it) else read(it, kind)
            }
        }
    }

    private fun read(value: Any?, kind: FavoriteKind): SyncedFavorite? {
        val map = value as? Map<*, *> ?: return null
        if (map.isEmpty()) return null

        @Suppress("UNCHECKED_CAST")
        val raw = map.filterKeys { it is String } as Map<String, Any?>

        return SyncedFavorite(
            key = raw[kind.keyField] as? String ?: "",
            value = raw[kind.valueField] as? String ?: "",
            updatedAt = (raw[UPDATED_AT] as? Number)?.toLong() ?: 0L,
            deleted = raw[DELETED] as? Boolean ?: false,
            raw = raw,
        )
    }

    /**
     * A unit conversion. One whose input is missing gets an empty key, so the merge carries it
     * untouched rather than dropping something another build wrote.
     */
    private fun readUnitConversion(value: Any?): SyncedFavorite? {
        val map = value as? Map<*, *> ?: return null
        if (map.isEmpty()) return null

        @Suppress("UNCHECKED_CAST")
        val raw = map.filterKeys { it is String } as Map<String, Any?>
        val input = (raw["inputValue"] as? Number)?.toDouble()
        val converted = (raw["convertedValue"] as? Number)?.toDouble()

        return SyncedFavorite(
            key = if (input == null) "" else unitConversionKey(
                raw["category"] as? String ?: "",
                raw["fromUnit"] as? String ?: "",
                raw["toUnit"] as? String ?: "",
                input,
            ),
            value = converted?.toString() ?: "",
            updatedAt = (raw[UPDATED_AT] as? Number)?.toLong() ?: 0L,
            deleted = raw[DELETED] as? Boolean ?: false,
            raw = raw,
        )
    }

    // ---- Writing -----------------------------------------------------------------------------

    /**
     * The value of the `favorites` field: the four lists, and nothing else.
     *
     * Written with `SetOptions.merge()`, which deep-merges this map into the document's, so another
     * key under `favorites` survives; each of the four lists is replaced whole.
     */
    fun toField(all: Map<FavoriteKind, List<SyncedFavorite>>): Map<String, Any?> =
        FavoriteKind.values().associate { kind ->
            kind.array to all[kind].orEmpty().map { item ->
                LinkedHashMap<String, Any?>(item.raw.orEmpty()).apply {
                    if (kind == FavoriteKind.UNIT_CONVERSION) {
                        // A keyless item came from the cloud as it is; its fields are already in raw.
                        parseUnitConversionKey(item.key)?.let { parts ->
                            put("category", parts.category)
                            put("fromUnit", parts.fromUnit)
                            put("toUnit", parts.toUnit)
                            put("inputValue", parts.inputValue)
                        }
                        item.value.toDoubleOrNull()?.let { put("convertedValue", it) }
                    } else {
                        put(kind.keyField, item.key)
                        put(kind.valueField, item.value)
                    }
                    put(UPDATED_AT, item.updatedAt)
                    put(DELETED, item.deleted)
                }
            }
        }

    // ---- Merging -----------------------------------------------------------------------------

    /**
     * Combine one tool's items from this device and the cloud.
     *
     * Matched by key. Per item the later change wins - a save or a removal - and on a tie this
     * device's, which keeps the cloud's raw map under it so a write sheds nothing the PC put there.
     * An item with no key cannot be matched, so the cloud's is carried as it is and this device's
     * copy of it is ignored (it can only be one the cloud handed over last time).
     *
     * Removals older than [TOMBSTONE_LIFETIME_MS] are dropped; saved items are kept newest first up
     * to [MAX_PER_KIND]. Merging the result again changes nothing.
     */
    fun merge(local: List<SyncedFavorite>, cloud: List<SyncedFavorite>, nowMillis: Long): List<SyncedFavorite> {
        val byKey = LinkedHashMap<String, SyncedFavorite>()
        val unmatched = mutableListOf<SyncedFavorite>()

        for (theirs in cloud) {
            if (theirs.key.isEmpty()) unmatched.add(theirs) else byKey[theirs.key] = theirs
        }

        for (mine in local) {
            if (mine.key.isEmpty()) continue
            val theirs = byKey[mine.key]
            byKey[mine.key] = when {
                theirs == null -> mine
                mine.updatedAt >= theirs.updatedAt -> mine.copy(raw = mine.raw ?: theirs.raw)
                else -> theirs
            }
        }

        val oldest = nowMillis - TOMBSTONE_LIFETIME_MS
        val all = byKey.values + unmatched
        val order = compareByDescending<SyncedFavorite> { it.updatedAt }.thenBy { it.key }

        val live = all.filter { !it.deleted }.sortedWith(order).take(MAX_PER_KIND)
        val removed = all.filter { it.deleted && it.updatedAt >= oldest }.sortedWith(order)

        return live + removed
    }

    /** Whether two lists hold different changes, compared on key, value, time and removal - not order. */
    fun differs(a: List<SyncedFavorite>, b: List<SyncedFavorite>): Boolean =
        a.map(::signature).sorted() != b.map(::signature).sorted()

    private fun signature(f: SyncedFavorite): String =
        "${f.key}\u001F${f.value}\u001F${f.updatedAt}" + if (f.deleted) "\u001Fx" else ""
}
