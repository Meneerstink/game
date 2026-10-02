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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bug search 2026-10-02: a live casino round holds coins outside the inventory, so they were never at risk on death - a
 * player about to die put everything on a blackjack hand or a mines board and finished it after respawning. At death the
 * round is now settled at its current value and the coins are RETURNED (not credited), so the death resolution hands them
 * to the killer / gravestone (death.plugin.kts casinoCoins). Seeds pinned as in [CasinoGameFlowTests].
 */
class CasinoDeathSettlementTests {
    private fun player(
        name: String = "tester",
        coins: Long = 1_000_000,
    ): Player {
        val coinDef =
            ItemDef(CasinoWallet.CURRENCY).apply {
                this.name = "Coins"
                stacks = true
            }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, any()) } returns coinDef
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.username } returns name
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.STACK)
        every { player.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        if (coins > 0) player.inventory.add(CasinoWallet.CURRENCY, coins.toInt())
        player.attr[CasinoSeeds.CLIENT_SEED] = "client"
        player.attr[CasinoSeeds.SERVER_SEED] = "server"
        player.attr[CasinoSeeds.NONCE] = "0"
        return player
    }

    private fun held(p: Player): Long = CasinoWallet.balance(p) + p.bank.getItemCount(CasinoWallet.CURRENCY)

    @Test
    fun `a mines board is cashed out at death and the coins are handed to the death, not the player`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        MinesGame.reveal(p, 0)
        val value = MinesGame.active(p)!!.cashoutValue()
        assertEquals(value, Casino.riskCoins(p), "the risk read-out shows what the killer would get")

        assertEquals(value, Casino.settleForDeath(p))
        assertEquals(90_000, held(p), "nothing credited to the inventory or the bank")
        assertFalse(MinesGame.isPlaying(p), "the board is gone - it cannot be cashed out again after respawning")
        assertEquals(0, Casino.settleForDeath(p), "a second death call settles nothing")
    }

    @Test
    fun `a board with no reveal yet settles at the same value a cash-out formula gives`() {
        val p = player(coins = 100_000)
        MinesGame.start(p, 10_000, 3)
        val value = MinesGame.active(p)!!.cashoutValue()
        assertEquals(value, Casino.settleForDeath(p))
        assertTrue(value < 10_000, "never more than the stake: dying is not a free undo")
    }

    @Test
    fun `an open blackjack hand stands at death with exactly the result standing would give`() {
        val dies = player(coins = 1_000_000)
        val stands = player(coins = 1_000_000)
        val table = BlackjackGame.deal(dies, 10_000)!!
        BlackjackGame.deal(stands, 10_000)
        if (table.phase != BlackjackGame.Phase.PLAYER) return // a dealt natural settles at once: nothing left open

        val before = held(stands)
        BlackjackGame.stand(stands)
        val standingPays = held(stands) - before

        val heldBefore = held(dies)
        assertEquals(standingPays, Casino.settleForDeath(dies))
        assertEquals(heldBefore, held(dies), "the payout goes to the death resolution, not the player")
        assertNull(BlackjackGame.active(dies), "the table is cleared - nothing to finish after respawning")
    }

    @Test
    fun `a flower poker match being planted pays the dying winner's pot to the death and is never paid twice`() {
        val a = player("a")
        val b = player("b")
        val match = FlowerPokerMatch(a, b)
        a.attr[FLOWER_MATCH_ATTR] = match
        b.attr[FLOWER_MATCH_ATTR] = match
        match.setStake(50_000)
        match.setAccepted(a, true)
        match.setAccepted(b, true)
        assertEquals(FlowerPokerMatch.StartResult.OK, match.start())
        val decisive = assertNotNull(match.decisive())
        val winner = if (decisive.outcome > 0) a else b
        val loser = if (winner == a) b else a
        val winnerBefore = held(winner)
        val loserBefore = held(loser)

        assertEquals(match.pot, Casino.riskCoins(winner))
        assertEquals(match.pot, Casino.settleForDeath(winner), "the dying winner's pot joins the death")
        assertEquals(winnerBefore, held(winner))
        assertEquals(loserBefore, held(loser))
        assertNull(match.finish(), "the planting animation finds the match settled")
        assertNotNull(match.settlement, "and shows that result instead of refunding both stakes")
    }

    @Test
    fun `a flower poker loser who dies leaves the pot to the winner`() {
        val a = player("a")
        val b = player("b")
        val match = FlowerPokerMatch(a, b)
        a.attr[FLOWER_MATCH_ATTR] = match
        b.attr[FLOWER_MATCH_ATTR] = match
        match.setStake(50_000)
        match.setAccepted(a, true)
        match.setAccepted(b, true)
        match.start()
        val winner = if (match.decisive()!!.outcome > 0) a else b
        val loser = if (winner == a) b else a
        val winnerBefore = held(winner)
        assertEquals(0, Casino.settleForDeath(loser))
        assertEquals(winnerBefore + match.pot, held(winner))
    }

    @Test
    fun `coins for the death resolution never exceed a stack`() {
        val stacks = CasinoWallet.coinStacks(Int.MAX_VALUE.toLong() + 5)
        assertEquals(listOf(Int.MAX_VALUE, 5), stacks.map { it.amount })
        assertTrue(CasinoWallet.coinStacks(0).isEmpty())
    }

    @Test
    fun `the death resolution routes the settled coins like beast of burden cargo, and every coin entry is gated`() {
        val death = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/death.plugin.kts").readText()
        assertTrue(death.contains("converted += casinoCoins(victim)"), "PvP: killer loot")
        assertTrue(death.contains("takeAllCargo(victim) + casinoCoins(victim)"), "PvM: gravestone")
        assertTrue(death.contains("Casino.settleForDeath(victim)"), "safe death: paid back")
        val screens = File("src/main/kotlin/gg/rsmod/plugins/content/activity/casino/CasinoScreens.kt").readText()
        assertTrue(screens.contains("if (Casino.refusePlay(player)) return"), "every game screen opens through the gate")
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/activity/casino/casino.plugin.kts").readText()
        listOf(
            "Layout.Dice.ROLL_BUTTON) {",
            "Layout.Mines.START_BUTTON) {",
            "Layout.Blackjack.DOUBLE_BUTTON) {",
            "Layout.Blackjack.SPLIT_BUTTON) {",
            "Layout.Blackjack.INSURE_BUTTON) {",
        ).forEach { button ->
            val at = plugin.indexOf("component = $button")
            assertTrue(at >= 0, button)
            assertTrue(plugin.substring(at, at + 200).contains("Casino.refusePlay(player)"), "$button puts coins in: gated")
        }
        assertTrue(plugin.contains("if (Casino.refusePlay(player)) return\n    val stake = CasinoScreens.bet(player)\n    val refusal = BlackjackGame.validate"), "deal")
        val flower = File("src/main/kotlin/gg/rsmod/plugins/content/activity/casino/flower_poker.plugin.kts").readText()
        assertTrue(flower.contains("Casino.refusePlay(side)"), "both stakes are escrowed only from where play is allowed")
    }
}
