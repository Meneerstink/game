package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.ChaosElementalCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.ChaosElementalCombat.Attack
import gg.rsmod.plugins.content.areas.wilderness.bosses.ChaosElementalCombat.Style
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N522 nachtrun deel 1: the OSRS Chaos Elemental - OSRS Wiki infobox / drop table / Strategies, Kronos-184 bosses/ChaosElemental. */
class ChaosElementalTests : WildernessBossContractTests(Npcs.CHAOS_ELEMENTAL, 250, listOf(270, 270, 270, 270, 270), 4, 14, "Chaos Elemental", 300) {
    @Test fun `madness is picked 1 in 10 only with a free inventory slot, else confusion 1 in 12, else discord`() {
        assertEquals(Attack.MADNESS, ChaosElementalCombat.choose(freeSlot = true, madnessRoll = 0, confusionRoll = 5))
        assertEquals(Attack.CONFUSION, ChaosElementalCombat.choose(freeSlot = false, madnessRoll = 0, confusionRoll = 0))
        assertEquals(Attack.DISCORD, ChaosElementalCombat.choose(freeSlot = false, madnessRoll = 0, confusionRoll = 3))
        val madness = (0..9).count { ChaosElementalCombat.choose(true, it, 5) == Attack.MADNESS }
        assertEquals(1, madness)
        val confusion = (0..11).count { ChaosElementalCombat.choose(true, 5, it) == Attack.CONFUSION }
        assertEquals(1, confusion)
    }

    @Test fun `discord is magic 6 in 10, the rest ranged or melee half each`() {
        val styles = (0..9).flatMap { r -> (0..1).map { ChaosElementalCombat.discordStyle(r, it) } }
        assertEquals(12, styles.count { it == Style.MAGIC })
        assertEquals(4, styles.count { it == Style.RANGED })
        assertEquals(4, styles.count { it == Style.MELEE })
    }

    private fun player(worn: List<Int>, free: Int): Player {
        val p = mockk<Player>(relaxed = true)
        val equipment = mockk<ItemContainer>(relaxed = true)
        val inventory = mockk<ItemContainer>(relaxed = true)
        every { equipment[any<Int>()] } answers { if (firstArg<Int>() in worn) Item(4151) else null }
        every { inventory.freeSlotCount } returns free
        every { p.equipment } returns equipment
        every { p.inventory } returns inventory
        return p
    }

    @Test fun `madness always takes the weapon first and at most four items, one per free inventory slot`() {
        val all = ChaosElementalCombat.MADNESS_SLOTS
        val slots = ChaosElementalCombat.madnessSlots(player(all, 28)) { it }
        assertEquals(4, slots.size)
        assertEquals(EquipmentType.WEAPON.id, slots.first())
        assertEquals(listOf(EquipmentType.WEAPON.id), ChaosElementalCombat.madnessSlots(player(all, 1)) { it })
        assertTrue(ChaosElementalCombat.madnessSlots(player(all, 0)) { it }.isEmpty(), "a full inventory prevents Madness")
        val noWeapon = ChaosElementalCombat.madnessSlots(player(listOf(EquipmentType.HEAD.id, EquipmentType.BOOTS.id), 28)) { it }
        assertEquals(listOf(EquipmentType.HEAD.id, EquipmentType.BOOTS.id), noWeapon)
        val ringOnly = ChaosElementalCombat.madnessSlots(player(listOf(EquipmentType.RING.id, EquipmentType.CAPE.id), 28)) { it }
        assertTrue(ringOnly.isEmpty(), "rings and capes are not Madness slots (Kronos DISARM_SLOTS)")
    }

    @Test fun `confusion moves the player straight away from the footprint and stops before blocked ground`() {
        val f = WildernessBossTestSupport.Fixture()
        val npc = f.npc(Npcs.CHAOS_ELEMENTAL, Tile(3260, 3926))
        val collision = mockk<gg.rsmod.game.model.collision.CollisionManager>(relaxed = true)
        every { f.world.collision } returns collision
        every { collision.canStep(any(), any()) } returns true
        // East of the 3x3 footprint (x 3260..3262): pushed east.
        assertEquals(Tile(3267, 3927), ChaosElementalCombat.teleportDestination(npc, Tile(3263, 3927), 4))
        assertEquals(Tile(3264, 3927), ChaosElementalCombat.teleportDestination(npc, Tile(3263, 3927), 1))
        every { collision.canStep(Tile(3265, 3927), any()) } returns false
        assertEquals(Tile(3265, 3927), ChaosElementalCombat.teleportDestination(npc, Tile(3263, 3927), 4))
    }
}
