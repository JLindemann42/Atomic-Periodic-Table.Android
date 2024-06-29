package com.jlindemann.science.model

object PoissonModel {
    fun getList(poisson: ArrayList<Poisson>) {

        //Rocks:
        poisson.add(Poisson("Andesite", 0.20, 0.35, "rock"))
        poisson.add(Poisson("Basalt", 0.10, 0.35, "rock"))
        poisson.add(Poisson("Claystone", 0.25, 0.40, "rock"))
        poisson.add(Poisson("Conglomerate", 0.10, 0.4, "rock"))
        poisson.add(Poisson("Diabase", 0.10, 0.28, "rock"))
        poisson.add(Poisson("Diorite", 0.20, 0.30, "rock"))
        poisson.add(Poisson("Dolerite", 0.15, 0.35, "rock"))
        poisson.add(Poisson("Dolomite", 0.10, 0.35, "rock"))
        poisson.add(Poisson("Gneiss", 0.10, 0.30, "rock"))
        poisson.add(Poisson("Basalt", 0.10, 0.33, "rock"))
        poisson.add(Poisson("Granodiorite", 0.15, 0.25, "rock"))
        poisson.add(Poisson("Greywacke", 0.08, 0.23, "rock"))
        poisson.add(Poisson("Linestone", 0.10, 0.33, "rock"))
        poisson.add(Poisson("Marble", 0.15, 0.30, "rock"))
        poisson.add(Poisson("Marl", 0.13, 0.33, "rock"))
        poisson.add(Poisson("Norite", 0.20, 0.25, "rock"))
        poisson.add(Poisson("Quartzite", 0.10, 0.33, "rock"))
        poisson.add(Poisson("Rock Salt", 0.05, 0.30, "rock"))
        poisson.add(Poisson("Sandstone", 0.05, 0.40, "rock"))
        poisson.add(Poisson("Shale", 0.05, 0.32, "rock"))
        poisson.add(Poisson("Silstone", 0.13, 0.35, "rock"))
        poisson.add(Poisson("Tuff", 0.10, 0.28, "rock"))

        //Soils:
        poisson.add(Poisson("Loose Sand", 0.20, 0.40, "soil"))
        poisson.add(Poisson("Medium dense Sand", 0.25, 0.40, "soil"))
        poisson.add(Poisson("Dense Sand", 0.30, 0.45, "soil"))
        poisson.add(Poisson("Silty Sand", 0.20, 0.40, "soil"))
        poisson.add(Poisson("Sand and Gravel", 0.15, 0.35, "soil"))
        poisson.add(Poisson("Saturated cohesive Soil", 0.50, 0.50, "soil"))

        //minerals:
        poisson.add(Poisson("α-Cristobalite (SiO2)", -0.164, -0.164, "mineral"))
        poisson.add(Poisson("Diamond (C)", 0.069, 0.069, "mineral"))
        poisson.add(Poisson("α-Quartz (SiO2)", 0.079, 0.079, "mineral"))
        poisson.add(Poisson("Periclase (MgO)", 0.182, 0.182, "mineral"))
        poisson.add(Poisson("Topaz (Al2(F,OH)2SiO4)", 0.221, 0.221, "mineral"))
        poisson.add(Poisson("Graphite (C)",  0.223,  0.223, "mineral"))
        poisson.add(Poisson("Sapphire (Al2O3)", 0.234, 0.234, "mineral"))
        poisson.add(Poisson("Magnesite (MgCO3)", 0.251, 0.251, "mineral"))
        poisson.add(Poisson("Halite (NaCl)", 0.253, 0.253, "mineral"))
        poisson.add(Poisson("Magnetite (Fe3O4)", 0.262, 0.262, "mineral"))
        poisson.add(Poisson("Galena (PbS)", 0.270, 0.270, "mineral"))
        poisson.add(Poisson("Anhydrite (CaSO4)",  0.273,  0.273, "mineral"))
        poisson.add(Poisson("Rutile (TiO2)", 0.278, 0.278, "mineral"))
        poisson.add(Poisson("Chromite (FeOCr2O3)", 0.280, 0.280, "mineral"))
        poisson.add(Poisson("Albite (NaAlSi3O8)", 0.285, 0.285, "mineral"))
        poisson.add(Poisson("Fluorite (CaF2)", 0.289, 0.289, "mineral"))
        poisson.add(Poisson("Dolomite (CaMg(CO3)2)",  0.292,  0.292, "mineral"))
        poisson.add(Poisson("Calcite (CaCO3)", 0.309, 0.309, "mineral"))
        poisson.add(Poisson("Sphalerite (ZnS)", 0.320, 0.320, "mineral"))
        poisson.add(Poisson("Uraninite (UO2)", 0.325, 0.325, "mineral"))
        poisson.add(Poisson("Gypsum (CaSO4 2H2O)", 0.336, 0.336, "mineral"))
        poisson.add(Poisson("Zincite (ZnO)", 0.353, 0.353, "mineral"))
        poisson.add(Poisson("Bunsenite (NiO)",  0.369,  0.369, "mineral"))
        poisson.add(Poisson("Celestite (SrSO4)", 0.379, 0.379, "mineral"))
    }
}