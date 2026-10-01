package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Owner 2026-09-29: revenant chainmaces and sceptres (rules in [RevenantWeapons], OSRS Wiki item pages). */
class RevenantWeaponsTests {
    @Test
    fun `activation takes 1000 ether and the rest is stored up to 16000`() {
        val uncharged = Item(Items.VIGGORAS_CHAINMACE_U)
        assertEquals(0, RevenantWeapons.etherToTake(uncharged, 999), "below the activation deposit nothing is taken")
        assertEquals(1_000 + 16_000, RevenantWeapons.etherToTake(uncharged, 50_000))
        val charged = RevenantWeapons.charge(uncharged, 1_500)
        assertEquals(Items.VIGGORAS_CHAINMACE, charged.id)
        assertEquals(500, RevenantWeapons.ether(charged))
        assertEquals(15_500, RevenantWeapons.etherToTake(charged, 20_000))
    }

    @Test
    fun `uncharge returns the stored ether and the activation deposit`() {
        val charged = RevenantWeapons.charge(Item(Items.ACCURSED_SCEPTRE_U), 3_000)
        val (uncharged, ether) = RevenantWeapons.uncharge(charged)
        assertEquals(Items.ACCURSED_SCEPTRE_U, uncharged.id)
        assertEquals(3_000, ether)
        assertEquals(0, RevenantWeapons.ether(uncharged))
    }

    @Test
    fun `swap keeps the ether and toggles the autocasting variant`() {
        val charged = RevenantWeapons.charge(Item(Items.THAMMARONS_SCEPTRE_U), 1_200)
        val swapped = RevenantWeapons.swapped(charged)!!
        assertEquals(Items.THAMMARONS_SCEPTRE_A, swapped.id)
        assertEquals(200, RevenantWeapons.ether(swapped))
        assertEquals(Items.THAMMARONS_SCEPTRE, RevenantWeapons.swapped(swapped)!!.id)
        assertNull(RevenantWeapons.swapped(Item(Items.URSINE_CHAINMACE)), "chainmaces have no Swap")
    }
}
