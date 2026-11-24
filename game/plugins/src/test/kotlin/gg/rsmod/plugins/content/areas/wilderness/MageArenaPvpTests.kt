package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.*
import io.mockk.*
import java.lang.ref.WeakReference
import kotlin.test.*

class MageArenaPvpTests {
    private val world = mockk<World>(relaxed = true)
    private var cycle = 100
    private var nextNpc = 0

    @BeforeTest fun setup() {
        mockkObject(MageArena, MageArenaIIEncounter)
        every { MageArena.ownsDuelNpc(any(), any()) } returns false
        every { MageArena.dueling(any()) } returns false
        every { MageArenaIIEncounter.stateOf(any()) } returns null
        every { MageArenaIIEncounter.active(any(), any()) } returns null
        every { world.currentCycle } answers { cycle }
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(32))
        mockkStatic("gg.rsmod.plugins.api.ext.TileExtKt")
        every { any<Tile>().isMulti(world) } returns false
    }

    @AfterTest fun cleanup() = unmockkAll()

    private fun player(at: Tile = Tile(3104, 3933)) = mockk<Player>(relaxed = true).also {
        every { it.world } returns world
        every { it.tile } returns at
        every { it.attr } returns AttributeMap()
        every { it.timers } returns TimerMap()
        every { it.isOnline } returns true
        every { it.isDead() } returns false
    }

    private fun guardian(owner: Player, ma2: Boolean): Npc = mockk<Npc>(relaxed = true).also {
        every { it.world } returns world
        every { it.tile } returns owner.tile
        every { it.attr } returns AttributeMap()
        every { it.isDead() } returns false
        val index = ++nextNpc
        every { it.index } returns index
        world.npcs.add(it)
        if (ma2) {
            val state = MageArenaIIEncounter.State(MageArenaIIEncounter.FOLLOWERS.first(), WeakReference(owner), 0)
            every { MageArenaIIEncounter.stateOf(it) } returns state
            every { MageArenaIIEncounter.active(owner, MageArenaII.God.SARADOMIN) } returns it
        } else {
            every { MageArena.ownsDuelNpc(owner, it) } returns true
            every { MageArena.dueling(owner) } returns true
        }
    }

    private fun npcHit(npc: Npc, target: Player) {
        target.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        target.attr[LAST_HIT_BY_ATTR] = WeakReference(npc)
    }

    @Test fun `only the owners actual MA1 or MA2 opponent yields its NPC reservation to PvP`() {
        for (ma2 in listOf(false, true)) {
            val a = player(); val b = player(); val npc = guardian(a, ma2)
            npcHit(npc, a)
            assertFalse(a.isAttackedByOtherThan(b))
            val stranger = player()
            npcHit(npc, stranger)
            assertTrue(stranger.isAttackedByOtherThan(b), "another player's opponent is not an exception")
        }
    }

    @Test fun `continuing arena hits cannot admit a third PKer or stale projectile and actual boss keeps fighting`() {
        for (ma2 in listOf(false, true)) {
            val a = player(); val b = player(); val third = player(); val npc = guardian(a, ma2)
            MageArenaPvp.recordAttack(b, a)
            npcHit(npc, a)
            assertFalse(a.isAttackedByOtherThan(b))
            assertFalse(a.isAttackedByOtherThan(npc))
            assertTrue(a.isAttackedByOtherThan(third))
            assertTrue(b.isAttackedByOtherThan(third))
            assertFalse(MageArenaPvp.allowsImpact(third, a))
            assertFalse(Combat.canEngage(third, a), "manual spells and instant specials share this gate")
            MageArenaPvp.recordAttack(third, a)
            assertSame(b, MageArenaPvp.reservation(a))
            every { MageArena.dueling(a) } returns false
            every { MageArenaIIEncounter.active(a, any()) } returns null
            cycle += Combat.PJ_TICKS - 1
            assertFalse(MageArenaPvp.allowsImpact(third, a))
            cycle++
            assertTrue(MageArenaPvp.allowsImpact(third, a), "ordinary PJ duration after arena cleanup")
        }
    }

    @Test fun `ordinary NPC singles safe tiles and multi combat retain existing rules`() {
        val a = player(); val b = player(); val npc = guardian(a, false)
        every { MageArena.ownsDuelNpc(a, npc) } returns false
        every { MageArena.dueling(a) } returns false
        npcHit(npc, a)
        assertTrue(a.isAttackedByOtherThan(b), "ordinary NPC still shields in ordinary singles")
        MageArenaPvp.recordAttack(a, b)
        assertNull(MageArenaPvp.reservation(a))
        val safe = player(Tile(3200, 3200)); val safeB = player(Tile(3200, 3200))
        val safeNpc = guardian(safe, false)
        npcHit(safeNpc, safe)
        assertTrue(safe.isAttackedByOtherThan(safeB))
        MageArenaPvp.recordAttack(safe, safeB)
        assertNull(MageArenaPvp.reservation(safe))
        guardian(a, false)
        every { any<Tile>().isMulti(world) } returns true
        MageArenaPvp.recordAttack(a, b)
        assertNull(MageArenaPvp.reservation(a))
    }
}
