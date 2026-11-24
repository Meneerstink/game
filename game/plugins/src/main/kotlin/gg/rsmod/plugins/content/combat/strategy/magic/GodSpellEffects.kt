package gg.rsmod.plugins.content.combat.strategy.magic

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills

/** OSRS Wiki Claws of Guthix / Flames of Zamorak / Saradomin Strike (Mod Ash, 6 May 2014).
 * God spell drains are distinct from curse spells: +1, no reapplication below base, and NPC Magic is supported.
 * Contact! update, 10 January 2007, explicitly includes boosted Defence/Magic.
 */
object GodSpellEffects {
    fun apply(target: Pawn, effect: SpellEffect.GodDrain) {
        val skill = effect.skill
        if (skill == Skills.PRAYER) {
            if (target is Player) target.skills.setCurrentLevel(skill, maxOf(0, target.skills.getCurrentLevel(skill) - 1))
            return // NPCs do not have a Prayer stat.
        }
        val base: Int
        val current: Int
        val index: Int
        when (target) {
            is Player -> {
                index = skill
                base = target.skills.getMaxLevel(index)
                current = target.skills.getCurrentLevel(index)
            }
            is Npc -> {
                index = when (skill) {
                    Skills.DEFENCE -> NpcSkills.DEFENCE
                    Skills.MAGIC -> NpcSkills.MAGIC
                    else -> return
                }
                base = target.stats.getMaxLevel(index)
                current = target.stats.getCurrentLevel(index)
            }
            else -> return
        }
        if (current < base) return
        val drained = maxOf(0, current - 1 - current * 5 / 100)
        when (target) {
            is Player -> target.skills.setCurrentLevel(index, drained)
            is Npc -> target.stats.setCurrentLevel(index, drained)
        }
    }
}
