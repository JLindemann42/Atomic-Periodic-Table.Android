package com.jlindemann.science.quiz

import com.jlindemann.science.R
import com.jlindemann.science.ai.data.ChemistryMath
import java.util.Locale

/**
 * Formulas for the molar-mass game.
 *
 * The app ships no compound dataset — every non-element table in `model/` is elements, minerals,
 * constants or prose — so this list is authored here. Chemical formulas are language-neutral, so
 * unlike those tables it costs nothing to localize.
 */
object Compounds {

    val FORMULAS: List<String> = listOf(
        // Water, acids and bases
        "H2O", "H2O2", "HCl", "HBr", "HF", "HNO3", "HNO2", "H2SO4", "H2SO3", "H3PO4",
        "H2CO3", "H2S", "NaOH", "KOH", "LiOH", "Ca(OH)2", "Mg(OH)2", "Ba(OH)2", "NH3", "Al(OH)3",
        // Halides and simple salts
        "NaCl", "KCl", "LiCl", "CaCl2", "MgCl2", "AlCl3", "FeCl2", "FeCl3", "CuCl2", "ZnCl2",
        "NaF", "CaF2", "KI", "NaBr", "AgCl", "NH4Cl",
        // Sulfates, carbonates, nitrates, phosphates
        "CuSO4", "ZnSO4", "MgSO4", "FeSO4", "Na2SO4", "K2SO4", "CaSO4", "BaSO4",
        "Al2(SO4)3", "Fe2(SO4)3",
        // (NH4)2SO4 is omitted: ChemistryMath.strictParse rejects a formula that opens with a group.
        "CaCO3", "Na2CO3", "K2CO3", "MgCO3", "NaHCO3", "Li2CO3",
        "NaNO3", "KNO3", "AgNO3", "Ca(NO3)2", "Cu(NO3)2", "NH4NO3",
        "Ca3(PO4)2", "Na3PO4", "K3PO4",
        // Oxidisers and other notable salts
        "KMnO4", "K2Cr2O7", "K2CrO4", "NaClO", "NaClO3", "KClO3", "Na2S2O3",
        // Oxides
        "CO", "CO2", "SO2", "SO3", "NO", "NO2", "N2O", "Fe2O3", "Fe3O4", "Al2O3",
        "CaO", "MgO", "ZnO", "CuO", "TiO2", "SiO2", "MnO2", "PbO2", "P4O10", "Na2O",
        // Organics
        "CH4", "C2H6", "C3H8", "C4H10", "C2H4", "C2H2", "C6H6", "C7H8",
        "CH3OH", "C2H5OH", "C3H7OH", "CH3COOH", "C6H12O6", "C12H22O11", "CO(NH2)2",
        // Miscellaneous
        "SiC", "PH3", "N2H4", "SF6", "CCl4", "CHCl3"
    )
}

/** "What is the molar mass of Fe2(SO4)3?" */
class MolarMassGenerator(
    override val categoryKey: String,
    override val baseXp: Int
) : QuestionGenerator {

    override fun generate(ctx: GeneratorContext, count: Int): List<QuizQuestion> {
        val masses = Compounds.FORMULAS.mapNotNull { formula ->
            ChemistryMath.parseFormula(formula) { symbol ->
                ctx.store.bySymbol(symbol)?.quantity("atomic_mass")?.value
            }?.let { formula to it.molarMass }
        }
        if (masses.size < 4) return emptyList()

        val questions = ArrayList<QuizQuestion>(count)
        val used = HashSet<String>()
        var attempts = count * 8

        while (questions.size < count && attempts-- > 0) {
            val (formula, mass) = masses.random(ctx.random)
            if (!used.add(formula)) continue
            val correct = format(ctx, mass)

            // Distractors are other real compounds' molar masses in the same neighbourhood, so a
            // wrong answer is never obviously wrong by magnitude alone.
            val wrongs = masses
                .filter { it.first != formula && it.second in (mass * 0.4)..(mass * 2.5) }
                .map { format(ctx, it.second) }
                .filter { it != correct }
                .distinct()
                .shuffled(ctx.random)
                .take(3)
            if (wrongs.size < 3) continue

            questions.add(
                QuizQuestion(
                    question = ctx.string(R.string.question_molar_mass, formula),
                    correctAnswer = correct,
                    alternatives = (wrongs + correct).shuffled(ctx.random),
                    baseXp = baseXp
                )
            )
        }
        return questions
    }

    private fun format(ctx: GeneratorContext, mass: Double): String =
        ctx.string(R.string.molar_mass_value, String.format(Locale.getDefault(), "%.2f", mass))
}
