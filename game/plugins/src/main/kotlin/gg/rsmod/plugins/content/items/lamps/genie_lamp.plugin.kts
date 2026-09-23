package gg.rsmod.plugins.content.items.lamps

/**
 * @author Alycia <https://github.com/alycii>
 *
 * Every experience lamp in [ExperienceLamps] (the genie lamp and all antique lamps) opens the skill-choice interface; the lamp
 * that was rubbed is remembered for the confirm button (lamp_interface.plugin.kts).
 */
ExperienceLamps.REWARDS.keys.forEach { lamp ->
    on_item_option(item = lamp, option = "rub") {
        player.attr[LampInterfaceState.RUBBED_LAMP] = lamp
        player.openInterface(interfaceId = 1139, dest = InterfaceDestination.MAIN_SCREEN)
        player.filterableMessage("You rub the lamp...")
    }
}
