package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Correctness net for the two halves of collecting that do not need a Player: what an offer still
 * owes once the inventory has taken what it can ([GrandExchangeCollection.payOut]), and what the
 * player is told about it ([GrandExchangeCollection.describe]).
 *
 * The inventory is stood in for by a deposit function, which is exactly the seam the production code
 * uses, so these exercise the real logic rather than a copy of it.
 */
class GrandExchangeCollectionTests {
    @Test
    fun `an offer the inventory swallows whole leaves nothing behind`() {
        val deposit = RecordingDeposit(takeAll = true)

        val leftover = GrandExchangeCollection.payOut(coins = 500L, items = 20, itemId = 4151, deposit = deposit::take)

        assertEquals(Leftover(0L, 0), leftover)
        assertEquals(listOf(Items.COINS_995 to 500, 4151 to 20), deposit.calls)
    }

    @Test
    fun `a full inventory leaves everything to be restored`() {
        val deposit = RecordingDeposit(takeAll = false)

        val leftover = GrandExchangeCollection.payOut(coins = 500L, items = 20, itemId = 4151, deposit = deposit::take)

        assertEquals(Leftover(500L, 20), leftover)
    }

    @Test
    fun `only what fitted is deducted from what is owed`() {
        val deposit = RecordingDeposit(takeAll = false, taking = { _, amount -> amount / 2 })

        val leftover = GrandExchangeCollection.payOut(coins = 500L, items = 20, itemId = 4151, deposit = deposit::take)

        assertEquals(Leftover(250L, 10), leftover)
    }

    @Test
    fun `coins beyond a stack are paid out as far as a stack goes`() {
        val owed = Int.MAX_VALUE.toLong() + 1000L
        val deposit = RecordingDeposit(takeAll = true)

        val leftover = GrandExchangeCollection.payOut(coins = owed, items = 0, itemId = null, deposit = deposit::take)

        assertEquals(1000L, leftover.coins)
        assertEquals(listOf(Items.COINS_995 to Int.MAX_VALUE), deposit.calls)
    }

    @Test
    fun `items whose offer has vanished are left to be restored, not dropped`() {
        val deposit = RecordingDeposit(takeAll = true)

        val leftover = GrandExchangeCollection.payOut(coins = 0L, items = 7, itemId = null, deposit = deposit::take)

        assertEquals(Leftover(0L, 7), leftover)
        assertEquals(emptyList(), deposit.calls)
    }

    @Test
    fun `having no offers at all is not the same as having nothing to collect`() {
        val noOffers = CollectionOutcome(collected = 0, partial = 0, nothingOwed = false, noOffers = true)
        val nothingOwed = CollectionOutcome(collected = 0, partial = 0, nothingOwed = true, noOffers = false)

        assertEquals(listOf("You have no Grand Exchange offers."), GrandExchangeCollection.describe(noOffers))
        assertEquals(listOf("You have nothing to collect."), GrandExchangeCollection.describe(nothingOwed))
    }

    @Test
    fun `a collection that ran out of room says so`() {
        val partial = CollectionOutcome(collected = 0, partial = 1, nothingOwed = false, noOffers = false)
        val mixed = CollectionOutcome(collected = 1, partial = 1, nothingOwed = false, noOffers = false)
        val whole = CollectionOutcome(collected = 2, partial = 0, nothingOwed = false, noOffers = false)

        assertEquals(1, GrandExchangeCollection.describe(partial).size)
        assertEquals(2, GrandExchangeCollection.describe(mixed).size)
        assertEquals(listOf("Collected your Grand Exchange proceeds."), GrandExchangeCollection.describe(whole))
    }

    /** Stands in for the inventory, remembering what it was handed. */
    private class RecordingDeposit(
        private val takeAll: Boolean,
        private val taking: ((Int, Int) -> Int)? = null,
    ) {
        val calls = mutableListOf<Pair<Int, Int>>()

        fun take(
            item: Int,
            amount: Int,
        ): Int {
            calls += item to amount
            return taking?.invoke(item, amount) ?: if (takeAll) amount else 0
        }
    }
}
