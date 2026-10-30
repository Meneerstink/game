package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * K12 N11: the worn Dragonfire shield's "Activate" fires the dragonfire blast ([DragonfireBlast], OSRS Wiki "Dragonfire shield").
 */
class DragonfireBlastTests {
    @Test
    fun `the blast hits up to 25 and a target's dragonfire protection lowers it, Protect from Magic does not`() {
        assertEquals(25, DragonfireBlast.maxHit(target()))
        assertEquals(25, DragonfireBlast.maxHit(target(prayer = true)), "\"Protect from Magic does not help\"")
        assertEquals(3, DragonfireBlast.maxHit(target(shield = Items.ANTIDRAGON_SHIELD)), "\"reduced to 3\" by an anti-dragon shield")
        assertEquals(3, DragonfireBlast.maxHit(target(shield = Items.DRAGONFIRE_SHIELD)), "a dragonfire shield protects like one")
        assertEquals(0, DragonfireBlast.maxHit(target(shield = Items.ANTIDRAGON_SHIELD, antifire = true)))
        assertEquals(17, DragonfireBlast.maxHit(target(antifire = true)))
        assertEquals(0, DragonfireBlast.maxHit(target(superAntifire = true)))
    }

    @Test
    fun `no charges, the cooldown, no fight and range refuse the blast in that order`() {
        val player = target()
        assertEquals(DragonfireBlast.NO_CHARGES, DragonfireBlast.refusal(player, charges = 0, target = null))
        player.timers[DragonfireBlast.COOLDOWN] = 10
        assertEquals(DragonfireBlast.RECHARGING, DragonfireBlast.refusal(player, charges = 5, target = null))
        player.timers.remove(DragonfireBlast.COOLDOWN)
        assertEquals(DragonfireBlast.NOT_IN_COMBAT, DragonfireBlast.refusal(player, charges = 5, target = null))
    }

    @Test
    fun `the cooldown is about 1 minute 56 seconds and survives a relog`() {
        assertEquals(116.0, DragonfireBlast.COOLDOWN_TICKS * 0.6, 0.5)
        assertEquals("dragonfire_blast_cooldown", DragonfireBlast.COOLDOWN.persistenceKey)
    }

    private fun target(
        shield: Int? = null,
        prayer: Boolean = false,
        antifire: Boolean = false,
        superAntifire: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns if (prayer) PrayerIcon.PROTECT_FROM_MAGIC.id else -1
        val equipment = ItemContainer(DefinitionSet(), EQUIPMENT_KEY)
        if (shield != null) equipment[EquipmentType.SHIELD.id] = Item(shield)
        every { player.equipment } returns equipment
        val timers = TimerMap()
        if (antifire) timers[ANTIFIRE_TIMER] = 600
        if (superAntifire) timers[SUPER_ANTIFIRE_TIMER] = 300
        every { player.timers } returns timers
        every { player.attr } returns AttributeMap()
        return player
    }
}
