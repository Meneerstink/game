package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varbits
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

// PROJECT48 Q31-Q33: the Myreque trilogy (bracket 91-110). OSRS Wiki:
//  - In Search of the Myreque: Nature Spirit, Priest in Peril, The Restless Ghost, 25 Agility (boostable), a level 97 Skeleton Hellhound;
//    2 QP, 600 Attack/Defence/Strength/Hitpoints/Crafting XP.
//  - In Aid of the Myreque: the above + 25 Crafting, 15 Mining, 7 Magic, 25 Agility; Gadderanks (35) + Vampyre Juvinates; 2 QP, 2,000
//    Attack/Strength/Crafting/Defence XP, the Gadderhammer, the Rod of Ivandis.
//  - Darkness of Hallowvale: 5 Construction, 20 Mining, 22 Thieving, 26 Agility, 32 Crafting, 33 Magic, 40 Strength; no kill (Vanstrom's
//    five blows are a search step at the lookout); 2 QP, 7,000 Agility, 6,000 Thieving, 2,000 Construction XP, a Tome of experience.
// ONTGR not built: the Hollows / fairy ring DLS, Burgh de Rott bank/shop/furnace, Meiyerditch shortcut doors and the Temple Trekking minigame
// do not exist on this server (dossier BRACKET-QUESTS). Tiles: Void donor spawns; Vanstrom Klause (no donor tile) stands in the Hair of the Dog.

private val VANSTROM_POST = Tile(3496, 3492, 0)
private val CYREG_POST = Tile(3522, 3284, 0)
private val CURPILE_POST = Tile(3508, 3440, 0)
private val HIDEOUT_VELIAF = Tile(3494, 9628, 0)
private val HELLHOUND_FIGHT = Tile(3496, 9629, 0)
private val BURGH_VELIAF = Tile(3515, 3242, 0)
private val FLORIN_POST = Tile(3483, 3242, 0)
private val RAZVAN_POST = Tile(3492, 3235, 0)
private val GADDERANKS_FIGHT = Tile(3514, 3240, 0)
private val DREZEL_POST = Tile(3415, 3489, 2)
private val IVAN_POST = Tile(3484, 3243, 0)
private val JUVINATE_FIGHT = Tile(3484, 3241, 0)
private val RAL_POST = Tile(3602, 3209, 0)
private val SAFALAAN_POST = Tile(3589, 3335, 0)
private val LOOKOUT = Tile(3593, 3340, 0)
private val SARIUS_POST = Tile(3590, 3337, 0)
private val ROALD_POST = Tile(3220, 3471, 0)

object InSearchOfTheMyreque : ShortQuest(
    name = "In Search of the Myreque",
    startPoint = "Speak to Vanstrom Klause at the Hair of the Dog tavern in Canifis.",
    rewards = "2 Quest Points, 600 Attack, Defence, Strength, Hitpoints and Crafting XP.",
    pointReward = 2,
    questId = Varps.IN_SEARCH_OF_THE_MYREQUE_PROGRESS,
    slot = 65,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.VANSTROM_KLAUSE,
    startNpcName = "Vanstrom Klause",
    startPlace = "the Hair of the Dog tavern, Canifis",
    startLocation = VANSTROM_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 25),
            quests = listOf("Nature Spirit", "Priest in Peril", "The Restless Ghost"),
            boostable = setOf(Skills.AGILITY),
            extra = listOf("Must be able to defeat a level 97 Skeleton Hellhound (it can be safespotted)."),
        )

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.SILVER_SICKLE,
            xp = listOf(Skills.ATTACK to 600.0, Skills.DEFENCE to 600.0, Skills.STRENGTH to 600.0, Skills.CONSTITUTION to 600.0, Skills.CRAFTING to 600.0),
            lines = listOf("2 Quest Points", "600 Attack, Defence, Strength, Hitpoints and Crafting XP", "Access to the Myreque hideout"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "I know of a band of rebels, the Myreque, who hide",
            "beneath Canifis. Would you help me find them?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll find the Myreque.", "Not right now.", title = "Start In Search of the Myreque?") != 1) return false
        chatPlayer("I'll find them.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Cyreg Paddlehorn ferries travellers over the Mort Myre swamp. Speak to him.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.CYREG_PADDLEHORN,
                journal = listOf("I should speak to ${red("Cyreg Paddlehorn")} at the Mort Myre swamp boat."),
                done = listOf("Cyreg pointed me to Curpile Fyod."),
                place = "the Mort Myre swamp",
                location = CYREG_POST,
                talk = {
                    chatNpc("The Myreque? Ask Curpile Fyod, at the swamp's edge.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.CURPILE_FYOD,
                journal = listOf("I should speak to ${red("Curpile Fyod")} at the entrance of the hideout."),
                done = listOf("Curpile Fyod showed me the way into the hideout."),
                place = "the Mort Myre swamp",
                location = CURPILE_POST,
                talk = {
                    chatNpc("You look trustworthy. The hideout is below the tavern in", "Canifis: ask for Veliaf Hurtz.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.VELIAF_HURTZ,
                journal = listOf("I should speak to ${red("Veliaf Hurtz")} in the Myreque hideout."),
                done = listOf("Veliaf asked me to deal with a skeletal beast; Vanstrom betrayed us."),
                place = "the Myreque hideout",
                location = HIDEOUT_VELIAF,
                talk = {
                    chatNpc("Vanstrom is no friend of ours: a Skeleton Hellhound", "guards the tunnel. Slay it.", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SKELETON_HELLHOUND,
                journal = listOf("I must defeat the ${red("Skeleton Hellhound")} (level 97) in the tunnel."),
                done = listOf("I defeated the Skeleton Hellhound."),
                place = "the tunnel below the hideout",
                location = HELLHOUND_FIGHT,
                tip = "The Skeleton Hellhound is level 97 and can be safespotted.",
                fight = QuestFight(Npcs.SKELETON_HELLHOUND, "Skeleton Hellhound", 97, HELLHOUND_FIGHT, "GRRRR!"),
                talk = {
                    chatNpc("The hellhound guards the tunnel.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.VELIAF_HURTZ,
                journal = listOf("I should tell ${red("Veliaf Hurtz")} the hellhound is dead."),
                done = listOf("The Myreque welcomed me."),
                place = "the Myreque hideout",
                location = HIDEOUT_VELIAF,
                talk = {
                    chatNpc("The hound is dead - you have earned the Myreque's trust.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

object InAidOfTheMyreque : ShortQuest(
    name = "In Aid of the Myreque",
    startPoint = "Speak to Veliaf Hurtz in the Myreque hideout below the Canifis pub.",
    rewards = "2 Quest Points, 2,000 Attack, Defence, Strength and Crafting XP, the Gadderhammer, the Rod of Ivandis.",
    pointReward = 2,
    questId = Varbits.IN_AID_OF_THE_MYREQUE_PROGRESS,
    slot = 64,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.VELIAF_HURTZ,
    startNpcName = "Veliaf Hurtz",
    startPlace = "the Myreque hideout",
    startLocation = HIDEOUT_VELIAF,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 25, Skills.CRAFTING to 25, Skills.MINING to 15, Skills.MAGIC to 7),
            quests = listOf("In Search of the Myreque"),
            boostable = setOf(Skills.AGILITY, Skills.MAGIC),
        )

    override val recommendation = Recommendation.combat(70)

    override val reward =
        QuestReward(
            icon = Items.GADDERHAMMER,
            xp = listOf(Skills.ATTACK to 2_000.0, Skills.STRENGTH to 2_000.0, Skills.CRAFTING to 2_000.0, Skills.DEFENCE to 2_000.0),
            items = listOf(Items.GADDERHAMMER to 1),
            lines = listOf("2 Quest Points", "2,000 Attack, Strength, Crafting and Defence XP", "The Gadderhammer", "The Rod of Ivandis"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Burgh de Rott lies in ruins and vampyres rule it. Help us",
            "retake it and the Myreque may have a new home.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll help.", "Not right now.", title = "Start In Aid of the Myreque?") != 1) return false
        chatPlayer("I'll help retake Burgh de Rott.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Speak to Florin at the gate of Burgh de Rott.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.FLORIN,
                journal = listOf("I should speak to ${red("Florin")} at the gate of Burgh de Rott."),
                done = listOf("Florin sent me to Razvan."),
                place = "Burgh de Rott",
                location = FLORIN_POST,
                talk = {
                    chatNpc("The gate is held for now. Razvan inside needs help", "with the village.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.RAZVAN,
                journal = listOf("I should speak to ${red("Razvan")} in Burgh de Rott."),
                done = listOf("Razvan told me of Gadderanks, the vampyre lord."),
                place = "Burgh de Rott",
                location = RAZVAN_POST,
                talk = {
                    chatNpc("The shop, the bank and the furnace will work again", "once Gadderanks is gone.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GADDERANKS_3518,
                journal = listOf("I must defeat ${red("Gadderanks")} (level 35) and his two ${red("Vampyre Juvinates")}", "(level 54) in the inn cellar."),
                done = listOf("I defeated Gadderanks and took the Gadderhammer."),
                place = "the Burgh de Rott inn cellar",
                location = GADDERANKS_FIGHT,
                tip = "Gadderanks and the vampyres come one after the other.",
                gives = listOf(Items.GADDERHAMMER to 1),
                fight =
                    QuestFight(
                        Npcs.GADDERANKS_3518, "Gadderanks", 35, GADDERANKS_FIGHT, "Fresh blood!",
                        chain = listOf(Npcs.VAMPYRE_JUVINATE_3521 to "More blood!", Npcs.VAMPYRE_JUVINATE_3521 to "Blood!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("Gadderanks waits in the cellar of the inn.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.VELIAF_HURTZ,
                journal = listOf("I should tell ${red("Veliaf Hurtz")} in the Burgh de Rott inn cellar."),
                done = listOf("Veliaf sent me to Drezel."),
                place = "the Burgh de Rott inn cellar",
                location = BURGH_VELIAF,
                talk = {
                    chatNpc("Burgh de Rott is ours again! Drezel in Paterdomus can", "make a rod against the vampyres.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DREZEL_1049,
                journal = listOf("I should speak to ${red("Drezel")} in the temple of Paterdomus."),
                done = listOf("Drezel gave me the Rod of Ivandis."),
                place = "Paterdomus",
                location = DREZEL_POST,
                gives = listOf(Items.ROD_OF_IVANDIS_10 to 1),
                talk = {
                    chatNpc("A rod against the vampyres - the Rod of Ivandis! Take it.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.ROD_OF_IVANDIS_10, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.IVAN_STROM,
                journal = listOf("I should lead ${red("Ivan Strom")} to safety: speak to him in Burgh de Rott and", "defeat the vampyre that stops him."),
                done = listOf("Ivan Strom reached safety."),
                place = "Burgh de Rott",
                location = IVAN_POST,
                tip = "The Vampyre Juvinates are level 75.",
                fight =
                    QuestFight(
                        Npcs.VAMPYRE_JUVINATE_3537, "Vampyre Juvinate", 75, JUVINATE_FIGHT, "You will not leave!",
                        chain = listOf(Npcs.VAMPYRE_JUVINATE_3537 to "Blood!"),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc("Vampyres stand between me and the way out. Help me!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
        )
}

object DarknessOfHallowvale : ShortQuest(
    name = "Darkness of Hallowvale",
    startPoint = "Speak to Veliaf Hurtz in the basement of the Burgh de Rott inn.",
    rewards = "2 Quest Points, 7,000 Agility XP, 6,000 Thieving XP, 2,000 Construction XP, a Tome of experience (3 x 2,000 XP).",
    pointReward = 2,
    questId = Varbits.DARKNESS_OF_HALLOWVALE_PROGRESS,
    slot = 27,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.VELIAF_HURTZ,
    startNpcName = "Veliaf Hurtz",
    startPlace = "the Burgh de Rott inn basement",
    startLocation = BURGH_VELIAF,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CONSTRUCTION to 5, Skills.MINING to 20, Skills.THIEVING to 22, Skills.AGILITY to 26, Skills.CRAFTING to 32, Skills.MAGIC to 33, Skills.STRENGTH to 40),
            quests = listOf("In Aid of the Myreque"),
            boostable = setOf(Skills.CONSTRUCTION, Skills.AGILITY, Skills.MAGIC),
            extra = listOf("Must be able to withstand several melee attacks from a level 169 enemy."),
        )

    override val recommendation = Recommendation.combat(75)

    override val reward =
        QuestReward(
            icon = Items.SEALED_MESSAGE,
            xp = listOf(Skills.AGILITY to 7_000.0, Skills.THIEVING to 6_000.0, Skills.CONSTRUCTION to 2_000.0),
            lamps =
                List(3) { QuestLamp(2_000, FoundationRewards.ALL_SKILLS, 30, "Tome of experience") },
            lines = listOf("2 Quest Points", "7,000 Agility XP", "6,000 Thieving XP", "2,000 Construction XP", "A Tome of experience (3 x 2,000 XP)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Vanstrom Klause rules Meiyerditch, the city of the vampyres.",
            "We must learn how Castle Drakan is built. Will you scout it?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll scout Castle Drakan.", "Not right now.", title = "Start Darkness of Hallowvale?") != 1) return false
        chatPlayer("I'll scout it.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Old Man Ral can ferry you to Meiyerditch.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.OLD_MAN_RAL,
                journal = listOf("I should speak to ${red("Old Man Ral")} about the passage to Meiyerditch."),
                done = listOf("Ral told me where the Myreque base lies."),
                place = "Meiyerditch",
                location = RAL_POST,
                talk = {
                    chatNpc("Meiyerditch is a grim place. Safalaan holds the", "Myreque base in Darkmeyer.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should speak to ${red("Safalaan")} in the Myreque base."),
                done = listOf("Safalaan asked me to draw Castle Drakan."),
                place = "the Myreque base, Darkmeyer",
                location = SAFALAAN_POST,
                talk = {
                    chatNpc("We need a sketch of Castle Drakan. Go to the lookout", "and draw it - and beware of Vanstrom.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should go to the ${red("lookout")} near Castle Drakan and draw the castle."),
                done = listOf("I drew Castle Drakan; Vanstrom struck at me five times and I escaped."),
                place = "the lookout near Castle Drakan",
                location = LOOKOUT,
                tip = "Vanstrom Klause attacks five times in the real quest: here the sketch is made when you arrive.",
                search = QuestSearch(LOOKOUT, "You draw Castle Drakan. Vanstrom Klause appears and strikes - five times - but you slip away.", 3),
                talk = {
                    chatNpc("Go to the lookout near Castle Drakan.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SARIUS_GUILE,
                journal = listOf("I should speak to ${red("Sarius Guile")} in the laboratory."),
                done = listOf("Sarius gave me a Haemalchemy book."),
                place = "the Myreque laboratory",
                location = SARIUS_POST,
                gives = listOf(Items.HAEMALCHEMY to 1),
                talk = {
                    chatNpc("A sketch of the castle? Take my Haemalchemy book to", "Safalaan: it explains the vampyres.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.HAEMALCHEMY, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SAFALAAN,
                journal = listOf("I should give ${red("Safalaan")} the Haemalchemy book."),
                done = listOf("Safalaan gave me a sealed message for King Roald."),
                place = "the Myreque base, Darkmeyer",
                location = SAFALAAN_POST,
                gives = listOf(Items.SEALED_MESSAGE to 1),
                talk = {
                    if (!player.inventory.contains(Items.HAEMALCHEMY)) {
                        chatNpc("Bring me Sarius's book first.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.HAEMALCHEMY)
                        chatNpc("Excellent. Take this sealed message to King Roald in", "Varrock.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.SEALED_MESSAGE, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.KING_ROALD,
                journal = listOf("I should give the ${red("sealed message")} to ${red("King Roald")} in Varrock."),
                done = listOf("King Roald read the message: the Myreque have an ally."),
                place = "Varrock Palace",
                location = ROALD_POST,
                talk = {
                    if (!player.inventory.contains(Items.SEALED_MESSAGE)) {
                        chatNpc("You have no message for me.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.SEALED_MESSAGE)
                        chatNpc("A message from the Myreque! Varrock will not forget this.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
