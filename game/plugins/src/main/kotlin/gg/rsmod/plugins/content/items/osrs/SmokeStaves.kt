package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * OSRS-IMPORT Mystic smoke staff (OSRS Wiki "Mystic smoke staff" raw wikitext, fetched 2026-09-14): "requiring 40 Attack and 40
 * Magic to wield"; "provides unlimited amounts of air and fire runes"; "provides a 10% increase in accuracy and damage when
 * casting spells from the standard spellbook; this bonus also applies to curse spells" (8 October 2015); magic attack/defence
 * +14 (22 September 2021).
 * Stacking (wiki DPS calculator, citing Jagex): the accuracy is +10 percentage points of the additive magic accuracy bonus
 * (roll × (100 + bonus) / 100) and the damage +10 % of the additive magic damage bonus (`magicDmgBonus += 100`), both only for
 * standard-spellbook spells. This server has no other additive magic accuracy source, so the accuracy step is ×110/100.
 */
object SmokeStaves {
    const val ACCURACY_PERCENT = 10
    const val DAMAGE_BONUS = 0.10

    val STAVES: Set<Int> = setOf(Items.MYSTIC_SMOKE_STAFF)

    fun wielding(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id in STAVES

    /** Standard spellbook (interface 192) combat spells, curse spells included. */
    fun appliesTo(spell: CombatSpell?): Boolean = spell != null && spell.interfaceId == 192

    fun accuracyMultiplier(
        player: Player,
        spell: CombatSpell?,
    ): Double = if (wielding(player) && appliesTo(spell)) (100 + ACCURACY_PERCENT) / 100.0 else 1.0

    fun magicDamageBonus(
        pawn: Pawn,
        spell: CombatSpell?,
    ): Double = if (pawn is Player && wielding(pawn) && appliesTo(spell)) DAMAGE_BONUS else 0.0
}
