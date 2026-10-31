package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.potion.RemovedPotions
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N474 (2026-10-31): the Grand Exchange house sold Overload (4) and Blighted overload (4) although the owner took them out of play
 * (2026-09-15 / 09-29), and the excluded trinkets (2026-09-19). The GE refuses them now, noted forms too; held copies are NOT taken
 * at login (the owner keeps them where they are), and normal potions / the Trinket of fairies stay on the GE.
 */
class RemovedPotionsExchangeTests {
    private fun tradeable(id: Int) = ItemDef(id).apply { tradeable = true }

    private fun notedOf(id: Int) =
        ItemDef(id + 1).apply {
            tradeable = true
            noteTemplateId = 799
            noteLinkId = id
        }

    @Test
    fun `the GE refuses every removed potion and excluded trinket, noted forms too`() {
        val blocked = RemovedPotions.itemIds + BreachLoot.EXCLUDED_TRINKETS
        listOf(Items.OVERLOAD_4, Items.EXTREME_ATTACK_3, Items.PRAYER_RENEWAL_4, Items.DIVINE_SUPER_COMBAT_POTION_4, Items.TRINKET_OF_AVARICE).forEach {
            assertTrue(it in blocked, "$it in the set")
        }
        blocked.forEach { id ->
            assertFalse(GrandExchangeInterface.exchangeable(tradeable(id)), "$id still exchangeable")
            assertTrue(RemovedItems.isExchangeBlocked(notedOf(id)), "noted $id")
            assertTrue(id in RemovedItems.geHiddenIds, "$id still in the GE search")
        }
    }

    @Test
    fun `held copies are kept and normal items stay on the GE`() {
        (RemovedPotions.itemIds + BreachLoot.EXCLUDED_TRINKETS).forEach { id ->
            assertFalse(RemovedItems.isUnobtainable(tradeable(id)), "$id would be purged at login - the owner keeps held copies")
        }
        listOf(Items.SUPER_RESTORE_4, Items.SARADOMIN_BREW_4, Items.SUPER_COMBAT_POTION_4, Items.TRINKET_OF_FAIRIES, Items.ABYSSAL_WHIP).forEach { id ->
            assertTrue(GrandExchangeInterface.exchangeable(tradeable(id)), "$id must stay exchangeable")
            assertFalse(id in RemovedItems.geHiddenIds, "$id must stay in the GE search")
        }
    }
}
