package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.CombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/** Server mechanics; Claude supplies proven local AV ids alongside the item contract. */
object StarterWeaponCombat : CombatStrategy {
    data class Appearance(val animation: Int, val cast: Int, val projectile: Int, val impact: Int) {
        init { require(listOf(animation, cast, projectile, impact).all { it >= 0 }) }
    }
    @Volatile var staffAppearance: Appearance? = null
    @Volatile var bowAppearance: Appearance? = null

    fun builtInStaff(pawn: Pawn): Boolean = pawn is Player &&
        DeadmanStarterPack.isStaff(pawn.getEquipment(EquipmentType.WEAPON)?.id) && !pawn.attr.has(Combat.CASTING_SPELL)

    override fun getAttackRange(pawn: Pawn): Int =
        if (pawn is Player && CombatConfigs.getAttackStyle(pawn) == gg.rsmod.game.model.combat.WeaponStyle.LONG_RANGE) 8 else 6

    override fun canAttack(pawn: Pawn, target: Pawn): Boolean {
        val player = pawn as? Player ?: return false
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return false
        return target !is Player && DeadmanStarterPack.isStaff(weapon.id) &&
            DeadmanStarterPack.charges(weapon) > 0 && staffAppearance != null
    }

    override fun attack(pawn: Pawn, target: Pawn) {
        if (!canAttack(pawn, target)) return
        val player = pawn as Player
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        val look = staffAppearance ?: return
        player.stopMovement()
        player.animate(look.animation)
        player.graphic(look.cast, 96) // N628: hand height, like CombatSpell.FIRE_STRIKE (22 = knees, owner 17-11)
        val projectile = player.createProjectile(target, look.projectile, ProjectileType.MAGIC)
        player.world.spawn(projectile)
        val landed = gg.rsmod.plugins.content.items.osrs.ConflictionGauntlets.roll(
            player, target, weapon.id, MagicCombatFormula.getAccuracy(player, target), eligible = true,
        ) { player.world.randomDouble() }
        target.graphic(Graphic(if (landed) look.impact else MagicCombatStrategy.SPLASH_GFX, 32, projectile.lifespan))
        val hit = player.dealHit(target = target, maxHit = MagicCombatFormula.getMaxHit(player, target),
            landHit = landed, delay = MagicCombatStrategy.getHitDelay(player.getCentreTile(), target.getCentreTile()), hitType = HitType.MAGIC)
        val damage = hit.hit.hitmarks.sumOf { it.damage }
        val longrange = CombatConfigs.getAttackStyle(player) == gg.rsmod.game.model.combat.WeaponStyle.LONG_RANGE
        if (landed) hit.hit.addAction { gg.rsmod.plugins.content.items.osrs.SerpentineHelm.rollVenom(player, target) }
        consume(player, weapon)
        if (gg.rsmod.plugins.content.areas.poh.PlayerHouse.isCombatDummy(target)) return
        val xpDamage = if (target is Npc) minOf(damage, target.getCurrentLifepoints()) else damage
        if (xpDamage <= 0) return
        val multiplier = if (target is Npc) Combat.getNpcXpMultiplier(target) else 1.0
        val bonus = player.addXp(Skills.MAGIC, xpDamage * (if (longrange) CombatXpRates.DEFENSIVE_MAGIC_PER_DAMAGE else 2.0) * multiplier, checkBrawlingGloves = true)
        if (longrange) player.addXp(Skills.DEFENCE, xpDamage * CombatXpRates.DEFENSIVE_DEFENCE_PER_DAMAGE * multiplier * bonus)
        player.addXp(Skills.CONSTITUTION, xpDamage * CombatXpRates.HITPOINTS_PER_DAMAGE * multiplier * bonus)
    }

    fun consume(player: Player, weapon: Item) {
        player.equipment[EquipmentType.WEAPON.id] = DeadmanStarterPack.afterAttack(weapon)
        if (DeadmanStarterPack.charges(weapon) <= 1) {
            player.refreshBonuses()
            if (DeadmanStarterPack.isStaff(weapon.id)) player.message("<col=ff0000>Your starter staff has fallen apart!</col>")
        }
    }
}
