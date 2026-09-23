package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.attr.REFLECTING_DAMAGE_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses

/**
 * Generic, deterministic damage-response dispatcher for reflect/recoil-style effects that
 * trigger off *incoming* damage a [Pawn] just took, striking back at whoever dealt it:
 * Deflect curses, Vengeance, Ring of recoil/suffering, and the Dharok + amulet of the damned
 * set bonus (RSPS_DEFINITIEF_MASTERPLAN.md section 33, "Recoil / Vengeance / reflect").
 * Sourced from the OSRS Wiki pages "Ring of recoil", "Vengeance" and "Amulet of the damned"
 * - see RSPS_DECISIONS.md for the full sourcing note. Stacking between all of these IS
 * confirmed authentic (a fully-decked-out player can, in the wiki's own words, "rebound
 * 100% of damage received in a single hit" by combining Dharok's + amulet of the damned +
 * Vengeance + ring of recoil).
 *
 * Called exactly once per landed hit, from the same
 * [gg.rsmod.plugins.content.combat.PawnExt.dealHit] action block that already drives
 * [AncientCurses.onDamageDealt] - this *is* the one deterministic order this file exists to
 * guarantee: every response source below is evaluated in the same fixed sequence, off the
 * same original [onIncomingHit] `damage` parameter, never off each other's output. Stacking
 * therefore never compounds and never depends on iteration order of some mutable collection
 * of registered sources.
 *
 * Recursion / duplicate-damage safety: every response strikes back using the raw
 * [gg.rsmod.plugins.api.ext.hit] primitive (the same one Deflect already used before this
 * pass), which does not re-enter [gg.rsmod.plugins.content.combat.PawnExt.dealHit] and
 * therefore can never re-trigger this dispatcher - reflected damage cannot itself be
 * reflected. [REFLECTING_DAMAGE_ATTR] is a defensive, belt-and-braces reentrancy guard on
 * top of that structural guarantee, in case a future change ever routes a reflected hit back
 * through `dealHit`.
 *
 * Poison/venom/disease damage never reaches this dispatcher at all, because those tick
 * timers apply damage via the same raw `.hit()` primitive directly, not through `dealHit` -
 * which also happens to match the sourced rule that Vengeance "does not affect poison damage".
 */
object DamageResponse {
    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        style: CombatClass,
        damage: Int,
        deflectDamage: Int = damage,
    ) {
        if ((damage <= 0 && deflectDamage <= 0) || attacker.isDead() || target.isDead()) return
        if (target.attr[REFLECTING_DAMAGE_ATTR] == true) return

        target.attr[REFLECTING_DAMAGE_ATTR] = true
        try {
            // 1) Deflect curses - existing implementation, logic unchanged, just moved one
            //    call site up so it participates in this single deterministic order.
            AncientCurses.onIncomingHit(attacker, target, style, deflectDamage)
            if (damage <= 0) return
            //    RCV-011: the same deflect for npcs showing a Deflect overhead (Nex), see NpcDeflect.
            NpcDeflect.onIncomingHit(attacker, target, style, damage)
            // 2) Vengeance - single-use, 75% of damage, consumes itself on trigger.
            Vengeance.onIncomingHit(attacker, target, damage)
            // 3) Ring of recoil / Ring of suffering - floor(10% damage) + 1, 40-damage
            //    cumulative charge before shattering.
            RingOfRecoil.onIncomingHit(attacker, target, damage)
            // 4) Dharok + amulet of the damned - BLOCKED, see DharokDamnedReflect.kt.
            DharokDamnedReflect.onIncomingHit(attacker, target, damage)
        } finally {
            target.attr.remove(REFLECTING_DAMAGE_ATTR)
        }
    }
}
