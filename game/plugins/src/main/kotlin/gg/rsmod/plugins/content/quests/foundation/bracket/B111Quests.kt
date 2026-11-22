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
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

/**
 * PROJECT48: the mini-quests of combat bracket 111+ (Q34-Q40), in the order of VOORTGANG.tsv.
 * Npcs that already have a Talk-to plugin or a QuestNpcPost are NOT listed here (their handler calls FoundationQuests.talk first).
 */
object B111Quests {
    val ALL: List<ShortQuest> = listOf(BlackKnightsFortress, MurderMystery, JunglePotion, ShiloVillage, OneSmallFavour, ATailOfTwoCats, DeviousMinds, SecretsOfTheNorth, MakingFriendsWithMyArm, TheGeneralsShadow, HisFaithfulServants, RecipeForDisaster)

    val NPCS: List<BracketNpc> =
        B111Rfd.NPCS + listOf(
            BracketNpc(Npcs.MURPHY, null, Direction.SOUTH, listOf("Well hello my brave adventurer.")),
            // PROJECT48 X4-X6: Making Friends with My Arm, The General's Shadow, His Faithful Servants.
            BracketNpc(Npcs.BURNTMEAT, null, Direction.SOUTH, listOf("Hmm? What human do in troll kitchen?", "Burntmeat tired of cooking goats. Human look tasty.")),
            BracketNpc(Npcs.MY_ARM, null, Direction.SOUTH, listOf("My Arm is a happy troll.")),
            BracketNpc(Npcs.GENERAL_KHAZARD, Tile(2712, 3628, 0), Direction.SOUTH, listOf("Wooo... the Shadow Realm is cold.")),
            BracketNpc(Npcs.SIN_SEER, Tile(2708, 3484, 0), Direction.SOUTH, listOf("Augh! Your sins! They BLIND me!")),
            BracketNpc(Npcs.SCOUT, Tile(2826, 3066, 0), Direction.SOUTH, listOf("Wooo...")),
            BracketNpc(Npcs.SCOUT_5568, Tile(2461, 3374, 0), Direction.SOUTH, listOf("Wooo...")),
            BracketNpc(Npcs.SCOUT_5569, Tile(3072, 3344, 0), Direction.SOUTH, listOf("Wooo...")),
            BracketNpc(Npcs.SCOUT_5570, Tile(3303, 3110, 0), Direction.SOUTH, listOf("Wooo...")),
            BracketNpc(Npcs.STRANGE_OLD_MAN, null, Direction.SOUTH, listOf("Heh heh heh.")),
            // Q41 Secrets of the North: the guard and the barman stand in the world already; Hazeel, Claus and Big Fish get a post.
            BracketNpc(Npcs.GUARD_887, null, Direction.SOUTH, listOf("Move along. Nothing to see at the mansion.")),
            BracketNpc(Npcs.ANGOR, null, Direction.NORTH, listOf("What can I get you?")),
            BracketNpc(Npcs.HAZEEL, Tile(2607, 9673, 0), Direction.SOUTH, listOf("The north remembers its masters.")),
            BracketNpc(Npcs.CLAUS_THE_CHEF, Tile(2570, 9668, 0), Direction.SOUTH, listOf("Mind the pots, they are hot.")),
            BracketNpc(SotnNpcs.BIG_FISH, Tile(2868, 3938, 0), Direction.SOUTH, listOf("Blub.")),
            BracketNpc(Npcs.TRAIBORN, null, Direction.SOUTH, listOf("Ello young thingummywut.")),
            BracketNpc(Npcs.DORIS, null, Direction.SOUTH, listOf("Hello again dearie. How are you doing?")),
            BracketNpc(Npcs.SIR_AMIK_VARZE, null, Direction.SOUTH, listOf("Hello, friend!")),
            BracketNpc(Npcs.POISON_SALESMAN, null, Direction.SOUTH, listOf("Poisons for every occasion, friend.")),
            BracketNpc(Npcs.MOSOL_REI, null, Direction.SOUTH, listOf("Shilo Village is a sad place, bwana.")),
            BracketNpc(Npcs.YANNI_SALIKA, null, Direction.SOUTH, listOf("Greetings Bwana! My name is Yanni and I buy and sell antiques.")),
            BracketNpc(Npcs.JUNGLE_FORESTER, null, Direction.SOUTH, listOf("The jungle is full of things that bite.")),
            BracketNpc(Npcs.UNFERTH, Tile(2920, 3559, 0), Direction.SOUTH, listOf("It's just not the same without Bob around.")),
            BracketNpc(Npcs.HILD, null, Direction.SOUTH, listOf("Burthorpe is a fine place to live.")),
            BracketNpc(Npcs.BOB_2635, Tile(2917, 3561, 0), Direction.SOUTH, listOf("Meow.")),
            BracketNpc(Npcs.APOTHECARY, null, Direction.SOUTH, listOf("I am the Apothecary. I brew potions.")),
            BracketNpc(Npcs.MONK_803, Tile(3425, 3485, 0), Direction.SOUTH, listOf("Greetings traveller.")),
            BracketNpc(Npcs.HIGH_PRIEST, null, Direction.SOUTH, listOf("Welcome to the church of Entrana.")),
            BracketNpc(Npcs.SIR_TIFFY_CASHIEN, null, Direction.SOUTH, listOf("Good day. Falador's park is lovely.")),
            BracketNpc(Npcs.TRUFITUS, null, Direction.SOUTH, listOf("My greatest respects Bwana, I have communed with my gods and the future", "looks good for my people.")),
        )
}

// PROJECT48 Q34: Black Knights' Fortress. OSRS Wiki: 12 quest points; no fight (evade the knights); 3 QP, 2,500 coins and two antique lamps
// (1,000 XP in a combat skill each). The listening at the grill and the cabbage in the cauldron are search steps. Estimated tiles.
private val AMIK_POST = Tile(2962, 3337, 2)
private val GRILL = Tile(3025, 3507, 0)
private val CAULDRON_HOLE = Tile(3030, 3512, 0)

object BlackKnightsFortress : ShortQuest(
    name = "Black Knights' Fortress",
    startPoint = "Speak to Sir Amik Varze on the second floor of the White Knights' Castle's western tower in Falador.",
    rewards = "3 Quest Points, 2,500 coins and two lamps of 1,000 XP in a combat skill.",
    pointReward = 3,
    questId = Varps.BLACK_KNIGHTS_FORTRESS_PROGRESS,
    slot = 159,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.SIR_AMIK_VARZE,
    startNpcName = "Sir Amik Varze",
    startPlace = "the White Knights' Castle, Falador",
    startLocation = AMIK_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(questPoints = 12, extra = listOf("Must be able to evade level 33 Black Knights."))

    override val recommendation = Recommendation.combat(25)

    override val reward =
        QuestReward(
            icon = Items.CABBAGE,
            lamps =
                List(2) {
                    gg.rsmod.plugins.content.quests.foundation.QuestLamp(
                        1_000,
                        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC, Skills.PRAYER),
                        1,
                        "Black Knights' Fortress combat",
                    )
                },
            items = listOf(Items.COINS_995 to 2_500),
            lines = listOf("3 Quest Points", "2,500 coins", "Two lamps of 1,000 combat XP"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The Black Knights are plotting something",
            "in their fortress south of Falador. Find",
            "out what - and ruin it, if you can.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll infiltrate the fortress.", "Not right now.", title = "Start Black Knights' Fortress?") != 1) return false
        chatPlayer("I'll find out what they plan.", facialExpression = FacialExpression.CALM_TALK)
        FoundationRewards.grant(player, Items.CABBAGE, 1)
        chatNpc("Take this cabbage - it may come", "in useful in a witch's cauldron.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = -1,
                journal = listOf("I should listen at the ${red("grill")} of the Black Knights' Fortress."),
                done = listOf("I overheard the witch and the Black Knight Captain: they brew a potion of invincibility."),
                place = "the Black Knights' Fortress",
                location = GRILL,
                search = QuestSearch(GRILL, "You listen at the grill: the witch and the Black Knight Captain are brewing a potion of invincibility for their army.", 3),
            ),
            QuestStep(
                npc = -1,
                journal = listOf("I should drop the ${red("cabbage")} down the hole above the witch's cauldron."),
                done = listOf("The cabbage fell into the cauldron: the potion is ruined."),
                place = "the Black Knights' Fortress",
                location = CAULDRON_HOLE,
                search =
                    QuestSearch(
                        CAULDRON_HOLE,
                        "You drop the cabbage down the hole. It lands in the cauldron: the witch cries out - the potion is ruined!",
                        3,
                        needs = listOf(Items.CABBAGE to 1),
                        missing = "You need a cabbage to drop down the hole.",
                    ),
            ),
            QuestStep(
                npc = Npcs.SIR_AMIK_VARZE,
                journal = listOf("I should tell ${red("Sir Amik Varze")} that the Black Knights' plan is ruined."),
                done = listOf("Sir Amik Varze rewarded me."),
                place = "the White Knights' Castle, Falador",
                location = AMIK_POST,
                talk = {
                    chatNpc("Excellent! The Black Knights' plan is", "ruined. Here is your reward, 2,500 coins.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

// PROJECT48 Q35: Murder Mystery. OSRS Wiki: no requirements; 3 QP, 1,406 Crafting XP, 2,000 coins. The culprit is fixed per account
// (hash of the account name) like the saved choice in OSRS: the Poison Salesman names who bought the poison, the Gossip takes the accusation.
private val GOSSIP_POST = Tile(2742, 3555, 0)
private val SALESMAN_POST = Tile(2697, 3496, 0)
private val SUSPECTS = listOf("Anna", "Bob", "Carol", "David", "Elizabeth", "Frank")

private fun culprit(player: gg.rsmod.game.model.entity.Player): String = SUSPECTS[Math.floorMod(player.username.hashCode(), SUSPECTS.size)]

object MurderMystery : ShortQuest(
    name = "Murder Mystery",
    startPoint = "Speak to the Gossip near the Sinclair Mansion, north of Seers' Village.",
    rewards = "3 Quest Points, 1,406 Crafting XP, 2,000 coins.",
    pointReward = 3,
    questId = Varps.MURDER_MYSTERY_PROGRESS,
    slot = 78,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.GOSSIP,
    startNpcName = "Gossip",
    startPlace = "the Sinclair Mansion",
    startLocation = GOSSIP_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements()

    override val recommendation = Recommendation.combat(3)

    override val reward =
        QuestReward(
            icon = Items.COINS_995,
            xp = listOf(Skills.CRAFTING to 1_406.0),
            items = listOf(Items.COINS_995 to 2_000),
            lines = listOf("3 Quest Points", "1,406 Crafting XP", "2,000 coins"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Have you heard? Lord Sinclair has",
            "been murdered! The killer is one of",
            "the family. Will you find out who?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll investigate.", "Not right now.", title = "Start Murder Mystery?") != 1) return false
        chatPlayer("I'll find the murderer.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("The poison used was bought somewhere - ask", "the Poison Salesman in Seers' Village.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.GOSSIP,
                journal = listOf("I should ask the ${red("Gossip")} about the Sinclair family."),
                done = listOf("The Gossip told me the poison came from a salesman."),
                place = "the Sinclair Mansion",
                location = GOSSIP_POST,
                talk = {
                    chatNpc("Anna, Bob, Carol, David, Elizabeth and Frank all", "disliked him. Find who bought the poison!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.POISON_SALESMAN,
                journal = listOf("I should ask the ${red("Poison Salesman")} in Seers' Village who bought the poison."),
                done = listOf("The Poison Salesman told me who bought the poison."),
                place = "Seers' Village",
                location = SALESMAN_POST,
                talk = {
                    chatNpc("A Sinclair bought it, all right: ${culprit(player)}!", "I remember the face.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GOSSIP,
                journal = listOf("I should accuse the murderer to the ${red("Gossip")}."),
                done = listOf("I accused the right murderer."),
                place = "the Sinclair Mansion",
                location = GOSSIP_POST,
                talk = {
                    chatNpc("So, who killed Lord Sinclair?", facialExpression = FacialExpression.CALM_TALK)
                    var named: String? = null
                    while (named == null) {
                        val pick = options("Anna", "Bob", "Carol", "David", "Another suspect...", title = "The murderer is...")
                        named =
                            if (pick <= 4) SUSPECTS[pick - 1]
                            else options("Elizabeth", "Frank", "Back", title = "The murderer is...").let { if (it <= 2) SUSPECTS[3 + it] else null }
                    }
                    if (named == culprit(player)) {
                        chatNpc("Of course - $named! Justice will be done.", facialExpression = FacialExpression.HAPPY)
                        true
                    } else {
                        chatNpc("That cannot be right. Think again.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    }
                },
            ),
        )
}

// PROJECT48 Q36: Jungle Potion. OSRS Wiki: Druidic Ritual, 3 Herblore (boostable); Jogres/Harpie bug swarms; 1 QP, 775 Herblore XP.
// Snake weed and the rogue's purse are search steps. Estimated tiles. ONTGR: none (the Shilo Village gates ask Shilo Village).
private val TRUFITUS_POST = Tile(2808, 3086, 0)
private val SNAKE_WEED_TILE = Tile(2790, 3093, 0)
private val PURSE_TILE = Tile(2835, 9395, 0)

object JunglePotion : ShortQuest(
    name = "Jungle Potion",
    startPoint = "Speak to Trufitus in Tai Bwo Wannai.",
    rewards = "1 Quest Point, 775 Herblore XP.",
    pointReward = 1,
    questId = Varps.JUNGLE_POTION_PROGRESS,
    slot = 66,
    completedValue = 4, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.TRUFITUS,
    startNpcName = "Trufitus",
    startPlace = "Tai Bwo Wannai",
    startLocation = TRUFITUS_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.HERBLORE to 3),
            quests = listOf("Druidic Ritual"),
            boostable = setOf(Skills.HERBLORE),
            extra = listOf("Must be able to defend against level 53 Jogres and level 46 Harpie Bug Swarms."),
        )

    override val recommendation = Recommendation.combat(30)

    override val reward =
        QuestReward(
            icon = Items.CLEAN_SNAKE_WEED,
            xp = listOf(Skills.HERBLORE to 775.0),
            lines = listOf("1 Quest Point", "775 Herblore XP"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "I need a rare herb from the jungle and a",
            "rogue's purse from the dungeon to brew a potion",
            "for the spirits. Will you gather them for me?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll gather them.", "Not right now.", title = "Start Jungle Potion?") != 1) return false
        chatPlayer("I'll bring both.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Snake weed grows in the swamp", "south-west of the village.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = -1,
                journal = listOf("I should find ${red("snake weed")} in the swamp south-west of Tai Bwo Wannai."),
                done = listOf("I picked a grimy snake weed."),
                place = "the swamp south-west of Tai Bwo Wannai",
                location = SNAKE_WEED_TILE,
                search = QuestSearch(SNAKE_WEED_TILE, "You pick a grimy snake weed from the swamp vines.", 3, gives = listOf(Items.GRIMY_SNAKE_WEED to 1)),
            ),
            QuestStep(
                npc = -1,
                journal = listOf("I should find the ${red("rogue's purse")} in the Pothole Dungeon."),
                done = listOf("I took a grimy rogue's purse from the dungeon wall."),
                place = "the Pothole Dungeon",
                location = PURSE_TILE,
                search = QuestSearch(PURSE_TILE, "You find a grimy rogue's purse in the wall of the cave.", 3, gives = listOf(Items.GRIMY_ROGUES_PURSE to 1)),
            ),
            QuestStep(
                npc = Npcs.TRUFITUS,
                journal = listOf("I should bring the ${red("snake weed")} and the ${red("rogue's purse")} to ${red("Trufitus")}."),
                done = listOf("Trufitus brewed the potion."),
                place = "Tai Bwo Wannai",
                location = TRUFITUS_POST,
                talk = {
                    val p = player
                    if (!p.inventory.contains(Items.GRIMY_SNAKE_WEED) || !p.inventory.contains(Items.GRIMY_ROGUES_PURSE)) {
                        chatNpc("I need the snake weed and the rogue's purse.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        p.inventory.remove(Items.GRIMY_SNAKE_WEED)
                        p.inventory.remove(Items.GRIMY_ROGUES_PURSE)
                        chatNpc("Splendid! The potion is brewed and the spirits are", "pleased. Thank you!", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
