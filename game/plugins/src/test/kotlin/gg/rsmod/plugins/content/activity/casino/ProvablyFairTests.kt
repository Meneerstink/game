package gg.rsmod.plugins.content.activity.casino

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the four provably-fair algorithms to the MIT reference (`roatpkz/rsps-provably-fair`).
 *
 * Every hard-coded number below was derived **outside** this code base - SHA-256 computed with
 * `System.Security.Cryptography.SHA256` in PowerShell and the byte/modulo arithmetic done separately - so these are
 * independent vectors, not a snapshot of whatever the Kotlin happens to produce. If a refactor changes a separator,
 * a byte count or a counter order, these fail, which is the point: the material layout is a compatibility contract
 * with Roat's public verifier pages, not an implementation detail.
 */
class ProvablyFairTests {
    // ---------------------------------------------------------------- core

    @Test
    fun `sha256 matches the reference digest`() {
        assertEquals("b3eacd33433b31b5252351032c9b3e7a2e7aa7738d5decdf0dd6c62680853c06", ProvablyFair.sha256Hex("server"))
    }

    @Test
    fun `generated seeds are 16 characters from the reference alphabet`() {
        repeat(200) {
            val seed = ProvablyFair.randomSeed()
            assertEquals(ProvablyFair.SEED_LENGTH, seed.length)
            assertTrue(ProvablyFair.isSeedValid(seed), "generated seed rejected: $seed")
        }
    }

    @Test
    fun `seed validation rejects anything outside the reference alphabet`() {
        assertTrue(ProvablyFair.isSeedValid("abcXYZ-019"))
        assertFalse(ProvablyFair.isSeedValid(null))
        assertFalse(ProvablyFair.isSeedValid(""))
        // A colon would forge extra fields in the hash material; a space and a non-ASCII char break the round-trip.
        assertFalse(ProvablyFair.isSeedValid("a:b"))
        assertFalse(ProvablyFair.isSeedValid("a b"))
        assertFalse(ProvablyFair.isSeedValid("sé"))
        assertFalse(ProvablyFair.isSeedValid("x".repeat(ProvablyFair.MAX_CLIENT_SEED_LENGTH + 1)))
    }

    // ---------------------------------------------------------------- dice

    @Test
    fun `dice reproduces the reference draws for client and server`() {
        // material = "client:server:<nonce>", first 4 bytes as unsigned 32-bit, % 10001.
        assertEquals(9555, ProvablyFairDice.rollScaled("client", "server", 0))
        assertEquals(7849, ProvablyFairDice.rollScaled("client", "server", 1))
        assertEquals(3613, ProvablyFairDice.rollScaled("client", "server", 2))
        assertEquals(95.55, ProvablyFairDice.rollPercentage("client", "server", 0))
        assertEquals(78.49, ProvablyFairDice.rollPercentage("client", "server", 1))
        assertEquals(36.13, ProvablyFairDice.rollPercentage("client", "server", 2))
    }

    @Test
    fun `dice draws stay inside the published range`() {
        for (nonce in 0 until 500) {
            val scaled = ProvablyFairDice.rollScaled("c", "s", nonce)
            assertTrue(scaled in 0..10_000, "draw out of range: $scaled")
        }
    }

    @Test
    fun `dice is deterministic and nonce-separated`() {
        assertEquals(ProvablyFairDice.rollScaled("c", "s", 7), ProvablyFairDice.rollScaled("c", "s", 7))
        assertTrue(ProvablyFairDice.rollScaled("c", "s", 7) != ProvablyFairDice.rollScaled("c", "s", 8))
        assertTrue(ProvablyFairDice.rollScaled("c", "s", 7) != ProvablyFairDice.rollScaled("c2", "s", 7))
    }

    // ---------------------------------------------------------------- mines

    @Test
    fun `mines reproduces the reference layout`() {
        // Draw-bag order for "client:server:0:<counter>" with 3 mines: cells 9, 22 and 8.
        assertEquals(setOf(9, 22, 8), ProvablyFairMines.generateLayout("client", "server", 0, 3))
    }

    @Test
    fun `mines always places exactly the requested number of distinct cells in range`() {
        for (count in ProvablyFairMines.MIN_MINES..ProvablyFairMines.MAX_MINES) {
            val layout = ProvablyFairMines.generateLayout("c", "s", count, count)
            assertEquals(count, layout.size, "wrong mine count for $count")
            assertTrue(layout.all { it in 0 until ProvablyFairMines.SLOTS }, "cell out of range for $count")
        }
    }

    @Test
    fun `mines rejects impossible boards`() {
        for (bad in intArrayOf(0, 25, 26, -1)) {
            val failed =
                try {
                    ProvablyFairMines.generateLayout("c", "s", 0, bad)
                    false
                } catch (expected: IllegalArgumentException) {
                    true
                }
            assertTrue(failed, "mineCount $bad should have been rejected")
        }
    }

    // ---------------------------------------------------------------- blackjack

    @Test
    fun `blackjack shuffle reproduces the reference permutation`() {
        val deck = (0..9).toMutableList()
        ProvablyFairBlackjack.shuffle(deck, "client", "server", 0)
        assertEquals(listOf(9, 8, 6, 5, 3, 2, 7, 0, 1, 4), deck)
    }

    @Test
    fun `blackjack shuffle is a permutation of the whole shoe`() {
        val shoe = (0 until 416).toMutableList()
        ProvablyFairBlackjack.shuffle(shoe, "c", "s", 3)
        assertEquals(416, shoe.size)
        assertEquals((0 until 416).toSet(), shoe.toSet())
        assertTrue(shoe != (0 until 416).toList(), "the shoe was not shuffled at all")
    }

    @Test
    fun `blackjack shuffle is deterministic and nonce-separated`() {
        fun shoe(nonce: Long) = (0 until 416).toMutableList().also { ProvablyFairBlackjack.shuffle(it, "c", "s", nonce) }
        assertEquals(shoe(1), shoe(1))
        assertTrue(shoe(1) != shoe(2))
    }

    // ---------------------------------------------------------------- flower poker

    @Test
    fun `flower poker reproduces the reference draw order`() {
        // Rolls for "p1:p2:srv:<index>" are 215, 30, 238, 261, 289, 151, 41, 129, 24, 424.
        val expected =
            listOf(
                CasinoFlower.YELLOW,
                CasinoFlower.ASSORTED,
                CasinoFlower.ORANGE,
                CasinoFlower.ASSORTED,
                CasinoFlower.ASSORTED,
                CasinoFlower.BLUE,
                CasinoFlower.PURPLE,
                CasinoFlower.RED,
                CasinoFlower.RED,
                CasinoFlower.BLUE,
            )
        val actual = (0..9).map { ProvablyFairFlowerPoker.flowerAt("p1", "p2", "srv", it) }
        assertEquals(expected, actual)
    }

    @Test
    fun `flower poker deals alternating flowers to the two players`() {
        for (k in 0..4) {
            assertEquals(ProvablyFairFlowerPoker.flowerAt("p1", "p2", "srv", k * 2), ProvablyFairFlowerPoker.flowerAtPlayer1("p1", "p2", "srv", k))
            assertEquals(ProvablyFairFlowerPoker.flowerAt("p1", "p2", "srv", k * 2 + 1), ProvablyFairFlowerPoker.flowerAtPlayer2("p1", "p2", "srv", k))
        }
    }

    @Test
    fun `flower poker draws the two rares at roughly one in five hundred each`() {
        var black = 0
        var white = 0
        val samples = 100_000
        for (i in 0 until samples) {
            when (ProvablyFairFlowerPoker.flowerAt("a", "b", "c", i)) {
                CasinoFlower.BLACK -> black++
                CasinoFlower.WHITE -> white++
                else -> {}
            }
        }
        // Expected 200 of each in 100k draws; a generous band keeps this from flaking while still catching a
        // mis-mapped rare branch (which would give 0 or ~14000).
        assertTrue(black in 120..300, "black rate off: $black in $samples")
        assertTrue(white in 120..300, "white rate off: $white in $samples")
    }

    @Test
    fun `swapping the two player seeds changes the match`() {
        val ab = (0..9).map { ProvablyFairFlowerPoker.flowerAt("alice", "bob", "srv", it) }
        val ba = (0..9).map { ProvablyFairFlowerPoker.flowerAt("bob", "alice", "srv", it) }
        assertTrue(ab != ba, "player seed order must be part of the material")
    }
}
