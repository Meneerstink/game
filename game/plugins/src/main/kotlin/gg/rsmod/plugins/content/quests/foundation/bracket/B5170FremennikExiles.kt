package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.OsrsQuestSlots
import gg.rsmod.plugins.content.quests.foundation.OsrsQuestVarps
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 X1 (run 3, 22-11): The Fremennik Exiles (bracket 51-70, Fremennik storyline, Master). OSRS Wiki: 65 Crafting, 60 Slayer,
// 60 Smithing, 60 Fishing, 55 Runecraft (not boostable), 60 Mining (boostable); The Fremennik Isles, Lunar Diplomacy, Mountain Daughter,
// Heroes' Quest. Rewards 2 QP, 50,000 Slayer, 50,000 Crafting, 30,000 Runecraft XP (wiki Rewards); V's shield is not on this server.
//  - Owner 22-11 (choice a): places that do not exist are played at the nearest existing place, with the OSRS story and npcs; no map
//    import, npc import yes. The Island of Stone does not exist: Typhor and Jormungand are fought on the frozen shore north of
//    Rellekka (the same open snow as Secrets of the North).
//  - Imported OSRS npcs (batch p48x, 22-11): Basilisk Youngling, Monstrous Basilisk, Typhor, Jormungand ([XNpcs]); the market
//    invasion uses the server's own Basilisk (level 61, as OSRS).
//  - Not built: the exile itself (townspeople stay friendly), Baba Yaga / the Astral Altar / the geyser (Lunar Isle and the Mountain
//    Camp springs are dialogue), the stat drain without a mirror shield, Jormungand's petrify and the Island of Stone puzzles.
//  All tiles that are not a spawn are estimates checked with the server clipmap (dossier BRACKET-QUESTS).

private val BRUNDT_POST = Tile(2658, 3667, 0)
private val FREYGERD_POST = Tile(2667, 3701, 0)
private val SAND_PIT = Tile(2662, 3705, 0) // west side of the sand pit, free 3x3 (clipmap 22-11): 2668,3706 had a wall north, the youngling landed outside the fence
private val RELLEKKA_ANVIL = Tile(2615, 3669, 0)
private val MARKET = Tile(2644, 3677, 0) // south-west corner of a free 3x3 (clipmap 22-11): the monstrous basilisk is size 3
private val TYPHOR_SHORE = Tile(2867, 3931, 0) // free 3x3
private val JORMUNGAND_SHORE = Tile(2866, 3932, 0) // free 5x5 (also used by Secrets of the North): Jormungand is size 5

private const val KEGS = 2
private const val ASTRALS = 100

object FremennikExiles : ShortQuest(
    name = "The Fremennik Exiles",
    startPoint = "Speak to Brundt the Chieftain in the Rellekka longhall.",
    rewards = "2 Quest Points, 50,000 Slayer XP, 50,000 Crafting XP, 30,000 Runecraft XP.",
    pointReward = 2,
    questId = OsrsQuestVarps.THE_FREMENNIK_EXILES,
    slot = OsrsQuestSlots.THE_FREMENNIK_EXILES,
    completedValue = 10, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.BRUNDT_THE_CHIEFTAIN,
    startNpcName = "Brundt the Chieftain",
    startPlace = "the Rellekka longhall",
    startLocation = BRUNDT_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CRAFTING to 65, Skills.SLAYER to 60, Skills.SMITHING to 60, Skills.FISHING to 60, Skills.RUNECRAFTING to 55, Skills.MINING to 60),
            quests = listOf("The Fremennik Isles", "Lunar Diplomacy", "Mountain Daughter", "Heroes' Quest"),
            boostable = setOf(Skills.MINING),
        )

    override val recommendation = Recommendation.combat(110)

    override val reward =
        QuestReward(
            icon = Items.MIRROR_SHIELD,
            xp = listOf(Skills.SLAYER to 50_000.0, Skills.CRAFTING to 50_000.0, Skills.RUNECRAFTING to 30_000.0),
            lines = listOf("2 Quest Points", "50,000 Slayer XP", "50,000 Crafting XP", "30,000 Runecraft XP"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Something disturbed the folk by the sand",
            "pit last night. Freygerd will not stop",
            "talking about red eyes at her window.",
            "Will you look into it, outerlander?",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll look into it.", "Not right now.", title = "Start The Fremennik Exiles?") != 1) return false
        chatPlayer("Leave it to me.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.FREYGERD,
                journal = listOf("I should speak to ${red("Freygerd")} in her house by the sand pit in the", "north of Rellekka."),
                done = listOf("Freygerd saw a pair of red eyes staring through her window."),
                place = "the north of Rellekka",
                location = FREYGERD_POST,
                talk = {
                    chatNpc(
                        "Two red eyes, outerlander, right there at the",
                        "window! And this morning the boxes outside were",
                        "knocked over and the sand pit was all churned up.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FREYGERD,
                journal = listOf("Something is buried in the sand pit. I must defeat the ${red("Basilisk Youngling")}", "(level 57) by the sand pit of Rellekka."),
                done = listOf("A basilisk youngling hid in the sand pit. It carried an unsealed letter:", "a basilisk army is coming for Rellekka."),
                place = "the sand pit of Rellekka",
                location = SAND_PIT,
                tip = "A mirror shield blocks its gaze. Hits up to 5.",
                fight = QuestFight(XNpcs.BASILISK_YOUNGLING, "Basilisk Youngling", 57, SAND_PIT, ""),
                talk = {
                    chatNpc("Look in the sand pit - something is down there!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I should show the letter to ${red("Brundt the Chieftain")} in the longhall."),
                done = listOf("The Serpent King Jormungand is waking. Only a new V's shield can", "reflect his gaze - Brundt told me how to make one."),
                place = "the Rellekka longhall",
                location = BRUNDT_POST,
                talk = {
                    chatNpc(
                        "Basilisks? The army of Jormungand, the",
                        "Serpent King, who once enslaved our",
                        "people! The hero V threw him down with",
                        "a shield that reflected his gaze.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    chatNpc(
                        "The Council blames you, outerlander,",
                        "and wants you gone - but I need you.",
                        "Make a new V's shield: lunar glass, V's",
                        "sigil and a polished rock on a shield.",
                        facialExpression = FacialExpression.SAD,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf(
                    "I should forge ${red("V's shield")} at the anvil in the south-west of Rellekka.",
                    "I need ${red("1 molten glass")} and ${red("$ASTRALS astral runes")} for the lunar glass.",
                ),
                done = listOf("I forged V's shield: lunar glass, V's sigil and a polished rock."),
                place = "the anvil of Rellekka",
                location = RELLEKKA_ANVIL,
                tip = "Bring 1 molten glass and $ASTRALS astral runes.",
                search =
                    QuestSearch(
                        RELLEKKA_ANVIL,
                        "You infuse the glass with astral power, hammer out V's sigil and fit the polished rock: V's shield is whole again.",
                        3,
                        needs = listOf(Items.MOLTEN_GLASS to 1, Items.ASTRAL_RUNE to ASTRALS),
                        missing = "You need 1 molten glass and $ASTRALS astral runes to make the lunar glass for V's shield.",
                    ),
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I should show V's shield to ${red("Brundt")}. He asked for ${red("$KEGS kegs of beer")}", "for the Council."),
                done = listOf("The Council ended my exile. The basilisks are attacking the market!"),
                place = "the Rellekka longhall",
                location = BRUNDT_POST,
                tip = "Bring $KEGS kegs of beer.",
                talk = {
                    chatNpc("You made it? Then bring the Council", "$KEGS kegs of beer and they will listen.", facialExpression = FacialExpression.HAPPY)
                    if (player.inventory.getItemCount(Items.KEG_OF_BEER) < KEGS) {
                        chatPlayer("I don't have $KEGS kegs of beer on me.", facialExpression = FacialExpression.SAD)
                        false
                    } else {
                        player.inventory.remove(Items.KEG_OF_BEER, KEGS)
                        chatNpc(
                            "The Council takes you back, Fremennik! Now hurry:",
                            "the basilisks are in the market!",
                            facialExpression = FacialExpression.TOUGH,
                        )
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I must defeat the ${red("Monstrous Basilisk")} (level 135) and its basilisks in", "the market of Rellekka."),
                done = listOf("The basilisks were driven out of the market. Their king waits on the", "shore to the north."),
                place = "the market of Rellekka",
                location = MARKET,
                tip = "Wear a mirror shield. Hits up to 11, two basilisks follow.",
                fight =
                    QuestFight(
                        XNpcs.MONSTROUS_BASILISK, "Monstrous Basilisk", 135, MARKET, "",
                        chain = listOf(Npcs.BASILISK to "Another basilisk crawls out of the crowd!", Npcs.BASILISK to "One more basilisk!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("To the market! Hold up V's shield - or a", "mirror shield - against their gaze and fight!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("The serpent's guardian waits on the frozen shore north of Rellekka.", "I must defeat ${red("Typhor")} (level 218)."),
                done = listOf("Typhor, the Serpent King's guardian, is dead."),
                place = "the frozen shore north of Rellekka",
                location = TYPHOR_SHORE,
                tip = "Melee and magic up to 20. Bring food.",
                fight = QuestFight(XNpcs.TYPHOR, "Typhor", 218, TYPHOR_SHORE, ""),
                talk = {
                    chatNpc("North, on the frozen shore.", "Typhor guards the Serpent King.", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I must defeat ${red("Jormungand")}, the Serpent King (level 363), on the frozen", "shore north of Rellekka."),
                done = listOf("Jormungand, the Serpent King, is defeated."),
                place = "the frozen shore north of Rellekka",
                location = JORMUNGAND_SHORE,
                tip = "Melee and magic up to 22. Bring food.",
                fight = QuestFight(XNpcs.JORMUNGAND, "Jormungand", 363, JORMUNGAND_SHORE, ""),
                talk = {
                    chatNpc("The Serpent King himself! End this, Fremennik!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I should tell ${red("Brundt the Chieftain")} in the longhall that the Serpent", "King is defeated."),
                done = listOf("Rellekka is safe. Brundt toasted me as a hero of the Fremennik."),
                place = "the Rellekka longhall",
                location = BRUNDT_POST,
                talk = {
                    chatPlayer("Jormungand is defeated.", facialExpression = FacialExpression.CALM_TALK)
                    chatNpc("Then the songs will be about", "you for a hundred years! Skal!", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
