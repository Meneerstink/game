package gg.rsmod.plugins.content.mechanics.practicepvp

import gg.rsmod.plugins.api.EquipmentType

// Audit finding 9 remainder ("vervanging van gear"): EquipAction.equip silently moves whatever
// is already equipped in a slot into the player's real inventory when a different item is worn
// over it - a route into inventory that the existing Remove-button guards never covered, since
// it isn't a Remove click at all. Registered for every slot (not just the 5 the presets grant)
// so it also covers a 2h-weapon/shield conflation bumping an adjacent slot. Symmetric with the
// canUnequipSlot check EquipAction.unequip itself now also runs, so both real routes are closed
// by the one PracticePvp.isHoldingTempGear check.
EquipmentType.values().forEach { slot ->
    can_unequip_from_slot(slot.id) {
        if (PracticePvp.isHoldingTempGear(player)) {
            player.filterableMessage("You can't remove free Practice PvP gear - it's cleared automatically when the match ends.")
            return@can_unequip_from_slot false
        }
        true
    }
}

// Best-effort: player-pre-death plugins run in registration order, which this codebase
// doesn't guarantee relative to death.plugin.kts's handler - see PracticePvp.kt's doc
// comment for the residual (bounded, low-value-gear) risk this leaves if this handler
// happens to run after the normal loot/gravestone resolution for the same death.
on_player_pre_death {
    PracticePvp.cleanup(player)
}

on_logout {
    PracticePvp.cleanup(player)
}

on_command("practice") {
    val arg = player.getCommandArgs().getOrNull(0)?.lowercase()
    val preset =
        when (arg) {
            "range", "ranged" -> PracticePvp.Preset.RANGE
            "mage", "magic" -> PracticePvp.Preset.MAGE
            else -> PracticePvp.Preset.MELEE
        }
    PracticePvp.queueUp(player, preset)
}
