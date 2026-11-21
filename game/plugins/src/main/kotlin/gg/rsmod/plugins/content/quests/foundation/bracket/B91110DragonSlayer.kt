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

// PROJECT48 Q27: Dragon Slayer (bracket 91-110). OSRS Wiki: 32 quest points (the Champions' Guild door); Melzar the Mad (43), Wormbrain
// (2) and Elvarg (83); 2 QP, 18,650 Strength + 18,650 Defence XP. ONTGR: the Rune platebody, Green d'hide body and Dragon platebody are
// worn, and the rune platebody sold by Oziach, only after this quest (OsrsQuestRequirements.WEAR + Oziach's shop); the Champions' Guild
// door asks 32 quest points (OSRS) instead of all quests. Not built: Crandor sailing (Ned moves the player to Elvarg's lair),
// dragonfire protection, dragons as a Slayer task (slayer is parked, N574).

private val GUILDMASTER_POST = Tile(3190, 3358, 0)
private val OZIACH_POST = Tile(3069, 3516, 0)
private val MELZAR_FIGHT = Tile(2929, 9649, 0)
private val ORACLE_POST = Tile(3010, 3502, 0)
private val WORMBRAIN_POST = Tile(3014, 3189, 0)
private val DUKE_POST = Tile(3212, 3220, 1)
private val NED_POST = Tile(3098, 3257, 0)
private val ELVARG_LAIR = Tile(2854, 9636, 0)

object DragonSlayer : ShortQuest(
    name = "Dragon Slayer",
    startPoint = "Speak to the Guildmaster in the Champions' Guild, south-west of Varrock.",
    rewards = "2 Quest Points, 18,650 Strength XP, 18,650 Defence XP, the right to wear the Rune platebody, Green d'hide body and Dragon platebody.",
    pointReward = 2,
    questId = Varps.DRAGON_SLAYER_PROGRESS,
    slot = 4,
    completedValue = 9, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.GUILDMASTER,
    startNpcName = "Guildmaster",
    startPlace = "the Champions' Guild",
    startLocation = GUILDMASTER_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(questPoints = 32, extra = listOf("Must be able to defeat a level 83 dragon (Elvarg)."))

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.ANTIDRAGON_SHIELD,
            xp = listOf(Skills.STRENGTH to 18_650.0, Skills.DEFENCE to 18_650.0),
            lines = listOf("2 Quest Points", "18,650 Strength XP", "18,650 Defence XP", "The right to wear a Rune platebody,", "Green d'hide body and Dragon platebody"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Greetings! This is the Champions' Guild. Only the",
            "best adventurers may enter. You may wear a rune",
            "platebody once you have slain Elvarg of Crandor.",
            facialExpression = FacialExpression.HAPPY,
        )
        if (options("I'll slay Elvarg.", "Not right now.", title = "Start Dragon Slayer?") != 1) return false
        chatPlayer("I will slay the dragon Elvarg.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Then speak to Oziach in Edgeville: he knows the dragon.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.OZIACH,
                journal = listOf("I should speak to ${red("Oziach")} in Edgeville about Elvarg."),
                done = listOf("Oziach told me to find three map pieces and a ship."),
                place = "Edgeville",
                location = OZIACH_POST,
                talk = {
                    chatNpc("Elvarg lives on Crandor. To reach it you need the three", "pieces of Crandor's map, a ship, and a shield against", "dragonfire.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.OZIACH,
                journal = listOf("I must defeat ${red("Melzar the Mad")} (level 43) in Melzar's Maze for a map piece."),
                done = listOf("I defeated Melzar the Mad and took his map piece."),
                place = "Melzar's Maze",
                location = MELZAR_FIGHT,
                tip = "Melzar the Mad is only level 43.",
                gives = listOf(Items.MAP_PART_1536 to 1),
                fight = QuestFight(Npcs.MELZAR_THE_MAD, "Melzar the Mad", 43, MELZAR_FIGHT, "None shall pass me!"),
                talk = {
                    chatNpc("Melzar the Mad guards the maze north of Rimmington.", "Defeat him and take the map piece.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ORACLE,
                journal = listOf("I should speak to the ${red("Oracle")} on Ice Mountain for a map piece."),
                done = listOf("The Oracle gave me a map piece."),
                place = "Ice Mountain",
                location = ORACLE_POST,
                gives = listOf(Items.MAP_PART_1537 to 1),
                talk = {
                    chatNpc("The map piece you seek lies in my care. Take it.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.MAP_PART_1537, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.WORMBRAIN,
                journal = listOf("I must find ${red("Wormbrain")} in the Port Sarim jail for a map piece."),
                done = listOf("Wormbrain gave me his map piece."),
                place = "the Port Sarim jail",
                location = WORMBRAIN_POST,
                gives = listOf(Items.MAP_PART to 1),
                talk = {
                    chatNpc("Me not give map piece! ...Oh, you got the Wormbrain", "crown jewels? Here, take it.", facialExpression = FacialExpression.WORRIED)
                    FoundationRewards.grant(player, Items.MAP_PART, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DUKE_HORACIO,
                journal = listOf("I should ask ${red("Duke Horacio")} in Lumbridge Castle for an anti-dragon", "shield."),
                done = listOf("The Duke gave me an anti-dragon shield."),
                place = "Lumbridge Castle",
                location = DUKE_POST,
                gives = listOf(Items.ANTIDRAGON_SHIELD to 1),
                talk = {
                    chatNpc("A brave dragon slayer! Take this shield: it will protect", "you from the dragon's fire.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.ANTIDRAGON_SHIELD, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.NED,
                journal = listOf("I should give ${red("Ned")} the three map pieces and 2,000 coins for the ship", "to Crandor."),
                done = listOf("Ned sailed me to Crandor."),
                place = "Draynor Village",
                location = NED_POST,
                talk = {
                    val p = player
                    val parts = listOf(Items.MAP_PART, Items.MAP_PART_1536, Items.MAP_PART_1537)
                    if (parts.any { !p.inventory.contains(it) } || p.inventory.getItemCount(Items.COINS_995) < 2_000) {
                        chatNpc("You need all three map pieces and 2,000 coins for the", "ship, and the voyage is dangerous.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        parts.forEach { p.inventory.remove(it) }
                        p.inventory.remove(Items.COINS_995, 2_000)
                        chatNpc("The map is whole! All aboard - next stop: Crandor.", facialExpression = FacialExpression.HAPPY)
                        p.moveTo(Tile(ELVARG_LAIR.x + 3, ELVARG_LAIR.z + 3, ELVARG_LAIR.height))
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.NED,
                journal = listOf("I must slay ${red("Elvarg")} (level 83) in her lair on Crandor."),
                done = listOf("I slew Elvarg of Crandor!"),
                place = "Elvarg's lair on Crandor",
                location = ELVARG_LAIR,
                tip = "Wear the anti-dragon shield: Elvarg breathes fire.",
                fight = QuestFight(Npcs.ELVARG, "Elvarg", 83, ELVARG_LAIR, "ROAR!"),
                talk = {
                    chatNpc("Elvarg is in her lair on Crandor. Wear the shield!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.OZIACH,
                journal = listOf("I should tell ${red("Oziach")} that Elvarg is dead."),
                done = listOf("Oziach let me buy rune platebodies."),
                place = "Edgeville",
                location = OZIACH_POST,
                talk = {
                    chatNpc("Ye slew the dragon! Then ye have earned the right to", "wear a rune platebody - ask me to sell ye one.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
