package gg.rsmod.plugins.content.combat.specialattack.weapons.dragonequipment

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.BurningClaws
import gg.rsmod.plugins.content.items.osrs.Burns
import gg.rsmod.plugins.content.items.osrs.DragonClaws

/**
 * Dragon claws "Slice and Dice" (50 %) and Burning claws "Burning barrage" (35 %), both exactly as the OSRS Wiki item pages and the
 * wiki DPS calculator (`dists/claws.ts`, read 2026-09-17): no accuracy multiplier, every accuracy roll against slash defence, the
 * hitsplats of [DragonClaws.sliceAndDice] / [BurningClaws.barrage]. Every hitsplat goes through the shared [dealHit] route
 * (protection prayers, Deflect, special experience). A zero hitsplat is dealt as a non-landing hit.
 *
 * Replaces the earlier approximation (1.5x accuracy, half-damage follow-ups, 20 % consolation hit) that did not match OSRS.
 * Hitsplat timing: the four Dragon claws hits land on 1, 1, 2, 2 ([DragonClaws.HIT_DELAYS], audit C-06); Burning claws uses 1, 1, 2
 * (ADAPTED). Look: Dragon claws keep the 667 special; Burning claws play the imported OSRS HUMAN_WEAPON_BURNING_CLAWS_02_SPEC,
 * VFX_BURNING_CLAWS_SPEC_02 and burning_claws_swipe_01.
 */
fun splat(
    player: gg.rsmod.game.model.entity.Player,
    target: gg.rsmod.game.model.entity.Pawn,
    damage: Int,
    delay: Int,
) = player.dealHit(
    target = target,
    minHit = damage.toDouble(),
    maxHit = damage.toDouble(),
    landHit = damage > 0,
    delay = delay,
    hitType = HitType.MELEE,
).let { hit -> if (damage > 0) hit.hit.hitmarks.firstOrNull()?.damage ?: 0 else 0 }

SpecialAttacks.register(50, Items.DRAGON_CLAWS) {
    player.animate(Anims.DRAGON_CLAWS_SPECIAL)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target)
    val accuracy = MeleeCombatFormula.getAccuracyAgainst(player, target, 1.0, StyleType.SLASH)
    val splats = DragonClaws.sliceAndDice(maxHit.toInt(), { accuracy >= world.randomDouble() }, kotlin.random.Random.Default)
    // Audit C-06: hits land on 1, 1, 2, 2 instead of 1-4.
    splats.forEachIndexed { i, damage -> splat(player, target, damage, DragonClaws.HIT_DELAYS[i]) }
}

SpecialAttacks.register(BurningClaws.ENERGY, Items.BURNING_CLAWS) {
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_WEAPON_BURNING_CLAWS_02_SPEC)
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.BURNING_CLAWS_SPEC)
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.BURNING_CLAWS_SWIPE)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target)
    val accuracy = MeleeCombatFormula.getAccuracyAgainst(player, target, 1.0, StyleType.SLASH)
    val barrage = BurningClaws.barrage(maxHit.toInt(), { accuracy >= world.randomDouble() }, kotlin.random.Random.Default)
    val delays = intArrayOf(1, 1, 2)
    barrage.hitsplats.forEachIndexed { i, damage ->
        val hit =
            player.dealHit(
                target = target,
                minHit = damage.toDouble(),
                maxHit = damage.toDouble(),
                landHit = damage > 0,
                delay = delays[i],
                hitType = HitType.MELEE,
            )
        // "Each of the three hits also has a chance to inflict a burn" - per hitsplat, only when one of the three rolls succeeded.
        if (barrage.successfulRoll >= 0 && world.randomDouble() < barrage.burnChance) {
            hit.hit.addAction { if (!target.isDead()) Burns.apply(target) }
        }
    }
}
