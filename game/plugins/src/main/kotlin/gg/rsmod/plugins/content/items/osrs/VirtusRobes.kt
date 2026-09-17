package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * OSRS-IMPORT audit round 2026-09-17b, "Virtus robes" (OSRS Wiki raw wikitext): "Each piece of Virtus robes gives a
 * 2% magic damage bonus for a total of 6%" - already applied generically through each piece's own items.yml
 * `magic_damage` field, no code needed for that part. "When using combat spells from Ancient Magicks, an additional
 * 3% magic damage is given per piece, taking the bonus to 5% per piece for a total of 15% for the full set" - this
 * conditional extra was entirely missing before this fix.
 * ADAPTED: the (broken) variants already carry `magic_damage: 0` in items.yml (no flat bonus either), so they are
 * excluded here too - only the intact pieces count towards the Ancient Magicks bonus, matching that existing choice.
 */
object VirtusRobes {
    const val ANCIENT_MAGICKS_BONUS_PER_PIECE = 0.03
    private const val ANCIENT_MAGICKS_INTERFACE_ID = 193

    val PIECES =
        setOf(
            Items.VIRTUS_MASK, Items.VIRTUS_MASK_20161,
            Items.VIRTUS_ROBE_TOP, Items.VIRTUS_ROBE_TOP_20165,
            Items.VIRTUS_ROBE_LEGS, Items.VIRTUS_ROBE_LEGS_20169,
        )

    private fun worn(player: Player): Set<Int> = EquipmentType.values().mapNotNull { player.getEquipment(it)?.id }.toSet()

    /** Extra magic damage fraction (0.03 per worn intact piece) for an Ancient Magicks spell; 0 outside that spellbook. */
    fun ancientMagicksBonus(
        pawn: Pawn,
        spell: CombatSpell?,
    ): Double {
        if (pawn !is Player || spell == null || spell.interfaceId != ANCIENT_MAGICKS_INTERFACE_ID) return 0.0
        return ANCIENT_MAGICKS_BONUS_PER_PIECE * PIECES.count { it in worn(pawn) }
    }
}
