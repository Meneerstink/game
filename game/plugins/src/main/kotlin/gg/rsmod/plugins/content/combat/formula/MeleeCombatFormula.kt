@file:Suppress("UNUSED_PARAMETER")

package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import kotlin.math.floor

/**
 * @author Tom <rspsmods@gmail.com>
 */
object MeleeCombatFormula : CombatFormula {
    /**
     * OSRS Wiki "Berserker necklace": +20 % damage only with obsidian weapons; the wiki DPS calculator's
     * `isWearingTzhaarWeapon` lists Tzhaar-ket-em, Tzhaar-ket-om (+ (t), absent here), Toktz-xil-ak, Toktz-xil-ek and
     * Toktz-mej-tal.
     */
    private val OBSIDIAN_MELEE_WEAPONS = intArrayOf(Items.TOKTZXILAK, Items.TZHAARKETOM, Items.TZHAARKETEM, Items.TOKTZXILEK, Items.TOKTZMEJTAL)

    override fun getAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Double = accuracy(pawn, target, specialAttackMultiplier, defenceStyle = null)

    /**
     * Accuracy against a forced defence style. The wiki DPS calculator (weirdgloop/osrs-dps-calc `PlayerVsNPCCalc.ts`)
     * sets `defenceStyle = 'slash'` for the special attacks of the Dragon claws/dagger/halberd/longsword/scimitar, Crystal
     * halberd, Abyssal dagger, Saradomin sword, Arkan blade and every godsword.
     */
    fun getAccuracyAgainst(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        defenceStyle: StyleType,
    ): Double = accuracy(pawn, target, specialAttackMultiplier, defenceStyle)

    private fun accuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        defenceStyle: StyleType?,
    ): Double {
        // Check if the target has the prayer protection and the attacker is not a player
        if ((target.isProtectedFrom(CombatClass.MELEE) && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.deflects(target, CombatClass.MELEE)) && pawn !is Player) {
            return 0.0 // Hits will never land
        }
        val attack = getAttackRoll(pawn, target, specialAttackMultiplier)
        val defence =
            when {
                (pawn is Npc && target is Player) && pawn.combatDef.attackStyleType == StyleType.MAGIC_MELEE ->
                    MagicCombatFormula
                        .getDefenceRoll(
                            target,
                        )
                else -> getDefenceRoll(pawn, target, defenceStyle)
            }

        val accuracy: Double =
            if (pawn is Player && gg.rsmod.plugins.content.items.osrs.OsmumtensFang.usesFangAccuracy(pawn)) {
                // Osmumten's fang, stab styles: its own hit chance formula (OsmumtensFang).
                gg.rsmod.plugins.content.items.osrs.OsmumtensFang.hitChance(attack.toDouble(), defence.toDouble())
            } else if (attack > defence) {
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
                getEffectiveStrengthLevel(pawn, target)
            } else if (pawn is Npc) {
                getEffectiveStrengthLevel(pawn, target)
            } else {
                0.0
            }
        val b = getEquipmentStrengthBonus(pawn)

        // OSRS Wiki "Maximum melee hit": Max hit = ⌊0.5 + Effective Strength × (Strength bonus + 64) / 640⌋.
        var base = floor(0.5 + a * (b + 64.0) / 640.0)
        if (pawn is Player) {
            base = applyStrengthSpecials(pawn, target, base, specialAttackMultiplier, specialPassiveMultiplier)
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
                getEffectiveAttackLevel(pawn, target)
            } else if (pawn is Npc) {
                getEffectiveAttackLevel(pawn, target)
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
        defenceStyle: StyleType? = null,
    ): Int {
        val a =
            if (target is Player) {
                getEffectiveDefenceLevel(target)
            } else if (target is Npc) {
                getEffectiveDefenceLevel(target)
            } else {
                0.0
            }
        val b = getEquipmentDefenceBonus(pawn, target, defenceStyle)

        var maxRoll = a * (b + 64.0)
        maxRoll = applyDefenceSpecials(target, maxRoll)
        return maxRoll.toInt()
    }

    private fun applyStrengthSpecials(
        player: Player,
        target: Pawn,
        base: Double,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        // OSRS Wiki "Maximum melee hit" step three: ⌊⌊Base⌋ × Special Bonus⌋ - every multiplier is floored before the next.
        var hit = base

        hit = floor(hit * TargetModifiers.equipmentMultiplier(player, target))
        hit = TargetModifiers.addPercent(hit, TargetModifiers.meleeDemonbanePercent(player, target))
        // Dragon hunter lance: multiplicative with the target-specific gear bonus, its own floored step.
        hit = floor(hit * TargetModifiers.meleeDragonbaneDamage(player, target))

        hit =
            if (specialPassiveMultiplier == 1.0) {
                applyPassiveMultiplier(player, target, hit)
            } else {
                floor(hit * specialPassiveMultiplier)
            }

        hit = floor(hit * specialAttackMultiplier)

        // "Damage per second/Melee": against a player praying Protect from Melee, multiply by 6/10.
        if ((target.isProtectedFrom(CombatClass.MELEE) && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.deflects(target, CombatClass.MELEE))) {
            hit = floor(hit * 0.6)
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
        // Attack roll × target-specific gear bonus, floored, then the special attack accuracy multiplier.
        var hit = floor(base * TargetModifiers.equipmentMultiplier(player, target))
        hit = TargetModifiers.addPercent(hit, TargetModifiers.meleeDemonbanePercent(player, target))
        hit = floor(hit * TargetModifiers.meleeDragonbaneAccuracy(player, target))
        hit = floor(hit * specialAttackMultiplier)
        return hit
    }

    private fun applyDefenceSpecials(
        target: Pawn,
        base: Double,
    ): Double {
        var hit = base

        // TODO: find if there's any defence specials for 667
        /**if (target is Player && isWearingTorag(target) && target.hasEquipped(EquipmentType.AMULET, Items.AMULET_OF_THE_DAMNED_FULL)) {
         val lost = (target.getMaxHp() - target.getCurrentHp()) / 100.0
         val max = target.getMaxHp() / 100.0
         hit *= (1.0 + (lost * max))
         hit = Math.floor(hit)
         }**/

        return hit
    }

    private fun getEquipmentStrengthBonus(pawn: Pawn): Double =
        when (pawn) {
            is Player -> pawn.getStrengthBonus().toDouble()
            is Npc -> pawn.getStrengthBonus().toDouble()
            else -> throw IllegalArgumentException("Invalid pawn type. $pawn")
        }

    private fun getEquipmentAttackBonus(pawn: Pawn): Double {
        val bonus =
            when (val combatStyle = CombatConfigs.getCombatStyle(pawn)) {
                StyleType.STAB -> BonusSlot.ATTACK_STAB
                StyleType.SLASH -> BonusSlot.ATTACK_SLASH
                StyleType.CRUSH -> BonusSlot.ATTACK_CRUSH
                StyleType.MAGIC_MELEE -> BonusSlot.ATTACK_STAB
                else -> throw IllegalStateException("Invalid combat style. $combatStyle")
            }
        return pawn.getBonus(bonus).toDouble()
    }

    private fun getEquipmentDefenceBonus(
        pawn: Pawn,
        target: Pawn,
        defenceStyle: StyleType? = null,
    ): Double {
        val bonus =
            when (val combatStyle = defenceStyle ?: CombatConfigs.getCombatStyle(pawn)) {
                StyleType.STAB -> BonusSlot.DEFENCE_STAB
                StyleType.SLASH -> BonusSlot.DEFENCE_SLASH
                StyleType.CRUSH -> BonusSlot.DEFENCE_CRUSH
                else -> throw IllegalStateException("Invalid combat style. $combatStyle")
            }
        return target.getBonus(bonus).toDouble()
    }

    private fun getEffectiveStrengthLevel(player: Player, opponent: Pawn? = null): Double {
        var effectiveLevel = floor(player.skills.getCurrentLevel(Skills.STRENGTH) * getPrayerStrengthMultiplier(player, opponent) * AncientCurses.drainMultiplier(player, Skills.STRENGTH))

        effectiveLevel +=
            when (CombatConfigs.getAttackStyle(player)) {
                WeaponStyle.AGGRESSIVE -> 3.0
                WeaponStyle.CONTROLLED -> 1.0
                // S1, 2026-09-03: Accurate/Defensive (and any other style) give effective
                // Strength +0, not +1 - see RSPS_DECISIONS.md for the sourced fix. The
                // previous `else -> 1.0` silently over-applied a Controlled-sized bonus to
                // every non-Aggressive/Controlled style, most commonly Accurate, inflating
                // every melee max hit rolled under that style.
                else -> 0.0
            }

        effectiveLevel += 8.0

        // "Maximum melee hit": ⌊(⌊Strength × Prayer⌋ + Style + 8) × Void⌋.
        if (VoidKnight.wearing(player, VoidKnight.MELEE_HELMS)) {
            effectiveLevel = floor(effectiveLevel * 1.10)
        }

        return effectiveLevel
    }

    private fun getEffectiveAttackLevel(player: Player, opponent: Pawn? = null): Double {
        var effectiveLevel = floor(player.skills.getCurrentLevel(Skills.ATTACK) * getPrayerAttackMultiplier(player, opponent) * AncientCurses.drainMultiplier(player, Skills.ATTACK))

        effectiveLevel +=
            when (CombatConfigs.getAttackStyle(player)) {
                WeaponStyle.ACCURATE -> 3.0
                WeaponStyle.CONTROLLED -> 1.0
                else -> 0.0
            }

        effectiveLevel += 8.0

        if (VoidKnight.wearing(player, VoidKnight.MELEE_HELMS)) {
            effectiveLevel = floor(effectiveLevel * 1.10)
        }

        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(player: Player, opponent: Pawn? = null): Double {
        var effectiveLevel = floor(player.skills.getCurrentLevel(Skills.DEFENCE) * getPrayerDefenceMultiplier(player, opponent) * AncientCurses.drainMultiplier(player, Skills.DEFENCE))

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

    private fun getEffectiveStrengthLevel(npc: Npc, opponent: Pawn? = null): Double {
        var effectiveLevel = floor(npc.stats.getCurrentLevel(NpcSkills.STRENGTH) * AncientCurses.drainMultiplier(npc, Skills.STRENGTH))
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveAttackLevel(npc: Npc, opponent: Pawn? = null): Double {
        var effectiveLevel = floor(npc.stats.getCurrentLevel(NpcSkills.ATTACK) * AncientCurses.drainMultiplier(npc, Skills.ATTACK))
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(npc: Npc, opponent: Pawn? = null): Double {
        // "Damage per second/Melee": NPC defence roll = (Defence level + 9) × (style defence bonus + 64).
        var effectiveLevel = floor(npc.stats.getCurrentLevel(NpcSkills.DEFENCE) * AncientCurses.drainMultiplier(npc, Skills.DEFENCE))
        effectiveLevel += 9
        return effectiveLevel
    }

    private fun getPrayerStrengthMultiplier(player: Player, opponent: Pawn? = null): Double =
        when {
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.leechMultiplier(player, Skills.STRENGTH)
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.turmoilMultiplier(player, Skills.STRENGTH, opponent)
            Prayers.isActive(player, Prayer.BURST_OF_STRENGTH) -> 1.05
            Prayers.isActive(player, Prayer.SUPERHUMAN_STRENGTH) -> 1.10
            Prayers.isActive(player, Prayer.ULTIMATE_STRENGTH) -> 1.15
            Prayers.isActive(player, Prayer.CHIVALRY) -> 1.18
            Prayers.isActive(player, Prayer.PIETY) -> 1.23
            else -> 1.0
        }

    private fun getPrayerAttackMultiplier(player: Player, opponent: Pawn? = null): Double =
        when {
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.leechMultiplier(player, Skills.ATTACK)
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.turmoilMultiplier(player, Skills.ATTACK, opponent)
            Prayers.isActive(player, Prayer.CLARITY_OF_THOUGHT) -> 1.05
            Prayers.isActive(player, Prayer.IMPROVED_REFLEXES) -> 1.10
            Prayers.isActive(player, Prayer.INCREDIBLE_REFLEXES) -> 1.15
            Prayers.isActive(player, Prayer.CHIVALRY) -> 1.15
            Prayers.isActive(player, Prayer.PIETY) -> 1.20
            else -> 1.0
        }

    private fun getPrayerDefenceMultiplier(player: Player, opponent: Pawn? = null): Double =
        when {
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.getBook(player) == gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT && !gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.leechMultiplier(player, Skills.DEFENCE)
            gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.isTurmoilActive(player) ->
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.turmoilMultiplier(player, Skills.DEFENCE, opponent)
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
        pawn: Pawn,
        target: Pawn,
        base: Double,
    ): Double {
        if (pawn is Player) {
            val world = pawn.world
            val multiplier =
                when {
                    pawn.hasEquipped(EquipmentType.AMULET, Items.BERSERKER_NECKLACE) &&
                        pawn.hasEquipped(EquipmentType.WEAPON, *OBSIDIAN_MELEE_WEAPONS) -> 1.2
                    isWearingDharok(pawn) -> {
                        val lost = (pawn.getMaximumLifepoints() - pawn.getCurrentLifepoints()) / 100.0
                        val max = pawn.getMaximumLifepoints() / 100.0
                        1.0 + (lost * max)
                    }
                    pawn.hasEquipped(
                        EquipmentType.WEAPON,
                        Items.GADDERHAMMER,
                    ) &&
                        isShade(target) -> if (world.chance(1, 20)) 2.0 else 1.25
                    pawn.hasEquipped(EquipmentType.WEAPON, Items.KERIS, Items.KERIS_P) &&
                        // OSRS Wiki "Keris": "33% bonus damage against all kalphites and scabarites", 1/51 triple damage.
                        (isKalphite(target) || isScarab(target)) -> if (world.chance(1, 51)) 3.0 else 1.33
                    else -> 1.0
                }
            // Verac's "Defiler" (25 % guaranteed hit, +1 damage against monsters) is a proc on the hit roll, not a
            // max-hit bonus: it lives in MeleeCombatStrategy, never here.
            return floor(base * multiplier)
        }
        return base
    }

    private fun getDamageDealMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_DEAL_MULTIPLIER] ?: 1.0

    private fun getDamageTakeMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_TAKE_MULTIPLIER] ?: 1.0

    private fun isDemon(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.DEMON)
        }
        return false
    }

    private fun isShade(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.SHADE)
        }
        return false
    }

    private fun isKalphite(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.KALPHITE)
        }
        return false
    }

    private fun isScarab(pawn: Pawn): Boolean {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).isSpecies(NpcSpecies.SCARAB)
        }
        return false
    }

    private fun isWearingDharok(pawn: Pawn): Boolean {
        if (pawn.entityType.isPlayer) {
            val player = pawn as Player
            return player.hasEquipped(
                EquipmentType.HEAD,
                Items.DHAROKS_HELM,
                Items.DHAROKS_HELM_25,
                Items.DHAROKS_HELM_50,
                Items.DHAROKS_HELM_75,
                Items.DHAROKS_HELM_100,
            ) &&
                player.hasEquipped(
                    EquipmentType.WEAPON,
                    Items.DHAROKS_GREATAXE,
                    Items.DHAROKS_GREATAXE_25,
                    Items.DHAROKS_GREATAXE_50,
                    Items.DHAROKS_GREATAXE_75,
                    Items.DHAROKS_GREATAXE_100,
                ) &&
                player.hasEquipped(
                    EquipmentType.CHEST,
                    Items.DHAROKS_PLATEBODY,
                    Items.DHAROKS_PLATEBODY_25,
                    Items.DHAROKS_PLATEBODY_50,
                    Items.DHAROKS_PLATEBODY_75,
                    Items.DHAROKS_PLATEBODY_100,
                ) &&
                player.hasEquipped(
                    EquipmentType.LEGS,
                    Items.DHAROKS_PLATELEGS,
                    Items.DHAROKS_PLATELEGS_25,
                    Items.DHAROKS_PLATELEGS_50,
                    Items.DHAROKS_PLATELEGS_75,
                    Items.DHAROKS_PLATELEGS_100,
                )
        }
        return false
    }

    fun isWearingVerac(pawn: Pawn): Boolean {
        if (pawn.entityType.isPlayer) {
            val player = pawn as Player
            return player.hasEquipped(
                EquipmentType.HEAD,
                Items.VERACS_HELM,
                Items.VERACS_HELM_25,
                Items.VERACS_HELM_50,
                Items.VERACS_HELM_75,
                Items.VERACS_HELM_100,
            ) &&
                player.hasEquipped(
                    EquipmentType.WEAPON,
                    Items.VERACS_FLAIL,
                    Items.VERACS_FLAIL_25,
                    Items.VERACS_FLAIL_50,
                    Items.VERACS_FLAIL_75,
                    Items.VERACS_FLAIL_100,
                ) &&
                player.hasEquipped(
                    EquipmentType.CHEST,
                    Items.VERACS_BRASSARD,
                    Items.VERACS_BRASSARD_25,
                    Items.VERACS_BRASSARD_50,
                    Items.VERACS_BRASSARD_75,
                    Items.VERACS_BRASSARD_100,
                ) &&
                player.hasEquipped(
                    EquipmentType.LEGS,
                    Items.VERACS_PLATESKIRT,
                    Items.VERACS_PLATESKIRT_25,
                    Items.VERACS_PLATESKIRT_50,
                    Items.VERACS_PLATESKIRT_75,
                    Items.VERACS_PLATESKIRT_100,
                )
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
