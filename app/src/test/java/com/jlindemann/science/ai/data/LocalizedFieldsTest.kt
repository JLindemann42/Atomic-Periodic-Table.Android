package com.jlindemann.science.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * `element_appearance` and `electrical_type` are overlaid per language, and until roadmap 0.8 the
 * data behind them was English or a word-for-word substitution of it ("Grayesth White",
 * "银白色 Metallic"). They are now translated from `shared/tools/translations`; these keep it so.
 * Windows porting notes, D12 and D58.
 */
class LocalizedFieldsTest {

    private val english by lazy { TestAssets.elementTable("en").rows }
    private val others by lazy { TestAssets.availableLanguages().filter { it != "en" } }

    @Before fun setUp() = assumeTrue(TestAssets.available())

    @Test fun everyLanguageIsChecked() = assertEquals(11, others.size)

    @Test fun noAppearanceIsLeftInEnglish() {
        for (language in others) {
            val rows = TestAssets.elementTable(language).rows
            for ((key, row) in english) {
                val source = row["element_appearance"]?.toString().orEmpty()
                if (source == "---" || source.isEmpty()) continue
                assertNotEquals("$language/$key", source, rows.getValue(key)["element_appearance"])
            }
        }
    }

    /** Every element with an English electrical type has one in every language, not "---". */
    @Test fun everyElectricalTypeIsPresent() {
        for (language in others) {
            val rows = TestAssets.elementTable(language).rows
            for ((key, row) in english) {
                if (row["electrical_type"]?.toString().isNullOrEmpty()) continue
                val value = rows.getValue(key)["electrical_type"]?.toString().orEmpty()
                assertFalse("$language/$key has no electrical type", value.isEmpty() || value == "---")
            }
        }
    }

    @Test fun goldReadsInTheReadersLanguage() {
        assertEquals("Metallic yellow", english.getValue("gold")["element_appearance"])
        assertEquals("Metallisk gul", TestAssets.elementTable("sv").rows.getValue("gold")["element_appearance"])
        assertEquals("Halvledare", TestAssets.elementTable("sv").rows.getValue("silicon")["electrical_type"])
    }

    /** The substitution damage was found by these fragments; none may come back in any field. */
    @Test fun noSubstitutionDamageInTheTranslatedFields() {
        val damage = Regex("""[a-z](dans|esth)[a-z]|Tdans|Shdans""")
        val rows = TestAssets.elementTable("fr").rows
        for ((key, row) in rows) {
            for (field in listOf("element_appearance", "electrical_type", "element_group", "crystal_structure")) {
                val value = row[field]?.toString().orEmpty()
                assertFalse("fr/$key/$field: $value", damage.containsMatchIn(value))
            }
        }
    }
}
