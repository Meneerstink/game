package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.formula.TargetModifiers
import io.mockk.*
import java.io.File
import kotlin.test.*

class KrystiliaTests {
    private fun player(tile: Tile = Tile(3200, 3800)): Player = mockk<Player>(relaxed = true).also { p ->
        every { p.attr } returns AttributeMap()
        every { p.tile } returns tile
        every { p.skills.getMaxLevel(any()) } returns 99
        every { p.world.gameContext.home } returns Tile(3200, 3500)
        every { p.combatLevel } returns 3
    }
    private fun npc(p: Player, name: String, tile: Tile = p.tile): Npc = mockk<Npc>(relaxed = true).also { n ->
        every { n.attr } returns AttributeMap()
        every { n.world } returns p.world
        every { n.tile } returns tile
        every { n.def } returns NpcDef(1).apply { this.name = name }
        every { n.combatDef } returns NpcCombatDef.DEFAULT.copy(slayerXp = 30.0)
    }

    @Test fun `every ordinary task amount and weight matches the independent Wiki snapshot`() {
        val rows = File("src/test/resources/wilderness-routes/krystilia.tsv").readLines().filterNot { it.startsWith('#') || it.isBlank() }
        assertEquals(36, rows.size)
        val actual = rows.map { row ->
            val (name, amount, weight) = row.split('\t')
            // A wiki row names the task (e.g. "Spiritual creature", "Revenant"), not always one of the npcs that count for it.
            val task = Krystilia.TASKS.singleOrNull { name.lowercase() in it.names }
                ?: Krystilia.TASKS.single { it.assignment.identifier.lowercase().startsWith(name.lowercase()) }
            assertEquals(amount, "${task.amount.first}-${task.amount.last}", name)
            assertEquals(weight.toInt(), task.weight, name)
            task
        }
        assertEquals(Krystilia.TASKS.toSet(), actual.toSet())
    }

    @Test fun `only Wilderness kills count with player and monster both outside Ferox`() {
        val p = player()
        val task = Krystilia.TASKS.single { it.assignment == SlayerAssignment.SPIDER }
        Krystilia.assign(p, task, 70, 12345)
        val n = npc(p, "Spindel")
        assertTrue(Krystilia.matches(p, n))
        assertTrue(TargetModifiers.isCurrentSlayerTask(p, n))
        for (tile in listOf(Tile(3200, 3500), Tile(3151, 3635))) {
            every { p.tile } returns tile
            assertFalse(Krystilia.matches(p, n))
            assertFalse(TargetModifiers.isCurrentSlayerTask(p, n))
            p.handleDecrease(n)
            assertEquals(70, p.attr[SLAYER_AMOUNT])
        }
        every { p.tile } returns Tile(3200, 3800)
        every { n.tile } returns Tile(3151, 3635)
        assertFalse(Krystilia.matches(p, n))
        every { n.tile } returns Tile(3200, 3500)
        assertFalse(Krystilia.matches(p, n))
    }

    @Test fun `final kill completes once and keeps the normal task streak independent`() {
        val p = player()
        p.attr[CONSECUTIVE_SLAYER_TASKS] = 41
        p.attr[Krystilia.STREAK] = 9
        // Set a final remaining kill after a normally sized assignment.
        Krystilia.assign(p, Krystilia.TASKS.single { it.assignment == SlayerAssignment.SKELETON }, 65, 12345)
        p.attr[SLAYER_AMOUNT] = 1
        val n = npc(p, "Skeleton")
        p.handleDecrease(n)
        assertEquals(10, p.attr[Krystilia.STREAK])
        assertEquals(41, p.attr[CONSECUTIVE_SLAYER_TASKS])
        assertNull(p.attr[SLAYER_ASSIGNMENT])
        assertNull(p.attr[SLAYER_AMOUNT])
        p.handleDecrease(n)
        verify(exactly = 1) { p.addSlayerPoints(125) }
    }

    @Test fun `all point milestones start at fifth completion and use highest applicable interval`() {
        val examples = mapOf(1 to 0, 4 to 0, 5 to 25, 9 to 25, 10 to 125, 50 to 375, 100 to 625, 250 to 875, 500 to 875, 1000 to 1250)
        examples.forEach { (streak, points) -> assertEquals(points, Krystilia.points(streak)) }
    }

    @Test fun `paid skip clears Wilderness identity but preserves both task streaks`() {
        val p = player()
        p.attr[CONSECUTIVE_SLAYER_TASKS] = 19
        p.attr[Krystilia.STREAK] = 7
        Krystilia.assign(p, Krystilia.TASKS.single { it.assignment == SlayerAssignment.REVENANT }, 40, 12345)
        p.removeSlayerAssignment()
        assertFalse(Krystilia.active(p))
        assertNull(p.attr[Krystilia.TASK])
        assertEquals(7, p.attr[Krystilia.STREAK])
        assertEquals(19, p.attr[CONSECUTIVE_SLAYER_TASKS])
    }

    @Test fun `a KBD or lava dragon never substitutes for a black dragon`() {
        val p = player()
        Krystilia.assign(p, Krystilia.TASKS.single { it.assignment == SlayerAssignment.BLACK_DRAGON }, 8, 12345)
        assertTrue(Krystilia.matches(p, npc(p, "Black dragon")))
        listOf("King Black Dragon", "Lava dragon", "Revenant dragon").forEach { assertFalse(Krystilia.matches(p, npc(p, it))) }
    }

    @Test fun `unimported OSRS NPC id never binds to the 667 Fistandantilus`() {
        val world = mockk<World>(relaxed = true)
        every { world.definitions.getAll(NpcDef::class.java) } returns mapOf(7663 to NpcDef(7663).apply { name = "Fistandantilus" })
        assertNull(Krystilia.npcId(world))
        every { world.definitions.getAll(NpcDef::class.java) } returns mapOf(12345 to NpcDef(12345).apply { name = "Krystilia" })
        assertEquals(12345, Krystilia.npcId(world))
    }
}
