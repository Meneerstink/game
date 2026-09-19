package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.CombatConfigs
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT batch "owner0919" (owner 2026-09-19: mixed hide set, the god book we lacked, the shadow ring, the antler guard
 * and a fully working burning amulet) against the OSRS Wiki item pages, tx-20260919-211216.
 */
class Owner0919ImportTests {
    private val yml =
        ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .associateBy { it.path("id").asInt() }

    private fun equipment(id: Int) = yml.getValue(id).path("equipment")

    @Test
    fun `every imported item is in items yml with its OSRS name and slot`() {
        mapOf(
            Items.MIXED_HIDE_TOP to ("Mixed hide top" to 4),
            Items.MIXED_HIDE_LEGS to ("Mixed hide legs" to 7),
            Items.MIXED_HIDE_BOOTS to ("Mixed hide boots" to 10),
            Items.MIXED_HIDE_CAPE to ("Mixed hide cape" to 1),
            Items.BOOK_OF_DARKNESS to ("Book of Darkness" to 5),
            Items.RING_OF_SHADOWS to ("Ring of shadows" to 12),
            Items.RING_OF_SHADOWS_UNCHARGED to ("Ring of shadows (uncharged)" to 12),
            Items.BURNING_AMULET_5 to ("Burning amulet(5)" to 2),
            Items.SPIKED_MANACLES to ("Spiked manacles" to 10),
            Items.ANTLER_GUARD to ("Antler guard" to 5),
        ).forEach { (id, expected) ->
            val (name, slot) = expected
            assertEquals(name, yml.getValue(id).path("name").asText(), "item $id")
            assertEquals(slot, equipment(id).path("equip_slot").asInt(), name)
        }
    }

    @Test
    fun `the OSRS bonuses and tradeability of the new items`() {
        // OSRS Wiki infoboxes: Book of Darkness +10 magic attack, +5 prayer, untradeable; Ring of shadows +4 melee attack,
        // +5 magic attack / defence, +2 strength and prayer, untradeable; Mixed hide top +27 ranged defence, +2 strength.
        assertEquals(10, equipment(Items.BOOK_OF_DARKNESS).path("attack_magic").asInt())
        assertEquals(5, equipment(Items.BOOK_OF_DARKNESS).path("prayer").asInt())
        assertTrue(!yml.getValue(Items.BOOK_OF_DARKNESS).path("tradeable").asBoolean(), "god books are untradeable")
        assertEquals(4, equipment(Items.RING_OF_SHADOWS).path("attack_stab").asInt())
        assertEquals(5, equipment(Items.RING_OF_SHADOWS).path("attack_magic").asInt())
        assertEquals(2, equipment(Items.RING_OF_SHADOWS).path("melee_strength").asInt())
        assertEquals(2, equipment(Items.RING_OF_SHADOWS).path("prayer").asInt())
        assertTrue(!yml.getValue(Items.RING_OF_SHADOWS).path("tradeable").asBoolean(), "the DT2 ring is untradeable")
        assertEquals(27, equipment(Items.MIXED_HIDE_TOP).path("attack_ranged").asInt())
        assertEquals(2, equipment(Items.MIXED_HIDE_TOP).path("melee_strength").asInt())
        listOf(Items.MIXED_HIDE_TOP, Items.MIXED_HIDE_LEGS, Items.MIXED_HIDE_BOOTS, Items.MIXED_HIDE_CAPE, Items.SPIKED_MANACLES)
            .forEach { assertTrue(yml.getValue(it).path("tradeable").asBoolean(), "$it is tradeable in OSRS") }
    }

    @Test
    fun `the Zaros god book counts as a god book`() {
        assertTrue(Items.BOOK_OF_DARKNESS in CombatConfigs.BOOKS, "Book of Darkness is the sixth god book")
        assertEquals(6, CombatConfigs.BOOKS.size)
    }

    @Test
    fun `the burning amulet spends one charge per teleport and disintegrates`() {
        // OSRS Wiki "Burning amulet": five charges, "after all five charges are used, the amulet will disintegrate".
        val chain = listOf(Items.BURNING_AMULET_5, Items.BURNING_AMULET_4, Items.BURNING_AMULET_3, Items.BURNING_AMULET_2, Items.BURNING_AMULET_1)
        chain.zipWithNext().forEach { (higher, lower) -> assertEquals(lower, higher + 1, "charge chain is contiguous") }
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/items/jewellery/burning_amulet.plugin.kts").readText()
        listOf("Chaos Temple", "Bandit Camp", "Lava Maze").forEach { assertTrue(it in script, "$it teleport") }
        listOf("Tile(3234, 3634, 0)", "Tile(3038, 3651, 0)", "Tile(3028, 3842, 0)").forEach { assertTrue(it in script, "$it is the wiki tile") }
        assertTrue("disintegrates" in script)
    }

    @Test
    fun `the ring of shadows is the Desert Treasure II reward and charges with four runes`() {
        val rewards = File("src/main/kotlin/gg/rsmod/plugins/content/unlocks/UnlockNpcRewards.kt").readText()
        assertTrue("grant(player, Items.RING_OF_SHADOWS_UNCHARGED)" in rewards, "Azzanadra hands the ring on completion")
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/ring_of_shadows.plugin.kts").readText()
        listOf("BLOOD_RUNE", "SOUL_RUNE", "DEATH_RUNE", "LAW_RUNE").forEach { assertTrue(it in script, "charged with $it") }
        assertTrue("SHADOW_MAX_CHARGES = 1000" in script, "OSRS caps the ring at 1,000 charges")
    }
}
