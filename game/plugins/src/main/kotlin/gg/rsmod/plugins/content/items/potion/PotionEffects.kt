package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.attr.OVERLOAD_REFRESHES_ATTR
import gg.rsmod.game.model.attr.PRAYER_RENEWAL_TICKS_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.OVERLOAD_TIMER
import gg.rsmod.game.model.timer.PRAYER_RENEWAL_TIMER
import gg.rsmod.game.model.timer.RECOVER_SPECIAL_TIMER
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.content.inter.attack.AttackTab
import kotlin.math.floor
import kotlin.math.min

/**
 * RCV-010 A3: timed and gated potion effects of revision 667.
 *
 * Primary source: Novite 667 `Pots.java` + `Player.processEntity` (ids, formulas, messages, timings).
 * Novite and Void store lifepoints/prayer on the x10 unit; values here are converted to this server's
 * 1:1 unit and the conversion is named at each constant.
 */
object PotionEffects {
    const val ANTIPOISON_IMMUNITY_TICKS = 150
    const val SUPER_ANTIPOISON_IMMUNITY_TICKS = 600
    const val ANTIPOISON_PLUS_IMMUNITY_TICKS = 900
    const val ANTIPOISON_PLUS_PLUS_IMMUNITY_TICKS = 1200

    /**
     * OSRS-IMPORT potions-venom. OSRS keeps poison and venom immunity in one negative poison counter ticking every 30 game ticks; RuneLite
     * `TimersAndBuffsPlugin` (2026-09-14): `VENOM_VALUE_CUTOFF = -38; // Antivenom < -38 <= Antipoison < 0`, poison immunity
     * |(v + 1) x 30| and venom immunity |(v + 1 - (-38)) x 30| ticks, each plus the part of the running 30-tick cycle. The counter values
     * are DERIVED from the wiki durations and match both durations of every potion: antidote++ -40 (12 minutes; venom "18-36 seconds"),
     * anti-venom -41 (12 minutes / Poison table 12.3; venom "36-54 seconds"), anti-venom+ -50 (15 minutes; venom "approximately 3.6
     * minutes"), extended anti-venom+ -59 (17.7 minutes; venom "approximately 6.3 minutes"). This engine keeps two timers without the
     * shared cycle, so each uses the upper bound (|v| x 30 poison, (|v| - 38) x 30 venom) - the convention of the existing 1200-tick
     * antidote++ value. SOURCE_CONFLICT (recorded): the "Venom" page says anti-venom+ gives "three minutes"; the item page and the counter
     * give 3.6.
     */
    const val ANTIDOTE_PLUS_PLUS_VENOM_IMMUNITY_TICKS = (40 - 38) * 30
    const val ANTI_VENOM_POISON_IMMUNITY_TICKS = 41 * 30
    const val ANTI_VENOM_VENOM_IMMUNITY_TICKS = (41 - 38) * 30
    const val ANTI_VENOM_PLUS_POISON_IMMUNITY_TICKS = 50 * 30
    const val ANTI_VENOM_PLUS_VENOM_IMMUNITY_TICKS = (50 - 38) * 30
    const val EXTENDED_ANTI_VENOM_PLUS_POISON_IMMUNITY_TICKS = 59 * 30
    const val EXTENDED_ANTI_VENOM_PLUS_VENOM_IMMUNITY_TICKS = (59 - 38) * 30

    const val OVERLOAD_REFRESH_TICKS = 25
    const val OVERLOAD_REFRESHES = 20
    /** "You need more than 500 life points" - 500 on the x10 unit. */
    const val OVERLOAD_MIN_LIFEPOINTS = 50
    const val OVERLOAD_SELF_HITS = 5
    /** Novite `applyHit(new Hit(player, 100, ...))` on the x10 unit. */
    const val OVERLOAD_SELF_HIT_DAMAGE = 10
    /** Novite `resetOverLoadEffect`: `player.heal(500)` on the x10 unit. */
    const val OVERLOAD_END_HEAL = 50
    const val OVERLOAD_ANIMATION = 3170
    const val OVERLOAD_GRAPHIC = 560
    val OVERLOAD_SKILLS = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.MAGIC, Skills.RANGED)

    /** Novite `setPrayerRenewalDelay(501)`. */
    const val PRAYER_RENEWAL_TICKS = 501
    /** Novite restores 1 point per tick on its x10 prayer unit = 1 real point per 10 ticks. */
    const val PRAYER_RENEWAL_TICKS_PER_POINT = 10
    const val PRAYER_RENEWAL_GRAPHIC = 1295

    /** Novite: 30 000 ms re-use delay = 50 ticks; restores 25% special energy. */
    const val RECOVER_SPECIAL_COOLDOWN_TICKS = 50
    const val RECOVER_SPECIAL_ENERGY = 25

    const val OVERLOAD_END_MESSAGE = "<col=480000>The effects of overload have worn off and you feel normal again."
    const val RENEWAL_WARNING_MESSAGE = "<col=0000FF>Your prayer renewal will wear off in 30 seconds."
    const val RENEWAL_END_MESSAGE = "<col=0000FF>Your prayer renewal has ended."

    fun inWilderness(p: Player): Boolean = p.tile.getWildernessLevel() > 0

    fun notInWilderness(p: Player): Boolean {
        if (inWilderness(p)) {
            p.message("You cannot drink this potion here.")
            return false
        }
        return true
    }

    /** Void `levels.boost`: raise up to base + boost, never beyond. */
    fun boostCapped(p: Player, skill: Int, boost: Int) = p.skills.alterCurrentLevel(skill, boost, boost)

    /** Novite `getAffectedSkill`: min(current, base) + boost - a weaker boost replaces a stronger one. */
    fun setFromBase(p: Player, skill: Int, boost: Int) {
        val base = p.skills.getMaxLevel(skill)
        p.skills.setCurrentLevel(skill, min(p.skills.getCurrentLevel(skill), base) + boost)
    }

    /** Novite `EXTREME_*_POTION` and the non-wilderness `applyOverLoadEffect`. */
    fun extremeBoost(skill: Int, base: Int): Int =
        when (skill) {
            Skills.MAGIC -> 7
            Skills.RANGED -> 4 + floor(base / 5.2).toInt()
            else -> 5 + floor(base * 0.22).toInt()
        }

    /** Novite wilderness branch of `applyOverLoadEffect`. */
    fun wildernessOverloadBoost(skill: Int, base: Int): Int =
        when (skill) {
            Skills.MAGIC -> 5
            Skills.RANGED -> 5 + floor(base * 0.10).toInt()
            else -> 5 + floor(base * 0.15).toInt()
        }

    fun applyExtreme(p: Player, skill: Int) = setFromBase(p, skill, extremeBoost(skill, p.skills.getMaxLevel(skill)))

    /** Void `PotionEffects` zamorak brew: (health / 100) * 10 + 20 on x10 = health / 10 + 2 real. */
    fun zamorakBrewDamage(p: Player): Int = p.getCurrentLifepoints() / 10 + 2

    fun canDrinkOverload(p: Player): Boolean {
        if (!notInWilderness(p)) return false
        if (p.timers.has(OVERLOAD_TIMER)) {
            p.message("You may only use this potion every five minutes.")
            return false
        }
        if (p.getCurrentLifepoints() <= OVERLOAD_MIN_LIFEPOINTS) {
            p.message("You need more than 500 life points to survive the power of overload.")
            return false
        }
        return true
    }

    fun applyOverloadBoost(p: Player) {
        val wild = inWilderness(p)
        OVERLOAD_SKILLS.forEach { skill ->
            val base = p.skills.getMaxLevel(skill)
            setFromBase(p, skill, if (wild) wildernessOverloadBoost(skill, base) else extremeBoost(skill, base))
        }
    }

    fun startOverload(p: Player) {
        p.attr[OVERLOAD_REFRESHES_ATTR] = OVERLOAD_REFRESHES
        p.timers[OVERLOAD_TIMER] = OVERLOAD_REFRESH_TICKS
        applyOverloadBoost(p)
        // Novite: a 2-tick WorldTask that animates, shows the shock graphic and hits five times.
        p.queue {
            repeat(OVERLOAD_SELF_HITS) { index ->
                p.animate(OVERLOAD_ANIMATION)
                p.graphic(OVERLOAD_GRAPHIC)
                p.hit(OVERLOAD_SELF_HIT_DAMAGE)
                if (index < OVERLOAD_SELF_HITS - 1) wait(2)
            }
        }
    }

    fun tickOverload(p: Player) {
        val left = (p.attr[OVERLOAD_REFRESHES_ATTR] ?: 0) - 1
        if (left <= 0) {
            endOverload(p, died = false)
            return
        }
        p.attr[OVERLOAD_REFRESHES_ATTR] = left
        applyOverloadBoost(p)
        p.timers[OVERLOAD_TIMER] = OVERLOAD_REFRESH_TICKS
    }

    fun endOverload(p: Player, died: Boolean) {
        p.attr.remove(OVERLOAD_REFRESHES_ATTR)
        p.timers.remove(OVERLOAD_TIMER)
        if (!died) {
            OVERLOAD_SKILLS.forEach { skill ->
                val base = p.skills.getMaxLevel(skill)
                if (p.skills.getCurrentLevel(skill) > base) p.skills.setCurrentLevel(skill, base)
            }
            p.heal(OVERLOAD_END_HEAL)
        }
        p.message(OVERLOAD_END_MESSAGE)
    }

    fun startPrayerRenewal(p: Player) {
        p.attr[PRAYER_RENEWAL_TICKS_ATTR] = PRAYER_RENEWAL_TICKS
        p.timers[PRAYER_RENEWAL_TIMER] = 1
    }

    fun tickPrayerRenewal(p: Player) {
        val left = p.attr[PRAYER_RENEWAL_TICKS_ATTR] ?: 0
        if (left <= 1) {
            endPrayerRenewal(p)
            return
        }
        if (left == 50) p.message(RENEWAL_WARNING_MESSAGE)
        if (p.getCurrentPrayerPoints() < p.getMaximumPrayerPoints()) {
            if (left % PRAYER_RENEWAL_TICKS_PER_POINT == 0) p.restorePrayer(1)
            if ((left - 1) % 25 == 0) p.graphic(PRAYER_RENEWAL_GRAPHIC)
        }
        p.attr[PRAYER_RENEWAL_TICKS_ATTR] = left - 1
        p.timers[PRAYER_RENEWAL_TIMER] = 1
    }

    fun endPrayerRenewal(p: Player) {
        p.attr.remove(PRAYER_RENEWAL_TICKS_ATTR)
        p.timers.remove(PRAYER_RENEWAL_TIMER)
        p.message(RENEWAL_END_MESSAGE)
    }

    fun canDrinkRecoverSpecial(p: Player): Boolean {
        if (!notInWilderness(p)) return false
        if (p.timers.has(RECOVER_SPECIAL_TIMER)) {
            p.message("You may only use this pot every 30 seconds.")
            return false
        }
        return true
    }

    fun recoverSpecial(p: Player) {
        p.timers[RECOVER_SPECIAL_TIMER] = RECOVER_SPECIAL_COOLDOWN_TICKS
        AttackTab.setEnergy(p, min(100, AttackTab.getEnergy(p) + RECOVER_SPECIAL_ENERGY))
    }

    /** Novite: `overloadDelay == 1 || isDead()` / `prayerRenewalDelay == 1 || isDead()`. */
    fun onDeath(p: Player) {
        if (p.attr.has(OVERLOAD_REFRESHES_ATTR)) endOverload(p, died = true)
        if (p.attr.has(PRAYER_RENEWAL_TICKS_ATTR)) endPrayerRenewal(p)
    }
}
