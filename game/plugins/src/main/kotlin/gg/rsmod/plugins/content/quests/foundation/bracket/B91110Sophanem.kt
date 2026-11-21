package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q29 Icthlarin's Little Helper + Q30 Contact! (bracket 91-110). OSRS Wiki:
//  - Icthlarin's Little Helper: Gertrude's Cat; a level 91 Possessed Priest; 2 QP, 4,500 Thieving, 4,000 Agility, 4,000 Woodcutting XP,
//    Catspeak amulet. ONTGR not built: the magic carpet rides to Sophanem/Menaphos (no carpet system on this server).
//  - Contact!: Prince Ali Rescue + Icthlarin's Little Helper; a level 191 Giant scarab; 1 QP, 7,000 Thieving XP, a 2 x 7,000 combat
//    lamp, the Keris. ONTGR: the Keris is only obtained here (the quest reward); Sophanem's shops are not gated.
// Positions: Void donor spawns (wanderer 3315,2849 / sphinx / embalmer / jex); the High Priest (no donor tile) stands next to the Sphinx.

private val WANDERER_POST = Tile(3315, 2849, 0)
private val SPHINX_POST = Tile(3298, 2787, 0)
private val HIGH_PRIEST_POST = Tile(3296, 2790, 0)
private val EMBALMER_POST = Tile(3282, 2766, 0)
private val PRIEST_FIGHT = Tile(3305, 2796, 0)
private val JEX_POST = Tile(3302, 2801, 0)
private val MAISA_POST = Tile(3299, 2794, 0)
private val OSMAN_POST = Tile(3286, 3180, 0)
private val SCARAB_FIGHT = Tile(3306, 2806, 0)

object IcthlarinsLittleHelper : ShortQuest(
    name = "Icthlarin's Little Helper",
    startPoint = "Speak to the wanderer in the camp west of the Agility Pyramid.",
    rewards = "2 Quest Points, 4,500 Thieving XP, 4,000 Agility XP, 4,000 Woodcutting XP, the Catspeak amulet.",
    pointReward = 2,
    questId = Varbits.ICTHLARINS_LITTLE_HELPER_PROGRESS,
    slot = 63,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.WANDERER,
    startNpcName = "Wanderer",
    startPlace = "the camp west of the Agility Pyramid",
    startLocation = WANDERER_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(quests = listOf("Gertrude's Cat"), extra = listOf("Must be able to defeat a level 91 monster."))

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.CATSPEAK_AMULET,
            xp = listOf(Skills.THIEVING to 4_500.0, Skills.AGILITY to 4_000.0, Skills.WOODCUTTING to 4_000.0),
            items = listOf(Items.CATSPEAK_AMULET to 1),
            lines = listOf("2 Quest Points", "4,500 Thieving XP", "4,000 Agility XP", "4,000 Woodcutting XP", "The Catspeak amulet", "Access to the city of Sophanem"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "My cat has run off into the city of Sophanem, and the",
            "guards will not let me in. Please find my cat for me.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll find your cat.", "Not right now.", title = "Start Icthlarin's Little Helper?") != 1) return false
        chatPlayer("I'll go to Sophanem.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to the Sphinx: he knows the city's secrets.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.SPHINX,
                journal = listOf("I should speak to the ${red("Sphinx")} in Sophanem."),
                done = listOf("The Sphinx told me to see the High Priest."),
                place = "Sophanem",
                location = SPHINX_POST,
                talk = {
                    chatNpc("The cat was last seen near the temple. Speak to the High", "Priest of Icthlarin.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.HIGH_PRIEST_1986,
                journal = listOf("I should speak to the ${red("High Priest")} in the temple of Icthlarin."),
                done = listOf("The High Priest sent me to the Embalmer."),
                place = "the temple of Icthlarin",
                location = HIGH_PRIEST_POST,
                talk = {
                    chatNpc("A restless spirit has taken the priest below the temple.", "Fetch a holy symbol from the Embalmer to subdue it.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EMBALMER,
                journal = listOf("I should ask the ${red("Embalmer")} for a holy symbol."),
                done = listOf("The Embalmer gave me a holy symbol."),
                place = "Sophanem",
                location = EMBALMER_POST,
                gives = listOf(Items.HOLY_SYMBOL to 1),
                talk = {
                    chatNpc("A holy symbol of Icthlarin? Take this one.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.HOLY_SYMBOL, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.POSSESSED_PRIEST,
                journal = listOf("I must defeat the ${red("Possessed Priest")} (level 91) beneath the temple."),
                done = listOf("I defeated the Possessed Priest."),
                place = "the temple of Icthlarin",
                location = PRIEST_FIGHT,
                tip = "The Possessed Priest is level 91: bring food.",
                fight = QuestFight(Npcs.POSSESSED_PRIEST, "Possessed Priest", 91, PRIEST_FIGHT, "You cannot stop the master!"),
                talk = {
                    chatNpc("The possessed priest waits below the temple.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.HIGH_PRIEST_1986,
                journal = listOf("I should tell the ${red("High Priest")} the possessed priest is gone."),
                done = listOf("The High Priest gave me a Catspeak amulet."),
                place = "the temple of Icthlarin",
                location = HIGH_PRIEST_POST,
                talk = {
                    chatNpc("The spirit is gone! Take this Catspeak amulet: with it", "you can understand the cats of the desert.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

object Contact : ShortQuest(
    name = "Contact!",
    startPoint = "Speak to the High Priest of Icthlarin in Sophanem.",
    rewards = "1 Quest Point, 7,000 Thieving XP, a combat lamp (2 x 7,000 XP), the Keris dagger.",
    pointReward = 1,
    questId = Varbits.CONTACT_PROGRESS,
    slot = 24,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.HIGH_PRIEST_1986,
    startNpcName = "High Priest",
    startPlace = "Sophanem",
    startLocation = HIGH_PRIEST_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            quests = listOf("Prince Ali Rescue", "Icthlarin's Little Helper"),
            extra = listOf("Must be able to defeat a level 191 Giant scarab."),
        )

    override val recommendation = Recommendation.combat(90)

    override val reward =
        QuestReward(
            icon = Items.KERIS,
            xp = listOf(Skills.THIEVING to 7_000.0),
            lamps = listOf(QuestLamp(7_000, listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC), 1, "Contact! combat")),
            items = listOf(Items.KERIS to 1),
            lines = listOf("1 Quest Point", "7,000 Thieving XP", "A combat lamp (7,000 XP)", "The Keris dagger"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Something stirs beneath Sophanem: scarabs gather under",
            "the city. Will you help me find who leads them?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start Contact!?") != 1) return false
        chatPlayer("I'll find out who leads them.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to Jex first: he knows the way into the dungeon.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.JEX,
                journal = listOf("I should speak to ${red("Jex")} in Sophanem about the dungeon."),
                done = listOf("Jex showed me the way into the dungeon."),
                place = "Sophanem",
                location = JEX_POST,
                talk = {
                    chatNpc("The dungeon entrance is below the temple. Maisa has", "gone down there already.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MAISA,
                journal = listOf("I should find ${red("Maisa")} in the dungeon below Sophanem."),
                done = listOf("Maisa told me to bring Osman."),
                place = "the dungeon below Sophanem",
                location = MAISA_POST,
                talk = {
                    chatNpc("The scarabs are led by a giant one! Bring Osman: he is", "the spymaster and knows what to do.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.OSMAN,
                journal = listOf("I should bring ${red("Osman")} in Al Kharid to Sophanem."),
                done = listOf("Osman came with me to Sophanem."),
                place = "Al Kharid",
                location = OSMAN_POST,
                talk = {
                    chatNpc("The giant scarab at last! I shall come at once.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GIANT_SCARAB,
                journal = listOf("I must defeat the ${red("Giant scarab")} (level 191) in the dungeon."),
                done = listOf("I defeated the Giant scarab."),
                place = "the dungeon below Sophanem",
                location = SCARAB_FIGHT,
                tip = "The Giant scarab is level 191. Bring food.",
                fight = QuestFight(Npcs.GIANT_SCARAB, "Giant scarab", 191, SCARAB_FIGHT, "SKREEEE!"),
                talk = {
                    chatNpc("The Giant scarab waits in the dungeon.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.HIGH_PRIEST_1986,
                journal = listOf("I should tell the ${red("High Priest")} the Giant scarab is dead."),
                done = listOf("The High Priest rewarded me with the Keris."),
                place = "Sophanem",
                location = HIGH_PRIEST_POST,
                talk = {
                    chatNpc("The scarab is dead! Take this Keris: a blade made", "for the creatures of the desert.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
