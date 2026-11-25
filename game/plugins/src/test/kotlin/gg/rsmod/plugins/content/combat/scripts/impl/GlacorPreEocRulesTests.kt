package gg.rsmod.plugins.content.combat.scripts.impl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.combat.*
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference

/** Q-042: Glacor rules from the pre-EoC RuneScape Wiki revisions (Glacor 2012-09-23, glacyte pages 2012). Glacors are not in OSRS. */
class GlacorPreEocRulesTests {
    /** COMBATENGAGE-FLAKY: multi comes from mocked multi.yml chunks here; a class that loaded the plugins (multiway_combat) leaves the real surface map in this JVM. */
    @kotlin.test.BeforeTest
    fun unloadMultiZones() = gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.unload()

    @Test
    fun `only the same glacor and its own glacytes share the player reservation`() {
        val world = mockk<World>(relaxed = true)
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(16))
        every { world.instanceAllocator.getMap(any()) } returns null
        every { world.getMultiCombatChunks() } returns emptySet()
        every { world.getMultiCombatRegions() } returns emptySet()
        var index = 0
        fun npc(id: Int, parent: Npc? = null): Npc = mockk<Npc>(relaxed = true).also {
            every { it.world } returns world
            every { it.tile } returns Tile(4177, 5714)
            every { it.id } returns id
            every { it.attr } returns AttributeMap()
            every { it.timers } returns TimerMap()
            every { it.index } returns ++index
            if (parent != null) it.attr[GlacorCombatScript.GLACYTE_PARENT] = WeakReference(parent)
            world.npcs.add(it)
        }
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(4177, 5715)
        every { player.isOnline } returns true
        val parent = npc(Npcs.GLACOR)
        val second = npc(Npcs.GLACOR)
        val children = GlacorCombatScript.GLACYTE_IDS.map { npc(it, parent) }
        parent.attr[GlacorCombatScript.GLACYTES] = children.toMutableList()
        val foreign = npc(Npcs.SAPPING_GLACYTE, second)
        val orphan = npc(Npcs.SAPPING_GLACYTE)
        player.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        for (last in children + parent) {
            player.attr[LAST_HIT_BY_ATTR] = WeakReference(last)
            for (next in children + parent) assertFalse(player.isAttackedByOtherThan(next))
            assertTrue(player.isAttackedByOtherThan(second))
            assertTrue(player.isAttackedByOtherThan(foreign))
            assertTrue(player.isAttackedByOtherThan(orphan))
        }
        parent.attr[LAST_HIT_BY_ATTR] = WeakReference(player)
        parent.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        val stranger = mockk<Player>(relaxed = true)
        every { stranger.attr } returns AttributeMap()
        assertTrue(parent.isAttackedByOtherThan(stranger), "another player cannot join the parent's reserved fight")
        children.forEach { assertTrue(it.isAttackedByOtherThan(stranger), "an untouched sibling shares the parent reservation") }
        parent.timers.remove(Combat.PJ_TIMER)
        children.first().attr[LAST_HIT_BY_ATTR] = WeakReference(player)
        children.first().timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        assertTrue(parent.isAttackedByOtherThan(stranger), "hitting a child keeps the parent reserved")
        assertFalse(parent.isAttackedByOtherThan(player))
        children.first().timers.remove(Combat.PJ_TIMER)
        assertFalse(parent.isAttackedByOtherThan(stranger), "no permanent owner lock after the normal PJ window")
        parent.setCombatTarget(player)
        player.attr[LAST_HIT_BY_ATTR] = WeakReference(children.first())
        assertTrue(parent.isAttackedByOtherThan(stranger), "glacytes still hitting the player keep the paused parent reserved")
        assertFalse(parent.isAttackedByOtherThan(player))
        player.timers.remove(Combat.PJ_TIMER)
        assertFalse(parent.isAttackedByOtherThan(stranger), "stale attack focus alone does not hold a reservation")
    }

    @Test
    fun `enduring last cuts damage by 60 percent, freeze needs no magic protection, enduring glacyte is passive`() {
        assertEquals(0.4, GlacorCombatScript.ENDURING_LAST_DAMAGE_MULTIPLIER)
        val script = File("src/main/kotlin/gg/rsmod/plugins/content/combat/scripts/impl/GlacorCombatScript.kt").readText()
        assertTrue("it.damage * ENDURING_LAST_DAMAGE_MULTIPLIER" in script)
        assertFalse("it.damage * 0.6)" in script)
        assertTrue("damage > 0 && !target.isProtectedFrom(CombatClass.MAGIC) && world.random(5) == 0" in script)
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/glacor.plugin.kts").readText()
        assertTrue("radius = if (id == Npcs.ENDURING_GLACYTE) 0 else 8" in plugin)
        assertTrue("hitpoints = 5000" in plugin, "glacor 5,000 LP (x10) = 500")
        assertTrue("hitpoints = 1000" in plugin, "glacytes 1,000 LP (x10) = 100")
    }
}
