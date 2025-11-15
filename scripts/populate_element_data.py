#!/usr/bin/env python3
"""
Populate element JSON data with IUPAC-standard values.
Fills in "---" placeholders with scientifically accurate data from authoritative sources.
"""

import json
import os
import sys

# IUPAC-standard data for all 118 elements
# Data sources: IUPAC, NIST, WebElements, CRC Handbook

# Electrical properties data
ELECTRICAL_TYPE = {
    # Conductors (metals)
    "hydrogen": "Conductor", "lithium": "Conductor", "beryllium": "Conductor",
    "sodium": "Conductor", "magnesium": "Conductor", "aluminium": "Conductor",
    "potassium": "Conductor", "calcium": "Conductor", "scandium": "Conductor",
    "titanium": "Conductor", "vanadium": "Conductor", "chromium": "Conductor",
    "manganese": "Conductor", "iron": "Conductor", "cobalt": "Conductor",
    "nickel": "Conductor", "copper": "Conductor", "zinc": "Conductor",
    "gallium": "Conductor", "rubidium": "Conductor", "strontium": "Conductor",
    "yttrium": "Conductor", "zirconium": "Conductor", "niobium": "Conductor",
    "molybdenum": "Conductor", "technetium": "Conductor", "ruthenium": "Conductor",
    "rhodium": "Conductor", "palladium": "Conductor", "silver": "Conductor",
    "cadmium": "Conductor", "indium": "Conductor", "tin": "Conductor",
    "caesium": "Conductor", "barium": "Conductor", "lanthanum": "Conductor",
    "cerium": "Conductor", "praseodymium": "Conductor", "neodymium": "Conductor",
    "promethium": "Conductor", "samarium": "Conductor", "europium": "Conductor",
    "gadolinium": "Conductor", "terbium": "Conductor", "dysprosium": "Conductor",
    "holmium": "Conductor", "erbium": "Conductor", "thulium": "Conductor",
    "ytterbium": "Conductor", "lutetium": "Conductor", "hafnium": "Conductor",
    "tantalum": "Conductor", "tungsten": "Conductor", "rhenium": "Conductor",
    "osmium": "Conductor", "iridium": "Conductor", "platinum": "Conductor",
    "gold": "Conductor", "mercury": "Conductor", "thallium": "Conductor",
    "lead": "Conductor", "bismuth": "Conductor", "polonium": "Conductor",
    "francium": "Conductor", "radium": "Conductor", "actinium": "Conductor",
    "thorium": "Conductor", "protactinium": "Conductor", "uranium": "Conductor",
    "neptunium": "Conductor", "plutonium": "Conductor", "americium": "Conductor",
    "curium": "Conductor", "berkelium": "Conductor", "californium": "Conductor",
    "einsteinium": "Conductor", "fermium": "Conductor", "mendelevium": "Conductor",
    "nobelium": "Conductor", "lawrencium": "Conductor", "rutherfordium": "Conductor",
    "dubnium": "Conductor", "seaborgium": "Conductor", "bohrium": "Conductor",
    "hassium": "Conductor", "meitnerium": "Conductor", "darmstadtium": "Conductor",
    "roentgenium": "Conductor", "copernicium": "Conductor", "nihonium": "Conductor",
    "flerovium": "Conductor", "moscovium": "Conductor", "livermorium": "Conductor",
    
    # Semiconductors
    "boron": "Semiconductor", "silicon": "Semiconductor", "germanium": "Semiconductor",
    "arsenic": "Semiconductor", "selenium": "Semiconductor", "antimony": "Semiconductor",
    "tellurium": "Semiconductor",
    
    # Insulators (non-metals and noble gases)
    "helium": "Insulator", "carbon": "Insulator", "nitrogen": "Insulator",
    "oxygen": "Insulator", "fluorine": "Insulator", "neon": "Insulator",
    "phosphorus": "Insulator", "sulfur": "Insulator", "chlorine": "Insulator",
    "argon": "Insulator", "bromine": "Insulator", "krypton": "Insulator",
    "iodine": "Insulator", "xenon": "Insulator", "radon": "Insulator",
    "astatine": "Insulator", "tennessine": "Insulator", "oganesson": "Insulator",
}

# Resistivity data (at 20°C) in µΩ·cm
RESISTIVITY = {
    "silver": "1.59", "copper": "1.68", "gold": "2.44", "aluminium": "2.65",
    "calcium": "3.36", "beryllium": "3.6", "magnesium": "4.39", "rhodium": "4.51",
    "sodium": "4.77", "iridium": "5.3", "tungsten": "5.6", "molybdenum": "5.7",
    "zinc": "5.9", "cobalt": "6.24", "cadmium": "7.3", "nickel": "6.99",
    "ruthenium": "7.1", "lithium": "9.28", "iron": "9.71", "platinum": "10.6",
    "tin": "11.5", "chromium": "12.7", "vanadium": "19.7", "palladium": "10.8",
    "thallium": "18", "niobium": "15.2", "tantalum": "13.1", "osmium": "8.12",
    "rhenium": "17.2", "lead": "20.6", "titanium": "42", "antimony": "39",
    "bismuth": "106.8", "manganese": "144", "mercury": "96.1", "potassium": "7.0",
    "rubidium": "12.5", "caesium": "20", "strontium": "23", "barium": "34",
    "europium": "90", "ytterbium": "25", "scandium": "56.2", "yttrium": "59.6",
    "zirconium": "42.1", "hafnium": "33.1", "gallium": "13.6", "indium": "8.37",
    "technetium": "20", "lanthanum": "61.5", "cerium": "82.8", "praseodymium": "70",
    "neodymium": "64.3", "promethium": "75", "samarium": "94", "gadolinium": "131",
    "terbium": "115", "dysprosium": "92.6", "holmium": "81.4", "erbium": "86",
    "thulium": "67.6", "lutetium": "58.2",
}

# Resistivity multiplier (power of 10)
RESISTIVITY_MULT = {k: "−8" for k in RESISTIVITY}

# Covalent radius data in pm (picometers)
COVALENT_RADIUS = {
    "hydrogen": "31", "helium": "28", "lithium": "128", "beryllium": "96",
    "boron": "84", "carbon": "73", "nitrogen": "71", "oxygen": "66",
    "fluorine": "57", "neon": "58", "sodium": "166", "magnesium": "141",
    "aluminium": "121", "silicon": "111", "phosphorus": "107", "sulfur": "105",
    "chlorine": "102", "argon": "106", "potassium": "203", "calcium": "176",
    "scandium": "170", "titanium": "160", "vanadium": "153", "chromium": "139",
    "manganese": "139", "iron": "132", "cobalt": "126", "nickel": "124",
    "copper": "132", "zinc": "122", "gallium": "122", "germanium": "120",
    "arsenic": "119", "selenium": "120", "bromine": "120", "krypton": "116",
    "rubidium": "220", "strontium": "195", "yttrium": "190", "zirconium": "175",
    "niobium": "164", "molybdenum": "154", "technetium": "147", "ruthenium": "146",
    "rhodium": "142", "palladium": "139", "silver": "145", "cadmium": "144",
    "indium": "142", "tin": "139", "antimony": "139", "tellurium": "138",
    "iodine": "139", "xenon": "140", "caesium": "244", "barium": "215",
    "lanthanum": "207", "cerium": "204", "praseodymium": "203", "neodymium": "201",
    "promethium": "199", "samarium": "198", "europium": "198", "gadolinium": "196",
    "terbium": "194", "dysprosium": "192", "holmium": "192", "erbium": "189",
    "thulium": "190", "ytterbium": "187", "lutetium": "187", "hafnium": "175",
    "tantalum": "170", "tungsten": "162", "rhenium": "151", "osmium": "144",
    "iridium": "141", "platinum": "136", "gold": "136", "mercury": "132",
    "thallium": "145", "lead": "146", "bismuth": "148", "polonium": "140",
    "astatine": "150", "radon": "150", "francium": "260", "radium": "221",
    "actinium": "215", "thorium": "206", "protactinium": "200", "uranium": "196",
    "neptunium": "190", "plutonium": "187",
}

# Van der Waals radius data in pm (picometers)
VAN_DER_WAALS_RADIUS = {
    "hydrogen": "120", "helium": "140", "carbon": "170", "nitrogen": "155",
    "oxygen": "152", "fluorine": "147", "neon": "154", "phosphorus": "180",
    "sulfur": "180", "chlorine": "175", "argon": "188", "arsenic": "185",
    "selenium": "190", "bromine": "185", "krypton": "202", "tellurium": "206",
    "iodine": "198", "xenon": "216", "radon": "220",
}

# Thermal conductivity data in W/(m·K)
THERMAL_CONDUCTIVITY = {
    "diamond": "2200", "silver": "429", "copper": "401", "gold": "318",
    "aluminium": "237", "beryllium": "200", "magnesium": "156", "sodium": "142",
    "tungsten": "173", "zinc": "116", "nickel": "91", "iron": "80.4",
    "platinum": "71.6", "tin": "66.8", "lead": "35.3", "silicon": "148",
    "germanium": "60.2", "lithium": "85", "calcium": "201", "potassium": "102.4",
    "titanium": "21.9", "vanadium": "30.7", "chromium": "93.7", "manganese": "7.82",
    "cobalt": "100", "rhodium": "150", "palladium": "71.8", "cadmium": "96.8",
    "indium": "81.6", "antimony": "24.3", "bismuth": "7.87", "molybdenum": "138",
    "tantalum": "57.5", "rhenium": "48.0", "osmium": "87.6", "iridium": "147",
    "mercury": "8.34", "thallium": "46.1", "polonium": "20", "thorium": "54",
    "uranium": "27.6", "gallium": "40.6", "rubidium": "58.2", "strontium": "35.3",
    "yttrium": "17.2", "zirconium": "22.7", "niobium": "53.7", "technetium": "50.6",
    "ruthenium": "117", "scandium": "15.8", "europium": "13.9", "gadolinium": "10.6",
    "terbium": "11.1", "dysprosium": "10.7", "holmium": "16.2", "erbium": "14.5",
    "thulium": "16.9", "ytterbium": "38.5", "lutetium": "16.4", "hafnium": "23.0",
    "neptunium": "6.3", "plutonium": "6.74", "americium": "10",
}

# Electron affinity data in kJ/mol
ELECTRON_AFFINITY = {
    "hydrogen": "72.8", "helium": "0", "lithium": "59.6", "beryllium": "0",
    "boron": "26.7", "carbon": "121.9", "nitrogen": "7", "oxygen": "141.0",
    "fluorine": "328.0", "neon": "0", "sodium": "52.8", "magnesium": "0",
    "aluminium": "42.5", "silicon": "133.6", "phosphorus": "72.0", "sulfur": "200.4",
    "chlorine": "349.0", "argon": "0", "potassium": "48.4", "calcium": "2.37",
    "scandium": "18.1", "titanium": "7.6", "vanadium": "50.6", "chromium": "64.3",
    "manganese": "0", "iron": "15.7", "cobalt": "63.7", "nickel": "112",
    "copper": "118.4", "zinc": "0", "gallium": "28.9", "germanium": "119",
    "arsenic": "78", "selenium": "195.0", "bromine": "324.6", "krypton": "0",
    "rubidium": "46.9", "strontium": "5.03", "yttrium": "29.6", "zirconium": "41.1",
    "niobium": "86.1", "molybdenum": "71.9", "technetium": "53", "ruthenium": "101.3",
    "rhodium": "109.7", "palladium": "53.7", "silver": "125.6", "cadmium": "0",
    "indium": "28.9", "tin": "107.3", "antimony": "103.2", "tellurium": "190.2",
    "iodine": "295.2", "xenon": "0", "caesium": "45.5", "barium": "13.95",
    "platinum": "205.3", "gold": "222.8", "mercury": "0", "thallium": "19.2",
    "lead": "35.1", "bismuth": "91.2", "polonium": "183.3", "astatine": "270.1",
    "radon": "0",
}

# Allen electronegativity scale
ELECTRONEGATIVITY_ALLEN = {
    "hydrogen": "2.300", "helium": "4.160", "lithium": "0.912", "beryllium": "1.576",
    "boron": "2.051", "carbon": "2.544", "nitrogen": "3.066", "oxygen": "3.610",
    "fluorine": "4.193", "neon": "4.787", "sodium": "0.869", "magnesium": "1.293",
    "aluminium": "1.613", "silicon": "1.916", "phosphorus": "2.253", "sulfur": "2.589",
    "chlorine": "2.869", "argon": "3.242", "potassium": "0.734", "calcium": "1.034",
    "scandium": "1.19", "titanium": "1.38", "vanadium": "1.53", "chromium": "1.65",
    "manganese": "1.75", "iron": "1.80", "cobalt": "1.84", "nickel": "1.88",
    "copper": "1.85", "zinc": "1.588", "gallium": "1.756", "germanium": "1.994",
    "arsenic": "2.211", "selenium": "2.424", "bromine": "2.685", "krypton": "2.966",
    "rubidium": "0.706", "strontium": "0.963", "yttrium": "1.11", "zirconium": "1.32",
    "niobium": "1.41", "molybdenum": "1.47", "technetium": "1.51", "ruthenium": "1.54",
    "rhodium": "1.56", "palladium": "1.58", "silver": "1.87", "cadmium": "1.521",
    "indium": "1.656", "tin": "1.824", "antimony": "1.984", "tellurium": "2.158",
    "iodine": "2.359", "xenon": "2.582", "caesium": "0.659", "barium": "0.881",
    "lanthanum": "1.09", "cerium": "1.08", "praseodymium": "1.07", "neodymium": "1.07",
    "promethium": "1.07", "samarium": "1.07", "europium": "1.06", "gadolinium": "1.11",
    "terbium": "1.10", "dysprosium": "1.10", "holmium": "1.10", "erbium": "1.11",
    "thulium": "1.11", "ytterbium": "1.06", "lutetium": "1.14", "hafnium": "1.23",
    "tantalum": "1.33", "tungsten": "1.40", "rhenium": "1.46", "osmium": "1.52",
    "iridium": "1.55", "platinum": "1.44", "gold": "1.41", "mercury": "1.44",
    "thallium": "1.44", "lead": "1.55", "bismuth": "1.67", "polonium": "1.76",
    "astatine": "1.90", "radon": "2.06", "francium": "0.67", "radium": "0.89",
}

# Work function data in eV
WORK_FUNCTION = {
    "lithium": "2.93", "beryllium": "4.98", "sodium": "2.75", "magnesium": "3.66",
    "aluminium": "4.08", "potassium": "2.30", "calcium": "2.87", "scandium": "3.5",
    "titanium": "4.33", "vanadium": "4.3", "chromium": "4.5", "manganese": "4.1",
    "iron": "4.5", "cobalt": "5.0", "nickel": "5.15", "copper": "4.65",
    "zinc": "3.63", "gallium": "4.32", "rubidium": "2.16", "strontium": "2.59",
    "yttrium": "3.1", "zirconium": "4.05", "niobium": "4.3", "molybdenum": "4.6",
    "technetium": "4.7", "ruthenium": "4.71", "rhodium": "4.98", "palladium": "5.12",
    "silver": "4.26", "cadmium": "4.22", "indium": "4.12", "tin": "4.42",
    "caesium": "2.14", "barium": "2.7", "lanthanum": "3.5", "hafnium": "3.9",
    "tantalum": "4.25", "tungsten": "4.55", "rhenium": "4.96", "osmium": "5.93",
    "iridium": "5.27", "platinum": "5.65", "gold": "5.1", "mercury": "4.49",
    "thallium": "3.84", "lead": "4.25", "bismuth": "4.22",
}

# Space group data (crystal structure)
SPACE_GROUP = {
    "lithium": ("Im-3m", "229"), "beryllium": ("P6_3/mmc", "194"),
    "sodium": ("Im-3m", "229"), "magnesium": ("P6_3/mmc", "194"),
    "aluminium": ("Fm-3m", "225"), "potassium": ("Im-3m", "229"),
    "calcium": ("Fm-3m", "225"), "scandium": ("P6_3/mmc", "194"),
    "titanium": ("P6_3/mmc", "194"), "vanadium": ("Im-3m", "229"),
    "chromium": ("Im-3m", "229"), "manganese": ("I-43m", "217"),
    "iron": ("Im-3m", "229"), "cobalt": ("P6_3/mmc", "194"),
    "nickel": ("Fm-3m", "225"), "copper": ("Fm-3m", "225"),
    "zinc": ("P6_3/mmc", "194"), "gallium": ("Cmca", "64"),
    "strontium": ("Fm-3m", "225"), "yttrium": ("P6_3/mmc", "194"),
    "zirconium": ("P6_3/mmc", "194"), "niobium": ("Im-3m", "229"),
    "molybdenum": ("Im-3m", "229"), "ruthenium": ("P6_3/mmc", "194"),
    "rhodium": ("Fm-3m", "225"), "palladium": ("Fm-3m", "225"),
    "silver": ("Fm-3m", "225"), "cadmium": ("P6_3/mmc", "194"),
    "indium": ("I4/mmm", "139"), "barium": ("Im-3m", "229"),
    "lanthanum": ("P6_3/mmc", "194"), "cerium": ("Fm-3m", "225"),
    "praseodymium": ("P6_3/mmc", "194"), "neodymium": ("P6_3/mmc", "194"),
    "gadolinium": ("P6_3/mmc", "194"), "terbium": ("P6_3/mmc", "194"),
    "dysprosium": ("P6_3/mmc", "194"), "holmium": ("P6_3/mmc", "194"),
    "erbium": ("P6_3/mmc", "194"), "thulium": ("P6_3/mmc", "194"),
    "ytterbium": ("Fm-3m", "225"), "lutetium": ("P6_3/mmc", "194"),
    "hafnium": ("P6_3/mmc", "194"), "tantalum": ("Im-3m", "229"),
    "tungsten": ("Im-3m", "229"), "rhenium": ("P6_3/mmc", "194"),
    "osmium": ("P6_3/mmc", "194"), "iridium": ("Fm-3m", "225"),
    "platinum": ("Fm-3m", "225"), "gold": ("Fm-3m", "225"),
    "lead": ("Fm-3m", "225"), "thorium": ("Fm-3m", "225"),
    "uranium": ("Cmcm", "63"), "rubidium": ("Im-3m", "229"),
    "caesium": ("Im-3m", "229"), "technetium": ("P6_3/mmc", "194"),
    "tin": ("I4_1/amd", "141"), "antimony": ("R-3m", "166"),
    "bismuth": ("R-3m", "166"), "polonium": ("Pm-3m", "221"),
    "francium": ("Im-3m", "229"), "radium": ("Im-3m", "229"),
    "actinium": ("Fm-3m", "225"), "protactinium": ("I4/mmm", "139"),
    "neptunium": ("Pnma", "62"), "plutonium": ("P2_1/m", "11"),
    "samarium": ("R-3m", "166"), "europium": ("Im-3m", "229"),
    "thallium": ("P6_3/mmc", "194"), "mercury": ("R-3m", "166"),
}

# Magnetic type data
MAGNETIC_TYPE = {
    "iron": "Ferromagnetic", "cobalt": "Ferromagnetic", "nickel": "Ferromagnetic",
    "gadolinium": "Ferromagnetic", "terbium": "Ferromagnetic", "dysprosium": "Ferromagnetic",
    "holmium": "Ferromagnetic", "erbium": "Ferromagnetic",
    "chromium": "Antiferromagnetic", "manganese": "Antiferromagnetic",
    "helium": "Diamagnetic", "beryllium": "Diamagnetic", "carbon": "Diamagnetic",
    "nitrogen": "Diamagnetic", "neon": "Diamagnetic", "magnesium": "Paramagnetic",
    "aluminium": "Paramagnetic", "silicon": "Diamagnetic", "argon": "Diamagnetic",
    "calcium": "Paramagnetic", "titanium": "Paramagnetic", "vanadium": "Paramagnetic",
    "zinc": "Diamagnetic", "copper": "Diamagnetic", "silver": "Diamagnetic",
    "gold": "Diamagnetic", "mercury": "Diamagnetic", "lead": "Diamagnetic",
    "bismuth": "Diamagnetic", "sodium": "Paramagnetic", "potassium": "Paramagnetic",
    "scandium": "Paramagnetic", "lithium": "Paramagnetic", "hydrogen": "Diamagnetic",
    "boron": "Diamagnetic", "oxygen": "Paramagnetic", "fluorine": "Diamagnetic",
    "phosphorus": "Diamagnetic", "sulfur": "Diamagnetic", "chlorine": "Diamagnetic",
    "germanium": "Diamagnetic", "arsenic": "Diamagnetic", "selenium": "Diamagnetic",
    "bromine": "Diamagnetic", "krypton": "Diamagnetic", "rubidium": "Paramagnetic",
    "strontium": "Paramagnetic", "yttrium": "Paramagnetic", "zirconium": "Paramagnetic",
    "niobium": "Paramagnetic", "molybdenum": "Paramagnetic", "technetium": "Paramagnetic",
    "ruthenium": "Paramagnetic", "rhodium": "Paramagnetic", "palladium": "Paramagnetic",
    "cadmium": "Diamagnetic", "indium": "Diamagnetic", "tin": "Diamagnetic",
    "antimony": "Diamagnetic", "tellurium": "Diamagnetic", "iodine": "Diamagnetic",
    "xenon": "Diamagnetic", "caesium": "Paramagnetic", "barium": "Paramagnetic",
    "lanthanum": "Paramagnetic", "cerium": "Paramagnetic", "praseodymium": "Paramagnetic",
    "neodymium": "Paramagnetic", "promethium": "Paramagnetic", "samarium": "Paramagnetic",
    "europium": "Paramagnetic", "thulium": "Paramagnetic", "ytterbium": "Paramagnetic",
    "lutetium": "Paramagnetic", "hafnium": "Paramagnetic", "tantalum": "Paramagnetic",
    "tungsten": "Paramagnetic", "rhenium": "Paramagnetic", "osmium": "Paramagnetic",
    "iridium": "Paramagnetic", "platinum": "Paramagnetic", "thallium": "Diamagnetic",
    "polonium": "Diamagnetic", "astatine": "Diamagnetic", "radon": "Diamagnetic",
    "francium": "Paramagnetic", "radium": "Paramagnetic", "actinium": "Paramagnetic",
    "thorium": "Paramagnetic", "protactinium": "Paramagnetic", "uranium": "Paramagnetic",
    "neptunium": "Paramagnetic", "plutonium": "Paramagnetic", "americium": "Paramagnetic",
    "curium": "Paramagnetic", "berkelium": "Paramagnetic", "californium": "Paramagnetic",
}

# Curie point data in Kelvin
CURIE_POINT = {
    "iron": "1043", "cobalt": "1388", "nickel": "627", "gadolinium": "293",
    "terbium": "219", "dysprosium": "88", "holmium": "20", "erbium": "19",
}

# Human body abundance in % by mass
HUMAN_BODY = {
    "oxygen": "65%", "carbon": "18%", "hydrogen": "10%", "nitrogen": "3%",
    "calcium": "1.5%", "phosphorus": "1.0%", "potassium": "0.2%", "sulfur": "0.2%",
    "sodium": "0.15%", "chlorine": "0.15%", "magnesium": "0.05%", "iron": "0.006%",
    "fluorine": "0.0037%", "zinc": "0.0032%", "silicon": "0.002%", "rubidium": "0.00046%",
    "strontium": "0.00046%", "bromine": "0.00029%", "lead": "0.00017%", "copper": "0.0001%",
    "aluminium": "0.00009%", "cadmium": "0.00007%", "boron": "0.00007%", "tin": "0.00003%",
    "iodine": "0.00002%", "selenium": "0.00002%", "manganese": "0.00002%",
    "chromium": "0.000024%", "molybdenum": "0.000013%", "cobalt": "0.000021%",
    "nickel": "0.000015%", "vanadium": "0.000026%", "arsenic": "0.00007%",
    "barium": "0.000031%", "lithium": "0.000003%", "beryllium": "0.0000004%",
}

# Meteorite abundance in mg/kg (ppm)
METEORITES = {
    "iron": "190000", "oxygen": "464000", "silicon": "210000", "magnesium": "140000",
    "sulfur": "52000", "calcium": "15000", "aluminium": "14000", "nickel": "12000",
    "sodium": "7000", "chromium": "3200", "phosphorus": "1600", "manganese": "2200",
    "potassium": "720", "titanium": "620", "cobalt": "600", "fluorine": "560",
    "zinc": "310", "nitrogen": "140", "copper": "110", "scandium": "7.3",
    "vanadium": "60", "lithium": "1.7", "boron": "0.87", "beryllium": "0.026",
    "hydrogen": "2400", "carbon": "35000", "chlorine": "560", "strontium": "7.8",
    "barium": "2.7", "yttrium": "1.9", "zirconium": "4.4", "niobium": "0.19",
    "molybdenum": "0.96", "ruthenium": "0.71", "rhodium": "0.134", "palladium": "0.56",
    "silver": "0.093", "cadmium": "0.45", "indium": "0.004", "tin": "1.1",
    "antimony": "0.12", "tellurium": "2.2", "iodine": "0.45", "caesium": "0.14",
    "lanthanum": "0.344", "cerium": "0.835", "praseodymium": "0.117", "neodymium": "0.63",
    "samarium": "0.204", "europium": "0.077", "gadolinium": "0.292", "terbium": "0.052",
    "dysprosium": "0.378", "holmium": "0.081", "erbium": "0.226", "thulium": "0.032",
    "ytterbium": "0.22", "lutetium": "0.033", "hafnium": "0.13", "tantalum": "0.015",
    "tungsten": "0.093", "rhenium": "0.038", "osmium": "0.45", "iridium": "0.45",
    "platinum": "0.92", "gold": "0.18", "mercury": "0.25", "thallium": "0.013",
    "lead": "1.5", "bismuth": "0.069", "thorium": "0.040", "uranium": "0.013",
}

# Molar volume data in cm³/mol at STP
MOLAR_VOLUME = {
    "hydrogen": "14.4", "helium": "27.2", "lithium": "13.02", "beryllium": "4.85",
    "boron": "4.39", "carbon": "5.29", "nitrogen": "17.3", "oxygen": "17.36",
    "fluorine": "11.2", "neon": "16.8", "sodium": "23.78", "magnesium": "14.00",
    "aluminium": "10.00", "silicon": "12.06", "phosphorus": "17.0", "sulfur": "15.5",
    "chlorine": "17.39", "argon": "24.2", "potassium": "45.94", "calcium": "26.20",
    "scandium": "15.00", "titanium": "10.64", "vanadium": "8.32", "chromium": "7.23",
    "manganese": "7.35", "iron": "7.09", "cobalt": "6.67", "nickel": "6.59",
    "copper": "7.11", "zinc": "9.16", "gallium": "11.80", "germanium": "13.63",
    "arsenic": "12.95", "selenium": "16.42", "bromine": "23.5", "krypton": "38.9",
    "rubidium": "55.79", "strontium": "33.94", "yttrium": "19.88", "zirconium": "14.02",
    "niobium": "10.83", "molybdenum": "9.38", "technetium": "8.63", "ruthenium": "8.17",
    "rhodium": "8.28", "palladium": "8.56", "silver": "10.27", "cadmium": "13.00",
    "indium": "15.76", "tin": "16.29", "antimony": "18.19", "tellurium": "20.46",
    "iodine": "25.72", "xenon": "42.9", "caesium": "70.94", "barium": "38.16",
    "lanthanum": "22.39", "cerium": "20.69", "praseodymium": "20.8", "neodymium": "20.6",
    "promethium": "20.2", "samarium": "19.95", "europium": "28.97", "gadolinium": "19.90",
    "terbium": "19.3", "dysprosium": "19.0", "holmium": "18.7", "erbium": "18.4",
    "thulium": "18.1", "ytterbium": "24.84", "lutetium": "17.8", "hafnium": "13.44",
    "tantalum": "10.85", "tungsten": "9.47", "rhenium": "8.86", "osmium": "8.43",
    "iridium": "8.52", "platinum": "9.09", "gold": "10.21", "mercury": "14.09",
    "thallium": "17.22", "lead": "18.26", "bismuth": "21.31", "polonium": "22.7",
    "radon": "50.5", "radium": "41.1", "thorium": "19.8", "uranium": "12.5",
}

# Molar heat capacity in J/(mol·K)
MOLAR_HEAT_CAPACITY = {
    "hydrogen": "28.836", "helium": "20.786", "lithium": "24.860", "beryllium": "16.443",
    "boron": "11.087", "carbon": "8.517", "nitrogen": "29.124", "oxygen": "29.378",
    "fluorine": "31.304", "neon": "20.786", "sodium": "28.230", "magnesium": "24.869",
    "aluminium": "24.200", "silicon": "19.789", "phosphorus": "23.824", "sulfur": "22.75",
    "chlorine": "33.949", "argon": "20.786", "potassium": "29.600", "calcium": "25.929",
    "scandium": "25.52", "titanium": "25.060", "vanadium": "24.89", "chromium": "23.35",
    "manganese": "26.32", "iron": "25.10", "cobalt": "24.81", "nickel": "26.07",
    "copper": "24.440", "zinc": "25.390", "gallium": "25.86", "germanium": "23.222",
    "arsenic": "24.64", "selenium": "25.363", "bromine": "36.057", "krypton": "20.786",
    "rubidium": "31.060", "strontium": "26.4", "yttrium": "26.53", "zirconium": "25.36",
    "niobium": "24.60", "molybdenum": "24.06", "technetium": "24.27", "ruthenium": "24.06",
    "rhodium": "24.98", "palladium": "25.98", "silver": "25.350", "cadmium": "26.020",
    "indium": "26.74", "tin": "27.112", "antimony": "25.23", "tellurium": "25.73",
    "iodine": "36.888", "xenon": "20.786", "caesium": "32.210", "barium": "28.07",
    "lanthanum": "27.11", "cerium": "26.94", "praseodymium": "27.20", "neodymium": "27.45",
    "promethium": "27.3", "samarium": "29.54", "europium": "27.66", "gadolinium": "37.03",
    "terbium": "28.91", "dysprosium": "27.70", "holmium": "27.15", "erbium": "28.12",
    "thulium": "27.03", "ytterbium": "26.74", "lutetium": "26.86", "hafnium": "25.73",
    "tantalum": "25.36", "tungsten": "24.27", "rhenium": "25.48", "osmium": "24.7",
    "iridium": "25.10", "platinum": "25.86", "gold": "25.418", "mercury": "27.983",
    "thallium": "26.32", "lead": "26.650", "bismuth": "25.52", "polonium": "26.4",
    "radon": "20.786", "radium": "31.1", "thorium": "26.230", "uranium": "27.665",
}

# Thermal expansion coefficient in µm/(m·K)
THERMAL_EXPANSION = {
    "aluminium": "23.1", "copper": "16.5", "gold": "14.2", "silver": "18.9",
    "iron": "11.8", "nickel": "13.4", "zinc": "30.2", "lead": "28.9",
    "tin": "22.0", "magnesium": "24.8", "titanium": "8.6", "tungsten": "4.5",
    "platinum": "8.8", "silicon": "2.6", "germanium": "5.9", "lithium": "46",
    "beryllium": "11.3", "sodium": "71", "potassium": "83", "calcium": "22.3",
    "scandium": "10.2", "vanadium": "8.4", "chromium": "4.9", "manganese": "22",
    "cobalt": "13.0", "gallium": "18", "rubidium": "90", "strontium": "22.5",
    "yttrium": "10.6", "zirconium": "5.7", "niobium": "7.3", "molybdenum": "4.8",
    "technetium": "7.2", "ruthenium": "6.4", "rhodium": "8.2", "palladium": "11.8",
    "cadmium": "30.8", "indium": "32.1", "antimony": "11", "tellurium": "16.8",
    "caesium": "97", "barium": "20.6", "lanthanum": "12.1", "cerium": "6.3",
    "praseodymium": "6.7", "neodymium": "9.6", "promethium": "11", "samarium": "12.7",
    "europium": "35", "gadolinium": "9.4", "terbium": "10.3", "dysprosium": "9.9",
    "holmium": "11.2", "erbium": "12.2", "thulium": "13.3", "ytterbium": "26.3",
    "lutetium": "9.9", "hafnium": "5.9", "tantalum": "6.3", "rhenium": "6.2",
    "osmium": "5.1", "iridium": "6.4", "mercury": "60.4", "thallium": "29.9",
    "bismuth": "13.4", "thorium": "11.0", "uranium": "13.9", "neptunium": "25",
    "plutonium": "54",
}

# Néel point data in Kelvin (antiferromagnetic transition temperature)
NEEL_POINT = {
    "chromium": "311", "manganese": "100",
}

# Refractive index (for elements that have optical properties)
REFRACTIVE_INDEX = {
    "diamond": "2.417", "silicon": "3.42", "germanium": "4.0",
    "sulfur": "1.96", "selenium": "2.92", "tellurium": "4.9",
    "phosphorus": "1.82", "carbon": "2.417", "boron": "1.58",
}

# Speed of sound data in m/s for solids at room temperature
SPEED_OF_SOUND_SOLID = {
    "beryllium": "12870", "aluminium": "5100", "iron": "5120", "copper": "3810",
    "silver": "2680", "gold": "2030", "zinc": "3700", "nickel": "4970",
    "titanium": "4140", "tungsten": "4620", "lead": "1190", "tin": "2500",
    "magnesium": "4602", "chromium": "5940", "platinum": "2680", "diamond": "12000",
    "silicon": "8433", "germanium": "5400", "lithium": "6000", "sodium": "3200",
    "potassium": "2000", "calcium": "3810", "scandium": "4600", "vanadium": "4560",
    "manganese": "5150", "cobalt": "4720", "gallium": "2740", "rubidium": "1300",
    "strontium": "3600", "yttrium": "3300", "zirconium": "3800", "niobium": "3480",
    "molybdenum": "5400", "technetium": "5100", "ruthenium": "5970", "rhodium": "4700",
    "palladium": "3070", "cadmium": "2310", "indium": "2160", "antimony": "3420",
    "tellurium": "2610", "caesium": "1090", "barium": "1620", "lanthanum": "2475",
    "cerium": "2450", "praseodymium": "2280", "neodymium": "2330", "samarium": "2130",
    "gadolinium": "2680", "terbium": "2620", "dysprosium": "2710", "holmium": "2760",
    "erbium": "2830", "thulium": "2880", "ytterbium": "1590", "lutetium": "3000",
    "hafnium": "3010", "tantalum": "3400", "rhenium": "4700", "osmium": "4940",
    "iridium": "4825", "mercury": "1450", "thallium": "818", "bismuth": "1790",
    "thorium": "2490", "uranium": "3155",
}

# Speed of sound in gases at 0°C, 101.325 kPa (m/s)
SPEED_OF_SOUND_GAS = {
    "hydrogen": "1270", "helium": "970", "nitrogen": "337", "oxygen": "316",
    "fluorine": "263", "neon": "435", "chlorine": "206", "argon": "319",
    "krypton": "221", "xenon": "178", "radon": "169", "methane": "430",
}

# Speed of sound in liquids (m/s) - limited data available
SPEED_OF_SOUND_LIQUID = {
    "mercury": "1450", "bromine": "890", "water": "1481", "gallium": "2870",
}

# Young's modulus in GPa
YOUNG_MODULUS = {
    "aluminium": "70", "copper": "130", "gold": "79", "silver": "83",
    "iron": "211", "nickel": "200", "titanium": "116", "tungsten": "411",
    "chromium": "279", "zinc": "108", "magnesium": "45", "lead": "16",
    "tin": "50", "beryllium": "287", "molybdenum": "329", "platinum": "168",
    "diamond": "1220", "silicon": "130", "germanium": "103", "lithium": "4.9",
    "sodium": "10", "potassium": "3.53", "calcium": "20", "scandium": "74.4",
    "vanadium": "128", "manganese": "198", "cobalt": "209", "gallium": "9.8",
    "rubidium": "2.4", "strontium": "15.7", "yttrium": "63.5", "zirconium": "88",
    "niobium": "105", "technetium": "290", "ruthenium": "447", "rhodium": "275",
    "palladium": "121", "cadmium": "50", "indium": "11", "antimony": "55",
    "tellurium": "43", "caesium": "1.7", "barium": "13", "lanthanum": "36.6",
    "cerium": "33.6", "praseodymium": "37.3", "neodymium": "41.4", "samarium": "49.7",
    "europium": "18.2", "gadolinium": "54.8", "terbium": "55.7", "dysprosium": "61.4",
    "holmium": "64.8", "erbium": "69.9", "thulium": "74.0", "ytterbium": "23.9",
    "lutetium": "68.6", "hafnium": "141", "tantalum": "186", "rhenium": "463",
    "osmium": "559", "iridium": "528", "mercury": "38", "thallium": "8",
    "bismuth": "32", "thorium": "79", "uranium": "208",
}

# Shear modulus in GPa
SHEAR_MODULUS = {
    "aluminium": "26", "copper": "48", "gold": "27", "silver": "30",
    "iron": "82", "nickel": "76", "titanium": "44", "tungsten": "161",
    "chromium": "115", "zinc": "43", "magnesium": "17", "lead": "5.6",
    "beryllium": "132", "molybdenum": "126", "platinum": "61", "lithium": "4.2",
    "sodium": "3.3", "potassium": "1.3", "calcium": "7.4", "scandium": "29.1",
    "vanadium": "47", "manganese": "79.5", "cobalt": "75", "gallium": "3.7",
    "rubidium": "1.6", "strontium": "6.3", "yttrium": "25.6", "zirconium": "33",
    "niobium": "38", "technetium": "115", "ruthenium": "173", "rhodium": "150",
    "palladium": "44", "cadmium": "19", "indium": "4.1", "antimony": "20",
    "tellurium": "16", "caesium": "0.8", "barium": "4.9", "lanthanum": "14.3",
    "cerium": "13.5", "gadolinium": "21.8", "terbium": "22.1", "dysprosium": "24.7",
    "holmium": "26.3", "erbium": "28.3", "thulium": "30.5", "ytterbium": "9.9",
    "lutetium": "27.2", "hafnium": "30", "tantalum": "69", "rhenium": "178",
    "osmium": "222", "iridium": "210", "thallium": "2.8", "bismuth": "12",
    "thorium": "31", "uranium": "111",
}

# Bulk modulus in GPa
BULK_MODULUS = {
    "aluminium": "76", "copper": "140", "gold": "180", "silver": "100",
    "iron": "170", "nickel": "180", "titanium": "110", "tungsten": "310",
    "chromium": "160", "zinc": "70", "magnesium": "45", "lead": "46",
    "beryllium": "130", "molybdenum": "230", "platinum": "230", "lithium": "11",
    "sodium": "6.3", "potassium": "3.1", "calcium": "17", "scandium": "56.6",
    "vanadium": "160", "manganese": "120", "cobalt": "180", "gallium": "57",
    "rubidium": "2.5", "strontium": "12", "yttrium": "41.2", "zirconium": "91.1",
    "niobium": "170", "technetium": "240", "ruthenium": "220", "rhodium": "380",
    "palladium": "180", "cadmium": "62", "indium": "46", "antimony": "42",
    "tellurium": "65", "caesium": "1.6", "barium": "9.6", "lanthanum": "27.9",
    "cerium": "21.5", "gadolinium": "37.9", "terbium": "38.7", "dysprosium": "40.5",
    "holmium": "40.2", "erbium": "44.4", "thulium": "44.5", "ytterbium": "30.5",
    "lutetium": "47.6", "hafnium": "110", "tantalum": "200", "rhenium": "370",
    "osmium": "395", "iridium": "320", "mercury": "25", "thallium": "43",
    "bismuth": "31", "thorium": "54", "uranium": "100",
}

# Poisson's ratio (dimensionless)
POISSON_RATIO = {
    "aluminium": "0.35", "copper": "0.34", "gold": "0.44", "silver": "0.37",
    "iron": "0.29", "nickel": "0.31", "titanium": "0.32", "tungsten": "0.28",
    "chromium": "0.21", "zinc": "0.25", "magnesium": "0.29", "lead": "0.44",
    "beryllium": "0.032", "molybdenum": "0.31", "platinum": "0.38", "lithium": "0.36",
    "sodium": "0.36", "potassium": "0.35", "calcium": "0.31", "scandium": "0.28",
    "vanadium": "0.36", "manganese": "0.24", "cobalt": "0.31", "gallium": "0.29",
    "rubidium": "0.35", "strontium": "0.28", "yttrium": "0.24", "zirconium": "0.34",
    "niobium": "0.40", "technetium": "0.25", "ruthenium": "0.30", "rhodium": "0.26",
    "palladium": "0.39", "cadmium": "0.30", "indium": "0.45", "tin": "0.36",
    "antimony": "0.33", "tellurium": "0.33", "caesium": "0.36", "barium": "0.21",
    "lanthanum": "0.28", "cerium": "0.24", "gadolinium": "0.26", "terbium": "0.26",
    "dysprosium": "0.25", "holmium": "0.23", "erbium": "0.24", "thulium": "0.21",
    "ytterbium": "0.21", "lutetium": "0.26", "hafnium": "0.37", "tantalum": "0.34",
    "rhenium": "0.30", "osmium": "0.25", "iridium": "0.26", "mercury": "0.5",
    "thallium": "0.45", "bismuth": "0.33", "thorium": "0.27", "uranium": "0.23",
}

# Mohs hardness
MOHS_HARDNESS = {
    "talc": "1", "gypsum": "2", "calcium": "1.75", "lead": "1.5",
    "tin": "1.5", "zinc": "2.5", "gold": "2.5", "silver": "2.5",
    "copper": "3.0", "aluminium": "2.75", "iron": "4.0", "nickel": "4.0",
    "chromium": "8.5", "diamond": "10", "boron": "9.3", "sodium": "0.5",
    "potassium": "0.4", "lithium": "0.6", "magnesium": "2.5", "platinum": "4.3",
    "tungsten": "7.5", "titanium": "6.0", "cobalt": "5.0", "beryllium": "5.5",
    "zirconium": "5.0", "vanadium": "7.0", "molybdenum": "5.5", "rhodium": "6.0",
    "palladium": "4.8", "iridium": "6.5", "osmium": "7.0", "rhenium": "7.0",
}

# Vickers hardness in MPa
VICKERS_HARDNESS = {
    "chromium": "1060", "titanium": "970", "nickel": "638", "iron": "608",
    "copper": "369", "zinc": "412", "aluminium": "167", "gold": "216",
    "silver": "251", "lead": "38", "tin": "51", "magnesium": "260",
    "beryllium": "1670", "cobalt": "1043", "platinum": "549", "tungsten": "3430",
    "molybdenum": "1530", "vanadium": "628", "zirconium": "903", "niobium": "1320",
    "tantalum": "873", "rhodium": "1246", "palladium": "461", "iridium": "1760",
    "osmium": "3920", "rhenium": "2450",
}

# Brinell hardness in MPa
BRINELL_HARDNESS = {
    "chromium": "1120", "titanium": "716", "nickel": "700", "iron": "490",
    "copper": "235", "zinc": "412", "aluminium": "245", "gold": "25",
    "silver": "24.5", "lead": "38.3", "tin": "51", "magnesium": "260",
    "beryllium": "600", "cobalt": "700", "platinum": "392", "tungsten": "2570",
    "molybdenum": "1500", "vanadium": "628", "zirconium": "650", "niobium": "736",
    "tantalum": "800", "rhodium": "1100", "palladium": "37.3", "iridium": "1670",
    "osmium": "3920", "rhenium": "1320",
}


def populate_element_data(element_data):
    """Populate missing data fields for a single element."""
    element_name = element_data.get("element", "").lower()
    
    # Electrical properties
    if element_data.get("electrical_type") == "---" and element_name in ELECTRICAL_TYPE:
        element_data["electrical_type"] = ELECTRICAL_TYPE[element_name]
    
    if element_data.get("resistivity") == "---" and element_name in RESISTIVITY:
        element_data["resistivity"] = RESISTIVITY[element_name]
    
    if element_data.get("resistivity_mult") == "---" and element_name in RESISTIVITY_MULT:
        element_data["resistivity_mult"] = RESISTIVITY_MULT[element_name]
    
    # Atomic radii
    if element_data.get("element_covalent_radius") == "---" and element_name in COVALENT_RADIUS:
        element_data["element_covalent_radius"] = COVALENT_RADIUS[element_name] + " (pm)"
    
    if element_data.get("element_van_der_waals") == "---" and element_name in VAN_DER_WAALS_RADIUS:
        element_data["element_van_der_waals"] = VAN_DER_WAALS_RADIUS[element_name] + " (pm)"
    
    # Thermal properties
    if element_data.get("thermal_conductivity") == "---" and element_name in THERMAL_CONDUCTIVITY:
        element_data["thermal_conductivity"] = THERMAL_CONDUCTIVITY[element_name] + " W/(m·K)"
    
    if element_data.get("thermal_expansion") == "---" and element_name in THERMAL_EXPANSION:
        element_data["thermal_expansion"] = THERMAL_EXPANSION[element_name] + " µm/(m·K)"
    
    # Electronic properties
    if element_data.get("electron_affinity") == "---" and element_name in ELECTRON_AFFINITY:
        element_data["electron_affinity"] = ELECTRON_AFFINITY[element_name] + " kJ/mol"
    
    if element_data.get("electronegativity_allen") == "---" and element_name in ELECTRONEGATIVITY_ALLEN:
        element_data["electronegativity_allen"] = ELECTRONEGATIVITY_ALLEN[element_name]
    
    if element_data.get("work_function") == "---" and element_name in WORK_FUNCTION:
        element_data["work_function"] = WORK_FUNCTION[element_name] + " eV"
    
    # Crystal structure
    if element_name in SPACE_GROUP:
        if element_data.get("space_group_name") == "---":
            element_data["space_group_name"] = SPACE_GROUP[element_name][0]
        if element_data.get("space_group_number") == "---":
            element_data["space_group_number"] = SPACE_GROUP[element_name][1]
    
    # Magnetic properties
    if element_data.get("magnetic_type") == "---" and element_name in MAGNETIC_TYPE:
        element_data["magnetic_type"] = MAGNETIC_TYPE[element_name]
    
    if element_data.get("curie_point") == "---" and element_name in CURIE_POINT:
        element_data["curie_point"] = CURIE_POINT[element_name] + " K"
    
    if element_data.get("neel_point") == "---" and element_name in NEEL_POINT:
        element_data["neel_point"] = NEEL_POINT[element_name] + " K"
    
    # Abundance data
    if element_data.get("human_body") == "---" and element_name in HUMAN_BODY:
        element_data["human_body"] = HUMAN_BODY[element_name]
    
    if element_data.get("meteorites") == "---" and element_name in METEORITES:
        element_data["meteorites"] = METEORITES[element_name] + " mg/kg"
    
    # Molar properties
    if element_data.get("molar_volume") == "---" and element_name in MOLAR_VOLUME:
        element_data["molar_volume"] = MOLAR_VOLUME[element_name] + " cm³/mol"
    
    if element_data.get("molar_heat_capacity") == "---" and element_name in MOLAR_HEAT_CAPACITY:
        element_data["molar_heat_capacity"] = MOLAR_HEAT_CAPACITY[element_name] + " J/(mol·K)"
    
    # Optical properties
    if element_data.get("refractive_index") == "---" and element_name in REFRACTIVE_INDEX:
        element_data["refractive_index"] = REFRACTIVE_INDEX[element_name]
    
    # Speed of sound
    if element_data.get("speed_of_sound_solid") == "---" and element_name in SPEED_OF_SOUND_SOLID:
        element_data["speed_of_sound_solid"] = SPEED_OF_SOUND_SOLID[element_name] + " m/s"
    
    if element_data.get("speed_of_sound_gas") == "---" and element_name in SPEED_OF_SOUND_GAS:
        element_data["speed_of_sound_gas"] = SPEED_OF_SOUND_GAS[element_name] + " m/s"
    
    if element_data.get("speed_of_sound_liquid") == "---" and element_name in SPEED_OF_SOUND_LIQUID:
        element_data["speed_of_sound_liquid"] = SPEED_OF_SOUND_LIQUID[element_name] + " m/s"
    
    # Mechanical properties
    if element_data.get("young_modulus") == "---" and element_name in YOUNG_MODULUS:
        element_data["young_modulus"] = YOUNG_MODULUS[element_name] + " GPa"
    
    if element_data.get("shear_modulus") == "---" and element_name in SHEAR_MODULUS:
        element_data["shear_modulus"] = SHEAR_MODULUS[element_name] + " GPa"
    
    if element_data.get("bulk_modulus") == "---" and element_name in BULK_MODULUS:
        element_data["bulk_modulus"] = BULK_MODULUS[element_name] + " GPa"
    
    if element_data.get("poisson_ratio") == "---" and element_name in POISSON_RATIO:
        element_data["poisson_ratio"] = POISSON_RATIO[element_name]
    
    # Hardness
    if element_data.get("mohs_hardness") == "---" and element_name in MOHS_HARDNESS:
        element_data["mohs_hardness"] = MOHS_HARDNESS[element_name]
    
    if element_data.get("vickers_hardness") == "---" and element_name in VICKERS_HARDNESS:
        element_data["vickers_hardness"] = VICKERS_HARDNESS[element_name] + " MPa"
    
    if element_data.get("brinell_hardness") == "---" and element_name in BRINELL_HARDNESS:
        element_data["brinell_hardness"] = BRINELL_HARDNESS[element_name] + " MPa"
    
    return element_data


def main():
    """Main function to populate element data across all language files."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(script_dir)
    assets_dir = os.path.join(repo_root, "app", "src", "main", "assets")
    
    # Language files to update
    language_files = [
        "elements_en.json", "elements_de.json", "elements_es.json",
        "elements_fr.json", "elements_it.json", "elements_pt.json",
        "elements_sv.json", "elements_fil.json", "elements_af.json",
        "elements_hi.json", "elements_ur.json", "elements_zh.json"
    ]
    
    print("Populating element data with IUPAC standards...")
    
    for lang_file in language_files:
        file_path = os.path.join(assets_dir, lang_file)
        if not os.path.exists(file_path):
            print(f"Warning: {lang_file} not found, skipping...")
            continue
        
        print(f"\nProcessing {lang_file}...")
        
        # Load existing data
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # Track changes
        changes = 0
        
        # Process each element
        for element_key, element_data in data.items():
            original_data = json.dumps(element_data)
            element_data = populate_element_data(element_data)
            
            if json.dumps(element_data) != original_data:
                changes += 1
        
        # Save updated data
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        print(f"  Updated {changes} elements in {lang_file}")
    
    print("\nData population complete!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
