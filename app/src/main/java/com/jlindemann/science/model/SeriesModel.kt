package com.jlindemann.science.model

import android.content.Context
import com.jlindemann.science.utils.ElementDataLoader

object SeriesModel {
    /**
     * Get list of electrochemical series with localized element names
     * @param series ArrayList to populate with series data
     * @param context Context needed to access assets and load localized element data (optional)
     */
    fun getList(series: ArrayList<Series>, context: Context? = null) {
        // Define series data: elementKey, voltage, charge, symbol
        val seriesData = listOf(
            arrayOf("lithium", -3.04, "+", "Li"),
            arrayOf("potassium", -2.931, "+", "K"),
            arrayOf("barium", -2.912, "2+", "Ba"),
            arrayOf("calcium", -2.868, "2+", "Ca"),
            arrayOf("sodium", -2.71, "+", "Na"),
            arrayOf("magnesium", -2.372, "2+", "Mg"),
            arrayOf("aluminium", -1.662, "3+", "Al"),
            arrayOf("zinc", -0.7618, "2+", "Zn"),
            arrayOf("iron", -0.44, "2+", "Fe"),
            arrayOf("cobalt", -0.28, "2+", "Co"),
            arrayOf("nickel", -0.25, "2+", "Ni"),
            arrayOf("tin", -0.13, "2+", "Sn"),
            arrayOf("lead", -0.126, "2+", "Pb"),
            arrayOf("copper", -0.159, "2+", "Cu"),
            arrayOf("silver", +0.7996, "+", "Ag"),
            arrayOf("mercury", +0.85, "2+", "Hg"),
            arrayOf("platinum", +1.188, "2+", "Pt"),
            arrayOf("gold", +1.52, "3+", "Au")
        )
        
        // Load localized element names from JSON if context is provided
        if (context != null) {
            for (data in seriesData) {
                val elementKey = data[0] as String
                val jsonObject = ElementDataLoader.loadElementData(context, elementKey)
                val localizedName = jsonObject?.optString("element") ?: elementKey.replaceFirstChar { it.uppercase() }
                series.add(Series(
                    name = localizedName,
                    voltage = data[1] as Double,
                    charge = data[2] as String,
                    short = data[3] as String,
                    nameKey = elementKey
                ))
            }
        } else {
            // Fallback to English names if no context provided
            for (data in seriesData) {
                val elementKey = data[0] as String
                series.add(Series(
                    name = elementKey.replaceFirstChar { it.uppercase() },
                    voltage = data[1] as Double,
                    charge = data[2] as String,
                    short = data[3] as String,
                    nameKey = elementKey
                ))
            }
        }
    }
}