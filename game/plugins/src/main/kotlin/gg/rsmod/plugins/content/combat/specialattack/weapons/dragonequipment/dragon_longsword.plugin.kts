package gg.rsmod.plugins.content.combat.specialattack.weapons.dragonequipment

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

val SPECIAL_REQUIREMENT = 25

SpecialAttacks.register(SPECIAL_REQUIREMENT, Items.DRAGON_LONGSWORD) {
    player.animate(Anims.DRAGON_LONGSWORD_SPECIAL)
    player.graphic(Gfx.DRAGON_LONGSWORD_SPECIAL, 92)
    player.playSound(Sfx.CLEAVE)
    for (i in 0 until 1) {
        val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.25)
        // Audit I-10/C-04: OSRS Cleave is +25 % damage only (the accuracy bonus is the (bh) variant), rolled against slash.
        val accuracy = MeleeCombatFormula.getAccuracyAgainst(player, target, specialAttackMultiplier = 1.0, defenceStyle = gg.rsmod.game.model.combat.StyleType.SLASH)
        val landHit = accuracy >= world.randomDouble()
        val delay = if (target.entityType.isNpc) i + 1 else 1
        player.dealHit(
            target = target,
            maxHit = maxHit,
            landHit = landHit,
            delay = delay,
            hitType = HitType.MELEE,
        )
    }
}
