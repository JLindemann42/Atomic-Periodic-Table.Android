package com.jlindemann.science.model

object DictionaryModel {
    fun getList(dictionary: ArrayList<Dictionary>) {
        dictionary.add(Dictionary("Density", "The density (more precisely, the volumetric mass density; also known as specific mass), of a substance is its mass per unit volume. The symbol most often used for density is ρ (the lower case Greek letter rho), although the Latin letter D can also be used. Mathematically, density is defined as mass divided by volume", "https://en.wikipedia.org/wiki/Density"))
        dictionary.add(Dictionary("Standard Atomic Weight", "The standard atomic weight (Ar, standard) of a chemical element is the weighted arithmetic mean of the relative atomic masses (Ar) of all isotopes of that element weighted by each isotope's abundance on Earth. The standard atomic weight of each chemical element is determined and published by the Commission on Isotopic Abundances and Atomic Weights (CIAAW) of the International Union of Pure and Applied Chemistry (IUPAC) based on natural, stable, terrestrial sources of the element. It is the most common and practical atomic weight used by scientists.", "https://en.wikipedia.org/wiki/Standard_atomic_weight"))
        dictionary.add(Dictionary("Atomic Radius", "The atomic radius of a chemical element is a measure of the size of its atoms, usually the mean or typical distance from the center of the nucleus to the boundary of the surrounding shells of electrons. Since the boundary is not a well-defined physical entity, there are various non-equivalent definitions of atomic radius. Three widely used definitions of atomic radius are: Van der Waals radius, ionic radius, and covalent radius.", "https://en.wikipedia.org/wiki/Atomic_radius"))
        dictionary.add(Dictionary("Electronegativity", "Electronegativity, symbol χ, is a concept that describes the tendency of an atom to attract a shared pair of electrons (or electron density) towards itself. An atom's electronegativity is affected by both its atomic number and the distance at which its valence electrons reside from the charged nucleus. The higher the associated electronegativity number, the more an atom or a substituent group attracts electrons towards itself.", "https://en.wikipedia.org/wiki/Electronegativity"))
        dictionary.add(Dictionary("Ionization energy", "In physics and chemistry, ionization energy (American English spelling) or ionisation energy (British English spelling), denoted Ei, is the minimum amount of energy required to remove the most loosely bound electron, the valence electron, of an isolated neutral gaseous atom or molecule.", "https://en.wikipedia.org/wiki/Ionization_energy"))
        dictionary.add(Dictionary("Electron configuration", "In atomic physics and quantum chemistry, the electron configuration is the distribution of electrons of an atom or molecule (or other physical structure) in atomic or molecular orbitals", "https://en.wikipedia.org/wiki/Electron_configuration"))
        dictionary.add(Dictionary("Enthalpy of fusion", "The enthalpy of fusion of a substance, also known as (latent) heat of fusion is the change in its enthalpy resulting from providing energy, typically heat, to a specific quantity of the substance to change its state from a solid to a liquid, at constant pressure.", "https://en.wikipedia.org/wiki/Enthalpy_of_fusion"))
        dictionary.add(Dictionary("Specific heat capacity", "The specific heat capacity of a substance is the heat capacity of a sample of the substance divided by the mass of the sample. Informally, it is the amount of energy that must be added, in the form of heat, to one unit of mass of the substance in order to cause an increase of one unit in its temperature.", "https://en.wikipedia.org/wiki/Specific_heat_capacity"))
        dictionary.add(Dictionary("Enthalpy of vaporization", "The enthalpy of vaporization, (symbol ∆Hvap) also known as the (latent) heat of vaporization or heat of evaporation, is the amount of energy (enthalpy) that must be added to a liquid substance, to transform a quantity of that substance into a gas. The enthalpy of vaporization is a function of the pressure at which that transformation takes place.", "https://en.wikipedia.org/wiki/Enthalpy_of_vaporization"))
        dictionary.add(Dictionary("Solubility chart", "A solubility chart is a chart with a list of ions and how, when mixed with other ions, they can become precipitates or remain aqueous.", "https://en.wikipedia.org/wiki/Solubility_chart"))

    }
}
//Template
//dictionary.add(Dictionary("headingText", "contentText", "wikiLink"))