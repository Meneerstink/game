package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Direction
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/** PROJECT48: the mini-quests of combat bracket 51-70 (Q10-Q16), in the order of VOORTGANG.tsv. */
object B5170Quests {
    val ALL: List<ShortQuest> = listOf(FremennikTrials)

    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.BRUNDT_THE_CHIEFTAIN, null, Direction.SOUTH, listOf("Rellekka is a proud town.")),
            BracketNpc(Npcs.SIGLI_THE_HUNTSMAN, null, Direction.SOUTH, listOf("A good hunter reads the tracks.")),
            BracketNpc(Npcs.THORVALD_THE_WARRIOR, null, Direction.SOUTH, listOf("Strength wins the day.")),
        )
}
