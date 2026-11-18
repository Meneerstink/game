package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType

/**
 * The raid's monsters: OSRS base stats and the raid's scaling.
 *
 * Source: OSRS Wiki DPS calculator data (weirdgloop/osrs-dps-calc monsters.json = the Wiki infobox values) and its Chambers of Xeric
 * scaling (src/lib/scaling/ChambersOfXeric.ts), as the donor RSPSApp/tsps CoxMonsters.js holds them (code help only; values = Wiki):
 *  - levels follow the party's highest Hitpoints level (55 + 44 x hp/99), hitpoints its highest combat level (60-126);
 *  - party size n: offence x (100 + 7 x isqrt(n-1) + (n-1))%, defence x (100 + isqrt(n-1) + (n-1) x 7/10)%, hitpoints x (1 + n/2);
 *  - scavenger beasts and vespine soldiers scale as solo monsters; Guardians' base hitpoints = 151 + the party's average Mining;
 *  - the Great Olm: head 800 + 400k, claws 600 + 300k, k = min(n-1, 50) - 3 x (min(n, 50) / 8); the mage claw's Magic halved.
 * Challenge Mode is not built (owner besluit 2a).
 *
 * Owner besluit 3a (15-11 14:1x, like ToA 11-11): a run at 78legacy lasts at most 35 minutes -> bosses get [CoxDuration.BOSS_HP_PERCENT]
 * of their scaled hitpoints; points are paid as if the full OSRS hitpoints were dealt ([CoxDuration.pointsFor]) so loot stays OSRS.
 */
object CoxDuration {
    /** Same share as ToA (ToaDuration.BOSS_HP_PERCENT 55); measured runs may move it (dossier COX.txt). */
    const val BOSS_HP_PERCENT = 55

    /** Hitpoints a raid monster really gets. Trash (scavengers, soldiers, icefiends, grubs) keeps the OSRS value. */
    fun hp(scaled: Int, boss: Boolean): Int = if (!boss) scaled else ((scaled * BOSS_HP_PERCENT + 50) / 100).coerceAtLeast(1)

    /**
     * Combat points for [damage] dealt to a monster (tsps 'Damage points: 5 per hitpoint', not on the Wiki - Wiki 'Dealing damage within
     * the Chambers of Xeric will reward the player with points based on damage dealt'). A shortened boss pays 100/55 per hitpoint so a
     * room's points stay what OSRS pays for the whole fight.
     */
    fun pointsFor(damage: Int, boss: Boolean): Int = if (!boss) damage * 5 else damage * 5 * 100 / BOSS_HP_PERCENT
}

/**
 * Base stats in the donor's order: levels att, str, def, rng, mag; [5] melee accuracy, [7] magic accuracy, [9] ranged accuracy;
 * defences [10] stab [11] slash [12] crush [13] magic [14] ranged.
 */
data class CoxMonster(val name: String, val osrsIds: Set<Int>, val hp: Int, val stats: IntArray, val maxHit: Int, val boss: Boolean)

/** The figures the scaling reads (Wiki): raiders at the start, their highest combat/Hitpoints level, average Mining. */
data class CoxScaling(val size: Int, val maxCombat: Int, val maxHitpoints: Int, val averageMining: Int = 1)

data class CoxScaled(val hp: Int, val stats: IntArray, val maxHit: Int)

object CoxMonsters {
    const val ATT = 0
    const val STR = 1
    const val DEF = 2
    const val RNG = 3
    const val MAG = 4

    private fun m(name: String, ids: List<Int>, hp: Int, stats: List<Int>, maxHit: Int, boss: Boolean = true) =
        CoxMonster(name, ids.toSet(), hp, stats.toIntArray(), maxHit, boss)

    val ALL: List<CoxMonster> = listOf(
        m("Abyssal portal", listOf(7533), 250, listOf(1, 1, 176, 1, 176, 0, 0, 0, 0, 0, 0, 0, 0, 60, 140), 0),
        m("Deathly mage", listOf(7560), 120, listOf(1, 1, 155, 1, 210, 0, 0, 120, 0, 0, 0, 0, 0, 0, 0), 22, boss = false),
        m("Deathly ranger", listOf(7559), 120, listOf(1, 1, 155, 210, 155, 0, 0, 0, 0, 120, 0, 0, 0, 0, 0), 70, boss = false),
        m("Glowing crystal", listOf(7568), 120, listOf(1, 1, 100, 1, 100, 0, 0, 0, 0, 0, -5, 180, 180, 0, 0), 0),
        m("Great Olm head", listOf(7551, 7554), 800, listOf(250, 250, 150, 250, 250, 0, 0, 60, 0, 60, 200, 200, 200, 200, 50), 27),
        m("Great Olm left claw", listOf(7552, 7555), 600, listOf(250, 250, 175, 250, 175, 0, 0, 60, 0, 60, 50, 50, 50, 200, 200), 0),
        m("Great Olm right claw", listOf(7550, 7553), 600, listOf(250, 250, 175, 250, 87, 0, 0, 60, 0, 60, 200, 200, 200, 50, 200), 0),
        m("Guardian", listOf(7569, 7570, 7571, 7572), 250, listOf(140, 140, 100, 1, 1, 0, 0, 0, 0, 0, 80, 180, -10, 0, 0), 20),
        m("Ice demon", listOf(7584, 7585), 140, listOf(1, 1, 160, 390, 390, 0, 0, 0, 0, 0, 70, 70, 110, 40, 140), 0),
        m("Lizardman shaman", listOf(7573, 7574), 190, listOf(130, 130, 210, 130, 130, 58, 0, 0, 0, 56, 102, 160, 150, 160, 0), 65),
        m("Muttadile (large)", listOf(7561, 7563), 250, listOf(250, 250, 220, 250, 250, 88, 0, 0, 0, 82, -5, 82, 60, 75, 0), 72),
        m("Muttadile (small)", listOf(7562), 250, listOf(150, 150, 138, 150, 1, 71, 0, 0, 0, 83, -5, 72, 50, 60, 0), 28),
        m("Scavenger beast", listOf(7548, 7549), 30, listOf(120, 120, 45, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 13, boss = false),
        m("Skeletal Mystic", listOf(7604, 7605, 7606), 160, listOf(140, 140, 187, 1, 140, 85, 0, 40, 0, 0, 155, 155, 75, 140, 115), 0),
        m("Tekton", listOf(7540, 7541, 7542, 7545), 300, listOf(390, 390, 205, 1, 205, 64, 0, 0, 0, 0, 155, 165, 105, 0, 0), 52),
        m("Tekton (enraged)", listOf(7543, 7544), 300, listOf(390, 390, 205, 1, 205, 64, 0, 0, 0, 0, 280, 290, 180, 0, 0), 59),
        // Vanguards keep their OSRS hitpoints (not the 55% of besluit 3a): their fight is a RATIO rule (Wiki: more than 40% apart -> all heal
        // to full). At 55% (99 hp solo) one ranged hit already broke the 40% and they healed every ~17 s - unkillable (16-11 measurement,
        // dossier COX.txt). Eigen advies, the owner may overrule.
        m("Vanguard (magic)", listOf(7529), 180, listOf(150, 150, 160, 150, 150, 0, 0, 40, 0, 0, 315, 340, 400, 110, 50), 22, boss = false),
        m("Vanguard (melee)", listOf(7527, 7525, 7526), 180, listOf(150, 150, 160, 150, 150, 20, 0, 0, 0, 0, 150, 150, 150, 20, 400), 18, boss = false),
        m("Vanguard (ranged)", listOf(7528), 180, listOf(150, 150, 160, 150, 150, 0, 0, 0, 0, 40, 55, 60, 100, 400, 300), 22, boss = false),
        m("Vasa Nistirio", listOf(7565, 7566, 7567), 300, listOf(1, 1, 175, 230, 230, 0, 0, 0, 0, 100, 170, 190, 40, 400, 40), 0),
        m("Vespine soldier", listOf(7538, 7539), 100, listOf(150, 150, 40, 1, 40, 0, 0, 0, 0, 0, 0, 0, 0, 30, 0), 14, boss = false),
        m("Vespula", listOf(7530, 7531, 7532), 200, listOf(150, 150, 88, 150, 88, 0, 0, 0, 0, 0, 0, 0, 0, 70, 60), 14),
    )
    private val BY_ID: Map<Int, CoxMonster> = ALL.flatMap { m -> m.osrsIds.map { it to m } }.toMap()

    private val TEKTON = setOf(7540, 7541, 7542, 7543, 7544, 7545)
    private val GUARDIAN = setOf(7569, 7570, 7571, 7572)
    private val OLM_HEAD = setOf(7551, 7554)
    private val OLM_CLAWS = setOf(7550, 7552, 7553, 7555)
    private val OLM_MAGE_CLAW = setOf(7550, 7553)
    private val SINGLES = setOf(7548, 7549, 7538, 7539)
    /** Their Magic level is defensive (scaled as Defence). */
    private val MAGIC_DEFENSIVE = setOf(7559, 7540, 7541, 7542, 7543, 7544, 7545, 7533, 7530, 7531, 7532, 7538, 7539, 7550, 7552, 7553, 7555)

    fun of(osrs: Int): CoxMonster? = BY_ID[osrs]

    private fun isqrt(x: Int) = kotlin.math.sqrt(x.toDouble()).toInt()

    fun scale(osrs: Int, s: CoxScaling): CoxScaled? {
        val base = BY_ID[osrs] ?: return null
        val offensives = mutableListOf(ATT, STR, RNG)
        val defensives = mutableListOf(DEF)
        (if (osrs in MAGIC_DEFENSIVE) defensives else offensives) += MAG
        offensives.removeAll { base.stats[it] == 1 }
        defensives.removeAll { base.stats[it] == 1 }
        val baseOff = (offensives.maxOfOrNull { base.stats[it] } ?: 1).coerceAtLeast(1)
        val baseDef = (defensives.maxOfOrNull { base.stats[it] } ?: 1).coerceAtLeast(1)
        val baseHp = if (osrs in GUARDIAN) 151 + s.averageMining else base.hp
        val size = s.size.coerceIn(1, 100)
        var off: Int
        var def: Int
        var hp: Int
        if (osrs in SINGLES) {
            val hpScaler = s.maxCombat.coerceIn(60, 126)
            val statScaler = s.maxHitpoints.coerceIn(55, 99)
            hp = maxOf(baseHp * hpScaler / 126, 5)
            off = maxOf(baseOff * statScaler / 99, 1)
            def = maxOf(baseDef * statScaler / 99, 1)
        } else {
            val others = size - 1
            val highestCombat = s.maxCombat.coerceIn(60, 126)
            val highestHp = (55 + 44 * s.maxHitpoints / 99).coerceIn(55, 99)
            off = baseOff * highestHp / 99
            def = baseDef * highestHp / 99
            hp = baseHp * highestCombat / 126
            off = off * (100 + isqrt(others) * 7 + others) / 100
            def = def * (100 + isqrt(others) + others * 7 / 10) / 100
            hp += hp * (size * 50 / 100)
            hp = hp.coerceIn(50, 30000)
            off = off.coerceIn(50, 5000)
            def = def.coerceIn(50, 20000)
        }
        val stats = base.stats.copyOf()
        offensives.forEach { stats[it] = off }
        defensives.forEach { stats[it] = def }
        if (osrs in OLM_HEAD || osrs in OLM_CLAWS) {
            val k = minOf(size - 1, 50) - 3 * (minOf(size, 50) / 8)
            hp = if (osrs in OLM_HEAD) 800 + 400 * k else 600 + 300 * k
            if (osrs in OLM_MAGE_CLAW) stats[MAG] = stats[MAG] / 2
        }
        val maxHit = if (base.maxHit > 0) base.maxHit * (off + 9) / (baseOff + 9) else 0
        return CoxScaled(CoxDuration.hp(hp, base.boss), stats, maxHit)
    }

    /** A max hit given at base scale (Wiki, or 'varies' attacks), grown with the npc's scaled level as a max hit follows it (tsps). */
    fun maxHitFor(osrs: Int, scaled: CoxScaled, base: Int, statIndex: Int): Int {
        val entry = BY_ID[osrs] ?: return base
        return maxOf(1, base * (scaled.stats[statIndex] + 9) / (entry.stats[statIndex] + 9))
    }

    /** The engine definition: levels in rsmod order (att, str, def, magic, ranged) and the 18 bonus slots as ToaNpcStats fills them. */
    fun definition(scaled: CoxScaled, speed: Int, deathSeq: Int, meleeStyle: StyleType = StyleType.CRUSH): NpcCombatDef {
        val s = scaled.stats
        val bonuses = MutableList(18) { 0 }
        for (i in 0..2) bonuses[i] = s[5]
        bonuses[3] = s[7]; bonuses[4] = s[9]
        bonuses[5] = s[10]; bonuses[6] = s[11]; bonuses[7] = s[12]; bonuses[8] = s[13]; bonuses[9] = s[14]
        bonuses[10] = s[5]
        return NpcCombatDef.DEFAULT.copy(
            lifepoints = scaled.hp,
            stats = listOf(s[ATT], s[STR], s[DEF], s[MAG], s[RNG]),
            // Poison/venom immunity: the engine default (ONZEKER: per-monster Wiki infobox 'immune to poison' not read yet).
            attackStyleType = meleeStyle, attackSpeed = speed, bonuses = bonuses, respawnDelay = -1,
            deathAnimation = if (deathSeq >= 0) listOf(deathSeq) else NpcCombatDef.DEFAULT.deathAnimation,
            waitFullDeathAnimation = deathSeq >= 0,
            completesEncounter = false,
        )
    }
}
