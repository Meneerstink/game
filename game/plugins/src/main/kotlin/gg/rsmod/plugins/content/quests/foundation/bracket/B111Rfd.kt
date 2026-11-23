package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
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

// PROJECT48 Q42: Recipe for Disaster as ONE quest in ten parts (OSRS Wiki, the ten subquest pages). Each part is one talk step (the
// requirements of that part are checked there, hard) and, where OSRS has a fight, one fight step. The parts run in the wiki order of
// QuestBrackets.RFD_NAMES. A played part counts for the Culinaromancer's Chest gloves like a bracket part (QuestBrackets.rfdSubquests).
//  - Not built (no dishes/ingredient chains on this server): the item chains of every part are replaced by the part's conversation and
//    its fight; the Cooking-skill items are NOT taken. Barcrawl is left out (owner). Legends' Quest "started" is not checked.
//  - Reward as OSRS: 10 QP, an antique lamp of 20,000 XP (skill of level 50+), the Culinaromancer's Chest in full (QuestBrackets).
// Tiles that are not a donor spawn are estimates (dossier BRACKET-QUESTS).

private val COOK_POST = Tile(3209, 3215, 0)
private val ROHAK_POST = Tile(2867, 9877, 0)
private val GOBLIN_COOK_POST = Tile(2955, 3510, 0)
private val MURPHY_POST = Tile(2668, 3162, 0)
private val MUDSKIPPER_FIGHT = Tile(2671, 3161, 0)
private val TRAIBORN_POST = Tile(3112, 3162, 1)
private val DORIS_POST = Tile(3079, 3493, 0)
private val RANTZ_POST = Tile(2629, 2994, 0)
private val JUBBLY_FIGHT = Tile(2640, 2990, 0)
private val AWOWOGEI_POST = Tile(2802, 2755, 0)
private val SNAKE_FIGHT = Tile(2796, 2760, 0)
private val AMIK_POST = Tile(2962, 3337, 2)
// ONTBREKEND 21 (OSRS Wiki "Freeing Sir Amik Varze" + "Evil Chicken's Lair"): the egg comes from the Evil Chicken in the northern cave
// of the Evil Chicken's Lair (Zanaris, raw chicken on the Chicken Shrine; this map has the lair at 1540-1575, 4355-4405).
private val CHICKEN_FIGHT = Tile(1566, 4401, 0)
private val GYPSY_POST = Tile(3206, 3214, 0)
private val FINAL_FIGHT = Tile(3235, 3220, 0)

object RecipeForDisaster : ShortQuest(
    name = "Recipe for Disaster",
    startPoint = "Speak to the Cook in the kitchen of Lumbridge Castle.",
    rewards = "10 Quest Points, a lamp of 20,000 XP (skill of level 50+), the whole Culinaromancer's Chest.",
    pointReward = 10,
    questId = Varbits.RECIPE_FOR_DISASTER_PROGRESS,
    slot = 87,
    completedValue = 17, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.COOK,
    startNpcName = "Cook",
    startPlace = "Lumbridge Castle",
    startLocation = COOK_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(skills = listOf(Skills.COOKING to 10), quests = listOf("Cook's Assistant"), boostable = setOf(Skills.COOKING))

    override val recommendation = Recommendation.combat(110)

    override val reward =
        QuestReward(
            icon = Items.EXPERIENCE_LAMP,
            lamps = listOf(QuestLamp(20_000, FoundationRewards.ALL_SKILLS, 50, "Recipe for Disaster")),
            lines = listOf("10 Quest Points", "A lamp of 20,000 XP (skill of level 50+)", "The whole Culinaromancer's Chest", "Barrows gloves"),
        )

    /** The step numbers (1-based) that finish part 0..9 of the wiki order. */
    val PART_LAST_STEP = listOf(1, 2, 3, 5, 6, 7, 9, 11, 13, 16)

    /** Parts finished by [player] while the quest is open (0 before the first, the quest finished = all 10). */
    fun partsDone(player: Player): Int {
        val stage = stage(player)
        if (stage >= completedValue) return PART_LAST_STEP.size
        return PART_LAST_STEP.count { it < stage }
    }

    /**
     * The experience of each PLAYED part (OSRS Wiki, the "Rewards" of the ten subquest pages; part 0 and the final fight give none - the
     * final fight's lamp is the quest reward). Exact amounts, not the server rate (owner N184, as every quest reward). A part given by
     * the combat bracket pays nothing (wiki "No experience is received"): only [onStepDone] of a played step pays.
     */
    val PART_XP: List<List<Pair<Int, Double>>> =
        listOf(
            emptyList(),
            listOf(Skills.COOKING to 1_000.0, Skills.SLAYER to 1_000.0), // Mountain Dwarf
            listOf(Skills.COOKING to 1_000.0, Skills.FARMING to 1_000.0, Skills.CRAFTING to 1_000.0), // Goblin generals
            listOf(Skills.COOKING to 1_000.0, Skills.CRAFTING to 1_000.0, Skills.FISHING to 1_000.0, Skills.SMITHING to 1_000.0), // Pirate Pete
            listOf(Skills.COOKING to 2_500.0, Skills.MAGIC to 2_500.0), // Lumbridge Guide
            listOf(Skills.COOKING to 7_000.0), // Evil Dave
            listOf(Skills.WOODCUTTING to 1_500.0, Skills.COOKING to 1_500.0, Skills.CRAFTING to 1_500.0, Skills.RANGED to 1_500.0), // Skrach Uglogwee
            listOf(Skills.COOKING to 10_000.0, Skills.AGILITY to 10_000.0), // King Awowogei
            listOf(Skills.COOKING to 4_000.0, Skills.CONSTITUTION to 4_000.0), // Sir Amik Varze
            emptyList(),
        )

    /**
     * After a step: the experience of the part it finishes, and the Culinaromancer's Chest stands in the cellar as soon as the first
     * part is done (QuestBrackets.RFD_CHEST_STAGE).
     */
    override fun onStepDone(player: Player, stage: Int) {
        PART_XP.getOrNull(PART_LAST_STEP.indexOf(stage))?.forEach { (skill, amount) -> player.addXp(skill, amount, modifiers = false) }
        if (PART_LAST_STEP.contains(stage) && player.getVarbit(Varbits.RECIPE_FOR_DISASTER_PROGRESS) in 0 until 3) {
            player.setVarbit(Varbits.RECIPE_FOR_DISASTER_PROGRESS, 3)
        }
    }

    private suspend fun QueueTask.gate(req: QuestRequirements): Boolean {
        val missing = req.missing(player)
        if (missing.isNotEmpty()) {
            refuseStart(missing)
            return false
        }
        return true
    }

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Disaster! The Culinaromancer has frozen",
            "the council that was to eat at the",
            "centennial feast! I need someone to free",
            "all of them, one by one. Will you help?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll free the council.", "Not right now.", title = "Start Recipe for Disaster?") != 1) return false
        chatPlayer("I'll help.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            // Part 0: Another Cook's Quest
            QuestStep(
                npc = Npcs.COOK,
                journal = listOf("I should speak to the ${red("Cook")} again: he needs the ingredients for the feast."),
                done = listOf("I finished Another Cook's Quest: the Cook can prepare the feast."),
                place = "Lumbridge Castle",
                location = COOK_POST,
                talk = {
                    chatNpc("A newt's eye, ale, a tomato and a dirty", "blast... well, you have shown you are", "willing. Let us start freeing them.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            // Part 1: Freeing the Mountain Dwarf
            QuestStep(
                npc = Npcs.ROHAK,
                journal = listOf("I should speak to ${red("Rohak")} in the tunnel under White Wolf Mountain."),
                done = listOf("I freed the Mountain Dwarf: Rohak baked the dwarven rock cake."),
                place = "the tunnel under White Wolf Mountain",
                location = ROHAK_POST,
                talk = {
                    if (!gate(QuestRequirements(quests = listOf("Fishing Contest")))) {
                        false
                    } else {
                        chatNpc("Frozen? Aye. A rock cake will thaw him: here.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            // Part 2: Freeing the Goblin generals
            QuestStep(
                npc = Npcs.GOBLIN_COOK,
                journal = listOf("I should speak to ${red("Mudknuckles")}, the goblin cook, in the Goblin Village."),
                done = listOf("I freed the Goblin generals: the slop of compromise ended their quarrel."),
                place = "the Goblin Village",
                location = GOBLIN_COOK_POST,
                talk = {
                    if (!gate(QuestRequirements(quests = listOf("Goblin Diplomacy")))) {
                        false
                    } else {
                        chatNpc("A meal both generals agree on? A", "slop of compromise, it is. Take it.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            // Part 3: Freeing Pirate Pete
            QuestStep(
                npc = Npcs.MURPHY,
                journal = listOf("I should speak to ${red("Murphy")} at Port Khazard about Pirate Pete."),
                done = listOf("Murphy sent me after the mudskippers."),
                place = "Port Khazard",
                location = MURPHY_POST,
                talk = {
                    if (!gate(QuestRequirements(skills = listOf(Skills.COOKING to 31), boostable = setOf(Skills.COOKING)))) {
                        false
                    } else {
                        chatNpc("Pete needs a fishcake. First, clear the", "mudskippers off the shore: five of them.", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.MURPHY,
                journal = listOf("I must defeat the five ${red("mudskippers")} (level 30 and 31)."),
                done = listOf("I freed Pirate Pete: the fishcake did it."),
                place = "Port Khazard",
                location = MUDSKIPPER_FIGHT,
                tip = "Five mudskippers come one after the other.",
                fight =
                    QuestFight(
                        Npcs.MUDSKIPPER, "Mudskipper", 30, MUDSKIPPER_FIGHT, "Snap!",
                        chain =
                            listOf(
                                Npcs.MUDSKIPPER_3423 to "Snap!",
                                Npcs.MUDSKIPPER to "Snap snap!",
                                Npcs.MUDSKIPPER_3423 to "Snap!",
                                Npcs.MUDSKIPPER to "Snap!",
                            ),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("The mudskippers are right there on the shore!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            // Part 4: Freeing the Lumbridge Guide
            QuestStep(
                npc = Npcs.TRAIBORN,
                journal = listOf("I should speak to ${red("Traiborn")} in the Wizards' Tower about the Lumbridge Guide."),
                done = listOf("I freed the Lumbridge Guide: Traiborn's cake of guidance."),
                place = "the Wizards' Tower",
                location = TRAIBORN_POST,
                talk = {
                    if (!gate(
                            QuestRequirements(
                                skills = listOf(Skills.COOKING to 40),
                                quests =
                                    listOf(
                                        "Big Chompy Bird Hunting", "Biohazard", "Plague City", "Demon Slayer", "Murder Mystery",
                                        "Nature Spirit", "Priest in Peril", "The Restless Ghost", "Witch's House",
                                    ),
                                boostable = setOf(Skills.COOKING),
                            ),
                        )
                    ) {
                        false
                    } else {
                        chatNpc("A cake of guidance! Thrice-folded flour, and thus:", "here it is, young one.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            // Part 5: Freeing Evil Dave
            QuestStep(
                npc = Npcs.DORIS,
                journal = listOf("I should speak to ${red("Doris")} in Edgeville about her son Evil Dave."),
                done = listOf("I freed Evil Dave: a spicy stew, and the hell-rats are gone."),
                place = "Doris' house, Edgeville",
                location = DORIS_POST,
                talk = {
                    if (!gate(QuestRequirements(quests = listOf("Gertrude's Cat", "Shadow of the Storm", "Demon Slayer", "The Golem")))) {
                        false
                    } else {
                        chatNpc("David lives in the basement. The hell-rats took", "the spices - but a spicy stew will thaw him, yes.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            // Part 6: Freeing Skrach Uglogwee
            QuestStep(
                npc = Npcs.RANTZ,
                journal = listOf("I should speak to ${red("Rantz")} in the Feldip Hills about Skrach Uglogwee."),
                done = listOf("Rantz sent me to hunt a jubbly bird."),
                place = "the Feldip Hills",
                location = RANTZ_POST,
                talk = {
                    if (!gate(QuestRequirements(skills = listOf(Skills.COOKING to 41, Skills.FIREMAKING to 20), quests = listOf("Big Chompy Bird Hunting"), boostable = setOf(Skills.COOKING, Skills.FIREMAKING)))) {
                        false
                    } else {
                        chatNpc("Skrach want cooked jubbly. You catch jubbly bird!", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.RANTZ,
                journal = listOf("I must defeat the ${red("jubbly bird")} (level 9)."),
                done = listOf("I freed Skrach Uglogwee: the cooked jubbly did it."),
                place = "the Feldip Hills",
                location = JUBBLY_FIGHT,
                fight = QuestFight(Npcs.JUBBLY_BIRD, "Jubbly bird", 9, JUBBLY_FIGHT, "Squawk!"),
                talk = {
                    chatNpc("Jubbly bird is right there!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            // Part 7: Freeing King Awowogei
            QuestStep(
                npc = Npcs.AWOWOGEI,
                journal = listOf("I should speak to ${red("King Awowogei")} on Ape Atoll."),
                done = listOf("Awowogei asked for a stuffed snake."),
                place = "Ape Atoll",
                location = AWOWOGEI_POST,
                talk = {
                    if (!gate(
                            QuestRequirements(
                                skills = listOf(Skills.COOKING to 70, Skills.AGILITY to 48),
                                quests = listOf("Monkey Madness", "The Grand Tree", "Tree Gnome Village"),
                                boostable = setOf(Skills.COOKING),
                            ),
                        )
                    ) {
                        false
                    } else {
                        chatNpc("Ook! Awowogei wants stuffed snake! Kill big snake!", facialExpression = FacialExpression.CALM_TALK)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.AWOWOGEI,
                journal = listOf("I must defeat the ${red("Big Snake")} (level 84)."),
                done = listOf("I freed King Awowogei: the stuffed snake did it."),
                place = "Ape Atoll",
                location = SNAKE_FIGHT,
                fight = QuestFight(Npcs.BIG_SNAKE, "Big Snake", 84, SNAKE_FIGHT, "Hissss!"),
                talk = {
                    chatNpc("Ook! The big snake is right there!", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            // Part 8: Freeing Sir Amik Varze
            QuestStep(
                npc = Npcs.SIR_AMIK_VARZE,
                journal = listOf("I should speak to ${red("Sir Amik Varze")} in the White Knights' Castle."),
                done = listOf("Sir Amik told me about the Evil Chicken's egg."),
                place = "the White Knights' Castle, Falador",
                location = AMIK_POST,
                talk = {
                    if (!gate(
                            QuestRequirements(
                                questPoints = 107,
                                quests =
                                    listOf(
                                        "Family Crest", "Heroes' Quest", "Shield of Arrav", "Lost City", "Merlin's Crystal", "Dragon Slayer",
                                        "Shilo Village", "Jungle Potion", "Underground Pass", "Biohazard", "Plague City", "Waterfall Quest",
                                    ),
                            ),
                        )
                    ) {
                        false
                    } else {
                        chatNpc(
                            "A brulee supreme needs the Evil Chicken's egg. Its lair",
                            "is in Zanaris: use a raw chicken on the Chicken Shrine.",
                            facialExpression = FacialExpression.WORRIED,
                        )
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.SIR_AMIK_VARZE,
                journal = listOf(
                    "I must defeat the ${red("Evil Chicken")} (level 159) in the northern cave of",
                    "the Evil Chicken's Lair (raw chicken on the Chicken Shrine in Zanaris).",
                ),
                done = listOf("I freed Sir Amik Varze: the brulee supreme did it."),
                place = "the Evil Chicken's Lair, Zanaris",
                location = CHICKEN_FIGHT,
                tip = "Bring an anti-dragon shield (4 black dragons). The chicken uses Magic.",
                fight = QuestFight(Npcs.EVIL_CHICKEN, "Evil Chicken", 159, CHICKEN_FIGHT, "MUAHAHAHAHAAA!"),
                talk = {
                    chatNpc("The Evil Chicken waits in its lair in Zanaris.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            // Part 9: Defeating the Culinaromancer
            QuestStep(
                npc = Npcs.GYPSY,
                journal = listOf("I should speak to the ${red("Gypsy")} in the dining room of Lumbridge Castle: the council is free."),
                done = listOf("The Gypsy opened the portal to the Culinaromancer."),
                place = "Lumbridge Castle",
                location = GYPSY_POST,
                talk = {
                    if (!gate(
                            QuestRequirements(
                                skills =
                                    listOf(
                                        Skills.COOKING to 70, Skills.AGILITY to 48, Skills.MINING to 50, Skills.FISHING to 53, Skills.THIEVING to 53,
                                        Skills.HERBLORE to 25, Skills.MAGIC to 59, Skills.SMITHING to 40, Skills.FIREMAKING to 50, Skills.RANGED to 40,
                                        Skills.CRAFTING to 40, Skills.FLETCHING to 10, Skills.WOODCUTTING to 36,
                                    ),
                                quests =
                                    listOf(
                                        "Desert Treasure", "The Dig Site", "Temple of Ikov", "The Tourist Trap", "Troll Stronghold", "Death Plateau",
                                        "Priest in Peril", "Waterfall Quest", "Horror from the Deep",
                                    ),
                                questPoints = 175,
                                boostable = setOf(Skills.COOKING, Skills.MINING, Skills.FISHING, Skills.HERBLORE, Skills.MAGIC, Skills.SMITHING, Skills.FIREMAKING, Skills.CRAFTING, Skills.FLETCHING, Skills.WOODCUTTING),
                                extra = listOf("Must be able to defeat every foe without Prayer."),
                            ),
                        )
                    ) {
                        false
                    } else {
                        chatNpc("The council is free! The", "Culinaromancer's portal is open. You", "must face his six foes without Prayer.", facialExpression = FacialExpression.WORRIED)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.GYPSY,
                journal = listOf("I must defeat ${red("Agrith-Na-Na")}, ${red("Flambeed")}, ${red("Karamel")}, ${red("Dessourt")}, the ${red("Gelatinnoth Mother")} and the ${red("Culinaromancer")}", "(the first is level 146)."),
                done = listOf("I defeated the Culinaromancer: the feast was saved."),
                place = "Lumbridge Castle",
                location = FINAL_FIGHT,
                tip = "Six foes in a row, no Prayer. Bring ice gloves.",
                fight =
                    QuestFight(
                        Npcs.AGRITHNANA, "Agrith-Na-Na", 146, FINAL_FIGHT, "Who dares enter the Culinaromancer's lair?",
                        chain =
                            listOf(
                                Npcs.FLAMBEED to "Burn, little mortal!",
                                Npcs.KARAMEL to "Cold and sweet: your end!",
                                Npcs.DESSOURT to "A fine dessert, you will make!",
                                Npcs.GELATINNOTH_MOTHER to "Gloop!",
                                Npcs.CULINAROMANCER_3491 to "I shall devour your feast!",
                            ),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("The portal is open. Go and end this!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            // Part 9 ends here: the Cook serves the saved feast.
            QuestStep(
                npc = Npcs.COOK,
                journal = listOf("I should tell the ${red("Cook")} in Lumbridge Castle that the Culinaromancer is dead."),
                done = listOf("The Cook served the feast: the council was saved."),
                place = "Lumbridge Castle",
                location = COOK_POST,
                talk = {
                    chatNpc("The Culinaromancer is gone? The feast can begin!", "You have saved the council - and my kitchen.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

object B111Rfd {
    val NPCS: List<BracketNpc> =
        listOf(
            BracketNpc(Npcs.ROHAK, null, Direction.SOUTH, listOf("Hello there youngster.")),
            BracketNpc(Npcs.GOBLIN_COOK, Tile(2955, 3510, 0), Direction.SOUTH, listOf("I thinking of calling it fast food, 'cos of how fast it goes from raw to burnt, see?")),
            BracketNpc(Npcs.RANTZ, Tile(2629, 2994, 0), Direction.SOUTH, listOf("Bwa! Rantz hungry.")),
            BracketNpc(Npcs.AWOWOGEI, Tile(2802, 2755, 0), Direction.SOUTH, listOf("Ook!")),
            BracketNpc(Npcs.GYPSY, Tile(3206, 3214, 0), Direction.SOUTH, listOf("I see great things in your future.")),
        )
}
