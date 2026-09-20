package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Objs

/**
 * The nine flower colours a flower-poker patch can grow.
 *
 * SOURCE: MIT `Flower.java` (`rsps-provably-fair`). Every item and object id it names exists unchanged in this
 * revision-667 cache and is referenced through the generated [Items] / [Objs] constants rather than written as a
 * literal: assorted 2460/2980, red 2462/2981, blue 2464/2982, yellow 2466/2983, purple 2468/2984, orange 2470/2985,
 * mixed 2472/2986, white 2474/2987, black 2476/2988. [ordinal] is deliberately the MIT `id`, because the interface
 * and the public verifier both index colours by it.
 */
enum class CasinoFlower(
    val itemId: Int,
    val objectId: Int,
    val displayName: String,
) {
    RED(Items.RED_FLOWERS, Objs.FLOWERS_2981, "Red"),
    BLUE(Items.BLUE_FLOWERS, Objs.FLOWERS_2982, "Blue"),
    YELLOW(Items.YELLOW_FLOWERS, Objs.FLOWERS_2983, "Yellow"),
    PURPLE(Items.PURPLE_FLOWERS, Objs.FLOWERS_2984, "Purple"),
    ORANGE(Items.ORANGE_FLOWERS, Objs.FLOWERS_2985, "Orange"),
    MIXED(Items.FLOWERS_2472, Objs.FLOWERS_2986, "Mixed"),
    ASSORTED(Items.FLOWERS, Objs.FLOWERS_2980, "Assorted"),
    BLACK(Items.BLACK_FLOWERS, Objs.FLOWERS_2988, "Black"),
    WHITE(Items.WHITE_FLOWERS, Objs.FLOWERS_2987, "White"),
    ;

    /** Black and white are the two rares; they force a replant instead of counting towards a hand. */
    val rare: Boolean
        get() = this == BLACK || this == WHITE

    companion object {
        val values = enumValues<CasinoFlower>()
    }
}

/**
 * Dice: a percentage roll in `0.00 .. 100.00`.
 *
 * SOURCE: MIT `ProvablyFairDice.java`. Material `clientSeed:serverSeed:nonce`, the first 4 bytes of the digest read
 * as an unsigned 32-bit value, `% 10001` to land on `0 .. 10000`, then divided by 100. The 10,001 discrete outcomes
 * (not 10,000) are part of the published algorithm and the odds in [CasinoOdds] are derived from that exact count.
 */
object ProvablyFairDice {
    const val OUTCOMES = 10_001

    /** The raw `0 .. 10000` draw; [rollPercentage] is this divided by 100. */
    fun rollScaled(
        clientSeed: String,
        serverSeed: String,
        nonce: Int,
    ): Int {
        val hash = ProvablyFair.digest("$clientSeed:$serverSeed:$nonce")
        return (ProvablyFair.unsigned32(hash) % OUTCOMES).toInt()
    }

    fun rollPercentage(
        clientSeed: String,
        serverSeed: String,
        nonce: Int,
    ): Double = rollScaled(clientSeed, serverSeed, nonce) / 100.0
}

/**
 * Mines: which of the 25 cells of a 5x5 grid hold a mine.
 *
 * SOURCE: MIT `ProvablyFairMines.java`. A draw-bag of the 25 cell indices; for each mine the material
 * `clientSeed:serverSeed:nonce:counter` is hashed, the first 8 bytes are read as an unsigned 64-bit value, reduced
 * modulo the number of cells still in the bag, and the chosen cell is removed with the swap-with-last idiom. The
 * counter starts at 0 and increments once per mine.
 */
object ProvablyFairMines {
    const val SLOTS = 25
    const val MIN_MINES = 1
    const val MAX_MINES = 24

    /** The mined cell indices, as a set of `0 .. 24`. */
    fun generateLayout(
        clientSeed: String,
        serverSeed: String,
        nonce: Int,
        mineCount: Int,
    ): Set<Int> {
        require(mineCount in MIN_MINES..MAX_MINES) { "mineCount must be $MIN_MINES-$MAX_MINES" }
        val bag = IntArray(SLOTS) { it }
        val mines = LinkedHashSet<Int>(mineCount)
        var remaining = SLOTS
        var counter = 0L
        repeat(mineCount) {
            val hash = ProvablyFair.digest("$clientSeed:$serverSeed:$nonce:$counter")
            counter++
            val index = java.lang.Long.remainderUnsigned(ProvablyFair.unsigned64(hash), remaining.toLong()).toInt()
            mines += bag[index]
            remaining--
            bag[index] = bag[remaining]
        }
        return mines
    }
}

/**
 * Blackjack: the order of the 416-card shoe.
 *
 * SOURCE: MIT `ProvablyFairBlackjack.java`. A Fisher-Yates shuffle walked backwards over the deck, where every swap
 * index comes from `SHA-256(clientSeed:serverSeed:nonce:counter)` with the counter starting at 0 on the last card
 * and incrementing once per swap.
 */
object ProvablyFairBlackjack {
    /** Shuffles [deck] in place, exactly as the MIT reference does, so the public verifier reproduces it. */
    fun <T> shuffle(
        deck: MutableList<T>,
        clientSeed: String,
        serverSeed: String,
        nonce: Long,
    ) {
        val base = "$clientSeed:$serverSeed:$nonce:"
        var i = deck.size - 1
        var counter = 0
        while (i > 0) {
            val hash = ProvablyFair.digest(base + counter)
            val j = java.lang.Long.remainderUnsigned(ProvablyFair.unsigned64(hash), (i + 1).toLong()).toInt()
            val swap = deck[i]
            deck[i] = deck[j]
            deck[j] = swap
            i--
            counter++
        }
    }
}

/**
 * Flower poker: the endless stream of flowers a match deals.
 *
 * SOURCE: MIT `ProvablyFairFlowerPoker.java`. Material `p1Seed:p2Seed:serverSeed:index`; the first 8 bytes of the
 * digest are masked to a positive 64-bit value and reduced to `1 .. 500`, where 1 is black, 2 is white and the other
 * 498 numbers cycle through the seven common colours - the classic 1-in-500 rare rate. Both players' client seeds
 * are part of the material, so neither side nor the server alone can steer a match; there is no nonce because the
 * server seed is revealed when the match ends.
 *
 * Flowers alternate between the two players: even indices belong to player 1, odd indices to player 2.
 */
object ProvablyFairFlowerPoker {
    const val RARE_RATE = 500

    private val COMMON =
        arrayOf(
            CasinoFlower.RED,
            CasinoFlower.BLUE,
            CasinoFlower.YELLOW,
            CasinoFlower.PURPLE,
            CasinoFlower.ORANGE,
            CasinoFlower.MIXED,
            CasinoFlower.ASSORTED,
        )

    fun flowerAt(
        p1Seed: String,
        p2Seed: String,
        serverSeed: String,
        index: Int,
    ): CasinoFlower {
        require(index >= 0) { "index must be >= 0" }
        val hash = ProvablyFair.digest("$p1Seed:$p2Seed:$serverSeed:$index")
        val positive = ProvablyFair.unsigned64(hash) and 0x7FFFFFFFFFFFFFFFL
        val roll = (positive % RARE_RATE).toInt() + 1
        return when (roll) {
            1 -> CasinoFlower.BLACK
            2 -> CasinoFlower.WHITE
            else -> COMMON[(roll - 3) % COMMON.size]
        }
    }

    fun flowerAtPlayer1(
        p1Seed: String,
        p2Seed: String,
        serverSeed: String,
        k: Int,
    ): CasinoFlower = flowerAt(p1Seed, p2Seed, serverSeed, k * 2)

    fun flowerAtPlayer2(
        p1Seed: String,
        p2Seed: String,
        serverSeed: String,
        k: Int,
    ): CasinoFlower = flowerAt(p1Seed, p2Seed, serverSeed, k * 2 + 1)
}
