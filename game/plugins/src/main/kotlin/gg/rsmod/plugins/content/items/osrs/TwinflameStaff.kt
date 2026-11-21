package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * PROJECT48 DMM-ITEMS C2 Twinflame staff (OSRS Wiki "Twinflame staff", fetched 2026-11-21): a one-handed staff (60 Magic) that
 * "acts as an infinite source of fire and water runes", gives "a 10% increase in accuracy and damage when casting spells from the
 * standard spellbook" and, "when casting elemental spells (excluding Strike and Surge spells), fires off two spells at once; the
 * second hit will land one tick after the first, dealing 40% of the damage dealt by the first, rounded down" (no extra experience,
 * runes or tome charges).
 * SOURCE_GAP (not built): the automatic switch to the spell of an NPC's elemental weakness and the Royal Titans wall rule - those
 * NPCs / that fight do not exist here.
 */
object TwinflameStaff {
    const val ID = 25494

    val DOUBLE_HIT_SPELLS =
        setOf(
            CombatSpell.WIND_BOLT, CombatSpell.WIND_BLAST, CombatSpell.WIND_WAVE,
            CombatSpell.WATER_BOLT, CombatSpell.WATER_BLAST, CombatSpell.WATER_WAVE,
            CombatSpell.EARTH_BOLT, CombatSpell.EARTH_BLAST, CombatSpell.EARTH_WAVE,
            CombatSpell.FIRE_BOLT, CombatSpell.FIRE_BLAST, CombatSpell.FIRE_WAVE,
        )

    fun worn(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id == ID

    /** Accuracy and damage factor: +10 % for every damaging spell of the standard spellbook (interface 192). */
    fun boost(
        player: Player,
        spell: CombatSpell?,
    ): Double = if (spell != null && worn(player) && spell.damaging && spell.interfaceId == 192) 1.1 else 1.0

    fun suppliesRune(
        player: Player,
        rune: Int,
    ): Boolean = (rune == Items.FIRE_RUNE || rune == Items.WATER_RUNE) && worn(player)

    fun doubleHit(
        player: Player,
        spell: CombatSpell,
    ): Boolean = spell in DOUBLE_HIT_SPELLS && worn(player)

    /**
     * The second spell's damage: 40 % of the first, rounded down. Hit damage is 1:1 with the shown hitsplat and the lifepoints
     * (Pawn.processHits subtracts hitmark.damage from lifepoints; Player.getMaximumLifepoints = the Hitpoints level), so no x10 here.
     */
    fun secondDamage(first: Int): Int = first * 4 / 10
}
