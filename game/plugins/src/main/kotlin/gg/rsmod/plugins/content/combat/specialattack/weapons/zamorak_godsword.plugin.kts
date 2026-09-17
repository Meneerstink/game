package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * Zamorak Godsword ("Ice Cleave") - real mechanic (OSRS Wiki, verified): 50% energy, doubled
 * accuracy, +10% max hit, single hit, on-hit freezes the target for 32 ticks (same duration as
 * Ice Barrage).
 *
 * The freeze effect is NOT implemented here: no freeze/immobilize mechanic (movement blocked,
 * attacking still allowed) exists anywhere in this codebase - the only related timer is
 * STUN_TIMER (game/model/timer/Timers.kt), which blocks movement AND attacking, a different
 * effect. Inventing a freeze system as an unplanned side effect of a weapon special would risk a
 * half-correct, unverified mechanic; the damage/accuracy half of this special (its real
 * substance) is implemented and correct. Mirrors the same disclose-don't-guess precedent already
 * used in armadyl_godsword.plugin.kts.
 */
// Special look: animation 7070 + graphic 1221 (Novite PlayerCombat case 11700; Void `ice_cleave_special` = 1221), sound 3865 (Void).
// Not added: the freeze impact graphic 2104 (Void `ice_cleave_impact`) - needs the freeze hook of this special, checked at build time.
SpecialAttacks.register(50, Items.ZAMORAK_GODSWORD, Items.ZAMORAK_GODSWORD_OR) {
    // The (or) godsword plays the OSRS ornate special (gameval ZGS_SPECIAL_ORNATE_PLAYER, imported); the 667 godsword keeps its 667 special.
    player.animate(if (player.getEquipment(EquipmentType.WEAPON)?.id == Items.ZAMORAK_GODSWORD_OR) gg.rsmod.plugins.content.items.osrs.OsrsSeq.ZGS_SPECIAL_ORNATE_PLAYER else 7070)
    player.graphic(1221)
    player.playSound(3865)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.10)
    val accuracy = MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 2.0)
    val landHit = accuracy >= world.randomDouble()
    player.dealHit(
        target = target,
        maxHit = maxHit,
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )
}
