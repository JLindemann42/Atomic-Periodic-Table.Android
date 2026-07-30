package com.jlindemann.science.ai.data

import java.io.File

/**
 * Loads the real shipped element assets into a JVM unit test.
 *
 * `org.json` is a throwing stub under plain JVM tests, and adding a JSON dependency just for
 * tests would be more machinery than this needs — the element files are plain nested objects of
 * strings, integers and nulls. This minimal reader covers exactly that, so the parsers can be
 * exercised against all 118 real elements rather than only against fixtures.
 */
object TestAssets {

    private val assetsDir: File by lazy {
        // Unit tests run with the module directory as the working directory.
        val candidates = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
            File("../app/src/main/assets")
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate assets from ${File(".").absolutePath}")
    }

    /** True when the real assets are reachable; lets tests skip rather than fail spuriously. */
    fun available(language: String = "en"): Boolean =
        runCatching { File(assetsDir, "elements_$language.json").isFile }.getOrDefault(false)

    /** The real element table for a language. */
    fun elementTable(language: String = "en"): ElementTable {
        val text = File(assetsDir, "elements_$language.json").readText(Charsets.UTF_8)
        @Suppress("UNCHECKED_CAST")
        val root = JsonReader(text).readValue() as Map<String, Any?>
        val rows = LinkedHashMap<String, ElementRow>(root.size * 2)
        for ((key, value) in root) {
            @Suppress("UNCHECKED_CAST")
            val row = value as? Map<String, Any?> ?: continue
            rows[key] = row
        }
        return ElementTable(rows)
    }

    fun availableLanguages(): List<String> =
        assetsDir.listFiles()
            ?.mapNotNull { f ->
                val name = f.name
                if (name.startsWith("elements_") && name.endsWith(".json")) {
                    name.removePrefix("elements_").removeSuffix(".json")
                } else null
            }?.sorted().orEmpty()

    /** Minimal recursive-descent JSON reader. Objects, arrays, strings, numbers, booleans, null. */
    private class JsonReader(private val src: String) {
        private var pos = 0

        fun readValue(): Any? {
            skipWhitespace()
            return when (val c = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> if (c == '-' || c.isDigit()) readNumber()
                else error("Unexpected character '$c' at $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (src[pos] == '}') { pos++; return map }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                require(src[pos] == ':') { "Expected ':' at $pos" }
                pos++
                map[key] = readValue()
                skipWhitespace()
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (src[pos] == ']') { pos++; return list }
            while (true) {
                list.add(readValue())
                skipWhitespace()
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun readString(): String {
            require(src[pos] == '"') { "Expected '\"' at $pos" }
            pos++
            val sb = StringBuilder()
            while (true) {
                when (val c = src[pos]) {
                    '"' -> { pos++; return sb.toString() }
                    '\\' -> {
                        pos++
                        when (val esc = src[pos]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('')
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                sb.append(src.substring(pos + 1, pos + 5).toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                        pos++
                    }
                    else -> { sb.append(c); pos++ }
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            val text = src.substring(start, pos)
            return text.toIntOrNull() ?: text.toDoubleOrNull() ?: text
        }

        private fun expect(literal: String) {
            require(src.startsWith(literal, pos)) { "Expected '$literal' at $pos" }
            pos += literal.length
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
