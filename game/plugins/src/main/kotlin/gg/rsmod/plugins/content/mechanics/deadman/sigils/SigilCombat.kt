package gg.rsmod.plugins.content.mechanics.deadman.sigils

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
        return accuracy
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
    ): Double = damage

    private fun taken(
        target: Player,
        attacker: Pawn,
        damage: Double,
        type: HitType,
    ): Double = damage

    /** After the hit is created: heals, curses and recoil of the attacker's and the target's sigils. */
    fun afterHit(
        attacker: Pawn,
        target: Pawn,
        hit: Any,
        type: HitType,
        landed: Boolean,
    ) {
        if (attacker is Player && any(attacker)) attackerAfterHit(attacker, target, type, landed)
        if (target is Player && any(target)) targetAfterHit(target, attacker, type, landed)
    }

    private fun attackerAfterHit(
        attacker: Player,
        target: Pawn,
        type: HitType,
        landed: Boolean,
    ) {
    }

    private fun targetAfterHit(
        target: Player,
        attacker: Pawn,
        type: HitType,
        landed: Boolean,
    ) {
    }

    /** True when [pawn] is an npc (sigils such as Deft Strikes only work against non-player enemies). */
    fun isNpc(pawn: Pawn): Boolean = pawn is Npc
}
