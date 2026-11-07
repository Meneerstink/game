package gg.rsmod.plugins.content.activity.toa.het

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.activity.toa.ToaLobby
import gg.rsmod.plugins.content.activity.toa.ToaPath
import gg.rsmod.plugins.content.activity.toa.ToaRaid
import gg.rsmod.plugins.content.activity.toa.ToaRoomKey
import gg.rsmod.plugins.content.activity.toa.ToaRooms
import java.util.WeakHashMap

/** N462 Path of Het: registers the challenge room and binds the cache options of its locs, the seal and the mirror item. */
val rooms = WeakHashMap<ToaRaid, HetRoom>()
ToaRooms.register(ToaRoomKey(ToaPath.HET, false)) { raid -> HetRoom(raid).also { rooms[raid] = it } }

fun room(p: gg.rsmod.game.model.entity.Player): HetRoom? =
    ToaLobby.of(p.world).raid(p.uid)?.takeIf { it.contains(p) }?.let { rooms[it] }

HetIds.loc(HetIds.MIRROR)?.let { id ->
    on_obj_option(id, "Pick-up") { room(player)?.pickUp(player, player.getInteractingGameObj()) }
    on_obj_option(id, "Rotate-clockwise") { room(player)?.rotate(player, player.getInteractingGameObj(), true) }
    on_obj_option(id, "Rotate-anticlockwise") { room(player)?.rotate(player, player.getInteractingGameObj(), false) }
    on_obj_option(id, "Push") { room(player)?.push(player, player.getInteractingGameObj()) }
}
HetIds.loc(HetIds.MIRROR_DIRTY)?.let { id -> on_obj_option(id, "Clean") { room(player)?.clean(player, player.getInteractingGameObj()) } }
listOfNotNull(HetIds.loc(HetIds.WORN), HetIds.loc(HetIds.WORN_END)).distinct().forEach { id ->
    on_obj_option(id, "Break") {
        val r = room(player) ?: return@on_obj_option
        val obj = player.getInteractingGameObj()
        player.queue { r.breakBarrier(this, player, obj) }
    }
}
listOfNotNull(HetIds.npc(HetIds.SEAL), HetIds.npc(HetIds.SEAL_WEAKENED)).distinct().forEach { id ->
    on_npc_option(id, "Destroy") {
        val r = room(player) ?: return@on_npc_option
        player.queue { r.mine(this, player) }
    }
}
HetIds.item(HetIds.MIRROR_ITEM)?.let { id ->
    on_item_option(id, "Place") { room(player)?.place(player, player.getInteractingItemSlot()) }
}

/**
 * Pickaxe statues (OSRS loc 45468, a multiloc on varbit 14440 = the stored pickaxe; on the map at the Het entrance/exit and in the room):
 * "If no pickaxe is stored, the statue at the room's entrance holds a free bronze pickaxe"; a stored pickaxe stays for later raids
 * (Statue (Tombs of Amascut) Wiki). ONZEKER T25: the statue's look per player (varbit) is not driven - it shows its default model.
 */
val STORED = AttributeKey<Int>("toa_het_pickaxe")
val PICKAXES = gg.rsmod.plugins.content.skills.mining.PickaxeType.values.map { it.item }.toSet()
HetIds.loc(45468)?.let { id ->
    on_obj_option(id, "Take-pickaxe") {
        val stored = player.attr[STORED]
        if (player.inventory.isFull) { player.message("You don't have enough inventory space."); return@on_obj_option }
        if (stored != null && stored > 0) {
            player.inventory.add(stored)
            player.attr.remove(STORED)
        } else player.inventory.add(Items.BRONZE_PICKAXE)
    }
    on_obj_option(id, "Deposit-pickaxe") {
        if (player.attr[STORED]?.let { it > 0 } == true) return@on_obj_option // ONZEKER T25: OSRS message unknown
        val slot = player.inventory.rawItems.indexOfFirst { it != null && it.id in PICKAXES && it.id != Items.BRONZE_PICKAXE }
        if (slot < 0) return@on_obj_option
        val item = player.inventory[slot]!!.id
        if (player.inventory.remove(item, 1, beginSlot = slot).hasSucceeded()) player.attr[STORED] = item
    }
}
