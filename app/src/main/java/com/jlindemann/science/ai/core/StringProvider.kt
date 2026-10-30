package com.jlindemann.science.ai.core

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

/**
 * Localization seam for the AI engine.
 *
 * The planner, executors and composer all need localized text, but taking a `Context` would make
 * them untestable on the JVM. They take a StringProvider instead, so tests can supply
 * [FakeStrings] and assert on structure rather than prose.
 */
interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun array(@ArrayRes id: Int): List<String>

    /** The language these strings are being resolved in, e.g. `"sv"`. */
    val language: String
}

/** Resolves against a real (already locale-wrapped) Context. */
class AndroidStrings(
    private val context: Context,
    override val language: String
) : StringProvider {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    override fun array(id: Int): List<String> =
        runCatching { context.resources.getStringArray(id).toList() }.getOrDefault(emptyList())
}

/**
 * Test double. Unknown ids resolve to `"str:<id>"` so assertions can still tell strings apart
 * without the test needing to know real resource values.
 */
class FakeStrings(
    private val strings: Map<Int, String> = emptyMap(),
    private val arrays: Map<Int, List<String>> = emptyMap(),
    override val language: String = "en"
) : StringProvider {
    override fun get(id: Int, vararg args: Any): String {
        val template = strings[id] ?: return "str:$id"
        return if (args.isEmpty()) template else runCatching { String.format(template, *args) }
            .getOrDefault(template)
    }

    override fun array(id: Int): List<String> = arrays[id].orEmpty()
}
