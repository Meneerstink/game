package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/** PROJECT48: the mini-quests of combat bracket 51-70 (Q10-Q16), in the order of VOORTGANG.tsv. */
object B5170Quests {
    val ALL: List<ShortQuest> = listOf(FremennikTrials, FremennikIsles, PlagueCity, Biohazard, UndergroundPass, Regicide)

    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.BRUNDT_THE_CHIEFTAIN, null, Direction.SOUTH, listOf("Rellekka is a proud town.")),
            BracketNpc(Npcs.SIGLI_THE_HUNTSMAN, null, Direction.SOUTH, listOf("A good hunter reads the tracks.")),
            BracketNpc(Npcs.THORVALD_THE_WARRIOR, null, Direction.SOUTH, listOf("Strength wins the day.")),
            BracketNpc(Npcs.EDMOND, null, Direction.SOUTH, listOf("Please find my daughter.")),
            BracketNpc(Npcs.ALRENA, null, Direction.SOUTH, listOf("I worry about Elena every day.")),
            BracketNpc(Npcs.BRAVEK, null, Direction.SOUTH, listOf("Leave me be - I have a headache.")),
            BracketNpc(Npcs.ELENA_3215, gg.rsmod.game.model.Tile(2541, 9672, 0), Direction.SOUTH, listOf("It is a dreadful sickness.")),
            BracketNpc(Npcs.OMART, null, Direction.SOUTH, listOf("Keep your head down in this city.")),
            BracketNpc(Npcs.GUIDOR, null, Direction.SOUTH, listOf("My poor wife is ill.")),
            BracketNpc(Npcs.KING_LATHAS, null, Direction.SOUTH, listOf("Ardougne is in good hands.")),
            BracketNpc(Npcs.KOFTIK, Tile(2437, 3314, 0), Direction.SOUTH, listOf("The pass is no place for the weak.")),
            BracketNpc(Npcs.ELF_TRACKER, null, Direction.SOUTH, listOf("The forest watches us all.")),
            BracketNpc(Npcs.LORD_IORWERTH, Tile(2195, 3254, 0), Direction.SOUTH, listOf("Our people hold the line.")),
            BracketNpc(Npcs.GENERAL_HINING, null, Direction.SOUTH, listOf("Stay clear of the catapult.")),
            BracketNpc(Npcs.MORD_GUNNARS, null, Direction.SOUTH, listOf("The sea is calm today.")),
            BracketNpc(Npcs.KING_GJUKI_SORVOTT_IV, null, Direction.SOUTH, listOf("Jatizso stands proud.")),
            BracketNpc(
                Npcs.MAWNIS_BUROWGAR_5503, null, Direction.SOUTH, listOf("Neitiznot stands proud."),
                extra = { helmReclaim() },
            ),
        )
}

/**
 * ONTGR (The Fremennik Isles): after the quest Mawnis Burowgar sells additional Helms of Neitiznot for 50,000 coins (OSRS Wiki).
 * True when he spoke (offer taken or declined).
 */
private suspend fun QueueTask.helmReclaim(): Boolean {
    if (!gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, "The Fremennik Isles")) return false
    chatNpc("Another Helm of Neitiznot? They cost 50,000 coins.", facialExpression = FacialExpression.CALM_TALK)
    if (options("Buy a helm (50,000 coins).", "No thanks.", title = "Helm of Neitiznot") != 1) return true
    if (player.inventory.getItemCount(Items.COINS_995) < 50_000) {
        chatNpc("You do not have 50,000 coins.", facialExpression = FacialExpression.SAD)
        return true
    }
    player.inventory.remove(Items.COINS_995, 50_000)
    FoundationRewards.grant(player, Items.HELM_OF_NEITIZNOT, 1)
    chatNpc("Wear it with pride.", facialExpression = FacialExpression.HAPPY)
    return true
}
