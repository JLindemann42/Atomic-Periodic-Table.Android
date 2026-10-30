package com.jlindemann.science.ai.core

import com.jlindemann.science.ai.data.FieldRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards against format placeholders reaching the screen.
 *
 * Two ways that happened, both user-visible and both silent:
 *
 *  1. A **field label that is really a sentence template.** All four NFPA fields pointed at
 *     `ai_safety_health` and its siblings — "• **Health:** %1$d/4 (%2$s)" — and a label is
 *     resolved with no arguments, so users were shown the raw template, stray slash and all.
 *  2. A **translation that dropped the conversion.** Seven Hindi strings were written with a bare
 *     `%1` instead of `%1$s`; Android renders those literally.
 *
 * Neither shows up in a routing test, because both produce a perfectly well-formed answer that
 * happens to have "%2" in it.
 */
class StringFormatTest {

    /** `%1`, `%2`… not followed by a conversion. Android prints these verbatim. */
    private val bareIndex = Regex("""%\d(?!\$)""")

    /** Any positional placeholder at all. */
    private val anyPlaceholder = Regex("""%\d\$""")

    @Test
    fun noFieldLabelIsAFormatTemplate() {
        assumeTrue(TestStrings.available)
        val strings = TestStrings("en")
        val offenders = FieldRegistry.ALL.flatMap { spec ->
            listOf(spec.labelRes, spec.sentenceLabel()).distinct().mapNotNull { res ->
                val text = runCatching { strings.get(res) }.getOrNull() ?: return@mapNotNull null
                if (text.startsWith("str:")) null
                else if (anyPlaceholder.containsMatchIn(text) || bareIndex.containsMatchIn(text)) {
                    "${spec.id} -> \"$text\""
                } else null
            }
        }
        assertTrue(
            "A field label is resolved without arguments, so it must not be a format template:\n  " +
                    offenders.joinToString("\n  "),
            offenders.isEmpty()
        )
    }

    @Test
    fun everyLocaleUsesWellFormedPlaceholders() {
        val resDir = listOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"))
            .firstOrNull { it.isDirectory }
        assumeTrue(resDir != null)

        val offenders = ArrayList<String>()
        resDir!!.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.sortedBy { it.name }
            ?.forEach { folder ->
                val xml = File(folder, "strings.xml").takeIf { it.isFile } ?: return@forEach
                // Line-based on purpose: it reports the offending string, not just a count, and the
                // file is one string per line throughout.
                xml.readLines().forEach { line ->
                    val name = NAME.find(line)?.groupValues?.get(1) ?: return@forEach
                    if (bareIndex.containsMatchIn(line)) {
                        offenders.add("${folder.name}/$name: ${line.trim()}")
                    }
                }
            }
        assertTrue(
            "A placeholder with no conversion is printed verbatim by Android — write %1\$s, not %1:" +
                    "\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty()
        )
    }

    private companion object {
        val NAME = Regex("""<string name="([^"]+)"""")
    }
}
