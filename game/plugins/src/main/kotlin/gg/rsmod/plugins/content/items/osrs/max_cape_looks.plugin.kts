package gg.rsmod.plugins.content.items.osrs

/**
 * Max cape looks (rules in [MaxCapeLooks]): the appearance block draws the plain max cape as the chosen unlocked variant. The chooser
 * itself is on the max cape's OSRS "Features" menu (max_cape_options.plugin.kts); the 667 "Customise" option is gone with the OSRS
 * menu (owner 2026-09-24).
 */

gg.rsmod.game.model.entity.Player.appearanceItemOverride = { player, slot, itemId ->
    if (slot == EquipmentType.CAPE.id) MaxCapeLooks.shownCape(player, itemId) else itemId
}
