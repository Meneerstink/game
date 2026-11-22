package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.OsrsQuestSlots
import gg.rsmod.plugins.content.quests.foundation.OsrsQuestVarps
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 X4-X6 (run 3, 22-11): the last three quests that were not on this server (bracket 111+). OSRS Wiki:
//  - Making Friends with My Arm: My Arm's Big Adventure, Swan Song, Cold War, Romeo & Juliet; 66 Firemaking (not boostable), 72 Mining,
//    35 Construction, 68 Agility; items a saw and a bolt of cloth; Don't Know What (163) and Mother (198) in Weiss; 2 QP,
//    10,000 Construction, 40,000 Firemaking, 50,000 Mining and 50,000 Agility XP.
//  - The General's Shadow (miniquest): Fight Arena; ghostspeak amulet, ghostly robes, ring of visibility, 40 coins; Sin Seer, the four
//    ghost scouts, Bouncer (160, max hit 15); the Shadow sword and 2,000 Slayer XP.
//  - His Faithful Servants (miniquest): Priest in Peril; defeat the six Barrows brothers and loot the chest for the strange icon; a
//    dusty lamp (20,000 Prayer XP) and a crypt map.
//  - Owner 22-11 (choice a): Weiss does not exist - its part is played on the snowy north coast (the same place as Secrets of the North);
//    the Goblin Cave fight is at the cave entrance by the Fishing Guild. Romeo & Juliet is not on this server's quest list, so it is
//    not asked. The ghostly robes and the ring of visibility (Curse of the Empty Lord / Desert Treasure) are not asked: only the
//    ghostspeak amulet. Unlocks that do not exist here (Salt Mines, fire pits, the Weiss herb patch, the crypt map) are not given.
//  - Imported OSRS npcs (batch p48x, 22-11): Mother and Don't Know What ([XNpcs]); the other npcs are the server's own.

private val MY_ARM_POST = Tile(2815, 3683, 0)
private val WEISS_SHORE = Tile(2868, 3938, 0)
private val WEISS_FIGHT = Tile(2863, 3935, 0) // south-west corner of a free 3x3 (clipmap 22-11)
private val WISE_OLD_MAN_POST = Tile(3088, 3255, 0)
private val APOTHECARY_POST = Tile(3195, 3404, 0)

private val KHAZARD_POST = Tile(2712, 3628, 0)
private val SIN_SEER_POST = Tile(2708, 3484, 0)
private val SCOUT_KARAMJA = Tile(2826, 3066, 0)
private val SCOUT_GNOME = Tile(2461, 3374, 0)
private val SCOUT_DRAYNOR = Tile(3072, 3344, 0)
private val SCOUT_DESERT = Tile(3303, 3110, 0)
private val GOBLIN_CAVE = Tile(2619, 3393, 0) // free 3x3 at the Goblin Cave entrance (clipmap 22-11)

private val BARROWS_POST = Tile(3565, 3292, 0)

object MakingFriendsWithMyArm : ShortQuest(
    name = "Making Friends with My Arm",
    startPoint = "Talk to Burntmeat in the Troll Stronghold's kitchen.",
    rewards = "2 Quest Points, 10,000 Construction XP, 40,000 Firemaking XP, 50,000 Mining XP, 50,000 Agility XP.",
    pointReward = 2,
    questId = OsrsQuestVarps.MAKING_FRIENDS_WITH_MY_ARM,
    slot = OsrsQuestSlots.MAKING_FRIENDS_WITH_MY_ARM,
    completedValue = 8, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.BURNTMEAT,
    startNpcName = "Burntmeat",
    startPlace = "the Troll Stronghold's kitchen",
    startLocation = Tile(2845, 10058, 1),
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.FIREMAKING to 66, Skills.MINING to 72, Skills.CONSTRUCTION to 35, Skills.AGILITY to 68),
            quests = listOf("My Arm's Big Adventure", "Swan Song", "Cold War"),
        )

    override val recommendation = Recommendation.combat(90)

    override val reward =
        QuestReward(
            icon = Items.BOLT_OF_CLOTH,
            xp = listOf(Skills.CONSTRUCTION to 10_000.0, Skills.FIREMAKING to 40_000.0, Skills.MINING to 50_000.0, Skills.AGILITY to 50_000.0),
            lines = listOf("2 Quest Points", "10,000 Construction XP", "40,000 Firemaking XP", "50,000 Mining XP", "50,000 Agility XP"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "A troll from Weiss came. Wolfbone. He wants My Arm",
            "to visit his leader, Mother, far to the north. My Arm",
            "needs a friend to go with him.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll go with My Arm.", "Not right now.", title = "Start Making Friends with My Arm?") != 1) return false
        chatPlayer("I'll go with My Arm.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.MY_ARM,
                journal = listOf("I should talk to ${red("My Arm")} on the roof of the Troll Stronghold."),
                done = listOf("My Arm wants to go to Weiss and make friends with the trolls there."),
                place = "the Troll Stronghold",
                location = MY_ARM_POST,
                talk = {
                    chatNpc("My Arm go to Weiss! My Arm make friends! You", "come too, human.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MY_ARM,
                journal = listOf("I should travel with My Arm to ${red("Weiss")}, on the snowy north coast", "(bring a ${red("bolt of cloth")} for the raft)."),
                done = listOf("Mother wanted nothing My Arm offered. Only the head of the Wise Old Man", "would buy his trust."),
                place = "Weiss, the snowy north coast",
                location = WEISS_SHORE,
                tip = "Bring a bolt of cloth.",
                search =
                    QuestSearch(
                        WEISS_SHORE,
                        "You patch the raft's sail and reach Weiss. Mother laughs at My Arm: bring me the Wise Old Man, dead.",
                        3,
                        needs = listOf(Items.BOLT_OF_CLOTH to 1),
                        missing = "You need a bolt of cloth to patch the raft's sail.",
                    ),
            ),
            QuestStep(
                npc = Npcs.WISE_OLD_MAN,
                journal = listOf("I should ask the ${red("Wise Old Man")} in Draynor Village to help My Arm."),
                done = listOf("The Wise Old Man will play dead with a reduced cadava potion."),
                place = "Draynor Village",
                location = WISE_OLD_MAN_POST,
                talk = {
                    chatNpc(
                        "Kill me? Ha! But I can pretend. The Apothecary in",
                        "Varrock can make a reduced cadava potion - it makes",
                        "you look dead for a while.",
                        facialExpression = FacialExpression.LAUGHING,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.APOTHECARY,
                journal = listOf("I should ask the ${red("Apothecary")} in Varrock for a reduced cadava potion", "(bring ${red("cadava berries")})."),
                done = listOf("The Apothecary made a reduced cadava potion."),
                place = "Varrock",
                location = APOTHECARY_POST,
                tip = "Bring cadava berries.",
                talk = {
                    if (player.inventory.getItemCount(Items.CADAVA_BERRIES) < 1) {
                        chatNpc("A reduced cadava potion? Bring me some cadava berries", "and I will make one.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.CADAVA_BERRIES, 1)
                        chatNpc("Here. Just a sip - any more and he really will be dead.", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.WISE_OLD_MAN,
                journal = listOf("I should bring the potion to the ${red("Wise Old Man")}."),
                done = listOf("The Wise Old Man drank the potion. Mother did not believe it and lit", "a Fire of Domination. Snowflake chose My Arm."),
                place = "Draynor Village",
                location = WISE_OLD_MAN_POST,
                talk = {
                    chatNpc("Bottoms up. Carry me carefully, now...", facialExpression = FacialExpression.LAUGHING)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MY_ARM,
                journal = listOf("I must defeat ${red("Don't Know What")} (level 163) and ${red("Mother")} (level 198)", "in Weiss."),
                done = listOf("Mother is dead. Snowflake is the new leader of Weiss."),
                place = "Weiss, the snowy north coast",
                location = WEISS_FIGHT,
                tip = "Melee and ranged up to 16. Bring food.",
                fight =
                    QuestFight(
                        XNpcs.DONT_KNOW_WHAT, "Don't Know What", 163, WEISS_FIGHT, "Mother says kill!",
                        chain = listOf(XNpcs.MOTHER to "You will not take my daughter!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("Mother angry! My Arm fight for Snowflake!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MY_ARM,
                journal = listOf("I should talk to ${red("My Arm")} at the Troll Stronghold."),
                done = listOf("My Arm and Snowflake are married. My Arm made friends!"),
                place = "the Troll Stronghold",
                location = MY_ARM_POST,
                talk = {
                    chatNpc("My Arm and Snowflake forever! My Arm made a friend -", "you, human!", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

object TheGeneralsShadow : ShortQuest(
    name = "The General's Shadow",
    startPoint = "Speak to the ghost of General Khazard in the woods south-east of Rellekka.",
    rewards = "The Shadow sword, 2,000 Slayer XP.",
    pointReward = 0,
    questId = OsrsQuestVarps.THE_GENERALS_SHADOW,
    slot = OsrsQuestSlots.THE_GENERALS_SHADOW,
    completedValue = 10, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.GENERAL_KHAZARD,
    startNpcName = "General Khazard",
    startPlace = "the woods south-east of Rellekka",
    startLocation = KHAZARD_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(quests = listOf("Fight Arena"))

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.SHADOW_SWORD,
            xp = listOf(Skills.SLAYER to 2_000.0),
            items = listOf(Items.SHADOW_SWORD to 1),
            lines = listOf("The Shadow sword", "2,000 Slayer XP"),
        )

    /** Ghosts can only be understood with a ghostspeak amulet (OSRS); without it they only moan. */
    private suspend fun QueueTask.understood(): Boolean {
        if (player.equipment.contains(Items.GHOSTSPEAK_AMULET) || player.equipment.contains(Items.GHOSTSPEAK_AMULET_4250)) return true
        chatNpc("Wooo wooo wooooo.", facialExpression = FacialExpression.CALM_TALK)
        player.message("You need to wear a ghostspeak amulet to understand the ghost.")
        return false
    }

    override suspend fun QueueTask.intro(): Boolean {
        if (!understood()) return false
        chatNpc(
            "You walk and talk in the Shadow Realm... you look",
            "familiar. Prove you are one of us: the Sin Seer in",
            "Seers' Village will tell me who you are.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll see the Sin Seer.", "Not right now.", title = "Start The General's Shadow?") != 1) return false
        chatPlayer("I'll see the Sin Seer.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    private fun scout(npc: Int, where: String, tile: Tile, report: String): QuestStep =
        QuestStep(
            npc = npc,
            journal = listOf("I should hear the report of the ${red("ghost scout")} $where."),
            done = listOf("The scout $where gave me his report."),
            place = where,
            location = tile,
            talk = {
                if (!understood()) {
                    false
                } else {
                    chatNpc(report, facialExpression = FacialExpression.CALM_TALK)
                    true
                }
            },
        )

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.SIN_SEER,
                journal = listOf("I should ask the ${red("Sin Seer")} in Seers' Village for a note (bring ${red("40 coins")})."),
                done = listOf("The Sin Seer wrote a note: I am one of the Shadow Realm."),
                place = "Seers' Village",
                location = SIN_SEER_POST,
                tip = "Bring 40 coins.",
                talk = {
                    if (player.inventory.getItemCount(Items.COINS_995) < 40) {
                        chatNpc("A note for a ghost? It will cost you 40 coins.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.COINS_995, 40)
                        chatNpc("Thank you. Give the general this note.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GENERAL_KHAZARD,
                journal = listOf("I should give the note to ${red("General Khazard")}."),
                done = listOf("Khazard wants the reports of his four scouts across Gielinor."),
                place = "the woods south-east of Rellekka",
                location = KHAZARD_POST,
                talk = {
                    if (!understood()) {
                        false
                    } else {
                        chatNpc("So you are one of us. My scouts are out there:", "Karamja, the Gnome Stronghold, Draynor and the desert.", "Bring me their reports.", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            scout(Npcs.SCOUT, "at Tai Bwo Wannai", SCOUT_KARAMJA, "The jungle trolls and the natives are at war. Tell the general."),
            scout(Npcs.SCOUT_5568, "by the Gnome Stronghold", SCOUT_GNOME, "The gnomes are building gliders. Tell the general."),
            scout(Npcs.SCOUT_5569, "by Draynor Manor", SCOUT_DRAYNOR, "Something stirs in the manor. Tell the general."),
            scout(Npcs.SCOUT_5570, "by the Shantay Pass", SCOUT_DESERT, "The general wants you in the cave where the fish-men gather."),
            QuestStep(
                npc = Npcs.GENERAL_KHAZARD,
                journal = listOf("I should bring the reports to ${red("General Khazard")}."),
                done = listOf("Khazard sent me to the cave near where the fish-men gather for my reward."),
                place = "the woods south-east of Rellekka",
                location = KHAZARD_POST,
                talk = {
                    if (!understood()) {
                        false
                    } else {
                        chatNpc("Good. Your reward waits in the cave near where the", "fish-men gather. Go now.", facialExpression = FacialExpression.LAUGHING)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GENERAL_KHAZARD,
                journal = listOf("A trap! I must defeat ${red("Bouncer")} (level 160) at the Goblin Cave by the", "Fishing Guild."),
                done = listOf("I threw the severed leg and finished Bouncer: 'Away, darn spot!'"),
                place = "the Goblin Cave by the Fishing Guild",
                location = GOBLIN_CAVE,
                tip = "Hits up to 15, weak to wind spells.",
                fight = QuestFight(Npcs.BOUNCER_5564, "Bouncer", 160, GOBLIN_CAVE, "Grrrrr!"),
                talk = {
                    chatNpc("Go to the cave. Your reward awaits.", facialExpression = FacialExpression.LAUGHING)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GENERAL_KHAZARD,
                journal = listOf("I should look at what Bouncer left behind at the Goblin Cave."),
                done = listOf("A ghost left the Shadow sword where Bouncer fell."),
                place = "the Goblin Cave by the Fishing Guild",
                location = GOBLIN_CAVE,
                search = QuestSearch(GOBLIN_CAVE, "Where Bouncer fell, a ghost hands you a dark blade: the Shadow sword.", 4),
            ),
        )
}

object HisFaithfulServants : ShortQuest(
    name = "His Faithful Servants",
    startPoint = "Speak to the Strange Old Man at the Barrows, east of Mort'ton.",
    rewards = "A dusty lamp (20,000 Prayer XP).",
    pointReward = 0,
    questId = OsrsQuestVarps.HIS_FAITHFUL_SERVANTS,
    slot = OsrsQuestSlots.HIS_FAITHFUL_SERVANTS,
    completedValue = 2, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.STRANGE_OLD_MAN,
    startNpcName = "Strange Old Man",
    startPlace = "the Barrows",
    startLocation = BARROWS_POST,
) {
    override val ownSteps: Boolean get() = true

    /** The strange icon of the Barrows chest (this server has no icon item: the find is kept on the account). */
    val ICON = AttributeKey<Boolean>(persistenceKey = "p48_hfs_icon")

    override val needs = QuestRequirements(quests = listOf("Priest in Peril"))

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.ANTIQUE_LAMP,
            lamps = listOf(QuestLamp(20_000, listOf(Skills.PRAYER), 1, "Dusty lamp (Prayer)")),
            lines = listOf("A dusty lamp (20,000 Prayer XP)"),
        )

    /** Barrows: a looted chest while the quest is open holds the strange icon (called after every chest reward). */
    fun onBarrowsChest(player: Player) {
        if (stage(player) != 1 || player.attr[ICON] == true) return
        player.attr[ICON] = true
        player.message("Among the treasure you find a strange icon. The Strange Old Man will want to see it.")
    }

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Something wants its icon back. The brothers guard it",
            "in their chest. Bring it to me... or don't. Heh heh.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll fetch the icon.", "Not right now.", title = "Start His Faithful Servants?") != 1) return false
        chatPlayer("I'll fetch the icon.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.STRANGE_OLD_MAN,
                journal = listOf(
                    "I must defeat all six ${red("Barrows brothers")}, loot the ${red("chest")} and bring the",
                    "strange icon to the ${red("Strange Old Man")}.",
                ),
                done = listOf("I gave the strange icon to the Strange Old Man."),
                place = "the Barrows",
                location = BARROWS_POST,
                tip = "Kill all six, then open the chest.",
                talk = {
                    if (player.attr[ICON] != true) {
                        chatNpc("No icon? The brothers still have it. Kill them all", "and open their chest.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        chatPlayer("Here is the strange icon.", facialExpression = FacialExpression.CALM_TALK)
                        chatNpc("Ahh... their master will be pleased. Take this lamp.", facialExpression = FacialExpression.HAPPY)
                        player.attr.remove(ICON)
                        true
                    }
                },
            ),
        )
}
