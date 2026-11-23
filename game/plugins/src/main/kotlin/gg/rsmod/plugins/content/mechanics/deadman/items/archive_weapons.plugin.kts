package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.RenderAnimations
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.PoweredStaffCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.ZoryaSpell
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids

val tome = PoweredStaves.Staff.ZORYA
if (tome.available) {
    SpecialAttacks.register(ZoryaTome.SPECIAL_ENERGY, tome.charged) {
        if (!PoweredStaffCombatStrategy.canAttack(player, target) || !ZoryaSpell.fire(player, target, special = true)) specialFailed()
    }
    SpecialAttacks.registerReach(tome.charged, range = 7, needsAmmo = false)
    can_equip_item(tome.uncharged) {
        player.message("Zorya's tome has no charges, so it cannot be equipped.")
        false
    }
    tome.ids.forEach { id ->
        on_item_option(id, "charge") {
            val slot = player.getInteractingItemSlot()
            val expected = player.inventory[slot] ?: return@on_item_option
            player.queue {
                val max = PoweredStaves.chargesAffordable(tome, PoweredStaves.charges(expected)) { player.inventory.getItemCount(it) }
                if (max <= 0) {
                    player.message("You need 3 astral runes and 2 nature runes per charge.")
                    return@queue
                }
                val requested = if (max == 1) 1 else inputInt("How many charges would you like to add? (1 - $max)")
                if (ZoryaCharges.charge(player, slot, expected, requested) > 0)
                    player.message("Your weapon has ${PoweredStaves.charges(player.inventory[slot]!!)} charges.")
            }
        }
        on_item_option(id, "check") {
            player.inventory[player.getInteractingItemSlot()]?.let { player.message("Your weapon has ${PoweredStaves.charges(it)} charges.") }
        }
        on_equipment_option(id, "check") {
            player.getEquipment(EquipmentType.WEAPON)?.let { player.message("Your weapon has ${PoweredStaves.charges(it)} charges.") }
        }
        on_item_option(id, "uncharge") {
            val slot = player.getInteractingItemSlot()
            val expected = player.inventory[slot] ?: return@on_item_option
            player.queue {
                if (options("Proceed.", "Cancel.", title = "Fully uncharge Zorya's tome?") == 1) {
                    if (ZoryaCharges.uncharge(player, slot, expected)) player.message("You uncharge Zorya's tome.")
                }
            }
        }
    }
    Dmm2Ids.bas(15135)?.let { RenderAnimations.register(it, *tome.ids.toIntArray()) }
    Dmm2Ids.bas(15142)?.let { RenderAnimations.registerFemale(it, *tome.ids.toIntArray()) }
}
Dmm2Ids.item(34681)?.let { weapon -> Dmm2Ids.bas(15110)?.let { RenderAnimations.register(it, weapon) } }

Dmm2Ids.item(34669)?.let { shard ->
    on_item_on_item(Items.CHISEL, shard) {
        player.queue {
            while (AscensionCrafting.carve(player)) wait(2)
        }
    }
}
