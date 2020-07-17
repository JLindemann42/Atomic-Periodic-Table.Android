package com.jlindemann.science.model

object SeriesModel {
    fun getList(series: ArrayList<Series>) {
        series.add(Series("lithium", -3.04, "+", "Li"))
        series.add(Series("potassium", -2.931, "+", "K"))
        series.add(Series("barium", -2.912, "2+", "Ba"))
        series.add(Series("calcium", -2.868, "2+", "Ca"))
        series.add(Series("sodium", -2.71, "+", "Na"))
        series.add(Series("magnesium", -2.372, "2+", "Mg"))
        series.add(Series("aluminium", -1.662, "3+", "Al"))
        series.add(Series("zinc", -0.7618, "2+", "Zn"))
        series.add(Series("iron", -0.44, "2+", "Fe"))
        series.add(Series("cobalt", -0.28, "2+", "Co"))
        series.add(Series("nickel", -0.25, "2+", "Ni"))
        series.add(Series("tin", -0.13, "2+", "Sn"))
        series.add(Series("lead", -0.126, "2+", "Pb"))
        series.add(Series("copper", -0.159, "2+", "Cu"))
        series.add(Series("silver", +0.7996, "+", "Ag"))
        series.add(Series("mercury", +0.85, "2+", "Hg"))
        series.add(Series("platinum", +1.188, "2+", "Pt"))
        series.add(Series("gold", +1.52, "3+", "Au"))
    }
}