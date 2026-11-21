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
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q37 Shilo Village + Q38 One Small Favour (bracket 111+). OSRS Wiki:
//  - Shilo Village: Jungle Potion, 20 Crafting, 32 Agility (boostable); Nazastarool in 3 forms (91, 68, 93); 2 QP, 3,875 Crafting XP,
//    access to Shilo Village. ONTGR: the Brimhaven -> Shilo Village travel cart only runs for a player who finished it (travel_cart.plugin.kts).
//    Not gated: the Shilo gem mine teleport of the Karamja gloves and the walking route; the Lady of the Waves charter does not exist.
//  - One Small Favour: Rune Mysteries, Shilo Village, 36 Agility, 25 Crafting, 18 Herblore, 30 Smithing (boostable); Dwarf gang members
//    (44/48/49) and Slagilith (92); 2 QP, two 10,000 XP lamps, the Steel key ring. Not built: Guthix rest tea, pot lids, the Feldip glider
//    route. All tiles that are not a donor spawn are estimates (dossier BRACKET-QUESTS).

private val MOSOL_POST = Tile(2883, 2951, 0)
private val TRUFITUS_POST = Tile(2808, 3086, 0)
private val TEMPLE_TILE = Tile(2859, 2971, 0)
private val NAZA_FIGHT = Tile(2865, 2939, 0)
private val TOMB_TILE = Tile(2870, 2947, 0)

object ShiloVillage : ShortQuest(
    name = "Shilo Village",
    startPoint = "Speak to Mosol Rei outside Shilo Village, in the south of Karamja.",
    rewards = "2 Quest Points, 3,875 Crafting XP, access to Shilo Village and the Brimhaven cart.",
    pointReward = 2,
    questId = Varps.SHILO_VILLAGE_PROGRESS,
    slot = 99,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.MOSOL_REI,
    startNpcName = "Mosol Rei",
    startPlace = "outside Shilo Village",
    startLocation = MOSOL_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CRAFTING to 20, Skills.AGILITY to 32),
            quests = listOf("Jungle Potion"),
            boostable = setOf(Skills.CRAFTING, Skills.AGILITY),
            extra = listOf("Must be able to defend against many Undead Ones (levels 61-73) and defeat three monsters of about level 90."),
        )

    override val recommendation = Recommendation.combat(85)

    override val reward =
        QuestReward(
            icon = Items.WAMPUM_BELT,
            xp = listOf(Skills.CRAFTING to 3_875.0),
            lines = listOf("2 Quest Points", "3,875 Crafting XP", "Access to Shilo Village", "The Brimhaven - Shilo Village cart"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "A restless spirit haunts Shilo Village. Trufitus in Tai Bwo",
            "Wannai knows how to quiet it. Take this belt to him.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start Shilo Village?") != 1) return false
        chatPlayer("I'll take the belt to Trufitus.", facialExpression = FacialExpression.CALM_TALK)
        FoundationRewards.grant(player, Items.WAMPUM_BELT, 1)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.TRUFITUS,
                journal = listOf("I should take the ${red("wampum belt")} to ${red("Trufitus")} in Tai Bwo Wannai."),
                done = listOf("Trufitus told me to look in the temple."),
                place = "Tai Bwo Wannai",
                location = TRUFITUS_POST,
                talk = {
                    if (!player.inventory.contains(Items.WAMPUM_BELT)) {
                        chatNpc("Bring me the wampum belt from Mosol Rei.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.WAMPUM_BELT)
                        chatNpc("The belt! The spirit is Rashiliyia. Search the temple of", "Shilo for Zadimus's corpse.", facialExpression = FacialExpression.WORRIED)
                        true
                    }
                },
            ),
            QuestStep(
                npc = -1,
                journal = listOf("I should search the ${red("temple")} of Shilo Village for Zadimus's corpse."),
                done = listOf("I found Zadimus's corpse."),
                place = "the temple of Shilo Village",
                location = TEMPLE_TILE,
                search = QuestSearch(TEMPLE_TILE, "You search the temple and find the corpse of Zadimus, and a stone-plaque.", 3, gives = listOf(Items.ZADIMUS_CORPSE to 1)),
            ),
            QuestStep(
                npc = Npcs.TRUFITUS,
                journal = listOf("I should bring ${red("Zadimus's corpse")} to ${red("Trufitus")}."),
                done = listOf("Trufitus gave me a bone key."),
                place = "Tai Bwo Wannai",
                location = TRUFITUS_POST,
                gives = listOf(Items.BONE_KEY to 1),
                talk = {
                    if (!player.inventory.contains(Items.ZADIMUS_CORPSE)) {
                        chatNpc("Bring me Zadimus's corpse.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.ZADIMUS_CORPSE)
                        chatNpc("Good. Take this bone key: it opens the tomb of", "Rashiliyia. Beware the undead one, Nazastarool!", facialExpression = FacialExpression.WORRIED)
                        FoundationRewards.grant(player, Items.BONE_KEY, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.NAZASTAROOL,
                journal = listOf("I must defeat ${red("Nazastarool")} in his three forms (levels 91, 68 and 93)."),
                done = listOf("I defeated Nazastarool and took Rashiliyia's corpse."),
                place = "the tomb of Rashiliyia",
                location = NAZA_FIGHT,
                tip = "Nazastarool rises three times. Bring food.",
                gives = listOf(Items.RASHILIYIA_CORPSE to 1),
                fight =
                    QuestFight(
                        Npcs.NAZASTAROOL, "Nazastarool", 91, NAZA_FIGHT, "Who dares disturb my rest?",
                        chain = listOf(Npcs.NAZASTAROOL_508 to "I rise again!", Npcs.NAZASTAROOL_509 to "You cannot kill what is already dead!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("Nazastarool guards the tomb of Rashiliyia.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = -1,
                journal = listOf("I should lay ${red("Rashiliyia's corpse")} to rest in the Tomb of Bervirius."),
                done = listOf("Rashiliyia's ghost is at rest."),
                place = "the Tomb of Bervirius",
                location = TOMB_TILE,
                search =
                    QuestSearch(
                        TOMB_TILE,
                        "You lay Rashiliyia's corpse in the tomb. Her ghost fades: Shilo Village is at peace.",
                        3,
                        needs = listOf(Items.RASHILIYIA_CORPSE to 1),
                        missing = "You have no corpse to lay to rest here.",
                    ),
            ),
        )
}

private val YANNI_POST = Tile(2835, 2985, 0)
private val FORESTER_POST = Tile(2798, 2944, 0)
private val BRIAN_POST = Tile(3026, 3252, 0)
private val AGGIE_POST = Tile(3086, 3261, 0)
private val GANG_FIGHT = Tile(2967, 9811, 0)
private val SANFEW_POST = Tile(2899, 3430, 1)
private val SLAGILITH_FIGHT = Tile(2587, 9830, 0)

object OneSmallFavour : ShortQuest(
    name = "One Small Favour",
    startPoint = "Speak to Yanni Salika in Shilo Village.",
    rewards = "2 Quest Points, two lamps of 10,000 XP in any skill, the Steel key ring.",
    pointReward = 2,
    questId = Varps.ONE_SMALL_FAVOUR_PROGRESS,
    slot = 82,
    completedValue = 8, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.YANNI_SALIKA,
    startNpcName = "Yanni Salika",
    startPlace = "Shilo Village",
    startLocation = YANNI_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 36, Skills.CRAFTING to 25, Skills.HERBLORE to 18, Skills.SMITHING to 30),
            quests = listOf("Rune Mysteries", "Shilo Village"),
            boostable = setOf(Skills.AGILITY, Skills.CRAFTING, Skills.HERBLORE, Skills.SMITHING),
        )

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.STEEL_KEY_RING,
            lamps = List(2) { QuestLamp(10_000, FoundationRewards.ALL_SKILLS, 30, "One Small Favour") },
            items = listOf(Items.STEEL_KEY_RING to 1),
            lines = listOf("2 Quest Points", "Two lamps of 10,000 XP (any skill of level 30+)", "The Steel key ring"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "I wonder... would you do me a small favour? It is a long",
            "chain of errands, but the reward is good.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll do the favour.", "Not right now.", title = "Start One Small Favour?") != 1) return false
        chatPlayer("I'll do it.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Start with the Jungle Forester south of the village.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.JUNGLE_FORESTER,
                journal = listOf("I should speak to the ${red("Jungle Forester")} south of Shilo Village."),
                done = listOf("The forester wants his axe sharpened by Brian."),
                place = "south of Shilo Village",
                location = FORESTER_POST,
                talk = {
                    chatNpc("My axe is blunt. Brian in Port Sarim could sharpen it -", "tell him I sent you.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRIAN,
                journal = listOf("I should speak to ${red("Brian")} at the battleaxe bazaar in Port Sarim."),
                done = listOf("Brian wants something from Aggie first."),
                place = "Port Sarim",
                location = BRIAN_POST,
                talk = {
                    chatNpc("I will sharpen it, but I am out of grindstone dust.", "Ask Aggie in Draynor.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.AGGIE,
                journal = listOf("I should speak to ${red("Aggie")} in Draynor Village."),
                done = listOf("Aggie sent me to Hammerspike Stoutbeard."),
                place = "Draynor Village",
                location = AGGIE_POST,
                talk = {
                    chatNpc("Grindstone dust? Hammerspike Stoutbeard in the Dwarven", "Mine has some, but his gang guards him.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DWARF_GANG_MEMBER,
                journal = listOf("I must defeat the ${red("dwarf gang members")} (levels 44, 48 and 49) near", "Hammerspike Stoutbeard in the Dwarven Mine."),
                done = listOf("I defeated the dwarf gang."),
                place = "the Dwarven Mine",
                location = GANG_FIGHT,
                tip = "The dwarves come one after the other.",
                fight =
                    QuestFight(
                        Npcs.DWARF_GANG_MEMBER, "Dwarf gang member", 44, GANG_FIGHT, "Get him, lads!",
                        chain = listOf(Npcs.DWARF_GANG_MEMBER_1796 to "You will pay for that!", Npcs.DWARF_GANG_MEMBER_1797 to "Last one standing!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("Hammerspike's gang is in the Dwarven Mine.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SANFEW,
                journal = listOf("I should speak to ${red("Sanfew")} in Taverley about Guthix rest."),
                done = listOf("Sanfew told me where Slagilith lurks."),
                place = "Taverley",
                location = SANFEW_POST,
                talk = {
                    chatNpc("The last thing you need is in the Goblin Cave: Slagilith.", "Defeat it to free Petra Fyiad.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SLAGILITH,
                journal = listOf("I must defeat ${red("Slagilith")} (level 92) in the cave."),
                done = listOf("I defeated Slagilith: Petra Fyiad is free."),
                place = "the Goblin Cave",
                location = SLAGILITH_FIGHT,
                tip = "Slagilith is level 92 and can be safespotted.",
                fight = QuestFight(Npcs.SLAGILITH, "Slagilith", 92, SLAGILITH_FIGHT, "Rumble..."),
                talk = {
                    chatNpc("Slagilith guards the Goblin Cave.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.YANNI_SALIKA,
                journal = listOf("I should tell ${red("Yanni Salika")} the favour is done."),
                done = listOf("Yanni gave me the Steel key ring."),
                place = "Shilo Village",
                location = YANNI_POST,
                talk = {
                    chatNpc("You have done me a great favour! Take this Steel key", "ring, and two lamps.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
