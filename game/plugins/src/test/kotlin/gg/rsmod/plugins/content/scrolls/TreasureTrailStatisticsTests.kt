package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import io.mockk.*
import kotlin.test.*

class TreasureTrailStatisticsTests {
    @AfterTest fun cleanup() = unmockkAll()
    private fun account() = mockk<Player>(relaxed=true).also {
        every { it.attr } returns AttributeMap()
    }
    private fun count(p: Player, tier: ClueScrollTier, n: Int) {
        p.attr[AttributeKey<Int>(persistenceKey="clue_completions_${tier.name.lowercase()}")]=n
    }
    private fun unlock(p: Player, flag: String) {
        p.attr[AttributeKey<Boolean>(persistenceKey="clue_case_$flag")]=true
    }
    @Test fun `all source rank boundaries include exactly100 in Beginner without overflow`() {
        for ((last,name) in listOf(100L to "Beginner",200L to "Novice",400L to "The Explorer",600L to "Treasure Hunter",
                1000L to "Expert",1500L to "Elite",2000L to "Master",Long.MAX_VALUE to "Legend")) {
            assertEquals(name,TreasureTrailStatistics.rank(last))
        }
        for ((first,name) in listOf(0L to "Beginner",101L to "Novice",201L to "The Explorer",401L to "Treasure Hunter",
                601L to "Expert",1001L to "Elite",1501L to "Master",2001L to "Legend")) {
            assertEquals(name,TreasureTrailStatistics.rank(first))
        }
        assertFailsWith<IllegalArgumentException> { TreasureTrailStatistics.rank(-1) }
        val p=account()
        ClueScrollTier.values().forEach { count(p,it,Int.MAX_VALUE) }
        assertTrue(TreasureTrailStatistics.lines(p).any { "10737418235" in it && "Legend" in it })
    }
    @Test fun `case progress uses permanent unlocks current counters and only the correct account`() {
        val a=account();val b=account()
        count(a,ClueScrollTier.EASY,99)
        assertEquals(1,TreasureTrailStatistics.remaining(a,ClueScrollTier.EASY))
        count(a,ClueScrollTier.EASY,100)
        assertEquals(0,TreasureTrailStatistics.remaining(a,ClueScrollTier.EASY))
        unlock(a,"easy_minor")
        assertEquals(100,TreasureTrailStatistics.remaining(a,ClueScrollTier.EASY))
        unlock(a,"easy_major");unlock(a,"mimic")
        assertNull(TreasureTrailStatistics.remaining(a,ClueScrollTier.EASY))
        val rows=TreasureTrailStatistics.lines(a)
        assertEquals(12,rows.size)
        assertTrue(rows[6].contains("<col=ffff00>5</col><col=ffffff> scrolls | No more scroll cases"))
        assertTrue(rows.last().contains(">Obtained</col>"))
        assertTrue(TreasureTrailStatistics.lines(b).last().contains(">Not obtained</col>"))
        assertEquals(100,TreasureTrailStatistics.remaining(b,ClueScrollTier.EASY))
        assertEquals(0,ClueCompletions.get(b,ClueScrollTier.EASY))
        assertEquals(100,ClueCompletions.get(a,ClueScrollTier.EASY)) // reading does not count a casket
        assertFalse(rows.any { "Beginner:" in it || "Master:" in it })
        assertTrue(rows.any { it.startsWith("<col=ffffff>78:") })
    }
    @Test fun `paper opening writes only this player's12rows and requires the actual UI import`() {
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        val p=account()
        val text=mutableListOf<String>()
        every { p.setComponentText(1200,any(),capture(text)) } just Runs
        assertFalse(TreasureTrailStatistics.open(p,null))
        assertTrue(text.isEmpty())
        assertTrue(TreasureTrailStatistics.open(p,1200))
        assertEquals(TreasureTrailStatistics.lines(p),text)
        verify(exactly=1) { p.setInterfaceEvents(1200,0,-1..-1,0x2) }
        assertEquals(0,ClueCompletions.get(p,ClueScrollTier.EASY))
    }
    @Test fun `credits reject duplicates markup and injected rows`() {
        assertEquals(listOf("A classy boy","Pinuzzo"),TreasureTrailStatistics.contributors("[\"A classy boy\",\"Pinuzzo\"]"))
        for (json in listOf("[]","[\"x\",\"x\"]","[\"<col=1>name\"]","[\"line\\nline\"]")) {
            assertFailsWith<IllegalArgumentException> { TreasureTrailStatistics.contributors(json) }
        }
    }
    @Test fun `pinboard keeps all87source names reachable including final contributors after scrolling`() {
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        val p=account()
        assertFalse(TreasureTrailStatistics.openPinboard(p,null))
        assertTrue(TreasureTrailStatistics.openPinboard(p,1201))
        val text=slot<String>()
        verify(exactly=1) { p.setComponentText(1201,7,capture(text)) }
        val rows=text.captured.split("<br>")
        assertEquals(87,rows.size);assertEquals("A classy boy",rows.first());assertEquals("Demilancer",rows.last())
        verify(exactly=1) { p.runClientScript(5377,(1201 shl 16) or 5,(1201 shl 16) or 6,1396) }
        verify(exactly=1) { p.setInterfaceEvents(1201,2,-1..-1,0x2) }
    }
}
