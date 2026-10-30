package com.jlindemann.science.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jlindemann.science.activities.tools.UnitConversionFavorite
import org.json.JSONObject

/**
 * The tools' saved favourites on this device, and what sync needs to know about them.
 *
 * The lists themselves stay where the tools have always kept them - a StringSet each for the
 * calculator, the equation balancer and the ideal gas calculator, a JSON list for the unit converter
 * - so an existing user's favourites are read from exactly where they are, and nothing has to
 * migrate. Beside them, in [SYNC_PREFS], each tool keeps two maps: when each item was saved, and
 * when each removed item was removed. That is what lets [FavoritesDocument.merge] tell "removed
 * here" from "not seen here yet".
 *
 * **Favourites saved before sync existed are kept and uploaded.** An item in a tool's list with no
 * saved time is given one the first time this store sees it, so the first sync adds it to the cloud
 * beside whatever the other device has there - a union, never a replacement.
 *
 * Every call runs on the main thread, as SharedPreferences edits and Firestore callbacks both do,
 * so a save and a sync can never interleave.
 */
object FavoritesStore {

    private const val SYNC_PREFS = "FavoritesSync"
    private const val SAVED = "saved"
    private const val REMOVED = "removed"

    /** Matches the number at the start of the calculator's "18.015 (g/mol)". */
    private val LEADING_NUMBER = Regex("""^\s*[-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?""")

    private const val GRAMS_PER_MOLE = " (g/mol)"

    /**
     * Where a tool keeps its list, as (key, value) pairs in its own format - each tool chose its
     * own, and they are kept exactly, so the tools read what they always read.
     */
    private interface Tool {
        val kind: FavoriteKind
        fun read(context: Context): List<Pair<String, String>>
        fun write(context: Context, entries: List<Pair<String, String>>)
    }

    /** A StringSet of "key<separator>value" entries. */
    private class SetTool(
        override val kind: FavoriteKind,
        private val prefs: String,
        private val key: String,
        private val separator: String,
    ) : Tool {
        override fun read(context: Context): List<Pair<String, String>> =
            (context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getStringSet(key, emptySet()) ?: emptySet())
                .map { it.substringBefore(separator) to it.substringAfter(separator, "") }

        override fun write(context: Context, entries: List<Pair<String, String>>) {
            context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
                .putStringSet(key, entries.map { (k, v) -> "$k$separator$v" }.toSet())
                .apply()
        }
    }

    /**
     * The unit converter's Gson list of [UnitConversionFavorite]. The key is
     * [FavoritesDocument.unitConversionKey]; the value the converted number as text.
     */
    private object UnitTool : Tool {
        private const val PREFS = "unit_converter"
        private const val KEY = "unit_favorites"
        private val type = object : TypeToken<List<UnitConversionFavorite>>() {}.type

        override val kind = FavoriteKind.UNIT_CONVERSION

        override fun read(context: Context): List<Pair<String, String>> {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
            val list: List<UnitConversionFavorite> = try {
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: com.google.gson.JsonParseException) {
                emptyList()
            }
            return list.map { keyOf(it) to it.convertedValue.toString() }
        }

        override fun write(context: Context, entries: List<Pair<String, String>>) {
            val list = entries.mapNotNull { (key, value) -> toFavorite(key, value) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, Gson().toJson(list))
                .apply()
        }

        fun keyOf(f: UnitConversionFavorite): String =
            FavoritesDocument.unitConversionKey(f.category, f.fromUnit, f.toUnit, f.inputValue)

        fun toFavorite(key: String, value: String): UnitConversionFavorite? {
            val parts = FavoritesDocument.parseUnitConversionKey(key) ?: return null
            val converted = value.toDoubleOrNull() ?: return null
            return UnitConversionFavorite(parts.category, parts.fromUnit, parts.toUnit, parts.inputValue, converted)
        }
    }

    private val tools: Map<FavoriteKind, Tool> = mapOf(
        FavoriteKind.COMPOUND to SetTool(FavoriteKind.COMPOUND, "CalculatorPrefs", "favorites", ":"),
        FavoriteKind.EQUATION to SetTool(FavoriteKind.EQUATION, "EquationBalancerPrefs", "balancer_favorites", "\u001F"),
        FavoriteKind.IDEAL_GAS to SetTool(FavoriteKind.IDEAL_GAS, "IdealGasCalculatorPrefs", "ideal_gas_favorites", ":"),
        FavoriteKind.UNIT_CONVERSION to UnitTool,
    )

    /** Told after a sync changed what is saved here, so an open tool can reload its list. */
    private val listeners = mutableSetOf<(FavoriteKind) -> Unit>()

    fun addListener(listener: (FavoriteKind) -> Unit) { listeners.add(listener) }

    fun removeListener(listener: (FavoriteKind) -> Unit) { listeners.remove(listener) }

    // ---- The tools' API ------------------------------------------------------------------------

    /** The tool's saved items as (key, value), oldest first. */
    fun entries(context: Context, kind: FavoriteKind): List<Pair<String, String>> {
        val tool = tools.getValue(kind)
        val saved = stampedTimes(context, tool)
        return tool.read(context)
            .sortedWith(compareBy<Pair<String, String>> { saved[it.first] ?: 0L }.thenBy { it.first })
    }

    /** Save one item, replacing any with the same key. */
    fun save(context: Context, kind: FavoriteKind, key: String, value: String) {
        val tool = tools.getValue(kind)
        val entries = tool.read(context).filter { it.first != key } + (key to value)
        tool.write(context, entries)

        val times = readTimes(context, kind)
        times.first[key] = System.currentTimeMillis()
        times.second.remove(key)
        writeTimes(context, kind, times)

        FavoritesSyncManager.requestSync(context)
    }

    /** Remove the item with this key, remembering that it was removed. */
    fun remove(context: Context, kind: FavoriteKind, key: String) {
        val tool = tools.getValue(kind)
        val before = tool.read(context)
        // Exactly this key. The calculator used to remove by prefix, so removing H2 took H2O with it.
        val after = before.filter { it.first != key }
        if (after.size == before.size) return
        tool.write(context, after)

        val times = readTimes(context, kind)
        times.first.remove(key)
        times.second[key] = System.currentTimeMillis()
        writeTimes(context, kind, times)

        FavoritesSyncManager.requestSync(context)
    }

    /** The unit converter's saved conversions, oldest first. */
    fun unitConversions(context: Context): List<UnitConversionFavorite> =
        entries(context, FavoriteKind.UNIT_CONVERSION).mapNotNull { (key, value) -> UnitTool.toFavorite(key, value) }

    fun saveUnitConversion(context: Context, favorite: UnitConversionFavorite) =
        save(context, FavoriteKind.UNIT_CONVERSION, UnitTool.keyOf(favorite), favorite.convertedValue.toString())

    fun removeUnitConversion(context: Context, favorite: UnitConversionFavorite) =
        remove(context, FavoriteKind.UNIT_CONVERSION, UnitTool.keyOf(favorite))

    // ---- The sync's API ------------------------------------------------------------------------

    /** This device's items and removals for [kind], in the shared form. */
    internal fun local(context: Context, kind: FavoriteKind): List<SyncedFavorite> {
        val tool = tools.getValue(kind)
        val saved = stampedTimes(context, tool)
        val removed = readTimes(context, kind).second

        val live = tool.read(context).map { (key, value) ->
            SyncedFavorite(key, toCloud(kind, value), saved[key] ?: 0L)
        }
        val gone = removed.map { (key, at) -> SyncedFavorite(key, "", at, deleted = true) }

        // A removal and a later save of the same key can both be recorded; the newer is what happened.
        return (live + gone)
            .groupBy { it.key }
            .map { (_, same) -> same.maxByOrNull { it.updatedAt }!! }
    }

    /** Replace this device's items and removals for [kind] with a merge result. */
    internal fun apply(context: Context, kind: FavoriteKind, merged: List<SyncedFavorite>) {
        val tool = tools.getValue(kind)
        val before = tool.read(context).toSet()

        val live = merged.filter { !it.deleted && it.key.isNotEmpty() }.sortedBy { it.updatedAt }
        val after = live.map { it.key to fromCloud(kind, it.value) }

        writeTimes(
            context, kind,
            live.associate { it.key to it.updatedAt }.toMutableMap() to
                merged.filter { it.deleted && it.key.isNotEmpty() }
                    .associate { it.key to it.updatedAt }.toMutableMap(),
        )

        if (after.toSet() != before) {
            tool.write(context, after)
            listeners.toList().forEach { it(kind) }
        }
    }

    // ---- Values --------------------------------------------------------------------------------

    /**
     * The shared form of a value. The calculator shows its mass as "18.015 (g/mol)"; the cloud
     * holds the number alone, which is what the PC writes too.
     */
    private fun toCloud(kind: FavoriteKind, value: String): String =
        if (kind == FavoriteKind.COMPOUND) LEADING_NUMBER.find(value)?.value?.trim() ?: value else value

    private fun fromCloud(kind: FavoriteKind, value: String): String =
        if (kind == FavoriteKind.COMPOUND && LEADING_NUMBER.matches(value)) value.trim() + GRAMS_PER_MOLE else value

    // ---- Storage -------------------------------------------------------------------------------

    /**
     * Each saved item's time, giving any item that has none - one saved before sync existed - the
     * current time, spaced a millisecond apart in list order so they keep it.
     */
    private fun stampedTimes(context: Context, tool: Tool): Map<String, Long> {
        val times = readTimes(context, tool.kind)
        val unstamped = tool.read(context).map { it.first }.filter { it !in times.first }
        if (unstamped.isEmpty()) return times.first

        val now = System.currentTimeMillis()
        unstamped.forEachIndexed { i, key -> times.first[key] = now - unstamped.size + i }
        writeTimes(context, tool.kind, times)
        return times.first
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

    private fun readTimes(context: Context, kind: FavoriteKind): Pair<MutableMap<String, Long>, MutableMap<String, Long>> {
        val json = try {
            JSONObject(prefs(context).getString(kind.array, null) ?: "{}")
        } catch (e: org.json.JSONException) {
            JSONObject()
        }
        return readMap(json.optJSONObject(SAVED)) to readMap(json.optJSONObject(REMOVED))
    }

    private fun readMap(json: JSONObject?): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        json?.keys()?.forEach { key -> map[key] = json.optLong(key) }
        return map
    }

    private fun writeTimes(
        context: Context,
        kind: FavoriteKind,
        times: Pair<Map<String, Long>, Map<String, Long>>,
    ) {
        val json = JSONObject()
            .put(SAVED, JSONObject(times.first as Map<*, *>))
            .put(REMOVED, JSONObject(times.second as Map<*, *>))
        prefs(context).edit().putString(kind.array, json.toString()).apply()
    }
}
