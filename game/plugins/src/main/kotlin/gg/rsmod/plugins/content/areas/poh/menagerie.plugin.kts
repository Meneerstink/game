package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.plugins.api.ext.*

/**
 * CONSTRUCTION-99 deel 6: the menagerie's pet house window ([PohMenagerie]; Darkan `PetHouse` buttons on 879 / 878).
 */
register_container_key(PohMenagerie.KEY)

on_button(PohMenagerie.WINDOW, PohMenagerie.WINDOW_CLOSE) { PohMenagerie.close(player) }

on_interface_close(PohMenagerie.WINDOW) { PohMenagerie.onClosed(player) }

// Withdraw (op1) / Examine (op10) on a stored pet.
on_button(PohMenagerie.WINDOW, PohMenagerie.WINDOW_GRID) p@{
    val slot = player.getInteractingSlot()
    val house = PohMenagerie.container(player)
    if (slot !in 0 until house.capacity) return@p
    val item = house[slot] ?: return@p
    if (player.getInteractingOpcode() == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }
    PohMenagerie.withdraw(player, slot)
}

// Store (op1) / Examine (op10) on an inventory slot.
on_button(PohMenagerie.SIDE, PohMenagerie.SIDE_GRID) p@{
    val slot = player.getInteractingSlot()
    if (slot !in 0 until player.inventory.capacity) return@p
    val item = player.inventory[slot] ?: return@p
    if (player.getInteractingOpcode() == 25) {
        world.sendExamine(player, item.id, ExamineEntityType.ITEM)
        return@p
    }
    PohMenagerie.store(player, slot)
}
