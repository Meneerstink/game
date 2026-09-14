package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT Amulet of blood fury against the OSRS Wiki. */
class BloodFuryTests {
    @Test
    fun `shards give 10000 charges each up to 30000 and the amulet reverts at 0`() {
        val fury = Item(Items.AMULET_OF_FURY)
        assertEquals(3, BloodFury.shardsToAdd(fury, 5), "creation shard + two more")
        val created = BloodFury.withCharges(fury, BloodFury.CHARGES_PER_SHARD)
        assertEquals(Items.AMULET_OF_BLOOD_FURY, created.id)
        assertEquals(10_000, BloodFury.charges(created))
        assertEquals(2, BloodFury.shardsToAdd(created, 5))
        assertEquals(Items.AMULET_OF_FURY, BloodFury.withCharges(created, 0).id)
    }

    @Test
    fun `20 percent chance to heal 30 percent of the melee damage, wired for every landed melee hit and PvP death`() {
        assertEquals(0.2, BloodFury.HEAL_CHANCE)
        assertEquals(0.3, BloodFury.HEAL_FRACTION)
        assertEquals(15, BloodFury.healFor(50))
        assertEquals(0, BloodFury.healFor(3), "floor(0.9) = 0")
        val pawnExt = File("src/main/kotlin/gg/rsmod/plugins/content/combat/PawnExt.kt").readText()
        assertTrue("this is Player && hitType == HitType.MELEE && executeHit" in pawnExt && "BloodFury.onMeleeHit(attacker" in pawnExt)
        assertTrue("Item(Items.AMULET_OF_FURY, 1)" in File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/PvpDeathBreakables.kt").readText())
    }
}
