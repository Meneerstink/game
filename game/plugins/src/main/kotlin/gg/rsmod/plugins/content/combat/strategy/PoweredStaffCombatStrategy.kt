package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.venom
import gg.rsmod.plugins.content.items.osrs.PoweredStaves

/**
 * OSRS-IMPORT powered staff built-in spell (rules and sources in [PoweredStaves]). Accuracy and max hit come from
 * [MagicCombatFormula] (stance bonus and ⌊Magic/3⌋ + offset base), speed 4 from [gg.rsmod.plugins.content.combat.CombatConfigs].
 *
 * ADAPTED_TO_667 (owner decision option a): the OSRS cast animation 1167 and spotanims (Seas 1251/1252/1253, Swamp 665/1040/
 * 1042, Sanguinesti 1540/1539/1541) are not in the 667 cache, so the tridents show the 667 Water Blast look and the
 * Sanguinesti staff the 667 Blood Blitz look.
 * Experience: rsmod `PlayerAttackManager.giveStaffCombatXp` - 2 Magic and 1.33 Hitpoints per damage on every style, in this
 * server's existing magic strategy units. SOURCE_CONFLICT (owner question): the wiki style table lists Defence experience for
 * Longrange, rsmod gives none; the rsmod amounts are used until the owner decides.
 */
object PoweredStaffCombatStrategy : CombatStrategy {
    fun look(staff: PoweredStaves.Staff): CombatSpell = if (staff.leech) CombatSpell.BLOOD_BLITZ else CombatSpell.WATER_BLAST

    override fun getAttackRange(pawn: Pawn): Int {
        val player = pawn as? Player ?: return PoweredStaves.ATTACK_RANGE
        val range = PoweredStaves.ATTACK_RANGE + if (PoweredStaves.isLongrange(player)) PoweredStaves.LONGRANGE_EXTRA_RANGE else 0
        return minOf(10, range)
    }

    override fun canAttack(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        val player = pawn as? Player ?: return false
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return false
        if (PoweredStaves.charges(weapon) <= 0) {
            player.message(PoweredStaves.NO_CHARGES_MESSAGE)
            return false
        }
        // "Powered staff spells cannot be cast upon other players in the Wilderness."
        if (target is Player && (target.tile.getWildernessLevel() > 0 || player.tile.getWildernessLevel() > 0)) {
            player.message(PoweredStaves.WILDERNESS_PLAYER_MESSAGE)
            return false
        }
        return true
    }

    override fun attack(
        pawn: Pawn,
        target: Pawn,
    ) {
        val player = pawn as? Player ?: return
        val world = player.world
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        val staff = PoweredStaves.staffFor(weapon.id) ?: return
        val look = look(staff)

        player.stopMovement()
        look.castGfx?.let { player.graphic(it) }
        player.animate(look.castAnimation[1])
        // "One charge is consumed each time you cast the built-in spell."
        player.equipment[EquipmentType.WEAPON.id] = PoweredStaves.withCharges(weapon, PoweredStaves.charges(weapon) - 1)

        val projectile = player.createProjectile(target, gfx = look.projectile, type = ProjectileType.MAGIC)
        world.spawn(projectile)
        val hitDelay = MagicCombatStrategy.getHitDelay(player.getCentreTile(), target.getCentreTile())
        val landHit = MagicCombatFormula.getAccuracy(player, target) >= world.randomDouble()
        if (landHit) {
            look.impactGfx?.let { target.graphic(Graphic(it.id, it.height, projectile.lifespan)) }
        } else {
            target.graphic(Graphic(85, 96, projectile.lifespan))
        }

        // Sanguinesti: successful hits have a 1/5 chance of +8 damage and healing half the damage dealt.
        val leech = staff.leech && landHit && world.randomDouble() < PoweredStaves.LEECH_CHANCE
        val maxHit = MagicCombatFormula.getMaxHit(player, target)
        val pawnHit =
            player.dealHit(
                target = target,
                maxHit = maxHit,
                landHit = landHit,
                delay = hitDelay,
                hitType = HitType.MAGIC,
                bonusDamage = if (leech) PoweredStaves.LEECH_BONUS_DAMAGE else 0,
            )
        val damage = pawnHit.hit.hitmarks.sumOf { it.damage }
        if (leech) {
            pawnHit.hit.addAction { PoweredStaves.leechHeal(damage).takeIf { it > 0 }?.let { player.heal(it) } }
        }
        // Trident of the Swamp: 25 % venom on successful hits.
        if (landHit && staff.venomChance > 0.0) {
            pawnHit.hit.addAction { if (world.randomDouble() < staff.venomChance) target.venom() }
        }
        if (damage > 0) addCombatXp(player, target, damage)
    }

    /** rsmod `giveStaffCombatXp`: 2 Magic + 1.33 Hitpoints per damage, capped at an NPC's remaining hitpoints. */
    private fun addCombatXp(
        player: Player,
        target: Pawn,
        damage: Int,
    ) {
        val modDamage = if (target is Npc) target.getCurrentLifepoints().coerceAtMost(damage) else damage
        val multiplier = if (target is Npc) Combat.getNpcXpMultiplier(target) else 1.0
        // Owner decision 2026-09-14 (a): Longrange gives "Magic / Defence / Hitpoints" (wiki combat styles table of the Trident of
        // the Seas) at the defensive-casting rates 1.33 Magic, 1 Defence, 1.33 Hitpoints per damage ("Combat Options"); the wiki
        // gives no powered-staff-specific split (recorded). Same units as MagicCombatStrategy defensive casting.
        if (PoweredStaves.isLongrange(player)) {
            val longrangeRate = player.addXp(Skills.MAGIC, modDamage * 0.133 * multiplier, checkBrawlingGloves = true)
            player.addXp(Skills.DEFENCE, modDamage * 0.1 * multiplier * longrangeRate)
            player.addXp(Skills.CONSTITUTION, modDamage * 0.133 * multiplier * longrangeRate)
            return
        }
        // Same unit as MagicCombatStrategy (0.2 Magic / 0.133 Hitpoints per damage point there = 2 / 1.33).
        val bonusRate = player.addXp(Skills.MAGIC, modDamage * PoweredStaves.MAGIC_XP_PER_DAMAGE / 10.0 * multiplier, checkBrawlingGloves = true)
        player.addXp(Skills.CONSTITUTION, modDamage * 0.133 * multiplier * bonusRate)
    }
}
