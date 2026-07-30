package com.jlindemann.science.ai.data

import android.content.res.AssetManager
import com.jlindemann.science.utils.ElementDataLoader
import org.json.JSONObject

/**
 * Reads the element tables out of `assets/elements_{lang}.json` via [ElementDataLoader], so the
 * ~1 MB parse is shared with the rest of the app rather than duplicated.
 *
 * This is the only class in the AI engine that touches `org.json`; it converts straight into
 * plain Kotlin maps so nothing downstream depends on the Android JSON stubs.
 */
class AssetElementSource(private val assets: AssetManager) : ElementSource {

    override fun availableLanguages(): List<String> =
        ElementDataLoader.getAvailableLanguages(assets)

    override fun load(language: String): ElementTable? {
        val json = ElementDataLoader.getAllElements(assets, language) ?: return null
        val rows = HashMap<String, ElementRow>(json.length() * 2)
        val elementKeys = json.keys()
        while (elementKeys.hasNext()) {
            val key = elementKeys.next()
            val obj = json.optJSONObject(key) ?: continue
            rows[key] = toRow(obj)
        }
        return ElementTable(rows)
    }

    private fun toRow(obj: JSONObject): ElementRow {
        val row = HashMap<String, Any?>(obj.length() * 2)
        val fields = obj.keys()
        while (fields.hasNext()) {
            val field = fields.next()
            row[field] = when (val raw = obj.opt(field)) {
                null, JSONObject.NULL -> null
                is JSONObject -> toStringMap(raw)
                is Int -> raw
                is Number -> raw.toString()
                is Boolean -> raw.toString()
                else -> raw.toString()
            }
        }
        return row
    }

    private fun toStringMap(obj: JSONObject): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k)
            if (v != null && v != JSONObject.NULL) map[k] = v.toString()
        }
        return map
    }
}
