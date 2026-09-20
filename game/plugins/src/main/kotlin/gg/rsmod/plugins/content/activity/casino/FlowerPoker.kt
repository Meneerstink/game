package gg.rsmod.plugins.content.activity.casino

/**
 * Flower-poker hand ranking.
 *
 * SOURCE: Roat Pkz wiki "Flower Poker" - from lowest to highest: Bust ("no matching flower colors"), 1 Pair,
 * 2 Pair, 3 Oak, Full House ("three of one flower color and two of another"), 4 Oak, 5 Oak. White or black
 * flowers "trigger a replant", and when both players hold the same hand "you Re-Plant".
 *
 * Colour carries no rank of its own: a pair of reds and a pair of blues are the same hand, which is why an equal
 * ranking is a replant rather than a high-card comparison. That is the rule the whole game rests on, so it is
 * modelled directly - [FlowerPokerHand] has no tiebreaker field for a caller to accidentally invent one.
 */
enum class FlowerPokerHand(
    val rank: Int,
    val displayName: String,
) {
    BUST(0, "Bust"),
    ONE_PAIR(1, "1 Pair"),
    TWO_PAIR(2, "2 Pair"),
    THREE_OAK(3, "3 Oak"),
    FULL_HOUSE(4, "Full House"),
    FOUR_OAK(5, "4 Oak"),
    FIVE_OAK(6, "5 Oak"),
}

object FlowerPoker {
    /** Flowers planted per player per round. */
    const val HAND_SIZE = 5

    /**
     * A single planted round for both players, and what it resolved to.
     *
     * [replant] covers both replant causes so the caller never has to re-derive them: a rare flower anywhere in
     * the ten, or two hands of equal rank.
     */
    data class Round(
        val index: Int,
        val first: List<CasinoFlower>,
        val second: List<CasinoFlower>,
    ) {
        val rareDrawn: Boolean
            get() = (first + second).any { it.rare }

        val firstHand: FlowerPokerHand
            get() = evaluate(first)

        val secondHand: FlowerPokerHand
            get() = evaluate(second)

        /** 1 if the first player wins, -1 if the second does, 0 when the round must be replanted. */
        val outcome: Int
            get() {
                if (rareDrawn) {
                    return 0
                }
                return firstHand.rank.compareTo(secondHand.rank).coerceIn(-1, 1)
            }

        val replant: Boolean
            get() = outcome == 0

        val reason: String
            get() =
                when {
                    rareDrawn -> "A rare flower was planted - replant."
                    firstHand == secondHand -> "Both players planted ${firstHand.displayName} - replant."
                    outcome > 0 -> "${firstHand.displayName} beats ${secondHand.displayName}."
                    else -> "${secondHand.displayName} beats ${firstHand.displayName}."
                }
    }

    /**
     * Ranks five flowers.
     *
     * A rare flower never reaches a ranking in a real match (the round is replanted), but the function stays total
     * so it can be called on any five flowers without the caller guarding first.
     */
    fun evaluate(flowers: List<CasinoFlower>): FlowerPokerHand {
        require(flowers.size == HAND_SIZE) { "a flower poker hand is $HAND_SIZE flowers, got ${flowers.size}" }
        val counts = flowers.groupingBy { it }.eachCount().values.sortedDescending()
        return when {
            counts[0] == 5 -> FlowerPokerHand.FIVE_OAK
            counts[0] == 4 -> FlowerPokerHand.FOUR_OAK
            counts[0] == 3 && counts.getOrElse(1) { 0 } == 2 -> FlowerPokerHand.FULL_HOUSE
            counts[0] == 3 -> FlowerPokerHand.THREE_OAK
            counts[0] == 2 && counts.getOrElse(1) { 0 } == 2 -> FlowerPokerHand.TWO_PAIR
            counts[0] == 2 -> FlowerPokerHand.ONE_PAIR
            else -> FlowerPokerHand.BUST
        }
    }

    /**
     * The [index]-th planted round of a match.
     *
     * Rounds consume the flower stream in order: round 0 uses draws 0-9, round 1 uses 10-19, and so on, with the
     * first player taking the even draws and the second the odd ones. A replant therefore does not re-roll the
     * same draws - it moves on - so no one can force a favourable re-deal.
     */
    fun round(
        firstSeed: String,
        secondSeed: String,
        serverSeed: String,
        index: Int,
    ): Round {
        require(index >= 0) { "round index must be >= 0" }
        val base = index * HAND_SIZE
        val first = (0 until HAND_SIZE).map { ProvablyFairFlowerPoker.flowerAtPlayer1(firstSeed, secondSeed, serverSeed, base + it) }
        val second = (0 until HAND_SIZE).map { ProvablyFairFlowerPoker.flowerAtPlayer2(firstSeed, secondSeed, serverSeed, base + it) }
        return Round(index, first, second)
    }

    /**
     * Plays a match out from round 0 until one side wins.
     *
     * [maxRounds] is a safety valve, not a rule: the chance of a replant is well under one in two, so reaching it
     * is astronomically unlikely, but a match must never be able to loop forever on the game thread.
     */
    fun play(
        firstSeed: String,
        secondSeed: String,
        serverSeed: String,
        maxRounds: Int = 64,
    ): List<Round> {
        val rounds = mutableListOf<Round>()
        for (index in 0 until maxRounds) {
            val round = round(firstSeed, secondSeed, serverSeed, index)
            rounds += round
            if (!round.replant) {
                break
            }
        }
        return rounds
    }
}
