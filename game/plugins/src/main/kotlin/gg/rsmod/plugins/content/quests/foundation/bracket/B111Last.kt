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
import gg.rsmod.plugins.content.quests.foundation.QuestLamp
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestSearch
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q39 A Tail of Two Cats + Q40 Devious Minds (bracket 111+). OSRS Wiki:
//  - A Tail of Two Cats: Icthlarin's Little Helper (and its Gertrude's Cat); 2 QP, a Doctor's hat, two 2,500 XP lamps (skills of level 30+).
//    Not built: the Mouse toy (no item), the Dragon Slayer II link.
//  - Devious Minds: 65 Smithing, 50 Runecraft, 50 Fletching (Smithing/Fletching boostable); Wanted!, Troll Stronghold, Doric's Quest (and
//    their chains); 1 QP, 5,000 Fletching, 5,000 Runecraft, 6,500 Smithing XP. The Bow-sword and the Illuminated orb do not exist as items
//    on this server: those steps are dialogue only (the Mithril 2h sword and the Bow string ARE taken). Entrana's High Priest is the monk of
//    the church. All tiles that are not a donor spawn are estimates (dossier BRACKET-QUESTS).

private val UNFERTH_POST = Tile(2920, 3559, 0)
private val HILD_POST = Tile(2931, 3567, 0)
private val BOB_POST = Tile(2917, 3561, 0)
private val SPHINX_POST = Tile(3298, 2787, 0)
private val APOTHECARY_POST = Tile(3195, 3404, 0)

object ATailOfTwoCats : ShortQuest(
    name = "A Tail of Two Cats",
    startPoint = "Speak to Unferth in his house in Burthorpe.",
    rewards = "2 Quest Points, a Doctor's hat, two lamps of 2,500 XP in a skill of level 30 or higher.",
    pointReward = 2,
    questId = Varbits.A_TAIL_OF_TWO_CATS_PROGRESS,
    slot = 104,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.UNFERTH,
    startNpcName = "Unferth",
    startPlace = "Burthorpe",
    startLocation = UNFERTH_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs = QuestRequirements(quests = listOf("Icthlarin's Little Helper", "Gertrude's Cat"))

    override val recommendation = Recommendation.combat(30)

    override val reward =
        QuestReward(
            icon = Items.DOCTORS_HAT,
            lamps = List(2) { QuestLamp(2_500, FoundationRewards.ALL_SKILLS, 30, "A Tail of Two Cats") },
            items = listOf(Items.DOCTORS_HAT to 1),
            lines = listOf("2 Quest Points", "A Doctor's hat", "Two lamps of 2,500 XP (skills of level 30+)"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "My cat, Bob, has not been the same",
            "since he left. Could you find out what",
            "troubles him? I fear for his mind.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll look into it.", "Not right now.", title = "Start A Tail of Two Cats?") != 1) return false
        chatPlayer("I'll find out what is wrong.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Hild, over there, can enchant", "a Catspeak amulet for you.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.HILD,
                journal = listOf("I should speak to ${red("Hild")} in Burthorpe about my Catspeak amulet."),
                done = listOf("Hild told me the amulet will let me talk to Bob."),
                place = "Burthorpe",
                location = HILD_POST,
                talk = {
                    chatNpc("A Catspeak amulet? It will", "do: now you can hear Bob.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BOB_2635,
                journal = listOf("I should speak to ${red("Bob")} the cat."),
                done = listOf("Bob told me his memories of Robert the Strong."),
                place = "Burthorpe",
                location = BOB_POST,
                talk = {
                    chatNpc("Meow... I remember Robert the Strong, who", "fought the Dragonkin. The Sphinx knows more.", facialExpression = FacialExpression.SAD)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SPHINX,
                journal = listOf("I should speak to the ${red("Sphinx")} in Sophanem about Bob's memories."),
                done = listOf("The Sphinx explained Bob's memories."),
                place = "Sophanem",
                location = SPHINX_POST,
                talk = {
                    chatNpc("Bob's memories are from before: he is a cat who", "once was a hero's companion. He is not ill.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.APOTHECARY,
                journal = listOf("I should tell the ${red("Apothecary")} in Varrock what I learned."),
                done = listOf("The Apothecary agreed: Unferth is not ill."),
                place = "Varrock",
                location = APOTHECARY_POST,
                talk = {
                    chatNpc("So Unferth is not ill, just sad? Then tell him", "his cat is well. Here, take a hat as thanks.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.UNFERTH,
                journal = listOf("I should tell ${red("Unferth")} that Bob is well."),
                done = listOf("Unferth rewarded me."),
                place = "Burthorpe",
                location = UNFERTH_POST,
                talk = {
                    chatNpc("Bob is well! Thank you - here are two", "lamps and a hat for your trouble.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}

private val MONK_POST = Tile(3425, 3485, 0)
private val DORIC_POST = Tile(2953, 3450, 0)
private val ENTRANA_ALTAR = Tile(2852, 3352, 0)
private val PRIEST_POST = Tile(2851, 3349, 0)
private val TIFFY_POST = Tile(2996, 3372, 0)

object DeviousMinds : ShortQuest(
    name = "Devious Minds",
    startPoint = "Speak to the monk outside the Paterdomus temple, at the northern end of the River Salve.",
    rewards = "1 Quest Point, 5,000 Fletching XP, 5,000 Runecrafting XP, 6,500 Smithing XP.",
    pointReward = 1,
    questId = Varbits.DEVIOUS_MINDS_PROGRESS,
    slot = 31,
    completedValue = 6, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.MONK_803,
    startNpcName = "Monk",
    startPlace = "outside the Paterdomus temple",
    startLocation = MONK_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.SMITHING to 65, Skills.RUNECRAFTING to 50, Skills.FLETCHING to 50),
            quests = listOf("Wanted!", "Troll Stronghold", "Doric's Quest"),
            boostable = setOf(Skills.SMITHING, Skills.FLETCHING),
        )

    override val recommendation = Recommendation.combat(45)

    override val reward =
        QuestReward(
            icon = Items.MITHRIL_2H_SWORD,
            xp = listOf(Skills.FLETCHING to 5_000.0, Skills.RUNECRAFTING to 5_000.0, Skills.SMITHING to 6_500.0),
            lines = listOf("1 Quest Point", "5,000 Fletching XP", "5,000 Runecrafting XP", "6,500 Smithing XP"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "A strange scheme: I need a sword that is",
            "also a bow. A sword from the finest",
            "mithril and a bowstring - bring them to",
            "Doric, who will make it. Will you do this?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll help.", "Not right now.", title = "Start Devious Minds?") != 1) return false
        chatPlayer("I'll bring a mithril 2h sword", "and a bow string to Doric.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.DORIC,
                journal = listOf("I should bring a ${red("mithril 2h sword")} and a ${red("bow string")} to ${red("Doric")}."),
                done = listOf("Doric made the bow-sword."),
                place = "Doric's house, north of Falador",
                location = DORIC_POST,
                talk = {
                    val p = player
                    if (!p.inventory.contains(Items.MITHRIL_2H_SWORD) || !p.inventory.contains(Items.BOW_STRING)) {
                        chatNpc("I need a mithril 2h sword and a bow string.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        p.inventory.remove(Items.MITHRIL_2H_SWORD)
                        p.inventory.remove(Items.BOW_STRING)
                        chatNpc("Hm. A bow-sword. Odd, but", "done! Take it to the monk.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.MONK_803,
                journal = listOf("I should take the bow-sword back to the ${red("monk")} outside Paterdomus."),
                done = listOf("The monk gave me an illuminated orb in a large pouch."),
                place = "outside the Paterdomus temple",
                location = MONK_POST,
                talk = {
                    chatNpc("A bow-sword! Now take this orb, in a large pouch,", "to the altar on Entrana, and do not look at it.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = -1,
                journal = listOf("I should place the ${red("orb")} on the altar of the church on Entrana."),
                done = listOf("The orb was placed on the altar and the relic was stolen."),
                place = "the church on Entrana",
                location = ENTRANA_ALTAR,
                search = QuestSearch(ENTRANA_ALTAR, "You place the orb on the altar. A flash of light - the relic that lay there is gone!", 3),
            ),
            QuestStep(
                npc = Npcs.HIGH_PRIEST,
                journal = listOf("I should tell the ${red("High Priest")} on Entrana what happened."),
                done = listOf("The High Priest told me to go to Sir Tiffy Cashien."),
                place = "the church on Entrana",
                location = PRIEST_POST,
                talk = {
                    chatNpc("The relic is stolen! Tell Sir Tiffy Cashien of the", "White Knights: he must hear of this.", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SIR_TIFFY_CASHIEN,
                journal = listOf("I should tell ${red("Sir Tiffy Cashien")} in Falador Park."),
                done = listOf("Sir Tiffy Cashien thanked me."),
                place = "Falador Park",
                location = TIFFY_POST,
                talk = {
                    chatNpc("A theft on Entrana? The White Knights will look", "into it. Thank you for the warning, adventurer.", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
        )
}
