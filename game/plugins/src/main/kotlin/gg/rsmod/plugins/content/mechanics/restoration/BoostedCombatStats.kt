package gg.rsmod.plugins.content.mechanics.restoration

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.items.potion.DivinePotions
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses

/** 2011 Knowledge Base: Berserker prolongs boosted combat stats by 15%, not HP or drained stats. */
object BoostedCombatStats {
    val TIMER = TimerKey()
    private val ELAPSED = AttributeKey<Int>()
    val skills = setOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)

    private fun interval(player: Player): Int = if (AncientCurses.isCurseActive(player, AncientCurse.BERSERKER)) 115 else 100

    /** Ticks until the next boosted combat stat drops one level (Boosts Information overlay); at least 1. */
    fun ticksUntilDecay(player: Player): Int = maxOf(1, interval(player) - (player.attr[ELAPSED] ?: 0))

    fun tick(player: Player) {
        val elapsed = (player.attr[ELAPSED] ?: 0) + 1
        val interval = interval(player)
        if (elapsed < interval) {
            player.attr[ELAPSED] = elapsed
            return
        }
        player.attr[ELAPSED] = 0
        for (skill in skills) {
            if (player.skills.getCurrentLevel(skill) > player.skills.getMaxLevel(skill) && !DivinePotions.protects(player, skill)) {
                player.skills.alterCurrentLevel(skill, -1)
            }
        }
        // A decayed Leech level leaves the curse tab's stat adjustment too (owner 2026-10-11).
        AncientCurses.refreshStatAdjustments(player)
    }
}
