package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
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

    /** Novite `EXTREME_MAGIC_POTION`. The owner removed the other extreme combat potions. */
    fun extremeBoost(skill: Int): Int =
        when (skill) {
            Skills.MAGIC -> 7
            else -> 0
        }

    fun applyExtreme(p: Player, skill: Int) = setFromBase(p, skill, extremeBoost(skill))

    /** Void `PotionEffects` zamorak brew: (health / 100) * 10 + 20 on x10 = health / 10 + 2 real. */
    fun zamorakBrewDamage(p: Player): Int = p.getCurrentLifepoints() / 10 + 2

}
