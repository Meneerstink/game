package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.model.Gender
import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids
import gg.rsmod.plugins.content.mechanics.deadman.items.ZoryaTome

/** Exact named cache2762 assets. Missing imports refuse the cast before resources or buffs change. */
object ZoryaSpell {
    fun ready(player: Player): Boolean {
        val female = player.appearance.gender == Gender.FEMALE
        return listOf(if (female) 15146 else 15139, if (female) 15147 else 15140).all { Dmm2Ids.seq(it) != null } &&
            (4427..4435).all { Dmm2Ids.spot(it) != null } && listOf(12660, 12661, 12662).all { Dmm2Ids.sound(it) != null }
    }

    fun fire(player: Player, target: Pawn, special: Boolean): Boolean {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return false
        if (PoweredStaves.staffFor(weapon.id)?.zorya != true || PoweredStaves.charges(weapon) <= 0 || target is Player || !ready(player)) return false
        val shot = ZoryaTome.prepare(player, special)
        val female = player.appearance.gender == Gender.FEMALE
        val animation = if (special) { if (female) 15147 else 15140 } else { if (female) 15146 else 15139 }
        val cast = if (special) { if (female) 4434 else 4432 } else { if (female) 4435 else 4433 }
        val travel = if (special || shot.empowered) 4428 else 4427
        val impact = if (special) 4430 else if (shot.empowered) 4431 else 4429
        player.stopMovement()
        player.animate(Dmm2Ids.seq(animation)!!)
        player.graphic(Dmm2Ids.spot(cast)!!)
        player.playSound(Dmm2Ids.sound(if (special) 12661 else 12662)!!)
        val projectile = player.createProjectile(target, Dmm2Ids.spot(travel)!!, ProjectileType.MAGIC)
        player.world.spawn(projectile)
        val landed = MagicCombatFormula.getAccuracy(player, target, shot.accuracy) >= player.world.randomDouble()
        val hit = player.dealHit(target, maxHit = MagicCombatFormula.getMaxHit(player, target, shot.damage),
            landHit = landed, delay = MagicCombatStrategy.getHitDelay(player.getCentreTile(), target.getCentreTile()), hitType = HitType.MAGIC)
        if (landed) target.graphic(Graphic(Dmm2Ids.spot(impact)!!, 0, projectile.lifespan)) else target.graphic(Graphic(85, 96, projectile.lifespan))
        if (special && landed && !gg.rsmod.plugins.content.areas.poh.PlayerHouse.isCombatDummy(target)) ZoryaTome.successfulSpecial(player)
        if (!gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilCombat.savesRunes(player)) {
            player.equipment[EquipmentType.WEAPON.id] = PoweredStaves.withCharges(weapon, PoweredStaves.charges(weapon) - 1)
        }
        if (!special) {
            val damage = hit.hit.hitmarks.sumOf { it.damage }
            MagicCombatStrategy.addCombatXp(player, target, damage, baseXp = 0.0)
        }
        return true
    }
}
