package gg.rsmod.plugins.content.scrolls

import kotlin.random.Random

/** OSRS Wiki clue/casket pages; cache 2686 identities. Local imports are resolved, never guessed. */
enum class ClueScrollTier(
    val osrsScrollId: Int,
    val osrsCasketId: Int,
    val trailSteps: IntRange,
    val rewardRolls: IntRange,
    val masterChance: Int = 0,
) {
    EASY(2677, 20546, 2..4, 2..4, 50),
    MEDIUM(2801, 20545, 3..5, 3..5, 30),
    HARD(2722, 20544, 4..6, 4..6, 15),
    ELITE(12073, 20543, 5..7, 4..6, 5),
    MASTER(19835, 19836, 6..8, 5..7),
    ;

    val scrollId: Int get() = ClueItems.local(osrsScrollId) ?: -1
    val casketId: Int get() = ClueItems.local(osrsCasketId) ?: -1
    val key: String get() = if (this == ELITE) "elite" else "clue_${name.lowercase()}"
    val displayName: String get() = if (this == MASTER) "78" else name.lowercase().replaceFirstChar { it.uppercaseChar() }
    val casketName: String get() = "Reward casket (${if (this == MASTER) "78" else name.lowercase()})"

    /** The five included tiers have equally likely trail lengths (Wiki clue pages). Beginner is owner-excluded. */
    fun rollTrailLength(random: Random): Int = random.nextInt(trailSteps.first, trailSteps.last + 1)
}
