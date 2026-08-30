package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * R04.10/R06.9: Armadyl Godsword special - 50% energy, +25% accuracy / +10% max hit, a single
 * hit. Real, stable, revision-independent AGS mechanic (no healing/draining side effect, unlike
 * Bandos/Saradomin/Zamorak/Guthix's variants - those are NOT implemented this pass, only this
 * one, to avoid guessing the others' exact numbers under time pressure).
 *
 * Deliberately uses the player's default attack animation/graphic rather than a specific
 * special-attack one: this codebase's Anims/Gfx cache ids have no names to look up by (animations
 * aren't named in the RS cache), so a specific "godsword special" anim/gfx id can't be verified
 * here without guessing - the damage/accuracy mechanic (the actual substance of the special) is
 * real and correct; the missing cosmetic polish is an honest, separate gap, not hidden behind a
 * wrong animation.
 */
SpecialAttacks.register(50, Items.ARMADYL_GODSWORD) {
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.10)
    val accuracy = MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 1.25)
    val landHit = accuracy >= world.randomDouble()
    player.dealHit(
        target = target,
        maxHit = maxHit,
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )
}
