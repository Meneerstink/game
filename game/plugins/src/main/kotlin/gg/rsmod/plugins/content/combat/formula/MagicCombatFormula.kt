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
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * @author Tom <rspsmods@gmail.com>
 */
object MagicCombatFormula : CombatFormula {
    private val MAGE_VOID =
        intArrayOf(Items.VOID_MAGE_HELM, Items.VOID_KNIGHT_TOP, Items.VOID_KNIGHT_ROBE, Items.VOID_KNIGHT_GLOVES)

    private val BOLT_SPELLS =
        enumSetOf(CombatSpell.WIND_BOLT, CombatSpell.WATER_BOLT, CombatSpell.EARTH_BOLT, CombatSpell.FIRE_BOLT)

    private val FIRE_SPELLS =
        enumSetOf(
            CombatSpell.FIRE_STRIKE,
            CombatSpell.FIRE_BOLT,
            CombatSpell.FIRE_BLAST,
            CombatSpell.FIRE_WAVE,
            CombatSpell.FIRE_SURGE,
        )

    override fun getAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Double {
        // Check if the target has the prayer protection and the attacker is not a player
        if (target.isProtectedFrom(CombatClass.MAGIC) && pawn !is Player) {
            return 0.0 // Hits will never land
        }
        return getUnprotectedAccuracy(pawn, target)
    }

    /**
     * Accuracy roll without the protection-prayer short circuit, for npcs whose magic attacks are
     * only partially blocked by Protect from Magic (e.g. the Corporeal Beast).
     */
    fun getUnprotectedAccuracy(
        pawn: Pawn,
        target: Pawn,
    ): Double {
        val attack = getAttackRoll(pawn, target)
        val defence =
            if (target is Player) {
                getDefenceRoll(target)
            } else if (target is Npc) {
                getDefenceRoll(pawn, target)
            } else {
                throw IllegalArgumentException("Unhandled pawn.")
            }

        val accuracy: Double
        if (attack > defence) {
            accuracy = 1.0 - (defence + 2.0) / (2.0 * (attack + 1.0))
        } else {
            accuracy = attack / (2.0 * (defence + 1))
        }
        return accuracy
    }

    override fun getMaxHit(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        val spell = pawn.attr[Combat.CASTING_SPELL]
        var hit = spell?.maxHit?.toDouble() ?: 1.0
        if (pawn is Player) {
            if (pawn.hasEquipped(EquipmentType.GLOVES, Items.CHAOS_GAUNTLETS) &&
                spell != null &&
                spell in BOLT_SPELLS
            ) {
                hit += 3
            }

            // Charge spell: god spells hit up to 30 while charged and wearing the matching cape.
            if (spell != null && pawn.attr[gg.rsmod.game.model.attr.GOD_SPELL_CHARGE_ATTR] == true) {
                val cape = pawn.getEquipment(EquipmentType.CAPE)?.id
                val charged =
                    (spell == CombatSpell.SARADOMIN_STRIKE && cape == Items.SARADOMIN_CAPE) ||
                        (spell == CombatSpell.CLAWS_OF_GUTHIX && cape == Items.GUTHIX_CAPE) ||
                        (spell == CombatSpell.FLAMES_OF_ZAMORAK && cape == Items.ZAMORAK_CAPE)
                if (charged) {
                    hit += 10
                }
            }

            var multiplier = 1.0 + (pawn.getMagicDamageBonus() / 100.0)

            hit *= multiplier
            hit = Math.floor(hit)
        } else if (pawn is Npc) {
            val spell = pawn.attr[Combat.CASTING_SPELL]
            if (spell == null) {
                val a = getEffectiveAttackLevel(pawn)
                val b = 1.0 + (pawn.getMagicDamageBonus() / 100.0)

                var base = 0.5 + a * (b + 64.0) / 640.0
                hit = Math.floor(base)
            }
            else {
                val multiplier = 1.0 + (pawn.getMagicDamageBonus() / 100.0)
                hit *= multiplier
                hit = Math.floor(hit)
            }
        }

        hit *= getDamageDealMultiplier(pawn)
        hit = Math.floor(hit)

        return hit
    }

    private fun getAttackRoll(
        pawn: Pawn,
        target: Pawn,
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
            maxRoll = applyAttackSpecials(pawn, target, maxRoll)
        }
        return maxRoll.toInt()
    }

    private fun getDefenceRoll(
        pawn: Pawn,
        target: Npc,
    ): Int {
        // S2, 2026-09-03: this must read the TARGET npc's effective Defence level, not the
        // attacker's - `getEffectiveDefenceLevel(npc: Npc)` below existed but was never
        // called by anything before this fix, which is itself evidence the target-based
        // path was never wired in. See RSPS_DECISIONS.md.
        val a = getEffectiveDefenceLevel(target)
        val b = getEquipmentDefenceBonus(target)

        val maxRoll = a * (b + 64.0)
        return maxRoll.toInt()
    }

    public fun getDefenceRoll(target: Player): Int {
        var effectiveLvl = getEffectiveDefenceLevel(target)

        effectiveLvl *= 0.3
        effectiveLvl = Math.floor(effectiveLvl)

        var magicLvl = target.skills.getCurrentLevel(Skills.MAGIC).toDouble()
        magicLvl *= getPrayerAttackMultiplier(target)
        magicLvl = Math.floor(magicLvl)

        magicLvl *= 0.7
        magicLvl = Math.floor(magicLvl)

        val a = Math.floor(effectiveLvl + magicLvl).toInt()
        val b = getEquipmentDefenceBonus(target)

        val maxRoll = a * (b + 64.0)
        return maxRoll.toInt()
    }

    private fun applyAttackSpecials(
        player: Player,
        target: Pawn,
        base: Double,
    ): Double {
        var hit = base

        hit *= TargetModifiers.equipmentMultiplier(player, target)
        hit = Math.floor(hit)

        return hit
    }

    private fun getEffectiveAttackLevel(player: Player): Double {
        var effectiveLevel = Math.floor(player.skills.getCurrentLevel(Skills.MAGIC) * getPrayerAttackMultiplier(player))

        effectiveLevel += 8.0

        if (player.hasEquipped(MAGE_VOID)) {
            effectiveLevel *= 1.45
            effectiveLevel = Math.floor(effectiveLevel)
        }

        return Math.floor(effectiveLevel)
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

        return Math.floor(effectiveLevel)
    }

    private fun getEffectiveAttackLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.MAGIC).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEffectiveDefenceLevel(npc: Npc): Double {
        var effectiveLevel = npc.stats.getCurrentLevel(NpcSkills.DEFENCE).toDouble()
        effectiveLevel += 8
        return effectiveLevel
    }

    private fun getEquipmentAttackBonus(pawn: Pawn): Double {
        return pawn.getBonus(BonusSlot.ATTACK_MAGIC).toDouble()
    }

    private fun getEquipmentDefenceBonus(target: Pawn): Double {
        return target.getBonus(BonusSlot.DEFENCE_MAGIC).toDouble()
    }

    private fun getPrayerAttackMultiplier(player: Player): Double =
        when {
            Prayers.isActive(player, Prayer.MYSTIC_WILL) -> 1.05
            Prayers.isActive(player, Prayer.MYSTIC_LORE) -> 1.10
            Prayers.isActive(player, Prayer.MYSTIC_MIGHT) -> 1.15
            Prayers.isActive(player, Prayer.AUGURY) -> 1.25
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

    private fun getDamageDealMultiplier(pawn: Pawn): Double = pawn.attr[Combat.DAMAGE_DEAL_MULTIPLIER] ?: 1.0
}
