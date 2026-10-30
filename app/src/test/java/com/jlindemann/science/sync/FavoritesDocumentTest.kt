package com.jlindemann.science.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tools' saved items: `users/{uid}.favorites`.
 *
 * The same cases as the Windows app's `FavoritesDocumentTest`, because the two merges must agree: a
 * removal on one device stays removed on the other, saving again beats an older removal, items this
 * device already had are added rather than lost, and a write sheds nothing it does not know.
 */
class FavoritesDocumentTest {

    private val now = 1_760_000_000_000L
    private val day = 86_400_000L

    /** One of each, as the PC writes them, plus an unknown field on an item and an unknown list. */
    private val cloudField: Map<String, Any?> = mapOf(
        "compounds" to listOf(
            mapOf(
                "formula" to "H2O",
                "molarMass" to "18.015",
                "updatedAt" to 1_759_000_000_000L,
                "deleted" to false,
                "note" to "from the PC",
            )
        ),
        "equations" to listOf(
            mapOf("input" to "H2 + O2 = H2O", "balanced" to "2H2 + O2 → 2H2O", "updatedAt" to 1_759_000_000_000L)
        ),
        "idealGas" to listOf(
            mapOf(
                "summary" to "P = 1 atm, V = 22.4 L, T = 273 K",
                "result" to "n = 1.00 mol",
                "updatedAt" to 1_759_000_000_000L,
                "deleted" to true,
            )
        ),
        "unitConversions" to listOf(
            mapOf(
                "category" to "Length",
                "fromUnit" to "Kilometer",
                "toUnit" to "Meter",
                // The PC writes a whole number as a double; Firestore can hand it back as a Long.
                "inputValue" to 2L,
                "convertedValue" to 2000.0,
                "updatedAt" to 1_759_000_000_000L,
            )
        ),
        "units" to listOf("km"),
    )

    private fun item(key: String, at: Long, deleted: Boolean = false) = SyncedFavorite(key, "v", at, deleted)

    @Test
    fun `reads each tool's items`() {
        val cloud = FavoritesDocument.fromField(cloudField)

        assertEquals(
            SyncedFavorite("H2O", "18.015", 1_759_000_000_000L),
            cloud.getValue(FavoriteKind.COMPOUND).single().copy(raw = null),
        )
        assertEquals("2H2 + O2 → 2H2O", cloud.getValue(FavoriteKind.EQUATION).single().value)
        assertFalse(cloud.getValue(FavoriteKind.EQUATION).single().deleted)
        assertTrue(cloud.getValue(FavoriteKind.IDEAL_GAS).single().deleted)
    }

    @Test
    fun `a missing field reads as empty lists`() {
        val empty = FavoritesDocument.fromField(null)

        assertEquals(4, empty.size)
        assertTrue(empty.values.all { it.isEmpty() })
    }

    @Test
    fun `a write keeps fields it does not know`() {
        val written = FavoritesDocument.toField(FavoritesDocument.fromField(cloudField))
        val compound = (written["compounds"] as List<*>).single() as Map<*, *>

        assertEquals("from the PC", compound["note"])
    }

    /** The write names its four lists only; `units`, under the same map, is left to the merge. */
    @Test
    fun `the write holds only the four lists`() {
        val written = FavoritesDocument.toField(FavoritesDocument.fromField(cloudField))

        assertEquals(setOf("compounds", "equations", "idealGas", "unitConversions"), written.keys)
    }

    @Test
    fun `a unit conversion reads its fields and numbers`() {
        val conversion = FavoritesDocument.fromField(cloudField).getValue(FavoriteKind.UNIT_CONVERSION).single()

        assertEquals(FavoritesDocument.unitConversionKey("Length", "Kilometer", "Meter", 2.0), conversion.key)
        assertEquals("2000.0", conversion.value)
        assertEquals(
            FavoritesDocument.UnitConversionKey("Length", "Kilometer", "Meter", 2.0),
            FavoritesDocument.parseUnitConversionKey(conversion.key),
        )
    }

    @Test
    fun `a unit conversion is written as fields with numbers`() {
        val item = SyncedFavorite(FavoritesDocument.unitConversionKey("Mass", "Gram", "Kilogram", 1500.0), "1.5", 7)
        val written = FavoritesDocument.toField(mapOf(FavoriteKind.UNIT_CONVERSION to listOf(item)))
        val fields = (written["unitConversions"] as List<*>).single() as Map<*, *>

        assertEquals("Gram", fields["fromUnit"])
        assertEquals(1500.0, fields["inputValue"])
        assertEquals(1.5, fields["convertedValue"])
        assertEquals(
            item,
            FavoritesDocument.fromField(written).getValue(FavoriteKind.UNIT_CONVERSION).single().copy(raw = null),
        )
    }

    /** What the user asked for: favourites saved before sync are added, the cloud's kept too. */
    @Test
    fun `favourites already on both devices are combined, not overwritten`() {
        val merged = FavoritesDocument.merge(listOf(item("NaCl", now)), listOf(item("H2O", now - day)), now)

        assertEquals(listOf("NaCl", "H2O"), merged.map { it.key })
    }

    @Test
    fun `a removal beats an older save`() {
        val merged = FavoritesDocument.merge(listOf(item("H2O", now, deleted = true)), listOf(item("H2O", now - day)), now)

        assertTrue(merged.single().deleted)
    }

    @Test
    fun `a removal is not undone by the other device`() {
        val merged = FavoritesDocument.merge(listOf(item("H2O", now - day)), listOf(item("H2O", now, deleted = true)), now)

        assertTrue(merged.single().deleted)
    }

    @Test
    fun `saving again beats an older removal`() {
        val merged = FavoritesDocument.merge(listOf(item("H2O", now)), listOf(item("H2O", now - day, deleted = true)), now)

        assertFalse(merged.single().deleted)
    }

    @Test
    fun `a tie keeps the cloud's unknown fields`() {
        val theirs = FavoritesDocument.fromField(cloudField).getValue(FavoriteKind.COMPOUND).single()
        val merged = FavoritesDocument.merge(listOf(SyncedFavorite("H2O", "18.02", theirs.updatedAt)), listOf(theirs), now).single()

        assertEquals("18.02", merged.value)
        assertEquals("from the PC", merged.raw?.get("note"))
    }

    @Test
    fun `old removals expire`() {
        val old = now - FavoritesDocument.TOMBSTONE_LIFETIME_MS - 1
        val merged = FavoritesDocument.merge(
            emptyList(),
            listOf(item("H2O", old, deleted = true), item("NaCl", now - day, deleted = true)),
            now,
        )

        assertEquals(listOf("NaCl"), merged.map { it.key })
    }

    @Test
    fun `saved items are capped newest first`() {
        val many = (0 until FavoritesDocument.MAX_PER_KIND + 5).map { item("k$it", now - it) }
        val merged = FavoritesDocument.merge(many, emptyList(), now)

        assertEquals(FavoritesDocument.MAX_PER_KIND, merged.size)
        assertEquals("k0", merged.first().key)
    }

    @Test
    fun `merging again changes nothing`() {
        val cloud = listOf(item("H2O", now - day), item("", now - day), item("NaCl", now, deleted = true))
        val once = FavoritesDocument.merge(listOf(item("CO2", now)), cloud, now)
        val twice = FavoritesDocument.merge(once, once, now)

        assertEquals(4, once.size)
        assertFalse(FavoritesDocument.differs(once, twice))
    }

    @Test
    fun `differs ignores order but not removal`() {
        assertFalse(FavoritesDocument.differs(listOf(item("a", 1), item("b", 2)), listOf(item("b", 2), item("a", 1))))
        assertTrue(FavoritesDocument.differs(listOf(item("a", 1)), listOf(item("a", 1, deleted = true))))
    }
}
