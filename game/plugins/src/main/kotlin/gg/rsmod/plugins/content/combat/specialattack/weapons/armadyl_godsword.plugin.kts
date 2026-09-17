package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * R04.10/R06.9: Armadyl Godsword special - 50% energy, +25% accuracy / +10% max hit, a single
 * hit. Real, stable, revision-independent AGS mechanic (no healing/draining side effect, unlike
 * Bandos/Saradomin/Zamorak/Guthix's variants - those are NOT implemented this pass, only this
 * one, to avoid guessing the others' exact numbers under time pressure).
 *
 * Special look (owner report 2026-09-14 "godswords have no special animations"): animation 11989 and graphic 2113 from the
 * Novite 667 donor (`PlayerCombat` case 11694), graphic confirmed by the Void donor (`the_judgement_special` = 2113); sound
 * 3865 from the Void donor (`godwars_godsword_special_attack`). LIVE/AV PENDING.
 */
SpecialAttacks.register(50, Items.ARMADYL_GODSWORD, Items.ARMADYL_GODSWORD_OR) {
    // The (or) godsword plays the OSRS ornate special (gameval AGS_SPECIAL_ORNATE_PLAYER, imported); the 667 godsword keeps its 667 special.
    player.animate(if (player.getEquipment(EquipmentType.WEAPON)?.id == Items.ARMADYL_GODSWORD_OR) gg.rsmod.plugins.content.items.osrs.OsrsSeq.AGS_SPECIAL_ORNATE_PLAYER else 11989)
    player.graphic(2113)
    player.playSound(3865)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.10)
    val accuracy = MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 1.25)
    val landHit = accuracy >= world.randomDouble()
    player.dealHit(
        target = target,
        maxHit = maxHit,
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )
}
