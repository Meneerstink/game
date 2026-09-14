package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OSRS Wiki "Dragonfire" Icy breath: shield max 10, Protect from Magic without a shield 20, freeze only blocked by the ancient wyvern shield. */
class WyvernIcyBreathTests {
    @Test
    fun `every listed shield caps icy breath at 10 and the anti-dragon shield does not`() {
        WyvernIcyBreath.SHIELDS.forEach { assertEquals(10.0, WyvernIcyBreath.maxHit(target(shield = it), 50.0), "shield $it") }
        assertEquals(50.0, WyvernIcyBreath.maxHit(target(shield = Items.ANTIDRAGON_SHIELD), 50.0), "anti-dragon shield has no effect")
        assertEquals(10.0, WyvernIcyBreath.maxHit(target(shield = Items.ELEMENTAL_SHIELD, prayer = true), 50.0), "shield wins over prayer")
        assertEquals(20.0, WyvernIcyBreath.maxHit(target(prayer = true), 50.0), "Protect from Magic without a shield")
        assertEquals(50.0, WyvernIcyBreath.maxHit(target(), 50.0))
        assertEquals(11, WyvernIcyBreath.FREEZE_TICKS, "6.6 seconds")
    }

    @Test
    fun `only the ancient wyvern shield blocks the freeze`() {
        assertTrue(WyvernIcyBreath.blocksFreeze(target(shield = Items.ANCIENT_WYVERN_SHIELD)))
        assertTrue(WyvernIcyBreath.blocksFreeze(target(shield = Items.ANCIENT_WYVERN_SHIELD_UNCHARGED)))
        (WyvernIcyBreath.SHIELDS.toList() - WyvernIcyBreath.FREEZE_SHIELDS.toList()).forEach { assertFalse(WyvernIcyBreath.blocksFreeze(target(shield = it)), "shield $it") }
        assertFalse(WyvernIcyBreath.blocksFreeze(target(prayer = true)))
    }

    private fun target(
        shield: Int? = null,
        prayer: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns if (prayer) PrayerIcon.PROTECT_FROM_MAGIC.id else -1
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (shield != null) equipment[EquipmentType.SHIELD.id] = Item(shield)
        every { player.equipment } returns equipment
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
