package com.jlindemann.science.model

import android.content.Context

object StatisticsModel {
    fun getList(context: Context, statistics: ArrayList<Statistics>) {
        val stat1 = Statistics(1, "element_opened", 0)
        stat1.loadProgress(context)
        statistics.add(stat1)

        val stat2 = Statistics(2, "calculations", 0)
        stat2.loadProgress(context)
        statistics.add(stat2)

        val stat3 = Statistics(3, "searches", 0)
        stat3.loadProgress(context)
        statistics.add(stat3)
    }
}