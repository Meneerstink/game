package gg.rsmod.plugins.content.activity.toa.core

import gg.rsmod.game.model.PlayerUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** N462 ToA points + chest (OSRS Wiki Chest (Tombs of Amascut) oldid 15385393, Drop Mechanics update oldid 14338876). */
class ToaCoreTests {
    private val a = PlayerUID("a")
    private val b = PlayerUID("b")

    @Test
    fun `modes are the OSRS mode boundaries`() {
        assertEquals(listOf(0, 150, 300), ToaMode.values().map { it.raidLevel })
        assertEquals(ToaMode.ENTRY, ToaMode.of(149))
        assertEquals(ToaMode.NORMAL, ToaMode.of(150))
        assertEquals(ToaMode.EXPERT, ToaMode.of(600))
    }

    @Test
    fun `points start at 5000, rooms cap at 20000, totals at 64000, MVP gets 300 per member, death costs 20 percent or 1000`() {
        val ledger = ToaPointsLedger(listOf(a, b))
        assertEquals(0, ledger.rewardPoints(a))
        ledger.damage(a, 30_000) // room cap
        ledger.damage(b, 1_000, ToaPointSource.BABA) // x2
        assertEquals(20_000, ledger.roomPoints(a))
        assertEquals(2_000, ledger.roomPoints(b))
        assertEquals(a, ledger.completeRoom())
        assertEquals(5_000 + 20_000 + 600, ledger.total(a))
        assertEquals(7_000, ledger.total(b))
        ledger.death(b) // 20% of 7000 = 1400
        assertEquals(5_600, ledger.total(b))
        repeat(5) { ledger.damage(a, 20_000); ledger.completeRoom() }
        assertEquals(64_000, ledger.total(a))
        assertEquals(59_000, ledger.rewardPoints(a))
        val small = ToaPointsLedger(listOf(a))
        small.death(a) // 20% of 5000 = 1000 = the minimum
        assertEquals(4_000, small.total(a))
        assertEquals(0, small.rewardPoints(a))
    }

    @Test
    fun `an MVP tie gives no MVP bonus and the core gives no points`() {
        val ledger = ToaPointsLedger(listOf(a, b))
        ledger.damage(a, 500)
        ledger.damage(b, 500)
        ledger.damage(a, 999, ToaPointSource.CORE)
        assertNull(ledger.completeRoom())
        assertEquals(5_500, ledger.total(a))
    }

    @Test
    fun `purple chance follows the wiki formula and caps at 55 percent`() {
        assertEquals(10_000 / (100.0 * 10_500), ToaLoot.purpleChance(10_000, 0), 1e-12)
        assertEquals(20_000 / (100.0 * (10_500 - 20 * 300)), ToaLoot.purpleChance(20_000, 300), 1e-12)
        assertEquals(0.55, ToaLoot.purpleChance(5_000_000, 300))
        assertEquals(350 + 170 / 6.0, ToaLoot.purpleRaidLevel(600), 1e-12)
    }

    @Test
    fun `pet, thread, jewel and clue chances follow the wiki`() {
        assertEquals(30_000 / (100.0 * (350_000 - 700 * 300)), ToaLoot.petChance(30_000, 300), 1e-12)
        assertEquals(0.1, ToaLoot.threadChance(0, false), 1e-12)
        assertEquals(0.3, ToaLoot.threadChance(15, false), 1e-12)
        assertEquals(0.3, ToaLoot.threadChance(99, false), 1e-12)
        assertEquals(0.02, ToaLoot.threadChance(0, true), 1e-12)
        assertEquals(4.0 / 50, ToaLoot.jewelChance(0, false), 1e-12)
        assertEquals(12.0 / 50, ToaLoot.jewelChance(75, false), 1e-12)
        assertEquals(4.0 / 50, ToaLoot.jewelChance(75, true), 1e-12)
        assertEquals(0.25, ToaLoot.eliteClueChance(80_000))
    }

    @Test
    fun `commons scale from raid level 300, cache of runes is always one, below 1500 points only fossilised dung`() {
        assertEquals(10_000, ToaLoot.commonAmount(ToaLoot.Common.COINS, 10_000, 150))
        assertEquals(11_500, ToaLoot.commonAmount(ToaLoot.Common.COINS, 10_000, 300))
        assertEquals(1, ToaLoot.commonAmount(ToaLoot.Common.CACHE_OF_RUNES, 50_000, 300))
        assertEquals(listOf(ToaLootDrop(ToaLoot.FOSSILISED_DUNG, 1, noted = true)), ToaLoot.commons(1_499, 300, Random(1)))
        assertEquals(27, ToaLoot.Common.values().size)
        assertEquals(240, ToaLoot.Unique.values().sumOf { it.weight })
    }

    @Test
    fun `at most one purple per raid and its winner gets no commons`() {
        val players = listOf(
            ToaLootPlayer(a, 40_000, 10, hadThread = true, ownedJewels = ToaLoot.JEWELS.toSet()),
            ToaLootPlayer(b, 40_000, 10, hadThread = true, ownedJewels = ToaLoot.JEWELS.toSet()),
        )
        val uniqueIds = ToaLoot.Unique.values().map { it.osrsItem }.toSet()
        var purples = 0
        repeat(2_000) { seed ->
            val result = ToaLoot.roll(players, setOf(a, b), 300, Random(seed))
            val got = result.drops.values.flatten().count { it.osrsItem in uniqueIds }
            assertTrue(got <= 1)
            result.purpleWinner?.let { winner ->
                purples++
                val winnerDrops = result.drops.getValue(winner).map { it.osrsItem }
                assertTrue(ToaLoot.Common.values().none { it.osrsItem in winnerDrops }, "purple winner got commons")
            }
        }
        // 80,000 party points at raid level 300: 80000 / 450000 = 17.8%.
        assertTrue(purples in 280..440, "purples $purples")
    }

    @Test
    fun `challenge rewards only at Expert, only deathless, never owned, one remnant in OSRS priority`() {
        assertTrue(ToaLoot.challengeRewards(150, partyDeathless = true) { false }.isEmpty())
        assertTrue(ToaLoot.challengeRewards(300, partyDeathless = false) { false }.isEmpty())
        val all = ToaLoot.challengeRewards(300, partyDeathless = true) { false }.map { it.osrsItem }
        assertEquals(ToaLoot.CHALLENGE_KITS + 27377, all) // three kits + Remnant of Akkha first
        val owned = setOf(27372, 27377, 27380)
        val some = ToaLoot.challengeRewards(300, partyDeathless = true) { it in owned }.map { it.osrsItem }
        assertEquals(listOf(27255, 27248, 27378), some) // next remnant after Akkha and Zebak is Ba-Ba
        val ledger = ToaPointsLedger(listOf(a, b))
        assertTrue(ledger.deathless)
        ledger.death(b)
        assertTrue(!ledger.deathless)
    }

    @Test
    fun `a missing jewel is the one given while any is missing`() {
        val owned = ToaLoot.JEWELS.take(3).toSet()
        val player = ToaLootPlayer(a, 2_000, 75, hadThread = true, ownedJewels = owned)
        repeat(500) { seed ->
            val jewels = ToaLoot.roll(listOf(player), setOf(a), 150, Random(seed)).drops.getValue(a).filter { it.osrsItem in ToaLoot.JEWELS }
            jewels.forEach { assertEquals(ToaLoot.JEWELS[3], it.osrsItem) }
        }
    }
}
