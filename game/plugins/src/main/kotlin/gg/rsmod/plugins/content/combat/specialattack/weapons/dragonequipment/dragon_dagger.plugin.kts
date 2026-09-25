package gg.rsmod.plugins.content.combat.specialattack.weapons.dragonequipment

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

val SPECIAL_REQUIREMENT = 25

SpecialAttacks.register(
    SPECIAL_REQUIREMENT,
    Items.DRAGON_DAGGER,
    Items.DRAGON_DAGGER_P,
    Items.DRAGON_DAGGER_P_5680,
    Items.DRAGON_DAGGER_P_5698,
    Items.CORRUPT_DRAGON_DAGGER,
    Items.C_DRAGON_DAGGER_DEG,
) {
    player.animate(Anims.DRAGON_DAGGER_SPECIAL)
    player.graphic(Gfx.DRAGON_DAGGER_SPECIAL, height = 92)
    player.playSound(Sfx.PUNCTURE)

    for (i in 0 until 2) {
        val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.15)
        // Audit C-04: the Puncture rolls against slash defence (osrs-dps-calc), whatever style is selected.
        val accuracy = MeleeCombatFormula.getAccuracyAgainst(player, target, specialAttackMultiplier = 1.15, defenceStyle = gg.rsmod.game.model.combat.StyleType.SLASH)
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
