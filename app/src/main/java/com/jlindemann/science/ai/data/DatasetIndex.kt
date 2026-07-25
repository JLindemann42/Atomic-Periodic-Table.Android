package com.jlindemann.science.ai.data

import com.jlindemann.science.model.*

/**
 * A row from one of the app's non-element datasets, flattened into a shape the agent can search,
 * cite and deep-link uniformly.
 *
 * @property dataset the dataset id, matching [DatasetIndex.DICTIONARY] and friends
 * @property id stable identifier within the dataset, used in citations
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
    val link: String? = null
)

/**
 * Every non-element dataset in the app, in one searchable index.
 *
 * The legacy agent reached only a fraction of these, through hand-written keyword cascades. Here
 * all of them become uniformly queryable and citable. The underlying models are English-only, so
 * answers drawn from them stay English regardless of chat language; the composer flags that
 * rather than pretending otherwise.
 */
class DatasetIndex(val rows: List<DatasetRow>) {

    val byDataset: Map<String, List<DatasetRow>> = rows.groupBy { it.dataset }

    fun dataset(name: String): List<DatasetRow> = byDataset[name].orEmpty()

    fun row(dataset: String, id: String): DatasetRow? =
        byDataset[dataset]?.firstOrNull { it.id.equals(id, ignoreCase = true) }

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
                        body = "${e.equationTitle} ${e.category} ${e.description}",
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
                        body = "${ind.name.replace('_', ' ')} ph indicator ${ind.acidColor} ${ind.neutralColor} ${ind.alkaliColor}",
                        detail = "Acid ${ind.acid}: ${ind.acidColor} · Neutral ${ind.neutral}: ${ind.neutralColor} · Alkali ${ind.alkali}: ${ind.alkaliColor}",
                        deepLink = DeepLinkTarget.PH
                    )
                )
            }

            ArrayList<Series>().also { SeriesModel.getList(it, null) }.forEach { s ->
                rows.add(
                    DatasetRow(
                        dataset = ELECTRODE, id = s.short, title = s.name,
                        body = "${s.name} ${s.short} ${s.charge} electrode potential electrochemical series",
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
                        body = "${column.cation} solubility soluble insoluble " +
                                pairs.joinToString(" ") { it.first },
                        detail = pairs.joinToString(" · ") { "${it.first} ${solubilityWord(it.second)}" },
                        deepLink = DeepLinkTarget.SOLUBILITY
                    )
                )
            }

            UnitCatalog.categories.forEach { (category, units) ->
                units.forEach { u ->
                    rows.add(
                        DatasetRow(
                            dataset = UNIT, id = "$category/${u.name}", title = u.name,
                            body = "${u.name} $category unit conversion",
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

        private fun solubilityWord(code: String): String = when (code) {
            "S" -> "soluble"
            "Ss" -> "slightly soluble"
            "I" -> "insoluble"
            else -> code
        }
    }
}
