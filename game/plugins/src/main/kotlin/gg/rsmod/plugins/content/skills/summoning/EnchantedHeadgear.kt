package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import kotlin.math.min

/**
 * RCV-010 A5 (owner live 2026-09-13: Pikkupstix "Enchant" -> "Nothing interesting happens").
 *
 * Headgear that holds combat Summoning scrolls. Source: Void 2011 `EnchantedHeadgear.kt` +
 * `enchanted_headgear.tables.toml` (levels, capacities, messages); item ids are this 667 cache's own
 * `Items` constants for the same names (base, "(e)", "(charged)"), verified by `EnchantedHeadgearTests`.
 * For the hunter helms the plain item is already scroll-capable (enchanted == base).
 */
object EnchantedHeadgear {
    data class Headgear(val base: Int, val enchanted: Int, val charged: Int, val level: Int, val capacity: Int)

    val rows: List<Headgear> =
        listOf(
            Headgear(Items.ANTLERS, Items.ANTLERS, Items.ANTLERS_CHARGED, 10, 40),
            Headgear(Items.LIZARD_SKULL, Items.LIZARD_SKULL, Items.LIZARD_SKULL_CHARGED, 30, 65),
            Headgear(Items.FEATHER_HEADDRESS, Items.FEATHER_HEADDRESS, Items.FEATHER_HEADDRESS_CHARGED, 50, 150),
            Headgear(Items.FEATHER_HEADDRESS_12213, Items.FEATHER_HEADDRESS_12213, Items.FEATHER_HEADDRESS_CHARGED_12215, 50, 150),
            Headgear(Items.FEATHER_HEADDRESS_12216, Items.FEATHER_HEADDRESS_12216, Items.FEATHER_HEADDRESS_CHARGED_12218, 50, 150),
            Headgear(Items.FEATHER_HEADDRESS_12219, Items.FEATHER_HEADDRESS_12219, Items.FEATHER_HEADDRESS_CHARGED_12221, 50, 150),
            Headgear(Items.FEATHER_HEADDRESS_12222, Items.FEATHER_HEADDRESS_12222, Items.FEATHER_HEADDRESS_CHARGED_12224, 50, 150),
            Headgear(Items.SNAKESKIN_BANDANA, Items.SNAKESKIN_BANDANA_E, Items.SNAKESKIN_BANDANA_CHARGED, 20, 50),
            Headgear(Items.ARCHER_HELM, Items.ARCHER_HELM_E, Items.ARCHER_HELM_CHARGED, 30, 70),
            Headgear(Items.BERSERKER_HELM, Items.BERSERKER_HELM_E, Items.BERSERKER_HELM_CHARGED, 30, 70),
            Headgear(Items.WARRIOR_HELM, Items.WARRIOR_HELM_E, Items.WARRIOR_HELM_CHARGED, 30, 70),
            Headgear(Items.FARSEER_HELM, Items.FARSEER_HELM_E, Items.FARSEER_HELM_CHARGED, 30, 70),
            Headgear(Items.RUNE_FULL_HELM, Items.RUNE_FULL_HELM_E, Items.RUNE_FULL_HELM_CHARGED, 30, 60),
            Headgear(Items.SPLITBARK_HELM, Items.SPLITBARK_HELM_E, Items.SPLITBARK_HELM_CHARGED, 30, 50),
            Headgear(Items.HELM_OF_NEITIZNOT, Items.HELM_OF_NEITIZNOT_E, Items.HELM_OF_NEITIZNOT_CHARGED, 45, 90),
            Headgear(Items.DRAGON_MED_HELM, Items.DRAGON_MED_HELM_E, Items.DRAGON_MED_HELM_CHARGED, 50, 110),
            Headgear(Items.LUNAR_HELM, Items.LUNAR_HELM_E, Items.LUNAR_HELM_CHARGED, 55, 110),
            Headgear(Items.ARMADYL_HELMET, Items.ARMADYL_HELMET_E, Items.ARMADYL_HELMET_CHARGED, 60, 120),
        )

    /** Void stores the held scroll per player (one charged helm at a time). */
    val SCROLL_ATTR = AttributeKey<Int>(persistenceKey = "enchanted_headgear_scroll")
    val COUNT_ATTR = AttributeKey<Int>(persistenceKey = "enchanted_headgear_count")

    val allItems: Set<Int> get() = rows.flatMap { listOf(it.base, it.enchanted, it.charged) }.toSet()

    fun forItem(id: Int?): Headgear? = id?.let { rows.firstOrNull { row -> id == row.base || id == row.enchanted || id == row.charged } }

    /** Void `isCombatScroll`: only scrolls of specials cast on an npc or player can be stored. */
    val combatScrollIds: Set<Int> by lazy {
        SummoningSpecialMoves.bindings
            .filter { it.target == FamiliarSpecialTarget.NPC || it.target == FamiliarSpecialTarget.PLAYER }
            .flatMap { binding -> binding.scrolls.map { it.scroll } }
            .toSet()
    }

    private fun name(player: Player, id: Int): String = player.world.definitions.get(ItemDef::class.java, id).name.lowercase()

    /** Pikkupstix: enchant a plain helm, disenchant an empty enchanted one, refuse a charged one. */
    fun enchant(player: Player, itemId: Int, slot: Int): Boolean {
        val headgear = forItem(itemId) ?: return false
        when (itemId) {
            headgear.charged -> player.message("You need to remove the scrolls before I can work on that helmet.")
            headgear.enchanted ->
                if (headgear.enchanted == headgear.base) {
                    player.message("That helmet is already able to hold Summoning scrolls - just use your combat scrolls on it.")
                } else {
                    player.inventory[slot] = Item(headgear.base)
                    player.message("Pikkupstix removes the enchantment from your headwear.")
                }
            else ->
                if (player.skills.getMaxLevel(Skills.SUMMONING) < headgear.level) {
                    player.message("You need a Summoning level of ${headgear.level} to enchant that helmet.")
                } else {
                    player.inventory[slot] = Item(headgear.enchanted)
                    player.message("Pikkupstix magically enchants your headwear.")
                }
        }
        return true
    }

    /** Scroll used on an enchanted or charged helm in the pack: fill it up to capacity with that scroll type. */
    fun store(player: Player, scrollId: Int, helmSlot: Int) {
        val helm = player.inventory[helmSlot] ?: return
        val headgear = forItem(helm.id) ?: return
        if (helm.id == headgear.base && headgear.base != headgear.enchanted) return
        if (scrollId !in combatScrollIds) {
            player.message("Only combat scrolls can be stored in headgear.")
            return
        }
        val stored = player.attr[COUNT_ATTR] ?: 0
        val held = player.attr[SCROLL_ATTR]
        if (stored > 0 && held != null && held != scrollId) {
            player.message("This helmet already holds ${name(player, held)}s. Empty it first.")
            return
        }
        val room = headgear.capacity - stored
        if (room <= 0) {
            player.message("The helmet is full.")
            return
        }
        val amount = min(room, player.inventory.getItemCount(scrollId))
        if (amount <= 0 || !player.inventory.remove(scrollId, amount, assureFullRemoval = true).hasSucceeded()) return
        player.attr[SCROLL_ATTR] = scrollId
        player.attr[COUNT_ATTR] = stored + amount
        if (helm.id == headgear.enchanted) player.inventory[helmSlot] = Item(headgear.charged)
        player.message("You store $amount ${name(player, scrollId)}${if (amount == 1) "" else "s"} in the helmet (${stored + amount}/${headgear.capacity}).")
    }

    fun commune(player: Player) {
        val count = player.attr[COUNT_ATTR] ?: 0
        val scroll = player.attr[SCROLL_ATTR]
        if (count <= 0 || scroll == null) {
            player.message("The helmet holds no scrolls.")
            return
        }
        player.message("The helmet holds $count ${name(player, scroll)}${if (count == 1) "" else "s"}.")
    }

    /** Empty the charged helm (worn or at [slot] in the pack) back to its enchanted form, returning the scrolls. */
    fun uncharge(player: Player, headgear: Headgear, worn: Boolean, slot: Int) {
        val scroll = player.attr[SCROLL_ATTR]
        val count = player.attr[COUNT_ATTR] ?: 0
        if (scroll != null && count > 0) {
            if (!player.inventory.add(scroll, count, assureFullInsertion = true).hasSucceeded()) return
            player.message("You empty the helmet, recovering $count ${name(player, scroll)}${if (count == 1) "" else "s"}.")
        }
        if (worn) {
            player.equipment[EquipmentType.HEAD.id] = Item(headgear.enchanted)
            player.addBlock(UpdateBlockType.APPEARANCE)
        } else {
            player.inventory[slot] = Item(headgear.enchanted)
        }
        player.attr.remove(SCROLL_ATTR)
        player.attr.remove(COUNT_ATTR)
    }

    /** The scroll a worn charged helm can supply for a special move, or null (Void `enchantedHeadgearScroll`). */
    fun wornScroll(player: Player): Int? {
        val head = player.equipment[EquipmentType.HEAD.id]?.id
        val headgear = forItem(head) ?: return null
        if (headgear.charged != head || (player.attr[COUNT_ATTR] ?: 0) <= 0) return null
        return player.attr[SCROLL_ATTR]
    }

    /** Spend one stored scroll; the last one returns the helm to its enchanted form (Void `spendEnchantedHeadgearScroll`). */
    fun spendWornScroll(player: Player) {
        val count = (player.attr[COUNT_ATTR] ?: 0) - 1
        val headgear = forItem(player.equipment[EquipmentType.HEAD.id]?.id) ?: return
        if (count <= 0) {
            player.equipment[EquipmentType.HEAD.id] = Item(headgear.enchanted)
            player.addBlock(UpdateBlockType.APPEARANCE)
            player.attr.remove(SCROLL_ATTR)
            player.attr.remove(COUNT_ATTR)
        } else {
            player.attr[COUNT_ATTR] = count
        }
    }
}
