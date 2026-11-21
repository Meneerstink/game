package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

/** PROJECT48: the mini-quests of combat bracket 71-90 (Q17-Q26), in the order of VOORTGANG.tsv. */
object B7190Quests {
    val ALL: List<ShortQuest> = listOf(TreeGnomeVillage)

    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.KING_BOLREN, null, Direction.SOUTH, listOf("The gnomes thank you for your help, friend.")),
            BracketNpc(Npcs.COMMANDER_MONTAI, null, Direction.SOUTH, listOf("The battle against the Khazard troops is far from over.")),
            BracketNpc(Npcs.TRACKER_GNOME_1, null, Direction.SOUTH, listOf("Shh - the Khazard guards are not far from here.")),
        )
}

// PROJECT48 Q17: Tree Gnome Village (bracket 71-90). OSRS Wiki: no skill requirements; 2 QP, 11,450 Attack XP, the Gnome amulet and
// the use of the spirit trees. ONTGR: spirit trees + the Trinket of fairies' tree route (mechanics\travel\SpiritTrees.kt, QuestStatus gate).

private val BOLREN_POST = Tile(2542, 3169, 0)
private val MONTAI_POST = Tile(2523, 3211, 0)
private val TRACKER_POST = Tile(2502, 3261, 0)
private val WARLORD_FIGHT = Tile(2457, 3302, 0)

object TreeGnomeVillage : ShortQuest(
    name = "Tree Gnome Village",
    startPoint = "Speak to King Bolren in the centre of the Tree Gnome Village maze.",
    rewards = "2 Quest Points, 11,450 Attack XP, the Gnome amulet, the use of the spirit trees.",
    pointReward = 2,
    questId = Varps.TREE_GNOME_VILLAGE_PROGRESS,
    slot = 110,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_BOLREN,
    startNpcName = "King Bolren",
    startPlace = "the Tree Gnome Village",
    startLocation = BOLREN_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(extra = listOf("Must be able to defeat a level 112 Khazard warlord (it can be safespotted)."))

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.GNOME_AMULET,
            xp = listOf(Skills.ATTACK to 11_450.0),
            items = listOf(Items.GNOME_AMULET to 1),
            lines = listOf("2 Quest Points", "11,450 Attack XP", "The Gnome amulet", "Use of the spirit trees"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The Khazard troops have stolen the three orbs of",
            "protection that keep our village safe. Without them the",
            "enemy walks in. Will you help us get them back?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start Tree Gnome Village?") != 1) return false
        chatPlayer("I'll get your orbs back.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Commander Montai leads the defence at the battlefield. Go and see him.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.COMMANDER_MONTAI,
                journal = listOf("I should speak to ${red("Commander Montai")} at the battlefield south of", "the village."),
                done = listOf("Commander Montai told me where the orbs went."),
                place = "the Khazard battlefield",
                location = MONTAI_POST,
                talk = {
                    chatNpc("The warlord took the orbs to his camp. One of my", "trackers watched him go - find the tracker gnome.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.TRACKER_GNOME_1,
                journal = listOf("I should find the ${red("tracker gnome")} near the Khazard fort."),
                done = listOf("The tracker gnome told me where the Khazard warlord is."),
                place = "near the Khazard fort",
                location = TRACKER_POST,
                talk = {
                    chatNpc("The Khazard warlord carries the orbs. He stands just", "outside the fort - be ready, he is a level 112 fighter.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.TRACKER_GNOME_1,
                journal = listOf("I must defeat the ${red("Khazard warlord")} (level 112) to take the", "orbs of protection."),
                done = listOf("I defeated the Khazard warlord and took the orbs."),
                place = "outside the Khazard fort",
                location = WARLORD_FIGHT,
                tip = "The Khazard warlord can be safespotted in OSRS. Bring good food.",
                gives = listOf(Items.ORBS_OF_PROTECTION to 1),
                fight = QuestFight(Npcs.KHAZARD_WARLORD, "Khazard warlord", 112, WARLORD_FIGHT, "You shall not leave with those orbs!"),
                talk = {
                    chatNpc("The warlord is camped over there. Take the orbs", "from him!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_BOLREN,
                journal = listOf("I should return the ${red("orbs of protection")} to ${red("King Bolren")}."),
                done = listOf("I returned the orbs of protection to King Bolren."),
                place = "the Tree Gnome Village",
                location = BOLREN_POST,
                talk = {
                    if (!player.inventory.contains(Items.ORBS_OF_PROTECTION)) {
                        chatNpc("You have not brought the orbs yet. The warlord has them.", facialExpression = FacialExpression.WORRIED)
                        false
                    } else {
                        player.inventory.remove(Items.ORBS_OF_PROTECTION)
                        chatNpc("The orbs! The village is safe. You have our eternal", "thanks - and the spirit trees are open to you.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
