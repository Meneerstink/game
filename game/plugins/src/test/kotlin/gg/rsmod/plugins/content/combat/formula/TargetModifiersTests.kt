package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.SLAYER_ASSIGNMENT
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [TargetModifiers] (P9, 2026-09-02 autonomous run - see `RSPS_DECISIONS.md` for
 * the full write-up). This is the extracted, target-condition-gated replacement for the
 * previously-triplicated, unconditionally-applied `getEquipmentMultiplier` in
 * `MeleeCombatFormula`/`RangedCombatFormula`/`MagicCombatFormula`; only the shared logic itself
 * is tested here, matching this project's established pattern of testing an extracted unit
 * directly rather than re-testing it through each formula's full accuracy/damage pipeline.
 */
class TargetModifiersTests {
    @Test
    fun `isUndead is true for an npc with the UNDEAD species`() {
        val npc = newNpc(species = setOf(NpcSpecies.UNDEAD))
        assertTrue(TargetModifiers.isUndead(npc))
    }

    @Test
    fun `isUndead is false for an npc without the UNDEAD species`() {
        val npc = newNpc(species = setOf(NpcSpecies.DEMON))
        assertFalse(TargetModifiers.isUndead(npc))
    }

    @Test
    fun `isUndead is false for a player target`() {
        val player = newPlayer()
        assertFalse(TargetModifiers.isUndead(player))
    }

    @Test
    fun `isCurrentSlayerTask is true when the npc's assignment matches the player's current task`() {
        val player = newPlayer(assignment = SlayerAssignment.BANSHEE)
        val npc = newNpc(assignment = SlayerAssignment.BANSHEE)
        assertTrue(TargetModifiers.isCurrentSlayerTask(player, npc))
    }

    @Test
    fun `isCurrentSlayerTask is false when the npc's assignment does not match the player's task`() {
        val player = newPlayer(assignment = SlayerAssignment.BANSHEE)
        val npc = newNpc(assignment = SlayerAssignment.ZOMBIE)
        assertFalse(TargetModifiers.isCurrentSlayerTask(player, npc))
    }

    @Test
    fun `isCurrentSlayerTask is false when the player has no current task`() {
        val player = newPlayer(assignment = null)
        val npc = newNpc(assignment = SlayerAssignment.BANSHEE)
        assertFalse(TargetModifiers.isCurrentSlayerTask(player, npc))
    }

    @Test
    fun `equipmentMultiplier applies the Salve amulet bonus only against undead`() {
        val player = newPlayer(amulet = Items.SALVE_AMULET)
        val undead = newNpc(species = setOf(NpcSpecies.UNDEAD))
        val nonUndead = newNpc(species = emptySet())

        assertEquals(7.0 / 6.0, TargetModifiers.equipmentMultiplier(player, undead), 1e-9)
        assertEquals(1.0, TargetModifiers.equipmentMultiplier(player, nonUndead), 1e-9)
    }

    @Test
    fun `equipmentMultiplier applies the Salve amulet (e) bonus only against undead`() {
        val player = newPlayer(amulet = Items.SALVE_AMULET_E)
        val undead = newNpc(species = setOf(NpcSpecies.UNDEAD))

        assertEquals(1.2, TargetModifiers.equipmentMultiplier(player, undead), 1e-9)
    }

    @Test
    fun `equipmentMultiplier applies the black mask bonus only on the matching slayer task`() {
        val player = newPlayer(head = Items.BLACK_MASK, assignment = SlayerAssignment.BANSHEE)
        val onTask = newNpc(assignment = SlayerAssignment.BANSHEE)
        val offTask = newNpc(assignment = SlayerAssignment.ZOMBIE)

        assertEquals(7.0 / 6.0, TargetModifiers.equipmentMultiplier(player, onTask), 1e-9)
        assertEquals(1.0, TargetModifiers.equipmentMultiplier(player, offTask), 1e-9)
    }

    @Test
    fun `equipmentMultiplier applies the same bonus for a slayer helmet as a black mask`() {
        val player = newPlayer(head = Items.SLAYER_HELMET, assignment = SlayerAssignment.BANSHEE)
        val onTask = newNpc(assignment = SlayerAssignment.BANSHEE)

        assertEquals(7.0 / 6.0, TargetModifiers.equipmentMultiplier(player, onTask), 1e-9)
    }

    @Test
    fun `equipmentMultiplier does not apply the black mask bonus without a current slayer task`() {
        val player = newPlayer(head = Items.BLACK_MASK, assignment = null)
        val npc = newNpc(assignment = SlayerAssignment.BANSHEE)

        assertEquals(1.0, TargetModifiers.equipmentMultiplier(player, npc))
    }

    @Test
    fun `equipmentMultiplier checks the Salve amulet before the black mask`() {
        // Both conditions are satisfied at once (undead task target, both items equipped);
        // the Salve (e) branch is checked first, so its multiplier - not the black mask's -
        // must win.
        val player =
            newPlayer(amulet = Items.SALVE_AMULET_E, head = Items.BLACK_MASK, assignment = SlayerAssignment.BANSHEE)
        val target = newNpc(species = setOf(NpcSpecies.UNDEAD), assignment = SlayerAssignment.BANSHEE)

        assertEquals(1.2, TargetModifiers.equipmentMultiplier(player, target), 1e-9)
    }

    @Test
    fun `equipmentMultiplier is 1_0 when no target condition is met`() {
        val player = newPlayer(amulet = Items.SALVE_AMULET, head = Items.BLACK_MASK, assignment = SlayerAssignment.BANSHEE)
        val target = newNpc(species = emptySet(), assignment = SlayerAssignment.ZOMBIE)

        assertEquals(1.0, TargetModifiers.equipmentMultiplier(player, target))
    }

    /**
     * OSRS-audit 2026-09-17b: the imbued Slayer helmet lineage (`SLAYER_HELMET_E`/`SLAYER_HELMET_CHARGED`/every
     * `FULL_SLAYER_HELMET*`) previously had zero ranged/magic wiring anywhere (every formula's own comment said
     * "no imbued helm exists in this cache" - false; those 5 ids were sitting unwired). The plain, unimbued
     * `SLAYER_HELMET` must NOT get the ranged/magic boost - only the melee 7/6 via [equipmentMultiplier].
     */
    @Test
    fun `hasImbuedSlayerHelmetTaskBoost is true for every imbued variant on the matching task`() {
        val onTask = newNpc(assignment = SlayerAssignment.BANSHEE)
        val imbuedIds =
            intArrayOf(
                Items.SLAYER_HELMET_E,
                Items.SLAYER_HELMET_CHARGED,
                Items.FULL_SLAYER_HELMET,
                Items.FULL_SLAYER_HELMET_E,
                Items.FULL_SLAYER_HELMET_CHARGED,
            )
        imbuedIds.forEach { id ->
            val player = newPlayer(head = id, assignment = SlayerAssignment.BANSHEE)
            assertTrue(TargetModifiers.hasImbuedSlayerHelmetTaskBoost(player, onTask), "item $id")
        }
    }

    @Test
    fun `hasImbuedSlayerHelmetTaskBoost is false for the plain unimbued slayer helmet`() {
        val player = newPlayer(head = Items.SLAYER_HELMET, assignment = SlayerAssignment.BANSHEE)
        val onTask = newNpc(assignment = SlayerAssignment.BANSHEE)
        assertFalse(TargetModifiers.hasImbuedSlayerHelmetTaskBoost(player, onTask))
    }

    @Test
    fun `hasImbuedSlayerHelmetTaskBoost is false off task even while wearing the imbued helmet`() {
        val player = newPlayer(head = Items.SLAYER_HELMET_CHARGED, assignment = SlayerAssignment.BANSHEE)
        val offTask = newNpc(assignment = SlayerAssignment.ZOMBIE)
        assertFalse(TargetModifiers.hasImbuedSlayerHelmetTaskBoost(player, offTask))
    }

    @Test
    fun `rangedAccuracyMultiplier and rangedDamageMultiplier apply the imbued slayer helmet 1_15 boost on task`() {
        val player = newPlayer(head = Items.FULL_SLAYER_HELMET_CHARGED, assignment = SlayerAssignment.BANSHEE)
        val onTask = newNpc(assignment = SlayerAssignment.BANSHEE)
        val offTask = newNpc(assignment = SlayerAssignment.ZOMBIE)

        assertEquals(1.15, TargetModifiers.rangedAccuracyMultiplier(player, onTask), 1e-9)
        assertEquals(1.15, TargetModifiers.rangedDamageMultiplier(player, onTask), 1e-9)
        assertEquals(1.0, TargetModifiers.rangedAccuracyMultiplier(player, offTask), 1e-9)
        assertEquals(1.0, TargetModifiers.rangedDamageMultiplier(player, offTask), 1e-9)
    }

    private fun newPlayer(
        amulet: Int? = null,
        head: Int? = null,
        assignment: SlayerAssignment? = null,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        amulet?.let { equipment[EquipmentType.AMULET.id] = Item(it) }
        head?.let { equipment[EquipmentType.HEAD.id] = Item(it) }
        every { player.equipment } returns equipment
        val attr = AttributeMap()
        if (assignment != null) {
            attr[SLAYER_ASSIGNMENT] = assignment.identifier
        }
        every { player.attr } returns attr
        return player
    }

    private fun newNpc(
        species: Set<Any> = emptySet(),
        assignment: SlayerAssignment? = null,
    ): Npc {
        val npc = mockk<Npc>(relaxed = true)
        // `Npc.species` is a computed property (`get() = combatDef.species`); stubbing it
        // directly avoids relying on mockk preserving that real getter body for a relaxed mock.
        every { npc.species } returns species
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(species = species, slayerAssignment = assignment)
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
