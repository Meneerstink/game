package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.HitType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.formula.CombatFormula
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula

/**
 * PROJECT48 SIGILS - the one file the combat code calls (a single line per hook, not 65). The hooks sit in `Pawn.dealHit`
 * (content/combat/PawnExt.kt): [accuracy] and [maxHit] where the formula is rolled, [modifyDamage] on the rolled damage (the attacker's
 * damage sigils AND the target's damage-reduction sigils) and [afterHit] once the hit exists (heals, curses, recoil). Every function
 * returns its input unchanged when no sigil applies, so a player without sigils pays only the [any] check.
 * The per-sigil effects are added below, one block per sigil row (S01..S65), each commented with its wiki sentence.
 */
object SigilCombat {
    enum class Style { MELEE, RANGED, MAGIC, OTHER }

    /** Whether any sigil is attuned at all (the cheap early exit of every hook). */
    fun any(player: Player): Boolean = Sigils.attuned(player).isNotEmpty()

    fun styleOf(formula: CombatFormula): Style =
        when (formula) {
            is MeleeCombatFormula -> Style.MELEE
            is RangedCombatFormula -> Style.RANGED
            is MagicCombatFormula -> Style.MAGIC
            else -> Style.OTHER
        }

    fun styleOf(type: HitType): Style =
        when (type) {
            HitType.MELEE -> Style.MELEE
            HitType.RANGE -> Style.RANGED
            HitType.MAGIC -> Style.MAGIC
            else -> Style.OTHER
        }

    /** Hit chance (0..1) of [attacker] against [target] after the sigils of the attacker. */
    fun accuracy(
        attacker: Pawn,
        target: Pawn,
        accuracy: Double,
        formula: CombatFormula,
    ): Double {
        if (attacker !is Player || !any(attacker)) return accuracy
        var factor = 1.0
        // S02 Deft Strikes (+20% vs non-player enemies), S24 Precision (+50% vs all monsters), S37 Meticulousness (+75% vs NPCs, the
        // higher-tier variant of Deft Strikes): they do not stack, the strongest one counts.
        if (target is Npc) {
            if (Sigils.effective(attacker, Sigil.DEFT_STRIKES)) factor = maxOf(factor, 1.20)
            if (Sigils.effective(attacker, Sigil.PRECISION)) factor = maxOf(factor, 1.50)
            if (Sigils.effective(attacker, Sigil.METICULOUSNESS)) factor = maxOf(factor, 1.75)
        }
        // S20 Barrows: +40% accuracy with Barrows weaponry against players and NPCs.
        if (Sigils.effective(attacker, Sigil.BARROWS) && wieldsBarrowsWeapon(attacker)) factor *= 1.40
        // S29 Adroit: +1% accuracy for each hitpoint the player is missing (players and NPCs).
        if (Sigils.effective(attacker, Sigil.THE_ADROIT)) {
            val missing = (attacker.getMaximumLifepoints() - attacker.getCurrentLifepoints()).coerceAtLeast(0)
            factor *= 1.0 + missing / 100.0
        }
        return if (factor == 1.0) accuracy else scaleRoll(accuracy, factor)
    }

    /**
     * The hit chance after the attack roll is multiplied by [factor] (OSRS: chance = 1 - (D+2)/(2(A+1)) when A > D, else A/(2(D+1))).
     * Only the chance is known here, so the roll ratio is rebuilt from it: exact for the large rolls of real fights.
     */
    fun scaleRoll(
        chance: Double,
        factor: Double,
    ): Double {
        val p = chance.coerceIn(0.0, 1.0)
        if (p <= 0.0) return 0.0
        return if (p > 0.5) {
            1.0 - (1.0 - p) / factor
        } else if (p * factor <= 0.5) {
            p * factor
        } else {
            1.0 - 1.0 / (4.0 * p * factor)
        }.coerceIn(0.0, 1.0)
    }

    /** Extra attack-bonus points of the attuned sigils for [slot] (added where the formula reads the equipment attack bonus). */
    fun attackBonus(
        pawn: Pawn,
        slot: BonusSlot,
    ): Int {
        if (pawn !is Player || !any(pawn)) return 0
        var bonus = 0
        when (slot) {
            // S15 Meticulous Mage: magic accuracy +40.
            BonusSlot.ATTACK_MAGIC -> if (Sigils.effective(pawn, Sigil.THE_METICULOUS_MAGE)) bonus += 40
            // S17 Rigorous Ranger: ranged accuracy +20.
            BonusSlot.ATTACK_RANGED -> if (Sigils.effective(pawn, Sigil.THE_RIGOROUS_RANGER)) bonus += 20
            // S51 Formidable Fighter: melee accuracy +30 in stab, slash and crush.
            BonusSlot.ATTACK_STAB, BonusSlot.ATTACK_SLASH, BonusSlot.ATTACK_CRUSH ->
                if (Sigils.effective(pawn, Sigil.THE_FORMIDABLE_FIGHTER)) bonus += 30
            else -> {}
        }
        return bonus
    }

    /** Extra defence-bonus points of the attuned sigils for [slot]: S23 Fortification +50, S40 Rampart +100 (no stacking). */
    fun defenceBonus(
        pawn: Pawn,
        slot: BonusSlot,
    ): Int {
        if (pawn !is Player || !any(pawn)) return 0
        return when (slot) {
            BonusSlot.DEFENCE_STAB, BonusSlot.DEFENCE_SLASH, BonusSlot.DEFENCE_CRUSH, BonusSlot.DEFENCE_RANGED ->
                if (Sigils.effective(pawn, Sigil.THE_RAMPART)) 100 else if (Sigils.effective(pawn, Sigil.FORTIFICATION)) 50 else 0
            else -> 0
        }
    }

    /** Max hit of [attacker] against [target] after the sigils of the attacker. */
    fun maxHit(
        attacker: Pawn,
        target: Pawn,
        maxHit: Double,
        formula: CombatFormula,
    ): Double {
        if (attacker !is Player || !any(attacker)) return maxHit
        return maxHit
    }

    /** The rolled [damage] of a hit after the sigils of the attacker (damage dealt) and of the target (damage taken). */
    fun modifyDamage(
        attacker: Pawn,
        target: Pawn,
        damage: Double,
        type: HitType,
        landed: Boolean,
    ): Double {
        var result = damage
        if (attacker is Player && any(attacker)) result = dealt(attacker, target, result, type, landed)
        if (target is Player && any(target)) result = taken(target, attacker, result, type)
        return result
    }

    private fun dealt(
        attacker: Player,
        target: Pawn,
        damage: Double,
        type: HitType,
        landed: Boolean,
    ): Double {
        if (!landed) return damage
        var result = damage
        // S01 Consistency: every attack does +1 damage to the target.
        if (Sigils.effective(attacker, Sigil.CONSISTENCY)) result += 1.0
        // S36 Aggression: all attacks deal 10% more damage (a Rampage bonus does not stack with it).
        if (Sigils.effective(attacker, Sigil.AGGRESSION)) result = Math.round(result * 1.10).toDouble()
        return result
    }

    private fun taken(
        target: Player,
        attacker: Pawn,
        damage: Double,
        type: HitType,
    ): Double {
        if (damage <= 0.0) return damage
        var result = damage
        // S39 Titanium (-60%) and S07 Resistance (-25%): all attacks from monsters; they do not stack (Titanium wins).
        if (attacker is Npc) {
            if (Sigils.effective(target, Sigil.TITANIUM)) {
                result = Math.floor(result * 0.40)
            } else if (Sigils.effective(target, Sigil.RESISTANCE)) {
                result = Math.floor(result * 0.75)
            }
        }
        // S36 Aggression: you take 5% more damage from all sources.
        if (Sigils.effective(target, Sigil.AGGRESSION)) result = Math.round(result * 1.05).toDouble()
        return result
    }

    /** After the hit is created: heals, curses and recoil of the attacker's and the target's sigils. */
    fun afterHit(
        attacker: Pawn,
        target: Pawn,
        hit: gg.rsmod.game.model.Hit,
        type: HitType,
        landed: Boolean,
    ) {
        if (attacker is Player && any(attacker)) attackerAfterHit(attacker, target, hit, type, landed)
        if (target is Player && any(target)) targetAfterHit(target, attacker, hit, type, landed)
    }

    private fun attackerAfterHit(
        attacker: Player,
        target: Pawn,
        hit: gg.rsmod.game.model.Hit,
        type: HitType,
        landed: Boolean,
    ) {
        if (!landed) return
        // S27 Restoration: on a successful hit restore 10% of the damage dealt (rounded up) to hitpoints - players and NPCs, not special
        // attacks (multi-target attacks are not told apart from single hits here: see the dossier).
        if (Sigils.effective(attacker, Sigil.RESTORATION) &&
            attacker.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] != true
        ) {
            hit.addAction {
                val dealt = hitmarks.sumOf { it.damage }
                if (dealt > 0) attacker.heal(Math.ceil(dealt / 10.0).toInt())
            }
        }
    }

    private fun targetAfterHit(
        target: Player,
        attacker: Pawn,
        hit: gg.rsmod.game.model.Hit,
        type: HitType,
        landed: Boolean,
    ) {
    }

    private val BARROWS_WEAPONS = listOf("dharok's greataxe", "verac's flail", "torag's hammers", "guthan's warspear", "karil's crossbow", "ahrim's staff")

    private fun wieldsBarrowsWeapon(player: Player): Boolean {
        val id = player.getEquipment(EquipmentType.WEAPON)?.id ?: return false
        val name = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name.lowercase()
        return BARROWS_WEAPONS.any { name.startsWith(it) }
    }

    /** Weapons that the Gunslinger does not speed up (wiki: the Crystal bow, Bow of Faerdhinen and Morrigan's throwing axe). */
    private fun gunslingerExcluded(
        pawn: Player,
        weaponId: Int?,
    ): Boolean {
        if (weaponId == null) return false
        val name = pawn.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, weaponId).name.lowercase()
        return name.contains("crystal bow") || name.contains("faerdhinen") || name.contains("morrigan")
    }

    /** S35 Swashbuckler (melee) and S30 Gunslinger (ranged): one tick faster for a base attack speed above 4. */
    fun baseSpeed(
        pawn: Pawn,
        weaponId: Int?,
        speed: Int,
        combatClass: gg.rsmod.game.model.combat.CombatClass?,
    ): Int {
        if (pawn !is Player || speed <= 4 || !any(pawn)) return speed
        return when (combatClass) {
            gg.rsmod.game.model.combat.CombatClass.MELEE -> if (Sigils.effective(pawn, Sigil.THE_SWASHBUCKLER)) speed - 1 else speed
            gg.rsmod.game.model.combat.CombatClass.RANGED ->
                if (Sigils.effective(pawn, Sigil.THE_GUNSLINGER) && !gunslingerExcluded(pawn, weaponId)) speed - 1 else speed
            else -> speed
        }
    }

    /** S19 Arcane Swiftness: one tick faster for a magic weapon with a base attack speed above 4. */
    fun magicSpeed(
        pawn: Pawn,
        speed: Int,
    ): Int = if (pawn is Player && speed > 4 && Sigils.effective(pawn, Sigil.ARCANE_SWIFTNESS)) speed - 1 else speed

    /** True when [pawn] is an npc (sigils such as Deft Strikes only work against non-player enemies). */
    fun isNpc(pawn: Pawn): Boolean = pawn is Npc
}
