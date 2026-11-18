package gg.rsmod.plugins.content.activity.cox

import kotlin.random.Random

/**
 * Ancient chest loot (owner besluit 7a 'beloningen precies OSRS'). Source: OSRS Wiki Ancient chest (raw, work/claude-logs/cox/
 * Ancient_chest.wiki, incl. the 'Summer Sweep Up' change of 12 August 2026): 'For every 8,676 total points obtained, a 1% chance to obtain a
 * unique loot is given ... capped at 65.7% (570,000 points) - any further points will be sent to roll for a second unique loot ... Up to six
 * unique rewards'; 'the player's points will also determine who is more likely to obtain the unique drop'; normal-mode weights /60; 'If no
 * unique drops are rolled or not given to the player, they will make two rolls on the common drop table ... the two rolls cannot end on the
 * same drop'; quantities scale up to 131,071 points (Chambers of Xeric 'Reward system'); herbs: '33% ... drop as seeds instead (at a rate
 * of 7 / 8 herbs per seed)'; ancient tablet 1/10 if not owned; dark journal always if not banked; elite clue 1/12 without a unique;
 * Olmlet 1/53 with one. All ids are OSRS ids - CoxLootItems.local maps them. Pure functions, no world: CoxLootTests.
 */
object CoxLoot {
    const val POINTS_PER_PERCENT = 8676
    const val UNIQUE_CAP = 570_000
    const val COMMON_CAP = 131_071
    const val MAX_UNIQUES = 6

    class Unique(val osrs: Int, val name: String, val weight: Int)

    val UNIQUES = listOf(
        Unique(21034, "Dexterous prayer scroll", 14), Unique(21079, "Arcane prayer scroll", 14),
        Unique(21000, "Twisted buckler", 4), Unique(21012, "Dragon hunter crossbow", 4), Unique(21015, "Dinh's bulwark", 3),
        Unique(21018, "Ancestral hat", 4), Unique(21021, "Ancestral robe top", 4), Unique(21024, "Ancestral robe bottom", 4),
        Unique(13652, "Dragon claws", 3), Unique(21003, "Elder maul", 2), Unique(21043, "Kodai insignia", 2), Unique(20997, "Twisted bow", 2),
    )

    /** [seed]/[perSeed]: a herb that drops as its seed one time in three (7 or 8 herbs per seed). */
    class Common(val osrs: Int, val name: String, val max: Int, val noted: Boolean = false, val seed: Int = -1, val perSeed: Int = 0)

    val COMMONS = listOf(
        Common(560, "Death rune", 3640), Common(565, "Blood rune", 4095), Common(566, "Soul rune", 6553), Common(892, "Rune arrow", 9362),
        Common(11212, "Dragon arrow", 648),
        Common(207, "Grimy ranarr weed", 138, true, 5295, 7), Common(3049, "Grimy toadflax", 210, true, 5296, 8),
        Common(209, "Grimy irit leaf", 675, true, 5297, 8), Common(211, "Grimy avantoe", 337, true, 5298, 8),
        Common(213, "Grimy kwuarm", 289, true, 5299, 8), Common(3051, "Grimy snapdragon", 84, true, 5300, 7),
        Common(215, "Grimy cadantine", 330, true, 5301, 8), Common(2485, "Grimy lantadyme", 442, true, 5302, 8),
        Common(217, "Grimy dwarf weed", 546, true, 5303, 8), Common(219, "Grimy torstol", 134, true, 5304, 7),
        Common(442, "Silver ore", 6553, true), Common(453, "Coal", 6553, true), Common(444, "Gold ore", 2978, true),
        Common(447, "Mithril ore", 4095, true), Common(449, "Adamantite ore", 789, true), Common(451, "Runite ore", 65, true),
        Common(1623, "Uncut sapphire", 697, true), Common(1621, "Uncut emerald", 923, true), Common(1619, "Uncut ruby", 541, true),
        Common(1617, "Uncut diamond", 258, true),
        Common(13391, "Lizardman fang", 4681), Common(7936, "Pure essence", 65535, true), Common(13421, "Saltpetre", 4681, true),
        Common(8780, "Teak plank", 1365, true), Common(8782, "Mahogany plank", 550, true), Common(13573, "Dynamite", 2427, true),
        Common(21047, "Torn prayer scroll", 1), Common(21027, "Dark relic", 1),
    )

    const val ANCIENT_TABLET = 21046
    const val DARK_JOURNAL = 20899
    const val OLMLET = 20851
    const val ELITE_CLUE = 12073

    /** The number of uniques for [teamPoints]: each 570,000 (or the rest) rolls once with points / 8,676 percent, at most six rolls. */
    fun uniqueCount(teamPoints: Int, random: Random): Int {
        var left = teamPoints.coerceAtLeast(0)
        var count = 0
        var rolls = 0
        while (left > 0 && rolls < MAX_UNIQUES) {
            val pts = minOf(UNIQUE_CAP, left)
            left -= pts
            rolls++
            if (random.nextDouble() * 100.0 < pts.toDouble() / POINTS_PER_PERCENT) count++
        }
        return count
    }

    fun pickUnique(random: Random): Unique {
        var roll = random.nextInt(UNIQUES.sumOf { it.weight })
        for (u in UNIQUES) {
            roll -= u.weight
            if (roll < 0) return u
        }
        return UNIQUES.last()
    }

    /** Who receives a unique: weighted by personal points (a raider with 0 points can still get one only if everybody has 0). */
    fun <K> pickReceiver(points: Map<K, Int>, random: Random): K? {
        val total = points.values.sumOf { it.coerceAtLeast(0).toLong() }
        if (points.isEmpty()) return null
        if (total <= 0) return points.keys.toList()[random.nextInt(points.size)]
        var roll = (random.nextDouble() * total).toLong()
        for ((k, v) in points) {
            roll -= v.coerceAtLeast(0)
            if (roll < 0) return k
        }
        return points.keys.last()
    }

    class Drop(val osrs: Int, val amount: Int, val noted: Boolean)

    /** Two different common rolls, amounts = max x min(points, 131,071) / 131,071 (at least 1); a third of the herbs come as seeds. */
    fun commons(personalPoints: Int, random: Random): List<Drop> {
        val share = personalPoints.coerceIn(0, COMMON_CAP).toDouble() / COMMON_CAP
        val first = COMMONS[random.nextInt(COMMONS.size)]
        val rest = COMMONS.filter { it !== first }
        val second = rest[random.nextInt(rest.size)]
        return listOf(first, second).map { c ->
            val amount = maxOf(1, (c.max * share).toInt())
            if (c.seed >= 0 && random.nextInt(3) == 0) Drop(c.seed, maxOf(1, amount / c.perSeed), noted = false)
            else Drop(c.osrs, amount, c.noted)
        }
    }

    /**
     * One raider's chest: [unique] = the unique this raider received (null = commons); the ancient tablet (1/10, if [hasTablet] is false)
     * replaces one common roll (tsps: 'An ancient tablet (1 in 10, if not owned) replaces one roll'); the dark journal when not banked;
     * the elite clue 1/12 without a unique; the Olmlet 1/53 with one.
     */
    fun chest(unique: Unique?, personalPoints: Int, hasTablet: Boolean, hasJournal: Boolean, random: Random): List<Drop> {
        val out = ArrayList<Drop>()
        if (unique != null) {
            out += Drop(unique.osrs, 1, false)
            if (random.nextInt(53) == 0) out += Drop(OLMLET, 1, false)
        } else {
            val rolls = commons(personalPoints, random).toMutableList()
            if (!hasTablet && random.nextInt(10) == 0) rolls[1] = Drop(ANCIENT_TABLET, 1, false)
            out += rolls
            if (random.nextInt(12) == 0) out += Drop(ELITE_CLUE, 1, false)
        }
        if (!hasJournal) out += Drop(DARK_JOURNAL, 1, false)
        return out
    }
}
