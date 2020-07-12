package com.jlindemann.science.model

object SeriesModel {
    fun getList(series: ArrayList<Series>) {
        series.add(Series("lithium", -3.04, "1+"))
        series.add(Series("potassium", -2.931, "1+"))
    }
}