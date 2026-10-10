package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.DangerWarning
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-27 (final check): every Quest Tutor and short-quest move to a Dangerous place asks first with the owner's
 * text, even when the general warning is switched off; Safe places never ask. And no route opens Ancient Magicks or the
 * Lunar book without its quest.
 */
class QuestTravelWarningTests {
    private val asked = mutableListOf<String>()
    private var answer = false

    private fun task(): QueueTask {
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val attributes = AttributeMap()
        every { player.attr } returns attributes
        every { player.world } returns world
        val task = mockk<QueueTask>(relaxed = true)
        every { task.ctx } returns player
        DangerWarning.confirm = { _, text, _ -> asked += text; answer }
        return task
    }

    @AfterTest
    fun reset() {
        DangerWarning.confirm = null
    }

    @Test
    fun `a dangerous quest destination always asks with the owner's text and No stays`() {
        val lunarDock = Tile(2142, 3900, 0)
        assertTrue(AreaState.isDangerous(lunarDock))
        // The general warning is off (no ENABLED/INTRO attribute): the quest warning still shows.
        answer = false
        assertFalse(runBlocking { DangerWarning.confirmQuestTravel(task(), lunarDock) })
        answer = true
        assertTrue(runBlocking { DangerWarning.confirmQuestTravel(task(), lunarDock) })
        assertEquals(2, asked.size)
        val plain = asked.first().replace(Regex("<[^>]+>"), "")
        assertEquals("This destination is in a Dangerous area. Other players can attack you and you can lose your items. Continue?", plain)
    }

    @Test
    fun `a safe quest destination never asks`() {
        val geHall = Tile(3164, 3491, 0)
        assertFalse(AreaState.isDangerous(geHall))
        assertTrue(runBlocking { DangerWarning.confirmQuestTravel(task(), geHall) })
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `every quest move goes through the warning`() {
        val dir = File(sourceRoot(), "gg/rsmod/plugins/content/quests")
        val raw =
            // TutorIntro.kt: the new-player tour moves an invisible, locked player between camera stand tiles with the warning
            // suppressed on purpose (DangerWarning.SUPPRESSED) and always ends in the guarded hall - not a quest travel.
            // IntroGuardScene.kt is a stop of that tour: it moves the same hidden watcher inside its private mirrored scene.
            dir.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name !in setOf("TutorIntro.kt", "IntroGuardScene.kt") }.flatMap { f ->
                val lines = f.readLines()
                lines.indices.filter { i ->
                    val l = lines[i]
                    (l.contains("player.moveTo(") || l.contains("player.teleport(")) &&
                        (maxOf(0, i - 3)..i).none { lines[it].contains("confirmQuestTravel") }
                }.map { "${f.name}:${it + 1}" }
            }.toList()
        assertTrue(raw.isEmpty(), "quest moves without the Dangerous-area warning: $raw")
    }

    @Test
    fun `quest destinations report`() {
        // Not an assertion on the map: prints each destination's state for the owner's report.
        FoundationQuests.SHORT.forEach { quest ->
            val tiles = mutableListOf<Pair<String, Tile>>()
            quest.startLocation?.let { tiles += "start ${quest.startPlace}" to it }
            quest.steps.forEach { step ->
                if (step.location != null) tiles += "step ${step.place}" to step.location!!
            }
            tiles.distinctBy { it.second }.forEach { (what, tile) ->
                println("QUEST-DEST ${quest.name} | $what | ${tile.x},${tile.z},${tile.height} | ${if (AreaState.isDangerous(tile)) "DANGEROUS" else "SAFE"}")
            }
        }
    }

    @Test
    fun `no route opens a locked spellbook`() {
        val player = mockk<Player>(relaxed = true)
        val attributes = AttributeMap()
        every { player.attr } returns attributes
        assertFalse(Spellbooks.hasAccess(player, Spellbook.ANCIENT))
        assertFalse(Spellbooks.hasAccess(player, Spellbook.LUNAR))
        assertTrue(Spellbooks.hasAccess(player, Spellbook.STANDARD))
        attributes[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] = true
        assertTrue(Spellbooks.hasAccess(player, Spellbook.ANCIENT))
        // Every raw book switch outside Spellbooks is either Spellbook Swap's revert or behind hasAccess.
        val root = sourceRoot()
        val cape = File(root, "gg/rsmod/plugins/content/items/osrs/max_cape_options.plugin.kts").readText()
        // Owner 2026-10-03 (buglist E2): the Max cape has no Spellbook option at all - so it cannot open a locked book.
        assertTrue("option = \"Spellbook\"" !in cape && "Spellbooks.hasAccess(player, it)" !in cape, "the Max cape offers spellbooks again")
        val swap = File(root, "gg/rsmod/plugins/content/magic/lunar/lunar_spells.plugin.kts").readText()
        assertTrue("Spellbooks.hasAccess(player, book)" in swap, "Spellbook Swap opens Ancient Magicks without Desert Treasure")
    }

    private fun sourceRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main/kotlin/gg/rsmod/plugins").isDirectory) dir = dir.parentFile
        return File(requireNotNull(dir) { "plugins source root not found" }, "src/main/kotlin")
    }
}
