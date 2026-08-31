package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
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
    fun `maxPoints equals current Summoning level`() {
        val player = newPlayer(summoningLevel = 43)
        assertEquals(43, Familiar.maxPoints(player))
    }

    @Test
    fun `summon deducts the immediate level-10 cost and blocks below-cost summons`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)

        val summoned = Familiar.summon(player, DREADFOWL)

        assertTrue(summoned)
        // level 4 -> round(4/10) coerced up to a minimum of 1 point immediate cost.
        assertEquals(DREADFOWL.level - 1, Familiar.currentPoints(player))
    }

    @Test
    fun `summon refuses when points are below the immediate cost`() {
        val player = newPlayer(summoningLevel = PACK_YAK.level)
        player.attr[gg.rsmod.game.model.attr.SUMMONING_POINTS_ATTR] = 0
        player.inventory[0] = Item(PACK_YAK.pouch, 1)

        val summoned = Familiar.summon(player, PACK_YAK)

        assertFalse(summoned)
        assertNull(Familiar.current(player))
        assertEquals(1, player.inventory.getItemCount(PACK_YAK.pouch), "pouch must not be consumed on a refused summon")
    }

    @Test
    fun `a full familiar life drains exactly the pouch's own level in total`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)
        check(Familiar.summon(player, DREADFOWL))

        repeat(Familiar.LIFETIME_CYCLES) { Familiar.tick(player) }

        // Total drained over the whole life (immediate + gradual) must equal the sourced rule:
        // total drained = the familiar's own required level.
        assertEquals(DREADFOWL.level - DREADFOWL.level, Familiar.currentPoints(player))
        assertNull(Familiar.current(player), "familiar's timer ran out over its full life")
    }

    @Test
    fun `running out of points despawns the familiar early, before the timer would`() {
        val player = newPlayer(summoningLevel = PACK_YAK.level)
        player.inventory[0] = Item(PACK_YAK.pouch, 1)
        check(Familiar.summon(player, PACK_YAK))
        assertNotEquals(null, Familiar.current(player))

        // Force points to run out immediately, independent of the timer.
        player.attr[gg.rsmod.game.model.attr.SUMMONING_POINTS_ATTR] = 0
        Familiar.tick(player)

        assertNull(Familiar.current(player))
    }

    @Test
    fun `renew restarts the gradual drain allowance without an extra immediate charge`() {
        val player = newPlayer(summoningLevel = DREADFOWL.level)
        player.inventory[0] = Item(DREADFOWL.pouch, 1)
        check(Familiar.summon(player, DREADFOWL))
        val afterSummon = Familiar.currentPoints(player)

        val renewed = Familiar.renew(player)

        assertTrue(renewed)
        assertEquals(afterSummon, Familiar.currentPoints(player), "renew itself must not cost extra points")
    }

    @Test
    fun `summoning and special points are separate capped resources`() {
        val player = newPlayer(summoningLevel = 43)
        player.attr[gg.rsmod.game.model.attr.SUMMONING_POINTS_ATTR] = 5

        Familiar.restorePoints(player, 10)
        assertEquals(15, Familiar.currentPoints(player))
        assertEquals(Familiar.MAX_SPECIAL_POINTS, Familiar.currentSpecialPoints(player))

        assertTrue(Familiar.consumeSpecialPoints(player, 12))
        assertEquals(48, Familiar.currentSpecialPoints(player))
        Familiar.restoreSpecialPoints(player, 20)
        assertEquals(Familiar.MAX_SPECIAL_POINTS, Familiar.currentSpecialPoints(player))
    }

    private val DREADFOWL get() = SummoningPouchData.DREADFOWL
    private val PACK_YAK get() = SummoningPouchData.PACK_YAK

    private fun newPlayer(summoningLevel: Int): Player {
        val world = mockk<World>(relaxed = true)
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
