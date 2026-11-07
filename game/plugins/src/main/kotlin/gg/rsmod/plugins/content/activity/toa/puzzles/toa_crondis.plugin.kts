package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.skills.farming.logic.handler.WaterHandler

ToaPuzzles.registerCrondis()

listOf(45398, 45399).mapNotNull(ToaIds::loc).distinct().forEach { id ->
    on_obj_option(id, "Fill") { (ToaPuzzles.of(player) as? CrondisRoom)?.fill(player, player.getInteractingGameObj()) }
    ToaIds.item(CrondisRoom.CONTAINER)?.let { item -> on_item_on_obj(id, item) {
        (ToaPuzzles.of(player) as? CrondisRoom)?.fill(player, player.getInteractingGameObj(), itemUsed = true)
    } }
}
(CrondisRoom.PALM..CrondisRoom.PALM + 3).mapNotNull(ToaIds::npc).distinct().forEach { id ->
    on_npc_option(npc = id, option = "Water") { (ToaPuzzles.of(player) as? CrondisRoom)?.water(player, player.getInteractingNpc()) }
    ToaIds.item(CrondisRoom.CONTAINER)?.let { item -> on_item_on_npc(item = item, npc = id) {
        (ToaPuzzles.of(player) as? CrondisRoom)?.water(player, player.getInteractingNpc(), itemUsed = true)
    } }
    (WaterHandler.wateringCans.drop(1) + listOfNotNull(ToaIds.item(CrondisRoom.GRICOLLER))).distinct().forEach { can ->
        on_item_on_npc(item = can, npc = id) {
            (ToaPuzzles.of(player) as? CrondisRoom)?.waterCan(player, player.getInteractingNpc(), player.getInteractingItemSlot())
        }
    }
}
ToaIds.item(CrondisRoom.CONTAINER)?.let { id ->
    set_ground_item_condition(id) { (ToaPuzzles.of(player) as? CrondisRoom)?.canTake(player, player.getInteractingGroundItem()) == true }
    on_item_option(id, "Empty") { (ToaPuzzles.of(player) as? CrondisRoom)?.empty(player) }
    on_item_option(id, "Check") { (ToaPuzzles.of(player) as? CrondisRoom)?.check(player) }
    on_item_option(id, "Destroy") {
        val item = player.getInteractingItem()
        val slot = player.getInteractingItemSlot()
        player.queue {
            //Water-container Wiki destroy text, not a custom confirmation message.
            if (confirmWarning("Another will be nearby.") && player.inventory[slot] === item) player.inventory[slot] = null
        }
    }
    can_drop_item(id) { false }
}
ToaIds.npc(CrondisRoom.CROCODILE)?.let { id -> on_npc_combat(npc = id) { } }
