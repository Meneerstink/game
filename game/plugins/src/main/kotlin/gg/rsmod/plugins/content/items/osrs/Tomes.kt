package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * OSRS-IMPORT Tome of Fire / Tome of Water (OSRS Wiki raw wikitext, fetched 2026-09-14).
 * - Fire: "each page adding 20 charges", "The tome can hold 1,000 pages"; "Casting fire spells with a charged tome equipped will
 *   increase the spell's damage by 10% against NPCs and 50% against players ... and consume one charge per spell" (29 May 2024:
 *   NPC boost 50% -> 10%); "only to the standard spellbook's fire spells (Fire Strike, Fire Bolt, Fire Blast, Fire Wave, and Fire
 *   Surge)"; "When the tome has at least one charge, it acts as an infinite source of fire runes while equipped. Using it as a
 *   source of fire runes for non-combat spells does not consume any charges"; "requires level 50 in Magic to wield".
 * - Water: "Casting an offensive water spell from the standard spellbook with a charged tome equipped will increase the spell's
 *   accuracy and damage by 10% against NPCs and 20% against players (stacking multiplicatively with Magic damage bonuses), and
 *   consume one charge per cast." "Curse spells such as Vulnerability and Entangle gain a 20% boost to their accuracy, likewise
 *   consuming one charge per cast." "Casting any other spell that requires water runes will not consume a charge." Ice spells
 *   "do not receive the increase ... and do not consume any charges". "Stat-draining curse spells (i.e. Confuse, Weaken, Curse,
 *   Vulnerability, Enfeeble, and Stun) also gain a 50% boost to their effectiveness." Soaked pages "act as a source of water
 *   runes whilst equipped".
 * SOURCE_CONFLICT (owner question): the wiki DPS calculator multiplies water-spell accuracy by 6/5 against NPCs (its line is
 * marked "todo"); the item page says 10% - the item page is used. SOURCE_GAP (ADAPTED): drain rounding (the boosted drain is
 * floored); the "Pages" option shows the page count (its OSRS behaviour is not described); messages.
 */
object Tomes {
    const val CHARGES_PER_PAGE = 20
    const val MAX_PAGES = 1_000
    const val MAX_CHARGES = CHARGES_PER_PAGE * MAX_PAGES

    val FIRE_SPELLS = setOf(CombatSpell.FIRE_STRIKE, CombatSpell.FIRE_BOLT, CombatSpell.FIRE_BLAST, CombatSpell.FIRE_WAVE, CombatSpell.FIRE_SURGE)
    val WATER_SPELLS = setOf(CombatSpell.WATER_STRIKE, CombatSpell.WATER_BOLT, CombatSpell.WATER_BLAST, CombatSpell.WATER_WAVE, CombatSpell.WATER_SURGE)
    val STAT_DRAIN_CURSES = setOf(CombatSpell.CONFUSE, CombatSpell.WEAKEN, CombatSpell.CURSE, CombatSpell.VULNERABILITY, CombatSpell.ENFEEBLE, CombatSpell.STUN)
    val CURSE_SPELLS = STAT_DRAIN_CURSES + setOf(CombatSpell.BIND, CombatSpell.SNARE, CombatSpell.ENTANGLE)

    enum class Tome(
        val charged: Int,
        val empty: Int,
        val page: Int,
        val rune: Int,
    ) {
        FIRE(Items.TOME_OF_FIRE, Items.TOME_OF_FIRE_EMPTY, Items.TOME_BURNT_PAGE, Items.FIRE_RUNE),
        WATER(Items.TOME_OF_WATER, Items.TOME_OF_WATER_EMPTY, Items.TOME_SOAKED_PAGE, Items.WATER_RUNE),
    }

    fun tomeFor(itemId: Int?): Tome? = Tome.values().firstOrNull { itemId == it.charged || itemId == it.empty }

    fun charges(item: Item): Int = if (item.id == tomeFor(item.id)?.charged) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun withCharges(
        item: Item,
        charges: Int,
    ): Item {
        val tome = tomeFor(item.id) ?: return item
        val id = if (charges > 0) tome.charged else tome.empty
        return Item(id, item.amount).copyAttr(item).also {
            if (charges > 0) it.attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** Pages [available] add to [item]: 20 charges each, never beyond 1,000 pages. */
    fun pagesToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, (MAX_CHARGES - charges(item)) / CHARGES_PER_PAGE).coerceAtLeast(0)

    /** The worn tome holding at least one charge. */
    fun chargedWorn(player: Player): Tome? {
        val shield = player.getEquipment(EquipmentType.SHIELD) ?: return null
        val tome = tomeFor(shield.id) ?: return null
        return if (charges(shield) > 0) tome else null
    }

    fun suppliesRune(
        player: Player,
        rune: Int,
    ): Boolean = chargedWorn(player)?.rune == rune

    fun damageMultiplier(
        player: Player,
        target: Pawn,
        spell: CombatSpell?,
    ): Double =
        when {
            spell == null -> 1.0
            chargedWorn(player) == Tome.FIRE && spell in FIRE_SPELLS -> if (target is Player) 1.5 else 1.1
            chargedWorn(player) == Tome.WATER && spell in WATER_SPELLS -> if (target is Player) 1.2 else 1.1
            else -> 1.0
        }

    fun accuracyMultiplier(
        player: Player,
        target: Pawn,
        spell: CombatSpell?,
    ): Double =
        when {
            spell == null || chargedWorn(player) != Tome.WATER -> 1.0
            spell in WATER_SPELLS -> if (target is Player) 1.2 else 1.1
            spell in CURSE_SPELLS -> 1.2
            else -> 1.0
        }

    /** Stat-draining curses drain 50 % more with a charged Tome of Water. */
    fun drainBoost(
        player: Pawn,
        spell: CombatSpell,
    ): Double = if (player is Player && chargedWorn(player) == Tome.WATER && spell in STAT_DRAIN_CURSES) 1.5 else 1.0

    fun usesCharge(
        tome: Tome,
        spell: CombatSpell,
    ): Boolean =
        when (tome) {
            Tome.FIRE -> spell in FIRE_SPELLS
            Tome.WATER -> spell in WATER_SPELLS || spell in CURSE_SPELLS
        }

    /** One charge per qualifying cast; the tome becomes empty at 0. */
    fun afterCast(
        player: Player,
        spell: CombatSpell,
    ) {
        val tome = chargedWorn(player) ?: return
        if (!usesCharge(tome, spell)) return
        val shield = player.getEquipment(EquipmentType.SHIELD) ?: return
        player.equipment[EquipmentType.SHIELD.id] = withCharges(shield, charges(shield) - 1)
    }
}
