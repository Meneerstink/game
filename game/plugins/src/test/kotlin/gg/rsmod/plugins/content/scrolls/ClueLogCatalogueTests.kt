package gg.rsmod.plugins.content.scrolls

import java.io.File
import kotlin.test.*

class ClueLogCatalogueTests {
    @Test fun `all five cache pages and the shared page retain complete source order`() {
        val log = ClueLogCatalogue.load(File("../../data/cfg/clue/collection-log.json"))
        assertEquals(setOf("easy", "medium", "hard", "elite", "master", "shared", "cases"), log.pages.keys)
        assertEquals(listOf(131,115,134,59,49,49,11), log.pages.values.map { it.osrsItems.size })
        assertEquals(listOf(2141,2142,2143,2144,2145,2146,3607), log.pages.values.map { it.sourceEnum })
        assertEquals((30906..30926 step 2).toList(), log.pages.getValue("cases").osrsItems)
        assertEquals(listOf(2577,2579,12598,23413,23389), log.pages.getValue("medium").osrsItems.take(5))
        assertTrue(log.pages.values.all { it.osrsItems.distinct().size == it.osrsItems.size })
    }
}
