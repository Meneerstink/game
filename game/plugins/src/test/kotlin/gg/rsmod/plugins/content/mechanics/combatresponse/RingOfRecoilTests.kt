package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.RING_OF_RECOIL_CHARGE_ATTR
import gg.rsmod.game.model.combat.DamageMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage for [RingOfRecoil] (P6, 2026-09-02 autonomous run - see RSPS_DECISIONS.md for the
 * full sourcing note): floor(10% of damage) + 1 reflected onto the attacker, credited to the
 * attacker's [DamageMap] against the target (kill-attribution correctness), and a 40-damage
 * cumulative charge that shatters the ring once exhausted.
 */
class RingOfRecoilTests {
    @Test
    fun `no ring equipped does not reflect`() {
        val attacker = newPlayer()
        val target = newPlayer()

        RingOfRecoil.onIncomingHit(attacker, target, damage = 57)

        assertEquals(0, attacker.damageMap.getDamageFrom(target))
        assertNull(target.attr[RING_OF_RECOIL_CHARGE_ATTR])
    }

    @Test
    fun `ring equipped reflects floor 10 percent plus 1 and credits the attacker's damage map`() {
        val attacker = newPlayer()
        val target = newPlayer(ringEquipped = true)

        RingOfRecoil.onIncomingHit(attacker, target, damage = 57)

        // floor(57 / 10) + 1 = 6
        assertEquals(6, attacker.damageMap.getDamageFrom(target))
        assertEquals(6, target.attr[RING_OF_RECOIL_CHARGE_ATTR])
    }

    @Test
    fun `charge accumulates across hits and shatters once it reaches the 40 damage limit`() {
        val attacker = newPlayer()
        val target = newPlayer(ringEquipped = true)

        // (30/10)+1 = 4 per hit; 9 hits = 36 charge, still intact.
        repeat(9) { RingOfRecoil.onIncomingHit(attacker, target, damage = 30) }
        assertEquals(36, target.attr[RING_OF_RECOIL_CHARGE_ATTR])
        assertEquals(Items.RING_OF_RECOIL, target.getEquipment(EquipmentType.RING)?.id)

        // One more hit pushes cumulative charge to 40 -> shatters.
        RingOfRecoil.onIncomingHit(attacker, target, damage = 30)

        assertNull(target.attr[RING_OF_RECOIL_CHARGE_ATTR])
        assertNull(target.getEquipment(EquipmentType.RING))
    }

    @Test
    fun `npc target is never affected since the ring is a player-only equipment slot`() {
        val attacker = newPlayer()
        val npcTarget = mockk<Npc>(relaxed = true)
        every { npcTarget.attr } returns AttributeMap()

        RingOfRecoil.onIncomingHit(attacker, npcTarget, damage = 57)

        assertEquals(0, attacker.damageMap.getDamageFrom(npcTarget))
    }

    private fun newPlayer(ringEquipped: Boolean = false): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.damageMap } returns DamageMap()
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (ringEquipped) {
            equipment[EquipmentType.RING.id] = Item(Items.RING_OF_RECOIL)
        }
        every { player.equipment } returns equipment
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
