package com.jlindemann.science.ai.compose

import com.jlindemann.science.R
import com.jlindemann.science.ai.core.Citation
import com.jlindemann.science.ai.core.StringProvider
import com.jlindemann.science.ai.data.DeepLinkTarget

/**
 * A tappable affordance attached to an answer — normally "open this element" or "open the table
 * this figure came from".
 *
 * The chat renderer supports no links at all (only `###` and `**bold**`), so a citation cannot be
 * a hyperlink. Carrying it as structured data and rendering it as a chip is what makes a grounded
 * answer navigable rather than just attributed.
 */
data class ChatAction(
    val label: String,
    val target: DeepLinkTarget,
    val args: Map<String, String> = emptyMap()
)

/** Turn a citation into the chip a user can tap. */
fun Citation.toAction(strings: StringProvider): ChatAction = ChatAction(
    label = runCatching { strings.get(R.string.ai_open_in_app, source) }
        .getOrNull()?.takeIf { !it.startsWith("str:") } ?: source,
    target = deepLink,
    args = args
)

/**
 * Serialises actions onto [com.jlindemann.science.model.ChatMessage].
 *
 * A string field rather than a typed list: it keeps `@Parcelize` working with no custom writers,
 * keeps the field optional so every existing positional constructor call still compiles, and lets
 * older stored messages decode to an empty list with no migration.
 *
 * The format is a deliberately small record encoding rather than JSON, so this stays plain Kotlin
 * and unit-testable — `org.json` is a throwing stub under JVM tests. Records are separated by
 * `` and fields by ``, control characters that cannot appear in a label or an
 * element key, so no escaping is needed.
 */
object ChatActionCodec {

    private const val RECORD = ''
    private const val FIELD = ''
    private const val PAIR = ''

    /** @return the encoded actions, or null when there is nothing to attach */
    fun encode(actions: List<ChatAction>): String? {
        if (actions.isEmpty()) return null
        return actions.joinToString(RECORD.toString()) { action ->
            val args = action.args.entries.joinToString(PAIR.toString()) { "${it.key}=${it.value}" }
            listOf(sanitise(action.label), action.target.name, args).joinToString(FIELD.toString())
        }
    }

    /** Never throws: malformed or absent data yields an empty list. */
    fun decode(encoded: String?): List<ChatAction> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size < 2) return@mapNotNull null
            val label = parts[0].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val target = runCatching { DeepLinkTarget.valueOf(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            val args = parts.getOrNull(2)
                ?.takeIf { it.isNotBlank() }
                ?.split(PAIR)
                ?.mapNotNull { pair ->
                    val at = pair.indexOf('=')
                    if (at <= 0) null else pair.substring(0, at) to pair.substring(at + 1)
                }
                ?.toMap()
                .orEmpty()
            ChatAction(label, target, args)
        }
    }

    /** Strip the delimiters so a label can never corrupt the encoding. */
    private fun sanitise(value: String): String =
        value.filterNot { it == RECORD || it == FIELD || it == PAIR }
}
