package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** DMM-ARMAGEDDON D4 (owner 20-11-2026): boss Deadman Points - wiki table, first kill 10x, half after 100 kills. */
class BossDeadmanPointsTests {
    private fun player(): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        return p
    }

    @Test
    fun `first kill pays ten times, kills 2-100 the table value, kill 101 on half rounded down`() {
        assertEquals(1000, BossDeadmanPoints.pointsFor(100, 1))
        assertEquals(100, BossDeadmanPoints.pointsFor(100, 2))
        assertEquals(100, BossDeadmanPoints.pointsFor(100, 100))
        assertEquals(50, BossDeadmanPoints.pointsFor(100, 101))
        assertEquals(11, BossDeadmanPoints.pointsFor(23, 500), "Kree'arra 23 -> 11")
        assertEquals(2, BossDeadmanPoints.pointsFor(5, 101))
        assertEquals(0, BossDeadmanPoints.pointsFor(0, 1), "a boss without points pays nothing")
        assertEquals(0, BossDeadmanPoints.pointsFor(10, 0))
    }

    @Test
    fun `the approved table - wiki bosses, our own bosses, Mimic variants, unknown names`() {
        assertEquals(100, BossDeadmanPoints.perKill("TzTok-Jad"))
        assertEquals(35, BossDeadmanPoints.perKill("Corporeal Beast"))
        assertEquals(23, BossDeadmanPoints.perKill("Kree'arra"))
        assertEquals(17, BossDeadmanPoints.perKill("K'ril Tsutsaroth"))
        assertEquals(4, BossDeadmanPoints.perKill("Crazy archaeologist"))
        assertEquals(25, BossDeadmanPoints.perKill("Kalphite King"))
        assertEquals(5, BossDeadmanPoints.perKill("Calvar'ion"))
        assertEquals(10, BossDeadmanPoints.perKill("The Mimic (elite)"))
        assertEquals(10, BossDeadmanPoints.perKill("The Mimic (78)"))
        assertEquals(0, BossDeadmanPoints.perKill("Porazdir"))
        assertEquals(0, BossDeadmanPoints.perKill("Man"))
        assertEquals(100, BossDeadmanPoints.toa(0))
        assertEquals(100, BossDeadmanPoints.toa(149))
        assertEquals(120, BossDeadmanPoints.toa(150))
        assertEquals(150, BossDeadmanPoints.toa(300))
        assertEquals(120, BossDeadmanPoints.COX)
        assertEquals(10, BossDeadmanPoints.BARROWS)
    }

    @Test
    fun `a kill adds Deadman Points to the balance and stops at the maximum`() {
        val p = player()
        val dp = StoreCatalogue.Currency.DEADMAN.attr
        assertEquals(230, BossDeadmanPoints.onBossKill(p, "Kree'arra", 1))
        assertEquals(35, BossDeadmanPoints.onBossKill(p, "Corporeal Beast", 2))
        assertEquals(230 + 35, p.attr[dp])
        p.attr[dp] = Int.MAX_VALUE - 3
        assertEquals(3, BossDeadmanPoints.onBossKill(p, "TzTok-Jad", 5))
        assertEquals(0, BossDeadmanPoints.onBossKill(p, "TzTok-Jad", 6))
        assertEquals(Int.MAX_VALUE, p.attr[dp])
    }
}
