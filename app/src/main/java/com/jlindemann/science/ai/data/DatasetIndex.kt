package com.jlindemann.science.ai.data

import com.jlindemann.science.model.*

/**
 * A row from one of the app's non-element datasets, flattened into a shape the agent can search,
 * cite and deep-link uniformly.
 *
 * @property dataset the dataset id, matching [DatasetIndex.DICTIONARY] and friends
 * @property id stable identifier within the dataset, used in citations
 * @property key the canonical id, the same on both platforms; see [DatasetIndex.canonicalKeys]
 * @property title the row's display name
 * @property body searchable prose; what BM25 indexes
 * @property detail rendered answer text
 * @property numericValue a comparable value when the row carries one, e.g. a constant or a voltage
 */
data class DatasetRow(
    val dataset: String,
    val id: String,
    val title: String,
    val body: String,
    val detail: String,
    val deepLink: DeepLinkTarget,
    val numericValue: Double? = null,
    val unit: String? = null,
    val link: String? = null,
    val key: String = ""
)

/**
 * Every non-element dataset in the app, in one searchable index.
 *
 * The legacy agent reached only a fraction of these, through hand-written keyword cascades. Here
 * all of them become uniformly queryable and citable. The underlying models are English-only, so
 * answers drawn from them stay English regardless of chat language; the composer flags that
 * rather than pretending otherwise.
 */
class DatasetIndex(rows: List<DatasetRow>) {

    /** The rows, each with its canonical [DatasetRow.key]. */
    val rows: List<DatasetRow> = canonicalKeys(rows)

    val byDataset: Map<String, List<DatasetRow>> = this.rows.groupBy { it.dataset }

    fun dataset(name: String): List<DatasetRow> = byDataset[name].orEmpty()

    fun row(dataset: String, id: String): DatasetRow? =
        byDataset[dataset]?.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** A row by its canonical key, e.g. `poisson/basalt`. */
    fun byKey(key: String): DatasetRow? = rows.firstOrNull { it.key == key }

    companion object {
        const val DICTIONARY = "dictionary"
        const val EQUATION = "equation"
        const val CONSTANT = "constant"
        const val POISSON = "poisson"
        const val GEOLOGY = "geology"
        const val ION = "ion"
        const val INDICATOR = "indicator"
        const val ELECTRODE = "electrode"
        const val SOLUBILITY = "solubility"
        const val UNIT = "unit"
        const val ALLOY = "alloy"

        /**
         * Build the index from the app's model objects.
         *
         * Each `getList` call rebuilds its list, so this runs once and the result is held by the
         * engine rather than being rebuilt per query.
         */
        fun build(): DatasetIndex {
            val rows = ArrayList<DatasetRow>(600)

            ArrayList<Dictionary>().also { DictionaryModel.getList(it) }.forEach { d ->
                rows.add(
                    DatasetRow(
                        dataset = DICTIONARY, id = d.heading, title = d.heading,
                        body = "${d.heading} ${d.category} ${d.text}",
                        detail = d.text, deepLink = DeepLinkTarget.DICTIONARY, link = d.wiki
                    )
                )
            }

            ArrayList<Equation>().also { EquationModel.getList(it) }.forEach { e ->
                rows.add(
                    DatasetRow(
                        dataset = EQUATION, id = e.equationTitle, title = e.equationTitle,
                        // "Formula" and "equation", because that is what an equation is asked
                        // for by: "formula for potential energy" (roadmap 1.1).
                        body = "${e.equationTitle} ${e.category} formula equation ${e.description}",
                        detail = e.description, deepLink = DeepLinkTarget.EQUATIONS
                    )
                )
            }

            ArrayList<Constants>().also { ConstantsModel.getList(it) }.forEach { c ->
                rows.add(
                    DatasetRow(
                        dataset = CONSTANT, id = c.name, title = c.name,
                        body = "${c.name} ${c.category} ${c.info} ${c.unit}",
                        detail = buildString {
                            append(c.value)
                            if (c.unit.isNotBlank() && c.unit != "-") append(" ").append(c.unit)
                            if (c.info.isNotBlank()) append(" — ").append(c.info)
                        },
                        deepLink = DeepLinkTarget.CONSTANTS,
                        numericValue = c.value.replace(",", "").toDoubleOrNull(),
                        unit = c.unit.takeIf { it.isNotBlank() && it != "-" }
                    )
                )
            }

            ArrayList<Poisson>().also { PoissonModel.getList(it) }.forEach { p ->
                rows.add(
                    DatasetRow(
                        dataset = POISSON, id = p.name, title = p.name,
                        body = "${p.name} ${p.type} poisson ratio",
                        detail = "${p.start} – ${p.end} (${p.type})",
                        deepLink = DeepLinkTarget.POISSON,
                        numericValue = (p.start + p.end) / 2
                    )
                )
            }

            ArrayList<Geology>().also { GeologyModel.getList(it) }.forEach { g ->
                rows.add(
                    DatasetRow(
                        dataset = GEOLOGY, id = g.name, title = g.name,
                        body = "${g.name} ${g.type} ${g.group} ${g.color} ${g.streak} ${g.cristal} ${g.luster}",
                        detail = "${g.group} · ${g.color} · Mohs ${g.hardness} · ${g.luster}",
                        deepLink = DeepLinkTarget.GEOLOGY
                    )
                )
            }

            ArrayList<Ion>().also { IonModel.getList(it) }.forEach { i ->
                rows.add(
                    DatasetRow(
                        dataset = ION, id = i.name, title = i.name,
                        body = "${i.name} ${i.short} ionization energy",
                        detail = "${i.count} ionization energy values",
                        deepLink = DeepLinkTarget.ION,
                        numericValue = i.count.toDouble()
                    )
                )
            }

            ArrayList<Indicator>().also { IndicatorModel.getList(it) }.forEach { ind ->
                rows.add(
                    DatasetRow(
                        dataset = INDICATOR, id = ind.name, title = ind.name.replace('_', ' '),
                        // "Colour", "turn" and "range", because that is how indicators are asked
                        // about: "what colour does phenolphthalein turn" (roadmap 1.1).
                        body = "${ind.name.replace('_', ' ')} ph indicator ph range colour color turn turns changes " +
                                "${ind.acidColor} ${ind.neutralColor} ${ind.alkaliColor}",
                        detail = "Acid ${ind.acid}: ${ind.acidColor} · Neutral ${ind.neutral}: ${ind.neutralColor} · Alkali ${ind.alkali}: ${ind.alkaliColor}",
                        deepLink = DeepLinkTarget.PH
                    )
                )
            }

            ArrayList<Series>().also { SeriesModel.getList(it, null) }.forEach { s ->
                rows.add(
                    DatasetRow(
                        dataset = ELECTRODE, id = s.short, title = s.name,
                        // "Standard" and "reduction": this is the standard reduction potential
                        // table, and that is what it is asked for by (roadmap 1.1).
                        body = "${s.name} ${s.short} ${s.charge} standard electrode potential reduction potential electrochemical series",
                        detail = "${s.charge}: ${s.voltage} V",
                        deepLink = DeepLinkTarget.ELECTRODE,
                        numericValue = s.voltage, unit = "V"
                    )
                )
            }

            SolubilityData.getColumns().forEach { column ->
                val pairs = SolubilityData.anions.zip(column.values)
                    .filter { it.second.isNotBlank() && it.second != "---" }
                rows.add(
                    DatasetRow(
                        dataset = SOLUBILITY, id = column.cation, title = column.cation,
                        // Anion *names* and "water", because users write "is silver chloride
                        // soluble in water", not "Ag^+ Cl^-" (roadmap 1.1).
                        body = "${column.cation} solubility soluble insoluble dissolve water " +
                                pairs.joinToString(" ") { "${it.first} ${ANION_NAMES[it.first].orEmpty()}" },
                        detail = pairs.joinToString(" · ") { "${it.first} ${solubilityWord(it.second)}" },
                        deepLink = DeepLinkTarget.SOLUBILITY
                    )
                )
            }

            ArrayList<Alloy>().also { AlloyModel.getList(it) }.forEach { a ->
                rows.add(
                    DatasetRow(
                        dataset = ALLOY, id = a.name, title = a.name,
                        body = "${a.name} alloy ${a.base} ${a.composition} ${a.description}",
                        detail = "${a.composition}. ${a.description}",
                        // Alloys have no screen of their own; the base element is the nearest
                        // place a reader can go for related data.
                        deepLink = DeepLinkTarget.ELEMENT_INFO,
                        link = a.wiki
                    )
                )
            }

            UnitCatalog.categories.forEach { (category, units) ->
                // The unit everything in the category converts through, named on every row:
                // "how many joules in a calorie" names the joule, and the calorie row did not.
                val base = units.firstOrNull { it.factor == 1.0 }?.name.orEmpty()
                units.forEach { u ->
                    rows.add(
                        DatasetRow(
                            dataset = UNIT, id = "$category/${u.name}", title = u.name,
                            body = "${u.name} $category unit conversion $base",
                            detail = if (category == UnitCatalog.TEMPERATURE) category
                            else "1 ${u.name} = ${u.factor} (base $category unit)",
                            deepLink = DeepLinkTarget.UNIT_CONVERTER,
                            numericValue = u.factor
                        )
                    )
                }
            }

            return DatasetIndex(rows)
        }

        /**
         * Give every row a canonical key: `<dataset>/<slug of its title>`, with `#2`, `#3`… for a
         * title repeated within a dataset, in source order. `#` rather than `-`, because titles end
         * in numbers of their own: `Mg^2+` is `solubility/mg-2`.
         *
         * The two apps used to identify rows differently. Android keyed on the name alone, so the
         * second of two Basalt rows in the Poisson table could never be cited; Windows appended the
         * range. A corpus row that names *which* row must come back (roadmap 0.6) needs one id both
         * agree on, so the key is computed the same way here and in the C# `DatasetIndex`, and both
         * are pinned by `shared/golden/dataset_keys.tsv`.
         *
         * Units are keyed on `category/name` rather than the name, because unit names repeat across
         * categories and the suffix order would then depend on category order.
         */
        fun canonicalKeys(rows: List<DatasetRow>): List<DatasetRow> {
            val seen = HashMap<String, Int>()
            return rows.map { row ->
                val natural = if (row.dataset == UNIT) row.id else row.title
                val base = "${row.dataset}/${slug(natural)}"
                val n = (seen[base] ?: 0) + 1
                seen[base] = n
                row.copy(key = if (n == 1) base else "$base#$n")
            }
        }

        /**
         * Lowercase ASCII words joined by hyphens. Compatibility-decomposed first, so "₂" is "2" and
         * "é" is "e"; anything left that is not a letter or digit separates words.
         */
        fun slug(text: String): String =
            java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(java.util.Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifEmpty { "row" }

        /**
         * The names people use for the anions the solubility table is keyed by. The table writes
         * "Cl^-"; nobody asks whether silver Cl^- dissolves.
         */
        private val ANION_NAMES = mapOf(
            "F^-" to "fluoride", "Cl^-" to "chloride", "Br^-" to "bromide", "I^-" to "iodide",
            "OH^-" to "hydroxide", "S^2-" to "sulfide sulphide", "SO4^2-" to "sulfate sulphate",
            "CO3^2-" to "carbonate", "NO3^-" to "nitrate", "PO4^3-" to "phosphate",
            "CrO4^2-" to "chromate", "CH3CO2^-" to "acetate", "SO3^2-" to "sulfite sulphite",
            "SiO3^2-" to "silicate"
        )

        /**
         * Translated row titles from `datasets/titles_{lang}.tsv` in the shared data (roadmap 1.3):
         * canonical key, then the title, with other wordings after a `|` - "Neutronens massa |
         * neutronmassa". `#` lines and the header are skipped; a row with no title is no row.
         */
        fun parseTitles(text: String?): Map<String, List<String>> {
            if (text.isNullOrBlank()) return emptyMap()
            val out = HashMap<String, List<String>>()
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("key\t")) continue
                val cells = line.trimEnd('\r').split('\t')
                val forms = cells.getOrNull(1).orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }
                if (forms.isNotEmpty()) out[cells[0]] = forms
            }
            return out
        }

        private fun solubilityWord(code: String): String = when (code) {
            "S" -> "soluble"
            "Ss" -> "slightly soluble"
            "I" -> "insoluble"
            else -> code
        }
    }
}
