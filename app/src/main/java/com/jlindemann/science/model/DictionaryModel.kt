package com.jlindemann.science.model

object DictionaryModel {
    fun getList(dictionary: ArrayList<Dictionary>) {
        dictionary.add(Dictionary("Density", "The density (more precisely, the volumetric mass density; also known as specific mass), of a substance is its mass per unit volume. The symbol most often used for density is ρ (the lower case Greek letter rho), although the Latin letter D can also be used. Mathematically, density is defined as mass divided by volume:[1]", "https://en.wikipedia.org/wiki/Density"))
        dictionary.add(Dictionary("Standard Atomic Weight", "The standard atomic weight (Ar, standard) of a chemical element is the weighted arithmetic mean of the relative atomic masses (Ar) of all isotopes of that element weighted by each isotope's abundance on Earth. The standard atomic weight of each chemical element is determined and published by the Commission on Isotopic Abundances and Atomic Weights (CIAAW) of the International Union of Pure and Applied Chemistry (IUPAC) based on natural, stable, terrestrial sources of the element. It is the most common and practical atomic weight used by scientists.", "https://en.wikipedia.org/wiki/Standard_atomic_weight"))
        dictionary.add(Dictionary("Atomic Radius", "The atomic radius of a chemical element is a measure of the size of its atoms, usually the mean or typical distance from the center of the nucleus to the boundary of the surrounding shells of electrons. Since the boundary is not a well-defined physical entity, there are various non-equivalent definitions of atomic radius. Three widely used definitions of atomic radius are: Van der Waals radius, ionic radius, and covalent radius.", "https://en.wikipedia.org/wiki/Atomic_radius"))
    }
}
//Template
//dictionary.add(Dictionary("headingText", "contentText", "wikiLink"))