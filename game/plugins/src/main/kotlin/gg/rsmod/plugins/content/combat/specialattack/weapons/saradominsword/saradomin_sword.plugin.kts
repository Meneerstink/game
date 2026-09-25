package gg.rsmod.plugins.content.combat.specialattack.weapons.saradominsword

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

val SPECIAL_REQUIREMENT = 100
val MAGIC_DAMAGE_MAX_HIT = 20.0
val MAGIC_DAMAGE_MIN_HIT = 5.0
val SARASWORD_SPEC_SFX_ID = 3853
/**
 * From the wiki in 2011 (see https://runescape.wiki/w/Saradomin_sword?oldid=4832090):
 * Its special attack, Saradomin's Lightning, adds 50-200 extra magic-based melee damage to the standard melee attack
 * and raises the maximum inflicted damage by 10% for the melee hit. Players receive magic experience for using this
 * special, but only for the magical hit. Essentially, the special deals two attacks: the melee attack that the
 * player would normally have hit (with its maximum inflicted damage raised by 10% however) without the special,
 * and an extra magical attack that can deal 50-200(or 90-240 with a ferocious ring) damage. The bonus from a
 * hexcrest or full slayer helm on a slayer task is applied to this magical attack. The special attack drains 100% of
 * the special attack bar.
 */

SpecialAttacks.register(SPECIAL_REQUIREMENT, Items.SARADOMIN_SWORD) {
    // First normal attack
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.10)
    // Audit C-04: Saradomin Lightning rolls against slash defence (osrs-dps-calc), whatever style is selected.
    val accuracy = MeleeCombatFormula.getAccuracyAgainst(player, target, specialAttackMultiplier = 1.0, defenceStyle = gg.rsmod.game.model.combat.StyleType.SLASH)
    val landHit = accuracy >= world.randomDouble()
    val delay = 1

    // Melee and magic experience for both hits come from the shared special attack experience (SpecialAttackXp).
    player.dealHit(
        target = target,
        maxHit = maxHit,
        landHit = landHit,
        delay = delay,
        hitType = HitType.MELEE,
    )

    // Magic special attack
    // One cue on the attacker's sound-effects channel. The radius-10 AreaSound of the same synth also reached the attacker
    // (AreaSound has no source exclusion), so the special was heard twice with ambient sound on (owner 2026-09-18 audit).
    player.playSound(SARASWORD_SPEC_SFX_ID)
    player.animate(Anims.SARADOMIN_SWORD_SPECIAL)
    player.graphic(Gfx.SARADOMIN_SWORD_SPECIAL)
    target.graphic(Gfx.SARADOMIN_SWORD_SPECIAL_TARGET_EFFECT)

    player.dealHit(
        target = target,
        maxHit = MAGIC_DAMAGE_MAX_HIT,
        minHit = MAGIC_DAMAGE_MIN_HIT,
        landHit = true,
        delay = 1,
        hitType = HitType.MAGIC,
    )
}
