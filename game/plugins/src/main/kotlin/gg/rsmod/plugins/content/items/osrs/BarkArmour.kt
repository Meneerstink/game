package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import kotlin.math.floor

/**
 * OSRS-IMPORT step 4 Swampbark and Bloodbark armour (OSRS Wiki raw wikitext 2026-09-14, "Swampbark armour", "Bloodbark armour"):
 * - Swampbark (50 Magic + 50 Defence): "the helm, body, and legs increase the duration of bind spells by 2 ticks each" (Bind 8 -> 14,
 *   Snare 16 -> 22, Entangle 24 -> 30 with all three).
 * - Bloodbark (60 Magic + 60 Defence): "each piece of armour increases the amount that blood spells heal by 2% of the damage dealt. When the
 *   full set of armour is equipped, blood spells will heal ... 35%"; "The 1/4 calculation rounds down first. After that, the result is
 *   scaled up by the bloodbark effect" (Mod Ash); with the Ancient sceptre "the total healing to 38.5%". "bloodbark armour has no effect"
 *   with the Sanguinesti staff.
 * - Creation: the matching splitbark piece used on the Nature Altar (nature runes) / Blood Altar (blood runes): gauntlets and boots 100 runes
 *   (42 / 77 Runecraft), helm 250 runes (46 / 79), body and legs 500 runes (48 / 81).
 * ADAPTED: the scaled heal is rounded down. NOT ENFORCED: the Runescroll of swampbark / bloodbark unlock (item absent in 667). No
 * experience is stated for infusing.
 */
object BarkArmour {
    const val BIND_TICKS_PER_PIECE = 2
    const val HEAL_PERCENT_PER_PIECE = 2

    val SWAMPBARK_BIND_PIECES = setOf(Items.SWAMPBARK_HELM, Items.SWAMPBARK_BODY, Items.SWAMPBARK_LEGS)

    val BLOODBARK_PIECES =
        setOf(Items.BLOODBARK_HELM, Items.BLOODBARK_BODY, Items.BLOODBARK_LEGS, Items.BLOODBARK_GAUNTLETS, Items.BLOODBARK_BOOTS)

    data class Infusion(
        val splitbark: Int,
        val result: Int,
        val rune: Int,
        val runes: Int,
        val runecraftLevel: Int,
    )

    val INFUSIONS =
        listOf(
            Infusion(Items.SPLITBARK_GAUNTLETS, Items.SWAMPBARK_GAUNTLETS, Items.NATURE_RUNE, 100, 42),
            Infusion(Items.SPLITBARK_BOOTS, Items.SWAMPBARK_BOOTS, Items.NATURE_RUNE, 100, 42),
            Infusion(Items.SPLITBARK_HELM, Items.SWAMPBARK_HELM, Items.NATURE_RUNE, 250, 46),
            Infusion(Items.SPLITBARK_BODY, Items.SWAMPBARK_BODY, Items.NATURE_RUNE, 500, 48),
            Infusion(Items.SPLITBARK_LEGS, Items.SWAMPBARK_LEGS, Items.NATURE_RUNE, 500, 48),
            Infusion(Items.SPLITBARK_GAUNTLETS, Items.BLOODBARK_GAUNTLETS, Items.BLOOD_RUNE, 100, 77),
            Infusion(Items.SPLITBARK_BOOTS, Items.BLOODBARK_BOOTS, Items.BLOOD_RUNE, 100, 77),
            Infusion(Items.SPLITBARK_HELM, Items.BLOODBARK_HELM, Items.BLOOD_RUNE, 250, 79),
            Infusion(Items.SPLITBARK_BODY, Items.BLOODBARK_BODY, Items.BLOOD_RUNE, 500, 81),
            Infusion(Items.SPLITBARK_LEGS, Items.BLOODBARK_LEGS, Items.BLOOD_RUNE, 500, 81),
        )

    private fun worn(player: Player): Set<Int> = EquipmentType.values().mapNotNull { player.getEquipment(it)?.id }.toSet()

    /** Extra bind ticks for a standard-spellbook bind spell ([CombatSpell.interfaceId] 192 with a freeze effect). */
    fun bindBonus(
        pawn: Pawn,
        spell: CombatSpell,
    ): Int {
        if (pawn !is Player || spell.interfaceId != 192 || spell.effect !is SpellEffect.Freeze) return 0
        return BIND_TICKS_PER_PIECE * SWAMPBARK_BIND_PIECES.count { it in worn(pawn) }
    }

    fun bloodbarkPieces(pawn: Pawn): Int = if (pawn is Player) BLOODBARK_PIECES.count { it in worn(pawn) } else 0

    /** Blood spell healing with [pieces] bloodbark pieces worn; without bloodbark the Ancient sceptre rule applies unchanged. */
    fun bloodHeal(
        damage: Int,
        pieces: Int,
        sceptreBoosted: Boolean,
    ): Int {
        if (pieces <= 0) return AncientSceptres.bloodHeal(damage, sceptreBoosted)
        val base = damage / 4
        val scaled = base * (25.0 + HEAL_PERCENT_PER_PIECE * pieces) / 25.0
        return floor(if (sceptreBoosted) scaled * 1.1 else scaled).toInt()
    }
}
