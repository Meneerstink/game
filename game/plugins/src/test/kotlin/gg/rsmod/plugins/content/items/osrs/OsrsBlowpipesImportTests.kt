package gg.rsmod.plugins.content.items.osrs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "blowpipes" against the OSRS Wiki blowpipe pages. */
class OsrsBlowpipesImportTests {
    @Test
    fun `every blowpipe loads darts up to its tier and only toxic ones use scales`() {
        val tiers =
            mapOf(
                Blowpipe.Pipe.TOXIC to Blowpipe.Dart.DRAGON,
                Blowpipe.Pipe.BLAZING to Blowpipe.Dart.DRAGON,
                Blowpipe.Pipe.CAMPHOR to Blowpipe.Dart.MITHRIL,
                Blowpipe.Pipe.IRONWOOD to Blowpipe.Dart.ADAMANT,
                Blowpipe.Pipe.ROSEWOOD to Blowpipe.Dart.RUNE,
            )
        assertEquals(Blowpipe.Pipe.values().toSet(), tiers.keys, "every pipe has a sourced tier")
        tiers.forEach { (pipe, strongest) ->
            Blowpipe.Dart.values().forEach { dart ->
                val load = Blowpipe.loadDarts(Item(pipe.empty), dart.itemId, 100)
                if (dart.ordinal <= strongest.ordinal) {
                    assertEquals(Blowpipe.LoadOutcome.LOADED, load.outcome, "$pipe loads $dart")
                    assertEquals(pipe.charged, load.result.id, "$pipe becomes charged")
                } else {
                    assertEquals(Blowpipe.LoadOutcome.TOO_STRONG, load.outcome, "$pipe refuses $dart")
                }
            }
        }
        val rosewood = Blowpipe.loadDarts(Item(Items.ROSEWOOD_BLOWPIPE_EMPTY), Items.RUNE_DART, 10).result
        assertTrue(Blowpipe.canFire(rosewood), "Sailing blowpipes fire without Zulrah's scales")
        val shot = Blowpipe.spendShot(rosewood, scaleRoll = 0.9, dartRoll = 0.9, capeId = null)
        assertFalse(shot.scaleUsed)
        assertEquals(26, Blowpipe.dartStrength(rosewood))
        assertEquals(Items.ROSEWOOD_BLOWPIPE_EMPTY, Blowpipe.unloadDarts(rosewood).id)
        assertEquals(Blowpipe.SAILING_NO_DARTS_MESSAGE, Blowpipe.noChargesMessage(Item(Items.CAMPHOR_BLOWPIPE)))
        val blazing = Blowpipe.loadDarts(Item(Items.BLAZING_BLOWPIPE_EMPTY), Items.DRAGON_DART, 10).result
        assertFalse(Blowpipe.canFire(blazing), "the blazing blowpipe still needs scales")
        assertEquals(Blowpipe.NO_SCALES_MESSAGE, Blowpipe.noChargesMessage(blazing))
        assertTrue(Blowpipe.Pipe.BLAZING.toxic && !Blowpipe.Pipe.ROSEWOOD.toxic)
        assertEquals(25, Blowpipe.RAPID_BURST_ENERGY)
    }

    @Test
    fun `requirements, kit rows and wiring`() {
        val yml = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
            .filter { it.path("id").asInt() in Items.BLAZING_BLOWPIPE..Items.ROSEWOOD_BLOWPIPE_EMPTY_NOTED }.associateBy { it.path("id").asInt() }
        fun reqs(id: Int) = yml.getValue(id).path("equipment").path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
        assertEquals(mapOf(4 to 75), reqs(Items.BLAZING_BLOWPIPE))
        assertEquals(mapOf(4 to 45), reqs(Items.CAMPHOR_BLOWPIPE))
        assertEquals(mapOf(4 to 55), reqs(Items.IRONWOOD_BLOWPIPE))
        assertEquals(mapOf(4 to 65), reqs(Items.ROSEWOOD_BLOWPIPE))
        // Empty blowpipes have no Wield option upstream ("options1 = Drop" / "Dismantle, Drop"): not wearable.
        listOf(Items.BLAZING_BLOWPIPE_EMPTY, Items.CAMPHOR_BLOWPIPE_EMPTY, Items.IRONWOOD_BLOWPIPE_EMPTY, Items.ROSEWOOD_BLOWPIPE_EMPTY).forEach {
            assertTrue(yml.getValue(it).path("equipment").isNull, "$it has no equipment block")
        }
        val kit = OsrsOrnamentKits.forPvpConversion(Items.BLAZING_BLOWPIPE_EMPTY)
        assertEquals(Items.TOXIC_BLOWPIPE_EMPTY, kit?.base)
        assertEquals(Items.BLOWPIPE_ORNAMENT_KIT, kit?.kit)
        assertNull(OsrsOrnamentKits.forPvpConversion(Items.BLAZING_BLOWPIPE), "the charged blazing blowpipe has its own PvP branch")
        val death = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt").readText()
        assertTrue("Item(Items.BLOWPIPE_ORNAMENT_KIT, 1)" in death)
        val specials = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/toxic_blowpipe.plugin.kts").readText()
        assertTrue("Items.TOXIC_BLOWPIPE, Items.BLAZING_BLOWPIPE" in specials && "Blowpipe.RAPID_BURST_ENERGY, Items.ROSEWOOD_BLOWPIPE" in specials)
        assertTrue("Blowpipe.isBlowpipe(weapon.id)" in File("src/main/kotlin/gg/rsmod/plugins/content/combat/CombatConfigs.kt").readText())
    }
}
