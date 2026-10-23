package gg.rsmod.plugins.content.areas.godwars.nex

import gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.Phase

/**
 * C4: Nex's attack schedule and numbers, ported 1:1 from the first donor (Darkan rev 727, pre-EoC, the RS fight the owner
 * chose - not OSRS Nex). Darkan values are x10; ours are 1:1 lifepoints, so every damage is divided by ten.
 *
 * Sources (import-source\donors\darkan\world-server-dev\...\godwars\factions\zaros\):
 * - NexCombat.java:42-76 attack(): the special is a pure function of the phase and the attack counter.
 * - NexCombat.java:78-142 autoAttack(): melee 360, smoke/blood/ice magic 250, Zaros magic 350, shadow 800 - d*800/11 within 10.
 * - Nex.java:73/336 attackCount = 1 at spawn, 0 at setPhase; :368-376 every attack (auto or special) increments it.
 * - Nex.java:31/210-228 setCapDamage(500) in every phase; :305-320 a protection prayer cuts her hits to 60%.
 * - Nex.java:325-327 BLOOD phase heals 15% of her melee/magic hits above 15.
 * - attack\*.java special numbers.
 *
 * The earlier port (Novite random-roll rotation, MAGIC_MAX 36.9, cap 500 outside Zaros) is replaced, see dossier C4.
 */
object NexDarkanRules {
    /** Darkan attack\*.java; [speedFactor] = the multiple of attackSpeed the special returns (x2 = a longer pause). */
    enum class Special(val speedFactor: Int) {
        DRAG(1),
        NO_ESCAPE(1),
        VIRUS(1),
        EMBRACE_DARKNESS(1),
        SHADOW_TRAPS(1),
        SIPHON(1),
        BLOOD_SACRIFICE(2),
        CONTAIN_THIS(2),
        ICE_PRISON(2),
    }

    /** Nex.java:73 - the counter starts at 1 when Nex spawns. */
    const val SPAWN_ATTACK_COUNT = 1

    /** Nex.java:336 - setPhase resets the counter to 0. */
    const val PHASE_ATTACK_COUNT = 0

    /**
     * NexCombat.java:42-76. [dragRoll] = Darkan `Utils.random(4) == 0` (exclusive bound: 1 in 4 smoke rushes is a Drag).
     * Null = the auto attack.
     */
    fun special(
        phase: Phase,
        attackCount: Int,
        dragRoll: Boolean,
    ): Special? =
        when (phase) {
            Phase.SMOKE ->
                when {
                    attackCount % 12 == 0 -> if (dragRoll) Special.DRAG else Special.NO_ESCAPE
                    attackCount % 6 == 0 -> Special.VIRUS
                    else -> null
                }
            Phase.SHADOW ->
                when {
                    attackCount == 0 -> Special.EMBRACE_DARKNESS
                    attackCount % 5 == 0 -> Special.SHADOW_TRAPS
                    else -> null
                }
            Phase.BLOOD ->
                when {
                    attackCount % 8 == 0 -> Special.SIPHON
                    attackCount % 4 == 0 -> Special.BLOOD_SACRIFICE
                    else -> null
                }
            Phase.ICE ->
                when {
                    attackCount % 8 == 0 -> Special.CONTAIN_THIS
                    attackCount % 4 == 0 -> Special.ICE_PRISON
                    else -> null
                }
            Phase.ZAROS -> null
        }

    /** NexCombat.java:89 getMaxHit(nex, 360, MELEE) - every phase. */
    const val MELEE_MAX = 36.0

    /** NexCombat.java:98/114/118/131: smoke, blood and ice magic 250. */
    const val MAGIC_MAX = 25.0

    /** NexCombat.java:131 Zaros magic 350. */
    const val ZAROS_MAGIC_MAX = 35.0

    fun magicMax(phase: Phase): Double = if (phase == Phase.ZAROS) ZAROS_MAGIC_MAX else MAGIC_MAX

    /**
     * NexCombat.java:106-110: shadow arrows only reach players within 10 tiles of Nex (her south-west tile), max
     * 800 - distance * 800 / 11 (integer maths, x10). Null = no attack on that player.
     */
    fun shadowMax(distance: Int): Double? = if (distance > 10) null else (800 - distance * 800 / 11) / 10.0

    /** Nex.java:31/210-228 setCapDamage(500) = 50 per hit, in every phase including Zaros. */
    const val HIT_CAP = 50

    /** Nex.java:305-320 get{Mage,Range,Melee}PrayerMultiplier = 0.6. */
    const val PROTECTION_MULTIPLIER = 0.6

    /** NexCombat.java:99/124 `Utils.getRandomInclusive(5) == 0`: poison (smoke) and freeze (ice) on 1 in 6 hits that deal damage. */
    const val EFFECT_ROLL_BOUND = 5

    /** NexCombat.java:126 freeze Ticks.fromSeconds(18) - 3 s with Protect from Magic (Ticks.fromSeconds = seconds / 0.6). */
    fun iceFreezeTicks(protectingMagic: Boolean): Int = if (protectingMagic) 5 else 30

    /** Nex.java:325-327: BLOOD phase heals 15% of a melee/magic hit above 15 (x10, so above 1.5 here). */
    fun bloodHeal(
        phase: Phase,
        damage: Int,
    ): Int = if (phase == Phase.BLOOD && damage * 10 > 15) (damage * 0.15).toInt() else 0

    /** NexCombat.java:138-139 in the Zaros phase the prayer form switches when the counter is a multiple of 3. */
    fun zarosSwitch(attackCount: Int): Boolean = attackCount % 3 == 0

    /** Nex.java:378-380 shouldStopMeleeing: Nex stops chasing for melee after 20 ticks without an attack. */
    const val STOP_MELEEING_TICKS = 20

    /** Virus.java:47 getRandomInclusive(100) true damage. */
    val VIRUS_DAMAGE = 0..10

    /**
     * EmbraceDarkness.java:50-55: the darkness one player sees = (int) Utils.getDistance(player, nex) capped at 30, * 255 / 30
     * (0 = black beside Nex, 255 = none). Distance from Nex's south-west tile, as Darkan's getX/getY.
     */
    fun darknessLevel(dx: Int, dz: Int): Int = Math.sqrt((dx * dx + dz * dz).toDouble()).toInt().coerceAtMost(30) * 255 / 30

    /** EmbraceDarkness.java:52 Utils.getRandomInclusive(2) == 0: each tick a 1 in 3 chance to refresh every player's darkness. */
    const val DARKNESS_REFRESH_BOUND = 2

    /** Virus.java:48-52 lowerStat(skill, 0.05, 0.0): each of these drops by (int) (current level * 0.05), never below 0. */
    const val VIRUS_DRAIN = 0.05

    /**
     * Virus.java:46 `Utils.getDistance(t, infected) <= 1` (darkan-lib Utils.getDistance = sqrt(dx*dx + dy*dy), as Matrix): the infected
     * player (Nex's target) himself and the players straight beside him - not the diagonal tiles.
     */
    fun virusReaches(infected: gg.rsmod.game.model.Tile, other: gg.rsmod.game.model.Tile): Boolean {
        val dx = other.x - infected.x
        val dz = other.z - infected.z
        return other.height == infected.height && dx * dx + dz * dz <= 1
    }

    /** NoEscape.java:66 getRandomInclusive(650) true damage. */
    val NO_ESCAPE_DAMAGE = 0..65

    /** IcePrison.java:68 Utils.random(600, 800) true damage to a player still on a prison tile after 8 ticks. */
    val ICE_PRISON_DAMAGE = 60..79
    const val ICE_PRISON_DELAY = 8

    /** ContainThis.java:52 Utils.random(200, 350) true damage after 6 ticks, protection blocked 12 ticks. */
    val CONTAIN_DAMAGE = 20..34
    const val CONTAIN_DELAY = 6
    const val PROTECTION_BLOCK_TICKS = 12

    /** BloodSacrifice.java:46: still closer than 5 tiles -> every target takes 10% of its hitpoints as true damage. */
    const val SACRIFICE_SAFE_DISTANCE = 5

    fun sacrificeDamage(hitpoints: Int): Int = (hitpoints * 0.1).toInt()

    /** Drag.java:46: lock 3 ticks, protection blocked 12. */
    const val DRAG_LOCK_TICKS = 3

    /** Siphon.java:46 siphoning ends after 6 ticks. */
    const val SIPHON_TICKS = 6

    /** Nex.java:126-150 sendDeath: wrath 0-600 (x10) to targets within 5 tiles, 5 ticks after the shout. */
    val WRATH_DAMAGE = 0..60
    const val WRATH_RADIUS = 5
    const val WRATH_DELAY = 5
}
