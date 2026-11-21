package gg.rsmod.plugins.content.quests.foundation.bracket

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

// PROJECT48 Q24: Fight Arena (bracket 71-90). OSRS Wiki: no requirements, fights against a level 63 Khazard ogre, a level 44 Khazard
// scorpion and a level 137 Bouncer; 2 QP, 12,175 Attack XP, 2,175 Thieving XP, 1,000 coins, the Khazard armour set. ONTGR: none.
// Estimated tiles (no spawn source for the Servils): Lady Servil's house and Jeremy's cell - see STAND.txt.

private val SERVIL_POST = Tile(2575, 3205, 0)
private val GUARD_POST = Tile(2569, 3149, 0)
private val JEREMY_POST = Tile(2617, 3169, 0)
private val ARENA = Tile(2608, 3163, 0)

object FightArena : ShortQuest(
    name = "Fight Arena",
    startPoint = "Speak to Lady Servil west of the Ardougne Monastery, outside the north-eastern corner of the Gnome Maze.",
    rewards = "2 Quest Points, 12,175 Attack XP, 2,175 Thieving XP, 1,000 coins, the Khazard armour.",
    pointReward = 2,
    questId = Varps.FIGHT_ARENA_PROGRESS,
    slot = 46,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.LADY_SERVIL,
    startNpcName = "Lady Servil",
    startPlace = "west of the Ardougne Monastery",
    startLocation = SERVIL_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(extra = listOf("Must be able to defeat a level 44, a level 63 and a level 137 enemy (all can be safespotted)."))

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.KHAZARD_HELMET,
            xp = listOf(Skills.ATTACK to 12_175.0, Skills.THIEVING to 2_175.0),
            items = listOf(Items.COINS_995 to 1_000), // the Khazard armour itself is handed out at the start (OSRS Wiki)
            lines = listOf("2 Quest Points", "12,175 Attack XP", "2,175 Thieving XP", "1,000 coins", "The Khazard armour"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "My husband and my son were taken to General",
            "Khazard's fight arena. The guards will not let me in. Will",
            "you rescue them?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll rescue them.", "Not right now.", title = "Start Fight Arena?") != 1) return false
        chatPlayer("I'll bring them home.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Take this armour so the guards let you in. Start with the head guard in the prison.", facialExpression = FacialExpression.HAPPY)
        FoundationRewards.grant(player, Items.KHAZARD_HELMET, 1)
        FoundationRewards.grant(player, Items.KHAZARD_ARMOUR, 1)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.KHAZARD_GUARD_254,
                journal = listOf("I should talk to the ${red("head guard")} in the Khazard prison (wearing the", "Khazard armour) and get the cell keys."),
                done = listOf("The head guard got drunk on Khali brew and gave me the cell keys."),
                place = "the Khazard prison",
                location = GUARD_POST,
                gives = listOf(Items.KHAZARD_CELL_KEYS to 1),
                talk = {
                    chatNpc("I would kill for a Khali brew... last time I drank too much", "and fell asleep on the job.", facialExpression = FacialExpression.CALM_TALK)
                    chatPlayer("Here, have a Khali brew on me.", facialExpression = FacialExpression.HAPPY)
                    chatNpc("Hic... thanks... take the keys... hic... if you want.", facialExpression = FacialExpression.LAUGH_EXCITED)
                    FoundationRewards.grant(player, Items.KHAZARD_CELL_KEYS, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.JEREMY_SERVIL,
                journal = listOf("I should free ${red("Jeremy Servil")} from his cell with the ${red("cell keys")}."),
                done = listOf("I freed Jeremy; he told me his father fights in the arena."),
                place = "the Khazard prison cells",
                location = JEREMY_POST,
                talk = {
                    if (!player.inventory.contains(Items.KHAZARD_CELL_KEYS)) {
                        chatNpc("The cell is locked. Get the keys from the head guard!", facialExpression = FacialExpression.SAD)
                        false
                    } else {
                        player.inventory.remove(Items.KHAZARD_CELL_KEYS)
                        chatNpc("You opened the cell! My father is forced to fight in the", "arena - please save him.", facialExpression = FacialExpression.WORRIED)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.JEREMY_SERVIL,
                journal = listOf("I must win the three fights in ${red("General Khazard's arena")}: a Khazard", "ogre (level 63), a Khazard scorpion (44) and the Bouncer (137)."),
                done = listOf("I won the arena fights and Justin Servil was freed."),
                place = "General Khazard's arena",
                location = ARENA,
                tip = "Three foes, one by one. Bring food.",
                fight = QuestFight(
                    Npcs.KHAZARD_OGRE, "Khazard ogre", 63, ARENA, "Grrr! Fresh meat!",
                    chain = listOf(Npcs.KHAZARD_SCORPION to "Skkrrt!", Npcs.BOUNCER to "ROAR!"),
                    chainAtTile = true,
                ),
                talk = {
                    chatNpc("The arena is to the east. Fight the ogre, the scorpion", "and the Bouncer to free my father.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.LADY_SERVIL,
                journal = listOf("I should tell ${red("Lady Servil")} her family is free."),
                done = listOf("Lady Servil rewarded me with the Khazard armour."),
                place = "west of the Ardougne Monastery",
                location = SERVIL_POST,
                talk = {
                    chatNpc("My husband and my son are home safe! Take this", "reward - and keep the armour.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
