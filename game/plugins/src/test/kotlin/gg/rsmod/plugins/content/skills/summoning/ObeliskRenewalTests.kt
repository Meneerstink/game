package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards Summoning point renewal, the only route back from an empty pool other than a Summoning
 * potion, and therefore the one mechanic that decides whether a persisted account can get stuck
 * at zero points forever.
 *
 * `summoning_obelisks.plugin.kts` and `obelisk.plugin.kts` bind their options by reading the
 * object definitions instead of listing ids, and both fail plugin loading if the sourced counts
 * pinned here ever stop matching. This test pins the same numbers against the cache directly, so
 * a cache change is reported as a test failure rather than as a server that will not boot.
 */
class ObeliskRenewalTests {
    @Test
    fun `every sourced obelisk exposes the renew option`() {
        val renew = objectsWithOption("Renew-points")
        assertEquals(39, renew.size)
        // The seven world obelisks, the small obelisks, the player-owned-house obelisks and the
        // five "Summoning obelisk" ids - none of which the hand-written list used to cover.
        listOf(5787, 28716, 28734, 29938, 29959, 44837, 44842, 50205, 55605).forEach {
            assertTrue(it in renew, "object $it should expose Renew-points")
        }
        // These five carry no options at all in this revision and were only ever guesses.
        listOf(54650, 56083, 56084, 56085, 56086).forEach {
            assertFalse(it in renew, "object $it exposes no options in this revision")
        }
    }

    @Test
    fun `every sourced obelisk exposes the infuse option`() {
        val infuse = objectsWithOption("Infuse-pouch")
        assertEquals(12, infuse.size)
        assertEquals(
            listOf(28716, 28719, 28722, 28725, 28728, 28731, 28734, 50205, 50206, 50207, 53883, 55605),
            infuse,
        )
        assertTrue(infuse.all { it in objectsWithOption("Renew-points") }, "every large obelisk also renews")
    }

    @Test
    fun `renewing restores a drained account to full`() {
        val player = newPlayer(summoningLevel = 50)
        player.skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 0)
        assertEquals(0, Familiar.currentPoints(player))

        Familiar.restorePoints(player)

        assertEquals(50, Familiar.currentPoints(player))
    }

    /**
     * The knowledge base recharges the special-move pool over time and through Summoning potions,
     * which "also restore a portion of your special move bar" - the obelisk renews points only.
     */
    @Test
    fun `renewing leaves the special move pool alone`() {
        val player = newPlayer(summoningLevel = 50)
        player.skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 0)
        Familiar.consumeSpecialPoints(player, 45)

        Familiar.restorePoints(player)

        assertEquals(50, Familiar.currentPoints(player))
        assertEquals(Familiar.MAX_SPECIAL_POINTS - 45, Familiar.currentSpecialPoints(player))
    }

    /** One dose of a Summoning potion: a quarter of the maximum plus seven, and 15 special. */
    @Test
    fun `a summoning potion dose tops up a drained account`() {
        val player = newPlayer(summoningLevel = 60)
        player.skills.setCurrentLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 0)
        Familiar.consumeSpecialPoints(player, Familiar.MAX_SPECIAL_POINTS)

        Familiar.restorePoints(player, Familiar.maxPoints(player) / 4 + 7)
        Familiar.restoreSpecialPoints(player, 15)

        assertEquals(22, Familiar.currentPoints(player))
        assertEquals(15, Familiar.currentSpecialPoints(player))
    }

    private fun objectsWithOption(option: String): List<Int> =
        DEFINITIONS
            .getAll(ObjectDef::class.java)
            .values
            .filterIsInstance<ObjectDef>()
            .filter { def -> def.options.any { it?.equals(option, ignoreCase = true) == true } }
            .map { it.id }
            .sorted()

    /**
     * A real [SkillSet], not a relaxed mock: since 2026-09-06 Summoning points *are* the current
     * level of skill 23 (client scripts 755 and 801 read the stat directly - see [Familiar]), so a
     * mock that answers `getMaxLevel` alone would leave the current level permanently reading 0
     * and every point assertion below would be measuring the mock rather than the mechanic.
     */
    private fun newPlayer(summoningLevel: Int): Player {
        val world = mockk<World>(relaxed = true)
        // Real npc update-block table: a relaxed mock returns Objects that break Npc.addBlock.
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.definitions } returns DEFINITIONS

        val skills = SkillSet(Skills.SUMMONING + 1)
        skills.setBaseLevel(Skills.SUMMONING, summoningLevel)
        skills.setCurrentLevel(Skills.SUMMONING, summoningLevel)

        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.skills } returns skills
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadDefinitions() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
