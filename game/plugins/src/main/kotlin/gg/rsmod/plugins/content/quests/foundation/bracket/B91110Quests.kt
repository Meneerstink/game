package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/**
 * PROJECT48: the mini-quests of combat bracket 91-110 (Q27-Q33), in the order of VOORTGANG.tsv.
 * Npcs that already have a Talk-to plugin (Guildmaster, Oziach, Ned, Duke Horacio) are NOT listed here: their plugin calls
 * FoundationQuests.talk first.
 */
object B91110Quests {
    val ALL: List<ShortQuest> = listOf(DragonSlayer, HeroesQuest, IcthlarinsLittleHelper, Contact, InSearchOfTheMyreque, InAidOfTheMyreque, DarknessOfHallowvale, ATasteOfHope, ANightAtTheTheatre)

    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.ORACLE, null, Direction.SOUTH, listOf("The ice speaks to those who listen.")),
            BracketNpc(Npcs.ACHIETTIES, Tile(2900, 3512, 0), Direction.SOUTH, listOf("The Heroes' Guild admits only true heroes.")),
            BracketNpc(Npcs.STRAVEN, null, Direction.SOUTH, listOf("The Phoenix Gang pays well for loyalty.")),
            BracketNpc(Npcs.GRIP, null, Direction.SOUTH, listOf("Grrr!")),
            BracketNpc(Npcs.WANDERER, Tile(3315, 2849, 0), Direction.SOUTH, listOf("The desert wind never rests.")),
            BracketNpc(Npcs.SPHINX, null, Direction.SOUTH, listOf("I have guarded this city for many years.")),
            BracketNpc(Npcs.EMBALMER, null, Direction.SOUTH, listOf("Death is only the beginning.")),
            BracketNpc(Npcs.HIGH_PRIEST_1986, Tile(3296, 2790, 0), Direction.SOUTH, listOf("Icthlarin watches over all souls.")),
            BracketNpc(Npcs.JEX, null, Direction.SOUTH, listOf("Mind the dust.")),
            BracketNpc(Npcs.MAISA, Tile(3299, 2794, 0), Direction.SOUTH, listOf("The scarabs are everywhere.")),
            BracketNpc(Npcs.VANSTROM_KLAUSE, Tile(3496, 3492, 0), Direction.SOUTH, listOf("Mind your own business, stranger.")),
            BracketNpc(Npcs.CYREG_PADDLEHORN, null, Direction.SOUTH, listOf("The swamp is no place for the careless.")),
            BracketNpc(Npcs.CURPILE_FYOD, Tile(3508, 3440, 0), Direction.SOUTH, listOf("Keep your voice down.")),
            BracketNpc(Npcs.VELIAF_HURTZ, Tile(3494, 9628, 0), Direction.SOUTH, listOf("The Myreque will not fall.")),
            BracketNpc(Npcs.VELIAF_HURTZ, Tile(3515, 3242, 0), Direction.SOUTH, listOf("Burgh de Rott is ours again.")),
            BracketNpc(Npcs.FLORIN, Tile(3483, 3242, 0), Direction.SOUTH, listOf("The gate holds, for now.")),
            BracketNpc(Npcs.RAZVAN, Tile(3492, 3235, 0), Direction.SOUTH, listOf("Burgh de Rott is slowly coming back to life.")),
            BracketNpc(Npcs.IVAN_STROM, Tile(3484, 3243, 0), Direction.SOUTH, listOf("I just want to go home.")),
            BracketNpc(Npcs.OLD_MAN_RAL, null, Direction.SOUTH, listOf("Meiyerditch is a grim place.")),
            BracketNpc(Npcs.SAFALAAN, Tile(3589, 3335, 0), Direction.SOUTH, listOf("The Myreque endure.")),
            // PROJECT48 X2/X3: A Taste of Hope + A Night at the Theatre (Ver Sinhaza).
            BracketNpc(XNpcs.GARTH, Tile(3667, 3216, 0), Direction.SOUTH, listOf("Serafina was the bravest of us all.")),
            BracketNpc(XNpcs.MYSTERIOUS_STRANGER, Tile(3672, 3225, 0), Direction.SOUTH, listOf("...")),
            BracketNpc(Npcs.SARIUS_GUILE, Tile(3590, 3337, 0), Direction.SOUTH, listOf("Knowledge is the best weapon.")),
            BracketNpc(Npcs.WORMBRAIN, null, Direction.SOUTH, listOf("Me no talk to you!")),
        )
}
