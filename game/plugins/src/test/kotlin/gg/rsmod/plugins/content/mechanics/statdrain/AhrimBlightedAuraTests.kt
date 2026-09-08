package gg.rsmod.plugins.content.mechanics.statdrain

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Coverage for [AhrimBlightedAura] (stat-drain further-foundations pass, 2026-09-02 autonomous
 * run - see `RSPS_DECISIONS.md` for the full sourcing write-up: 25% chance per landed magic hit
 * while wearing the full Ahrim's set to lower the target's Strength by a flat 5, floored at 1).
 */
class AhrimBlightedAuraTests {
    @Test
    fun `lowers a player target's Strength by 5 when the proc succeeds while wearing the full set`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val target = newPlayerTarget(strength = 50)

        AhrimBlightedAura.onDamageDealt(attacker, target, CombatClass.MAGIC)

        assertEquals(45, target.skills.getCurrentLevel(Skills.STRENGTH))
    }

    @Test
    fun `lowers an npc target's Strength through its Stats block, floored at 0`() {
        // Unlike the player-side SkillSet.alterCurrentLevel (floored at 1), Npc.Stats'
        // equivalent floors at 0 - confirmed directly in its source (Npc.kt).
        val attacker = newAttacker(fullSet = true, procs = true)
        val npc = mockk<Npc>(relaxed = true)
        val stats =
            Npc.Stats(5).apply {
                setMaxLevel(NpcSkills.STRENGTH, 4)
                setCurrentLevel(NpcSkills.STRENGTH, 4)
            }
        every { npc.stats } returns stats

        AhrimBlightedAura.onDamageDealt(attacker, npc, CombatClass.MAGIC)

        assertEquals(0, stats.getCurrentLevel(NpcSkills.STRENGTH))
    }

    @Test
    fun `does nothing when the proc chance fails`() {
        val attacker = newAttacker(fullSet = true, procs = false)
        val target = newPlayerTarget(strength = 50)

        AhrimBlightedAura.onDamageDealt(attacker, target, CombatClass.MAGIC)

        assertEquals(50, target.skills.getCurrentLevel(Skills.STRENGTH))
    }

    @Test
    fun `does nothing without the full Ahrim set equipped`() {
        val attacker = newAttacker(fullSet = false, procs = true)
        val target = newPlayerTarget(strength = 50)

        AhrimBlightedAura.onDamageDealt(attacker, target, CombatClass.MAGIC)

        assertEquals(50, target.skills.getCurrentLevel(Skills.STRENGTH))
    }

    @Test
    fun `does nothing on a non-magic hit`() {
        val attacker = newAttacker(fullSet = true, procs = true)
        val target = newPlayerTarget(strength = 50)

        AhrimBlightedAura.onDamageDealt(attacker, target, CombatClass.MELEE)

        assertEquals(50, target.skills.getCurrentLevel(Skills.STRENGTH))
    }

    private fun newAttacker(
        fullSet: Boolean,
        procs: Boolean,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (fullSet) {
            equipment[EquipmentType.HEAD.id] = Item(Items.AHRIMS_HOOD)
            equipment[EquipmentType.WEAPON.id] = Item(Items.AHRIMS_STAFF)
            equipment[EquipmentType.CHEST.id] = Item(Items.AHRIMS_ROBE_TOP)
            equipment[EquipmentType.LEGS.id] = Item(Items.AHRIMS_ROBE_SKIRT)
        }
        every { player.equipment } returns equipment
        val world = mockk<World>()
        every { world.chance(1, 4) } returns procs
        every { player.world } returns world
        return player
    }

    private fun newPlayerTarget(strength: Int): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.STRENGTH, strength)
        every { player.skills } returns skills
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
