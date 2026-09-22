package gg.rsmod.plugins.content.mechanics.restoration

import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.STAT_RESTORE
import kotlin.math.sign

/**
 * @author Alycia <https://github.com/alycii>
 */

on_login { player.timers[BoostedCombatStats.TIMER] = 1 }

on_timer(BoostedCombatStats.TIMER) {
    BoostedCombatStats.tick(player)
    player.timers[BoostedCombatStats.TIMER] = 1
}

// Restoration rates, including Rapid Restore / Rapid Heal / Rapid Renewal, live in RestorationRates.
on_timer(key = STAT_RESTORE) {
    val lowered = RestorationRates.loweredDue(player)
    val boosted = RestorationRates.boostedDue(player)
    val life = RestorationRates.lifeDue(player)

    val tempLevels = Array(SkillSet.DEFAULT_SKILL_COUNT) { player.skills.getCurrentLevel(it) }
    val actualLevels = Array(SkillSet.DEFAULT_SKILL_COUNT) { player.skills.getMaxLevel(it) }

    actualLevels.forEachIndexed { index, actualLevel ->
        val difference = actualLevel - tempLevels[index]
        val boost = sign(difference.toDouble()).toInt()
        // A lowered stat climbs back at the Rapid Restore rate; a boosted one decays at the normal rate.
        val steps = if (difference > 0) lowered else boosted

        if (difference != 0 && steps > 0 && !(difference < 0 && index in BoostedCombatStats.skills)) {
            val cap = 125 * boost
            when (index) {
                Skills.CONSTITUTION -> {
                    // Do nothing here, since hitpoints is not coupled to constitution level
                }
                Skills.PRAYER -> {
                    // Do nothing, as Prayer does not naturally restore.
                }
                Skills.SUMMONING -> {
                    // Summoning points are the current level of skill 23 and, like Prayer, do not
                    // regenerate. The 2011 Knowledge Base is explicit: summoning a familiar "will
                    // drain your Summoning points, which can only be regained by visiting a
                    // Summoning obelisk or drinking a Summoning potion" (Summoning: The Basics,
                    // archived at 2011.rs). Letting the generic restore loop tick them back up is
                    // what made a familiar look free - the owner's "points do not keep draining".
                }
                else -> {
                    // Divine potions: a boosted level "will not drain below the maximum boost" for five minutes (DivinePotions).
                    if (!(boost < 0 && gg.rsmod.plugins.content.items.potion.DivinePotions.protects(player, index))) {
                        repeat(minOf(steps, kotlin.math.abs(difference))) {
                            player.skills.alterCurrentLevel(skill = index, value = boost, capValue = cap)
                        }
                    }
                }
            }
        }
    }

    repeat(life) {
        if (player.getMaximumLifepoints() > player.getCurrentLifepoints()) {
            player.alterLifepoints(value = 1, capValue = 0)
        }
    }

    player.timers[STAT_RESTORE] = RestorationRates.STEP
}
