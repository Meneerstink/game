package gg.rsmod.plugins.content.scrolls

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

class ClueGuardianPvpTests {
    private val world = mockk<World>(relaxed = true)
    private var cycle = 100
    private var nextNpc = 0
    @BeforeTest fun setup() {
        mockkObject(TreasureTrailGuardians)
        every { TreasureTrailGuardians.owner(any()) } returns null
        every { TreasureTrailGuardians.session(any()) } returns null
        every { world.currentCycle } answers { cycle }
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(16))
        mockkStatic("gg.rsmod.plugins.api.ext.TileExtKt")
        every { any<Tile>().isMulti(world) } returns false
    }
    @AfterTest fun cleanup() = unmockkAll()
    private fun player(at: Tile = Tile(3200,3800)) = mockk<Player>(relaxed=true).also {
        every { it.world } returns world
        every { it.tile } returns at
        every { it.attr } returns AttributeMap()
        every { it.timers } returns TimerMap()
        every { it.isOnline } returns true
        every { it.isDead() } returns false
    }
    private fun guardian(owner: Player) = mockk<Npc>(relaxed=true).also {
        every { it.world } returns world
        every { it.tile } returns owner.tile
        every { it.attr } returns AttributeMap()
        every { it.isDead() } returns false
        val index = ++nextNpc
        every { it.index } returns index
        world.npcs.add(it)
        every { TreasureTrailGuardians.owner(it) } returns owner
        every { TreasureTrailGuardians.session(owner) } returns mockk(relaxed=true)
    }
    private fun hit(npc: Npc, target: Player) {
        target.timers[Combat.PJ_TIMER]=Combat.PJ_TICKS
        target.attr[LAST_HIT_BY_ATTR]=WeakReference(npc)
    }

    @Test fun `only the owners actual clue guardian yields to a player in Wilderness singles`() {
        val a=player();val b=player();val guard=guardian(a)
        hit(guard,a)
        assertFalse(a.isAttackedByOtherThan(b))
        assertFalse(a.isAttackedByOtherThan(guard))
        val stranger=player();hit(guard,stranger)
        assertTrue(stranger.isAttackedByOtherThan(b))
        every { TreasureTrailGuardians.owner(guard) } returns null
        assertTrue(a.isAttackedByOtherThan(b), "ordinary/despawned copies do not acquire the clue exception")
    }

    @Test fun `wizard hits never expose an existing player fight to a third account or its delayed projectile`() {
        val a=player();val b=player();val third=player();val guard=guardian(a)
        ClueGuardianPvp.recordAttack(b,a)
        hit(guard,a)
        assertFalse(a.isAttackedByOtherThan(b))
        assertTrue(a.isAttackedByOtherThan(third))
        assertTrue(b.isAttackedByOtherThan(third))
        assertFalse(ClueGuardianPvp.allowsImpact(third,a))
        assertFalse(ClueGuardianPvp.allowsImpact(a,third))
        assertTrue(ClueGuardianPvp.allowsImpact(a,b))
        ClueGuardianPvp.recordAttack(third,a)
        assertSame(b,ClueGuardianPvp.reservation(a), "a stale third attack cannot steal the reservation")
    }

    @Test fun `reservation retains ordinary PJ duration after clue cleanup then expires`() {
        val a=player();val b=player();val third=player();guardian(a)
        ClueGuardianPvp.recordAttack(a,b)
        every { TreasureTrailGuardians.session(a) } returns null
        cycle+=Combat.PJ_TICKS-1
        assertFalse(ClueGuardianPvp.allowsImpact(third,a))
        cycle++
        assertTrue(ClueGuardianPvp.allowsImpact(third,a))
        assertNull(ClueGuardianPvp.reservation(a))
    }

    @Test fun `ordinary NPCs outside Wilderness and real multi areas retain ordinary rules`() {
        val a=player(Tile(3200,3200));val b=player(Tile(3200,3200));val guard=guardian(a)
        hit(guard,a)
        assertTrue(a.isAttackedByOtherThan(b))
        ClueGuardianPvp.recordAttack(a,b)
        assertNull(ClueGuardianPvp.reservation(a))
        val wild=player();val other=player();guardian(wild)
        every { any<Tile>().isMulti(world) } returns true
        ClueGuardianPvp.recordAttack(wild,other)
        assertNull(ClueGuardianPvp.reservation(wild))
    }

    @Test fun `offline or dead opponents cannot reserve another players clue fight`() {
        val a=player();val b=player();val c=player();guardian(a)
        ClueGuardianPvp.recordAttack(a,b)
        every { b.isOnline } returns false
        assertTrue(ClueGuardianPvp.allowsImpact(c,a))
        every { b.isOnline } returns true
        every { b.isDead() } returns true
        assertTrue(ClueGuardianPvp.allowsImpact(c,a))
    }
}
