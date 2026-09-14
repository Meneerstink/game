package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.combat.XpMode
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.hasWeaponType
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import kotlin.math.min

/**
 * @author Tom <rspsmods@gmail.com>
 */
object MeleeCombatStrategy : CombatStrategy {
    override fun getAttackRange(pawn: Pawn): Int {
        var baseDistance = 1

        if (pawn is Player) {
            val halberd = pawn.hasWeaponType(WeaponType.HALBERD)
            if (halberd) baseDistance = 2
        }

        return baseDistance
    }

    override fun canAttack(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        return true
    }

    override fun attack(
        pawn: Pawn,
        target: Pawn,
    ) {
        val world = pawn.world

        val animation = CombatConfigs.getAttackAnimation(pawn)
        pawn.animate(animation)

        if (pawn is Player) {
            val weapon = pawn.equipment[3]
            if (weapon != null && world.definitions.get(ItemDef::class.java, weapon.id).attackAudio > -1) {
                pawn.playSound(world.definitions.get(ItemDef::class.java, weapon.id).attackAudio)
            }
        }

        val blockAnimation = CombatConfigs.getBlockAnimation(target)
        target.animate(blockAnimation, priority = false)

        val formula = MeleeCombatFormula
        val accuracy = formula.getAccuracy(pawn, target)
        val maxHit = formula.getMaxHit(pawn, target)
        // OSRS Wiki Verac the Defiled's equipment ("Defiler"): 25 % chance of a guaranteed hit ignoring accuracy,
        // and against monsters that attack does 1 extra damage.
        val defiler = pawn is Player && MeleeCombatFormula.isWearingVerac(pawn) && world.chance(1, 4)
        val landHit = defiler || accuracy >= world.randomDouble()
        // Osmumten's fang: every successful hit rolls between 15% and 85% of the max hit (OsmumtensFang).
        val fangRange =
            if (pawn is Player && gg.rsmod.plugins.content.items.osrs.OsmumtensFang.isWielding(pawn)) {
                gg.rsmod.plugins.content.items.osrs.OsmumtensFang.damageRange(maxHit, special = false)
            } else {
                null
            }
        // Noxious halberd Virulence: the next accurate attack gets the cured poison/venom hit as its minimum (NoxiousHalberd).
        val virulenceMinimum =
            if (pawn is Player && gg.rsmod.plugins.content.items.osrs.NoxiousHalberd.isWielding(pawn)) {
                gg.rsmod.plugins.content.items.osrs.NoxiousHalberd.takeMinimum(pawn, landHit)
            } else {
                0
            }

        val damage =
            pawn
                .dealHit(
                    target = target,
                    minHit = gg.rsmod.plugins.content.items.osrs.OsmumtensFang.minHitArgument(maxOf(fangRange?.first ?: 0, virulenceMinimum)),
                    maxHit = fangRange?.second?.toDouble() ?: maxHit,
                    landHit = landHit,
                    delay = 1,
                    hitType = HitType.MELEE,
                    bonusDamage = if (defiler && target is Npc) 1 else 0,
                ).also { pawnHit ->
                    // Noxious halberd: 33% chance to envenom per attack (NoxiousHalberd).
                    if (pawn is Player && gg.rsmod.plugins.content.items.osrs.NoxiousHalberd.isWielding(pawn)) {
                        pawnHit.hit.addAction { gg.rsmod.plugins.content.items.osrs.NoxiousHalberd.rollVenom(pawn, target) }
                    }
                    // Toxic staff of the dead: 25 % venom on opponents struck by the charged staff (StaffOfTheDead).
                    if (pawn is Player && landHit) {
                        pawnHit.hit.addAction { gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.rollVenom(pawn, target) }
                    }
                }.hit.hitmarks
                .sumOf { it.damage }

        if (damage > 0 && pawn.entityType.isPlayer) {
            addCombatXp(pawn as Player, target, damage)
        }
    }

    private fun addCombatXp(
        player: Player,
        target: Pawn,
        damage: Int,
    ) {
        val modDamage = if (target.entityType.isNpc) min(target.getCurrentLifepoints(), damage) else damage
        val mode = CombatConfigs.getXpMode(player)
        val multiplier = if (target is Npc) Combat.getNpcXpMultiplier(target) else 1.0

        val hitpointsExperience = (modDamage * 0.133) * multiplier
        val combatExperience = (modDamage * 0.4) * multiplier
        val sharedExperience = (modDamage * 0.133) * multiplier
        var bonusRate: Double
        when (mode) {
            XpMode.ATTACK_XP -> {
                bonusRate = player.addXp(Skills.ATTACK, combatExperience, checkBrawlingGloves = true)
                player.addXp(Skills.CONSTITUTION, hitpointsExperience * bonusRate)
            }

            XpMode.STRENGTH_XP -> {
                bonusRate = player.addXp(Skills.STRENGTH, combatExperience, checkBrawlingGloves = true)
                player.addXp(Skills.CONSTITUTION, hitpointsExperience * bonusRate)
            }

            XpMode.DEFENCE_XP -> {
                bonusRate = player.addXp(Skills.DEFENCE, combatExperience, checkBrawlingGloves = true)
                player.addXp(Skills.CONSTITUTION, hitpointsExperience * bonusRate)
            }

            XpMode.SHARED_XP -> {
                bonusRate = player.addXp(Skills.ATTACK, sharedExperience, checkBrawlingGloves = true)
                player.addXp(Skills.STRENGTH, sharedExperience * bonusRate)
                player.addXp(Skills.DEFENCE, sharedExperience * bonusRate)
                player.addXp(Skills.CONSTITUTION, hitpointsExperience * bonusRate)
            }

            XpMode.RANGED_XP -> TODO()
            XpMode.MAGIC_XP -> TODO()
        }
    }
}
