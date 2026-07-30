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
class TestStrings(
    override val language: String = "en",
    /**
     * Whether a key missing from this language falls back to English, which is what Android's
     * resource resolution actually does.
     *
     * Off by default, and that default is load-bearing in two opposite directions:
     *  - **Strict (false)** returns the `str:<id>` sentinel, which makes a missing translation
     *    visible. `StringCoverageTest` needs exactly this.
     *  - **Fallback (true)** reproduces production, where a key absent from `values-sv-rSE` is
     *    served from `values/` and lands in the middle of an otherwise Swedish sentence. Anything
     *    asserting *routing* in another language needs this, or every Swedish row fails on the
     *    sentinel before its real expectation is ever evaluated.
     */
    private val fallBackToEnglish: Boolean = false
) : StringProvider {

    private val byId: Map<Int, String> by lazy { load(language) }
    private val fallback: Map<Int, String> by lazy {
        if (fallBackToEnglish && language != "en") load("en") else emptyMap()
    }

    override fun get(id: Int, vararg args: Any): String {
        val template = byId[id] ?: fallback[id] ?: return "str:$id"
        if (args.isEmpty()) return template
        return runCatching { String.format(template, *args) }.getOrDefault(template)
    }

    /** True when this language has its own value for the key, rather than borrowing English. */
    fun hasOwn(id: Int): Boolean = byId.containsKey(id)

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

        /**
         * The folders Android consults for a language, most specific first.
         *
         * A region folder resolves upward to its base-language folder before reaching English, so
         * `values-es-rES` then `values-es`. Reading only the region folder made this harness
         * disagree with the device: Spanish and Urdu deliberately hold their shared strings in
         * `values-es` / `values-ur` so unregioned variants (es-CO, plain ur) resolve at all, and a
         * key promoted there looked "missing" here while working fine in the app.
         */
        private fun foldersFor(language: String): List<String> {
            val folder = folderFor(language)
            val base = folder.substringBefore("-r").takeIf { it != folder }
            return listOfNotNull(folder, base)
        }

        private fun load(language: String): Map<Int, String> {
            val byName = HashMap<String, String>(1200)
            val pattern = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

            // Least specific first, so a region override wins over the base language.
            for (folder in foldersFor(language).reversed()) {
                val xml = listOf(
                    File("src/main/res/$folder/strings.xml"),
                    File("app/src/main/res/$folder/strings.xml")
                ).firstOrNull { it.isFile } ?: continue
                for (match in pattern.findAll(xml.readText(Charsets.UTF_8))) {
                    byName[match.groupValues[1]] = unescape(match.groupValues[2])
                }
            }
            if (byName.isEmpty()) return emptyMap()

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
