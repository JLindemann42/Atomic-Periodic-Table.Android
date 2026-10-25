package com.jlindemann.science.ai.retrieval

/**
 * "Yes, go on" in the twelve shipped languages, outside the legacy router.
 *
 * `AIAgentManager.isAffirmative` holds the same list, but nothing platform-free can reach it, and
 * the entity resolver needs it to know which words are never a misspelled element name. Ported
 * back from the Windows `Atomic.Core.Text.FuzzyMatch`, which is where the orchestrator replacing
 * `AIAgentManager` will look for it too.
 */
object FuzzyMatch {

    /**
     * Whether a reply means "yes, go on".
     *
     * Matched on word boundaries through [TextMatching.containsToken], because a plain containment
     * check reads the "ja" inside "japan" and the "ha" inside "hafnium".
     */
    fun isAffirmative(query: String): Boolean {
        if (query.isBlank()) return false
        val lower = query.trim().lowercase()
        val normalized = TextMatching.normalizeForLookup(query)
        return AFFIRMATIVES.any { TextMatching.containsToken(lower, normalized, it) }
    }

    /**
     * Folded, like every other vocabulary compared against normalised text — "sí" is written "si",
     * "gärna" is "garna". The router's list keeps the accents and compares them against a
     * lowercased-but-unfolded query, so those entries only match when the user types the accent.
     */
    private val AFFIRMATIVES: Set<String> = setOf(
        // English
        "yes", "sure", "ok", "okay", "yep", "yeah", "absolutely", "please", "do it", "more",
        "next", "tell me more", "something else", "anything else", "other", "another",
        // Swedish
        "ja", "visst", "okej", "garna", "absolut", "snalla", "gor det", "mer", "nasta",
        "beratta mer", "annat", "nagot annat", "nat annat", "vidare",
        // German
        "jawohl", "sicher", "gerne", "bitte", "mehr", "erzahl mir mehr", "erzahlen sie mir mehr",
        // French
        "oui", "bien sur", "d'accord", "s'il vous plait", "plus", "dis-m'en plus", "dites-m'en plus",
        // Spanish
        "si", "claro", "por supuesto", "por favor", "mas", "cuentame mas", "cuenteme mas",
        // Portuguese
        "sim", "com certeza", "mais", "conta-me mais", "me conte mais",
        // Hindi / Urdu
        "हाँ", "ज़रूर", "ठीक है", "ji", "ha", "zaroor", "thik hai", "aur", "zhada",
        "mujhe aur batao", "mujhe aur batayein", "mazeed", "mazeed batayein",
        // Chinese
        "是", "是的", "当然", "好的", "shi", "shide", "dangran", "haode", "geng duo", "jixu",
        "更多", "继续", "告诉我更多", "多跟我说说",
        // Filipino
        "oo", "sige", "oo naman", "walang anuman", "pa", "dagdag", "magkwento ka pa", "sabihin mo pa",
        // Italian
        "ancora", "di piu", "altro", "continua", "dimmi di piu", "mi dica di piu",
        // Afrikaans
        "vertel my meer", "sekerlik", "asseblief"
    )
}
