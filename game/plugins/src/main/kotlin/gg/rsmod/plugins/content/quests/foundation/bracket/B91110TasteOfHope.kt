package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
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

// PROJECT48 X2 + X3 (run 3, 22-11): the Myreque storyline after Darkness of Hallowvale (bracket 91-110). OSRS Wiki:
//  - A Taste of Hope: Darkness of Hallowvale; 48 Crafting, 45 Agility, 40 Attack, 40 Herblore, 38 Slayer (none boostable); the
//    Abomination (149) and Ranis Drakan (233, two level 87 vyrewatch at 65%); 1 QP, the Ivandis flail, Drakan's medallion and a
//    Tome of experience (3 x 2,500 XP, level 35+).
//  - A Night at the Theatre: A Taste of Hope; a vyrewatch in Ranis' crypt, araxytes at the spider nest, the Hespori (302) and the
//    Theatre of Blood; 2 QP, four antique lamps of 20,000 XP (Attack/Strength/Defence/Ranged/Magic/Hitpoints, level 50+).
//  - Owner 22-11 (choice a): places that do not exist are played at the nearest existing place, with the OSRS story and npcs; no map
//    import, npc import yes. The crypt below Ver Sinhaza, the spider nest by Slepe, the Sisterhood Sanctuary and the Theatre of
//    Blood are not on this server: the vyrewatch is fought in Ver Sinhaza, the nest is the north of Mort Myre, the sulphuric acid is
//    a search and the Theatre of Blood part is told by the Mysterious Stranger (ToB itself = N523, later).
//  - The Ivandis flail is not on this server: the flail is made in a search step (story only) and Ranis can be hurt by any weapon;
//    the reward is Drakan's medallion + the tome (dossier BRACKET-QUESTS, deviations).
//  - Imported OSRS npcs (batch p48x, 22-11): Garth, the Abomination, Ranis Drakan, the Mysterious Stranger, the Araxyte, the Hespori
//    ([XNpcs]); the vyrewatch are the server's own (4805, level 110).
//  All tiles that are not a spawn are estimates checked with the server clipmap (dossier BRACKET-QUESTS).

private val GARTH_POST = Tile(3667, 3216, 0)
private val KAEL_POST = Tile(3664, 3219, 0)
private val RANIS_FIGHT = Tile(3659, 3219, 0) // 3660,3220 lies under loc 64066 (NewPlayerFoundationTests)
private val TOH_SAFALAAN = Tile(3589, 3335, 0)
private val HIDEOUT_FIGHT = Tile(3614, 3215, 0) // free 2x2 (clipmap 22-11): the abomination is size 2
private val RAL_HOUSE = Tile(3602, 3209, 0)
private val STRANGER_POST = Tile(3672, 3225, 0)
private val FILLIMAN_POST = Tile(3440, 3336, 0) // his bracket spawn (B0350Quests)
private val SPIDER_NEST = Tile(3430, 3460, 0)
private val HESPORI_ISLAND = Tile(3477, 3427, 0) // free 3x3: the hespori is size 3

object ATasteOfHope : ShortQuest(
    name = "A Taste of Hope",
    startPoint = "Speak to Garth in Ver Sinhaza, by the entrance to the Theatre of Blood.",
    rewards = "1 Quest Point, Drakan's medallion, a Tome of experience (3 x 2,500 XP).",
    pointReward = 1,
    questId = OsrsQuestVarps.A_TASTE_OF_HOPE,
    slot = OsrsQuestSlots.A_TASTE_OF_HOPE,
    completedValue = 8, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = XNpcs.GARTH,
    startNpcName = "Garth",
    startPlace = "Ver Sinhaza",
    startLocation = GARTH_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CRAFTING to 48, Skills.AGILITY to 45, Skills.ATTACK to 40, Skills.HERBLORE to 40, Skills.SLAYER to 38),
            quests = listOf("Darkness of Hallowvale"),
        )

    override val recommendation = Recommendation.combat(85)

    override val reward =
        QuestReward(
            icon = Items.DRAKANS_MEDALLION,
            items = listOf(Items.DRAKANS_MEDALLION to 1),
            lamps = List(3) { QuestLamp(2_500, FoundationRewards.ALL_SKILLS, 35, "Tome of experience") },
            lines = listOf("1 Quest Point", "Drakan's medallion", "A Tome of experience (3 x 2,500 XP)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Have you heard the tale of Serafina? She",
            "fought the vampyres in the Theatre and",
            "never came out. Ranis Drakan himself",
            "watches the games from up there.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("Tell Safalaan what I saw.", "Not right now.", title = "Start A Taste of Hope?") != 1) return false
        chatPlayer("The Myreque should hear about this.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should tell ${red("Safalaan")} at the Myreque base what I saw at the", "Theatre of Blood."),
                done = listOf("Safalaan wants to know what Vanstrom is planning in Ver Sinhaza."),
                place = "the Myreque base, Meiyerditch",
                location = TOH_SAFALAAN,
                talk = {
                    chatNpc(
                        "Ranis watches the Theatre, but Vanstrom never goes",
                        "there. Something is wrong. Climb the Theatre walls",
                        "and listen, then come back to me.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should listen at the walls of the ${red("Theatre of Blood")} in Ver Sinhaza."),
                done = listOf("I overheard Vanstrom and Ranis: Vanstrom has a plan for the Myreque."),
                place = "the walls of the Theatre of Blood",
                location = KAEL_POST,
                search =
                    QuestSearch(
                        KAEL_POST,
                        "You creep along the wall and hear Ranis and Vanstrom: the Myreque will walk into their trap.",
                        3,
                    ),
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should report what I heard to ${red("Safalaan")}."),
                done = listOf("It was a trap: Vanstrom's abomination broke into the Meiyerditch hideout!"),
                place = "the Myreque base, Meiyerditch",
                location = TOH_SAFALAAN,
                talk = {
                    chatNpc("A trap? Then we leave tonight. Meet us at the old", "hideout in Meiyerditch.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I must defeat the ${red("Abomination")} (level 149) in the Meiyerditch hideout."),
                done = listOf("The abomination is dead, but the Myreque lost many friends."),
                place = "the Meiyerditch hideout",
                location = HIDEOUT_FIGHT,
                tip = "Melee and ranged up to 23. Pray missiles.",
                fight = QuestFight(XNpcs.ABOMINATION, "Abomination", 149, HIDEOUT_FIGHT, ""),
                talk = {
                    chatNpc("Vanstrom's creature! Kill", "it before it kills us all!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf(
                    "Flaygian's notes say a flail can hurt a vampyre lord. I should forge",
                    "the ${red("Ivandis flail")} in Old Man Ral's house (bring an ${red("emerald")}).",
                ),
                done = listOf("I made the Ivandis flail from the Rod of Ivandis, a silver sickle and a chain."),
                place = "Old Man Ral's house",
                location = RAL_HOUSE,
                tip = "Bring 1 emerald.",
                search =
                    QuestSearch(
                        RAL_HOUSE,
                        "You set the enchanted emerald in a silver sickle and chain it to the Rod of Ivandis: the Ivandis flail.",
                        3,
                        needs = listOf(Items.EMERALD to 1),
                        missing = "You need an emerald for the Ivandis flail.",
                    ),
            ),
            QuestStep(
                npc = XNpcs.GARTH, // Kael Forshaw 4465 has no Talk-to in this cache (NewPlayerFoundationTests): Garth stands in Ver Sinhaza too
                journal = listOf("Ranis Drakan speaks to the people in Ver Sinhaza. I must defeat", "${red("Ranis Drakan")} (level 233)."),
                done = listOf("Ranis Drakan is dead. The people of Meiyerditch heard my speech."),
                place = "Ver Sinhaza",
                location = RANIS_FIGHT,
                tip = "Hits up to 20, two vyrewatch help him.",
                fight =
                    QuestFight(
                        XNpcs.RANIS_DRAKAN, "Ranis Drakan", 233, RANIS_FIGHT, "You dare to face me, human?",
                        chain = listOf(Npcs.VYREWATCH_4805 to "Vyrewatch, to me!", Npcs.VYREWATCH_4805 to "Destroy this human!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("He is up there, telling them the Myreque", "are dead. Show them they are wrong!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should tell ${red("Safalaan")} that Ranis Drakan is dead."),
                done = listOf("Kael gave me Drakan's medallion from Ranis' body. The people of", "Meiyerditch have hope again."),
                place = "the Myreque base, Meiyerditch",
                location = TOH_SAFALAAN,
                talk = {
                    chatPlayer("Ranis Drakan is dead.", facialExpression = FacialExpression.CALM_TALK)
                    chatNpc("You have given Meiyerditch a taste", "of hope, my friend. Kael found this", "medallion on his body - it is yours.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

object ANightAtTheTheatre : ShortQuest(
    name = "A Night at the Theatre",
    startPoint = "Speak to the Mysterious Stranger in Ver Sinhaza.",
    rewards = "2 Quest Points, four antique lamps (20,000 combat XP each).",
    pointReward = 2,
    questId = OsrsQuestVarps.A_NIGHT_AT_THE_THEATRE,
    slot = OsrsQuestSlots.A_NIGHT_AT_THE_THEATRE,
    completedValue = 9, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = XNpcs.MYSTERIOUS_STRANGER,
    startNpcName = "Mysterious Stranger",
    startPlace = "Ver Sinhaza",
    startLocation = STRANGER_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(quests = listOf("A Taste of Hope"))

    override val recommendation = Recommendation.combat(100)

    override val reward =
        QuestReward(
            icon = Items.ANTIQUE_LAMP,
            lamps = List(4) { QuestLamp(20_000, FoundationRewards.COMBAT_SKILLS, 50, "Antique lamp (combat)") },
            lines = listOf("2 Quest Points", "Four antique lamps (20,000 XP in Attack, Strength,", "Defence, Ranged, Magic or Hitpoints)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Verzik Vitur grows stronger, and nobody knows",
            "why. Ranis' memories could tell us. You killed",
            "him - will you bring me what is left of him?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll help you.", "Not right now.", title = "Start A Night at the Theatre?") != 1) return false
        chatPlayer("I'll help you.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = XNpcs.MYSTERIOUS_STRANGER,
                journal = listOf("I must defeat the ${red("Vyrewatch")} (level 110) guarding Ranis' remains in", "Ver Sinhaza."),
                done = listOf("I took Ranis' remains to the Mysterious Stranger. His memory showed", "Verzik's purple spider eggs."),
                place = "Ver Sinhaza",
                location = RANIS_FIGHT,
                tip = "The vyrewatch hits up to 11.",
                fight = QuestFight(Npcs.VYREWATCH_4805, "Vyrewatch", 110, RANIS_FIGHT, "Who disturbs Lord Ranis?"),
                talk = {
                    chatNpc("His crypt is guarded. Deal with the guard.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = XNpcs.MYSTERIOUS_STRANGER,
                journal = listOf("I should look for the ${red("spider nest")} in the north of Mort Myre."),
                done = listOf("The egg sac is too hard to cut. I need acid to dissolve it."),
                place = "the north of Mort Myre",
                location = SPIDER_NEST,
                search = QuestSearch(SPIDER_NEST, "You find a huge egg sac, but no blade can cut it. Acid might dissolve it.", 3),
            ),
            QuestStep(
                npc = XNpcs.MYSTERIOUS_STRANGER,
                journal = listOf("I must defeat the ${red("Araxyte")} (level 96) guarding the egg sac."),
                done = listOf("I dissolved the egg sac and took the strange spider eggs."),
                place = "the north of Mort Myre",
                location = SPIDER_NEST,
                tip = "Araxytes hit up to 13.",
                fight = QuestFight(XNpcs.ARAXYTE, "Araxyte", 96, SPIDER_NEST, ""),
                talk = {
                    chatNpc("Dissolve the sac and take the", "eggs. Watch for the spiders.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = XNpcs.MYSTERIOUS_STRANGER,
                journal = listOf("I should bring the spider eggs to the ${red("Mysterious Stranger")} in Ver Sinhaza."),
                done = listOf("The memory showed Verzik breeding nylocas with the bark of a hespori."),
                place = "Ver Sinhaza",
                location = STRANGER_POST,
                talk = {
                    chatNpc("Nylocas... bred with something from", "a plant. Find out what a hespori is.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FILLIMAN_TARLOCK,
                journal = listOf("I should ask ${red("Filliman Tarlock")} at the Nature Grotto in Mort Myre about", "the hespori."),
                done = listOf("Filliman told me a hespori grows on an island north-east in the swamp."),
                place = "the Nature Grotto, Mort Myre",
                location = FILLIMAN_POST,
                talk = {
                    chatNpc("A hespori? Very rare. There is", "one to the north-east, on a small", "island in the swamp. Be careful.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.FILLIMAN_TARLOCK,
                journal = listOf("I must defeat the ${red("Hespori")} (level 302) on the island in Mort Myre."),
                done = listOf("I cut a hespori bark from the dead plant."),
                place = "the island in Mort Myre",
                location = HESPORI_ISLAND,
                tip = "Ranged up to 8, magic up to 14.",
                fight = QuestFight(XNpcs.HESPORI, "Hespori", 302, HESPORI_ISLAND, ""),
                talk = {
                    chatNpc("North-east, on the island.", "It will fight for its life.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = XNpcs.MYSTERIOUS_STRANGER,
                journal = listOf("I should bring the hespori bark to the ${red("Mysterious Stranger")}."),
                done = listOf("The memory showed the Nylocas Queen. Then I fought through the Theatre", "of Blood beside the Stranger, and Verzik fled as a bat."),
                place = "Ver Sinhaza",
                location = STRANGER_POST,
                talk = {
                    chatNpc(
                        "The Nylocas Queen... her bite gave Verzik",
                        "her powers. We went through the Theatre",
                        "together, you and I, and when Verzik fled",
                        "I touched her. One memory is left.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = XNpcs.MYSTERIOUS_STRANGER,
                journal = listOf("I should talk to the ${red("Mysterious Stranger")} once more."),
                done = listOf("The Verzik in the Theatre is the Nylocas Queen. The Stranger thanked", "me with four antique lamps."),
                place = "Ver Sinhaza",
                location = STRANGER_POST,
                talk = {
                    chatNpc(
                        "That was no vampyre. The Verzik in the Theatre",
                        "is the Nylocas Queen herself, and she wants",
                        "Morytania. Take these lamps - you earned them.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
        )
}
