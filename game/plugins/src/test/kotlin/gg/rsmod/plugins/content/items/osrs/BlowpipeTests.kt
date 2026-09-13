package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Toxic blowpipe charge model against the OSRS Wiki item, Darts and Tanzanite fang pages. */
class BlowpipeTests {
    private fun charged(dart: Int, darts: Int, scales: Int): Item {
        val loaded = Blowpipe.loadDarts(Item(Items.TOXIC_BLOWPIPE_EMPTY), dart, darts).result
        return Blowpipe.chargeScales(loaded, scales).result
    }

    @Test
    fun `every unpoisoned dart loads with its OSRS ranged strength, poisoned darts do not`() {
        val expected =
            mapOf(
                Items.BRONZE_DART to 1, Items.IRON_DART to 2, Items.STEEL_DART to 3, Items.BLACK_DART to 6, Items.MITHRIL_DART to 9,
                Items.ADAMANT_DART to 17, Items.RUNE_DART to 26, Items.AMETHYST_DART to 28, Items.DRAGON_DART to 35,
            )
        assertEquals(expected, Blowpipe.Dart.values().associate { it.itemId to it.rangedStrength })
        expected.forEach { (dart, strength) ->
            assertEquals(strength, Blowpipe.dartStrength(charged(dart, 10, 10)), "dart $dart")
        }
        assertEquals(Blowpipe.LoadOutcome.NOT_A_DART, Blowpipe.loadDarts(Item(Items.TOXIC_BLOWPIPE), Items.DRAGON_DART_P, 5).outcome)
    }

    @Test
    fun `charges cap at 16383, a different dart type is refused and the id follows the contents`() {
        val empty = Item(Items.TOXIC_BLOWPIPE_EMPTY)
        val full = Blowpipe.loadDarts(empty, Items.RUNE_DART, 20_000)
        assertEquals(16_383, full.added)
        assertEquals(Items.TOXIC_BLOWPIPE, full.result.id)
        assertEquals(Blowpipe.LoadOutcome.FULL, Blowpipe.loadDarts(full.result, Items.RUNE_DART, 1).outcome)
        assertEquals(Blowpipe.LoadOutcome.DIFFERENT_DART, Blowpipe.loadDarts(full.result, Items.DRAGON_DART, 1).outcome)
        assertEquals(16_383, Blowpipe.chargeScales(full.result, 99_999).added)
        val withScales = charged(Items.DRAGON_DART, 5, 7)
        val unloaded = Blowpipe.unloadDarts(withScales)
        assertEquals(Items.TOXIC_BLOWPIPE, unloaded.id)
        assertNull(Blowpipe.dart(unloaded))
        assertEquals(7, Blowpipe.scales(unloaded))
        assertEquals(Items.TOXIC_BLOWPIPE_EMPTY, Blowpipe.uncharge(withScales).id)
        assertEquals(0, Blowpipe.scales(Blowpipe.uncharge(withScales)))
    }

    @Test
    fun `a shot keeps its scale one time in three and Ava's devices save darts at the wiki rates`() {
        val pipe = charged(Items.DRAGON_DART, 10, 10)
        assertFalse(Blowpipe.spendShot(pipe, scaleRoll = 0.33, dartRoll = 0.99, capeId = null).scaleUsed)
        assertTrue(Blowpipe.spendShot(pipe, scaleRoll = 0.34, dartRoll = 0.99, capeId = null).scaleUsed)
        assertTrue(Blowpipe.spendShot(pipe, scaleRoll = 0.5, dartRoll = 0.0, capeId = null).dartUsed, "no device: darts always used")
        assertFalse(Blowpipe.spendShot(pipe, 0.5, dartRoll = 0.59, capeId = Items.AVAS_ATTRACTOR).dartUsed)
        assertTrue(Blowpipe.spendShot(pipe, 0.5, dartRoll = 0.60, capeId = Items.AVAS_ATTRACTOR).dartUsed)
        assertFalse(Blowpipe.spendShot(pipe, 0.5, dartRoll = 0.71, capeId = Items.AVAS_ACCUMULATOR).dartUsed)
        val spent = Blowpipe.spendShot(pipe, 0.5, 0.5, null).result
        assertEquals(9, Blowpipe.darts(spent))
        assertEquals(9, Blowpipe.scales(spent))
        val last = Blowpipe.spendShot(charged(Items.DRAGON_DART, 1, 1), 0.5, 0.5, null).result
        assertEquals(Items.TOXIC_BLOWPIPE_EMPTY, last.id, "no darts and no scales left: the empty blowpipe")
        assertFalse(Blowpipe.canFire(Blowpipe.unloadDarts(pipe)))
        assertTrue(Blowpipe.canFire(pipe))
    }

    @Test
    fun `Toxic Siphon and creation constants match the wiki`() {
        assertEquals(50, Blowpipe.SPECIAL_ENERGY)
        assertEquals(2.0, Blowpipe.SIPHON_ACCURACY)
        assertEquals(1.5, Blowpipe.SIPHON_DAMAGE)
        assertEquals(7, Blowpipe.siphonHeal(15))
        assertEquals(0.25, Blowpipe.VENOM_CHANCE)
        assertEquals(78, Blowpipe.FLETCHING_LEVEL)
        assertEquals(120.0, Blowpipe.FLETCHING_XP)
        assertEquals(20_000, Blowpipe.DISMANTLE_SCALES)
    }
}
