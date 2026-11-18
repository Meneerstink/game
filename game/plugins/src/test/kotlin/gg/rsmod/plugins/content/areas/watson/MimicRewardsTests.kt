package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import java.io.File
import kotlin.random.Random
import kotlin.test.*

class MimicRewardsTests {
    private val rewards = MimicRewards.load(File(gg.rsmod.game.RspsPaths.data("cfg/clue/mimic-rewards.json")))
    private fun at(value: Double) = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextDouble() = value
    }

    @Test fun `all first and later attempt outcomes are reachable as one event plus the guaranteed plank`() {
        for ((tier,table) in rewards.tables) for (failed in 0 until table.attempts) {
            var start = 0.0
            for (row in table.rows) {
                val bonus = rewards.roll(tier,failed,at(start+row.chance(failed)/2))
                assertEquals(MimicRewards.Bonus(8782,1),bonus.first())
                assertEquals(row.osrs,bonus.last().osrs)
                assertTrue(bonus.last().amount>0)
                start+=row.chance(failed)
            }
            assertEquals(1.0,start,1e-12)
        }
    }

    @Test fun `third age ring is six times the rare-equipment event and all 23 pieces share its probability`() {
        for ((tier, first, later) in listOf(Triple(ClueScrollTier.ELITE,250,264),Triple(ClueScrollTier.MASTER,228,240))) {
            val table = rewards.tables.getValue(tier)
            val ring = table.rows.single { it.osrs==23185 }
            val equipment = table.rows.filter { !it.scales && it.osrs!=23185 }
            assertEquals(23,equipment.size)
            for ((failed, denominator) in listOf(0 to first,1 to later)) {
                assertEquals(6.0/denominator,ring.chance(failed),1e-15)
                equipment.forEach { assertEquals(1.0/(denominator*23),it.chance(failed),1e-15) }
            }
        }
    }

    @Test fun `failure-scaled quantities use the exact discrete master and elite sequences from the Wiki`() {
        for ((tier,name,amounts) in listOf(
            Triple(ClueScrollTier.MASTER,"Blood rune",listOf(500,416,333,250,166,83)),
            Triple(ClueScrollTier.MASTER,"Raw manta ray",listOf(15,12,10,7,5,2)),
            Triple(ClueScrollTier.ELITE,"Blood rune",listOf(500,400,300,200,100)))) {
            val table=rewards.tables.getValue(tier)
            val row=table.rows.single { it.name==name }
            assertEquals(amounts,(0 until table.attempts).map { failed ->
                val before=table.rows.takeWhile { it!==row }.sumOf { it.chance(failed) }
                rewards.roll(tier,failed,at(before+row.chance(failed)/2)).last().amount
            })
            assertFailsWith<IllegalArgumentException> { rewards.roll(tier,table.attempts) }
        }
    }
}
