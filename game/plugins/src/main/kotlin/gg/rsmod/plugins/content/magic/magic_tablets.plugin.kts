package gg.rsmod.plugins.content.magic

import gg.rsmod.plugins.content.magic.jewelleryenchanting.EnchantmentData

/*
 * The non-teleport lectern tablets (poh_furniture.plugin.kts makes them) had no handler, so a tablet the player had just made did nothing.
 * - Bones to Bananas / Bones to Peaches: "Break" converts the bones like the spell (BonesToFruit), no runes, no experience.
 * - Enchant tablets: RuneScape Wiki "Lvl-1 Enchant" (tablet): "the proper process is to 'Use' this tablet on a piece of jewellery,
 *   which gives no experience". Used on jewellery of its own level (EnchantmentData) it makes the enchanted item and is used up.
 */
on_item_option(item = Items.BONES_TO_BANANAS, option = "break") {
    breakFruitTablet(player, Items.BONES_TO_BANANAS, Items.BANANA)
}

on_item_option(item = Items.BONES_TO_PEACHES_8015, option = "break") {
    breakFruitTablet(player, Items.BONES_TO_PEACHES_8015, Items.PEACH)
}

fun breakFruitTablet(player: Player, tablet: Int, produce: Int) {
    if (BonesToFruit.refuseWithoutBones(player)) return
    if (!player.inventory.remove(tablet).hasSucceeded()) return
    BonesToFruit.convert(player, produce)
}

val ENCHANT_TABLETS: Map<Int, SpellbookData> =
    mapOf(
        Items.ENCHANT_SAPPHIRE to SpellbookData.LVL_1_ENCHANT,
        Items.ENCHANT_EMERALD to SpellbookData.LVL_2_ENCHANT,
        Items.ENCHANT_RUBY to SpellbookData.LVL_3_ENCHANT,
        Items.ENCHANT_DIAMOND to SpellbookData.LVL_4_ENCHANT,
        Items.ENCHANT_DRAGONSTN to SpellbookData.LVL_5_ENCHANT,
        Items.ENCHANT_ONYX to SpellbookData.LVL_6_ENCHANT,
    )

ENCHANT_TABLETS.forEach { (tablet, level) ->
    EnchantmentData.values().filter { it.spell == level }.forEach { jewellery ->
        on_item_on_item(tablet, jewellery.raw) {
            if (!player.inventory.contains(tablet) || !player.inventory.contains(jewellery.raw)) return@on_item_on_item
            if (player.inventory.remove(tablet).hasSucceeded() && player.inventory.remove(jewellery.raw).hasSucceeded()) {
                player.inventory.add(jewellery.product)
                player.playSound(jewellery.sound)
            }
        }
    }
}
