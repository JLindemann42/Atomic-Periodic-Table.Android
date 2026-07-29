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
        "higher temperature than", "hotter than", "at a higher temperature than",
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
        "以上", "大于", "超过", "高于", "多于",                                  // zh
        "sopra", "al di sopra di", "acima de", "encima de", "por encima de", "bo", "sa itaas ng", "से ऊपर", "سے اوپر", "以上", "高于", "超过",
        // Only the romanised spellings of the Hindi and Urdu comparatives were listed, so "सोने से
        // अधिक घने" — "denser than gold" — carried no comparator at all.
        "से अधिक", "से ज्यादा", "سے زیادہ", "سے بڑھ کر"
    )

    /** "less than", in the sense of a numeric threshold. */
    val LESS = listOf(
        "less than", "lower than", "below", "under", "beneath", "at most", "smaller than",
        "lower temperature than", "colder than", "at a lower temperature than",
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
        "以下", "小于", "低于", "少于",                                          // zh
        "sotto", "al di sotto di", "abaixo de", "debajo de", "por debajo de", "onder", "sa ibaba ng", "से नीचे", "سے نیچے", "以下", "低于"
    )

    /** Superlatives asking for the largest value. */
    val MOST = listOf(
        "most", "highest", "largest", "greatest", "maximum", "max", "biggest", "heaviest",
        "densest", "hardest", "strongest", "hottest", "top",
        // "Which element conducts heat best" carried no superlative marker at all, so it fell
        // through to the dictionary. "Best" is a superlative in every language listed here.
        "best", "greatest of all",
        "bast", "am besten", "beste", "mejor", "meilleur", "migliore", "melhor",
        "pinakamahusay", "सबसे अच्छा", "بہترین", "最好", "最佳",
        "mest", "hogst", "storst", "tyngst", "tatast", "hardast", "starkast", "hetast",
        "meiste", "hochste", "grosste", "schwerste", "dichteste", "harteste",
        "mas", "mayor", "maximo", "mas alto", "mas pesado", "mas denso", "mas duro",
        "plus", "maximal", "le plus", "plus eleve", "plus lourd", "plus dense", "plus dur",
        "piu", "massimo", "piu alto", "piu pesante", "piu denso", "piu duro",
        "maior", "maximo", "mais alto", "mais pesado", "mais denso",
        "meeste", "hoogste", "grootste", "swaarste", "digste",
        // Filipino prefixes the superlative onto the adjective, producing one long token that no
        // suffix rule reaches from "pinaka" — so each form has to be listed.
        "pinaka", "pinakamataas", "pinakamabigat", "pinakamalaki",
        "pinakamadensidad", "pinakadensidad", "pinakamadense", "pinakamainit", "pinakamatigas",
        "sabse adhik", "sabse jyada", "sabse bhari", "uchchatam",
        "sab se ziyada", "sab se bara",
        "सबसे", "सबसे अधिक", "सबसे ज्यादा", "सबसे भारी",
        "سب سے", "سب سے زیادہ", "سب سے بڑا",
        "最高", "最大", "最重", "最密", "最硬", "最强",
        // Chinese ranks with 第N followed by the degree character — 第二大 is "second largest". The
        // rank and the direction are in the same word, so listing it here is what makes the query
        // carry a superlative at all; RANK_ORDINALS then reads the 第二 out of it.
        "第二大", "第三大", "第二高", "第三高", "第二重", "第三重",
        "更", "最", "pinaka", "زیادہ", "سب سے", "अधिक", "सबसे"
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
        "सबसे कम", "सबसे हल्का",
        "سب سے کم", "سب سے ہلکا",
        "最低", "最小", "最轻", "最软", "最弱"
    )

    /**
     * Particles that turn a superlative word into a comparison.
     *
     * Romance languages build both from the same word: French "le plus dense" is a superlative,
     * "plus dense **que** l'or" is a comparison. Without the particle the two are indistinguishable
     * and "which elements are denser than gold" was answered with a single densest element.
     */
    val THAN_WORDS = listOf(
        "than", "an", "som", "als", "que", "che", "do que", "kaysa", "se", "比",
        "se", "سے", "में", "kaysa sa", "dan",
        "से",
        // Afrikaans "as" is spelled the same as the Portuguese and Spanish feminine plural
        // article, and "e as suas densidades" is not a comparison. Listed with the comparative it
        // always follows instead, which is how Afrikaans actually builds one — "swaarder as",
        // "groter as" — so nothing is lost and the collision goes away.
        "er as", "ter as", "der as"
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
        "平均",
        "اوسط", "औसत", "平均"
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
        "总和", "总计", "合计",
        // Inflected and native-script forms. Only the stems were listed, so "die gesamte Atommasse"
        // and "कुल परमाणु द्रव्यमान" asked for a sum and were answered with a single element's mass.
        "gesamte", "gesamten", "totale", "totala", "totalt",
        "कुल", "کل", "总"
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
        "多少", "几个", "数量",
        "कितनه9", "کتنے", "cuantos hay", "combien y en a", "有多少",
        "کتنے", "کتنی", "کتنا", "کितने", "कितने", "कितनी", "有多少", "多少个",
        // Filipino inflects the counter itself — "ilan" becomes "ilang" before the noun, which is
        // the form every natural phrasing uses ("ilang noble gas ang mayroon").
        "ilang", "gaano karami"
    )

    /** The word "element" itself, for questions about the table rather than about an element. */
    val ELEMENT_WORDS = listOf(
        "element", "elements", "grundamne", "grundamnen", "element", "elemente",
        "elemento", "elementos", "elemenceptif", "elementi", "elemente",
        "तत्व", "عنصر", "元素",
        "عناصر", "عنصر", "तत्व", "तत्वों", "元素"
    )

    /** Phrases introducing a range, paired with the word joining its two bounds. */
    val BETWEEN_WORDS = listOf(
        "between", "from", "in the range",
        "mellan", "zwischen", "entre", "entre", "tra", "fra", "tussen", "sa pagitan",
        "के बीच", "کے درمیان", "之间"
    )

    /** Words joining the two bounds of a range. */
    val RANGE_JOINERS = listOf(
        "and", "to", "och", "till", "und", "bis", "y", "a", "et", "e", "en", "至", "和",
        // Filipino joins the bounds with "at", Hindi with "और", Urdu with "اور" and Chinese
        // colloquially with "到" — none of which were listed, so the upper bound was never found
        // and "between 5 and 10" became a bare lower bound.
        "at", "और", "اور", "到", "ate", "hasta", "fino a"
    )

    /** Negations that invert a subset filter. */
    val NEGATIONS = listOf(
        "not ", "non-", "non ", "aren't", "arent", "isn't", "isnt", "except", "excluding",
        "inte ", "icke-", "nicht ", "kein", "no ", "non ", "nao ", "nie ",
        "hindi ", "नहीं", "نہیں", "不", "非"
    )

    /** Ordinal words selecting a rank other than the first. */
    val RANK_ORDINALS: Map<String, Int> = mapOf(
        "second" to 2, "2nd" to 2, "andra" to 2, "zweit" to 2, "segundo" to 2, "deuxieme" to 2,
        "third" to 3, "3rd" to 3, "tredje" to 3, "dritt" to 3, "tercer" to 3, "troisieme" to 3,
        "fourth" to 4, "4th" to 4, "fjarde" to 4, "viert" to 4, "cuarto" to 4,
        "fifth" to 5, "5th" to 5, "femte" to 5, "funft" to 5, "quinto" to 5,
        "sixth" to 6, "6th" to 6, "seventh" to 7, "7th" to 7,
        "eighth" to 8, "8th" to 8, "ninth" to 9, "9th" to 9, "tenth" to 10, "10th" to 10,
        // German writes the rank as a compound — "das zweitdichteste Element" — and Chinese as a
        // numeral phrase. Neither had an entry, so "the second densest" ranked first.
        "zweitdichteste" to 2, "zweitgrosste" to 2, "zweithochste" to 2, "zweitschwerste" to 2,
        "第二" to 2, "第三" to 3, "第四" to 4, "第五" to 5,
        "segunda" to 2, "seconda" to 2, "segundo elemento" to 2, "tweede" to 2,
        "pangalawa" to 2, "pangalawang" to 2, "दूसरा" to 2, "दूसरे" to 2,
        "دوسرا" to 2, "دوسرے" to 2
    )

    /** Phrases asking for elements sharing a group or period with a named element. */
    val SAME_AS_WORDS = listOf(
        "same group as", "same column as", "same family as", "same period as", "same row as",
        "samma grupp som", "samma period som", "gleiche gruppe wie", "gleiche periode wie",
        "mismo grupo que", "mismo periodo que", "meme groupe que", "meme periode que",
        "stesso gruppo di", "mesmo grupo que", "同一族", "同一周期",
        // Case and article forms. German declines the adjective after a preposition ("in der
        // gleichen Gruppe wie"), Italian contracts the article into the preposition ("dello stesso
        // gruppo del"), and six languages had no entry — so the question resolved the element and
        // the word "group" and was answered with that element's own group number.
        "gleichen gruppe wie", "gleichen periode wie",
        "stesso gruppo del", "stesso gruppo dell", "stesso periodo del",
        "dieselfde groep as", "dieselfde periode as",
        "parehong grupo ng", "parehong periodo ng",
        "समान समूह", "उसी समूह", "समान आवर्त",
        "ہی گروپ", "اسی گروپ", "ہی دور",
        // Anaphoric forms. "What else is in that group", asked after establishing an element's
        // group, is the same question as "which elements are in the same group as iron" — and it
        // was answered by repeating that element's own group number.
        "in that group", "in that period", "in the same group", "in the same period",
        "else is in", "i den gruppen", "i samma grupp", "in dieser gruppe",
        "en ese grupo", "dans ce groupe", "in quel gruppo", "nesse grupo",
        "in daardie groep", "sa grupong iyon", "उस समूह में", "اس گروپ میں", "在那一族"
    )

    /**
     * The [SERIES_WORDS] surfaces that are plural.
     *
     * "What is a noble gas" asks for the definition; "what are the noble gases" asks for the
     * fifteen-odd members, and only the plural says which. Both open with a definitional frame, so
     * the frame alone routed every one of them to the dictionary — "wat is die edelgasse" and "ano
     * ang mga noble gas" were answered with a paragraph explaining what a noble gas is.
     *
     * Kept as an explicit set rather than a morphological rule, because the plural is formed nine
     * different ways across the twelve languages and two of them mark it with a separate particle.
     */
    val PLURAL_SERIES_WORDS = setOf(
        "noble gases", "adelgaser", "adelgaserna", "edelgase", "edelgasen", "edelgasse",
        "gases nobles", "gases nobres", "gas nobili", "gaz nobles", "mga noble gas",
        "उत्कृष्ट गैसें", "उत्कृष्ट गैसों", "نیک گیسیں", "نیک گیسوں", "惰性气体", "稀有气体",
        "halogens", "halogener", "halogene", "halogenes", "alogeni", "halogenos", "halogenios",
        "हैलोजन", "ہیلوجن", "卤素",
        "alkali metals", "alkalimetalle", "alkalimetaller", "alkalimetallerna", "alkalimetale",
        "metales alcalinos", "metaux alcalins", "metalli alcalini", "metais alcalinos",
        "mga alkali metal", "क्षार धातुओं", "الکلی دھاتوں", "碱金属",
        "alkaline earth metals",
        "transition metals", "oorgangsmetale", "metalli di transizione", "metais de transicao",
        "metaux de transition", "过渡金属",
        "lanthanides", "lanthanoids", "actinides", "metalloids", "nonmetals", "non-metals"
    )

    /**
     * Vocabulary that names one of the app's reference tables rather than an element property.
     *
     * The dataset fallback holds a deliberately high bar when the query names an element, because
     * the element is itself evidence the question is about the element — and relaxing that bar
     * wholesale was measured and rejected: it recovered five dataset answers and lost four
     * must-defer rows.
     *
     * These cues are the missing discriminator. "Standard electrode potential of zinc" names a
     * table the app ships and a topic no element field covers; "what is the weather today" names
     * neither, so the low bar can be granted on this evidence without letting chatter through.
     */
    val DATASET_TOPIC_CUES = listOf(
        "electrode potential", "reduction potential", "oxidation potential", "standard potential",
        "half cell", "half-cell",
        "solubility", "soluble", "insoluble", "dissolves in", "dissolve in",
        "elektrodpotential", "reduktionspotential", "loslighet", "losligt", "loslig",
        "elektrodenpotential", "loslichkeit", "loslich",
        "potencial de electrodo", "solubilidad", "soluble",
        "potentiel d electrode", "solubilite", "soluble",
        "potenziale di elettrodo", "solubilita", "solubile",
        "potencial de eletrodo", "solubilidade", "soluvel",
        "elektrodepotensiaal", "oplosbaarheid",
        "natutunaw sa tubig", "solubility ng",
        "घुलनशीलता", "इलेक्ट्रोड विभव",
        "حل پذیری", "الیکٹروڈ پوٹینشل",
        "溶解度", "电极电位", "标准电极电势"
    )

    /**
     * Localized names for topics that live only in the app's English reference tables.
     *
     * The tables are English — `DatasetIndex` says so — and BM25 is lexical, so a Swedish user
     * asking "vad är stål gjort av" retrieves nothing at all: the row is titled "Steel". The gloss
     * is appended to the *retrieval* query only; the answer is still composed in the user's
     * language and still carries the disclosure that the table itself is English.
     *
     * Small on purpose. It covers the topics users actually ask for by name, not the whole table.
     */
    val DATASET_GLOSS: Map<String, String> = mapOf(
        // Alloys
        "stal" to "steel", "stahl" to "steel", "acero" to "steel", "acier" to "steel",
        "acciaio" to "steel", "aco" to "steel", "staal" to "steel", "asero" to "steel",
        "इस्पात" to "steel", "فولاد" to "steel", "钢" to "steel",
        "massing" to "brass", "messing" to "brass", "laton" to "brass", "laiton" to "brass",
        "ottone" to "brass", "latao" to "brass", "黄铜" to "brass",
        "brons" to "bronze", "bronze" to "bronze", "bronce" to "bronze", "bronzo" to "bronze",
        "青铜" to "bronze",
        // Constants
        "elektronens massa" to "electron mass", "elektronmassa" to "electron mass",
        "elektronenmasse" to "electron mass", "masa del electron" to "electron mass",
        "masse de l electron" to "electron mass", "massa dell elettrone" to "electron mass",
        "massa do eletron" to "electron mass", "elektronmassa se" to "electron mass",
        "इलेक्ट्रॉन द्रव्यमान" to "electron mass", "الیکٹران کی کمیت" to "electron mass",
        "电子质量" to "electron mass",
        "protonens massa" to "proton mass", "protonenmasse" to "proton mass",
        "质子质量" to "proton mass",
        "ljusets hastighet" to "speed of light", "lichtgeschwindigkeit" to "speed of light",
        "velocidad de la luz" to "speed of light", "vitesse de la lumiere" to "speed of light",
        "velocita della luce" to "speed of light", "velocidade da luz" to "speed of light",
        "光速" to "speed of light",
        "gaskonstanten" to "gas constant", "allmanna gaskonstanten" to "gas constant",
        "constante des gaz" to "gas constant", "constante de los gases" to "gas constant",
        "气体常数" to "gas constant",
        "avogadros tal" to "avogadro", "avogadro zahl" to "avogadro",
        "numero de avogadro" to "avogadro", "阿伏伽德罗常数" to "avogadro"
    )

    /** Words asking for a median. */
    val MEDIAN = listOf(
        "median", "mediane", "mediana", "medianen", "mediaan", "中位数", "中位",
        "माध्यिका", "وسطی"
    )

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
        "कौन", "कौन सा", "कौन से", "कौनसा",
        "کون", "کون سا", "کونسا",
        "哪些", "哪个", "列出", "全部", "所有",
        "وسطی", "माध्यिका", "中位数",
        // Spanish and Urdu select with a word that cannot be listed bare. Spanish "qué" folds to
        // "que", which is also its comparison particle, and Urdu "کس" is a one-syllable oblique
        // that appears all over the language — so both are listed only in the phrases where they
        // unambiguously open a question about elements. Without these, "qué elemento tiene el
        // punto de ebullición más alto" and "کس عنصر کا نقطہ ابال سب سے زیادہ ہے" were declined
        // outright, while their "cuál"/"کون سا" synonyms worked.
        "que elemento", "que elementos", "کس عنصر", "کن عناصر",
        "فہرست", "सूची",
        // Imperative list openers. Only the noun form of each was listed ("elenco", "lista"), so
        // "elenca i metalli alcalini" and "ilista ang mga alkali metal" named a family and were
        // still not read as asking for its members.
        "elenca", "elencare", "ilista", "lys", "liste", "listar", "liste die", "auflisten",
        "mostrami", "enumera"
    )

    /**
     * Words that genuinely select *between* candidates.
     *
     * Deliberately not [WHICH], which contains "what" and is therefore true for "what is its
     * density" — using that as a selection signal turned every ordinary follow-up into a
     * comparison. These are the words that only appear when the user is choosing between things.
     */
    val SELECTIVE = listOf(
        "which", "whichever",
        "vilket", "vilken", "vilka",
        "welches", "welche", "welcher",
        "quel", "quelle", "lequel",
        "cual", "cuales",
        "quale", "quali",
        "qual", "quais",
        "watter", "alin", "aling",
        "kaun", "kaunsa", "konsa",
        "कौन", "कौन सा", "कौन से",
        "کون", "کون سا",
        "哪个", "哪种", "哪一个",
        "que elemento", "que elementos", "کس عنصر", "کن عناصر"
    )

    /** Subset names mapped to the series they select. */
    val SERIES_WORDS: Map<String, Set<SeriesId>> = mapOf(
        "gaz nobles" to setOf(SeriesId.NOBLE_GAS),
        "gaz noble" to setOf(SeriesId.NOBLE_GAS),
        "edelgasse" to setOf(SeriesId.NOBLE_GAS),
        "noble gas" to setOf(SeriesId.NOBLE_GAS),
        "mga noble gas" to setOf(SeriesId.NOBLE_GAS),
        "उत्कृष्ट गैस" to setOf(SeriesId.NOBLE_GAS),
        "उत्कृष्ट गैसें" to setOf(SeriesId.NOBLE_GAS),
        "نیک گیس" to setOf(SeriesId.NOBLE_GAS),
        "نیک گیسیں" to setOf(SeriesId.NOBLE_GAS),
        "oorgangsmetale" to setOf(SeriesId.TRANSITION_METAL),
        "metalli di transizione" to setOf(SeriesId.TRANSITION_METAL),
        "metais de transicao" to setOf(SeriesId.TRANSITION_METAL),
        "transition metals" to setOf(SeriesId.TRANSITION_METAL),
        "संक्रमण धातु" to setOf(SeriesId.TRANSITION_METAL),
        "منتقلی دھات" to setOf(SeriesId.TRANSITION_METAL),
        "过渡金属" to setOf(SeriesId.TRANSITION_METAL),
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
        // Swedish and German definite plurals. Subset names are matched on word boundaries, so
        // "ädelgaserna" — the form a Swedish speaker actually writes — does not match "adelgas"
        // and "Vilka är ädelgaserna?" was answered with a single-element dump instead of a list.
        "adelgaser" to setOf(SeriesId.NOBLE_GAS),
        "adelgaserna" to setOf(SeriesId.NOBLE_GAS),
        "edelgas" to setOf(SeriesId.NOBLE_GAS),
        "edelgase" to setOf(SeriesId.NOBLE_GAS),
        "edelgasen" to setOf(SeriesId.NOBLE_GAS),
        "edelgasse" to setOf(SeriesId.NOBLE_GAS),
        "gas noble" to setOf(SeriesId.NOBLE_GAS),
        "惰性气体" to setOf(SeriesId.NOBLE_GAS),
        "稀有气体" to setOf(SeriesId.NOBLE_GAS),
        "halogen" to setOf(SeriesId.HALOGEN),
        "halogens" to setOf(SeriesId.HALOGEN),
        "halogener" to setOf(SeriesId.HALOGEN),
        "halogene" to setOf(SeriesId.HALOGEN),
        "卤素" to setOf(SeriesId.HALOGEN),
        "镧系" to setOf(SeriesId.LANTHANOID),
        "镧系元素" to setOf(SeriesId.LANTHANOID),
        "锕系" to setOf(SeriesId.ACTINIDE),
        "lantanidi" to setOf(SeriesId.LANTHANOID),
        "lantanidos" to setOf(SeriesId.LANTHANOID),
        "lantanideos" to setOf(SeriesId.LANTHANOID),
        "lanthanoide" to setOf(SeriesId.LANTHANOID),
        "lanthanides" to setOf(SeriesId.LANTHANOID),
        "lantaniderna" to setOf(SeriesId.LANTHANOID),
        "lantaniede" to setOf(SeriesId.LANTHANOID),
        "लैंथेनाइड" to setOf(SeriesId.LANTHANOID),
        "لینتھانائیڈ" to setOf(SeriesId.LANTHANOID),
        "lanthanide" to setOf(SeriesId.LANTHANOID),
        "lanthanides" to setOf(SeriesId.LANTHANOID),
        "lanthanoid" to setOf(SeriesId.LANTHANOID),
        "lanthanoids" to setOf(SeriesId.LANTHANOID),
        "lantanid" to setOf(SeriesId.LANTHANOID),
        "actinide" to setOf(SeriesId.ACTINIDE),
        "actinides" to setOf(SeriesId.ACTINIDE),
        "aktinid" to setOf(SeriesId.ACTINIDE),
        // ---- Halogens and alkali metals, outside English -------------------------------------
        // Only the English and German spellings were listed, so "quali elementi sono alogeni" and
        // "क्षार धातुओं की सूची दें" resolved no subset and the whole question was declined. Every
        // one of these is the ordinary way to name the family in that language.
        // Plural family names. The singular was listed and the plural was not, which is the form
        // every "which elements are …" question uses.
        "gases nobles" to setOf(SeriesId.NOBLE_GAS),
        "gases nobres" to setOf(SeriesId.NOBLE_GAS),
        "gas nobili" to setOf(SeriesId.NOBLE_GAS),
        "gas nobile" to setOf(SeriesId.NOBLE_GAS),
        "gaz nobles" to setOf(SeriesId.NOBLE_GAS),
        "alogeni" to setOf(SeriesId.HALOGEN),
        "alogeno" to setOf(SeriesId.HALOGEN),
        "halogenos" to setOf(SeriesId.HALOGEN),
        "halogeno" to setOf(SeriesId.HALOGEN),
        "halogenios" to setOf(SeriesId.HALOGEN),
        "halogenes" to setOf(SeriesId.HALOGEN),
        "हैलोजन" to setOf(SeriesId.HALOGEN),
        "ہیلوجن" to setOf(SeriesId.HALOGEN),
        "alkalimetaller" to setOf(SeriesId.ALKALI_METAL),
        "alkalimetallerna" to setOf(SeriesId.ALKALI_METAL),
        "alkalimetalle" to setOf(SeriesId.ALKALI_METAL),
        "alkalimetale" to setOf(SeriesId.ALKALI_METAL),
        "metales alcalinos" to setOf(SeriesId.ALKALI_METAL),
        "metal alcalino" to setOf(SeriesId.ALKALI_METAL),
        "metaux alcalins" to setOf(SeriesId.ALKALI_METAL),
        "metal alcalin" to setOf(SeriesId.ALKALI_METAL),
        "metalli alcalini" to setOf(SeriesId.ALKALI_METAL),
        "metallo alcalino" to setOf(SeriesId.ALKALI_METAL),
        "metais alcalinos" to setOf(SeriesId.ALKALI_METAL),
        "alkali metal" to setOf(SeriesId.ALKALI_METAL),
        "mga alkali metal" to setOf(SeriesId.ALKALI_METAL),
        "क्षार धातु" to setOf(SeriesId.ALKALI_METAL),
        "क्षार धातुओं" to setOf(SeriesId.ALKALI_METAL),
        "الکلی دھات" to setOf(SeriesId.ALKALI_METAL),
        "الکلی دھاتوں" to setOf(SeriesId.ALKALI_METAL),
        "碱金属" to setOf(SeriesId.ALKALI_METAL),
        "उत्कृष्ट गैसों" to setOf(SeriesId.NOBLE_GAS),
        "نیک گیسوں" to setOf(SeriesId.NOBLE_GAS),
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
    /**
     * Small talk, quizzes and games — conversational turns the grounded engine must never claim.
     *
     * Without this the dataset-retrieval fallback answers them: "quiz me" scored high enough
     * against the constants table to be answered with the mass of an electron, and "tell me a
     * joke" and "write me a poem" went the same way. Retrieval has no way to know that a query
     * is not a question about chemistry at all, so the deny-list has to be explicit.
     */
    val DEFER_TO_PERSONALITY = listOf(
        // Quizzes, tests and games
        "quiz", "test me", "ask me", "play a game", "lets play", "let s play", "game",
        "fragesport", "fraga mig", "testa mig", "ge mig en fraga", "spel", "spela",
        "quiz mich", "teste mich", "spiel", "spielen",
        "interroge moi", "hazme una pregunta", "pregunta", "juego", "gioco", "jogo",
        "测验", "游戏", "quizmo", "toets", "pagsusulit",
        // Greetings, thanks and farewells
        "hello", "hi there", "hey", "good morning", "good afternoon", "good evening",
        "thanks", "thank you", "bye", "goodbye", "see you",
        "hej", "hejsan", "god morgon", "tack", "hej da",
        "hallo", "guten morgen", "guten tag", "danke", "tschuss", "auf wiedersehen",
        "bonjour", "merci", "au revoir", "hola", "gracias", "adios",
        "ciao", "grazie", "ola", "obrigado", "goeie more", "dankie",
        "kumusta", "salamat", "namaste", "dhanyavaad", "salam", "shukriya",
        "你好", "谢谢", "再见",
        // Jokes, facts and other chit-chat
        "joke", "fun fact", "tell me a fact", "give me a fact", "something interesting",
        "surprise me", "what can you do", "help me", "who are you",
        // Asking what the assistant can do is a question about the app, not about chemistry, and
        // the answer is the feature overview the personality layer holds. The grounded engine has
        // to decline it in every language or it never gets there — and it *will* claim it, because
        // "vilka funktioner har du" and "quali funzioni" both look like ordinary questions.
        "what do you do", "what are you able", "how can you help", "what can i ask",
        "what can you help", "how to use", "how do i use", "what features", "capabilities",
        "vad kan du gora", "vad kan du hjalpa", "vilka funktioner", "hur anvander jag",
        "was kannst du", "wobei kannst du", "welche funktionen", "wie benutze ich",
        "que puedes hacer", "en que puedes ayudar", "que funciones", "como se usa",
        "que peux-tu faire", "que sais-tu faire", "quelles fonctionnalites",
        "cosa sai fare", "cosa puoi fare", "quali funzioni", "come si usa",
        "o que voce pode fazer", "quais funcoes", "como usar",
        "wat kan jy doen", "watter funksies", "hoe gebruik ek",
        "ano ang kaya mong gawin", "ano ang magagawa mo", "paano gamitin",
        "क्या कर सकते हो", "आप क्या कर सकते हैं", "कौन सी सुविधाएं", "कैसे उपयोग करें",
        "کیا کر سکتے ہو", "آپ کیا کر سکتے ہیں", "کون سی خصوصیات", "کیسے استعمال",
        "你能做什么", "你会做什么", "你可以做什么", "有哪些功能", "怎么使用",
        "skamt", "kul fakta", "roligt", "berätta nagot intressant", "vad kan du gora", "hjalp",
        "witz", "spass", "was kannst du", "hilfe",
        "blague", "chiste", "barzelletta", "piada", "grap",
        "笑话", "有趣"
    )

    /**
     * Questions about usage, discovery, origin and biological role.
     *
     * These are narrative: the answer is prose drawn from the element's description, not a stored
     * field. They matter here because of what happens when they are *not* recognised — the query
     * names an element, resolves no field, and slot inheritance hands it whatever field the
     * previous turn used. That is what answered "Vad används titan till" with titanium's atomic
     * number, and "Användning av Titan" with its crustal abundance.
     *
     * `discovered_by` and `year_discovered` are deliberately absent: those *are* stored fields,
     * and are handled by the resolver's cognate list instead.
     */
    val NARRATIVE_INTENTS = listOf(
        "used for", "use of", "uses of", "usage", "used in", "used to", "application",
        "applications", "what is it for", "good for",
        // Swedish and German put the subject between the verb and its particle — "vad *används
        // titan till*" — so the two-word phrase never appears contiguously. The verb alone is a
        // safe marker: "används" and "verwendet" occur in little else.
        "anvandning", "anvands", "anvander", "anvandningsomraden",
        "verwendung", "verwendet", "wofur wird", "anwendung",
        "utilisation", "utilise", "sert a", "usage",
        "uso", "usos", "se usa", "utiliza", "para que sirve", "serve",
        "utilizzo", "usato", "gebruik", "ginagamit",
        "upyog", "istemal", "用途", "用于",
        // Biological role
        "biological role", "in the body", "for the body", "essential for life",
        "biologisk roll", "i kroppen", "biologische rolle", "role biologique",
        "papel biologico", "ruolo biologico", "生物作用",
        // Etymology and naming
        "name come from", "come from", "named after", "why is it called", "origin of the name",
        "etymology",
        "varifran kommer namnet", "namnet kommer", "varfor kallas", "uppkallad efter",
        "woher kommt der name", "benannt nach", "warum heisst",
        "origine du nom", "origen del nombre", "origine del nome", "名字的由来",
        "come from", "comes from", "came from", "origin of the name", "name origin",
        "etymology", "etymologically", "named after", "why is it called", "where does the name",
        "etymologi", "namnets ursprung", "uppkallad efter", "herkunft des namens",
        "etymologie", "etimologia", "origen del nombre"
    )

    /**
     * Discourse markers that introduce a second, independent question.
     *
     * "…och sen densitet av väte" and "…Furthermore, what is the density of iron" are both second
     * questions, but neither tail opens with a question word, so the splitter left the whole query
     * whole and it was planned as a two-element comparison.
     */
    val DISCOURSE_MARKERS = listOf(
        "furthermore", "additionally", "in addition", "moreover", "besides", "next",
        "sen", "sedan", "dessutom", "vidare", "darefter",
        "ausserdem", "zusatzlich", "weiterhin", "danach",
        "de plus", "en outre", "ensuite", "puis",
        "ademas", "adicionalmente", "luego", "despues",
        "inoltre", "poi", "alem disso", "depois",
        "verder", "bukod pa", "iske alawa", "此外", "另外", "然后"
    )

    val UNBACKED_CONCEPTS = listOf(
        // Reactivity is scored from group and position, not stored.
        "reactive", "reactivity", "reaktiv", "reaktivitet", "reaktivaste", "reaktivitat",
        "reactivo", "reactividad", "reactif", "reactivite", "reattivo", "reattivita",
        "reativo", "reatividade", "reaktief", "reaktibo", "活泼", "反应性",
        // What happens when substances react is chemistry, not a field lookup. Without this,
        // "sodium and chlorine react" names two elements and would be answered as a
        // side-by-side property comparison instead.
        "react", "reaction", "reagera", "reaktion", "reagieren", "reagir", "reaccionar",
        "reagire", "reazione", "reaccion", "reaction", "प्रतिक्रिया", "अभिक्रिया",
        "radd-e-amal", "反应", "fanying", "reaksyon",
        // Similarity is a judgement over several properties, not a stored value.
        "similar", "liknar", "liknande", "ahnlich", "similaire", "parecido", "simile",
        "semelhante", "soortgelyk", "katulad", "समान", "相似"
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
        // Agreement forms and the six languages with no entry at all. "Propiedades mecánicas" is
        // how the question is actually asked; only the masculine singular "mecánico" was listed.
        "mecanicas" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "mecanicos" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "meccaniche" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "meccanico" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "meganiese" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "mekanikal" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "यांत्रिक" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,
        "میکانکی" to com.jlindemann.science.ai.data.FieldCategory.MECHANICAL,

        // The identity block — symbol, number, name, family. "Identity of gold" named the category
        // the registry already has and resolved nothing, because no word mapped to it.
        "identity" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identification" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identitet" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identitat" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identidad" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identite" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identita" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "identidade" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "पहचान" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "شناخت" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,
        "身份" to com.jlindemann.science.ai.data.FieldCategory.IDENTITY,

        "atomic properties" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,
        "atomara" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,
        "atomare" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,
        "原子性质" to com.jlindemann.science.ai.data.FieldCategory.ATOMIC,

        "crystal properties" to com.jlindemann.science.ai.data.FieldCategory.CRYSTAL,
        "nuclear properties" to com.jlindemann.science.ai.data.FieldCategory.NUCLEAR,

        "crystallographic" to com.jlindemann.science.ai.data.FieldCategory.CRYSTAL,
        "kristallografisk" to com.jlindemann.science.ai.data.FieldCategory.CRYSTAL,

        "abundance" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "where is it found" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "where is" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        // "where is gold found" already worked; these are the phrasings that did not, and each was
        // left unplannable rather than answered wrongly — the engine declined and the narrative
        // handler produced an overview instead of the nine abundance figures.
        "where does" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "where can" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "how common" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "how abundant" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "occur" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "forekomst" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "var finns" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "vart finns" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "hur vanligt" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "vorkommen" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "wo kommt" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "wo findet" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "abundancia" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "donde se encuentra" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "abondance" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "ou trouve" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "onde se encontra" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "dove si trova" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "丰度" to com.jlindemann.science.ai.data.FieldCategory.ABUNDANCE,
        "propiedades termicas" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "proprieta termiche" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "propriedades termicas" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "termiese eienskappe" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "thermal properties" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "तापीय गुण" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "حرارتی خصوصیات" to com.jlindemann.science.ai.data.FieldCategory.THERMO,
        "热学性质" to com.jlindemann.science.ai.data.FieldCategory.THERMO
    )

    /** Words that ask for a narrative overview, which the engine leaves to the personality layer. */
    val OVERVIEW_WORDS = listOf(
        "tell me about", "what is", "who is", "describe", "overview", "summary", "explain",
        "berätta om", "beratta om", "vad ar", "erzahl mir", "was ist", "beschreibe",
        "hablame de", "que es", "parle moi de", "qu est ce que", "parlami di", "cos e",
        "fale sobre", "o que e", "vertel my van", "wat is", "sabihin mo sa akin",
        "batao", "bataye", "介绍", "是什么"
    )

    /** Words asking about an element's isotopes. */
    val ISOTOPE_WORDS = listOf(
        "isotope", "isotopes", "half life", "half-life", "halflife", "nuclide", "decay",
        "isotop", "isotoper", "halveringstid", "sonderfall",
        "isotope", "halbwertszeit", "zerfall",
        "isotopo", "isotopos", "vida media", "desintegracion",
        "isotope", "demi-vie", "desintegration",
        "isotopi", "tempo di dimezzamento", "decadimento",
        "isotopos", "meia-vida", "decaimento",
        "isotoop", "halfleeftyd", "isotopo", "kalahating buhay",
        "समस्थानिक", "अर्ध आयु", "آاइسوٹوپ", "نصف زندگی",
        "同位素", "半衰期", "衰变",
        // "How long does plutonium 239 last" is a half-life question that never says "half-life".
        // The frame is what carries it; the query resolved no field and was declined outright.
        "how long does", "how long is", "how long do", "how long will",
        "hur lange", "wie lange", "combien de temps", "cuanto dura", "quanto dura",
        "quanto tempo", "hoe lank", "gaano katagal", "कितने समय", "کتنی دیر", "能持续多久",
        "stable", "unstable", "stability", "decays",
        "stabil", "stabilt", "instabil", "sonderfallt", "zerfallt", "estable", "stabile",
        "isotopi", "isotopo", "isotopos", "isotopen", "isotope", "समस्थानिक", "آئسوٹوپ", "同位素"
    )

    /** Words asking whether an element is hazardous. */
    val SAFETY_WORDS = listOf(
        "safe", "safety", "danger", "dangerous", "hazard", "hazardous", "toxic", "toxicity",
        "poisonous", "harmful", "nfpa", "flammable", "corrosive",
        "saker", "sakerhet", "farlig", "fara", "giftig", "brandfarlig",
        "sicher", "sicherheit", "gefahrlich", "gefahr", "giftig", "entzundlich",
        "seguro", "seguridad", "peligroso", "peligro", "toxico", "inflamable",
        "sur", "securite", "dangereux", "danger", "toxique", "inflammable",
        "sicuro", "sicurezza", "pericoloso", "pericolo", "tossico", "infiammabile",
        "seguro", "seguranca", "perigoso", "perigo", "toxico", "inflamavel",
        "veilig", "gevaarlik", "giftig", "ligtas", "mapanganib", "nakakalason",
        "खतरनाक", "जहरीला", "सुरक्षित", "خطرناک", "زہریلا", "محفوظ",
        "危险", "有毒", "安全", "易燃",
        // "what are the risks of handling caesium" is a safety question that named no hazard word,
        // so it was declined outright rather than answered from the NFPA ratings.
        "risk", "risks", "risker", "riesgo", "riesgos", "risiko", "risque", "rischio", "risco",
        "风险",
        "危险", "有毒", "安全吗", "خطرناک", "زہریلا",
        "farligt", "gefahrliche", "pericoloso", "pericolosa", "perigoso", "perigosa", "gevaarlik", "delikado", "mapanganib", "खतरनाक", "خطرناک", "危险吗", "有毒吗"
    )

    /**
     * Words asking to see an element's emission spectrum.
     *
     * Its own shape rather than a field: the app stores the spectrum as an image per element and
     * holds no wavelengths at all, so there is nothing to look up and the answer is the picture.
     * Without this, "what does the emission spectrum of iron look like" resolved `appearance` and
     * described the metal's colour.
     */
    val SPECTRUM_WORDS = listOf(
        "emission spectrum", "emission spectra", "emission line", "emission lines",
        "spectral line", "spectral lines", "spectrum", "spectra",
        "emissionsspektrum", "emissionslinjer", "spektrallinjer", "spektrum",
        "emissionsspektren", "spektrallinien", "spektrallinien",
        "espectro de emision", "lineas espectrales", "espectro",
        "spectre d emission", "raies spectrales", "spectre",
        "spettro di emissione", "righe spettrali", "spettro",
        "espectro de emissao", "linhas espectrais",
        "emissiespektrum", "spektraallyne",
        "espektro ng emisyon", "mga linya ng spektrum",
        "उत्सर्जन स्पेक्ट्रम", "स्पेक्ट्रम", "वर्णक्रम",
        "اخراجی طیف", "طیف", "طیفی لکیریں",
        "发射光谱", "光谱", "谱线"
    )

    /** Words asking for a formula's molar mass. */
    val MOLAR_MASS_WORDS = listOf(
        "molar mass", "molecular mass", "molecular weight", "formula mass", "formula weight",
        "molmassa", "molmasse", "molare masse", "massa molare", "masa molar", "masse molaire",
        "massa molar", "molere massa", "molar na masa", "मोलर द्रव्यमान", "مولر ماس", "摩尔质量"
    )

    /** Words asking for the percentage breakdown of a compound. */
    val COMPOSITION_WORDS = listOf(
        "percentage composition", "percent composition", "composition of", "mass percent",
        "percentage of", "made up of", "sammansattning", "zusammensetzung",
        "composicion", "composition", "composizione", "composicao", "samestelling",
        "komposisyon", "संघटन", "ترکیب", "组成", "百分比组成"
    )

    /** Words asking about neutron counts. */
    val NEUTRON_WORDS = listOf(
        "neutron", "neutrons", "neutroner", "neutronen", "neutrones", "neutroni",
        "neutrões", "neutrone", "न्यूट्रॉन", "نیوٹران", "中子"
    )

    /** Words asking about moles or particle counts. */
    val MOLE_WORDS = listOf(
        "mole", "moles", "mol", "avogadro", "atoms in", "particles",
        "molekyler", "teilchen", "moles", "molecole", "मोल", "مول", "摩尔", "阿伏伽德罗",
        // Plurals the languages actually write: Italian "2 moli di carbonio", Portuguese "2 mols".
        "moli", "mols", "mole di", "atomi in", "atomos em", "atome in"
    )

    /** Compounds users name in words rather than writing the formula. */
    val COMMON_COMPOUNDS: Map<String, String> = mapOf(
        "water" to "H2O", "vatten" to "H2O", "wasser" to "H2O", "agua" to "H2O",
        "eau" to "H2O", "acqua" to "H2O", "水" to "H2O",
        "carbon dioxide" to "CO2", "koldioxid" to "CO2", "kohlendioxid" to "CO2",
        "table salt" to "NaCl", "salt" to "NaCl", "koksalt" to "NaCl",
        "sulfuric acid" to "H2SO4", "svavelsyra" to "H2SO4", "schwefelsaure" to "H2SO4",
        "ammonia" to "NH3", "ammoniak" to "NH3",
        "methane" to "CH4", "metan" to "CH4",
        "glucose" to "C6H12O6", "glukos" to "C6H12O6",
        "hydrochloric acid" to "HCl", "saltsyra" to "HCl"
    )

    /** Openers marking a question as asking for an explanation rather than a value. */
    val WHY_WORDS = listOf(
        "why", "how come", "explain", "reason",
        "varfor", "forklara", "warum", "wieso", "erklare",
        "por que", "porque", "explica", "pourquoi", "explique",
        "perche", "spiega", "por que", "explique", "hoekom", "verduidelik",
        "bakit", "ipaliwanag", "क्यों", "समझाओ", "کیوں", "وضاحت", "为什么", "解释"
    )

    /** Frames marking a question as asking for a definition. */
    val DEFINITION_FRAMES = listOf(
        "what is a", "what is an", "what are", "what is the", "define", "definition of",
        "vad ar en", "vad ar ett", "vad ar", "was ist ein", "was ist eine", "was ist",
        "que es un", "que es una", "que es", "qu est ce qu", "cos e un", "cos e",
        "o que e", "wat is n", "wat is", "ano ang", "क्या है", "کیا ہے", "什么是", "是什么",
        // French elides and hyphenates its definitional frame, and the tokenizer splits on both —
        // so "qu'est-ce que l'électronégativité" never matched the spaced spelling above and every
        // French "what is a …" was declined.
        "qu'est-ce que", "qu'est ce que", "qu est ce que", "qu'est-ce qu", "c'est quoi"
    )

    /**
     * Concepts a "why" question is really about, mapped to the dictionary entry that explains
     * them. Without this the plain "Atomic radius" definition outranks the trend entry that
     * actually answers "why does atomic radius increase down a group".
     */
    val EXPLANATION_TOPICS: Map<String, String> = mapOf(
        "atomic radius" to "Periodic trend: atomic radius",
        "atomic size" to "Periodic trend: atomic radius",
        "ionization energy" to "Periodic trend: ionization energy",
        "ionisation energy" to "Periodic trend: ionization energy",
        "electronegativity" to "Periodic trend: electronegativity",
        "electronegative" to "Periodic trend: electronegativity",
        "reactive" to "Periodic trend: reactivity",
        "reactivity" to "Periodic trend: reactivity",
        "metallic character" to "Metallic character",
        "alkali metal" to "Alkali metal",
        "noble gas" to "Noble gas",
        "inert" to "Noble gas",
        "halogen" to "Halogen",
        "transition metal" to "Transition metal",
        "lanthanide" to "Lanthanide",
        "actinide" to "Actinide",
        "4s" to "Aufbau principle",
        "3d" to "Aufbau principle",
        "aufbau" to "Aufbau principle",
        "hund" to "Hund's rule",
        "pauli" to "Pauli exclusion principle",
        "electron configuration unusual" to "Anomalous electron configuration",
        "configuration unusual" to "Anomalous electron configuration",
        "chromium" to "Anomalous electron configuration",
        "synthetic" to "Synthetic element",
        "valence electron" to "Valence electrons",
        "oxidation state" to "Oxidation state",
        "mass number" to "Mass number",
        "atomic number" to "Atomic number",
        "atomic mass" to "Atomic mass",

        // ---- Other languages ------------------------------------------------------------------
        // The dictionary rows themselves are English — `DatasetIndex` says so — but the *topic* a
        // question names has to be recognised in the language it was asked in, or the question is
        // declined outright. "Vad är masstal" and "was ist Elektronegativität" resolved nothing and
        // fell through to a vague deflection, which is worse than an English definition.
        //
        // Only the concepts users actually ask to have explained, in the languages whose spelling
        // differs from English. The composer discloses that the definition itself is English.
        "masstal" to "Mass number",
        "massenzahl" to "Mass number",
        "numero de masa" to "Mass number",
        "nombre de masse" to "Mass number",
        "numero di massa" to "Mass number",
        "numero de massa" to "Mass number",
        "质量数" to "Mass number",
        "atomnummer" to "Atomic number",
        "ordnungszahl" to "Atomic number",
        "numero atomico" to "Atomic number",
        "numero atomique" to "Atomic number",
        "原子序数" to "Atomic number",
        "atommassa" to "Atomic mass",
        "atommasse" to "Atomic mass",
        "masa atomica" to "Atomic mass",
        "massa atomica" to "Atomic mass",
        "masse atomique" to "Atomic mass",
        "原子质量" to "Atomic mass",
        "elektronegativitet" to "Periodic trend: electronegativity",
        "elektronegativitat" to "Periodic trend: electronegativity",
        "electronegatividad" to "Periodic trend: electronegativity",
        "electronegativite" to "Periodic trend: electronegativity",
        "elettronegativita" to "Periodic trend: electronegativity",
        "eletronegatividade" to "Periodic trend: electronegativity",
        "电负性" to "Periodic trend: electronegativity",
        "atomradie" to "Periodic trend: atomic radius",
        "atomradius" to "Periodic trend: atomic radius",
        "radio atomico" to "Periodic trend: atomic radius",
        "rayon atomique" to "Periodic trend: atomic radius",
        "raggio atomico" to "Periodic trend: atomic radius",
        "原子半径" to "Periodic trend: atomic radius",
        "joniseringsenergi" to "Periodic trend: ionization energy",
        "ionisierungsenergie" to "Periodic trend: ionization energy",
        "energia de ionizacion" to "Periodic trend: ionization energy",
        "energie d ionisation" to "Periodic trend: ionization energy",
        "电离能" to "Periodic trend: ionization energy",
        "isotop" to "Isotope",
        "isotope" to "Isotope",
        "isotopo" to "Isotope",
        "同位素" to "Isotope",
        "adelgas" to "Noble gas",
        "edelgas" to "Noble gas",
        "gas noble" to "Noble gas",
        "gaz noble" to "Noble gas",
        "惰性气体" to "Noble gas",
        "halogen" to "Halogen",
        "halogene" to "Halogen",
        "halogeno" to "Halogen",
        "卤素" to "Halogen",
        "alkalimetall" to "Alkali metal",
        "metal alcalino" to "Alkali metal",
        "metal alcalin" to "Alkali metal",
        "碱金属" to "Alkali metal",
        "overgangsmetall" to "Transition metal",
        "ubergangsmetall" to "Transition metal",
        "metal de transicion" to "Transition metal",
        "过渡金属" to "Transition metal",
        "oxidationstal" to "Oxidation state",
        "oxidationszahl" to "Oxidation state",
        "estado de oxidacion" to "Oxidation state",
        "氧化态" to "Oxidation state",
        "valenselektron" to "Valence electrons",
        "valenzelektron" to "Valence electrons",
        "electron de valencia" to "Valence electrons",
        "价电子" to "Valence electrons",
        "elektronkonfiguration" to "Anomalous electron configuration",
        "elektronenkonfiguration" to "Anomalous electron configuration"
    )

    /**
     * Comparative adjectives and the field each implies, with the direction they point.
     *
     * "Is gold denser than lead" names no property explicitly; the adjective is the property.
     * The boolean is true when the adjective means "greater value".
     */
    val COMPARATIVE_ADJECTIVES: Map<String, Pair<String, Boolean>> = mapOf(
        "denser" to ("density" to true),
        "more dense" to ("density" to true),
        "less dense" to ("density" to false),
        "lighter" to ("density" to false),
        "heavier" to ("atomic_mass" to true),
        "more massive" to ("atomic_mass" to true),
        "harder" to ("mohs_hardness" to true),
        "softer" to ("mohs_hardness" to false),
        "hotter" to ("melting_point" to true),
        "more electronegative" to ("electronegativity" to true),
        "less electronegative" to ("electronegativity" to false),
        "bigger" to ("atomic_radius" to true),
        "larger" to ("atomic_radius" to true),
        "smaller" to ("atomic_radius" to false),
        // Positive-degree forms ("dense", "tät", "dicht") are deliberately *not* here. They name the
        // property, not a comparison, so "how dense is osmium" is a plain lookup — listing them as
        // comparatives turned it into a filtered list of everything denser. They live in
        // FieldResolver's cognates instead.
        "storre" to ("atomic_radius" to true),
        "grosser" to ("atomic_radius" to true),
        "tatare" to ("density" to true),
        "tyngre" to ("atomic_mass" to true),
        "lattare" to ("density" to false),
        "dichter" to ("density" to true),
        "schwerer" to ("atomic_mass" to true),
        "leichter" to ("density" to false),
        "mas denso" to ("density" to true),
        "mas pesado" to ("atomic_mass" to true),
        "plus dense" to ("density" to true),
        // The plural periphrastic comparatives. "Quali elementi sono più densi dell'oro" and "quais
        // elementos são mais densos que o ouro" ask for every element above a threshold set by one
        // named element, and neither the singular forms above nor a bare degree word reach them.
        "piu denso" to ("density" to true),
        "piu densi" to ("density" to true),
        "mais denso" to ("density" to true),
        "mais densos" to ("density" to true),
        "mas densos" to ("density" to true),
        "plus denses" to ("density" to true),
        "mas madensidad" to ("density" to true),
        "plus lourd" to ("atomic_mass" to true),
        "更重" to ("atomic_mass" to true),
        "更密" to ("density" to true),
        "tatare" to ("density" to true),
        "dichter" to ("density" to true),
        "digter" to ("density" to true),
        "tyngre" to ("atomic_mass" to true),
        "schwerer" to ("atomic_mass" to true),
        "swaarder" to ("atomic_mass" to true),
        "lattare" to ("density" to false),
        "leichter" to ("density" to false),
        // Chinese marks the comparison with the particle 比 and leaves the adjective bare, so the
        // "更X" forms above never appear in the phrasing users actually write: 铅比铁重吗 is
        // "is lead heavier than iron". Without the bare adjective the query named two elements and
        // no field, and was answered with a five-row side-by-side table instead of yes or no.
        // Kept to the two adjectives the corpus actually exercises. 大 and 小 would be the obvious
        // next pair, but they are also the second character of 最大/最小, so admitting them
        // untested risks reading every Chinese superlative as a comparison.
        "重" to ("atomic_mass" to true),
        "轻" to ("density" to false),
        // Romance and Filipino "heavier". Spanish and French were listed; Italian, Portuguese and
        // Filipino were not, so "è più pesante dell'argento" was read as a superlative over the
        // whole table and "mas mabigat ba ito kaysa sa pilak" as a filtered list.
        "piu pesante" to ("atomic_mass" to true),
        "mais pesado" to ("atomic_mass" to true),
        "mais pesada" to ("atomic_mass" to true),
        "mas mabigat" to ("atomic_mass" to true),
        // Verb phrasings of a comparison. "Which melts first, iron or copper" names the field with
        // a verb and the *direction* with an adverb — and the direction is not the obvious one:
        // melting first means the **lower** melting point. Without these the question was answered
        // with a side-by-side table instead of a winner, and a naive fix would have named the wrong
        // one.
        "melts first" to ("melting_point" to false),
        "melts sooner" to ("melting_point" to false),
        "melts last" to ("melting_point" to true),
        "boils first" to ("boiling_point" to false),
        "boils last" to ("boiling_point" to true),
        "conducts heat better" to ("thermal_conductivity" to true),
        "conducts heat best" to ("thermal_conductivity" to true),
        "conducts electricity better" to ("electrical_conductivity" to true),
        "piu leggero" to ("density" to false),
        "mais leve" to ("density" to false),
        "mas magaan" to ("density" to false),
        "swaarder" to ("atomic_mass" to true),
        "digter" to ("density" to true)
    )

    /** Openers marking a yes/no question. */
    val YESNO_OPENERS = listOf(
        "is ", "are ", "does ", "do ", "can ", "has ", "have ",
        "ar ", "har ", "kan ", "ist ", "sind ", "hat ", "kann ",
        "es ", "son ", "tiene ", "est ", "sont ", "sono ",
        // Accented, and matched against the *raw* query rather than the folded one. Italian "è"
        // and Portuguese "é" mean "is"; their unaccented "e" means "and". Folding erases the only
        // difference, so a bare "e " opener turned every "e le loro densità" — an ordinary
        // follow-up — into a yes/no comparison of the two elements before it.
        "è ", "é ",
        "是", "有", "能"
    )

    /** Phrases asking which of two named things wins on some measure. */
    val WHICH_OF_TWO = listOf(
        "which is", "which one is", "which has", "which of", "who is",
        "vilken ar", "vilket ar", "welche ist", "welches ist",
        "cual es", "quel est", "quale e", "qual e", "watter is", "alin ang",
        "कौन सा", "کون سا", "哪个",
        // "How does that compare to iron" is a request for a winner, not for a table. It named no
        // comparative adjective and no "which", so it was answered side-by-side.
        "how does that compare", "how does it compare", "how do they compare",
        "hur star sig", "wie schneidet", "comment se compare", "como se compara",
        "come si confronta", "hoe vergelyk", "如何比较"
    )

    /** Phrases asking by how much two things differ. */
    val BY_HOW_MUCH = listOf(
        "how much", "how many times", "what factor", "ratio of", "difference between",
        "hur mycket", "hur manga ganger", "wie viel", "um wie viel",
        "cuanto", "combien", "quanto", "quanto mais", "hoeveel", "gaano",
        "कितना", "کتنا", "多少倍", "相差"
    )

    /** Phrases asking for the element next to another in the table. */
    val NEIGHBOUR_WORDS: Map<String, Int> = mapOf(
        "comes after" to 1, "come after" to 1, "after" to 1, "next after" to 1,
        "follows" to 1, "next element" to 1,
        "comes before" to -1, "come before" to -1, "before" to -1, "precedes" to -1,
        "kommer efter" to 1, "kommer fore" to -1,
        "kommt nach" to 1, "kommt vor" to -1,
        "despues de" to 1, "antes de" to -1,
        "apres" to 1, "avant" to -1,
        "dopo" to 1, "prima di" to -1, "prima del" to -1, "prima dell" to -1,
        "之后" to 1, "之前" to -1,
        // The six languages with no entry at all. A neighbour question resolved one element and no
        // field, so it inherited the previous turn's property and answered something else entirely.
        "depois de" to 1, "depois do" to 1, "vem depois" to 1,
        "antes do" to -1, "vem antes" to -1,
        "kom na" to 1, "kom voor" to -1,
        "kasunod ng" to 1, "kasunod na" to 1, "nauna sa" to -1, "bago ang" to -1,
        "के बाद" to 1, "से पहले" to -1,
        "کے بعد" to 1, "سے پہلے" to -1
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
        "比较", "对比", "区别",
        "比较", "对比", "比一比"
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
        "gas" to "gas", "gases" to "gas", "gasses" to "gas", "gaseous" to "gas",
        "gasformig" to "gas", "gasformigt" to "gas", "gaser" to "gas", "gase" to "gas",
        "gaseoso" to "gas", "gazeux" to "gas", "gassoso" to "gas", "气体" to "gas",
        "solids" to "solid", "liquids" to "liquid",
        // Plural and native-script forms. "Watter elemente is gasse" fell through to dataset
        // retrieval, and the Hindi and Urdu phrasings resolved nothing at all.
        "gasse" to "gas", "gaz" to "gas", "gases" to "gas", "गैस" to "gas", "گیس" to "gas",
        "vloeibaar" to "liquid", "द्रव" to "liquid", "مائع" to "liquid",
        "vaste" to "solid", "ठोस" to "solid", "ٹھوس" to "solid"
    )

    /** Phrases restricting a query to elements that occur in nature. */
    val NATURAL_WORDS = listOf(
        "naturally occurring", "natural", "occurs naturally", "occur naturally", "found in nature",
        "occur in nature", "exist in nature", "non-synthetic",
        "naturligt", "naturlig", "naturlich", "natural", "naturel", "naturale",
        "natuurlik", "natural", "प्राकृतिक", "قدرتی", "天然"
    )

    /**
     * Cues for "which elements have a value recorded for this field at all".
     *
     * This is a question about coverage rather than about chemistry, and it is worth answering
     * honestly: several fields are recorded for a handful of elements, and a reader who asks is
     * better served by "17 of 118" than by silence.
     */
    val HAS_VALUE_WORDS = listOf(
        "have a", "have an", "have any", "has a", "has an", "have data", "have values",
        "recorded", "known", "measured", "listed",
        "har ett", "har en", "har nagot", "haben ein", "haben einen", "ont un", "ont une",
        "tienen un", "tienen una", "hanno un", "tem um", "het n"
    )

    /**
     * Anaphors that refer to *several* subjects at once.
     *
     * "And their melting points" after comparing gold and silver is a question about both, and
     * inheriting only the focus element answered it about one — the single most common way a
     * comparison thread quietly narrowed to a lookup.
     */
    val PLURAL_ANAPHORS = listOf(
        "their", "them", "these", "those", "both", "all three", "each of them", "the two",
        "deras", "dem", "bada", "de tva", "ihre", "ihnen", "beide", "leurs", "eux", "les deux",
        "sus", "ambos", "los dos", "loro", "entrambi", "seus", "ambos os", "他们", "两者",
        // "suas" was in ANAPHORS but not here, so the Portuguese feminine plural possessive —
        // "e as suas densidades" — inherited only the focus element and answered half the question.
        "suas", "le loro", "as suas", "os seus", "sus dos",
        "i loro", "hul", "hulle", "kanilang", "nila", "उनके", "उनकी", "ان کے", "ان کی", "它们的", "两者",
        // "Compare the two" is how a thread asks for its two subjects side by side, and only the
        // English, Swedish, French and Spanish spellings were listed — so the same turn in the
        // other eight languages resolved no subject at all and was declined.
        "die beiden", "die twee", "i due", "os dois", "as duas", "ang dalawa", "de tvaa",
        "दोनों", "دونوں", "这两个", "这两种", "两个"
    )

    /**
     * Frames meaning "the same question, about this instead".
     *
     * These have to be named rather than inferred, for two reasons that pull in opposite
     * directions. German "was ist mit Kupfer" opens with the same two words as "was ist Kupfer",
     * so the overview guard declined it as a request for narrative — it is the *"mit"* that makes
     * it a follow-up. And Filipino "paano naman" is built from words that appear nowhere in the
     * corpus, so the unknown-term test read it as a change of subject.
     */
    val WHAT_ABOUT_FRAMES = listOf(
        "what about", "how about", "and what about",
        "hur ar det med", "vad sags om",
        "was ist mit", "wie sieht es mit", "und wie ist es mit",
        "que hay de", "y que hay de",
        "qu en est il de", "et pour",
        "che mi dici di", "e per quanto riguarda",
        "e quanto a", "e sobre",
        "wat van", "hoe lyk dit met",
        "paano naman", "kumusta naman",
        "के बारे में", "کے بارے میں",
        // Chinese marks the whole construction with one sentence-final particle — 铜呢 *is*
        // "what about copper" — and it appears in nothing else, so it is safe on its own.
        "呢"
    )

    /**
     * Cues asking for the size of a difference already established.
     *
     * "By how much" is a complete question only in context: it means "quantify the comparison you
     * just made", and it names neither subject nor field.
     */
    val MARGIN_WORDS = listOf(
        "by how much", "how much more", "how much bigger", "how much larger", "what is the difference",
        "the difference", "how big is the gap",
        "hur mycket", "hur stor ar skillnaden", "skillnaden", "um wie viel", "der unterschied",
        "de combien", "la difference", "por cuanto", "la diferencia", "di quanto", "la differenza",
        "por quanto", "a diferenca",
        // The languages that ask for the margin without any of the phrases above. Chinese 相差多少
        // is literally "differ how much", and its 多少 is also the count word — so the turn was
        // read as "how many" and answered with the size of the set instead of the gap.
        "hoeveel meer", "hoeveel groter", "die verskil",
        "gaano kalaki ang pagkakaiba", "pagkakaiba", "gaano kalaki",
        "कितना अधिक", "कितना ज्यादा", "अंतर",
        "کتنا زیادہ", "فرق",
        "相差", "相差多少", "差多少"
    )

    /**
     * Words naming the act of discovery, in every shipped language.
     *
     * Paired with [WHEN_WORDS] and [WHO_WORDS] to choose between the two discovery fields, which
     * share every alias they have: "who discovered helium" and "when was helium discovered" differ
     * only in the interrogative, and answering either with the other is a confident wrong answer.
     */
    val DISCOVERY_WORDS = listOf(
        "discovered", "discover", "discovery", "found", "identified", "isolated",
        "upptackt", "upptackte", "upptacktes", "entdeckt", "entdeckte", "gefunden",
        "decouvert", "decouverte", "descubierto", "descubrio", "scoperto", "scoperta",
        "descoberto", "descobriu", "ontdek", "natuklasan",
        // The four scripts that had no entry at all. Both discovery fields were unreachable in
        // Filipino, Hindi, Urdu and Chinese, so "who discovered helium" resolved no field, inherited
        // whatever the previous turn used, and answered a question about history with a number.
        "nakatuklas", "tuklas", "खोज", "खोजा", "दریافت", "دریافت", "发现", "发现了"
    )

    /** Interrogatives asking for a time. */
    val WHEN_WORDS = listOf(
        "when", "nar", "wann", "quand", "cuando", "quando", "wanneer", "kailan",
        "what year", "vilket ar", "welches jahr",
        "कब", "کب", "什么时候", "何时", "哪一年", "किस वर्ष", "کس سال"
    )

    /** Interrogatives asking for a person. */
    val WHO_WORDS = listOf(
        "who", "vem", "wer", "qui", "quien", "chi", "quem", "sino", "kaun", "kon",
        "किसने", "किसके", "کس نے", "谁"
    )

    /**
     * Frames that ask *how something works* rather than what a value is.
     *
     * These questions have no grounded answer in a table of properties, and the damage they do is
     * specific: the query mentions a real field in passing, so the planner resolves it and answers
     * with a number nobody asked for. "Why does radius decrease left to right, despite adding
     * electrons" was answered "Potassium's Electrons is 19" — confidently, with a citation.
     *
     * Recognised so the planner can decline them outright instead of falling through to a lookup.
     */
    val MECHANISM_FRAMES = listOf(
        "how does", "how do ", "how did", "how is it that", "how come", "how would",
        "what happens when", "what happens if", "what would happen", "what causes",
        "explain why", "explain how", "reason for", "the reason", "account for",
        "hur kommer det sig", "hur gar det till", "vad hander nar", "vad hander om",
        "varfor ar det", "wie kommt es", "was passiert wenn", "wieso", "warum ist",
        "comment se fait", "que se passe", "pourquoi est", "por que ocurre", "que pasa cuando",
        "come mai", "cosa succede", "o que acontece"
    )

    /**
     * Questions about where a name or symbol came from.
     *
     * Split out of [NARRATIVE_INTENTS] because these have to outrank a resolved field: the field is
     * what the question is *about*, not what it asks for.
     */
    val ETYMOLOGY_INTENTS = listOf(
        "come from", "comes from", "came from", "origin of the name", "name origin",
        "etymology", "etymologically", "named after", "why is it called", "where does the name",
        "etymologi", "namnets ursprung", "uppkallad efter", "varifran kommer namnet",
        "herkunft des namens", "benannt nach", "etymologie", "etimologia", "origen del nombre"
    )

    /**
     * Pronouns standing in for the element already under discussion.
     *
     * The counterpart to [PLURAL_ANAPHORS]. Needed to tell "is it radioactive" — a question about
     * the element in play — from "noble gases", which is the same length and also carries a family
     * word but introduces its own subject.
     */
    val ANAPHORS = listOf(
        "it", "its", "that", "this", "they", "the same",
        "den", "det", "dess", "denna", "detta", "es", "ihr", "dessen",
        "il", "elle", "son", "sa", "lo", "la", "su", "esse", "questo",
        "dit", "sy", "ito", "nito", "iyon", "esso", "essa", "seus", "suas", "sus", "leurs", "यह", "इस", "इसके", "इनके", "اس", "اس کے", "ان کے", "یہ", "它", "它们", "它的",
        // The singular possessives. Only their plurals were listed, so "qual é a sua densidade"
        // looked subject-less and the Romance interrogative in front of it was read as a choice
        // between the two elements of the previous turn.
        "sua", "seu", "suo", "suoi", "ses", "seine", "seiner", "sein", "sy se", "kanya", "kanyang"
    )

    /**
     * Degree words that are ambiguous between a comparative and a superlative.
     *
     * English marks the two apart morphologically — "denser" against "densest" — so a superlative
     * there is unmistakable. The Romance languages, Filipino, Hindi, Urdu and Chinese all build both
     * from the same degree word, separated only by an article or by context: "plus dense" against
     * "le plus dense", "更致密" against "最致密".
     *
     * Only these words license reading a superlative marker as a comparison between the two
     * elements already in play. Keyed on this rather than on [MOST] as a whole, because doing the
     * latter turned every English "which is the densest" follow-up into a two-element comparison.
     */
    val AMBIGUOUS_DEGREE = listOf(
        "more", "less", "plus", "mas", "más", "mais", "piu", "più", "meer",
        "更", "अधिक", "ज्यादा", "زیادہ"
    )

    /**
     * Fixed terms that contain a phase word without being about that phase.
     *
     * Removed from the query before phase matching, so "the gas constant" and "the ideal gas law"
     * stop producing a filtered list of every gaseous element.
     */
    val PHASE_EXEMPT_PHRASES = listOf(
        "gas constant", "ideal gas law", "gas law", "natural gas", "greenhouse gas",
        "gaskonstant", "allmanna gaslagen", "idealgaslagen",
        "gaskonstante", "ideales gasgesetz", "gasgesetz",
        "constante des gaz", "loi des gaz", "constante de los gases", "ley de los gases",
        "costante dei gas", "legge dei gas", "constante dos gases", "lei dos gases",
        "气体常数", "理想气体定律",
        // The noble gases are a *series*, and every language but English names them with its word
        // for "gas". Admitting the plural phase words above without these would turn "quels
        // éléments sont des gaz nobles" into a list of every gaseous element.
        "gaz noble", "gaz nobles", "gases nobles", "gases nobres", "gas nobili", "gas nobile",
        "edelgas", "edelgase", "edelgasse", "adelgas", "adelgaser", "noble gas", "noble gases",
        "mga noble gas", "उत्कृष्ट गैस", "उत्कृष्ट गैसें", "उत्कृष्ट गैसों",
        "نیک گیس", "نیک گیسیں", "نیک گیسوں"
    )

    /** Phrases restricting a query to elements made artificially. */
    val SYNTHETIC_WORDS = listOf(
        "synthetic", "man-made", "manmade", "artificial", "artificially",
        "syntetisk", "konstgjord", "synthetisch", "kunstlich",
        "sintetico", "artificial", "synthetique", "artificiel", "sintetico", "artificiale",
        "sinteties", "sintetiko", "कृत्रिम", "مصنوعی", "人造", "合成",
        // Agreement forms. "Which elements are synthetic" puts the adjective in the plural in every
        // Romance and Scandinavian language, and only the singular was listed — so the question was
        // declined in five of the twelve.
        "syntetiska", "sinteticos", "sinteticas", "sintetica",
        "synthetiques", "synthetique", "sintetici", "sintetiche",
        "kunstliche", "kunstlich", "sintetiese"
    )

    val RADIOACTIVE_WORDS = listOf(
        "radioactive", "radioaktiv", "radioaktivt", "radiactivo", "radioactif",
        "radioattivo", "radioativo", "radioaktief", "radyoaktibo", "rediyoaktiv", "放射性",
        // Plural and agreement forms. "Vilka grundämnen är radioaktiva" and "quels éléments sont
        // radioactifs" are the natural way to ask, and only the singular was listed.
        "radioaktiva", "radioaktive", "radioaktiven", "radiactivos", "radiactivas",
        "radioactifs", "radioactives", "radioattivi", "radioattive", "radioativos", "radioativas",
        "radioaktiewe", "रेडियोधर्मी", "تابکار",
        "radiactivo", "radiactivos", "radiactiva", "radiactivas"
    )

    /** Ordinal words for banked fields such as "second ionization energy". */
    val ORDINALS: Map<String, Int> = mapOf(
        "first" to 1, "1st" to 1, "forsta" to 1, "erste" to 1, "primero" to 1, "premier" to 1, "primo" to 1, "第一" to 1,
        // Feminine and the four languages with no entry. "La première énergie d'ionisation" and
        // "a primeira energia de ionização" are how the question is written; only the masculine was
        // listed, so the ordinal was lost and the first ionization energy came back unbanked.
        "premiere" to 1, "primeira" to 1, "prima energia" to 1, "eerste" to 1,
        "पहली" to 1, "पहला" to 1, "پہلی" to 1, "پہلا" to 1,
        "second" to 2, "2nd" to 2, "andra" to 2, "zweite" to 2, "segundo" to 2, "deuxieme" to 2, "secondo" to 2, "第二" to 2,
        "third" to 3, "3rd" to 3, "tredje" to 3, "dritte" to 3, "tercero" to 3, "troisieme" to 3, "terzo" to 3, "第三" to 3,
        "fourth" to 4, "4th" to 4, "fjarde" to 4, "vierte" to 4, "cuarto" to 4, "quatrieme" to 4, "quarto" to 4,
        "fifth" to 5, "5th" to 5, "femte" to 5, "funfte" to 5, "quinto" to 5, "cinquieme" to 5
    )

    /** Prepositions that introduce a requested output unit. */
    val UNIT_PREPOSITIONS = listOf(
        "in", "as", "to", "i", "als", "en", "com", "em", "用", "以",
        // Hindi and Urdu are postpositional — "सेल्सियस में" is "celsius in" — and Filipino uses
        // "sa". Without these, a bare unit follow-up resolved nothing at all.
        "में", "میں", "sa"
    )

    /** Unit names the planner accepts as a conversion target. */
    val UNIT_WORDS: Map<String, String> = mapOf(
        "kelvin" to "K", "k" to "K",
        "celsius" to "°C", "centigrade" to "°C", "c" to "°C",
        "fahrenheit" to "°F", "f" to "°F",
        // The three scripts that transliterate the unit names rather than borrowing them.
        "सेल्सियस" to "°C", "फारेनहाइट" to "°F", "केल्विन" to "K",
        "سیلسیس" to "°C", "فارن ہائیٹ" to "°F", "کیلون" to "K",
        "摄氏度" to "°C", "华氏度" to "°F", "开尔文" to "K",
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
