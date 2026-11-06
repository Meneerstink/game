package gg.rsmod.plugins.content.activity.toa.core

import gg.rsmod.game.model.PlayerUID
import kotlin.random.Random

/** One reward line by OSRS item id; the raid resolves the local id (Items / ToaIds) when it fills the chest. */
data class ToaLootDrop(val osrsItem: Int, val amount: Int, val noted: Boolean = false)

/** What one player needs besides the points for the tertiary rolls (Thread of Elidinis, jewels). */
data class ToaLootPlayer(
    val uid: PlayerUID,
    val rewardPoints: Int,
    val completions: Int,
    val hadThread: Boolean,
    /** The jewels this player owns (or has in a partisan), by OSRS id - the Chest wiki's ownership check, not the collection log. */
    val ownedJewels: Set<Int>,
)

/**
 * N462 ToA reward chest - all numbers OSRS Wiki Chest (Tombs of Amascut) oldid 15385393 + Module:Tombs_of_Amascut_loot oldid 15313816
 * (GPT-A 20-beloningen.txt, 20-punten-formules.tsv, 20-commons.tsv, 20-purple-gewichten-per5.tsv, 20-droptabel-bron.tsv).
 * Our raid levels are 0 / 150 / 300 only (owner decision A), so the 2025 high-level weight changes (350+) and the challenge kits
 * (350/425/500) and remnants (450) never apply; the formulas below still take any raid level.
 */
object ToaLoot {
    enum class Unique(val osrsItem: Int, val weight: Int, val unlockRaidLevel: Int) {
        OSMUMTENS_FANG(26219, 70, 50),
        LIGHTBEARER(25975, 70, 50),
        ELIDINIS_WARD(25985, 30, 150),
        MASORI_MASK(27226, 20, 150),
        MASORI_BODY(27229, 20, 150),
        MASORI_CHAPS(27232, 20, 150),
        TUMEKENS_SHADOW(27277, 10, 150), // dropped uncharged (drop table row "Tumeken's shadow (uncharged)")
    }

    /** The 27 common entries: OSRS item, points per item (divisor; null = Cache of runes, always 1), noted as the drop table lists it. */
    enum class Common(val osrsItem: Int, val divisor: Int?, val noted: Boolean) {
        COINS(995, 1, false),
        DEATH_RUNE(560, 20, false),
        SOUL_RUNE(566, 40, false),
        GOLD_ORE(444, 90, true),
        DRAGON_DART_TIP(11232, 100, false),
        MAHOGANY_LOGS(6332, 180, true),
        SAPPHIRE(1607, 200, true),
        EMERALD(1605, 250, true),
        GOLD_BAR(2357, 250, true),
        POTATO_CACTUS(3138, 250, true),
        RAW_SHARK(383, 250, true),
        RUBY(1603, 300, true),
        DIAMOND(1601, 400, true),
        RAW_MANTA_RAY(389, 450, true),
        CACTUS_SPINE(6016, 600, true),
        DRAGONSTONE(1615, 600, true),
        BATTLESTAFF(1391, 1100, true),
        COCONUT_MILK(5935, 1100, true),
        LILY_OF_THE_SANDS(27272, 1100, true),
        TOADFLAX_SEED(5296, 2000, false),
        RANARR_SEED(5295, 2500, false),
        TORSTOL_SEED(5304, 3200, false),
        SNAPDRAGON_SEED(5300, 3200, false),
        DRAGON_MED_HELM(1149, 4000, true),
        MAGIC_SEED(5316, 6500, false),
        BLOOD_ESSENCE(26390, 7500, false),
        CACHE_OF_RUNES(27293, null, false),
    }

    const val FOSSILISED_DUNG = 27216
    const val THREAD_OF_ELIDINIS = 27279
    const val TUMEKENS_GUARDIAN = 27352
    const val CLUE_SCROLL_ELITE = 12073
    /** Breach of the Scarab, Eye of the Corruptor, Jewel of the Sun, Jewel of Amascut (build 240). */
    val JEWELS = listOf(27283, 27285, 27289, 30893)

    /** "Below 1,500 reward points: only Fossilised dung" (Chest wiki). */
    const val DUNG_THRESHOLD = 1_500
    const val MAX_PURPLE_CHANCE = 0.55

    /** S(R): the raid level the purple chance uses. */
    fun purpleRaidLevel(raidLevel: Int): Double = when {
        raidLevel <= 310 -> raidLevel.toDouble()
        raidLevel <= 430 -> 310 + (raidLevel - 310) / 3.0
        else -> 350 + (raidLevel - 430) / 6.0
    }

    /** min(P_party / (100 x (10,500 - 20 x S(R))), 0.55). */
    fun purpleChance(partyPoints: Int, raidLevel: Int): Double =
        minOf(partyPoints / (100.0 * (10_500 - 20 * purpleRaidLevel(raidLevel))), MAX_PURPLE_CHANCE)

    /** T(R): the raid level the pet chance uses. */
    fun petRaidLevel(raidLevel: Int): Double = when {
        raidLevel <= 400 -> raidLevel.toDouble()
        raidLevel <= 550 -> 400 + (raidLevel - 400) / 3.0
        else -> 450.0
    }

    /** P_individual / (100 x (350,000 - 700 x T(R))) - its own roll, independent of the purple. */
    fun petChance(points: Int, raidLevel: Int): Double = points / (100.0 * (350_000 - 700 * petRaidLevel(raidLevel)))

    /** min(P / 200,000, 0.25) (no elite Combat Achievements here: owner decision C). */
    fun eliteClueChance(points: Int): Double = minOf(points / 200_000.0, 0.25)

    /** Common quantity: P / divisor; from raid level 300 x (1.15 + 0.01 x (R - 300) / 5). ONZEKER: the module's extra x0.75 in Entry
     *  and its double floor are not in the published formula (20-punten-formules.tsv) - this is the published formula, floored once. */
    fun commonAmount(common: Common, points: Int, raidLevel: Int): Int {
        val divisor = common.divisor ?: return 1
        val base = points.toDouble() / divisor
        val scaled = if (raidLevel >= 300) base * (1.15 + 0.01 * (raidLevel - 300) / 5) else base
        return maxOf(1, scaled.toInt())
    }

    /** Thread of Elidinis: before the first one 1/10 at 0 completions rising linearly to 3/10 at 15; after that 1/50. */
    fun threadChance(completions: Int, hadThread: Boolean): Double =
        if (hadThread) 1.0 / 50 else (1.0 + 2.0 * minOf(completions, 15) / 15) / 10

    /** Any jewel: 4/50 rising linearly to 12/50 at 75 completions while one is missing; 4/50 once all four are owned. */
    fun jewelChance(completions: Int, ownedAll: Boolean): Double =
        if (ownedAll) 4.0 / 50 else (4.0 + 8.0 * minOf(completions, 75) / 75) / 50

    class Result(val purpleWinner: PlayerUID?, val drops: Map<PlayerUID, List<ToaLootDrop>>)

    /**
     * The whole chest of one completed raid. [players] = everybody whose points count (leavers too: their points stay in the party sum,
     * but only present players receive - the caller passes [present]). One purple at most; its winner gets no commons.
     */
    fun roll(players: List<ToaLootPlayer>, present: Set<PlayerUID>, raidLevel: Int, random: Random): Result {
        val partyPoints = players.sumOf { it.rewardPoints }
        val drops = linkedMapOf<PlayerUID, MutableList<ToaLootDrop>>()
        var winner: PlayerUID? = null
        if (partyPoints > 0 && random.nextDouble() < purpleChance(partyPoints, raidLevel)) {
            // The recipient in proportion to individual points (a leaver can win, then nobody receives it).
            var pick = random.nextInt(partyPoints)
            val chosen = players.first { p -> (pick < p.rewardPoints).also { if (!it) pick -= p.rewardPoints } }
            val unique = weighted(random)
            // Below its unlock level a unique needs an extra 1/50, otherwise no unique. ONZEKER: the wiki's "untradeable tertiary"
            // fallback is not specified (20-beloningen.txt) - nothing replaces it, and the player then rolls commons as usual.
            if (raidLevel >= unique.unlockRaidLevel || random.nextInt(50) == 0) {
                winner = chosen.uid
                if (chosen.uid in present) drops.getOrPut(chosen.uid) { mutableListOf() } += ToaLootDrop(unique.osrsItem, 1)
            }
        }
        players.filter { it.uid in present }.forEach { p ->
            val list = drops.getOrPut(p.uid) { mutableListOf() }
            if (p.uid != winner) list += commons(p.rewardPoints, raidLevel, random)
            if (random.nextDouble() < petChance(p.rewardPoints, raidLevel)) list += ToaLootDrop(TUMEKENS_GUARDIAN, 1)
            if (random.nextDouble() < threadChance(p.completions, p.hadThread)) list += ToaLootDrop(THREAD_OF_ELIDINIS, 1)
            val missing = JEWELS.filter { it !in p.ownedJewels }
            if (random.nextDouble() < jewelChance(p.completions, missing.isEmpty())) {
                list += ToaLootDrop((if (missing.isEmpty()) JEWELS else missing)[random.nextInt(if (missing.isEmpty()) JEWELS.size else missing.size)], 1)
            }
            if (random.nextDouble() < eliteClueChance(p.rewardPoints)) list += ToaLootDrop(CLUE_SCROLL_ELITE, 1)
        }
        return Result(winner, drops)
    }

    /** Three independent rolls over the 27 commons (1/27 each), stacked; Fossilised dung only below 1,500 points. */
    fun commons(points: Int, raidLevel: Int, random: Random): List<ToaLootDrop> {
        if (points < DUNG_THRESHOLD) return listOf(ToaLootDrop(FOSSILISED_DUNG, 1, noted = true))
        val all = Common.values()
        return List(3) { all[random.nextInt(all.size)] }.groupingBy { it }.eachCount().map { (common, times) ->
            ToaLootDrop(common.osrsItem, commonAmount(common, points, raidLevel) * times, common.noted)
        }
    }

    private fun weighted(random: Random): Unique {
        var pick = random.nextInt(Unique.values().sumOf { it.weight })
        return Unique.values().first { u -> (pick < u.weight).also { if (!it) pick -= u.weight } }
    }
}
