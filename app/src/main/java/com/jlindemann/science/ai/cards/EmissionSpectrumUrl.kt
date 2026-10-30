package com.jlindemann.science.ai.cards

/**
 * Where an element's emission-spectrum image lives.
 *
 * The app holds no spectral data — no wavelengths, no intensities — only a pre-rendered image per
 * element on the author's server. That matters for what the agent is allowed to say: it can show
 * the spectrum, but it cannot name a line, because it does not have one.
 *
 * The URL was built by hand in three places (`InfoExtension`, `ElementInfoActivity`,
 * `EmissionActivity`). One copy, so a server move is a one-line change and the string is testable.
 */
object EmissionSpectrumUrl {

    private const val BASE = "https://www.jlindemann.se/atomic/emission_lines/"
    private const val EXTENSION = ".gif"

    /** @param symbol the element symbol as authored, e.g. `Fe` */
    fun of(symbol: String): String = BASE + symbol.trim() + EXTENSION
}
