package com.jlindemann.science.sync

/**
 * One achievement's progress, as it crosses the wire.
 *
 * [id] is written as a number, which is what Windows now expects; it reads a string id too, but
 * writing one back would leave the field's type flip-flopping with whichever device synced last.
 *
 * [title] is the English title. Windows carries it without reading it, and this app never reads the
 * cloud copy either - it exists so that neither platform strips a field the other put in the
 * document.
 */
data class AchievementProgress(
    val id: Int,
    val progress: Int,
    val maxProgress: Int,
    val title: String = "",
)

/**
 * Everything the two devices exchange through `users/{uid}`.
 *
 * Deliberately absent: **chats**. `ChatHistoryManager` owns that array and stores it as a list of
 * maps, while Windows carries it as a list of strings. Since this app writes with
 * `SetOptions.merge()`, leaving the field out of the snapshot is what keeps it untouched - reading
 * it into a typed snapshot and writing it back is the only way it could get corrupted.
 *
 * @param streakLastPlay ISO `yyyy-MM-dd`, or null.
 * @param notes the raw blob; see [NotesCodec].
 */
data class ProgressSnapshot(
    val xp: Long = 0L,
    val level: Int = 0,
    val streak: Int = 0,
    val streakLastPlay: String? = null,
    val achievements: List<AchievementProgress> = emptyList(),
    val statistics: Map<Int, Long> = emptyMap(),
    val notes: String? = null,
    val windowsTier: String? = null,
    val androidTier: String? = null,
    val ultimate: Boolean = false,
) {
    companion object {
        val EMPTY = ProgressSnapshot()
    }
}
