package com.jlindemann.science.model

import com.jlindemann.science.R

object TablesModel {
    fun getList(tables: ArrayList<Tables>) {
        tables.add(Tables(R.string.table_ph.toString(), R.string.table_ph_text.toString(), 0, 0, "phActivity"))
        tables.add(Tables(R.string.table_electrochemical.toString(), R.string.table_electrochemical_text.toString(), 0, 0, "ElectrodeActivity"))
        tables.add(Tables(R.string.table_equations.toString(), R.string.table_equations_text.toString(), 0, 0, "EquationsActivity"))
        tables.add(Tables(R.string.table_ionization.toString(), R.string.table_ionization_text.toString(), 0, 0, "IonActivity"))
        tables.add(Tables(R.string.table_solubility.toString(), R.string.table_solubility_text.toString(), 0, 0, "SolubilityActivity"))
        tables.add(Tables(R.string.table_poisson.toString(), R.string.table_poisson_text.toString(), 1, 0, "PoissonActivity"))
        tables.add(Tables(R.string.table_nuclide.toString(), R.string.table_nuclide_text.toString(), 0, 1, "NuclideActivity"))
    }
}

