package com.jlindemann.science.sync

/**
 * The per-element notes blob.
 *
 * Format is `<element_code>text</element_code>` repeated, with no separator and no escaping. The
 * close tag is matched by backreference so a code containing regex metacharacters cannot break
 * parsing, and DOT_MATCHES_ALL is required because notes contain newlines.
 *
 * Entries are emitted **sorted by code** so the blob is stable: an unsorted rebuild would make the
 * string differ between devices that hold identical notes, and the merge rule compares lengths.
 */
object NotesCodec {

    private val ENTRY = Regex("""<([^>]+)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)

    /** Parse a blob into element code -> note content. Unparseable text is ignored. */
    fun parse(notes: String?): Map<String, String> {
        if (notes.isNullOrEmpty()) return emptyMap()

        val elementNotes = LinkedHashMap<String, String>()
        ENTRY.findAll(notes).forEach { match ->
            val code = match.groups[1]?.value ?: return@forEach
            elementNotes[code] = match.groups[2]?.value ?: ""
        }
        return elementNotes
    }

    /** Rebuild a blob, sorted by code. */
    fun format(elementNotes: Map<String, String>): String =
        elementNotes.entries
            .sortedBy { it.key }
            .joinToString("") { (code, content) -> "<$code>$content</$code>" }

    /**
     * Merge two blobs, per element.
     *
     * The longer note wins, on the assumption that more content means more user edits. An element
     * present on one side only is kept, never treated as a deletion - absence means "that device
     * has not heard of it", which is also true of a note written on a platform whose element table
     * is a version behind.
     */
    fun merge(local: String?, cloud: String?): String {
        if (cloud.isNullOrEmpty()) return local ?: ""
        if (local.isNullOrEmpty()) return cloud

        val localNotes = parse(local)
        val cloudNotes = parse(cloud)

        val merged = LinkedHashMap<String, String>()
        for (code in localNotes.keys + cloudNotes.keys) {
            val localNote = localNotes[code] ?: ""
            val cloudNote = cloudNotes[code] ?: ""
            merged[code] = if (localNote.length >= cloudNote.length) localNote else cloudNote
        }

        return format(merged)
    }
}
