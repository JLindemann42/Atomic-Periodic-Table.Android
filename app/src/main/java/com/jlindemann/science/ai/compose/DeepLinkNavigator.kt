package com.jlindemann.science.ai.compose

import android.app.Activity
import android.content.Intent
import com.jlindemann.science.activities.ElementInfoActivity
import com.jlindemann.science.activities.SolubilityActivity
import com.jlindemann.science.activities.tables.ConstantsActivity
import com.jlindemann.science.activities.tables.DictionaryActivity
import com.jlindemann.science.activities.tables.ElectrodeActivity
import com.jlindemann.science.activities.tables.EmissionActivity
import com.jlindemann.science.activities.tables.EquationsActivity
import com.jlindemann.science.activities.tables.GeologyActivity
import com.jlindemann.science.activities.tables.IonActivity
import com.jlindemann.science.activities.tables.NuclideActivity
import com.jlindemann.science.activities.tables.PoissonActivity
import com.jlindemann.science.activities.tables.phActivity
import com.jlindemann.science.activities.tools.CalculatorActivity
import com.jlindemann.science.activities.tools.ChemicalReactionsActivity
import com.jlindemann.science.activities.tools.FlashCardActivity
import com.jlindemann.science.activities.tools.IdealGasCalculatorActivity
import com.jlindemann.science.activities.tools.LearningGamesActivity
import com.jlindemann.science.activities.tools.UnitConversionActivity
import com.jlindemann.science.ai.data.DeepLinkTarget
import com.jlindemann.science.preferences.ElementSendAndLoad

/**
 * Takes the user to the screen an answer was drawn from.
 *
 * This is what turns a citation from an attribution into something actionable: the answer says
 * where a figure came from, and one tap opens that screen.
 */
object DeepLinkNavigator {

    /**
     * @param resend called for [DeepLinkTarget.RESEND_QUERY], so a suggested follow-up can be
     *   sent back through the chat rather than opening a screen
     */
    fun navigate(activity: Activity, action: ChatAction, resend: (String) -> Unit = {}) {
        if (action.target == DeepLinkTarget.RESEND_QUERY) {
            action.args["q"]?.let(resend)
            return
        }

        if (action.target == DeepLinkTarget.ELEMENT_INFO) {
            // ElementInfoActivity reads no intent extras; the selected element is passed through
            // ElementSendAndLoad, matching how the periodic table and the widgets open it.
            val key = action.args["key"] ?: return
            ElementSendAndLoad(activity).setValue(key)
            activity.startActivity(Intent(activity, ElementInfoActivity::class.java))
            return
        }

        val destination = activityFor(action.target) ?: return
        val intent = Intent(activity, destination)
        // Harmless if the destination ignores it; lets a table scroll to the cited row later.
        action.args["id"]?.let { intent.putExtra(EXTRA_FOCUS, it) }
        activity.startActivity(intent)
    }

    /** The extra a table can read to highlight the row a citation pointed at. */
    const val EXTRA_FOCUS = "ai_focus"

    private fun activityFor(target: DeepLinkTarget): Class<*>? = when (target) {
        DeepLinkTarget.NUCLIDE -> NuclideActivity::class.java
        DeepLinkTarget.EMISSION -> EmissionActivity::class.java
        DeepLinkTarget.CONSTANTS -> ConstantsActivity::class.java
        DeepLinkTarget.DICTIONARY -> DictionaryActivity::class.java
        DeepLinkTarget.EQUATIONS -> EquationsActivity::class.java
        DeepLinkTarget.POISSON -> PoissonActivity::class.java
        DeepLinkTarget.GEOLOGY -> GeologyActivity::class.java
        DeepLinkTarget.SOLUBILITY -> SolubilityActivity::class.java
        DeepLinkTarget.ION -> IonActivity::class.java
        DeepLinkTarget.ELECTRODE -> ElectrodeActivity::class.java
        DeepLinkTarget.PH -> phActivity::class.java
        DeepLinkTarget.MOLAR_MASS_CALC -> CalculatorActivity::class.java
        DeepLinkTarget.UNIT_CONVERTER -> UnitConversionActivity::class.java
        DeepLinkTarget.IDEAL_GAS -> IdealGasCalculatorActivity::class.java
        DeepLinkTarget.REACTION_BALANCER -> ChemicalReactionsActivity::class.java
        DeepLinkTarget.LEARNING_GAMES -> LearningGamesActivity::class.java
        DeepLinkTarget.FLASHCARDS -> FlashCardActivity::class.java
        DeepLinkTarget.ELEMENT_INFO, DeepLinkTarget.RESEND_QUERY -> null
    }
}
