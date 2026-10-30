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
        // Define series data: elementKey, voltage, charge, symbol.
        //
        // The 33 couples the Windows app has shipped since its port: the 18 this table used to hold
        // plus fifteen more including both halogens (Windows porting notes, D10, D56). Most negative
        // first, strictly, and the same order as Windows, so both apps list the series - and key its
        // rows for the assistant - the same way. Values are the CRC Handbook's; copper, thorium,
        // praseodymium, erbium and lanthanum were corrected in roadmap 0.8.
        val seriesData = listOf(
            arrayOf("lithium", -3.04, "+", "Li"),
            arrayOf("caesium", -3.026, "+", "Cs"),
            arrayOf("rubidium", -2.98, "+", "Rb"),
            arrayOf("potassium", -2.931, "+", "K"),
            arrayOf("barium", -2.912, "2+", "Ba"),
            arrayOf("francium", -2.9, "+", "Fr"),
            arrayOf("strontium", -2.89, "2+", "Sr"),
            arrayOf("calcium", -2.868, "2+", "Ca"),
            arrayOf("sodium", -2.71, "+", "Na"),
            arrayOf("lanthanum", -2.379, "3+", "La"),
            arrayOf("magnesium", -2.372, "2+", "Mg"),
            arrayOf("praseodymium", -2.353, "3+", "Pr"),
            arrayOf("erbium", -2.331, "3+", "Er"),
            arrayOf("thorium", -1.899, "4+", "Th"),
            arrayOf("aluminium", -1.662, "3+", "Al"),
            arrayOf("manganese", -1.18, "2+", "Mn"),
            arrayOf("zinc", -0.7618, "2+", "Zn"),
            arrayOf("chromium", -0.74, "3+", "Cr"),
            arrayOf("iron", -0.44, "2+", "Fe"),
            arrayOf("cadmium", -0.403, "2+", "Cd"),
            arrayOf("cobalt", -0.28, "2+", "Co"),
            arrayOf("nickel", -0.25, "2+", "Ni"),
            arrayOf("tin", -0.13, "2+", "Sn"),
            arrayOf("lead", -0.126, "2+", "Pb"),
            arrayOf("antimony", +0.204, "3+", "Sb"),
            arrayOf("bismuth", +0.317, "3+", "Bi"),
            arrayOf("copper", +0.3419, "2+", "Cu"),
            arrayOf("silver", +0.7996, "+", "Ag"),
            arrayOf("mercury", +0.85, "2+", "Hg"),
            arrayOf("platinum", +1.188, "2+", "Pt"),
            arrayOf("chlorine", +1.36, "-", "Cl"),
            arrayOf("gold", +1.52, "3+", "Au"),
            arrayOf("fluorine", +2.87, "-", "F")
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