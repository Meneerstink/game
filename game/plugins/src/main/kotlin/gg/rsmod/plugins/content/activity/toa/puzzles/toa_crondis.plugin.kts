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
    // Claude 07-11 testclient: the palm stands in a planter whose low rim blocks walking - water it across the rim (2 tiles in sight).
    on_npc_option(npc = id, option = "Water", lineOfSightDistance = 2) { (ToaPuzzles.of(player) as? CrondisRoom)?.water(player, player.getInteractingNpc()) }
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
        val room = ToaPuzzles.of(player) as? CrondisRoom
        player.queue {
            if (room != null && !room.eligible(player)) return@queue
            val dialog = this
            var waiting = true
            room?.scope?.onClose { if (waiting) dialog.terminate() } //options()'s terminateAction closes the owned chatbox too.
            try {
                //Water-container Wiki destroy text. A stale Yes cannot consume an item after a room wipe/retry.
                if (confirmWarning("Another will be nearby.") && (room == null || room.eligible(player)) &&
                    player.inventory[slot] === item) player.inventory[slot] = null
            } finally { waiting = false }
        }
    }
    can_drop_item(id) { false }
}
ToaIds.npc(CrondisRoom.CROCODILE)?.let { id -> on_npc_combat(npc = id) { } }
