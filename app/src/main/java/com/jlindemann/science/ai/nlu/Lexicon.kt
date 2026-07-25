package com.jlindemann.science.ai.nlu

import com.jlindemann.science.ai.data.Block
import com.jlindemann.science.ai.data.SeriesId

/**
 * The operator vocabulary the planner recognises, across all twelve supported languages.
 *
 * These are the words that turn a lookup into a computation — comparators, superlatives,
 * aggregations, counts and subset names. The legacy agent had superlatives in one undifferentiated
 * bag, so it could not tell "densest" from "least dense" and had to re-test with English
 * `contains` checks inside its handler. Here the two directions are separate sets from the start.
 *
 * Entries are matched against text that has already been lowercased and stripped of diacritics,
 * so accented forms are written here in their folded shape.
 */
object Lexicon {

    /** "greater than", in the sense of a numeric threshold. */
    val GREATER = listOf(
        "greater than", "more than", "higher than", "above", "over", "exceeds", "exceeding", "at least",
        "storre an", "mer an", "hogre an", "over", "minst",                 // sv
        "grosser als", "mehr als", "hoher als", "uber", "mindestens",       // de
        "mas de", "mayor que", "superior a", "por encima de",               // es
        "plus de", "superieur a", "au-dessus de", "plus grand que",         // fr
        "piu di", "maggiore di", "superiore a", "sopra",                    // it
        "mais de", "maior que", "superior a", "acima de",                   // pt
        "meer as", "groter as", "bo",                                       // af
        "higit sa", "mas mataas sa", "lampas sa",                           // fil
        "se adhik", "se jyada", "se zyada",                                 // hi (romanised)
        "se ziyada", "se barh",                                             // ur (romanised)
        "以上", "大于", "超过", "高于", "多于"                                  // zh
    )

    /** "less than", in the sense of a numeric threshold. */
    val LESS = listOf(
        "less than", "lower than", "below", "under", "beneath", "at most", "smaller than",
        "mindre an", "lagre an", "under", "hogst",                          // sv
        "weniger als", "kleiner als", "niedriger als", "unter", "hochstens", // de
        "menos de", "menor que", "inferior a", "por debajo de",             // es
        "moins de", "inferieur a", "en dessous de", "plus petit que",       // fr
        "meno di", "minore di", "inferiore a", "sotto",                     // it
        "menos de", "menor que", "inferior a", "abaixo de",                 // pt
        "minder as", "kleiner as", "onder",                                 // af
        "mas mababa sa", "kulang sa",                                       // fil
        "se kam",                                                           // hi
        "se kam", "se chota",                                               // ur
        "以下", "小于", "低于", "少于"                                          // zh
    )

    /** Superlatives asking for the largest value. */
    val MOST = listOf(
        "most", "highest", "largest", "greatest", "maximum", "max", "biggest", "heaviest",
        "densest", "hardest", "strongest", "hottest", "top",
        "mest", "hogst", "storst", "tyngst", "tatast", "hardast", "starkast", "hetast",
        "meiste", "hochste", "grosste", "schwerste", "dichteste", "harteste",
        "mas", "mayor", "maximo", "mas alto", "mas pesado", "mas denso", "mas duro",
        "plus", "maximal", "le plus", "plus eleve", "plus lourd", "plus dense", "plus dur",
        "piu", "massimo", "piu alto", "piu pesante", "piu denso", "piu duro",
        "maior", "maximo", "mais alto", "mais pesado", "mais denso",
        "meeste", "hoogste", "grootste", "swaarste", "digste",
        "pinaka", "pinakamataas", "pinakamabigat", "pinakamalaki",
        "sabse adhik", "sabse jyada", "sabse bhari", "uchchatam",
        "sab se ziyada", "sab se bara",
        "最高", "最大", "最重", "最密", "最硬", "最强"
    )

    /** Superlatives asking for the smallest value. */
    val LEAST = listOf(
        "least", "lowest", "smallest", "minimum", "min", "lightest", "softest", "weakest",
        "coldest", "sparsest", "rarest", "thinnest",
        "minst", "lagst", "lattast", "mjukast", "svagast", "kallast", "ovanligast",
        "wenigste", "niedrigste", "kleinste", "leichteste", "weichste", "kalteste",
        "menor", "minimo", "mas bajo", "mas ligero", "mas blando", "menos",
        "moins", "minimal", "le moins", "plus bas", "plus leger", "plus doux",
        "minimo", "piu basso", "piu leggero", "piu morbido", "meno",
        "menor", "minimo", "mais baixo", "mais leve", "mais macio",
        "minste", "laagste", "kleinste", "ligste", "sagste",
        "pinakamababa", "pinakamagaan", "pinakamaliit",
        "sabse kam", "sabse halka", "nyunatam",
        "sab se kam", "sab se halka",
        "最低", "最小", "最轻", "最软", "最弱"
    )

    /** Words asking for a mean. */
    val AVERAGE = listOf(
        "average", "mean", "avg",
        "genomsnitt", "medel", "medelvarde",
        "durchschnitt", "mittelwert", "durchschnittlich",
        "promedio", "media",
        "moyenne", "moyen",
        "media", "medio",
        "media", "medio",
        "gemiddelde",
        "karaniwan", "average",
        "ausat",
        "aosat",
        "平均"
    )

    /** Words asking for a total. */
    val SUM = listOf(
        "sum", "total", "combined", "altogether",
        "summa", "totalt", "sammanlagt",
        "summe", "gesamt", "insgesamt",
        "suma", "total",
        "somme", "total",
        "somma", "totale",
        "soma", "total",
        "som", "totaal",
        "kabuuan", "kabuuang",
        "yog", "kul",
        "总和", "总计", "合计"
    )

    /** Words asking how many. */
    val COUNT = listOf(
        "how many", "count", "number of", "how much",
        "hur manga", "antal", "antalet",
        "wie viele", "anzahl",
        "cuantos", "cuantas", "numero de", "cantidad",
        "combien", "nombre de",
        "quanti", "quante", "numero di",
        "quantos", "quantas", "numero de",
        "hoeveel", "aantal",
        "ilan", "bilang ng",
        "kitne", "kitni", "sankhya",
        "kitne", "tadad",
        "多少", "几个", "数量"
    )

    /** Words asking for a median. */
    val MEDIAN = listOf("median", "mediane", "mediana", "medianen", "中位数")

    /** Leading words that mark a query as a filtered list rather than a lookup. */
    val WHICH = listOf(
        "which", "what", "list", "show me", "find", "all",
        "vilka", "vilket", "lista", "visa", "alla",
        "welche", "welches", "liste", "zeige", "alle",
        "cuales", "cual", "lista", "muestra", "todos",
        "quels", "quelles", "liste", "montre", "tous",
        "quali", "quale", "elenco", "mostra", "tutti",
        "quais", "qual", "lista", "mostre", "todos",
        "watter", "lys", "wys", "alle",
        "alin", "aling", "ipakita", "lahat",
        "kaun", "kaunse", "suchi", "sabhi",
        "kaun", "konsa", "fehrist", "tamam",
        "哪些", "哪个", "列出", "全部", "所有"
    )

    /** Subset names mapped to the series they select. */
    val SERIES_WORDS: Map<String, Set<SeriesId>> = mapOf(
        "alkali metal" to setOf(SeriesId.ALKALI_METAL),
        "alkali metals" to setOf(SeriesId.ALKALI_METAL),
        "alkalimetall" to setOf(SeriesId.ALKALI_METAL),
        "alkalimetalle" to setOf(SeriesId.ALKALI_METAL),
        "alkaline earth" to setOf(SeriesId.ALKALINE_EARTH_METAL),
        "alkaline earth metal" to setOf(SeriesId.ALKALINE_EARTH_METAL),
        "alkaline earth metals" to setOf(SeriesId.ALKALINE_EARTH_METAL),
        "jordalkalimetall" to setOf(SeriesId.ALKALINE_EARTH_METAL),
        "transition metal" to setOf(SeriesId.TRANSITION_METAL),
        "transition metals" to setOf(SeriesId.TRANSITION_METAL),
        "overgangsmetall" to setOf(SeriesId.TRANSITION_METAL),
        "ubergangsmetall" to setOf(SeriesId.TRANSITION_METAL),
        "metal de transicion" to setOf(SeriesId.TRANSITION_METAL),
        "metaux de transition" to setOf(SeriesId.TRANSITION_METAL),
        "过渡金属" to setOf(SeriesId.TRANSITION_METAL),
        "post-transition metal" to setOf(SeriesId.POST_TRANSITION_METAL),
        "post transition metal" to setOf(SeriesId.POST_TRANSITION_METAL),
        "metalloid" to setOf(SeriesId.METALLOID),
        "metalloids" to setOf(SeriesId.METALLOID),
        "halbmetall" to setOf(SeriesId.METALLOID),
        "noble gas" to setOf(SeriesId.NOBLE_GAS),
        "noble gases" to setOf(SeriesId.NOBLE_GAS),
        "adelgas" to setOf(SeriesId.NOBLE_GAS),
        "edelgas" to setOf(SeriesId.NOBLE_GAS),
        "gas noble" to setOf(SeriesId.NOBLE_GAS),
        "惰性气体" to setOf(SeriesId.NOBLE_GAS),
        "稀有气体" to setOf(SeriesId.NOBLE_GAS),
        "halogen" to setOf(SeriesId.HALOGEN),
        "halogens" to setOf(SeriesId.HALOGEN),
        "halogener" to setOf(SeriesId.HALOGEN),
        "halogene" to setOf(SeriesId.HALOGEN),
        "卤素" to setOf(SeriesId.HALOGEN),
        "lanthanide" to setOf(SeriesId.LANTHANOID),
        "lanthanides" to setOf(SeriesId.LANTHANOID),
        "lanthanoid" to setOf(SeriesId.LANTHANOID),
        "lanthanoids" to setOf(SeriesId.LANTHANOID),
        "lantanid" to setOf(SeriesId.LANTHANOID),
        "actinide" to setOf(SeriesId.ACTINIDE),
        "actinides" to setOf(SeriesId.ACTINIDE),
        "aktinid" to setOf(SeriesId.ACTINIDE),
        "nonmetal" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL),
        "nonmetals" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL),
        "non-metal" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL),
        "non-metals" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL),
        "ickemetall" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL),
        "nichtmetall" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL),
        "非金属" to setOf(SeriesId.REACTIVE_NONMETAL, SeriesId.OTHER_NONMETAL)
    )

    /**
     * Concepts the element data has no field for, so the planner must decline them and let the
     * bespoke handlers answer. Reactivity is scored from group and position, not stored.
     */
    val UNBACKED_CONCEPTS = listOf(
        "reactive", "reactivity", "reaktiv", "reaktivitet", "reaktivaste", "reaktivitat",
        "reactivo", "reactividad", "reactif", "reactivite", "reattivo", "reattivita",
        "reativo", "reatividade", "reaktief", "reaktibo", "活泼", "反应性"
    )

    /**
     * Words selecting a whole family of properties rather than a single one.
     *
     * The legacy handler had branches that returned several related fields together — asking
     * about "thermal properties" gave fusion heat, vaporization heat, specific heat and
     * conductivity in one answer. These preserve that.
     */
    val CATEGORY_WORDS: Map<String, com.jlindemann.science.ai.data.FieldCategory> = mapOf(
        "thermal" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "thermodynamic" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "heat" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "termisk" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "termodynamisk" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "thermisch" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "termico" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "thermique" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "热" to com.jlindemann.science.ai.data.FieldCategory.THERMO,

        "electromagnetic" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,
        "magnetic properties" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,
        "electrical properties" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,
        "elektromagnetisk" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,
        "elektromagnetisch" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,
        "electromagnetico" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,
        "电磁" to com.jlindemann.science.ai.data.FieldCategory.ELECTROMAGNETIC,

        "mechanical" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "elastic" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "mekanisk" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "mechanisch" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "mecanico" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "mecanique" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "机械" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,

        "atomic properties" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,
        "atomara" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,
        "atomare" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,
        "原子性质" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,

        "crystal properties" to com.jlindemann.science.ai.data.FieldCategory.CRYSTAL,
        "nuclear properties" to com.jlindemann.science.ai.data.FieldCategory.NUCLEAR,

        "abundance" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "where is it found" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "where is" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "forekomst" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "vorkommen" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "abundancia" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "abondance" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "丰度" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE
    )

    /** Words that ask for a narrative overview, which the engine leaves to the personality layer. */
    val OVERVIEW_WORDS = listOf(
        "tell me about", "what is", "who is", "describe", "overview", "summary", "explain",
        "berätta om", "beratta om", "vad ar", "erzahl mir", "was ist", "beschreibe",
        "hablame de", "que es", "parle moi de", "qu est ce que", "parlami di", "cos e",
        "fale sobre", "o que e", "vertel my van", "wat is", "sabihin mo sa akin",
        "batao", "bataye", "介绍", "是什么"
    )

    /** Words that mark a query as a comparison between elements. */
    val COMPARE = listOf(
        "compare", "versus", "vs", "difference between", "denser than", "heavier than",
        "jamfor", "mot", "skillnad mellan",
        "vergleiche", "gegen", "gegenuber", "unterschied zwischen",
        "compara", "contra", "diferencia entre",
        "compare", "contre", "difference entre",
        "confronta", "contro", "differenza tra",
        "compare", "contra", "diferenca entre",
        "vergelyk", "teen",
        "ihambing", "kumpara",
        "tulna", "antar",
        "moazna", "farq",
        "比较", "对比", "区别"
    )

    /** Words selecting every metal, regardless of which metal series. */
    val METAL_WORDS = listOf("metal", "metals", "metall", "metaller", "metalle", "metales", "metaux", "metalli", "metais", "金属")

    val BLOCK_WORDS: Map<String, Block> = mapOf(
        "s-block" to Block.S, "s block" to Block.S,
        "p-block" to Block.P, "p block" to Block.P,
        "d-block" to Block.D, "d block" to Block.D,
        "f-block" to Block.F, "f block" to Block.F
    )

    val PHASE_WORDS: Map<String, String> = mapOf(
        "solid" to "solid", "fast" to "solid", "fest" to "solid", "solido" to "solid",
        "solide" to "solid", "solido" to "solid", "vast" to "solid", "固体" to "solid",
        "liquid" to "liquid", "flytande" to "liquid", "flussig" to "liquid",
        "liquido" to "liquid", "liquide" to "liquid", "vloeistof" to "liquid", "液体" to "liquid",
        "gas" to "gas", "gaseous" to "gas", "gasformig" to "gas", "gasformigt" to "gas",
        "gaseoso" to "gas", "gazeux" to "gas", "gassoso" to "gas", "气体" to "gas"
    )

    val RADIOACTIVE_WORDS = listOf(
        "radioactive", "radioaktiv", "radioaktivt", "radiactivo", "radioactif",
        "radioattivo", "radioativo", "radioaktief", "radyoaktibo", "rediyoaktiv", "放射性"
    )

    /** Ordinal words for banked fields such as "second ionization energy". */
    val ORDINALS: Map<String, Int> = mapOf(
        "first" to 1, "1st" to 1, "forsta" to 1, "erste" to 1, "primero" to 1, "premier" to 1, "primo" to 1, "第一" to 1,
        "second" to 2, "2nd" to 2, "andra" to 2, "zweite" to 2, "segundo" to 2, "deuxieme" to 2, "secondo" to 2, "第二" to 2,
        "third" to 3, "3rd" to 3, "tredje" to 3, "dritte" to 3, "tercero" to 3, "troisieme" to 3, "terzo" to 3, "第三" to 3,
        "fourth" to 4, "4th" to 4, "fjarde" to 4, "vierte" to 4, "cuarto" to 4, "quatrieme" to 4, "quarto" to 4,
        "fifth" to 5, "5th" to 5, "femte" to 5, "funfte" to 5, "quinto" to 5, "cinquieme" to 5
    )

    /** Prepositions that introduce a requested output unit. */
    val UNIT_PREPOSITIONS = listOf("in", "as", "to", "i", "als", "en", "com", "em", "用", "以")

    /** Unit names the planner accepts as a conversion target. */
    val UNIT_WORDS: Map<String, String> = mapOf(
        "kelvin" to "K", "k" to "K",
        "celsius" to "°C", "centigrade" to "°C", "c" to "°C",
        "fahrenheit" to "°F", "f" to "°F",
        "grader celsius" to "°C", "grad celsius" to "°C",
        "gram per cubic centimetre" to "g/cm³", "g/cm3" to "g/cm³",
        "kilograms per cubic metre" to "kg/m³", "kg/m3" to "kg/m³",
        "picometre" to "pm", "picometer" to "pm", "pm" to "pm",
        "angstrom" to "Å",
        "gigapascal" to "GPa", "gpa" to "GPa",
        "megapascal" to "MPa", "mpa" to "MPa",
        "electronvolt" to "eV", "ev" to "eV",
        "kilojoule per mole" to "kJ/mol", "kj/mol" to "kJ/mol"
    )
}
