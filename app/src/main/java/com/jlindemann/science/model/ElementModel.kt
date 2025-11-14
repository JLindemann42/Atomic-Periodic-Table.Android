package com.jlindemann.science.model

import android.content.Context
import com.jlindemann.science.utils.ElementDataLoader

object ElementModel {
    /**
     * Get list of elements with localized names from JSON files
     * @param elements ArrayList to populate with elements
     * @param context Context needed to access assets and load localized element data
     */
    fun getList(elements: ArrayList<Element>, context: Context? = null) {
        // Define all element data: key, symbol, number, electronegativity, isotopes
        val elementData = listOf(
            Triple("hydrogen", "H", arrayOf(1, 2.20, 7)),
            Triple("helium", "He", arrayOf(2, 0.0, 8)),
            Triple("lithium", "Li", arrayOf(3, 0.98, 11)),
            Triple("beryllium", "Be", arrayOf(4, 1.57, 11)),
            Triple("boron", "B", arrayOf(5, 2.04, 15)),
            Triple("carbon", "C", arrayOf(6, 2.55, 15)),
            Triple("nitrogen", "N", arrayOf(7, 3.04, 16)),
            Triple("oxygen", "O", arrayOf(8, 3.44, 16)),
            Triple("fluorine", "F", arrayOf(9, 3.98, 18)),
            Triple("neon", "Ne", arrayOf(10, 0.0, 20)),
            Triple("sodium", "Na", arrayOf(11, 0.93, 20)),
            Triple("magnesium", "Mg", arrayOf(12, 1.31, 22)),
            Triple("aluminium", "Al", arrayOf(13, 1.61, 22)),
            Triple("silicon", "Si", arrayOf(14, 1.90, 23)),
            Triple("phosphorus", "P", arrayOf(15, 2.19, 23)),
            Triple("sulfur", "S", arrayOf(16, 2.58, 23)),
            Triple("chlorine", "Cl", arrayOf(17, 3.16, 25)),
            Triple("argon", "Ar", arrayOf(18, 0.00, 26)),
            Triple("potassium", "K", arrayOf(19, 0.82, 29)),
            Triple("calcium", "Ca", arrayOf(20, 1.00, 29)),
            Triple("scandium", "Sc", arrayOf(21, 1.36, 29)),
            Triple("titanium", "Ti", arrayOf(22, 1.54, 29)),
            Triple("vanadium", "V", arrayOf(23, 1.63, 29)),
            Triple("chromium", "Cr", arrayOf(24, 1.66, 30)),
            Triple("manganese", "Mn", arrayOf(25, 1.55, 30)),
            Triple("iron", "Fe", arrayOf(26, 1.83, 31)),
            Triple("cobalt", "Co", arrayOf(27, 1.88, 32)),
            Triple("nickel", "Ni", arrayOf(28, 1.91, 34)),
            Triple("copper", "Cu", arrayOf(29, 1.90, 33)),
            Triple("zinc", "Zn", arrayOf(30, 1.65, 33)),
            Triple("gallium", "Ga", arrayOf(31, 1.81, 33)),
            Triple("germanium", "Ge", arrayOf(32, 2.01, 33)),
            Triple("arsenic", "As", arrayOf(33, 2.18, 33)),
            Triple("selenium", "Se", arrayOf(34, 2.55, 33)),
            Triple("bromine", "Br", arrayOf(35, 2.96, 35)),
            Triple("krypton", "Kr", arrayOf(36, 3.00, 35)),
            Triple("rubidium", "Rb", arrayOf(37, 0.82, 34)),
            Triple("strontium", "Sr", arrayOf(38, 0.95, 35)),
            Triple("yttrium", "Y", arrayOf(39, 1.22, 35)),
            Triple("zirconium", "Zr", arrayOf(40, 1.33, 36)),
            Triple("niobium", "Nb", arrayOf(41, 1.6, 36)),
            Triple("molybdenum", "Mo", arrayOf(42, 2.16, 36)),
            Triple("technetium", "Tc", arrayOf(43, 1.9, 35)),
            Triple("ruthenium", "Ru", arrayOf(44, 2.2, 36)),
            Triple("rhodium", "Rh", arrayOf(45, 2.28, 34)),
            Triple("palladium", "Pd", arrayOf(46, 2.20, 35)),
            Triple("silver", "Ag", arrayOf(47, 1.93, 40)),
            Triple("cadmium", "Cd", arrayOf(48, 1.69, 37)),
            Triple("indium", "In", arrayOf(49, 1.78, 38)),
            Triple("tin", "Sn", arrayOf(50, 1.96, 33)),
            Triple("antimony", "Sb", arrayOf(51, 2.05, 39)),
            Triple("tellurium", "Te", arrayOf(52, 2.1, 39)),
            Triple("iodine", "I", arrayOf(53, 2.66, 37)),
            Triple("xenon", "Xe", arrayOf(54, 2.60, 37)),
            Triple("caesium", "Cs", arrayOf(55, 0.79, 39)),
            Triple("barium", "Ba", arrayOf(56, 0.89, 37)),
            Triple("lanthanum", "La", arrayOf(57, 1.1, 36)),
            Triple("cerium", "Ce", arrayOf(58, 1.12, 39)),
            Triple("praseodymium", "Pr", arrayOf(59, 1.13, 37)),
            Triple("neodymium", "Nd", arrayOf(60, 1.14, 41)),
            Triple("promethium", "Pm", arrayOf(61, 1.13, 39)),
            Triple("samarium", "Sm", arrayOf(62, 1.17, 39)),
            Triple("europium", "Eu", arrayOf(63, 1.2, 38)),
            Triple("gadolinium", "Gd", arrayOf(64, 1.2, 34)),
            Triple("terbium", "Tb", arrayOf(65, 1.1, 36)),
            Triple("dysprosium", "Dy", arrayOf(66, 1.22, 39)),
            Triple("holmium", "Ho", arrayOf(67, 1.23, 37)),
            Triple("erbium", "Er", arrayOf(68, 1.24, 35)),
            Triple("thulium", "Tm", arrayOf(69, 1.25, 36)),
            Triple("ytterbium", "Yb", arrayOf(70, 1.1, 40)),
            Triple("lutetium", "Lu", arrayOf(71, 1.27, 40)),
            Triple("hafnium", "Hf", arrayOf(72, 1.3, 40)),
            Triple("tantalum", "Ta", arrayOf(73, 1.5, 40)),
            Triple("tungsten", "W", arrayOf(74, 2.36, 40)),
            Triple("rhenium", "Re", arrayOf(75, 1.9, 40)),
            Triple("osmium", "Os", arrayOf(76, 2.2, 37)),
            Triple("iridium", "Ir", arrayOf(77, 2.20, 40)),
            Triple("platinum", "Pt", arrayOf(78, 2.28, 40)),
            Triple("gold", "Au", arrayOf(79, 2.54, 40)),
            Triple("mercury", "Hg", arrayOf(80, 2.00, 40)),
            Triple("thallium", "Tl", arrayOf(81, 1.62, 40)),
            Triple("lead", "Pb", arrayOf(82, 1.87, 40)),
            Triple("bismuth", "Bi", arrayOf(83, 2.02, 40)),
            Triple("polonium", "Po", arrayOf(84, 2.0, 40)),
            Triple("astatine", "At", arrayOf(85, 2.2, 39)),
            Triple("radon", "Rn", arrayOf(86, 2.2, 40)),
            Triple("francium", "Fr", arrayOf(87, 0.79, 38)),
            Triple("radium", "Ra", arrayOf(88, 0.9, 34)),
            Triple("actinium", "Ac", arrayOf(89, 1.1, 37)),
            Triple("thorium", "Th", arrayOf(90, 1.3, 34)),
            Triple("protactinium", "Pa", arrayOf(91, 1.5, 31)),
            Triple("uranium", "U", arrayOf(92, 1.338, 29)),
            Triple("neptunium", "Np", arrayOf(93, 1.36, 25)),
            Triple("plutonium", "Pu", arrayOf(94, 1.28, 17)),
            Triple("americium", "Am", arrayOf(95, 1.13, 17)),
            Triple("curium", "Cm", arrayOf(96, 1.28, 19)),
            Triple("berkelium", "Bk", arrayOf(97, 1.3, 21)),
            Triple("californium", "Cf", arrayOf(98, 1.3, 20)),
            Triple("einsteinium", "Es", arrayOf(99, 1.3, 18)),
            Triple("fermium", "Fm", arrayOf(100, 1.3, 20)),
            Triple("mendelevium", "Md", arrayOf(101, 1.3, 17)),
            Triple("nobelium", "No", arrayOf(102, 1.3, 14)),
            Triple("lawrencium", "Lr", arrayOf(103, 1.3, 14)),
            Triple("rutherfordium", "Rf", arrayOf(104, 0.0, 16)),
            Triple("dubnium", "Db", arrayOf(105, 0.0, 13)),
            Triple("seaborgium", "Sg", arrayOf(106, 0.0, 13)),
            Triple("bohrium", "Bh", arrayOf(107, 0.0, 0)),
            Triple("hassium", "Hs", arrayOf(108, 0.0, 0)),
            Triple("meitnerium", "Mt", arrayOf(109, 0.0, 0)),
            Triple("darmstadtium", "Ds", arrayOf(110, 0.0, 0)),
            Triple("roentgenium", "Rg", arrayOf(111, 0.0, 0)),
            Triple("copernicium", "Cn", arrayOf(112, 0.0, 0)),
            Triple("nihonium", "Nh", arrayOf(113, 0.0, 0)),
            Triple("flerovium", "Fl", arrayOf(114, 0.0, 0)),
            Triple("moscovium", "Mc", arrayOf(115, 0.0, 0)),
            Triple("livermorium", "Lv", arrayOf(116, 0.0, 0)),
            Triple("tennessine", "Ts", arrayOf(117, 0.0, 0)),
            Triple("oganesson", "Og", arrayOf(118, 0.0, 0))
        )
        
        // Load localized element names from JSON if context is provided
        if (context != null) {
            for ((elementKey, symbol, data) in elementData) {
                val jsonObject = ElementDataLoader.loadElementData(context, elementKey)
                val localizedName = jsonObject?.optString("element") ?: elementKey.replaceFirstChar { it.uppercase() }
                elements.add(Element(
                    element = localizedName,
                    short = symbol,
                    number = data[0] as Int,
                    electro = data[1] as Double,
                    isotopes = data[2] as Int,
                    elementKey = elementKey
                ))
            }
        } else {
            // Fallback to English names if no context provided
            for ((elementKey, symbol, data) in elementData) {
                elements.add(Element(
                    element = elementKey.replaceFirstChar { it.uppercase() },
                    short = symbol,
                    number = data[0] as Int,
                    electro = data[1] as Double,
                    isotopes = data[2] as Int,
                    elementKey = elementKey
                ))
            }
        }
    }
}
