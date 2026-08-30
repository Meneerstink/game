package gg.rsmod.plugins.content.skills.hunter

// Numeric option indices are used instead of option text since the exact cache-defined
// menu strings for these trap items/objects have not been verified in this environment -
// see IMPLEMENTATION_STATUS.md. Index 1 is the primary/top option, matching the convention
// used elsewhere in this codebase (e.g. mining.plugin.kts).

on_item_option(item = Items.BIRD_SNARE, option = 1) {
    Hunter.layTrap(player, Items.BIRD_SNARE)
}

on_item_option(item = Items.BOX_TRAP, option = 1) {
    Hunter.layTrap(player, Items.BOX_TRAP)
}

HunterCreatures.ALL.forEach { creature ->
    on_obj_option(obj = creature.emptyTrapObj, option = 1) {
        Hunter.collect(player, player.getInteractingGameObj())
    }
    on_obj_option(obj = creature.fullTrapObj, option = 1) {
        Hunter.collect(player, player.getInteractingGameObj())
    }
}
