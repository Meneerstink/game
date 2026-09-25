package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * Zamorak Godsword ("Ice Cleave") - real mechanic (OSRS Wiki, verified): 50% energy, doubled
 * accuracy, +10% max hit, single hit, on-hit freezes the target for 32 ticks (same duration as
 * Ice Barrage).
 *
 * Audit I-03: the freeze uses the shared freeze() extension (FROZEN_TIMER plus freeze immunity), applied when the
 * hit lands.
 */
// Special look: animation 7070 + graphic 1221 (Novite PlayerCombat case 11700; Void `ice_cleave_special` = 1221), sound 3865 (Void).
/** Audit I-03: OSRS Ice Cleave freeze (same length as Ice Barrage). */
val ZGS_FREEZE_TICKS = 32

SpecialAttacks.register(50, Items.ZAMORAK_GODSWORD, Items.ZAMORAK_GODSWORD_OR) {
    // The (or) godsword plays the OSRS ornate special (gameval ZGS_SPECIAL_ORNATE_PLAYER, imported); the 667 godsword keeps its 667 special.
    player.animate(if (player.getEquipment(EquipmentType.WEAPON)?.id == Items.ZAMORAK_GODSWORD_OR) gg.rsmod.plugins.content.items.osrs.OsrsSeq.ZGS_SPECIAL_ORNATE_PLAYER else 7070)
    player.graphic(1221)
    // The 667 special sequence 7070 carries its own frame sound (cache: vorbis 6820, radius 10); only the silent imported ornate sequence needs the server cue.
    if (player.getEquipment(EquipmentType.WEAPON)?.id == Items.ZAMORAK_GODSWORD_OR) player.playSound(3865)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.10)
    // Audit C-04: godsword specials roll against the target's slash defence, whatever style is selected.
    val accuracy = MeleeCombatFormula.getAccuracyAgainst(player, target, specialAttackMultiplier = 2.0, defenceStyle = gg.rsmod.game.model.combat.StyleType.SLASH)
    val landHit = accuracy >= world.randomDouble()
    val victim = target
    val pawnHit =
        player.dealHit(
            target = victim,
            maxHit = maxHit,
            landHit = landHit,
            delay = 1,
            hitType = HitType.MELEE,
        )
    // Audit I-03: a successful Ice Cleave freezes the target for 32 ticks when the hit lands (graphic 2104, Void `ice_cleave_impact`).
    if (landHit) {
        pawnHit.hit.addAction {
            if (!victim.isDead() && victim.freeze(ZGS_FREEZE_TICKS) { if (victim is Player) victim.message("You have been frozen.") }) {
                victim.graphic(2104)
            }
        }
    }
}
