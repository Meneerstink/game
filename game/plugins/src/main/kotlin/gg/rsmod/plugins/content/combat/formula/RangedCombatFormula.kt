@file:Suppress("UNUSED_PARAMETER")

package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import kotlin.math.floor

/**
 * @author Tom <rspsmods@gmail.com>
 */
object RangedCombatFormula : CombatFormula {

    override fun getAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Double {
        // Check if the target has the prayer protection and the attacker is not a player
        if (target.isProtectedFrom(CombatClass.RANGED) && pawn !is Player) {
            return 0.0 // Hits will never land
        }
        return getUnprotectedAccuracy(pawn, target, specialAttackMultiplier)
    }

    /**
     * Accuracy roll without the protection-prayer short circuit, for npc effects that trigger when a hit "would have been
     * successful" through Protect from Missiles (OSRS Wiki Karil the Tainted set effect).
     */
    fun getUnprotectedAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double = 1.0,
    ): Double {
        val attack = getAttackRoll(pawn, target, specialAttackMultiplier)
        val defence = getDefenceRoll(pawn, target)

        val accuracy: Double =
            if (attack > defence) {
                1.0 - (defence + 2.0) / (2.0 * (attack + 1.0))
            } else {
                attack / (2.0 * (defence + 1))
            }
        return accuracy
    }

    override fun getMaxHit(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        val a =
            if (pawn is Player) {
                getEffectiveRangedLevel(pawn)
            } else if (pawn is Npc) {
                getEffectiveRangedLevel(pawn)
            } else {
                0.0
            }
        val b = getEquipmentRangedBonus(pawn)

        // OSRS Wiki "Maximum ranged hit": ⌊⌊0.5 + Effective Ranged Strength × (Ranged Strength + 64) / 640⌋ × Gear⌋.
        var base = floor(0.5 + a * (b + 64.0) / 640.0)
        if (pawn is Player) {
            base = applyRangedSpecials(pawn, target, base, specialAttackMultiplier, specialPassiveMultiplier)
        }
        return base
    }

    private fun getAttackRoll(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Int {
        val a =
            if (pawn is Player) {
                getEffectiveAttackLevel(pawn)
            } else if (pawn is Npc) {
                getEffectiveAttackLevel(pawn)
            } else {
                0.0
            }
        val b = getEquipmentAttackBonus(pawn)

        var maxRoll = a * (b + 64.0)
        if (pawn is Player) {
            maxRoll = applyAttackSpecials(pawn, target, maxRoll, specialAttackMultiplier)
        }
        return maxRoll.toInt()
    }

    private fun getDefenceRoll(
        pawn: Pawn,
        target: Pawn,
    ): Int {
        // S2, 2026-09-03: this must read the TARGET's effective Defence level, not the
        // attacker's - `MeleeCombatFormula`'s equivalent function already does this
        // correctly. Discovered while writing golden accuracy vectors: an existing
        // S1 regression test masked this because it left both pawns at the default
        // level-1 defence, making the (wrong) attacker-derived value and the (right)
        // target-derived value coincidentally equal. See RSPS_DECISIONS.md.
        val a =
            if (target is Player) {
                getEffectiveDefenceLevel(target)
            } else if (target is Npc) {
                getEffectiveDefenceLevel(target)
            } else {
                0.0
            }
        val b = getEquipmentDefenceBonus(target)

        var maxRoll = a * (b + 64.0)
        maxRoll = applyDefenceSpecials(target, maxRoll)
        return maxRoll.toInt()
    }

    private fun applyRangedSpecials(
        player: Player,
        target: Pawn,
        base: Double,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        var hit = base

        // Crystal armour with a crystal bow / Bow of Faerdhinen: ×(40 + n) / 40, before the Salve / Slayer factor (CrystalEquipment).
        hit = gg.rsmod.plugins.content.items.osrs.CrystalEquipment.applyDamage(player, hit)

        // S3, 2026-09-03: routes through the Ranged-specific damage composition (Salve/black
        // mask plus the Twisted bow passive, applied only while the bow is actually equipped -
        // see TargetModifiers.rangedDamageMultiplier) instead of the generic equipmentMultiplier
        // directly, so the bow-specific scaling can never leak into Melee/Magic.
        hit = floor(hit * TargetModifiers.rangedDamageMultiplier(player, target))

        // Eclipse atlatl: "it uses the melee bonuses from the slayer helmet and salve amulets" (wiki calculator: melee salve / black mask).
        if (gg.rsmod.plugins.content.items.osrs.MoonSets.wieldingAtlatl(player)) hit = floor(hit * TargetModifiers.equipmentMultiplier(player, target))

        // OSRS-IMPORT bows (wiki DPS calculator order): revenant bows x3/2 in the Wilderness, Scorching bow demonbane +30 %, then the
        // Tonalztics of Ralos x3/4.
        if (gg.rsmod.plugins.content.items.osrs.RevenantBows.wildernessBuff(player, target)) hit = floor(hit * 3 / 2)
        if (gg.rsmod.plugins.content.items.osrs.ScorchingBow.demonbane(player, target)) {
            hit = TargetModifiers.addPercent(hit, gg.rsmod.plugins.content.items.osrs.ScorchingBow.DEMONBANE_PERCENT)
        }
        if (gg.rsmod.plugins.content.items.osrs.Tonalztics.isTonalztics(player.getEquipment(EquipmentType.WEAPON)?.id)) {
            hit = gg.rsmod.plugins.content.items.osrs.Tonalztics.maxHit(hit)
        }

        // Step three ("⌊Base Damage × Special Bonus⌋"): every later multiplier is floored in turn.
        hit = floor(hit * specialAttackMultiplier)

        if (target.isProtectedFrom(CombatClass.RANGED)) {
            hit = floor(hit * 0.6)
        }

        if (player.hasEquipped(EquipmentType.WEAPON, Items.SLING)) {
            hit = floor(hit * 0.9)
        }

        hit =
            if (specialPassiveMultiplier == 1.0) {
                floor(applyPassiveMultiplier(player, target, hit))
            } else {
                floor(hit * specialPassiveMultiplier)
            }

        hit = floor(hit * getDamageDealMultiplier(player))
        hit = floor(hit * getDamageTakeMultiplier(target))

        return hit
    }

    private fun applyAttackSpecials(
        player: Player,
        target: Pawn,
        base: Double,
        specialAttackMultiplier: Double,
    ): Double {
        var hit = base

        // Crystal armour with a crystal bow / Bow of Faerdhinen: ×(20 + n) / 20, before the Salve / Slayer factor (CrystalEquipment).
        hit = gg.rsmod.plugins.content.items.osrs.CrystalEquipment.applyAccuracy(player, hit)

        // S3, 2026-09-03: accuracy-stage counterpart of the damage-stage change above - see
        // TargetModifiers.rangedAccuracyMultiplier.
        // "Damage per second/Ranged": ⌊Effective Ranged Attack × (Ranged Attack + 64) × Gear Bonus⌋.
        hit = floor(hit * TargetModifiers.rangedAccuracyMultiplier(player, target))
        // OSRS-IMPORT bows: revenant bows x3/2 in the Wilderness, Scorching bow demonbane +30 % (before the special factor).
        if (gg.rsmod.plugins.content.items.osrs.RevenantBows.wildernessBuff(player, target)) hit = floor(hit * 3 / 2)
        if (gg.rsmod.plugins.content.items.osrs.ScorchingBow.demonbane(player, target)) {
            hit = TargetModifiers.addPercent(hit, gg.rsmod.plugins.content.items.osrs.ScorchingBow.DEMONBANE_PERCENT)
        }
        hit = floor(hit * specialAttackMultiplier)

        return hit
    }

    private fun applyDefenceSpecials(
        target: Pawn,
        base: Double,
    ): Double {
        var hit = base

        /**
         if (target is Player && isWearingTorag(target) && target.hasEquipped(EquipmentType.AMULET, Items.AMULET_OF_THE_DAMNED_FULL)) {
         val lost = (target.getMaxHp() - target.getCurrentHp()) / 100.0
         val max = target.getMaxHp() / 100.0
         hit *= (1.0 + (lost * max))
         hit = Math.floor(hit)
         }**/

        return hit
    }

    private fun getEquipmentRangedBonus(pawn: Pawn): Double =
        when (pawn) {
            // Eclipse atlatl: the melee strength bonus replaces the ranged strength bonus (MoonSets).
            is Player ->
                if (gg.rsmod.plugins.content.items.osrs.MoonSets.wieldingAtlatl(pawn)) pawn.getStrengthBonus().toDouble() else pawn.getRangedStrengthBonus().toDouble()
            is Npc -> pawn.getRangedStrengthBonus().toDouble()
            else -> throw IllegalArgumentException("Invalid pawn type. $pawn")
        }

    private fun getEquipmentAttackBonus(pawn: Pawn): Double {
        // Dizana's Sunfire: +10 Ranged accuracy for arrows and bolts while the quiver is charged or blessed.
        val sunfire = if (pawn is Player) gg.rsmod.plugins.content.items.osrs.DizanasQuiver.accuracyBonus(pawn) else 0
        // A shot from Dizana's quiver's second slot uses the stored ammo's ranged attack instead of the unused slot ammo's.
        val quiverAmmo =
            if (pawn is Player) gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.quiverBonusCorrection(pawn, BonusSlot.ATTACK_RANGED) else 0
        return (pawn.getBonus(BonusSlot.ATTACK_RANGED) + sunfire + quiverAmmo).toDouble()
    }

    private fun getEquipmentDefenceBonus(target: Pawn): Double {
        return target.getBonus(BonusSlot.DEFENCE_RANGED).toDouble()
    }

    private fun getEffectiveRangedLevel(player: Player): Double {
        // Eclipse atlatl: the Strength level (visible boosts) replaces the Ranged level for the max hit, with ranged prayers (MoonSets).
        val damageSkill = if (gg.rsmod.plugins.content.items.osrs.MoonSets.wieldingAtlatl(player)) Skills.STRENGTH else Skills.RANGED
        var effectiveLevel = floor(player.skills.getCurrentLevel(damageSkill) * getPrayerRangedMultiplier(player))

        effectiveLevel +=
            when (CombatConfigs.getAttackStyle(player)) {
                WeaponStyle.ACCURATE -> 3.0
                else -> 0.0
            }

        effectiveLevel += 8.0

        // Void Knight equipment: ranged void +10 % damage, elite ranged void 12.5 %; "⌊(…) × Void Modifier⌋".
        if (VoidKnight.wearing(player, VoidKnight.RANGER_HELMS)) {
            val modifier = if (VoidKnight.wearingElite(player, VoidKnight.RANGER_HELMS)) 1.125 else 1.10
            effectiveLevel = floor(effectiveLevel * modifier)
        }

        return effectiveLevel
    }

    private fun getEffectiveAttackLevel(player: Player): Double {
        var effectiveLevel =
            Math.floor(
                player.skills.getCurrentLevel(Skills.RANGED) * getPrayerAttackMultiplier(player),
            )

        effectiveLevel +=
            when (CombatConfigs.getAttackStyle(player)) {
                WeaponStyle.ACCURATE -> 3.0
                else -> 0.0
            }

        effectiveLevel += 8.0

        // Void accuracy is 1.1 for both ranged void variants.
        if (VoidKnight.wearing(player, VoidKnight.RANGER_HELMS)) {
            effectiveLevel = floor(effectiveLevel * 1.10)
        }

        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(player: Player): Double {
        var effectiveLevel =
            Math.floor(
                player.skills.getCurrentLevel(Skills.DEFENCE) * getPrayerDefenceMultiplier(player),
            )

        effectiveLevel +=
            when (CombatConfigs.getAttackStyle(player)) {
                WeaponStyle.DEFENSIVE -> 3.0
                WeaponStyle.CONTROLLED -> 1.0
                WeaponStyle.LONG_RANGE -> 3.0
                else -> 0.0
            }

        effectiveLevel += 8.0

        return effectiveLevel
    }

    private fun getEffectiveRangedLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.RANGED).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveAttackLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.RANGED).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(npc: Npc): Double {
        // "Damage per second/Ranged": NPC ranged defence roll = (Defence level + 9) × (ranged defence bonus + 64).
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.DEFENCE).toDouble()
        effectiveLevel += 9
        return effectiveLevel
    }

    private fun getPrayerRangedMultiplier(player: Player): Double =
        when {
            Prayers.isActive(player, Prayer.SHARP_EYE) -> 1.05
            Prayers.isActive(player, Prayer.HAWK_EYE) -> 1.10
            Prayers.isActive(player, Prayer.EAGLE_EYE) -> 1.15
            Prayers.isActive(player, Prayer.RIGOUR) -> 1.23
            else -> 1.0
        }

    private fun getPrayerAttackMultiplier(player: Player): Double =
        when {
            Prayers.isActive(player, Prayer.SHARP_EYE) -> 1.05
            Prayers.isActive(player, Prayer.HAWK_EYE) -> 1.10
            Prayers.isActive(player, Prayer.EAGLE_EYE) -> 1.15
            Prayers.isActive(player, Prayer.RIGOUR) -> 1.20
            else -> 1.0
        }

    private fun getPrayerDefenceMultiplier(player: Player): Double =
        when {
            Prayers.isActive(player, Prayer.THICK_SKIN) -> 1.05
            Prayers.isActive(player, Prayer.ROCK_SKIN) -> 1.10
            Prayers.isActive(player, Prayer.STEEL_SKIN) -> 1.15
            Prayers.isActive(player, Prayer.CHIVALRY) -> 1.20
            Prayers.isActive(player, Prayer.PIETY) -> 1.25
            Prayers.isActive(player, Prayer.RIGOUR) -> 1.25
            Prayers.isActive(player, Prayer.AUGURY) -> 1.25
            else -> 1.0
        }

    private fun applyPassiveMultiplier(
        player: Player,
        target: Pawn,
        base: Double,
    ): Double {
        return base
    }

    private fun getDamageDealMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_DEAL_MULTIPLIER] ?: 1.0

    private fun getDamageTakeMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_TAKE_MULTIPLIER] ?: 1.0

    private fun isDragon(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.DRAGON)
        }
        return false
    }

    private fun isFiery(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.FIERY)
        }
        return false
    }

    private fun isWearingTorag(player: Player): Boolean {
        return player.hasEquipped(
            EquipmentType.HEAD,
            Items.TORAGS_HELM,
            Items.TORAGS_HELM_25,
            Items.TORAGS_HELM_50,
            Items.TORAGS_HELM_75,
            Items.TORAGS_HELM_100,
        ) &&
            player.hasEquipped(
                EquipmentType.WEAPON,
                Items.TORAGS_HAMMERS,
                Items.TORAGS_HAMMERS_25,
                Items.TORAGS_HAMMERS_50,
                Items.TORAGS_HAMMERS_75,
                Items.TORAGS_HAMMERS_100,
            ) &&
            player.hasEquipped(
                EquipmentType.CHEST,
                Items.TORAGS_PLATEBODY,
                Items.TORAGS_PLATEBODY_25,
                Items.TORAGS_PLATEBODY_50,
                Items.TORAGS_PLATEBODY_75,
                Items.TORAGS_PLATEBODY_100,
            ) &&
            player.hasEquipped(
                EquipmentType.LEGS,
                Items.TORAGS_PLATELEGS,
                Items.TORAGS_PLATELEGS_25,
                Items.TORAGS_PLATELEGS_50,
                Items.TORAGS_PLATELEGS_75,
                Items.TORAGS_PLATELEGS_100,
            )
    }
}
