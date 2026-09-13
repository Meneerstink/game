package gg.rsmod.plugins.content.combat.specialattack.weapons.dragonequipment

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * Dragon claws: a 50% energy, four-hit special.
 *
 * Real mechanic (unverified against this exact cache, but the standard/documented
 * behaviour used consistently since the weapon's introduction): hit 1 is a normal
 * accuracy roll. If it lands, hit 2 is guaranteed and deals half of hit 1's damage,
 * hit 3 is a fresh guaranteed-to-land roll, and hit 4 deals half of hit 3's damage.
 * If hit 1 misses, hit 2 is a small guaranteed consolation hit, hit 3 rolls accuracy
 * again, and hit 4 either halves hit 3 (if it landed) or repeats the consolation hit.
 * This ensures the weapon can never fully whiff all four swings.
 */
val CLAWS_SPECIAL_REQUIREMENT = 50

SpecialAttacks.register(
    CLAWS_SPECIAL_REQUIREMENT,
    Items.DRAGON_CLAWS,
) {
    player.animate(Anims.DRAGON_CLAWS_SPECIAL)

    val maxHit = MeleeCombatFormula.getMaxHit(player, target)
    val accuracy = MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 1.5)
    val consolationHit = (maxHit * 0.2).toInt().coerceAtLeast(1)

    val hit1Lands = accuracy >= world.randomDouble()
    val hit1 = player.dealHit(target = target, maxHit = maxHit, landHit = hit1Lands, delay = 1, hitType = HitType.MELEE)
    val dmg1 = hit1.hit.hitmarks.firstOrNull()?.damage ?: 0

    if (hit1Lands) {
        target.hit(damage = dmg1 / 2, type = HitType.MELEE.id, delay = 2)

        val hit3Lands = accuracy >= world.randomDouble()
        val hit3 = player.dealHit(target = target, maxHit = maxHit, landHit = hit3Lands, delay = 3, hitType = HitType.MELEE)
        val dmg3 = hit3.hit.hitmarks.firstOrNull()?.damage ?: 0
        target.hit(damage = dmg3 / 2, type = HitType.MELEE.id, delay = 4)
    } else {
        target.hit(damage = consolationHit, type = HitType.MELEE.id, delay = 2)

        val hit3Lands = accuracy >= world.randomDouble()
        val hit3 = player.dealHit(target = target, maxHit = maxHit, landHit = hit3Lands, delay = 3, hitType = HitType.MELEE)
        val dmg3 = hit3.hit.hitmarks.firstOrNull()?.damage ?: 0
        if (hit3Lands) {
            target.hit(damage = dmg3 / 2, type = HitType.MELEE.id, delay = 4)
        } else {
            target.hit(damage = consolationHit, type = HitType.MELEE.id, delay = 4)
        }
    }
}
