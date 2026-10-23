package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.content.skills.runecrafting.Altar

/**
 * OSRS-IMPORT step 4: infusing splitbark armour into Swampbark (Nature Altar) and Bloodbark (Blood Altar) armour (rules and sources in
 * [BarkArmour]). ADAPTED: messages. NOT ENFORCED: the Runescroll unlock.
 */

BarkArmour.INFUSIONS.forEach { infusion ->
    val altar = if (infusion.rune == Items.NATURE_RUNE) Altar.NATURE.altar else Altar.BLOOD.altar
    on_item_on_obj(obj = altar, item = infusion.splitbark) {
        if (player.skills.getCurrentLevel(Skills.RUNECRAFTING) < infusion.runecraftLevel) {
            player.message("You need a Runecraft level of ${infusion.runecraftLevel} to do that.")
            return@on_item_on_obj
        }
        if (player.inventory.getItemCount(infusion.rune) < infusion.runes) {
            player.message("You need ${infusion.runes} runes to infuse this armour.")
            return@on_item_on_obj
        }
        if (!player.inventory.contains(infusion.splitbark)) return@on_item_on_obj
        player.inventory.remove(infusion.rune, infusion.runes)
        player.inventory.remove(infusion.splitbark, 1)
        player.inventory.add(infusion.result, 1)
    }
}
