package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS-IMPORT Staff of the dead family and Power of Death against the OSRS Wiki pages. */
class StaffOfTheDeadTests {
    @Test
    fun `scales charge the toxic staff up to 11000 and it reverts to uncharged at 0`() {
        val uncharged = Item(Items.TOXIC_STAFF_UNCHARGED)
        assertEquals(11_000, StaffOfTheDead.scalesToAdd(uncharged, 20_000))
        val charged = StaffOfTheDead.withScales(uncharged, 10)
        assertEquals(Items.TOXIC_STAFF_OF_THE_DEAD, charged.id)
        assertEquals(10, StaffOfTheDead.scales(charged))
        assertEquals(10_990, StaffOfTheDead.scalesToAdd(charged, 20_000))
        assertEquals(Items.TOXIC_STAFF_UNCHARGED, StaffOfTheDead.withScales(charged, 0).id)
        assertEquals(Items.TOXIC_STAFF_UNCHARGED, StaffOfTheDead.withScales(charged, -5).id, "a last use below 10 scales empties it")
        assertEquals(10, StaffOfTheDead.SCALES_PER_USE)
        assertEquals(100, StaffOfTheDead.SCALE_USE_TICKS, "one minute")
    }

    @Test
    fun `Power of Death halves melee, ends on damage without the staff and is shared by four staves`() {
        assertEquals(12 to true, StaffOfTheDead.powerOfDeathDamage(active = true, wieldingStaff = true, melee = true, damage = 25))
        assertEquals(25 to true, StaffOfTheDead.powerOfDeathDamage(active = true, wieldingStaff = true, melee = false, damage = 25))
        assertEquals(25 to false, StaffOfTheDead.powerOfDeathDamage(active = true, wieldingStaff = false, melee = true, damage = 25))
        assertEquals(0 to true, StaffOfTheDead.powerOfDeathDamage(active = true, wieldingStaff = false, melee = true, damage = 0))
        assertEquals(25 to false, StaffOfTheDead.powerOfDeathDamage(active = false, wieldingStaff = true, melee = true, damage = 25))
        assertEquals(
            // The wiki lists the staff of the dead, toxic staff, staff of light and staff of balance (imported in batch magegearb).
            setOf(Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD, Items.STAFF_OF_LIGHT, Items.STAFF_OF_BALANCE),
            StaffOfTheDead.POWER_OF_DEATH_STAVES,
        )
        assertFalse(Items.STAFF_OF_LIGHT in StaffOfTheDead.DEAD_STAVES, "the rune save and autocast rule belong to the dead staves")
        assertEquals("Spirits of deceased evildoers offer you their protection.", StaffOfTheDead.POWER_OF_DEATH_MESSAGE)
        assertEquals(7, StaffOfTheDead.RUNE_SAVE_CHANCE)
        assertEquals(0.25, StaffOfTheDead.VENOM_CHANCE)
    }

    @Test
    fun `hooks are wired into hits, rune removal, venom, combat, autocast and PvP death`() {
        val base = "src/main/kotlin/gg/rsmod/plugins/content"
        fun read(path: String) = File("$base/$path").readText()
        assertTrue("StaffOfTheDead.modifyIncomingDamage(target, hitType" in read("combat/PawnExt.kt"))
        assertFalse("world.random(1) == 0" in read("combat/PawnExt.kt").substringBefore("Tormented demons"), "the 667 50 % roll is gone")
        assertTrue("StaffOfTheDead.savesRunes(p)" in read("magic/MagicSpells.kt"))
        assertTrue("StaffOfTheDead.rollVenom(pawn, target)" in read("combat/strategy/MeleeCombatStrategy.kt"))
        assertTrue("StaffOfTheDead.rollVenom(pawn, target)" in read("combat/strategy/MagicCombatStrategy.kt"))
        assertTrue("StaffOfTheDead.onCombat(it)" in read("combat/Combat.kt"))
        assertTrue("StaffOfTheDead.isWieldingDeadStaff(player)" in read("inter/magic/magic_tab.plugin.kts"))
        assertFalse("Items.STAFF_OF_LIGHT)" in read("combat/specialattack/weapons/melee_specials.plugin.kts"), "no second special binding")
        assertTrue("Item(Items.TOXIC_STAFF_UNCHARGED, 1)" in read("mechanics/death/PvpDeathBreakables.kt"))
    }
}
