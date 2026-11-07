package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.skills.farming.logic.handler.WaterHandler

/** The planter's rim separates the player from the palm. All watering routes share this NPC approach. */
object CrondisInteractions {
    // Claude's 07-11 testclient observation; cache2686 palm11700..11703 size5. This compensates for the planter's approach.
    const val PALM_REACH = 2

    fun bindPalm(plugin: KotlinPlugin) = with(plugin) {
        (CrondisRoom.PALM..CrondisRoom.PALM + 3).mapNotNull(ToaIds::npc).distinct().forEach { id ->
            // PawnPathAction.itemUsePlugin reads the SAME NPC distance as walkPlugin. An item-specific LOS API is unnecessary.
            on_npc_option(npc = id, option = "Water", lineOfSightDistance = PALM_REACH) {
                (ToaPuzzles.of(player) as? CrondisRoom)?.water(player, player.getInteractingNpc())
            }
            ToaIds.item(CrondisRoom.CONTAINER)?.let { item -> on_item_on_npc(item = item, npc = id) {
                (ToaPuzzles.of(player) as? CrondisRoom)?.water(player, player.getInteractingNpc(), itemUsed = true)
            } }
            (WaterHandler.wateringCans.drop(1) + listOfNotNull(ToaIds.item(CrondisRoom.GRICOLLER))).distinct().forEach { can ->
                on_item_on_npc(item = can, npc = id) {
                    (ToaPuzzles.of(player) as? CrondisRoom)?.waterCan(player, player.getInteractingNpc(), player.getInteractingItemSlot())
                }
            }
        }
    }
}
