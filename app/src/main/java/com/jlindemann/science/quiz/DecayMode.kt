package com.jlindemann.science.quiz

import androidx.annotation.StringRes
import com.jlindemann.science.R

/**
 * The decay modes the quiz asks about, canonicalised from the raw `decay_type_N` strings.
 *
 * The data spells the same mode several ways — `α` / `alpha` / `a`, and `EC` / `ε` / `e- capture`
 * / `e-capture` — so the raw values cannot be offered as answer options directly: the same mode
 * would appear twice under two spellings, and one of them would be marked wrong.
 */
enum class DecayMode(@StringRes val labelRes: Int) {
    ALPHA(R.string.decay_alpha),
    BETA_MINUS(R.string.decay_beta_minus),
    BETA_PLUS(R.string.decay_beta_plus),
    ELECTRON_CAPTURE(R.string.decay_electron_capture),
    PROTON_EMISSION(R.string.decay_proton_emission),
    NEUTRON_EMISSION(R.string.decay_neutron_emission),
    SPONTANEOUS_FISSION(R.string.decay_spontaneous_fission);

    companion object {

        /**
         * β+ emission and electron capture compete for the same nuclides and the source records
         * only one of them per isotope. Offering both in one question would mark a physically
         * defensible answer wrong, so they are never shown together.
         */
        private val CONFUSABLE: Set<Set<DecayMode>> = setOf(setOf(BETA_PLUS, ELECTRON_CAPTURE))

        fun confusable(a: DecayMode, b: DecayMode): Boolean = setOf(a, b) in CONFUSABLE

        /**
         * Null for stable isotopes and for the multi-particle variants (`2p`, `3p`, `2n`, `2B-`,
         * `2EC`). Folding those into the single-particle mode would be wrong physics, and at
         * roughly 30 of 3131 records they are too rare to deserve their own answer options.
         */
        fun of(raw: String?): DecayMode? = when (raw?.trim()?.lowercase()) {
            "α", "alpha", "a" -> ALPHA
            "b-", "β-" -> BETA_MINUS
            "b+", "β+" -> BETA_PLUS
            "ec", "ε", "e- capture", "e-capture" -> ELECTRON_CAPTURE
            "p" -> PROTON_EMISSION
            "n" -> NEUTRON_EMISSION
            "sf" -> SPONTANEOUS_FISSION
            else -> null
        }
    }
}
