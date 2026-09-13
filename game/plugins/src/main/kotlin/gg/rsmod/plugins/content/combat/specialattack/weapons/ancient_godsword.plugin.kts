package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.AncientGodsword

/**
 * OSRS-IMPORT Ancient godsword - Blood Sacrifice (rules and sources in [AncientGodsword]). The OSRS special
 * animation/graphics are not in 667: the normal godsword attack animation is used and no mark graphic is shown
 * (ADAPTED_TO_667). The sacrifice hit uses the server's typeless hitsplat.
 */
SpecialAttacks.register(AncientGodsword.SPECIAL_ENERGY, Items.ANCIENT_GODSWORD) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = AncientGodsword.SPECIAL_DAMAGE)
    val landHit = MeleeCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = AncientGodsword.SPECIAL_ACCURACY) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    if (!landHit) return@register
    player.world.queue {
        wait(AncientGodsword.MARK_TICKS)
        if (victim.isDead() || player.isDead()) return@queue
        if (victim.tile.height != player.tile.height || victim.tile.getDistance(player.tile) >= AncientGodsword.ESCAPE_DISTANCE) return@queue
        val before = victim.getCurrentLifepoints()
        victim.hit(damage = AncientGodsword.SACRIFICE_DAMAGE, type = HitType.REGULAR_HIT)
        val dealt = minOf(AncientGodsword.SACRIFICE_DAMAGE, before)
        val heal = AncientGodsword.heal(victim.getMaximumLifepoints(), victim is Player, dealt)
        if (heal > 0) player.heal(heal)
    }
}
