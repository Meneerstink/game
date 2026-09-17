package gg.rsmod.plugins.content.mechanics.statdrain

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [KarilAgilityDrain] (OSRS-IMPORT audit round 2026-09-17b: Karil the Tainted's
 * "Tainted Shot" set effect - 25% chance per successful ranged hit to lower the target player's
 * Agility by 20%, sourced in [gg.rsmod.plugins.content.npcs.definitions.barrows.BarrowsSetEffects]
 * and previously only wired for the NPC brother's own attacks, never for a player wearing the set).
 */
class KarilAgilityDrainTests {
    @Test
    fun `lowers a player target's Agility by 20 percent when the proc succeeds while wearing the full set`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val target = newPlayerTarget(agility = 50)

        KarilAgilityDrain.onDamageDealt(attacker, target, CombatClass.RANGED)

        // (50 * 0.2).toInt() = 10
        assertEquals(40, target.skills.getCurrentLevel(Skills.AGILITY))
    }

    @Test
    fun `does nothing when the proc chance fails`() {
        val attacker = newAttacker(fullSet = true, procs = false)
        val target = newPlayerTarget(agility = 50)

        KarilAgilityDrain.onDamageDealt(attacker, target, CombatClass.RANGED)

        assertEquals(50, target.skills.getCurrentLevel(Skills.AGILITY))
    }

    @Test
    fun `does nothing without the full Karil set equipped`() {
        val attacker = newAttacker(fullSet = false, procs = true)
        val target = newPlayerTarget(agility = 50)

        KarilAgilityDrain.onDamageDealt(attacker, target, CombatClass.RANGED)

        assertEquals(50, target.skills.getCurrentLevel(Skills.AGILITY))
    }

    @Test
    fun `does nothing on a non-ranged hit`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val target = newPlayerTarget(agility = 50)

        KarilAgilityDrain.onDamageDealt(attacker, target, CombatClass.MELEE)

        assertEquals(50, target.skills.getCurrentLevel(Skills.AGILITY))
    }

    @Test
    fun `does nothing against an npc target`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val npc = mockk<Npc>(relaxed = true)

        KarilAgilityDrain.onDamageDealt(attacker, npc, CombatClass.RANGED)
        // No exception, no interaction attempted with a non-existent player SkillSet on the npc.
    }

    private fun newAttacker(
        fullSet: Boolean,
        procs: Boolean,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.entityType } returns EntityType.PLAYER
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (fullSet) {
            equipment[EquipmentType.HEAD.id] = Item(Items.KARILS_COIF)
            equipment[EquipmentType.WEAPON.id] = Item(Items.KARILS_CROSSBOW)
            equipment[EquipmentType.CHEST.id] = Item(Items.KARILS_TOP)
            equipment[EquipmentType.LEGS.id] = Item(Items.KARILS_SKIRT)
        }
        every { player.equipment } returns equipment
        val world = mockk<World>()
        every { world.chance(25, 100) } returns procs
        every { player.world } returns world
        return player
    }

    private fun newPlayerTarget(agility: Int): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        val skills = SkillSet(maxSkills = 17)
        skills.setBaseLevel(Skills.AGILITY, agility)
        every { player.skills } returns skills
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
