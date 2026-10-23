package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N227 / N226 (owner 2026-10-23): no standard quest icon on either map; the hall quest npcs carry our own minimap quest icon. */
class QuestMapIconToolTests {
    private val caches = listOf(Paths.get("..", "data", "cache").toFile(), Paths.get("..", "..", "..", "file-server", "cache").toFile())

    @Test
    fun `hiding a quest element only turns its var range into 1 to 0`() {
        val library = CacheLibrary(caches[0].toString())
        try {
            // Element 752 "Start of Black Knights' Fortress" before or after the tool: same bytes except the 8 range bytes.
            val element = library.data(QuestMapIconTool.CONFIG_INDEX, QuestMapIconTool.MAP_ELEMENT_GROUP, 752)!!
            val hidden = QuestMapIconTool.hidden(element)
            assertEquals(1 to 0, QuestMapIconTool.varRange(hidden))
            assertTrue(QuestMapIconTool.isHidden(hidden))
            assertEquals(element.size, hidden.size)
            assertEquals(QuestMapIconTool.ops(element).map { it.code }, QuestMapIconTool.ops(hidden).map { it.code })
            assertEquals(QuestMapIconTool.QUEST_CATEGORY, QuestMapIconTool.category(hidden))
        } finally {
            library.close()
        }
    }

    @Test
    fun `in both caches every quest element is hidden and the three quest npcs carry the hall icon`() {
        caches.forEach { cache ->
            val library = CacheLibrary(cache.toString())
            try {
                val ids = library.index(QuestMapIconTool.CONFIG_INDEX).archive(QuestMapIconTool.MAP_ELEMENT_GROUP)!!.fileIds()
                val quests = ArrayList<Pair<Int, ByteArray>>()
                ids.forEach { id ->
                    val b = library.data(QuestMapIconTool.CONFIG_INDEX, QuestMapIconTool.MAP_ELEMENT_GROUP, id)
                    if (b != null && b.isNotEmpty() && QuestMapIconTool.category(b) == QuestMapIconTool.QUEST_CATEGORY) quests += id to b
                }
                assertEquals(179, quests.size, "$cache quest elements")
                val visible = quests.filterNot { (_, b) -> QuestMapIconTool.isHidden(b) }.map { it.first }
                assertTrue(visible.isEmpty(), "$cache: quest icons still shown: $visible")
                val hall = library.data(QuestMapIconTool.CONFIG_INDEX, QuestMapIconTool.MAP_ELEMENT_GROUP, QuestMapIconTool.HALL_QUEST_ELEMENT)!!
                assertTrue(hall.contentEquals(QuestMapIconTool.hallElement()), "$cache hall element")
                assertFalse(QuestMapIconTool.ops(hall).any { it.code == 9 }, "the hall icon has no var gate")
                val sprite = library.data(QuestMapIconTool.SPRITE_INDEX, QuestMapIconTool.QUEST_SPRITE, 0)
                assertTrue(sprite != null && sprite.size == 262, "$cache: the OSRS quest-start sprite (262 bytes) as ${QuestMapIconTool.QUEST_SPRITE}")
                QuestMapIconTool.QUEST_NPCS.forEach { npc ->
                    val def = library.data(CasinoMinimapIconTool.INDEX_NPC, npc ushr 7, npc and 0x7F)!!
                    assertEquals(QuestMapIconTool.HALL_QUEST_ELEMENT, CasinoMinimapIconTool.appendedMapElement(def), "$cache npc $npc")
                }
            } finally {
                library.close()
            }
        }
    }
}
