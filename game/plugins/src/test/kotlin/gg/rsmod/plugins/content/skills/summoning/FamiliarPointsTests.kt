package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R07.2: regression tests for [Familiar]'s real Summoning-points model - max pool = current
 * Summoning level (1:1, pre-2018-rework), an immediate ~level/10 on-summon deduction, and a
 * gradual drain spread across [Familiar.LIFETIME_CYCLES] that totals exactly the pouch's own
 * `level` over one full life (sourced formula, see [Familiar.immediateCost]'s KDoc).
 */
class FamiliarPointsTests {
    @Test
    fun `max points equals current Summoning level`() {
        val player = newPlayer(summoningLevel = 43)
        assertEquals(43, Familiar.maxPoints(player))
    }

    @Test
    fun `summon deducts the ledger pouch cost exactly once`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)

        assertTrue(Familiar.summon(player, DREADFOWL))
        assertEquals(3, Familiar.currentPoints(player))
        assertEquals(0, player.inventory.getItemCount(DREADFOWL.pouch))
    }

    /**
     * The "maximum one familiar" lifecycle rule, and - the part that actually costs the player if
     * it is wrong - that a rejected summon is **free**.
     *
     * The rule itself is a 2026-09-06 human-retest fix: summoning a second pouch used to
     * unconditionally dismiss the active familiar and replace it, losing whatever life and points
     * were left on it. It was corrected to reject the new summon outright, and that correction was
     * never covered by a test.
     *
     * The ordering matters as much as the rule. The guard sits above the pouch removal in
     * [Familiar.summon], so a rejected summon must leave the second pouch in the inventory and the
     * points untouched. A guard placed below the removal would read as "one familiar at a time"
     * while quietly eating a pouch on every misclick.
     */
    @Test
    fun `a second summon is rejected outright and costs the player nothing`() {
        val player = newPlayer(summoningLevel = PACK_YAK.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)
        player.inventory[1] = Item(PACK_YAK.pouch, 1)
        assertTrue(Familiar.summon(player, DREADFOWL))
        val first = Familiar.current(player)
        val pointsAfterFirst = Familiar.currentPoints(player)

        assertFalse(Familiar.summon(player, PACK_YAK), "a second familiar must not be summonable")

        assertEquals(first, Familiar.current(player), "the active familiar was replaced or lost")
        assertEquals(1, player.inventory.getItemCount(PACK_YAK.pouch), "the rejected summon consumed its pouch")
        assertEquals(pointsAfterFirst, Familiar.currentPoints(player), "the rejected summon consumed points")
    }

    @Test
    fun `summon refuses when points are below its real pouch cost`() {
        val player = newPlayer(summoningLevel = PACK_YAK.level)
        player.skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 9)
        player.inventory[0] = Item(PACK_YAK.pouch, 1)

        assertFalse(Familiar.summon(player, PACK_YAK))
        assertNull(Familiar.current(player))
        assertEquals(1, player.inventory.getItemCount(PACK_YAK.pouch))
    }

    @Test
    fun `expiry does not drain summoning points`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)
        check(Familiar.summon(player, DREADFOWL))
        val pointsAfterSummon = Familiar.currentPoints(player)

        player.timers.remove(FAMILIAR_LIFETIME_TIMER)
        Familiar.tick(player)

        assertNull(Familiar.current(player))
        assertEquals(pointsAfterSummon, Familiar.currentPoints(player))
    }

    @Test
    fun `renew is not gated by remaining time and consumes one matching pouch`() {
        // 2026-09-06 owner human retest: the former "less than 2:50 remaining" gate had no
        // source (see Familiar.renew's KDoc) and is removed - Renew must work at any remaining
        // duration, including immediately after summoning.
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 2)
        check(Familiar.summon(player, DREADFOWL))
        assertTrue(Familiar.renew(player), "renew must work regardless of remaining time")
        assertEquals(400, player.timers[FAMILIAR_LIFETIME_TIMER])
        assertEquals(0, player.inventory.getItemCount(DREADFOWL.pouch))
    }

    @Test
    fun `renew without a matching pouch fails and leaves the timer untouched`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)
        check(Familiar.summon(player, DREADFOWL))
        player.timers[FAMILIAR_LIFETIME_TIMER] = 1

        assertFalse(Familiar.renew(player))
        assertEquals(1, player.timers[FAMILIAR_LIFETIME_TIMER])
    }

    @Test
    fun `zero summoning points do not dismiss an already active familiar`() {
        val player = newPlayer(summoningLevel = PACK_YAK.level)
        player.inventory[0] = Item(PACK_YAK.pouch, 1)
        check(Familiar.summon(player, PACK_YAK))
        player.skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 0)

        Familiar.tick(player)

        assertNotEquals(null, Familiar.current(player))
    }

    @Test
    fun `summoning and special points are separate capped resources`() {
        val player = newPlayer(summoningLevel = 43)
        player.skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 5)

        Familiar.restorePoints(player, 10)
        assertEquals(15, Familiar.currentPoints(player))
        assertEquals(Familiar.MAX_SPECIAL_POINTS, Familiar.currentSpecialPoints(player))
        assertTrue(Familiar.consumeSpecialPoints(player, 12))
        assertEquals(48, Familiar.currentSpecialPoints(player))
        Familiar.restoreSpecialPoints(player, 20)
        assertEquals(Familiar.MAX_SPECIAL_POINTS, Familiar.currentSpecialPoints(player))
    }
    @Test
    fun `special points restore by 15 every 30 active seconds`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)
        check(Familiar.summon(player, DREADFOWL))
        check(Familiar.consumeSpecialPoints(player, 30))

        repeat(50) { Familiar.tick(player) }

        assertEquals(45, Familiar.currentSpecialPoints(player))
    }
    private val DREADFOWL get() = SummoningPouchData.DREADFOWL
    private val PACK_YAK get() = SummoningPouchData.PACK_YAK

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
        every { player.tile } returns Tile(0, 0, 0)
        // Familiar.tick drives the interface gating, which writes varc 1436 (the special-move
        // button's instant/targeted flag). Player.varcs is sized from the varbit definitions on a
        // real world; a relaxed mock hands back an empty list and the write goes out of bounds.
        every { player.varcs } returns MutableList(DEFINITIONS.getCount(VarbitDef::class.java)) { 0 }
        return player
    }

    companion object {
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
