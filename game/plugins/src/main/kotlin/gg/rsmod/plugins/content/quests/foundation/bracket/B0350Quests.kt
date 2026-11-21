package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

/** PROJECT48: the mini-quests of combat bracket 3-50 (Q01-Q09), in the order of VOORTGANG.tsv. */
object B0350Quests {
    val ALL: List<ShortQuest> = listOf(ErnestTheChicken, GoblinDiplomacy, ShieldOfArrav, MerlinsCrystal, HolyGrail)

    /** Veronica and Professor Oddenstein stand in the world already (spawns_12340) but have no Talk-to plugin: only bound here. */
    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.VERONICA, null, gg.rsmod.game.model.Direction.NORTH_WEST, listOf("I do hope Ernest is all right in there.")),
            BracketNpc(Npcs.PROFESSOR_ODDENSTEIN, null, gg.rsmod.game.model.Direction.EAST, listOf("Be careful what you touch in here,", "young one. Some of it bites.")),
            // Goblin Village (spawns_11830, no Talk-to plugin yet): the two generals.
            BracketNpc(Npcs.GENERAL_BENTNOZE_4493, null, gg.rsmod.game.model.Direction.SOUTH, listOf("Grr. Humans. What do you want?")),
            BracketNpc(Npcs.BARAEK, null, gg.rsmod.game.model.Direction.WEST, listOf("Furs! Fine furs! Best prices in Varrock!")),
            BracketNpc(Npcs.STRAVEN, null, gg.rsmod.game.model.Direction.NORTH, listOf("What do you want? I am busy.")),
            BracketNpc(Npcs.SIR_GAWAIN, null, gg.rsmod.game.model.Direction.NORTH, listOf("Camelot stands, and the Round Table with it.")),
            BracketNpc(
                Npcs.THE_LADY_OF_THE_LAKE, null, gg.rsmod.game.model.Direction.NORTH, listOf("The lake keeps what it keeps."),
                extra = { excaliburReclaim() },
            ),
            BracketNpc(Npcs.GALAHAD, null, gg.rsmod.game.model.Direction.NORTH, listOf("A quiet life by the woods suits me.")),
            BracketNpc(Npcs.GENERAL_WARTFACE_4494, null, gg.rsmod.game.model.Direction.EAST, listOf("Bentnoze is wrong about everything. Everything!")),
        )
}

private val VERONICA_POST = Tile(3110, 3330, 0)
private val ODDENSTEIN_POST = Tile(3111, 3367, 2)
private val MANOR_GROUND = Tile(3112, 3368, 0)

// ------------------------------------------------------------------------------------------ Q01 Ernest the Chicken

object ErnestTheChicken : ShortQuest(
    name = "Ernest the Chicken",
    startPoint = "Speak to Veronica south of Draynor Manor.",
    rewards = "4 Quest Points, 300 coins, an antique lamp (500 XP in a skill of your choice).",
    pointReward = 4,
    questId = Varps.ERNEST_THE_CHICKEN_PROGRESS,
    slot = 5,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.VERONICA,
    startNpcName = "Veronica",
    startPlace = "south of Draynor Manor",
    startLocation = VERONICA_POST,
) {
    override val ownSteps: Boolean get() = true

    // OSRS Wiki: "The ability to defend from a level 22 Skeleton."
    override val recommendation = Recommendation.combat(10)

    override val reward =
        QuestReward(
            icon = Items.RUBBER_TUBE,
            lamps = listOf(QuestLamp(500, FoundationRewards.ALL_SKILLS, 1, "Ernest the Chicken")),
            items = listOf(Items.COINS_995 to 300),
            lines = listOf("300 coins", "An antique lamp (500 XP in any skill)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Oh, I'm so worried! My fiance Ernest went into the",
            "manor to ask for directions, and that was an hour",
            "ago. He has not come out again.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll find Ernest for you.", "Not right now.", title = "Start Ernest the Chicken?") != 1) return false
        chatPlayer("I'll go in and find Ernest for you.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Oh, thank you! Be careful in there - the manor is", "full of the undead.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.PROFESSOR_ODDENSTEIN,
                journal = listOf("I should climb to the top floor of ${red("Draynor Manor")}", "and speak to ${red("Professor Oddenstein")}."),
                done = listOf("Professor Oddenstein turned Ernest into a chicken -", "his machine needs a rubber tube."),
                place = "Draynor Manor's top floor",
                location = ODDENSTEIN_POST,
                talk = {
                    chatNpc("Aha, a visitor! Your friend Ernest is the chicken", "over there. My machine went wrong.", facialExpression = FacialExpression.LAUGH_EXCITED)
                    chatNpc(
                        "To turn him back I need a new rubber tube. A",
                        "skeleton downstairs has been chewing on mine.",
                        "Fetch it and I shall put him right.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.PROFESSOR_ODDENSTEIN,
                journal = listOf("I must defeat the ${red("skeleton")} (level 22) on the ground", "floor of Draynor Manor and take back the rubber tube."),
                done = listOf("I beat the skeleton and took the rubber tube."),
                place = "Draynor Manor's ground floor",
                location = MANOR_GROUND,
                gives = listOf(Items.RUBBER_TUBE to 1),
                tip = "A level 22 skeleton: bring food and fight it in the open.",
                fight = QuestFight(Npcs.SKELETON, "skeleton", 22, MANOR_GROUND, "Rattle... rattle..."),
                talk = {
                    chatNpc("It is in the room below. Do hurry!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.PROFESSOR_ODDENSTEIN,
                journal = listOf("I should bring the rubber tube to ${red("Professor Oddenstein")}", "on the top floor of Draynor Manor."),
                done = listOf("Oddenstein fixed his machine and Ernest is a man again."),
                place = "Draynor Manor's top floor",
                location = ODDENSTEIN_POST,
                talk = {
                    if (!player.inventory.contains(Items.RUBBER_TUBE)) {
                        chatNpc("Ernest is still a chicken. Where is that tube?", facialExpression = FacialExpression.ANGRY)
                        if (FoundationRewards.waitingInBank(player, listOf(Items.RUBBER_TUBE))) chatNpc(FoundationRewards.IN_BANK_LINE, facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.RUBBER_TUBE)
                        chatNpc("Splendid! The machine hums again. Stand back...", facialExpression = FacialExpression.LAUGH_EXCITED)
                        chatNpc("There! Ernest is himself again. Veronica will be", "pleased - and so am I.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}

// ------------------------------------------------------------------------------------------ Q02 Goblin Diplomacy

private val GOBLIN_VILLAGE = Tile(2957, 3512, 0)
private val AGGIE_POST = Tile(3086, 3261, 0)

object GoblinDiplomacy : ShortQuest(
    name = "Goblin Diplomacy",
    startPoint = "Speak to General Bentnoze or General Wartface in Goblin Village.",
    rewards = "5 Quest Points, 200 Crafting XP, a gold bar.",
    pointReward = 5,
    questId = Varbits.GOBLIN_DIPLOMACY_PROGRESS,
    slot = 6,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.GENERAL_BENTNOZE_4493,
    startNpcName = "General Bentnoze",
    startPlace = "Goblin Village",
    startLocation = GOBLIN_VILLAGE,
) {
    override val ownSteps: Boolean get() = true

    override val reward =
        QuestReward(
            icon = Items.GOBLIN_MAIL,
            xp = listOf(Skills.CRAFTING to 200.0),
            items = listOf(Items.GOLD_BAR to 1),
            lines = listOf("200 Crafting XP", "A gold bar"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The goblin race is on the brink of civil war! Our",
            "armour must be orange, but General Wartface insists",
            "it stays the colour it always was.",
            facialExpression = FacialExpression.ANGRY,
        )
        chatNpc("Surely a human can settle this? Take a look at", "the armour, and make a suggestion.", facialExpression = FacialExpression.CALM_TALK)
        if (options("I'll help you settle this.", "Not right now.", title = "Start Goblin Diplomacy?") != 1) return false
        chatPlayer("Maybe I can find some orange armour for you.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Aggie in Draynor Village dyes things for a living.", "Ask her.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.AGGIE,
                journal = listOf("I should visit ${red("Aggie")} in Draynor Village: she can", "dye goblin mail for the generals."),
                done = listOf("Aggie dyed me an orange and a blue goblin mail."),
                place = "Draynor Village",
                location = AGGIE_POST,
                gives = listOf(Items.ORANGE_GOBLIN_MAIL to 1, Items.BLUE_GOBLIN_MAIL to 1, Items.GOBLIN_MAIL to 1),
                talk = {
                    chatNpc("So the goblins are quarrelling over their armour", "again? Hold still, dearie...", facialExpression = FacialExpression.EVIL)
                    chatNpc("There: one orange, one blue, and a plain one as well.", "Do take them back to those silly generals.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.ORANGE_GOBLIN_MAIL, 1)
                    FoundationRewards.grant(player, Items.BLUE_GOBLIN_MAIL, 1)
                    FoundationRewards.grant(player, Items.GOBLIN_MAIL, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GENERAL_BENTNOZE_4493,
                journal = listOf("I should show the orange and blue goblin mail to", "${red("General Bentnoze")} in Goblin Village."),
                done = listOf("The generals refused both the orange and the blue mail."),
                place = "Goblin Village",
                location = GOBLIN_VILLAGE,
                talk = {
                    if (!player.inventory.contains(Items.ORANGE_GOBLIN_MAIL) || !player.inventory.contains(Items.BLUE_GOBLIN_MAIL)) {
                        chatNpc("Where is the orange armour? And the blue one?", facialExpression = FacialExpression.ANGRY)
                        false
                    } else {
                        player.inventory.remove(Items.ORANGE_GOBLIN_MAIL)
                        player.inventory.remove(Items.BLUE_GOBLIN_MAIL)
                        chatNpc("Orange? That is NOT the colour I wanted!", "Blue is no better. Bah!", facialExpression = FacialExpression.ANGRY)
                        chatPlayer("Then what about the plain goblin mail?", facialExpression = FacialExpression.THINKING)
                        chatNpc("Plain? Hmm... show it to General Wartface, there.", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GENERAL_WARTFACE_4494,
                journal = listOf("I should offer the plain goblin mail to ${red("General Wartface")}."),
                done = listOf("Both generals agreed to wear the plain brown mail."),
                place = "Goblin Village",
                location = GOBLIN_VILLAGE,
                talk = {
                    if (!player.inventory.contains(Items.GOBLIN_MAIL)) {
                        chatNpc("I want to see the brown goblin mail first!", facialExpression = FacialExpression.ANGRY)
                        false
                    } else {
                        player.inventory.remove(Items.GOBLIN_MAIL)
                        chatNpc("Brown, the colour we always wore. I like it!", facialExpression = FacialExpression.HAPPY)
                        chatNpc("The war is over. You have the goblins' thanks.", "Take this gold bar, human.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}

// ------------------------------------------------------------------------------------------ Q03 Shield of Arrav

private val RELDO_POST = Tile(3208, 3496, 0)
private val BARAEK_POST = Tile(3217, 3435, 0)
private val BLUE_MOON_INN = Tile(3223, 3401, 0)
private val PHOENIX_HQ = Tile(3246, 9781, 0)
private val VARROCK_PALACE_KING = Tile(3220, 3471, 0)

object ShieldOfArrav : ShortQuest(
    name = "Shield of Arrav",
    startPoint = "Speak to Reldo in Varrock Palace's library.",
    rewards = "1 Quest Point, 600 coins, an antique lamp (500 XP in a skill of your choice).",
    pointReward = 1,
    questId = Varps.SHIELD_OF_ARRAV_PROGRESS_1,
    slot = 15,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.RELDO,
    startNpcName = "Reldo",
    startPlace = "Varrock Palace's library",
    startLocation = RELDO_POST,
) {
    override val ownSteps: Boolean get() = true

    override val reward =
        QuestReward(
            icon = Items.BROKEN_SHIELD,
            lamps = listOf(QuestLamp(500, FoundationRewards.ALL_SKILLS, 1, "Shield of Arrav")),
            items = listOf(Items.COINS_995 to 600),
            lines = listOf("600 coins", "An antique lamp (500 XP in any skill)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The Shield of Arrav is the most famous treasure of",
            "Varrock. It was broken in two, and both halves are",
            "said to lie with the Phoenix Gang and the Black Arm Gang.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll look for the shield halves.", "Not right now.", title = "Start Shield of Arrav?") != 1) return false
        chatPlayer("I'll see what I can find out.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Baraek in the square sells furs - and rumours.", "Ask him about the Phoenix Gang.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.BARAEK,
                journal = listOf("I should speak to ${red("Baraek")} in Varrock's square: he", "knows where the Phoenix Gang hides."),
                done = listOf("Baraek told me where the Phoenix Gang hides."),
                place = "Varrock's square",
                location = BARAEK_POST,
                talk = {
                    chatNpc("The Phoenix Gang? That information will cost you", "20 coins.", facialExpression = FacialExpression.SECRETLY_TALKING)
                    if (player.inventory.getItemCount(Items.COINS_995) < 20) {
                        chatPlayer("I don't have 20 coins on me.", facialExpression = FacialExpression.SAD)
                        false
                    } else {
                        player.inventory.remove(Items.COINS_995, 20)
                        chatNpc(
                            "Their hideout is in the sewers under south-east",
                            "Varrock. Straven wants a spy of theirs dealt with:",
                            "Jonny the Beard, in the Blue Moon Inn.",
                            facialExpression = FacialExpression.SECRETLY_TALKING,
                        )
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.BARAEK,
                journal = listOf("I must defeat ${red("Jonny the Beard")} (level 2) in the Blue Moon", "Inn and take his intel report."),
                done = listOf("I defeated Jonny the Beard and took his intel report."),
                place = "the Blue Moon Inn",
                location = BLUE_MOON_INN,
                gives = listOf(Items.INTEL_REPORT to 1),
                tip = "Jonny is a level 2 informer: an easy fight.",
                fight = QuestFight(Npcs.JONNY_THE_BEARD, "Jonny the Beard", 2, BLUE_MOON_INN, "Not the beard!"),
                talk = {
                    chatNpc("Jonny the Beard drinks in the Blue Moon Inn.", "He carries an intel report. Take it from him.", facialExpression = FacialExpression.SECRETLY_TALKING)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.STRAVEN,
                journal = listOf("I should bring the intel report to ${red("Straven")} in the", "Phoenix Gang's hideout under Varrock."),
                done = listOf("Straven paid me with the two halves of the shield."),
                place = "the Phoenix Gang hideout",
                location = PHOENIX_HQ,
                talk = {
                    if (!player.inventory.contains(Items.INTEL_REPORT)) {
                        chatNpc("You have no intel report. Jonny the Beard has it.", facialExpression = FacialExpression.ANGRY)
                        if (FoundationRewards.waitingInBank(player, listOf(Items.INTEL_REPORT))) chatNpc(FoundationRewards.IN_BANK_LINE, facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.INTEL_REPORT)
                        chatNpc("The Black Arm's plans! Good work. Take both halves", "of the shield to King Roald.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.BROKEN_SHIELD, 1)
                        FoundationRewards.grant(player, Items.BROKEN_SHIELD_765, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.KING_ROALD,
                journal = listOf("I should take both halves of the shield to ${red("King Roald")}", "in Varrock Palace."),
                done = listOf("King Roald took the shield halves and paid me 600 coins."),
                place = "Varrock Palace",
                location = VARROCK_PALACE_KING,
                talk = {
                    if (!player.inventory.contains(Items.BROKEN_SHIELD) || !player.inventory.contains(Items.BROKEN_SHIELD_765)) {
                        chatNpc("I would like to see both halves of the shield.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.BROKEN_SHIELD)
                        player.inventory.remove(Items.BROKEN_SHIELD_765)
                        chatNpc("The Shield of Arrav, whole at last! Varrock thanks", "you - here is your reward.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}

// ------------------------------------------------------------------------------------------ Q04 Merlin's Crystal

private val CAMELOT_COURT = Tile(2761, 3512, 0)
private val GAWAIN_POST = Tile(2760, 3501, 0)
private val KEEP_LE_FAYE = Tile(2769, 3402, 2)
private val LAKE_LADY_POST = Tile(2924, 3405, 0)

object MerlinsCrystal : ShortQuest(
    name = "Merlin's Crystal",
    startPoint = "Speak to King Arthur in Camelot Castle.",
    rewards = "6 Quest Points, 1,000 Firemaking XP, Excalibur.",
    pointReward = 6,
    questId = Varps.MERLINS_CRYSTAL_PROGRESS,
    slot = 72,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_ARTHUR,
    startNpcName = "King Arthur",
    startPlace = "Camelot Castle",
    startLocation = CAMELOT_COURT,
) {
    override val ownSteps: Boolean get() = true

    override val reward =
        QuestReward(
            icon = Items.EXCALIBUR,
            xp = listOf(Skills.FIREMAKING to 1000.0),
            lines = listOf("1,000 Firemaking XP", "Excalibur (from the Lady of the Lake)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Merlin, my wizard, has been trapped in a crystal by",
            "a dark force. If you want to prove yourself to the",
            "Knights of the Round Table, set him free.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll free Merlin.", "Not right now.", title = "Start Merlin's Crystal?") != 1) return false
        chatPlayer("I'll find a way to break the crystal.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Sir Gawain knows what happened. Speak to him.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.SIR_GAWAIN,
                journal = listOf("I should speak to ${red("Sir Gawain")} in Camelot Castle about how", "to free Merlin."),
                done = listOf("Sir Gawain said Morgan Le Faye knows how to break the", "crystal."),
                place = "Camelot Castle",
                location = GAWAIN_POST,
                talk = {
                    chatNpc(
                        "Merlin was taken by Morgan Le Faye's magic. Her son,",
                        "Sir Mordred, guards Keep Le Faye to the south.",
                        "Defeat him and she will give up her secret.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SIR_GAWAIN,
                journal = listOf("I must defeat ${red("Sir Mordred")} (level 39) at Keep Le Faye."),
                done = listOf("I defeated Sir Mordred; Morgan Le Faye begged for", "mercy: the crystal breaks with Excalibur."),
                place = "Keep Le Faye",
                location = KEEP_LE_FAYE,
                tip = "Sir Mordred hits with melee. Bring food.",
                fight = QuestFight(Npcs.SIR_MORDRED, "Sir Mordred", 39, KEEP_LE_FAYE, "You dare enter my mother's keep?"),
                talk = {
                    chatNpc("Keep Le Faye lies south of here. Mordred waits there.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.THE_LADY_OF_THE_LAKE,
                journal = listOf("Morgan Le Faye said only ${red("Excalibur")} can break the crystal.", "I should speak to the ${red("Lady of the Lake")} in Taverley."),
                done = listOf("The Lady of the Lake gave me Excalibur."),
                place = "the lake in Taverley",
                location = LAKE_LADY_POST,
                talk = {
                    chatNpc("You seek Excalibur? I give it only to one who is", "worthy and good... You defeated the dark knight.", facialExpression = FacialExpression.CALM_TALK)
                    chatNpc("Take it, and use it well.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.EXCALIBUR, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_ARTHUR,
                journal = listOf("I should show ${red("Excalibur")} to ${red("King Arthur")} in Camelot Castle."),
                done = listOf("King Arthur smashed the crystal with Excalibur: Merlin is free."),
                place = "Camelot Castle",
                location = CAMELOT_COURT,
                talk = {
                    if (!player.inventory.contains(Items.EXCALIBUR) && !player.equipment.contains(Items.EXCALIBUR)) {
                        chatNpc("Where is Excalibur? Merlin needs it to be freed.", facialExpression = FacialExpression.WORRIED)
                        false
                    } else {
                        chatNpc("Excalibur! With it the crystal will break.", "Merlin is free - Camelot thanks you.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}

/**
 * ONTGR (Merlin's Crystal): Excalibur comes back from the Lady of the Lake for everybody who has the quest (played, given by the
 * bracket, or a legacy account) and holds no Excalibur anywhere - OSRS: the Lady returns a lost one. True when she spoke.
 */
private suspend fun QueueTask.excaliburReclaim(): Boolean {
    if (!gg.rsmod.plugins.content.quests.QuestStatus.isCompleted(player, "Merlin's Crystal")) return false
    val held = player.inventory.contains(Items.EXCALIBUR) || player.bank.contains(Items.EXCALIBUR) || player.equipment.contains(Items.EXCALIBUR)
    if (held) return false
    chatNpc("You have lost Excalibur? It is yours by right.", "Take it back, and guard it better.", facialExpression = FacialExpression.CALM_TALK)
    FoundationRewards.grant(player, Items.EXCALIBUR, 1)
    return true
}

// ------------------------------------------------------------------------------------------ Q05 Holy Grail

private val MERLIN_POST = Tile(2757, 3502, 0)
private val GALAHAD_POST = Tile(2612, 3474, 0)
private val TITAN_BRIDGE = Tile(2791, 4722, 0)

object HolyGrail : ShortQuest(
    name = "Holy Grail",
    startPoint = "Speak to King Arthur in Camelot Castle.",
    rewards = "2 Quest Points, 11,000 Prayer XP, 15,300 Defence XP, access to the Fisher Realm.",
    pointReward = 2,
    questId = Varps.HOLY_GRAIL_PROGRESS,
    slot = 61,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.KING_ARTHUR,
    startNpcName = "King Arthur",
    startPlace = "Camelot Castle",
    startLocation = CAMELOT_COURT,
) {
    override val ownSteps: Boolean get() = true

    // OSRS Wiki Holy Grail: Attack 20 (not boostable), Merlin's Crystal; "must be able to defeat a level 120 Black Knight Titan".
    override val needs =
        QuestRequirements(
            skills = listOf(Skills.ATTACK to 20),
            quests = listOf("Merlin's Crystal"),
            extra = listOf("Must be able to defeat a level 120 Black Knight Titan."),
        )

    override val recommendation = Recommendation.combat(50)

    override val reward =
        QuestReward(
            icon = Items.HOLY_GRAIL,
            xp = listOf(Skills.PRAYER to 11_000.0, Skills.DEFENCE to 15_300.0),
            lines = listOf("11,000 Prayer XP", "15,300 Defence XP", "Access to the Fisher Realm"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Now that Merlin is free, a new quest awaits: the Holy",
            "Grail itself has been lost. Find it, and you will earn",
            "your place at the Round Table.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll find the Holy Grail.", "Not right now.", title = "Start Holy Grail?") != 1) return false
        chatPlayer("I will search for the Holy Grail.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to Merlin. He saw it in a vision.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.MERLIN,
                journal = listOf("I should speak to ${red("Merlin")} in Camelot Castle about the Grail."),
                done = listOf("Merlin told me to find Sir Galahad."),
                place = "Camelot Castle",
                location = MERLIN_POST,
                talk = {
                    chatNpc(
                        "The Grail was last seen in the Fisher Realm. Sir",
                        "Galahad, who lives west of McGrubor's Wood, kept",
                        "a napkin from the Grail's table. Ask him.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GALAHAD,
                journal = listOf("I should speak to ${red("Sir Galahad")} in his house west of", "McGrubor's Wood."),
                done = listOf("Sir Galahad gave me the Holy table napkin."),
                place = "Sir Galahad's house",
                location = GALAHAD_POST,
                gives = listOf(Items.HOLY_TABLE_NAPKIN to 1),
                talk = {
                    chatNpc(
                        "The Holy Grail? I have only the napkin from its table.",
                        "It will open the way to the Fisher Realm.",
                        "Take it - the Grail's keeper will let you pass.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    FoundationRewards.grant(player, Items.HOLY_TABLE_NAPKIN, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GALAHAD,
                journal = listOf("I must defeat the ${red("Black Knight Titan")} (level 120) in the Fisher", "Realm and take the Holy Grail."),
                done = listOf("I defeated the Black Knight Titan and took the Holy Grail."),
                place = "the Fisher Realm",
                location = TITAN_BRIDGE,
                gives = listOf(Items.HOLY_GRAIL to 1),
                tip = "Melee only: the Titan's magic and ranged defence is huge. Bring plenty of food.",
                fight = QuestFight(Npcs.BLACK_KNIGHT_TITAN, "Black Knight Titan", 120, TITAN_BRIDGE, "Who dares enter the Fisher Realm?"),
                talk = {
                    chatNpc("The Titan guards the Grail on the bridge beyond.", "Fight him with melee, and bring food.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_ARTHUR,
                journal = listOf("I should bring the Holy Grail to ${red("King Arthur")} in Camelot."),
                done = listOf("King Arthur received the Holy Grail."),
                place = "Camelot Castle",
                location = CAMELOT_COURT,
                talk = {
                    if (!player.inventory.contains(Items.HOLY_GRAIL)) {
                        chatNpc("Where is the Holy Grail?", facialExpression = FacialExpression.ANGRY)
                        if (FoundationRewards.waitingInBank(player, listOf(Items.HOLY_GRAIL))) chatNpc(FoundationRewards.IN_BANK_LINE, facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.HOLY_GRAIL)
                        chatNpc("The Holy Grail! You have the thanks of all Camelot.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
