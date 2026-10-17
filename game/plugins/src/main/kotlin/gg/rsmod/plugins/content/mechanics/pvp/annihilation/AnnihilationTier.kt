package gg.rsmod.plugins.content.mechanics.pvp.annihilation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.message

/**
 * N92 + N100 (owner 2026-10-17): the Deadman "Annihilation" tier of a player - ONE table read by every experience gain
 * (engine hook [gg.rsmod.game.model.skill.XpRates], installed by `annihilation_tier.plugin.kts`), every monster-kill drop roll
 * (DropTableFactory) and the Drop Viewer, and later the Character Summary / drop viewer (N108/N109) and protection (N23).
 *
 * Source: OSRS Wiki "Deadman: Annihilation" (fetched 2026-10-17), owner-approved VRAGEN 22 = A:
 *   combat bracket | combat XP | skilling XP | drop rate
 *   3-60           | 10x       | 10x         | 3x
 *   61-80          | 15x       | 10x         | 3x
 *   81-95          | 15x       | 10x         | 4x
 *   96-126 (138)   | 20x       | 10x         | 5x
 * "Prayer is counted as a combat skill for the purpose of these multipliers"; "ALL Combat skills (including Hitpoints)";
 * Summoning (not in OSRS) gains the skilling rate, always 10x (owner 2026-10-18, VRAGEN 33 = B);
 * "it is not possible to move back down a combat bracket". The pattern - one multiplier function every gain reads - is the
 * Void donor's (Donors/void/game/src/main/kotlin/content/entity/player/stat/BonusExperience.kt:67); no donor has the table.
 *
 * NOT the attack range: who may attack whom stays the +/-14 combat levels ("bracket" in AreaState / DeadmanHud). The two
 * are never mixed up, hence the separate name.
 */
enum class AnnihilationTier(
    val levels: IntRange,
    val combatXp: Double,
    val skillingXp: Double,
    val dropRate: Double,
) {
    TIER_3_60(3..60, 10.0, 10.0, 3.0),
    TIER_61_80(61..80, 15.0, 10.0, 3.0),
    TIER_81_95(81..95, 15.0, 10.0, 4.0),
    TIER_96_PLUS(96..Int.MAX_VALUE, 20.0, 10.0, 5.0),
    ;

    /** "3-60" ... "96+" (Character Summary / drop viewer text). */
    val label: String get() = if (levels.last == Int.MAX_VALUE) "${levels.first}+" else "${levels.first}-${levels.last}"

    companion object {
        /** The highest tier the account ever reached (ordinal): the rate never goes down, the red line comes once per rise. */
        val REACHED = AttributeKey<Int>(persistenceKey = "annihilation_tier_reached")

        fun forCombatLevel(level: Int): AnnihilationTier = values().lastOrNull { level >= it.levels.first } ?: TIER_3_60

        /** The player's tier: by combat level, never below the highest tier the account already reached. */
        fun of(player: Player): AnnihilationTier {
            val byLevel = forCombatLevel(player.combatLevel)
            val reached = player.attr[REACHED]?.let { values().getOrNull(it) } ?: return byLevel
            return if (reached.ordinal > byLevel.ordinal) reached else byLevel
        }

        /**
         * Whether [skill] gains the combat rate: the combat skills incl. Prayer and Hitpoints. Summoning counts for the combat level
         * but gains the SKILLING rate, always 10x (owner 2026-10-18, VRAGEN 33 = B; OSRS has no Summoning).
         */
        fun isCombatSkill(skill: Int): Boolean = skill != Skills.SUMMONING && Skills.isCombat(skill)

        /** The factor [player] gains [skill] experience at (the one rate; Player.addXp reads it through XpRates). */
        fun xpRate(
            player: Player,
            skill: Int,
        ): Double {
            val tier = of(player)
            val rate = if (isCombatSkill(skill)) tier.combatXp else tier.skillingXp
            val cap = AnnihilationConfig.INSTANCE_XP_CAP ?: return rate
            if (skill == Skills.SLAYER) return rate
            val instanced = runCatching { player.world.instanceAllocator.getMap(player.tile) != null }.getOrDefault(false)
            return if (instanced) minOf(rate, cap) else rate
        }

        /** The chance factor of every non-guaranteed line of a monster-kill drop roll (N100). */
        fun dropRate(player: Player): Double = of(player).dropRate

        /**
         * Login: records the tier the account is in without a message (an existing account had no rate change; a relog
         * never repeats the line). Call after the combat level is known.
         */
        fun recordAtLogin(player: Player) {
            val now = forCombatLevel(player.combatLevel).ordinal
            val reached = player.attr[REACHED]
            if (reached == null || now > reached) player.attr[REACHED] = now
        }

        /**
         * After a combat level-up: when the tier rose past the highest one reached, record it and send the red line once.
         * True when the line was sent.
         */
        fun announceIfRaised(player: Player): Boolean {
            val now = forCombatLevel(player.combatLevel)
            val reached = player.attr[REACHED]
            if (reached == null) {
                player.attr[REACHED] = now.ordinal
                return false
            }
            if (now.ordinal <= reached) return false
            player.attr[REACHED] = now.ordinal
            player.message(AnnihilationConfig.rateChangeMessage(now))
            return true
        }

        fun format(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}

/**
 * The Annihilation values the owner has not approved yet (DMM-PAKKET 0: "never block the build on a number"), in ONE place.
 * PROVISIONAL until N110 batch f is answered (Summoning = skilling rate is decided: VRAGEN 33 = B, [AnnihilationTier.isCombatSkill]).
 */
object AnnihilationConfig {
    /** N110 f (open): OSRS caps every skill but Slayer at 3x in instanced areas; null = no cap until the owner says yes. */
    val INSTANCE_XP_CAP: Double? = null

    /** N110 f (open): the red line text (owner 10-17: red, once per change, e.g. "Your combat XP rate is now 15x."). */
    fun rateChangeMessage(tier: AnnihilationTier): String =
        "<col=ef1020>Your XP rate is now ${AnnihilationTier.format(tier.combatXp)}x combat / " +
            "${AnnihilationTier.format(tier.skillingXp)}x skilling and your drop rate ${AnnihilationTier.format(tier.dropRate)}x.</col>"
}
