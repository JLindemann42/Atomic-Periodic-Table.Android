package com.jlindemann.science.model

/**
 * One cation's row of the solubility table: a value per anion, positionally aligned with
 * [SolubilityData.anions].
 */
data class SolubilityColumn(
    val cation: String,
    val values: List<String>
)

/**
 * Which salts dissolve in water at room temperature, as a textbook chart: `S` soluble, `Ss` slightly
 * soluble, `I` insoluble, and `---` for a pair that does not form a stable compound in water (it
 * hydrolyses, or one ion reduces the other).
 *
 * This is the one table both apps show. Android and Windows used to have different ones: Windows had
 * an H⁺ row and sulfite and silicate columns, Android a Zn²⁺ row and chromate and acetate columns
 * (Windows porting notes, D10). Roadmap 0.8 merged them here - Android's 156 cells unchanged, plus
 * the H⁺ row and the two columns, 40 new cells - and the Windows page now renders this table rather
 * than its own, which had sodium hydroxide insoluble and every sulfate soluble (D58).
 *
 * The Windows copy in `Atomic.Core/Models/SolubilityData.cs` is checked against
 * `shared/golden/solubility.tsv`, which `SolubilityGoldenTest` writes from this file.
 *
 * The H⁺ row describes the acid in water. Hydrogen with hydroxide is water itself, so it is `---`;
 * carbonic acid and hydrogen sulfide are `Ss` because CO₂ and H₂S are only sparingly soluble gases;
 * silicic acid is insoluble.
 */
object SolubilityData {
    val anions = listOf(
        "F^-", "Cl^-", "Br^-", "I^-", "OH^-", "S^2-", "SO4^2-", "CO3^2-", "NO3^-", "PO4^3-", "CrO4^2-", "CH3CO2^-",
        "SO3^2-", "SiO3^2-"
    )

    fun getColumns(): List<SolubilityColumn> {
        return listOf(
            SolubilityColumn("H^+", listOf("S", "S", "S", "S", "---", "Ss", "S", "Ss", "S", "S", "S", "S", "S", "I")),
            SolubilityColumn("NH4^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "---")),
            SolubilityColumn("Li^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "I", "S", "S", "S", "Ss")),
            SolubilityColumn("Na^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S")),
            SolubilityColumn("K^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S")),
            SolubilityColumn("Mg^2+", listOf("I", "S", "S", "S", "I", "---", "S", "I", "S", "I", "S", "S", "Ss", "I")),
            SolubilityColumn("Ca^2+", listOf("I", "S", "S", "S", "Ss", "---", "Ss", "I", "S", "I", "S", "S", "I", "I")),
            SolubilityColumn("Ba^2+", listOf("Ss", "S", "S", "S", "S", "---", "I", "I", "S", "I", "I", "S", "I", "I")),
            SolubilityColumn("Al^3+", listOf("S", "S", "S", "S", "I", "---", "S", "---", "S", "I", "---", "Ss", "---", "I")),
            SolubilityColumn("Fe^3+", listOf("Ss", "S", "S", "---", "I", "I", "S", "---", "S", "I", "I", "S", "---", "I")),
            SolubilityColumn("Cu^2+", listOf("S", "S", "S", "---", "I", "I", "S", "---", "S", "I", "I", "S", "---", "I")),
            SolubilityColumn("Ag^+", listOf("S", "I", "I", "I", "---", "I", "Ss", "I", "S", "I", "I", "S", "I", "I")),
            SolubilityColumn("Zn^2+", listOf("S", "S", "S", "S", "I", "I", "S", "I", "S", "I", "I", "S", "I", "I")),
            SolubilityColumn("Pb^2+", listOf("I", "S", "Ss", "I", "I", "I", "I", "I", "S", "I", "I", "S", "I", "I"))
        )
    }
}
