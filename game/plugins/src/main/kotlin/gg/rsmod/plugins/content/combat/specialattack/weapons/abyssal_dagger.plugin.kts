package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.AbyssalDagger

/**
 * OSRS-IMPORT Abyssal dagger - Abyssal Puncture (rules and sources in [AbyssalDagger]): one attack roll against the
 * target's slash defence decides both hits; each hit rolls its own damage up to 85% of the max hit. Hit timing follows
 * this server's Dragon dagger special (second hit one tick later against NPCs). The OSRS special animation/graphic are
 * not in 667: the normal dagger attack animation is used (ADAPTED_TO_667).
 */
SpecialAttacks.register(AbyssalDagger.SPECIAL_ENERGY, *AbyssalDagger.IDS) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.ABYSSAL_DAGGER_SPECIAL) // OSRS ABYSSAL_DAGGER_SPECIAL_SPOTANIM (fxpilot)
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = AbyssalDagger.SPECIAL_DAMAGE)
    val landHit =
        MeleeCombatFormula.getAccuracyAgainst(
            player,
            victim,
            specialAttackMultiplier = AbyssalDagger.SPECIAL_ACCURACY,
            defenceStyle = StyleType.SLASH,
        ) >= world.randomDouble()
    for (i in 0 until 2) {
        val delay = if (victim.entityType.isNpc) i + 1 else 1
        player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.MELEE)
    }
}
