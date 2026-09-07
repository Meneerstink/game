package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.FAMILIAR_NPC_ID_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Skills
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase E of the re-audit: the session half of the familiar lifecycle - logout, reconnect, server
 * restart and stale-state cleanup.
 *
 * `FamiliarLifecycleTests` already covers placement and the Death's Domain rescue, and
 * `FamiliarPointsTests` covers summon, renew, expiry and the one-familiar rule. What none of them
 * touched is what happens across a **session boundary**, which is where a familiar's persisted
 * state can most easily be lost or left orphaned:
 *
 * * a familiar must survive a logout with its remaining time intact rather than being dismissed,
 * * a familiar whose persisted npc id has lost its timer is stale and must be cleaned up rather
 *   than respawned or left half-present,
 * * and a beast of burden's cargo must still be there afterwards.
 *
 * These are asserted against real state - the attribute map, the timer map and the containers -
 * rather than against the fact that a handler was called.
 */
class FamiliarSessionLifecycleTests {
    /**
     * Logout is not dismissal. `Familiar.disconnect` despawns the npc, because a dead client
     * connection cannot render one, but everything needed to bring the same familiar back has to
     * survive: its npc id and its lifetime timer.
     */
    @Test
    fun `logging out despawns the familiar but keeps the state needed to restore it`() {
        val player = newPlayer(summoningLevel = SummoningPouchData.PACK_YAK.level)
        player.inventory[0] = Item(SummoningPouchData.PACK_YAK.pouch, 1)
        assertTrue(Familiar.summon(player, SummoningPouchData.PACK_YAK))
        val timeBefore = player.timers[FAMILIAR_LIFETIME_TIMER]

        Familiar.disconnect(player)

        assertNull(Familiar.current(player), "the npc should not still be in the world after logout")
        assertEquals(
            SummoningPouchData.PACK_YAK.npc,
            player.attr[FAMILIAR_NPC_ID_ATTR],
            "the persisted npc id was lost, so the familiar could never be restored",
        )
        assertTrue(player.timers.has(FAMILIAR_LIFETIME_TIMER), "the lifetime timer was lost on logout")
        assertEquals(timeBefore, player.timers[FAMILIAR_LIFETIME_TIMER], "logout consumed remaining familiar time")
    }

    /**
     * The other half of the round trip. This is the case the owner would experience as "my
     * familiar vanished when I logged back in".
     */
    @Test
    fun `logging back in restores the same familiar with the same time left`() {
        val player = newPlayer(summoningLevel = SummoningPouchData.PACK_YAK.level)
        player.inventory[0] = Item(SummoningPouchData.PACK_YAK.pouch, 1)
        assertTrue(Familiar.summon(player, SummoningPouchData.PACK_YAK))
        val timeBefore = player.timers[FAMILIAR_LIFETIME_TIMER]
        Familiar.disconnect(player)

        Familiar.restoreOnLogin(player)

        val restored = Familiar.current(player)
        assertNotNull(restored, "the familiar was not restored on login")
        assertEquals(SummoningPouchData.PACK_YAK.npc, restored!!.id, "a different familiar was restored")
        assertEquals(timeBefore, player.timers[FAMILIAR_LIFETIME_TIMER], "the restored familiar lost time")
    }

    /**
     * Stale state, which is what a server restart or a partially cleared save leaves behind: a
     * persisted npc id with no timer to go with it. Restoring it would produce a familiar with no
     * lifetime; leaving it would keep the HUD naming a familiar that does not exist. It has to be
     * cleared.
     */
    @Test
    fun `a persisted familiar with no timer left is cleaned up rather than restored`() {
        val player = newPlayer(summoningLevel = SummoningPouchData.PACK_YAK.level)
        player.attr[FAMILIAR_NPC_ID_ATTR] = SummoningPouchData.PACK_YAK.npc

        Familiar.restoreOnLogin(player)

        assertNull(Familiar.current(player), "a familiar with no remaining time was respawned")
        assertNull(player.attr[FAMILIAR_NPC_ID_ATTR], "stale persisted npc id was left behind")
        assertFalse(player.timers.has(FAMILIAR_LIFETIME_TIMER), "stale lifetime timer was left behind")
    }

    /** An account that never had a familiar must not gain one, or be altered, on login. */
    @Test
    fun `logging in without a familiar restores nothing`() {
        val player = newPlayer(summoningLevel = 99)

        Familiar.restoreOnLogin(player)

        assertNull(Familiar.current(player))
        assertNull(player.attr[FAMILIAR_NPC_ID_ATTR])
    }

    /**
     * Beast of burden cargo is held in a player container keyed by familiar, so it is persisted
     * with the player rather than with the npc. Logging out and back in must therefore not touch
     * it - and if it ever did, the loss would be silent.
     */
    @Test
    fun `beast of burden cargo survives a logout and login round trip`() {
        val player = newPlayer(summoningLevel = SummoningPouchData.PACK_YAK.level)
        player.inventory[0] = Item(SummoningPouchData.PACK_YAK.pouch, 1)
        player.inventory[1] = Item(TEST_STACKABLE, 5)
        assertTrue(Familiar.summon(player, SummoningPouchData.PACK_YAK))
        BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 5))
        assertEquals(5, BeastOfBurden.storedCount(player), "the deposit did not happen, so the test proves nothing")

        Familiar.disconnect(player)
        Familiar.restoreOnLogin(player)

        assertNotNull(Familiar.current(player), "the familiar was not restored, so its cargo cannot be checked")
        assertEquals(5, BeastOfBurden.storedCount(player), "cargo was lost across a logout and login")
    }

    /**
     * Dismissal is the opposite of logout and must leave nothing behind for a later login to pick
     * up: no persisted npc id, no timer. Otherwise a dismissed familiar would come back.
     */
    @Test
    fun `dismissing leaves nothing for a later login to restore`() {
        val player = newPlayer(summoningLevel = SummoningPouchData.PACK_YAK.level)
        player.inventory[0] = Item(SummoningPouchData.PACK_YAK.pouch, 1)
        assertTrue(Familiar.summon(player, SummoningPouchData.PACK_YAK))

        Familiar.dismiss(player)
        Familiar.restoreOnLogin(player)

        assertNull(Familiar.current(player), "a dismissed familiar came back on login")
        assertNull(player.attr[FAMILIAR_NPC_ID_ATTR], "dismiss left a persisted npc id behind")
        assertFalse(player.timers.has(FAMILIAR_LIFETIME_TIMER), "dismiss left a lifetime timer behind")
    }

    private fun newPlayer(summoningLevel: Int): Player {
        val world = mockk<World>(relaxed = true)
        // Real npc update-block table: a relaxed mock returns Objects that break Npc.addBlock.
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs
        every { world.spawn(any<Npc>()) } answers { npcs.add(firstArg()) }
        every { world.remove(any<Npc>()) } answers { npcs.remove(firstArg<Npc>()) }
        every { world.gameContext.cycleTime } returns 600

        val skills = SkillSet(Skills.SUMMONING + 1)
        skills.setBaseLevel(Skills.SUMMONING, summoningLevel)
        skills.setCurrentLevel(Skills.SUMMONING, summoningLevel)

        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.containers } returns HashMap()
        every { player.timers } returns TimerMap()
        every { player.skills } returns skills
        every { player.tile } returns Tile(3222, 3218, 0)
        every { player.varcs } returns MutableList(DEFINITIONS.getCount(VarbitDef::class.java)) { 0 }
        return player
    }

    companion object {
        /** A stackable item, so one inventory slot can carry the whole test deposit. */
        private const val TEST_STACKABLE = gg.rsmod.plugins.api.cfg.Items.COINS

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
