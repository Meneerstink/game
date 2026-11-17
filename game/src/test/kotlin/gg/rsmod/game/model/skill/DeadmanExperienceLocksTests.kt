package gg.rsmod.game.model.skill

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.EXPERIENCE_UP_SKILL_ID
import gg.rsmod.game.model.attr.LEVEL_UP_SKILL_ID
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.plugin.PluginRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.*

class DeadmanExperienceLocksTests {
    private val attr = AttributeMap()
    private val skills = SkillSet(25)
    private val varps = VarpSet(setOf(1801))
    private val plugins = mockk<PluginRepository>(relaxed = true)
    private val world = mockk<World>(relaxed = true)
    private val player = mockk<Player>(relaxed = true).also { p ->
        every { p.attr } returns attr
        every { p.skills } returns skills
        every { p.varps } returns varps
        every { p.world } returns world
        every { world.plugins } returns plugins
        every { p.timers } returns TimerMap()
        // The real member reads its own backing fields directly (not the mocked getters).
        // Seed those two values so callOriginal exercises the complete XP/orb/event path.
        listOf("skills" to skills, "varps" to varps).forEach { (name, value) ->
            Player::class.java.getDeclaredField(name).apply { isAccessible = true }.set(p, value)
        }
        every { p.addXp(any(), any(), any(), any()) } answers { callOriginal() }
        every { p.xpMultiplier(any(), any(), any()) } answers { if (secondArg<Boolean>()) 10.0 else 1.0 }
    }

    @Test
    fun `legacy and new accounts gain XP by default`() {
        (0 until 25).forEach { assertFalse(ExperienceLocks.blocks(player, it)) }
        player.addXp(0, 100.0)
        assertEquals(1000.0, skills.getCurrentXp(0))
        assertEquals(10000, varps[1801].state)
        verify(exactly = 1) { plugins.executeSkillExperienceUp(player) }
        verify(exactly = 1) { plugins.executeSkillLevelUp(player) }
    }

    @Test
    fun `all seven skills reject normal and exact reward XP before orb and task events`() {
        ExperienceLocks.setCombatDisabled(player, true)
        for (skill in 0..6) {
            player.addXp(skill, 1000.0)
            player.addXp(skill, 1000.0, modifiers = false, disableBonusExperience = true)
            assertEquals(0.0, skills.getCurrentXp(skill))
        }
        assertEquals(0, varps[1801].state)
        assertFalse(attr.has(EXPERIENCE_UP_SKILL_ID))
        assertFalse(attr.has(LEVEL_UP_SKILL_ID))
        verify(exactly = 0) { player.xpMultiplier(any(), any(), any()) }
        verify(exactly = 0) { plugins.executeSkillExperienceUp(any()) }
        verify(exactly = 0) { plugins.executeSkillLevelUp(any()) }
    }

    @Test
    fun `HP switch remains independent when combat is reenabled`() {
        ExperienceLocks.setHitpointsDisabled(player, true)
        assertTrue(ExperienceLocks.blocks(player, 3))
        assertFalse(ExperienceLocks.blocks(player, 0))
        ExperienceLocks.setCombatDisabled(player, true)
        ExperienceLocks.setHitpointsDisabled(player, false)
        assertTrue(ExperienceLocks.blocks(player, 3))
        ExperienceLocks.setCombatDisabled(player, false)
        assertFalse(ExperienceLocks.blocks(player, 3))
        ExperienceLocks.setHitpointsDisabled(player, true)
        ExperienceLocks.setCombatDisabled(player, true)
        ExperienceLocks.setCombatDisabled(player, false)
        assertTrue(ExperienceLocks.blocks(player, 3))
        player.addXp(0, 100.0, modifiers = false)
        player.addXp(3, 100.0, modifiers = false)
        assertEquals(100.0, skills.getCurrentXp(0))
        assertEquals(0.0, skills.getCurrentXp(3))
    }

    @Test
    fun `skilling and Summoning still gain XP with both locks`() {
        ExperienceLocks.setCombatDisabled(player, true)
        ExperienceLocks.setHitpointsDisabled(player, true)
        listOf(7, 18, 23).forEach { skill ->
            player.addXp(skill, 10.0, modifiers = false)
            assertEquals(10.0, skills.getCurrentXp(skill))
        }
    }

    @Test
    fun `persistent flags roundtrip through serializer key semantics and survive death cleanup`() {
        ExperienceLocks.setCombatDisabled(player, true)
        ExperienceLocks.setHitpointsDisabled(player, true)
        val loaded = AttributeMap()
        attr.toPersistentMap().forEach { (key, value) -> loaded[AttributeKey<Int>(key)] = (value as Number).toInt() }
        loaded.removeIf { it.resetOnDeath }
        val reconnected = mockk<Player>()
        every { reconnected.attr } returns loaded
        assertTrue(ExperienceLocks.combatDisabled(reconnected))
        assertTrue(ExperienceLocks.hitpointsDisabled(reconnected))
        assertEquals(setOf("deadman_combat_xp_disabled", "deadman_hitpoints_xp_disabled"), loaded.toPersistentMap().keys)
    }
}
