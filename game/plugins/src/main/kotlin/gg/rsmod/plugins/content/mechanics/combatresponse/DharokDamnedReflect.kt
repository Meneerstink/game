package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.entity.Pawn

/**
 * Dharok the Wretched's full set + amulet of the damned: sourced from the OSRS Wiki "Amulet
 * of the damned" page - a 25% chance per landed hit to recoil 15% of the damage taken, which
 * stacks with Vengeance and Ring of recoil. Requires the full four-piece Dharok's set *and*
 * the amulet of the damned equipped together (the amulet alone grants only amulet-of-glory-
 * equivalent stat bonuses; the reflect is a Dharok's set bonus, not an amulet property).
 *
 * BLOCKED (item id, not formula): "amulet of the damned" has no item id constant anywhere in
 * this codebase - grep-confirmed zero hits for "DAMNED" in Items.kt. The only trace of it is
 * a commented-out, non-compiling reference in `MeleeCombatFormula.kt`
 * (`Items.AMULET_OF_THE_DAMNED_FULL`, under a `TODO: find if there's any defence specials
 * for 667`) which was never a real constant. Per "never guess IDs", this stays a documented
 * no-op until the item is sourced (owner-supplied id or cache access) - see
 * RSPS_DECISIONS.md. `Dharok`'s existing full-set check already lives in
 * `MeleeCombatFormula.isWearingDharok` (private); once the amulet id exists, wiring this up
 * is: make that check reusable, add `target.hasEquipped(EquipmentType.AMULET,
 * Items.AMULET_OF_THE_DAMNED)`, then `if (isWearingDharok(target) && hasAmulet &&
 * target.world.percentChance(25.0))` reflect `floor(damage * 0.15)` the same way
 * [RingOfRecoil]/[Vengeance] do.
 */
object DharokDamnedReflect {
    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        // No-op - see class KDoc. Left as an explicit function (rather than omitted
        // entirely) so DamageResponse's call order documents where this slots in.
    }
}
