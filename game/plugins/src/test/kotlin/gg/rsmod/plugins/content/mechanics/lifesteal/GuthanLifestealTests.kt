package gg.rsmod.plugins.content.mechanics.lifesteal

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [GuthanLifesteal] (lifesteal further-foundations pass, 2026-09-02 autonomous
 * run - see `RSPS_DECISIONS.md` for the full sourcing write-up: 25% chance per landed melee hit
 * while wearing the full Guthan's set to heal the wielder HP equal to the damage dealt, capped
 * at max HP).
 */
class GuthanLifestealTests {
    @Test
    fun `heals the wielder by the damage dealt when the proc succeeds while wearing the full set`() {
        val player = newPlayer(fullSet = true, procs = true)

        GuthanLifesteal.onDamageDealt(player, CombatClass.MELEE, 12)

        verify { player.alterLifepoints(value = 12, capValue = 0) }
    }

    @Test
    fun `does nothing when the proc chance fails`() {
        val player = newPlayer(fullSet = true, procs = false)

        GuthanLifesteal.onDamageDealt(player, CombatClass.MELEE, 12)

        verify(exactly = 0) { player.alterLifepoints(any(), any()) }
    }

    @Test
    fun `does nothing without the full Guthan set equipped`() {
        val player = newPlayer(fullSet = false, procs = true)

        GuthanLifesteal.onDamageDealt(player, CombatClass.MELEE, 12)

        verify(exactly = 0) { player.alterLifepoints(any(), any()) }
    }

    @Test
    fun `does nothing on a non-melee hit`() {
        val player = newPlayer(fullSet = true, procs = true)

        GuthanLifesteal.onDamageDealt(player, CombatClass.RANGED, 12)

        verify(exactly = 0) { player.alterLifepoints(any(), any()) }
    }

    @Test
    fun `does nothing when no damage was dealt`() {
        val player = newPlayer(fullSet = true, procs = true)

        GuthanLifesteal.onDamageDealt(player, CombatClass.MELEE, 0)

        verify(exactly = 0) { player.alterLifepoints(any(), any()) }
    }

    @Test
    fun `heal call relies on Player heal's own default cap to never exceed max HP`() {
        // GuthanLifesteal.onDamageDealt always calls heal(damage) with the default capValue (0),
        // which alterLifepoints already caps at getMaximumLifepoints() - so the cap itself is
        // Player.heal's existing, separately-owned behaviour and isn't re-verified here.
        val player = newPlayer(fullSet = true, procs = true)

        GuthanLifesteal.onDamageDealt(player, CombatClass.MELEE, 99)

        verify { player.alterLifepoints(value = 99, capValue = 0) }
    }

    private fun newPlayer(
        fullSet: Boolean,
        procs: Boolean,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.entityType } returns EntityType.PLAYER
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (fullSet) {
            equipment[EquipmentType.HEAD.id] = Item(Items.GUTHANS_HELM)
            equipment[EquipmentType.WEAPON.id] = Item(Items.GUTHANS_WARSPEAR)
            equipment[EquipmentType.CHEST.id] = Item(Items.GUTHANS_PLATEBODY)
            equipment[EquipmentType.LEGS.id] = Item(Items.GUTHANS_CHAINSKIRT)
        }
        every { player.equipment } returns equipment
        val world = mockk<World>()
        every { world.chance(1, 4) } returns procs
        every { player.world } returns world
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
