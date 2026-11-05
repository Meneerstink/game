package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
import kotlin.test.Test
import kotlin.test.assertEquals

/** N570: OSRS Wiki "Slayer's enchantment" / "Slayer's staff (e)" / "Transcript:Slayer's enchantment". */
class SlayersStaffETests {
    @Test fun `drop rate is 1 in 320 minus floor of min 300 hitpoints times 0_8, bosses 1 in 30`() {
        assertEquals(320, SlayersStaffE.denominator(0, false))
        assertEquals(312, SlayersStaffE.denominator(10, false))
        assertEquals(232, SlayersStaffE.denominator(111, false)) // floor(88.8) = 88 (Mod Ash: "floored to 88")
        assertEquals(80, SlayersStaffE.denominator(300, false))
        assertEquals(80, SlayersStaffE.denominator(2500, false)) // "treat it as though its HP had been 300"
        assertEquals(30, SlayersStaffE.denominator(250, true))
    }

    @Test fun `boosted magic dart is floor magic over 6 plus 13`() {
        assertEquals(25, SlayersStaffE.boostedMaxHit(75))
        assertEquals(29, SlayersStaffE.boostedMaxHit(99))
        assertEquals(31, SlayersStaffE.boostedMaxHit(108))
    }

    @Test fun `charges are implicit at 2500 and the last one turns it back into a Slayer's staff`() {
        assertEquals(2500, SlayersStaffE.charges(Item(Items.SLAYERS_STAFF_E)))
        assertEquals(0, SlayersStaffE.charges(Item(Items.SLAYERS_STAFF)))
        val one = SlayersStaffE.withCharges(1)
        assertEquals(Items.SLAYERS_STAFF_E, one.id)
        assertEquals(1, one.attr[ItemAttribute.CHARGES])
        assertEquals(Items.SLAYERS_STAFF, SlayersStaffE.withCharges(0).id)
        assertEquals(2500, SlayersStaffE.charges(SlayersStaffE.withCharges(9999)))
    }

    @Test fun `check and revert texts`() {
        assertEquals("That staff contains 2,500 charges. You will not be able to get them back.", SlayersStaffE.chargesLine(2500))
        assertEquals(
            "An enchanted Slayer staff requires level 75 Magic and level 55 Slayer to equip, and is untradeable. Reverting it will not give your spell parchment back.",
            SlayersStaffE.ENCHANT_INFO,
        )
        assertEquals("The spell enchants your staff. The tatty parchment crumbles to dust.", SlayersStaffE.ENCHANT_DONE)
    }

    @Test fun `a PvP killer gets the plain Slayer's staff`() {
        assertEquals(listOf(Items.SLAYERS_STAFF), PvpDeathBreakables.PVP_BASE[Items.SLAYERS_STAFF_E])
    }
}
