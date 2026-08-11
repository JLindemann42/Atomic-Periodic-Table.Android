package com.jlindemann.science.ai.cards

/** The visual an answer can carry alongside its text. */
enum class ChatCardKind {
    ELECTRON_SHELL, CRYSTAL_STRUCTURE, EMISSION_SPECTRUM, POISSON_BAND,
    NFPA_DIAMOND, IONIZATION_SERIES, ISOTOPE_DECAY, ABUNDANCE
}

data class ChatCard(
    val kind: ChatCardKind,
    val elementKey: String,
    val title: String,
    val args: Map<String, String> = emptyMap()
)

object ChatCardCodec {

    private const val FIELD = ''
    private const val PAIR = ''

    fun encode(card: ChatCard?): String? {
        if (card == null) return null
        val args = card.args.entries.joinToString(PAIR.toString()) { "${it.key}=${it.value}" }
        return listOf(card.kind.name, sanitise(card.elementKey), sanitise(card.title), args)
            .joinToString(FIELD.toString())
    }

    fun decode(encoded: String?): ChatCard? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.split(FIELD)
        if (parts.size < 3) return null
        val kind = runCatching { ChatCardKind.valueOf(parts[0]) }.getOrNull() ?: return null
        val key = parts[1].takeIf { it.isNotBlank() } ?: return null
        val args = parts.getOrNull(3)
            ?.takeIf { it.isNotBlank() }
            ?.split(PAIR)
            ?.mapNotNull { pair ->
                val at = pair.indexOf('=')
                if (at <= 0) null else pair.substring(0, at) to pair.substring(at + 1)
            }
            ?.toMap()
            .orEmpty()
        return ChatCard(kind, key, parts[2], args)
    }

    /**
     * The card kind alone, without decoding the rest.
     *
     * `getItemViewType` is called for every visible row on every layout pass, so it should not pay
     * for a full decode just to choose a view type.
     */
    fun kindOf(encoded: String?): ChatCardKind? {
        if (encoded.isNullOrBlank()) return null
        val end = encoded.indexOf(FIELD)
        val name = if (end < 0) encoded else encoded.substring(0, end)
        return runCatching { ChatCardKind.valueOf(name) }.getOrNull()
    }

    /** Strip the delimiters so a value can never corrupt the encoding. */
    private fun sanitise(value: String): String = value.filterNot { it == FIELD || it == PAIR }
}
