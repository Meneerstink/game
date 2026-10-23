package gg.rsmod.plugins.content.inter.emotes

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.ext.getVarbit
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EMO (owner 10-16: goblin emotes at home never work) and N99 (26 other emotes always "You have not unlocked this emote yet."):
 * owner VRAGEN 25 = A - every emote open for everyone; Give Thanks and Seal of Approval play their real transformation.
 */
class EmotesUnlockTests {
    private val gated = Emote.values.filter { it.varbit != -1 && it != Emote.SKILLCAPE }

    @Test
    fun `after login every emote passes the unlock gate`() {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        every { player.varps } returns VarpSet(VARP_IDS)
        EmotesTab.unlockAll(player)
        gated.forEach { assertTrue(player.getVarbit(it.varbit) >= it.requiredVarbitValue, "$it varbit ${it.varbit} = ${player.getVarbit(it.varbit)}") }
    }

    @Test
    fun `N233 - every emote is drawn unlocked by the tab script's own test, Zombie Hand at 12`() {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        every { player.varps } returns VarpSet(VARP_IDS)
        EmotesTab.unlockAll(player)
        // Clientscript 1750 (emote tab): Zombie Hand needs varbit 4075 == 12, Around the World 6014 == 85, Puppet Master 8300 == 20,
        // Task Master 8601 == 428, Invoke Spring 9198 == 60, the goblin emotes Lost Tribe >= 7, every other emote == 1.
        assertEquals(12, player.getVarbit(Emote.ZOMBIE_HAND.varbit))
        gated.forEach { assertTrue(EmotesTab.isUnlocked(player.getVarbit(it.varbit), it), "$it varbit ${it.varbit} = ${player.getVarbit(it.varbit)}") }
        assertTrue(!EmotesTab.isUnlocked(1, Emote.ZOMBIE_HAND), "1 is the locked Zombie Hand the owner saw")
    }

    @Test
    fun `N223 - an emote plays to its end, unless the player walked away`() {
        assertTrue(EmotesTab.busy(currentCycle = 105, until = 110, startTile = 7, tile = 7), "a second click during the emote does nothing")
        assertTrue(!EmotesTab.busy(currentCycle = 110, until = 110, startTile = 7, tile = 7), "free once it ended")
        assertTrue(!EmotesTab.busy(currentCycle = 105, until = 110, startTile = 7, tile = 8), "walking ended it")
        assertTrue(!EmotesTab.busy(currentCycle = 105, until = null, startTile = null, tile = 7), "no emote yet")
    }

    @Test
    fun `emotes that share a varbit agree on its value and the login sets the goblin value`() {
        gated.groupBy { it.varbit }.forEach { (varbit, emotes) ->
            assertEquals(1, emotes.map { it.requiredVarbitValue }.distinct().size, "varbit $varbit: ${emotes.map { "$it=${it.requiredVarbitValue}" }}")
        }
        assertEquals(7, Emote.GOBLIN_BOW.requiredVarbitValue)
        val login = File("src/main/kotlin/gg/rsmod/plugins/content/runetek5.plugin.kts").readText()
        assertTrue("player.setVarbit(Varbits.THE_LOST_TRIBE_PROGRESS, 7)" in login, "the Lost Tribe progress login value unlocks the goblin emotes")
        val tab = File("src/main/kotlin/gg/rsmod/plugins/content/inter/emotes/emotes_tab.plugin.kts").readText()
        assertTrue("EmotesTab.unlockAll(player)" in tab.substringAfter("on_login"))
    }

    @Test
    fun `Give Thanks and Seal of Approval transform and always end as the player`() {
        listOf(Emote.GIVE_THANKS to 8499, Emote.SEAL_OF_APPROVAL to 13256).forEach { (emote, npc) ->
            val steps = EmotesTab.transformSteps(emote, seal = 13256)
            assertTrue(steps.isNotEmpty() && steps.all { it.anim > 0 }, "$emote animates")
            assertEquals(npc, steps.first { it.transmog != null }.transmog, "$emote turns into $npc")
            assertEquals(-1, steps.last().transmog, "$emote ends as the player")
        }
        assertEquals(6, EmotesTab.transformSteps(Emote.GIVE_THANKS).sumOf { it.wait }, "Novite: back on step 6")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var VARP_IDS: Set<Int>

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            VARP_IDS = Emote.values.filter { it.varbit != -1 }.map { DEFINITIONS.get(VarbitDef::class.java, it.varbit).varp }.toSet()
        }
    }
}
