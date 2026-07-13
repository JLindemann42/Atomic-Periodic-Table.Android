package com.jlindemann.science.model

import com.jlindemann.science.R

data class SolubilityColumn(
    val cation: String,
    val values: List<String>
)

object SolubilityData {
    val anions = listOf(
        "F^-", "Cl^-", "Br^-", "I^-", "OH^-", "S^2-", "SO4^2-", "CO3^2-", "NO3^-", "PO4^3-", "CrO4^2-", "CH3CO2^-"
    )

    fun getColumns(): List<SolubilityColumn> {
        return listOf(
            SolubilityColumn("NH4^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S")),
            SolubilityColumn("Li^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "I", "S", "S")),
            SolubilityColumn("Na^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S")),
            SolubilityColumn("K^+", listOf("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S")),
            SolubilityColumn("Mg^2+", listOf("I", "S", "S", "S", "I", "---", "S", "I", "S", "I", "S", "S")),
            SolubilityColumn("Ca^2+", listOf("I", "S", "S", "S", "Ss", "---", "Ss", "I", "S", "I", "S", "S")),
            SolubilityColumn("Ba^2+", listOf("Ss", "S", "S", "S", "S", "---", "I", "I", "S", "I", "I", "S")),
            SolubilityColumn("Al^3+", listOf("S", "S", "S", "S", "I", "---", "S", "---", "S", "I", "---", "Ss")),
            SolubilityColumn("Fe^3+", listOf("Ss", "S", "S", "---", "I", "I", "S", "---", "S", "I", "I", "S")),
            SolubilityColumn("Cu^2+", listOf("S", "S", "S", "---", "I", "I", "S", "---", "S", "I", "I", "S")),
            SolubilityColumn("Ag^+", listOf("S", "I", "I", "I", "---", "I", "Ss", "I", "S", "I", "I", "S")),
            SolubilityColumn("Zn^2+", listOf("S", "S", "S", "S", "I", "I", "S", "I", "S", "I", "I", "S")),
            SolubilityColumn("Pb^2+", listOf("I", "S", "Ss", "I", "I", "I", "I", "I", "S", "I", "I", "S"))
        )
    }
}
