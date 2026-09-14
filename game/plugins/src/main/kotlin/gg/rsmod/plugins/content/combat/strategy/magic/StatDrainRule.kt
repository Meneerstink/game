package gg.rsmod.plugins.content.combat.strategy.magic

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills

/**
 * One rule for the stat-draining curse spells (Confuse, Weaken, Curse, Vulnerability, Enfeeble, Stun), used by player casts
 * (MagicCombatStrategy) and by npc attack conditions (`not_confused` ... , Void `Spell.canDrain`).
 *
 * OSRS Wiki Confuse / Weaken / Curse (raw wikitext 2026-09-14): "Reduces your opponent's attack by 5%" and "The spell can only be cast
 * if an opponent's stats haven't already been lowered, meaning you can not stack stat-reduction with multiple spells in a row."
 * The drain is the floored percentage of the base level; a spell may drain only while the current level is above base - drain.
 * SOURCE_GAP: Void's threshold uses the unfloored percentage (a 99 would allow a second 5% drain at -4); the floored rule is kept
 * because the wiki forbids stacking.
 */
object StatDrainRule {
    fun drainAmount(
        base: Int,
        percent: Int,
        boost: Double = 1.0,
    ): Int = (base * percent * boost / 100.0).toInt()

    fun canDrain(
        base: Int,
        current: Int,
        percent: Int,
        boost: Double = 1.0,
    ): Boolean = current > base - drainAmount(base, percent, boost)

    /** Whether [spell]'s stat drain may still lower [target] (false for spells without a stat drain). */
    fun canDrain(
        target: Pawn,
        spell: CombatSpell,
    ): Boolean {
        val effect = spell.effect as? SpellEffect.StatDrain ?: return false
        return when (target) {
            is Player -> canDrain(target.skills.getMaxLevel(effect.skill), target.skills.getCurrentLevel(effect.skill), effect.percent)
            is Npc -> {
                val index = npcSkill(effect.skill) ?: return false
                canDrain(target.stats.getMaxLevel(index), target.stats.getCurrentLevel(index), effect.percent)
            }
            else -> false
        }
    }

    fun npcSkill(skill: Int): Int? =
        when (skill) {
            Skills.ATTACK -> NpcSkills.ATTACK
            Skills.STRENGTH -> NpcSkills.STRENGTH
            Skills.DEFENCE -> NpcSkills.DEFENCE
            else -> null
        }
}
