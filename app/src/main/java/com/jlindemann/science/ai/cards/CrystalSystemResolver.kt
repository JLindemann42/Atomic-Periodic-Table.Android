package com.jlindemann.science.ai.cards

import com.jlindemann.science.extensions.CrystalStructures

/**
 * Maps an authored `crystal_structure` string onto a wireframe in [CrystalStructures], or null.
 *
 * The null is the point. `CrystalStructureView` falls back to `"Cubic"` for anything it does not
 * recognise, which on the element screen is a cosmetic guess sitting next to a text label that
 * states the truth. In chat the card *is* the answer, so drawing a cubic cell for a rhombohedral
 * element would be a fabrication rather than a rough edge. The view keeps its fallback so its
 * existing behaviour is unchanged; the card layer uses the nullable result and simply shows no card.
 *
 * Extracted here rather than left in the view so the decision is one function, callable without an
 * Android context, and coverable by a test that enumerates every distinct value in the real assets.
 */
object CrystalSystemResolver {

    /** @return a key present in [CrystalStructures.data], or null when the string is unrecognised */
    fun resolve(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val key = when {
            // Hexagonal is matched loosely because the data spells it several ways
            // ("Hexagonal close-packed (hcp)", "Simple hexagonal").
            value.contains("Hexagonal", ignoreCase = true) -> "Hexagonal"
            value.equals("Body-centered cubic (bcc)", ignoreCase = true) -> "Body-centered cubic (bcc)"
            value.equals("Face-centered cubic (fcc)", ignoreCase = true) -> "Face-centered cubic (fcc)"
            value.equals("Rhombohedral", ignoreCase = true) -> "Rhombohedral"
            value.equals("Trigonal", ignoreCase = true) -> "Trigonal"
            value.equals("Tetragonal", ignoreCase = true) -> "Tetragonal"
            value.equals("Orthorhombic", ignoreCase = true) -> "Orthorhombic"
            value.equals("Monoclinic", ignoreCase = true) -> "Monoclinic"
            value.equals("Triclinic", ignoreCase = true) -> "Triclinic"
            value.equals("Cubic", ignoreCase = true) -> "Cubic"
            else -> return null
        }
        return key.takeIf { CrystalStructures.data.containsKey(it) }
    }
}
