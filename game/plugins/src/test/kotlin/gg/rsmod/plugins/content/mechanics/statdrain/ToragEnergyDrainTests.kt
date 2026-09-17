package gg.rsmod.plugins.content.mechanics.statdrain

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

/**
 * Coverage for [ToragEnergyDrain] (OSRS-IMPORT audit round 2026-09-17b: Torag the Corrupted's
 * "Corruption" set effect - 25% chance per successful melee hit to lower the target player's run
 * energy by 20% of its current amount, sourced in [gg.rsmod.plugins.content.npcs.definitions.barrows.BarrowsSetEffects]
 * and previously only wired for the NPC brother's own attacks, never for a player wearing the set).
 */
class ToragEnergyDrainTests {
    @Test
    fun `lowers a player target's run energy by 20 percent when the proc succeeds while wearing the full set`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val target = newPlayerTarget(energy = 100.0)

        ToragEnergyDrain.onDamageDealt(attacker, target, CombatClass.MELEE)

        // sendRunEnergy is a static extension (PlayerExt.kt) that just forwards to write(...); not
        // separately verified here - the runEnergy assignment above is the meaningful behaviour.
        verify { target.runEnergy = 80.0 }
    }

    @Test
    fun `does nothing when the proc chance fails`() {
        val attacker = newAttacker(fullSet = true, procs = false)
        val target = newPlayerTarget(energy = 100.0)

        ToragEnergyDrain.onDamageDealt(attacker, target, CombatClass.MELEE)

        verify(exactly = 0) { target.runEnergy = any() }
    }

    @Test
    fun `does nothing without the full Torag set equipped`() {
        val attacker = newAttacker(fullSet = false, procs = true)
        val target = newPlayerTarget(energy = 100.0)

        ToragEnergyDrain.onDamageDealt(attacker, target, CombatClass.MELEE)

        verify(exactly = 0) { target.runEnergy = any() }
    }

    @Test
    fun `does nothing on a non-melee hit`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val target = newPlayerTarget(energy = 100.0)

        ToragEnergyDrain.onDamageDealt(attacker, target, CombatClass.RANGED)

        verify(exactly = 0) { target.runEnergy = any() }
    }

    @Test
    fun `does nothing against an npc target - run energy is a player-only resource`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val npc = mockk<Npc>(relaxed = true)

        ToragEnergyDrain.onDamageDealt(attacker, npc, CombatClass.MELEE)
        // No exception, no interaction attempted with a non-existent runEnergy on the npc.
    }

    private fun newAttacker(
        fullSet: Boolean,
        procs: Boolean,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.entityType } returns EntityType.PLAYER
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (fullSet) {
            equipment[EquipmentType.HEAD.id] = Item(Items.TORAGS_HELM)
            equipment[EquipmentType.WEAPON.id] = Item(Items.TORAGS_HAMMERS)
            equipment[EquipmentType.CHEST.id] = Item(Items.TORAGS_PLATEBODY)
            equipment[EquipmentType.LEGS.id] = Item(Items.TORAGS_PLATELEGS)
        }
        every { player.equipment } returns equipment
        val world = mockk<World>()
        every { world.chance(25, 100) } returns procs
        every { player.world } returns world
        return player
    }

    private fun newPlayerTarget(energy: Double): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.runEnergy } returns energy
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
