package com.jlindemann.science.views

import android.view.View
import android.widget.TextView
import com.jlindemann.science.R
import com.jlindemann.science.ai.cards.NfpaLabels

/**
 * Fills in an already-inflated NFPA 704 diamond.
 *
 * Stateless, and every id is resolved against the passed-in root rather than an Activity — which is
 * what lets several diamonds exist at once, one per chat row. The binding logic previously lived
 * inside `InfoExtension`, a 1000-line abstract Activity, with the hazard wording as hardcoded
 * English literals in an app that ships twelve languages.
 *
 * The Pro gate deliberately sits *outside* this: whether to show a diamond is a product decision,
 * whereas filling one in is not.
 */
object NfpaDiamondBinder {

    /** The four rotated quadrants. */
    fun bindDiamond(root: View, labels: NfpaLabels) {
        root.findViewById<TextView>(R.id.fire_hazard_txt)?.text = labels.fire
        root.findViewById<TextView>(R.id.health_hazard_txt)?.text = labels.health
        root.findViewById<TextView>(R.id.reactivity_hazard_txt)?.text = labels.reactivity
        root.findViewById<TextView>(R.id.specific_hazard_txt)?.text = labels.special
    }

    /**
     * The quadrants plus the description rows the element screen shows beneath them.
     *
     * The rows are optional: the chat card includes only the diamond, so these lookups return null
     * there and are skipped.
     */
    fun bindWithDescriptions(root: View, labels: NfpaLabels) {
        bindDiamond(root, labels)
        root.findViewById<TextView>(R.id.fire_hazard_text)?.text = labels.fireText
        root.findViewById<TextView>(R.id.health_hazard_text)?.text = labels.healthText
        root.findViewById<TextView>(R.id.reactivity_hazard_text)?.text = labels.reactivityText
        root.findViewById<TextView>(R.id.specific_hazard_text)?.text = labels.specialText
    }
}
