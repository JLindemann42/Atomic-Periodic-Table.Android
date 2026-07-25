package com.jlindemann.science.ai.core

import com.jlindemann.science.R
import java.io.File

/**
 * A [StringProvider] backed by the real `values/strings.xml`.
 *
 * Reflection over the generated `R.string` class gives name-to-id, and the XML gives name-to-text,
 * so composed answers can be asserted against the strings users actually see — without needing a
 * Context or Robolectric.
 */
class TestStrings(override val language: String = "en") : StringProvider {

    private val byId: Map<Int, String> by lazy { load(language) }

    override fun get(id: Int, vararg args: Any): String {
        val template = byId[id] ?: return "str:$id"
        if (args.isEmpty()) return template
        return runCatching { String.format(template, *args) }.getOrDefault(template)
    }

    override fun array(id: Int): List<String> = emptyList()

    companion object {
        /** True when the real strings could be loaded; lets tests skip rather than fail. */
        val available: Boolean by lazy { load("en").isNotEmpty() }

        /**
         * Resource folder per language. These qualifiers are not derivable from the code alone —
         * the project uses region-qualified folders for several languages and a BCP-47 folder
         * for Filipino.
         */
        private val FOLDERS = mapOf(
            "en" to "values", "sv" to "values-sv-rSE", "de" to "values-de", "fr" to "values-fr",
            "es" to "values-es-rES", "it" to "values-it-rIT", "pt" to "values-pt-rBR",
            "hi" to "values-hi", "ur" to "values-ur-rPK", "zh" to "values-zh-rCN",
            "af" to "values-af", "fil" to "values-b+fil"
        )

        fun folderFor(language: String): String = FOLDERS[language] ?: "values"

        private fun load(language: String): Map<Int, String> {
            val folder = folderFor(language)
            val xml = listOf(
                File("src/main/res/$folder/strings.xml"),
                File("app/src/main/res/$folder/strings.xml")
            ).firstOrNull { it.isFile } ?: return emptyMap()

            val text = xml.readText(Charsets.UTF_8)
            val byName = HashMap<String, String>(1200)
            val pattern = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            for (match in pattern.findAll(text)) {
                byName[match.groupValues[1]] = unescape(match.groupValues[2])
            }

            val result = HashMap<Int, String>(byName.size)
            for (field in R.string::class.java.fields) {
                val value = byName[field.name] ?: continue
                runCatching { result[field.getInt(null)] = value }
            }
            return result
        }

        /** Android string resources escape apostrophes and use XML entities. */
        private fun unescape(raw: String): String = raw
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&#8217;", "’")
            .trim()
    }
}
