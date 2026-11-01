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

// N514 / N503 D11 (owner 2026-11-01): a Practice PvP death is safe - nothing lost, no loot, no gravestone. The match state must still
// be there when death.plugin.kts asks SafeDeath (pre-death hooks have no fixed order), so the cleanup waits for the death itself
// to finish (on_player_death, after the respawn) instead of running in the pre-death hook.
gg.rsmod.plugins.content.mechanics.death.SafeDeath.register { PracticePvp.safeDeath(it) }

on_player_death {
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
