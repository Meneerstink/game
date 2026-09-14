package gg.rsmod.plugins.content.combat.specialattack

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.mechanics.poison.WeaponPoison

/**
 * Adjacent gap "special attacks get no XP": OSRS Wiki "Combat": "Players gain combat skill experience from dealing damage. The normal value
 * is 4 experience per damage in ranged and melee combat styles, and 2 experience per damage in Magic combat style" - a special attack's
 * damage is damage dealt (Novite 667 PlayerCombat grants it in the same shared hit path). Every hit a special deals through
 * [gg.rsmod.plugins.content.combat.dealHit] while [WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] is set gets exactly the experience the
 * normal attack strategy of its hit type gives (melee / ranged XP mode, magic damage experience without a cast).
 * SOURCE_GAP (unchanged, no experience): delayed effect hits dealt outside the special's own swing (Ancient godsword blood sacrifice, Abyssal
 * vine whip vine hits); typeless hits.
 */
object SpecialAttackXp {
    fun award(
        player: Player,
        target: Pawn,
        damage: Int,
        hitType: HitType,
    ) {
        if (damage <= 0 || player.attr[WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] != true) return
        when (hitType) {
            HitType.MELEE -> MeleeCombatStrategy.addCombatXp(player, target, damage)
            HitType.RANGE -> RangedCombatStrategy.addCombatXp(player, target, damage)
            HitType.MAGIC -> MagicCombatStrategy.addCombatXp(player, target, damage, baseXp = 0.0)
            else -> Unit
        }
    }
}
