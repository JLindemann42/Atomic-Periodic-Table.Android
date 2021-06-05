package com.jlindemann.science.model

object IndicatorModel {
    fun getList(indicator: ArrayList<Indicator>) {
        indicator.add(Indicator("bromothymol_blue", "6.0", "yellow", "6.0-7.6", "green", "7.6", "blue"))
        indicator.add(Indicator("methyl_orange", "3.1", "red", "3.1-4.4", "orange", "4.4", "yellow"))
        indicator.add(Indicator("congo_red", "3.0", "blue", "3.0-5.0", "purple", "5.0", "red"))
        indicator.add(Indicator("phenolphthalein", "8.3", "colorless", "8.3-10", "colorless", "10", "pink"))
    }
}