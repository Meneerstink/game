package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.formula.TargetModifiers
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/**
 * N570 (N522 nachtrun deel 3, Krystilia's second "unusual loot"). OSRS Wiki "Slayer's enchantment" / "Slayer's staff (e)" (raw wikitext
 * 2026-11-05) and "Transcript:Slayer's enchantment":
 *  - dropped by Wilderness monsters while on a Krystilia task: bosses 1/30, others 1/(320 - floor(min(300, hitpoints) x 8/10));
 *  - used on a Slayer's staff it makes a Slayer's staff (e) with 2,500 charges, untradeable, 75 Magic + 55 Slayer;
 *  - Magic Dart on the CURRENT Slayer assignment: max hit floor(Magic/6) + 13 instead of floor(Magic/10) + 10; "Each cast of Magic Dart
 *    consumes 1 charge from the staff if the spell hits. If the spell does not hit, no charges will be consumed. Charges are also not
 *    consumed (and damage is not boosted) when casting Magic Dart on a monster that is not a current Slayer assignment";
 *  - "Once the charges run out, it will revert to a regular Slayer's staff".
 * Donor Near Reality WildernessSlayerDropProcessor: the boss rate applies while the Krystilia task is a boss task.
 */
object SlayersStaffE {
    const val ENCHANTMENT = Items.SLAYERS_ENCHANTMENT
    const val STAFF = Items.SLAYERS_STAFF
    const val STAFF_E = Items.SLAYERS_STAFF_E
    const val MAX_CHARGES = 2_500
    const val BOSS_DENOMINATOR = 30

    // Transcript:Slayer's enchantment.
    const val ENCHANT_INFO = "An enchanted Slayer staff requires level 75 Magic and level 55 Slayer to equip, and is untradeable. " +
        "Reverting it will not give your spell parchment back."
    const val ENCHANT_QUESTION = "Are you sure you wish to enchant it?"
    const val ENCHANT_DONE = "The spell enchants your staff. The tatty parchment crumbles to dust."

    /** Infobox "destroy" text of the staff (e); the Fandom wiki gives the same line for Revert. */
    fun chargesLine(charges: Int): String =
        "That staff contains ${String.format(java.util.Locale.ROOT, "%,d", charges)} charges. You will not be able to get them back."

    /** The staff (e) carries its 2,500 charges implicitly until the first one is used (the same rule as PoweredStaves). */
    fun charges(item: Item): Int = if (item.id == STAFF_E) item.attr[ItemAttribute.CHARGES] ?: MAX_CHARGES else 0

    fun withCharges(charges: Int): Item =
        if (charges <= 0) Item(STAFF) else Item(STAFF_E).apply { attr[ItemAttribute.CHARGES] = charges.coerceAtMost(MAX_CHARGES) }

    fun denominator(hitpoints: Int, boss: Boolean): Int =
        if (boss) BOSS_DENOMINATOR else 320 - (minOf(300, maxOf(0, hitpoints)) * 8) / 10

    fun bossTask(player: Player): Boolean = Krystilia.current(player)?.let { it in Krystilia.BOSS_TASKS } == true

    /** Called once per Krystilia task kill (SlayerExt.handleDecrease, only after Krystilia.matches - the kill is in the Wilderness). */
    fun rollEnchantment(player: Player, npc: Npc) {
        try {
            val chance = 1.0 / denominator(npc.combatDef.lifepoints / 10, bossTask(player))
            if (player.world.randomDouble() >= chance) return
            gg.rsmod.plugins.content.drops.DropTableFactory.giveDrop(player.world, player, npc.id, npc.tile, listOf(Item(ENCHANTMENT, 1)), allowLootShare = false)
        } catch (e: Exception) {
            gg.rsmod.game.model.Trace.crash("slayer's enchantment", e)
        }
    }

    fun wieldedCharges(player: Player): Int = player.equipment[EquipmentType.WEAPON.id]?.let { charges(it) } ?: 0

    /** The boosted Magic Dart applies while a charged staff (e) is wielded and [target] is the player's current Slayer assignment. */
    fun boosts(player: Player, target: Pawn, spell: CombatSpell?): Boolean =
        spell == CombatSpell.MAGIC_DART && wieldedCharges(player) > 0 && TargetModifiers.isCurrentSlayerTask(player, target)

    fun boostedMaxHit(magicLevel: Int): Int = magicLevel / 6 + 13

    /** One charge per Magic Dart that hits a task monster; the last one turns the staff back into a Slayer's staff. */
    fun afterCast(player: Player, target: Pawn, spell: CombatSpell, landed: Boolean) {
        if (!landed || !boosts(player, target, spell)) return
        val weapon = player.equipment[EquipmentType.WEAPON.id] ?: return
        player.equipment[EquipmentType.WEAPON.id] = withCharges(charges(weapon) - 1)
    }

    fun check(player: Player, item: Item) = player.message(chargesLine(charges(item)).substringBefore(" You will"))

    fun magicLevel(player: Player): Int = player.skills.getCurrentLevel(Skills.MAGIC)
}
