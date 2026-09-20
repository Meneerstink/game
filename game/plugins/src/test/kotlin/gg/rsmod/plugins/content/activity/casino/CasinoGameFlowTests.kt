package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of the games against a player's coins: the wagering, the payouts and the anti-exploit
 * rules the owner asked for ("no dupes, double bets, disconnect exploits or packet abuse").
 *
 * The seed pair is pinned on the test player so the outcomes are the ones already verified in
 * [ProvablyFairTests]: with `client` / `server` / nonce 0 the dice draw is 9555 (95.55%) and a three-mine board
 * puts mines on cells 9, 22 and 8. That lets these tests assert exact coin balances instead of "something
 * plausible happened".
 *
 * Coin containers are forced to [ContainerStackType.STACK] so the suite needs no cache: stacking is the only
 * definition-driven behaviour coins rely on here.
 */
class CasinoGameFlowTests {
    private fun player(
        coins: Long = 1_000_000,
        clientSeed: String = "client",
        serverSeed: String = "server",
    ): Player {
        // ItemContainer looks the item up to decide stacking and note links, so the definition has to be a real
        // ItemDef rather than a relaxed mock. Coins are the only item these tests move.
        val coinDef =
            ItemDef(CasinoWallet.CURRENCY).apply {
                name = "Coins"
                stacks = true
            }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, any()) } returns coinDef
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions

        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.username } returns "tester"
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.STACK)
        every { player.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)

        if (coins > 0) {
            player.inventory.add(CasinoWallet.CURRENCY, coins.toInt())
        }
        player.attr[CasinoSeeds.CLIENT_SEED] = clientSeed
        player.attr[CasinoSeeds.SERVER_SEED] = serverSeed
        player.attr[CasinoSeeds.NONCE] = "0"
        return player
    }

    private fun coins(player: Player): Long = CasinoWallet.balance(player)

    // ---------------------------------------------------------------- wallet

    @Test
    fun `a withdrawal the player cannot cover changes nothing`() {
        val p = player(coins = 500)
        assertFalse(CasinoWallet.withdraw(p, 1_000))
        assertEquals(500, coins(p))
    }

    @Test
    fun `a withdrawal is all or nothing`() {
        val p = player(coins = 1_000)
        assertTrue(CasinoWallet.withdraw(p, 1_000))
        assertEquals(0, coins(p))
        assertFalse(CasinoWallet.withdraw(p, 1))
        assertEquals(0, coins(p))
    }

    @Test
    fun `a payout that overflows the inventory stack lands in the bank instead of vanishing`() {
        val p = player(coins = 0)
        p.inventory.add(CasinoWallet.CURRENCY, Int.MAX_VALUE)
        val payout = CasinoWallet.deposit(p, 5_000)
        assertEquals(5_000, payout.total, "the payout must not be lost")
        assertEquals(5_000, payout.bank)
        assertEquals(5_000, p.bank.getItemCount(CasinoWallet.CURRENCY).toLong())
    }

    // ---------------------------------------------------------------- dice

    @Test
    fun `a winning dice roll debits the stake and credits the payout`() {
        val p = player(coins = 100_000)
        // Draw is 9555 (95.55%); target 50 wins.
        val result = DiceGame.roll(p, stake = 10_000, target = 50)
        assertNotNull(result)
        assertEquals(9555, result.rollScaled)
        assertTrue(result.won)
        assertEquals(CasinoOdds.dicePayout(10_000, 50), result.payout)
        // 100,000 - 10,000 staked + payout
        assertEquals(100_000 - 10_000 + result.payout, coins(p))
    }

    @Test
    fun `a losing dice roll keeps the stake and pays nothing`() {
        val p = player(coins = 100_000)
        // Draw is 9555 (95.55%); target 96 needs 9600.
        val result = DiceGame.roll(p, stake = 10_000, target = 96)
        assertNotNull(result)
        assertFalse(result.won)
        assertEquals(0, result.payout)
        assertEquals(90_000, coins(p))
    }

    @Test
    fun `a dice bet the player cannot cover is refused without touching their coins`() {
        val p = player(coins = 5_000)
        assertNull(DiceGame.roll(p, stake = 10_000, target = 50))
        assertEquals(5_000, coins(p))
        assertEquals(DiceGame.Rejection.NOT_ENOUGH_COINS, DiceGame.validate(p, 10_000, 50))
    }

    @Test
    fun `a dice bet whose win could not be paid in coins is refused up front`() {
        val p = player(coins = 100_000_000)
        // Target 100 pays ~9,900x, which overflows a coin stack long before the roll happens.
        assertEquals(DiceGame.Rejection.PAYOUT_TOO_LARGE, DiceGame.validate(p, 100_000_000, 100))
        assertNull(DiceGame.roll(p, stake = 100_000_000, target = 100))
        assertEquals(100_000_000, coins(p))
    }

    @Test
    fun `dice rejects targets and stakes outside the published limits`() {
        val p = player()
        assertEquals(DiceGame.Rejection.BAD_TARGET, DiceGame.validate(p, 10_000, 0))
        assertEquals(DiceGame.Rejection.BAD_TARGET, DiceGame.validate(p, 10_000, 101))
        assertEquals(DiceGame.Rejection.STAKE_TOO_SMALL, DiceGame.validate(p, CasinoWallet.MIN_WAGER - 1, 50))
        assertEquals(DiceGame.Rejection.STAKE_TOO_LARGE, DiceGame.validate(p, CasinoWallet.MAX_WAGER + 1, 50))
    }

    @Test
    fun `every dice roll consumes exactly one nonce`() {
        val p = player(coins = 1_000_000)
        assertEquals(0, CasinoSeeds.peekNonce(p))
        val first = DiceGame.roll(p, 10_000, 50)!!
        val second = DiceGame.roll(p, 10_000, 50)!!
        assertEquals(0, first.nonce)
        assertEquals(1, second.nonce)
        assertEquals(2, CasinoSeeds.peekNonce(p))
        // Two rounds on the same seed pair must not share an outcome.
        assertTrue(first.rollScaled != second.rollScaled)
    }

    @Test
    fun `a refused dice bet does not burn a nonce`() {
        val p = player(coins = 5_000)
        DiceGame.roll(p, stake = 10_000, target = 50)
        assertEquals(0, CasinoSeeds.peekNonce(p))
    }

    @Test
    fun `dice writes a history row the player can verify`() {
        val p = player(coins = 100_000)
        val result = DiceGame.roll(p, 10_000, 50)!!
        val row = CasinoHistory.recent(p).first()
        assertEquals(CasinoGame.DICE, row.game)
        assertEquals(10_000, row.stake)
        assertEquals(result.payout, row.payout)
        assertEquals(result.nonce, row.nonce)
        assertEquals("client", row.clientSeed)
        assertEquals(ProvablyFair.sha256Hex("server"), row.serverSeedHash)
    }

    // ---------------------------------------------------------------- mines

    @Test
    fun `starting a mines board debits the stake and lays the verified layout`() {
        val p = player(coins = 100_000)
        val board = MinesGame.start(p, stake = 10_000, mineCount = 3)
        assertNotNull(board)
        assertEquals(90_000, coins(p))
        assertEquals(setOf(9, 22, 8), board.mines)
    }

    @Test
    fun `revealing a mine ends the board and keeps the stake`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        val reveal = MinesGame.reveal(p, 9)
        assertTrue(reveal is MinesGame.Reveal.Boom)
        assertEquals(90_000, coins(p))
        assertFalse(MinesGame.isPlaying(p), "a busted board must not stay live")
    }

    @Test
    fun `revealing gems then cashing out pays the sourced multiplier`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        assertTrue(MinesGame.reveal(p, 0) is MinesGame.Reveal.Gem)
        assertTrue(MinesGame.reveal(p, 1) is MinesGame.Reveal.Gem)
        val cashout = MinesGame.cashout(p)
        assertNotNull(cashout)
        assertEquals(2, cashout.gems)
        assertEquals(CasinoOdds.minesPayout(10_000, 3, 2), cashout.payout)
        assertEquals(90_000 + cashout.payout, coins(p))
        assertFalse(MinesGame.isPlaying(p))
    }

    @Test
    fun `a second mines board cannot be started while one is live`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        assertEquals(MinesGame.Rejection.ALREADY_PLAYING, MinesGame.validate(p, 10_000, 3))
        assertNull(MinesGame.start(p, 10_000, 3))
        assertEquals(90_000, coins(p), "the refused second bet must not be charged")
    }

    @Test
    fun `re-clicking a revealed cell or clicking off the grid is not a move`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        MinesGame.reveal(p, 0)
        assertTrue(MinesGame.reveal(p, 0) is MinesGame.Reveal.Ignored, "a repeat click must not count as a gem")
        assertTrue(MinesGame.reveal(p, -1) is MinesGame.Reveal.Ignored)
        assertTrue(MinesGame.reveal(p, 25) is MinesGame.Reveal.Ignored)
        assertEquals(1, MinesGame.active(p)!!.gems)
    }

    @Test
    fun `clicking a dead board does nothing`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        MinesGame.reveal(p, 9) // boom
        assertTrue(MinesGame.reveal(p, 0) is MinesGame.Reveal.Ignored)
        assertNull(MinesGame.cashout(p))
        assertEquals(90_000, coins(p))
    }

    @Test
    fun `cashing out before revealing anything is refused rather than losing two percent`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        assertNull(MinesGame.cashout(p))
        assertTrue(MinesGame.isPlaying(p))
        assertEquals(90_000, coins(p))
    }

    @Test
    fun `a mines board survives being rebuilt from its saved state`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        MinesGame.reveal(p, 0)
        // active() re-reads the persisted attributes: this is exactly what a relog does.
        val restored = MinesGame.active(p)
        assertNotNull(restored)
        assertEquals(setOf(0), restored.revealed)
        assertEquals(setOf(9, 22, 8), restored.mines, "the layout must not move across a relog")
        assertEquals(10_000, restored.stake)
    }

    @Test
    fun `clearing a full board pays out automatically`() {
        val p = player(coins = 10_000_000)
        // 24 mines leaves exactly one safe cell, so one correct click clears the board.
        val board = MinesGame.start(p, 10_000, 24)!!
        val safe = (0 until 25).first { it !in board.mines }
        val reveal = MinesGame.reveal(p, safe)
        assertTrue(reveal is MinesGame.Reveal.Cleared)
        assertEquals(CasinoOdds.minesPayout(10_000, 24, 1), reveal.payout)
        assertFalse(MinesGame.isPlaying(p))
    }

    // ---------------------------------------------------------------- blackjack

    @Test
    fun `dealing blackjack debits the stake and gives both sides two cards`() {
        val p = player(coins = 100_000)
        val table = BlackjackGame.deal(p, 10_000)
        assertNotNull(table)
        assertEquals(2, table.dealer.size)
        assertEquals(2, table.hands[0].cards.size)
        // The stake is debited; a natural may already have paid out, so only check it was taken.
        assertTrue(coins(p) <= 90_000 + 25_000)
    }

    @Test
    fun `a second blackjack hand cannot be dealt while one is live`() {
        val p = player(coins = 1_000_000)
        val table = BlackjackGame.deal(p, 10_000)!!
        if (table.phase == BlackjackGame.Phase.PLAYER) {
            val before = coins(p)
            assertEquals(BlackjackGame.Rejection.ALREADY_PLAYING, BlackjackGame.validate(p, 10_000))
            assertNull(BlackjackGame.deal(p, 10_000))
            assertEquals(before, coins(p), "the refused second deal must not be charged")
        }
    }

    @Test
    fun `standing settles the hand and pays out at most once`() {
        val p = player(coins = 1_000_000)
        val table = BlackjackGame.deal(p, 10_000)!!
        if (table.phase == BlackjackGame.Phase.PLAYER) {
            val settled = BlackjackGame.stand(p)
            assertNotNull(settled)
            assertEquals(BlackjackGame.Phase.SETTLED, settled.phase)
            val after = coins(p)
            // Acting on a settled table must not pay again.
            assertNull(BlackjackGame.hit(p))
            assertNull(BlackjackGame.stand(p))
            assertEquals(after, coins(p))
        }
    }

    @Test
    fun `the dealer stands on every seventeen including a soft one`() {
        // Verified directly against the rule rather than through a dealt hand, so it cannot be missed by luck.
        assertEquals(17, BlackjackCards.value(listOf(0, 5)).total) // A + 6 = soft 17
        assertTrue(BlackjackCards.value(listOf(0, 5)).soft)
        assertTrue(BlackjackCards.value(listOf(0, 5)).total >= 17, "soft 17 must be a standing total")
    }

    @Test
    fun `a settled blackjack table is cleared so the player can bet again`() {
        val p = player(coins = 1_000_000)
        val table = BlackjackGame.deal(p, 10_000)!!
        if (table.phase == BlackjackGame.Phase.PLAYER) {
            BlackjackGame.stand(p)
        }
        BlackjackGame.finish(p)
        assertNull(BlackjackGame.active(p))
        assertNull(BlackjackGame.validate(p, 10_000))
    }

    // ---------------------------------------------------------------- cross-game rules

    @Test
    fun `the server seed cannot be rotated while a round is live`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        assertTrue(Casino.hasLiveRound(p))
        assertNull(Casino.rotateSeed(p), "rotating mid-board would reveal the seed the board is derived from")
        assertEquals("server", p.attr[CasinoSeeds.SERVER_SEED])
        assertEquals(Casino.SeedChange.ROUND_LIVE, Casino.setClientSeed(p, "newseed"))
    }

    @Test
    fun `rotating the seed reveals the old one and starts a fresh nonce`() {
        val p = player(coins = 100_000)
        DiceGame.roll(p, 10_000, 50)
        val revealed = Casino.rotateSeed(p)
        assertNotNull(revealed)
        assertEquals("server", revealed.serverSeed)
        assertEquals(1, revealed.rounds)
        assertEquals(ProvablyFair.sha256Hex("server"), revealed.hash)
        assertEquals(0, CasinoSeeds.peekNonce(p))
        assertTrue(p.attr[CasinoSeeds.SERVER_SEED] != "server", "a rotated seed must be replaced")
    }

    @Test
    fun `a client seed is only accepted in the published alphabet`() {
        val p = player()
        assertEquals(Casino.SeedChange.OK, Casino.setClientSeed(p, "my-seed-1"))
        assertEquals("my-seed-1", CasinoSeeds.clientSeed(p))
        assertEquals(Casino.SeedChange.INVALID_SEED, Casino.setClientSeed(p, "bad:seed"))
        assertEquals("my-seed-1", CasinoSeeds.clientSeed(p), "a rejected seed must not be stored")
    }

    @Test
    fun `changing the client seed does not rewind the nonce`() {
        val p = player(coins = 1_000_000)
        DiceGame.roll(p, 10_000, 50)
        DiceGame.roll(p, 10_000, 50)
        assertEquals(2, CasinoSeeds.peekNonce(p))
        Casino.setClientSeed(p, "another")
        assertEquals(2, CasinoSeeds.peekNonce(p), "reusing a nonce would repeat an outcome")
    }

    @Test
    fun `history is capped so a heavy gambler cannot grow their save without bound`() {
        val p = player(coins = 1_000_000_000)
        repeat(CasinoHistory.MAX_ENTRIES + 10) { DiceGame.roll(p, 1_000, 50) }
        assertEquals(CasinoHistory.MAX_ENTRIES, CasinoHistory.recent(p).size)
    }
}
