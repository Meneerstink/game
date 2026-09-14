package gg.rsmod.plugins.content.mechanics.restoration

import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.STAT_RESTORE
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import kotlin.math.sign

/**
 * @author Alycia <https://github.com/alycii>
 */

on_timer(key = STAT_RESTORE) {
    val tempLevels = Array(SkillSet.DEFAULT_SKILL_COUNT) { player.skills.getCurrentLevel(it) }
    val actualLevels = Array(SkillSet.DEFAULT_SKILL_COUNT) { player.skills.getMaxLevel(it) }

    actualLevels.forEachIndexed { index, actualLevel ->
        val difference = actualLevel - tempLevels[index]
        val boost = sign(difference.toDouble()).toInt()

        if (difference != 0) {
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
                        player.skills.alterCurrentLevel(skill = index, value = boost, capValue = cap)
                    }
                }
            }
        }
    }

    if (player.getMaximumLifepoints() > player.getCurrentLifepoints()) {
        player.alterLifepoints(value = 1, capValue = 0)
    }

    // Berserker curse: boosted/drained combat stats take 15% longer to tick back toward base
    // (real-world ~1min9s per level instead of 1min - sourced from runescape.wiki "Berserker").
    // 100 ticks * 0.6s = 60s baseline; 115 ticks * 0.6s = 69s.
    player.timers[STAT_RESTORE] = if (AncientCurses.isCurseActive(player, AncientCurse.BERSERKER)) 115 else 100
}
