package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.items.osrs.CrystalEquipment
import gg.rsmod.plugins.content.items.osrs.StaffOfTheDead

/** Resolve behaviour through the exact base state; keep physical ids and attributes when a charge mechanic changes state. */
object DeadmanCosmetics {
    fun base(id: Int?): Int? = id?.let { NigelRewardStore.active?.baseItem(Item(it))?.id ?: it }
    fun state(original: Item, result: Item): Item {
        val store = NigelRewardStore.active ?: return result
        val from = store.contract.skins.firstOrNull { it.cosmetic == original.id } ?: return result
        val to = store.contract.skins.firstOrNull { it.unlock == from.unlock && it.base == result.id } ?: return result
        return Item(to.cosmetic, result.amount).copyAttr(result)
    }
    enum class Result { OK, ITEM_CHANGED, UNSUPPORTED, NO_RESOURCE, NO_SPACE }
    /** Same underlying resource/caps as the base item. Every transaction retains the skin and all unrelated attributes. */
    fun charge(player: Player, slot: Int, expectedId: Int, resource: Int): Result {
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val held = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (held.id != expectedId || held.amount != 1 || held.getDef(player.world.definitions).noted) return Result.ITEM_CHANGED
        val underlying = base(held.id)
        val inventory = ItemContainer(player.inventory)
        val added: Int
        val charged: Item
        when {
            underlying in setOf(Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD) && resource == Items.ZULRAHS_SCALES -> {
                added = StaffOfTheDead.scalesToAdd(held, inventory.getItemCount(resource))
                charged = StaffOfTheDead.withScales(held, StaffOfTheDead.scales(held) + added)
            }
            (underlying in CrystalEquipment.INACTIVE_FOR || underlying in CrystalEquipment.CHARGED_FOR) && resource == Items.CRYSTAL_SHARD -> {
                added = CrystalEquipment.shardsToAdd(held, inventory.getItemCount(resource))
                charged = CrystalEquipment.withCharges(held, CrystalEquipment.charges(held) + added * CrystalEquipment.CHARGES_PER_SHARD)
            }
            else -> return Result.UNSUPPORTED
        }
        if (added <= 0 || !inventory.remove(resource, added).hasSucceeded()) return Result.NO_RESOURCE
        inventory[slot] = charged
        DeadmanInventory.publish(inventory, player.inventory); player.persistNow()
        return Result.OK
    }
    fun unchargeToxic(player: Player, slot: Int, expectedId: Int): Result {
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val held = player.inventory[slot] ?: return Result.ITEM_CHANGED
        if (held.id != expectedId || held.amount != 1) return Result.ITEM_CHANGED
        if (base(held.id) != Items.TOXIC_STAFF_OF_THE_DEAD) return Result.UNSUPPORTED
        val inventory = ItemContainer(player.inventory)
        inventory[slot] = StaffOfTheDead.withScales(held, 0)
        val scales = StaffOfTheDead.scales(held)
        if (scales > 0 && !DeadmanInventory.add(inventory, Item(Items.ZULRAHS_SCALES, scales))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory); player.persistNow()
        return Result.OK
    }
}
