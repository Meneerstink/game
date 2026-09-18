package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.combat.formula.DragonfireTable.Potion
import gg.rsmod.plugins.content.combat.formula.DragonfireTable.Type
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The OSRS Wiki "Dragonfire" damage reduction tables, row for row (RCV-012 decision "dragonfire = OSRS model"). Each expected value
 * is `failed/won` for the chromatic and metallic columns and one value for the King Black Dragon columns.
 */
class DragonfireTableTests {
    private fun maxOf(
        type: Type,
        shield: Boolean,
        prayer: Boolean,
        potion: Potion,
    ): String = DragonfireTable.max(type, shield, prayer, potion).let { if (it.splitByAccuracy) "${it.failed}/${it.won}" else "${it.failed}" }

    /** Rows in the wiki order: none, shield, prayer, shield+prayer, antifire, super, shield+antifire, shield+super, prayer+antifire, prayer+super, all+antifire, all+super. */
    private val rows =
        listOf(
            Triple(false, false, Potion.NONE), Triple(true, false, Potion.NONE), Triple(false, true, Potion.NONE), Triple(true, true, Potion.NONE),
            Triple(false, false, Potion.ANTIFIRE), Triple(false, false, Potion.SUPER_ANTIFIRE), Triple(true, false, Potion.ANTIFIRE),
            Triple(true, false, Potion.SUPER_ANTIFIRE), Triple(false, true, Potion.ANTIFIRE), Triple(false, true, Potion.SUPER_ANTIFIRE),
            Triple(true, true, Potion.ANTIFIRE), Triple(true, true, Potion.SUPER_ANTIFIRE),
        )

    private fun column(type: Type) = rows.map { (shield, prayer, potion) -> maxOf(type, shield, prayer, potion) }

    @Test
    fun `chromatic dragonfire matches the wiki table`() {
        assertEquals(listOf("50/30", "5", "10", "5", "35/15", "0", "0", "0", "0", "0", "0", "0"), column(Type.CHROMATIC))
    }

    @Test
    fun `King Black Dragon fiery and special breaths match the wiki table`() {
        assertEquals(listOf("65", "15", "20", "15", "50", "0", "0", "0", "5", "0", "0", "0"), column(Type.KING_BLACK_DRAGON_FIERY))
        assertEquals(listOf("50", "10", "15", "10", "50", "50", "10", "10", "15", "15", "10", "10"), column(Type.KING_BLACK_DRAGON_SPECIAL))
    }

    @Test
    fun `metallic dragonfire matches the wiki table and ignores Protect from Magic`() {
        val withoutPrayer = rows.filter { !it.second }.map { (shield, _, potion) -> maxOf(Type.METALLIC, shield, false, potion) }
        assertEquals(listOf("50/30", "5", "35/15", "0", "0", "0"), withoutPrayer)
        rows.filter { it.second }.forEach { (shield, _, potion) ->
            assertEquals(maxOf(Type.METALLIC, shield, false, potion), maxOf(Type.METALLIC, shield, true, potion), "prayer with shield=$shield $potion")
        }
    }

    @Test
    fun `every live dragonfire attack in the npc attack table resolves to its wiki category`() {
        val expected =
            mapOf(
                "king_black_dragon:dragonfire" to Type.KING_BLACK_DRAGON_FIERY,
                "king_black_dragon:toxic" to Type.KING_BLACK_DRAGON_SPECIAL,
                "king_black_dragon:ice" to Type.KING_BLACK_DRAGON_SPECIAL,
                "king_black_dragon:shock" to Type.KING_BLACK_DRAGON_SPECIAL,
                "bronze_dragon" to Type.METALLIC, "iron_dragon" to Type.METALLIC, "steel_dragon" to Type.METALLIC, "mithril_dragon" to Type.METALLIC,
                "green_dragon" to Type.CHROMATIC, "red_dragon" to Type.CHROMATIC, "blue_dragon" to Type.CHROMATIC, "black_dragon" to Type.CHROMATIC,
                "brutal_green_dragon" to Type.CHROMATIC, "frost_dragon" to Type.CHROMATIC,
            )
        var dragonfireHits = 0
        // Load the table here: rows() is only populated when another test happened to load it first (order-dependent).
        NpcAttacks.load(java.nio.file.Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile())
        NpcAttacks.rows().forEach { row ->
            row.attacks.forEach { attack ->
                attack.hits.filter { it.offense == "dragonfire" }.forEach { _ ->
                    dragonfireHits++
                    val key = "${row.combatDef}:${attack.id}"
                    val want = expected[key] ?: expected[row.combatDef] ?: error("unclassified dragonfire attack $key (npc ${row.id})")
                    assertEquals(want, DragonfireTable.typeFor(row.combatDef, attack.id), key)
                }
            }
        }
        assertEquals(42, dragonfireHits, "dragonfire hits in npc-attacks.json")
    }
}
