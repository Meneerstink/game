package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.content.items.osrs.AmuletOfTheDamned

/**
 * Dharok the Wretched's full set + amulet of the damned (OSRS Wiki "Amulet of the Damned"): "25% chance of recoiling 15% of damage
 * taken. This effect can stack with the Vengeance spell and ring of recoil effects." The amulet was imported on 2026-09-29 (owner:
 * "fully working"), which lifts the former id block. The reflect lands like the Ring of recoil's.
 */
object DharokDamnedReflect {
    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        val reflect = AmuletOfTheDamned.dharokRecoil(target, damage)
        if (reflect <= 0) return
        attacker.hit(damage = reflect, type = HitType.REFLECTED)
        attacker.damageMap.add(target, reflect)
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDamage(attacker, target, reflect)
    }
}
