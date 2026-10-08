package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.quests.QuestStage
import gg.rsmod.plugins.content.quests.getCurrentStage
import gg.rsmod.plugins.content.quests.red
import gg.rsmod.plugins.content.quests.striked
import gg.rsmod.plugins.content.mechanics.pvp.DangerWarning
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards

/*
 * The eight short playable quests of the permanent quest choices (owner 2026-09-26) and the ten OSRS quests the
 * revision-667 quest list did not have. Rewards are the quests' own (OSRS Wiki / RuneScape Wiki "Rewards", fetched
 * 2026-09-26): quest points, experience and the unlock. Stage values of the four 667 quests are the cache's "complete"
 * values (clientscript 2193: Desert Treasure varbit 358 = 15, Lunar Diplomacy varbit 2448 = 190, King's Ransom varbit
 * 3888 = 90, The Temple at Senntisten varbit 6775 = 90); the OSRS quests use the free varps 1910-1919 (no varbit, cache
 * clientscript or server code uses them) and are shown by the quest-list entries QuestListCacheTool added (slots 185-194).
 */

/** Varbit 3909 (Void quest.varbits "knights_waves"; the old Prayers.kt gate compared it with 8): the eight waves won. */
const val KNIGHT_WAVES_VARBIT = 3909
const val KNIGHT_WAVES_COMPLETE = 8

private val GE_HALL = Tile(3164, 3491, 0)
private val BANDIT_CAMP = Tile(3183, 2982, 0)
private val RELLEKKA_DOCK = Tile(2621, 3685, 0)
private val NARDAH = Tile(3419, 2936, 0)
private val SINCLAIR_MANSION = Tile(2741, 3553, 0)
private val CAMELOT = Tile(2758, 3504, 0)
private val EDGEVILLE = Tile(3068, 3513, 0)
private val DRAYNOR = Tile(3097, 3253, 0)
private val CRANDOR = Tile(2851, 3238, 0)
private val EAST_ARDOUGNE = Tile(2568, 3331, 0)
private val GLIDER_HANGAR = Tile(2648, 4517, 0)
private val CRASH_ISLAND = Tile(2894, 2726, 0)

// Desert Treasure: the four warriors' posts (2009scape DiamondOf*Listeners spawns; OSRS Wiki quick guide locations).
private val DESSOUS_GRAVE = Tile(3570, 3405, 0)
private val KAMIL_PATH = Tile(2857, 3754, 0)
private val DAMIS_LAIR = Tile(2739, 5088, 0)
private val FAREED_PIT = Tile(3315, 9376, 0)

// The other short quests' places (npc spawns of this world and Void 2011 spawn data; checked against the map by tests).
private val RELLEKKA_LONGHALL = Tile(2658, 3667, 0)
private val LUNAR_DOCK = Tile(2142, 3900, 0)
private val LUNAR_ALTAR = Tile(2151, 3868, 0)
private val DIGSITE = Tile(3361, 3341, 0)
private val GHORROCK = Tile(2978, 3872, 0)
private val BARROWS = Tile(3565, 3289, 0)
private val KNIGHT_WAVES = Tile(2758, 3508, 2)
private val VARROCK_PALACE = Tile(3220, 3471, 0)
private val WEST_ARDOUGNE = Tile(2542, 3326, 0)
private val LLETYA = Tile(2352, 3163, 0)
private val GRAND_TREE = Tile(2390, 3514, 1)
private val APE_ATOLL = Tile(2805, 2762, 0)
private val APE_ATOLL_GUARD = Tile(2795, 2775, 0)
private val MERLIN_CELL = Tile(2757, 3502, 0)

private val DIAMONDS = listOf(Items.BLOOD_DIAMOND, Items.ICE_DIAMOND, Items.SMOKE_DIAMOND, Items.SHADOW_DIAMOND)

// ------------------------------------------------------------------------------------------------ Desert Treasure

object DesertTreasure : ShortQuest(
    name = "Desert Treasure",
    startPoint = "Speak to the Archaeologist in the Grand Exchange hall.",
    rewards = "3 Quest Points, 20,000 Magic XP, the Ancient Magicks spellbook.",
    pointReward = 3,
    questId = Varbits.DESERT_TREASURE_PROGRESS,
    slot = 30,
    completedValue = 15,
    usesVarbits = true,
    startNpc = Npcs.ARCHAEOLOGIST,
    startNpcName = "the Archaeologist",
    startPlace = "the Grand Exchange hall",
    startLocation = GE_HALL,
) {
    override val reward =
        QuestReward(
            icon = Items.ANCIENT_STAFF,
            xp = listOf(Skills.MAGIC to 20_000.0),
            lines = listOf("20,000 Magic XP", "The Ancient Magicks spellbook", "You can buy and wield the ancient staff"),
            unlock = { p ->
                p.attr[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] = true
                p.attr[UnlockNpcRewards.ANCIENT_MAGIC_REWARDED] = true
            },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "I've found the tomb of a Mahjarrat under the sand -",
            "Azzanadra, sealed in by four diamonds. The four",
            "warriors who sealed him still keep them.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        chatNpc(
            "Eblis at the Bandit Camp knows where they are.",
            "Fetch the diamonds? I'd go myself, but I bruise.",
            facialExpression = FacialExpression.HAPPY,
        )
        if (options("I'll recover the diamonds.", "Not right now.", title = "Start Desert Treasure?") != 1) return false
        chatPlayer("I'll recover the diamonds for you.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.EBLIS,
                journal = listOf("I should travel to the ${red("Bandit Camp")} in the desert", "and speak to ${red("Eblis")} about the four diamonds."),
                done = listOf("Eblis told me which warrior guards each diamond."),
                place = "the Bandit Camp",
                location = BANDIT_CAMP,
                talk = {
                    chatNpc(
                        "So the digger sent you. Four warriors of Zamorak",
                        "sealed Azzanadra in: Dessous, Kamil, Damis and",
                        "Fareed. Each still guards the diamond he took.",
                        facialExpression = FacialExpression.SECRETLY_TALKING,
                    )
                    chatNpc(
                        "Dessous, the vampyre, sleeps in a grave north of",
                        "Castle Drakan. Mind your prayers - he hisses at",
                        "those who guard against his claws.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    chatNpc(
                        "Kamil walks the Ice Path above Trollheim; his ice",
                        "will freeze you where you stand. Damis hides in",
                        "the Shadow Dungeon and does not die just once.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    chatNpc(
                        "And Fareed burns in the Smoke Dungeon west of",
                        "Pollnivneach. Wear ice gloves, or fight him with",
                        "water spells. Bring me word when all four fall.",
                        facialExpression = FacialExpression.EVIL,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EBLIS,
                journal = listOf("I must defeat ${red("Dessous")} (level 139) at the graveyard", "north of Castle Drakan for the Diamond of Blood."),
                done = listOf("I destroyed Dessous and took the Diamond of Blood."),
                place = "the graveyard north of Castle Drakan",
                location = DESSOUS_GRAVE,
                gives = listOf(Items.BLOOD_DIAMOND to 1),
                tip = "Dessous hisses magic and ranged at you while you protect from melee. Bring food.",
                fight = QuestFight(Npcs.DESSOUS, "Dessous", 139, DESSOUS_GRAVE, "Who dares disturb my rest?"),
                talk = {
                    chatNpc("Dessous waits in his grave. Protect yourself well.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EBLIS,
                journal = listOf("I must defeat ${red("Kamil")} (level 154) on the Ice Path", "above Trollheim for the Diamond of Ice."),
                done = listOf("I shattered Kamil and took the Diamond of Ice."),
                place = "the Ice Path above Trollheim",
                location = KAMIL_PATH,
                gives = listOf(Items.ICE_DIAMOND to 1),
                tip = "Kamil freezes you in place. Bring food and fight from where you stand.",
                fight = QuestFight(Npcs.KAMIL, "Kamil", 154, KAMIL_PATH, "You will freeze, like the others!"),
                talk = {
                    chatNpc("Kamil walks the Ice Path. Dress warmly.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EBLIS,
                journal = listOf("I must defeat ${red("Damis")} (level 103, then 174) in the", "Shadow Dungeon for the Diamond of Shadow."),
                done = listOf("I defeated both forms of Damis and took the", "Diamond of Shadow."),
                place = "the Shadow Dungeon",
                location = DAMIS_LAIR,
                gives = listOf(Items.SHADOW_DIAMOND to 1),
                tip = "Damis rises a second time and then drains your Prayer with every hit.",
                fight =
                    QuestFight(
                        Npcs.DAMIS, "Damis", 103, DAMIS_LAIR, "The shadows will take you.",
                        chain = listOf(Npcs.DAMIS_1975 to "Now I show... you... my true power!"),
                    ),
                talk = {
                    chatNpc("Damis lurks in the Shadow Dungeon. He rises twice.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EBLIS,
                journal = listOf("I must defeat ${red("Fareed")} (level 167) in the Smoke", "Dungeon for the Diamond of Smoke. Ice gloves or water", "spells help against his heat."),
                done = listOf("I put out Fareed's flames and took the Diamond of Smoke."),
                place = "the Smoke Dungeon",
                location = FAREED_PIT,
                gives = listOf(Items.SMOKE_DIAMOND to 1),
                tip = "Wear ice gloves to keep your weapon, or use water spells - other magic does nothing.",
                fight = QuestFight(Npcs.FAREED, "Fareed", 167, FAREED_PIT, "Burn, fool!"),
                talk = {
                    chatNpc("Fareed burns in the Smoke Dungeon. Ice gloves!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.AZZANADRA,
                journal = listOf("I should bring the four diamonds to ${red("Azzanadra")}", "in the Grand Exchange hall."),
                done = listOf("I freed Azzanadra, who taught me the Ancient Magicks."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    if (!DIAMONDS.all { player.inventory.contains(it) }) {
                        chatNpc(
                            "You carry the scent of my prison but not the",
                            "stones themselves. Bring all four diamonds.",
                            facialExpression = FacialExpression.ANGRY,
                        )
                        false
                    } else {
                        DIAMONDS.forEach { player.inventory.remove(it) }
                        chatNpc(
                            "Blood, ice, smoke and shadow... the seals break.",
                            "Zaros remembers those who serve him, mortal.",
                            facialExpression = FacialExpression.CALM_TALK,
                        )
                        chatNpc(
                            "Take the magicks of the ancients as your reward.",
                            "Tell the digger his tomb is empty now.",
                            facialExpression = FacialExpression.SECRETLY_TALKING,
                        )
                        true
                    }
                },
            ),
        )
}

// ------------------------------------------------------------------------------------------------ Lunar Diplomacy

object LunarDiplomacy : ShortQuest(
    name = "Lunar Diplomacy",
    startPoint = "Speak to the Oneiromancer in the Grand Exchange hall.",
    rewards = "2 Quest Points, 5,000 Magic XP, 5,000 Runecrafting XP, the Lunar spellbook.",
    pointReward = 2,
    questId = Varbits.LUNAR_DIPLOMACY_PROGRESS,
    slot = 70,
    completedValue = 190,
    usesVarbits = true,
    startNpc = Npcs.ONEIROMANCER,
    startNpcName = "the Oneiromancer",
    startPlace = "the Grand Exchange hall",
    startLocation = GE_HALL,
) {
    override val reward =
        QuestReward(
            icon = Items.SEAL_OF_PASSAGE,
            xp = listOf(Skills.MAGIC to 5_000.0, Skills.RUNECRAFTING to 5_000.0),
            items = listOf(Items.ASTRAL_RUNE to 50),
            lines = listOf("5,000 Magic XP", "5,000 Runecrafting XP", "Access to the Lunar spellbook", "Access to Lunar Isle", "50 astral runes"),
            unlock = { p ->
                p.attr[UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED] = true
                p.attr[UnlockNpcRewards.LUNAR_MAGIC_REWARDED] = true
            },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The Fremennik and my people, the Moon Clan, have",
            "not spoken in an age. I would end that silence, but",
            "they will not listen to a Moon Clan mage.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        chatNpc(
            "Lokar Searunner sails from Rellekka's west dock. He",
            "knows the way to our isle - and a Fremennik who",
            "carries a Seal of passage may come ashore.",
            facialExpression = FacialExpression.SECRETLY_TALKING,
        )
        if (options("I'll carry your message to the Fremennik.", "Not right now.", title = "Start Lunar Diplomacy?") != 1) return false
        chatPlayer("I'll carry your message to the Fremennik.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.LOKAR_SEARUNNER,
                journal = listOf("I should speak to ${red("Lokar Searunner")} on the west", "dock of ${red("Rellekka")} about sailing to Lunar Isle."),
                done = listOf("Lokar agreed to sail me to Lunar Isle, with a Seal."),
                place = "Rellekka",
                location = RELLEKKA_DOCK,
                talk = {
                    chatNpc(
                        "Lunar Isle? Ha! I'll sail anywhere for a good",
                        "story. But the moon folk turn back any ship",
                        "without a Seal of passage.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                    chatNpc(
                        "Only our chieftain, Brundt, can give you one. You'll",
                        "find him in the longhall. Come back with the Seal.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I should ask ${red("Brundt the Chieftain")} in the Rellekka", "longhall for a ${red("Seal of passage")}."),
                done = listOf("Brundt gave me a Seal of passage."),
                place = "the Rellekka longhall",
                location = RELLEKKA_LONGHALL,
                gives = listOf(Items.SEAL_OF_PASSAGE to 1),
                talk = {
                    chatNpc(
                        "The Moon Clan wants to talk after all these years?",
                        "Then prove you are worth a Fremennik's trust.",
                        facialExpression = FacialExpression.THINKING,
                    )
                    chatNpc(
                        "Answer me this, outerlander: what does a Fremennik",
                        "prize above gold?",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    val answer = options("Honour.", "Mead.", "A fine longship.", title = "Brundt's question")
                    if (answer != 1) {
                        chatNpc("Hah! Spoken like an outerlander. Think again and come back.", facialExpression = FacialExpression.LAUGH)
                        return@QuestStep false
                    }
                    chatNpc(
                        "Honour. Good. Here - a Seal of passage. Wear it on",
                        "the isle and the moon folk will know you come in",
                        "peace, speaking for the Fremennik.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    FoundationRewards.grant(player, Items.SEAL_OF_PASSAGE, 1)
                    player.message("Brundt hands you a Seal of passage.")
                    true
                },
            ),
            QuestStep(
                npc = Npcs.LOKAR_SEARUNNER,
                journal = listOf("I should show the Seal to ${red("Lokar Searunner")} on", "the Rellekka dock so he sails me to Lunar Isle."),
                done = listOf("Lokar sailed me to Lunar Isle."),
                place = "Rellekka",
                location = RELLEKKA_DOCK,
                talk = {
                    if (!hasSeal(player)) {
                        chatNpc("No Seal, no voyage. Brundt's in the longhall.", facialExpression = FacialExpression.CALM_TALK)
                        return@QuestStep false
                    }
                    chatNpc(
                        "Brundt's own Seal! Climb aboard. Mind the suqah",
                        "on the isle - hairy brutes, and they don't like",
                        "visitors any more than the moon folk do.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                    if (!DangerWarning.confirmQuestTravel(this, LUNAR_DOCK)) return@QuestStep false
                    // Owner 2026-10-07: the trip starts on "Enter" - the move first, the line in the chatbox, no click in between.
                    player.moveTo(LUNAR_DOCK)
                    player.message("Lokar sails you across the sea to Lunar Isle.")
                    wait(2)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.LOKAR_SEARUNNER,
                journal = listOf("I must defeat the ${red("suqah")} (level 111) that guards the", "path from the Lunar Isle dock."),
                done = listOf("I killed the suqah on the path from the dock."),
                place = "Lunar Isle",
                location = LUNAR_DOCK,
                tip = "Bring food and a weapon: suqahs hit with melee and magic.",
                fight = QuestFight(Npcs.SUQAH, "suqah", 111, LUNAR_DOCK, "Grraaah!"),
                talk = {
                    chatNpc("The suqah is still on the path. Go on!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ONEIROMANCER,
                journal = listOf("I should bring the Seal of passage to the", "${red("Oneiromancer")} in the Grand Exchange hall."),
                done = listOf("The Oneiromancer sent me into the dream world."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    if (!hasSeal(player)) {
                        chatNpc(
                            "Without the Seal the Fremennik will not believe",
                            "you speak for them. Bring it to me.",
                            facialExpression = FacialExpression.UPSET,
                        )
                        return@QuestStep false
                    }
                    chatNpc(
                        "The Seal... then the Fremennik have answered. But",
                        "a message between our peoples must be spoken in",
                        "the dream world, where no lie survives.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    chatNpc(
                        "Sleep by the altar on Lunar Isle. In the dream you",
                        "will meet the one who doubts you most - yourself.",
                        "You cannot change your equipment there.",
                        facialExpression = FacialExpression.EYES_CLOSED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ONEIROMANCER,
                journal = listOf("I must defeat ${red("Me")} (level 79) in the dream by the", "Lunar Isle altar. I cannot change equipment there."),
                done = listOf("I defeated my own doubt in the dream world."),
                place = "the Lunar Isle altar",
                location = LUNAR_ALTAR,
                tip = "You cannot change equipment in the dream: wear your gear before you go.",
                fight = QuestFight(Npcs.ME, "Me", 79, LUNAR_ALTAR, "You can't beat me. I know all your moves."),
                talk = {
                    chatNpc("Go to the altar on Lunar Isle and dream.", facialExpression = FacialExpression.EYES_CLOSED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ONEIROMANCER,
                journal = listOf("I should return to the ${red("Oneiromancer")} in the Grand", "Exchange hall."),
                done = listOf("The Oneiromancer made peace between our peoples", "and taught me the Lunar spells."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "You faced yourself and won. The moon listened, and",
                        "so did the Fremennik. Peace, after an age.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    messageBox("The Oneiromancer teaches you the spells of the Moon Clan.")
                    chatNpc(
                        "Welcome, friend of the Moon Clan. Keep the Seal;",
                        "Lunar Isle is open to you now.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    true
                },
            ),
        )

    private fun hasSeal(player: gg.rsmod.game.model.entity.Player): Boolean =
        player.inventory.contains(Items.SEAL_OF_PASSAGE) || player.equipment.contains(Items.SEAL_OF_PASSAGE)
}

// ------------------------------------------------------------------------------------ The Temple at Senntisten

object TempleAtSenntisten : ShortQuest(
    name = "The Temple at Senntisten",
    startPoint = "Speak to Azzanadra in the Grand Exchange hall.",
    rewards = "2 Quest Points, 10,000 Prayer XP, experience lamps, the Ancient Curses.",
    pointReward = 2,
    questId = Varbits.THE_TEMPLE_AT_SENNTISTEN_PROGRESS,
    slot = 157,
    completedValue = 90,
    usesVarbits = true,
    startNpc = Npcs.AZZANADRA,
    startNpcName = "Azzanadra",
    startPlace = "the Grand Exchange hall",
    startLocation = GE_HALL,
) {
    override val reward =
        QuestReward(
            icon = Items.ANCIENT_HYMNAL,
            xp = listOf(Skills.PRAYER to 10_000.0),
            lamps =
                listOf(
                    QuestLamp(23_000, FoundationRewards.ALL_SKILLS, 1, "23,000 XP lamp (any skill)"),
                    QuestLamp(20_000, FoundationRewards.COMBAT_SKILLS, 1, "20,000 XP combat lamp"),
                    QuestLamp(20_000, FoundationRewards.COMBAT_SKILLS, 1, "20,000 XP combat lamp"),
                ),
            items = listOf(Items.ANCIENT_HYMNAL to 1),
            lines =
                listOf(
                    "10,000 Prayer XP",
                    "A 23,000 XP lamp (any skill)",
                    "Two 20,000 XP combat lamps",
                    "The Ancient Curses prayer book",
                ),
            unlock = { p ->
                p.attr[AncientCurses.NPC_UNLOCKED_ATTR] = true
                p.attr[AncientCurses.UNLOCKED_ATTR] = true
                p.attr[UnlockNpcRewards.ANCIENT_CURSES_REWARDED] = true
            },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "The temple of Zaros at Senntisten lies beneath the",
            "Digsite, defiled by Saradominist hands. I mean to",
            "rededicate it - and for that I need two relics.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        chatNpc(
            "The Frostenhorn, lost in the ice of Ghorrock, and the",
            "icon the Barrows brothers guard. Ali the Wise of",
            "Nardah has kept my secrets before. Start with him.",
            facialExpression = FacialExpression.SECRETLY_TALKING,
        )
        if (options("I'll help you restore the temple.", "Not right now.", title = "Start The Temple at Senntisten?") != 1) return false
        chatPlayer("I'll help you restore the temple.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ALI_THE_WISE,
                journal = listOf("I should speak to ${red("Ali the Wise")} in ${red("Nardah")}", "about the temple."),
                done = listOf("Ali the Wise sent me to Dr Nabanik at the Digsite."),
                place = "Nardah",
                location = NARDAH,
                talk = {
                    chatNpc(
                        "Azzanadra sends a messenger now? He never did",
                        "learn patience. The temple lies under the Digsite,",
                        "and the Exam Centre guards every stone of it.",
                        facialExpression = FacialExpression.THINKING,
                    )
                    chatNpc(
                        "Speak to Dr Nabanik at the Exam Centre. He will",
                        "let you dig where others may not. Trust him - more",
                        "than you would trust his face.",
                        facialExpression = FacialExpression.SECRETLY_TALKING,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DR_NABANIK,
                journal = listOf("I should speak to ${red("Dr Nabanik")} at the ${red("Digsite Exam Centre")}."),
                done = listOf("Dr Nabanik - Azzanadra in disguise - explained the relics."),
                place = "the Digsite Exam Centre",
                location = DIGSITE,
                talk = {
                    chatNpc(
                        "You found me. Yes - Nabanik is a mask I wear among",
                        "these scholars. I am Azzanadra, and the temple is",
                        "beneath our feet.",
                        facialExpression = FacialExpression.SECRETLY_TALKING,
                    )
                    chatNpc(
                        "First the Frostenhorn. It lies in Ghorrock, deep in",
                        "the frozen north of the Wilderness, and an ice",
                        "demon keeps it. Water will not touch that beast.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DR_NABANIK,
                journal = listOf("I must defeat the ${red("Ice demon")} (level 177) in ${red("Ghorrock")},", "deep in the Wilderness, for the Frostenhorn.", "Water spells do not harm it."),
                done = listOf("I shattered the ice demon and took the Frostenhorn."),
                place = "Ghorrock, in the Wilderness",
                location = GHORROCK,
                tip = "Ghorrock is deep in the Wilderness. Water spells do not harm the ice demon.",
                fight = QuestFight(Npcs.ICE_DEMON, "Ice demon", 177, GHORROCK, "Cold... so cold... you will join me!"),
                talk = {
                    chatNpc("The Frostenhorn waits in Ghorrock. Beware the Wilderness.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DR_NABANIK,
                journal = listOf("I must defeat ${red("Dharok the Wretched")} (level 115) at the", "${red("Barrows")} to claim the Barrows icon."),
                done = listOf("I defeated Dharok and took the Barrows icon."),
                place = "the Barrows",
                location = BARROWS,
                tip = "Dharok hits harder the lower his life points are. Protect from Melee helps.",
                fight = QuestFight(Npcs.DHAROK_THE_WRETCHED_14264, "Dharok the Wretched", 115, BARROWS, "You dare disturb my rest?"),
                talk = {
                    chatNpc(
                        "The Frostenhorn! Now the icon. The Barrows brothers",
                        "will not part with it - Dharok least of all. His axe",
                        "hits harder the closer he is to death.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.AZZANADRA,
                journal = listOf("I won the Frostenhorn and the Barrows icon. I should", "tell ${red("Azzanadra")} in the Grand Exchange hall."),
                done = listOf("Azzanadra rededicated the temple and taught me the", "Ancient Curses."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "The Frostenhorn and the icon, both. The relics are",
                        "placed and the temple breathes again. Kneel, and",
                        "hear the old words.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    messageBox("The temple shakes as Zarosian power pours into it. Azzanadra teaches you the curses of Zaros.")
                    chatNpc(
                        "These are not prayers. They are demands. The",
                        "Ancient hymnal will remind you of them.",
                        facialExpression = FacialExpression.SECRETLY_TALKING,
                    )
                    true
                },
            ),
        )
}

// ------------------------------------------------------------------------------------------------- King's Ransom

object KingsRansom : ShortQuest(
    name = "King's Ransom",
    startPoint = "Speak to the Gossip outside Sinclair Mansion.",
    rewards = "1 Quest Point, experience lamps, the Knight Waves Training Ground (Chivalry and Piety).",
    pointReward = 1,
    questId = Varbits.KINGS_RANSOM_PROGRESS,
    slot = 126,
    completedValue = 90,
    usesVarbits = true,
    startNpc = Npcs.GOSSIP,
    startNpcName = "the Gossip",
    startPlace = "Sinclair Mansion, north of Seers' Village",
    startLocation = SINCLAIR_MANSION,
) {
    override val reward =
        QuestReward(
            icon = Items.ANTIQUE_LAMP,
            lamps =
                listOf(
                    QuestLamp(5_000, listOf(Skills.MAGIC), 1, "5,000 Magic XP lamp"),
                    QuestLamp(5_000, FoundationRewards.ALL_SKILLS, 1, "5,000 XP antique lamp (any skill)"),
                    QuestLamp(33_000, listOf(Skills.DEFENCE), 1, "33,000 Defence XP lamp"),
                ),
            lines =
                listOf(
                    "A 5,000 Magic XP lamp",
                    "A 5,000 XP antique lamp",
                    "A 33,000 Defence XP lamp",
                    "Knight Waves done: Chivalry and Piety",
                ),
            unlock = { p -> p.setVarbit(KNIGHT_WAVES_VARBIT, KNIGHT_WAVES_COMPLETE) },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Have you heard? The Sinclairs are all locked up",
            "for murder - and King Arthur's gone missing too!",
            "Somebody ought to look into it. Not me, mind.",
            facialExpression = FacialExpression.TALKING_ALOT,
        )
        chatNpc(
            "Anna's the one they say did it. Poor thing. Funny,",
            "though - I saw a man in black armour by the back",
            "door that night. And the butler had a wet boot.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll look into it.", "Not right now.", title = "Start King's Ransom?") != 1) return false
        chatPlayer("I'll look into it.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ANNA,
                journal = listOf("I should speak to ${red("Anna")} inside Sinclair Mansion."),
                done = listOf("Anna told me she is on trial for a murder she did", "not commit."),
                place = "Sinclair Mansion",
                location = SINCLAIR_MANSION,
                talk = {
                    chatNpc(
                        "I didn't kill anyone! They found the butler dead and",
                        "my knife beside him - but I lost that knife a week",
                        "ago. My trial is today, and nobody believes me.",
                        facialExpression = FacialExpression.DISTRESSED,
                    )
                    chatNpc(
                        "Please - speak for me. Tell them what really",
                        "happened that night.",
                        facialExpression = FacialExpression.SAD,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ANNA,
                // Night run 2026-10-08 (played as a new player): the Gossip's clue was told once only; the journal, the
                // Quest Helper and the reminder now carry it, so nobody has to remember an old line.
                journal = listOf("I should speak for ${red("Anna")} at her trial with what the ${red("Gossip")}", "saw: a man in black armour at the back door, and the", "butler had a wet boot."),
                done = listOf("I proved at the trial that the Black Knights framed", "the Sinclairs."),
                place = "Sinclair Mansion",
                location = SINCLAIR_MANSION,
                talk = {
                    messageBox("The trial begins. The judge asks what you know of the night of the murder.")
                    val who = options("A man in black armour was seen at the back door.", "Anna was seen with the knife.", "The butler fell down the stairs.", title = "Your evidence")
                    if (who != 1) {
                        messageBox("The judge is not convinced. Perhaps the Gossip saw something more useful.")
                        return@QuestStep false
                    }
                    val clue = options("The butler had a wet boot - he came in from the moat.", "Anna's knife was clean.", "The door was locked all night.", title = "And the butler?")
                    if (clue != 1) {
                        messageBox("The judge frowns. That does not explain how the killer got in.")
                        return@QuestStep false
                    }
                    messageBox("Under questioning, a guard confesses: the Black Knights paid him to frame the Sinclairs - and they hold King Arthur.")
                    chatNpc("You saved me. Now save the king - Merlin knows where he is!", facialExpression = FacialExpression.HAPPY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.MERLIN,
                journal = listOf("I should free ${red("Merlin")}, locked in a cell at ${red("Camelot")}."),
                done = listOf("I picked the lock of Merlin's cell."),
                place = "Camelot",
                location = CAMELOT,
                talk = {
                    chatNpc(
                        "At last! The Black Knights sealed my cell with four",
                        "tumblers. I heard them set it: high, low, low, high.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    val combination = listOf("High", "Low", "Low", "High")
                    for (i in combination.indices) {
                        val pick = options("High", "Low", title = "Tumbler ${i + 1}")
                        if ((if (pick == 1) "High" else "Low") != combination[i]) {
                            messageBox("The tumblers slip back. The lock stays shut.")
                            return@QuestStep false
                        }
                    }
                    messageBox("With a click, the cell door swings open.")
                    chatNpc(
                        "Thank you! King Arthur is held in the Black Knights'",
                        "fortress, turned to stone by their magic. I have",
                        "broken the spell from here - go and bring him home.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_ARTHUR,
                journal = listOf("I should speak to ${red("King Arthur")} in ${red("Camelot")}, now that", "Merlin has broken the Black Knights' spell."),
                done = listOf("King Arthur returned to Camelot and cleared the Sinclairs."),
                place = "Camelot",
                location = CAMELOT,
                talk = {
                    chatNpc(
                        "Stone, for a week! Merlin's spell brought me home,",
                        "and Anna is free. I owe you my crown, friend.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    chatNpc(
                        "Prove yourself against my knights in the Knight",
                        "Waves Training Ground, and you shall learn the",
                        "prayers of the Round Table. Speak to Sir Lancelot.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SIR_LANCELOT,
                journal = listOf("I must defeat all eight knights of the ${red("Knight Waves")}", "${red("Training Ground")} in Camelot, one after another,", "from Sir Bedivere (level 110) to Sir Lancelot (level 127)."),
                done = listOf("I defeated all eight Knight Waves."),
                place = "the Knight Waves Training Ground",
                location = KNIGHT_WAVES,
                tip = "Eight knights in a row, no rest between them. Bring plenty of food.",
                fight =
                    QuestFight(
                        Npcs.SIR_BEDIVERE_6177, "Sir Bedivere", 110, KNIGHT_WAVES, "Wave one! Have at you!",
                        chain =
                            listOf(
                                Npcs.SIR_PELLEAS_6176 to "Wave two: Sir Pelleas!",
                                Npcs.SIR_TRISTRAM_6175 to "Wave three: Sir Tristram!",
                                Npcs.SIR_PALOMEDES_1883 to "Wave four: Sir Palomedes!",
                                Npcs.SIR_LUCAN_6173 to "Wave five: Sir Lucan!",
                                Npcs.SIR_GAWAIN_6172 to "Wave six: Sir Gawain!",
                                Npcs.SIR_KAY_6171 to "Wave seven: Sir Kay!",
                                Npcs.SIR_LANCELOT_6170 to "The last wave. Face me, if you dare!",
                            ),
                        chainAtTile = true,
                    ),
                talk = {
                    chatNpc(
                        "Eight knights, one after another. Keep your",
                        "guard up and your head down.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SIR_LANCELOT,
                journal = listOf("I should speak to ${red("Sir Lancelot")} in Camelot."),
                done = listOf("Sir Lancelot taught me the prayers of the Round Table."),
                place = "Camelot",
                location = CAMELOT,
                talk = {
                    chatNpc(
                        "Well fought! The prayers of Chivalry and Piety",
                        "are yours - if your faith and defence are strong.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    true
                },
            ),
        )
}

// -------------------------------------------------------------------------------------------- Desert Treasure II

object DesertTreasureII : ShortQuest(
    name = "Desert Treasure II - The Fallen Empire",
    startPoint = "Speak to the Archaeologist in the Grand Exchange hall.",
    rewards = "5 Quest Points, three 100,000 XP ancient lamps, ancient rings, ancient sceptres and the Ring of shadows.",
    pointReward = 5,
    questId = OsrsQuestVarps.DESERT_TREASURE_II,
    slot = OsrsQuestSlots.DESERT_TREASURE_II,
    completedValue = 10,
    usesVarbits = false,
    startNpc = Npcs.ARCHAEOLOGIST,
    startNpcName = "the Archaeologist",
    startPlace = "the Grand Exchange hall",
    startLocation = GE_HALL,
) {
    override val reward =
        QuestReward(
            icon = Items.RING_OF_SHADOWS_UNCHARGED,
            lamps =
                List(3) {
                    QuestLamp(
                        100_000,
                        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC, Skills.PRAYER),
                        1,
                        "100,000 XP ancient lamp (combat or Prayer)",
                    )
                },
            items = listOf(Items.RING_OF_SHADOWS_UNCHARGED to 1),
            lines =
                listOf(
                    "Three 100,000 XP ancient lamps",
                    "The Ring of shadows",
                    "Ability to wear the ancient rings",
                    "Ability to make and wield the ancient sceptres",
                    "Access to the Scar essence mine",
                ),
            unlock = { p ->
                p.attr[UnlockNpcRewards.DESERT_TREASURE_II_REWARDED] = true
                p.attr[UnlockNpcRewards.DESERT_TREASURE_II_UNLOCKED] = true
                p.attr[UnlockNpcRewards.SCAR_ESSENCE_MINE_UNLOCKED] = true
                p.attr[UnlockNpcRewards.ANCIENT_RINGS_UNLOCKED] = true
                p.attr[UnlockNpcRewards.FORGOTTEN_FOUR_UNLOCKED] = true
                p.attr[UnlockNpcRewards.DEMONIC_BRUTUS_UNLOCKED] = true
            },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "There's a vault in the desert that wasn't there last",
            "year. Old Zarosian work, with four statues at its",
            "heart - and the statues have started to move.",
            facialExpression = FacialExpression.WORRIED,
        )
        chatNpc(
            "Ali the Wise knows the desert's secrets better than",
            "anyone. Start with him?",
            facialExpression = FacialExpression.THINKING,
        )
        if (options("I'll investigate the vault.", "Not right now.", title = "Start Desert Treasure II?") != 1) return false
        chatPlayer("I'll investigate the vault.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ALI_THE_WISE,
                journal = listOf("I should ask ${red("Ali the Wise")} in ${red("Nardah")} about", "the Ancient Vault."),
                done = listOf("Ali the Wise told me of the Forgotten Four."),
                place = "Nardah",
                location = NARDAH,
                talk = {
                    chatNpc(
                        "The vault holds the Forgotten Four, Zaros's greatest",
                        "servants, sealed in stone: Vardorvis the blade, Duke",
                        "Sucellus in his ice, the Leviathan of the deep...",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    chatNpc(
                        "...and the Whisperer, whose voice is a whisper.",
                        "Each statue answers to a seal. The vault's walls",
                        "tell the order the seals were set - read it to me.",
                        facialExpression = FacialExpression.THINKING,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ALI_THE_WISE,
                journal = listOf("I should read the ${red("vault's inscription")} to ${red("Ali the Wise")}:", "\"The blade before the ice, the ice before the deep,", "the deep before the whisper.\""),
                done = listOf("I read the order of the Forgotten Four's seals."),
                place = "Nardah",
                location = NARDAH,
                talk = {
                    messageBox("The inscription reads: \"The blade before the ice, the ice before the deep, the deep before the whisper.\"")
                    // Owner 2026-10-07 (stuck at Ali): the seals carry their clue word, so the inscription alone solves the order.
                    val order = listOf("Vardorvis (the blade)", "Duke Sucellus (the ice)", "The Leviathan (the deep)", "The Whisperer (the whisper)")
                    val remaining = order.toMutableList()
                    for (i in order.indices) {
                        val shown = remaining.sorted()
                        val pick = shown.getOrNull(options(*shown.toTypedArray(), title = "Which seal was set ${listOf("first", "second", "third", "last")[i]}?") - 1)
                        if (pick != order[i]) {
                            chatNpc(
                                "No... the walls say the blade before the ice, the",
                                "ice before the deep, the deep before the whisper.",
                                "Speak to me again and set them in that order.",
                                facialExpression = FacialExpression.UPSET,
                            )
                            return@QuestStep false
                        }
                        remaining.remove(pick)
                    }
                    chatNpc(
                        "Blade, ice, deep, whisper. Yes. And the dead are",
                        "walking out of that vault because the seals are",
                        "failing. One mummy has come as far as our town.",
                        facialExpression = FacialExpression.SCARED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ALI_THE_WISE,
                journal = listOf("I must defeat the ${red("mummy")} (level 84) that came out of", "the Ancient Vault into Nardah."),
                done = listOf("I put the vault's mummy back to rest."),
                place = "Nardah",
                location = NARDAH,
                tip = "Bring food and a weapon for the mummy.",
                fight = QuestFight(Npcs.MUMMY_2018, "mummy", 84, NARDAH, "Mmmhhh..."),
                talk = {
                    chatNpc("The mummy still walks. Please, hurry.", facialExpression = FacialExpression.SCARED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.AZZANADRA,
                journal = listOf("I should warn ${red("Azzanadra")} in the Grand Exchange", "hall about the Forgotten Four."),
                done = listOf("Azzanadra helped me seal the Forgotten Four away."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "The seals fail because someone is breaking them.",
                        "Sliske - I would know his hand anywhere. He wants",
                        "the Four as his own champions.",
                        facialExpression = FacialExpression.ANGRY,
                    )
                    chatNpc(
                        "He will not have them. Take my strength and set",
                        "the medallions again, in the order you learned.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    messageBox("With Azzanadra's power behind you, you reseal the Forgotten Four and take the rings Sliske left behind.")
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ARCHAEOLOGIST,
                journal = listOf("I should tell the ${red("Archaeologist")} in the Grand", "Exchange hall that the vault is safe."),
                done = listOf("The Archaeologist catalogued the vault's treasures."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "The four of them, sealed again? And the vault's all",
                        "mine to catalogue? You wonderful lunatic!",
                        facialExpression = FacialExpression.LAUGH_EXCITED,
                    )
                    true
                },
            ),
        )
}

// ---------------------------------------------------------------------------------------------- Dragon Slayer II

object DragonSlayerII : ShortQuest(
    name = "Dragon Slayer II",
    startPoint = "Speak to Aleck in the Grand Exchange hall.",
    rewards = "5 Quest Points, 80,000 Smithing, 60,000 Mining, 50,000 Agility and 50,000 Thieving XP, Ava's assembler.",
    pointReward = 5,
    questId = OsrsQuestVarps.DRAGON_SLAYER_II,
    slot = OsrsQuestSlots.DRAGON_SLAYER_II,
    completedValue = 10,
    usesVarbits = false,
    startNpc = Npcs.ALECK,
    startNpcName = "Aleck",
    startPlace = "the Grand Exchange hall",
    startLocation = GE_HALL,
) {
    override val reward =
        QuestReward(
            icon = Items.VORKATHS_HEAD,
            xp = listOf(Skills.SMITHING to 80_000.0, Skills.MINING to 60_000.0, Skills.AGILITY to 50_000.0, Skills.THIEVING to 50_000.0),
            // OSRS Wiki "Ellen": after the quest, 25,000 XP four times in Attack, Strength, Defence, Ranged, Magic or
            // Hitpoints; no level requirement is given.
            lamps = List(4) { QuestLamp(25_000, FoundationRewards.COMBAT_SKILLS, 1, "25,000 XP Myths' Guild combat reward") },
            lines = listOf("80,000 Smithing XP", "60,000 Mining XP", "50,000 Agility XP", "50,000 Thieving XP", "Four 25,000 XP combat rewards", "Access to the Myths' Guild", "Ability to make Ava's assembler"),
            unlock = { p ->
                p.attr[UnlockNpcRewards.DRAGON_SLAYER_II_REWARDED] = true
                p.attr[UnlockNpcRewards.DRAGON_SLAYER_II_UNLOCKED] = true
                p.attr[UnlockNpcRewards.MYTHS_GUILD_UNLOCKED] = true
                p.attr[UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] = true
            },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Dragons are gathering again, and not by chance.",
            "Someone is calling them - something older than",
            "Elvarg ever was.",
            facialExpression = FacialExpression.WORRIED,
        )
        chatNpc(
            "Oziach in Edgeville sold armour to the last hero",
            "who faced a dragon like that. Ask him.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll hunt the dragons down.", "Not right now.", title = "Start Dragon Slayer II?") != 1) return false
        chatPlayer("I'll find out who is calling them.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.OZIACH,
                journal = listOf("I should ask ${red("Oziach")} in ${red("Edgeville")} about the", "gathering dragons."),
                done = listOf("Oziach told me of Galvek and the dragonkin."),
                place = "Edgeville",
                location = EDGEVILLE,
                talk = {
                    chatNpc(
                        "Galvek. That's the name the old books give it -",
                        "a dragon of the first age, and the dragonkin",
                        "behind it. One hero can't stop that alone.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    chatNpc(
                        "You'll need the kingdoms behind you. Start with",
                        "King Roald in Varrock, then the Fremennik.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_ROALD,
                journal = listOf("I should ask ${red("King Roald")} in ${red("Varrock Palace")} to stand", "against the dragons."),
                done = listOf("King Roald pledged Varrock's soldiers."),
                place = "Varrock Palace",
                location = VARROCK_PALACE,
                talk = {
                    chatNpc(
                        "Dragons? Over my city? Not while I sit on this",
                        "throne. Varrock's soldiers are yours - tell the",
                        "Fremennik we fight together for once.",
                        facialExpression = FacialExpression.ANGRY,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BRUNDT_THE_CHIEFTAIN,
                journal = listOf("I should ask ${red("Brundt the Chieftain")} in the ${red("Rellekka")}", "longhall for the Fremennik's longships."),
                done = listOf("Brundt pledged the Fremennik longships."),
                place = "the Rellekka longhall",
                location = RELLEKKA_LONGHALL,
                talk = {
                    chatNpc(
                        "Varrock asks the Fremennik for help? The world",
                        "must truly be ending. Our longships will sail.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                    chatNpc(
                        "But the call comes from Crandor, where Elvarg's",
                        "bones lie. Ned of Draynor knows those waters.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.NED,
                journal = listOf("I should ask ${red("Ned")} in ${red("Draynor Village")} to sail me", "to Crandor."),
                done = listOf("Ned sailed me out to Crandor."),
                place = "Draynor Village",
                location = DRAYNOR,
                talk = {
                    chatNpc(
                        "Another dragon? My knees! Still, the Lady Lumbridge",
                        "never lost a crew yet. Climb aboard - it's Crandor,",
                        "where old Elvarg's bones were stirring.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                    if (!DangerWarning.confirmQuestTravel(this, CRANDOR)) return@QuestStep false
                    // Owner 2026-10-07: the trip starts on "Enter" - the move first, the line in the chatbox, no click in between.
                    player.moveTo(CRANDOR)
                    player.message("Ned sails you out to Crandor. Galvek's call has woken Elvarg again.")
                    wait(2)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.NED,
                journal = listOf("I must defeat ${red("Elvarg")} (level 83), woken by Galvek's call,", "on Crandor. An anti-dragon shield protects from her fire."),
                done = listOf("I slew Elvarg again and Ned sailed me back."),
                place = "Crandor",
                location = CRANDOR,
                tip = "Wear an anti-dragon shield or drink an antifire potion: Elvarg breathes fire.",
                fight = QuestFight(Npcs.ELVARG, "Elvarg", 83, CRANDOR, "Rrrooaaarr!", returnTo = DRAYNOR),
                talk = {
                    chatNpc("Back to Crandor, then? Hold on to your hat.", facialExpression = FacialExpression.LAUGH)
                    if (!DangerWarning.confirmQuestTravel(this, CRANDOR)) return@QuestStep false
                    player.moveTo(CRANDOR)
                    wait(2)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ALECK,
                journal = listOf("I should tell ${red("Aleck")} in the Grand Exchange hall", "that Galvek's call is broken."),
                done = listOf("Aleck welcomed me to the Myths' Guild."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "Elvarg slain, Varrock and the Fremennik side by",
                        "side, and Galvek's call silenced! The Myths' Guild",
                        "will want your name on its walls.",
                        facialExpression = FacialExpression.LAUGH_EXCITED,
                    )
                    true
                },
            ),
        )
}

// -------------------------------------------------------------------------------------------- Song of the Elves

object SongOfTheElves : ShortQuest(
    name = "Song of the Elves",
    startPoint = "Speak to Edmond in East Ardougne.",
    rewards = "4 Quest Points, 40,000 XP in eight skills, crystal equipment.",
    pointReward = 4,
    questId = OsrsQuestVarps.SONG_OF_THE_ELVES,
    slot = OsrsQuestSlots.SONG_OF_THE_ELVES,
    completedValue = 10,
    usesVarbits = false,
    startNpc = Npcs.EDMOND,
    startNpcName = "Edmond",
    startPlace = "his home in East Ardougne",
    startLocation = EAST_ARDOUGNE,
) {
    override val reward =
        QuestReward(
            icon = Items.CRYSTAL_SEED,
            xp =
                listOf(Skills.AGILITY, Skills.CONSTRUCTION, Skills.FARMING, Skills.HERBLORE, Skills.HUNTER, Skills.MINING, Skills.SMITHING, Skills.WOODCUTTING)
                    .map { it to 40_000.0 },
            lines =
                listOf(
                    "40,000 XP in Agility, Construction, Farming,",
                    "Herblore, Hunter, Mining, Smithing, Woodcutting",
                    "Ability to wear crystal armour and the Bow of faerdhinen",
                    "Ability to sing crystal at a singing bowl",
                ),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Elena went back into West Ardougne. She says the",
            "plague was never real - that the Mourners and King",
            "Lathas lied to keep the city walled. I'm worried sick.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll find Elena.", "Not right now.", title = "Start Song of the Elves?") != 1) return false
        chatPlayer("I'll find Elena for you.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ELENA,
                journal = listOf("I should find ${red("Elena")} in ${red("East Ardougne")}."),
                done = listOf("Elena told me the plague was a hoax."),
                place = "East Ardougne",
                location = EAST_ARDOUGNE,
                talk = {
                    chatNpc(
                        "Father sent you? I'm fine. I've seen the proof: there",
                        "is no plague. The Mourners are Lord Iorwerth's",
                        "elves and Lathas's men, keeping the west walled.",
                        facialExpression = FacialExpression.ANGRY,
                    )
                    chatNpc(
                        "One of their armed Mourners watches the gate. Stop",
                        "him, and the people will see the truth.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ELENA,
                journal = listOf("I must defeat the ${red("armed Mourner")} (level 108) in", "${red("West Ardougne")}."),
                done = listOf("I defeated the armed Mourner and the city rose up."),
                place = "West Ardougne",
                location = WEST_ARDOUGNE,
                tip = "Bring food and a weapon for the armed Mourner.",
                fight = QuestFight(Npcs.MOURNER_2373, "armed Mourner", 108, WEST_ARDOUGNE, "You'll never leave the city!"),
                talk = {
                    chatNpc("He's still at his post in the west. Be careful!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ARIANWYN,
                journal = listOf("I should speak to ${red("Arianwyn")} in the elf camp of", "${red("Lletya")}, west of the Arandar pass."),
                done = listOf("Arianwyn told me Iorwerth means to wake the Dark Lord."),
                place = "Lletya",
                location = LLETYA,
                talk = {
                    chatNpc(
                        "Lathas has fallen? Then Iorwerth has lost his",
                        "puppet - but not his plan. He means to wake the",
                        "Dark Lord and rule Tirannwn through him.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    chatNpc(
                        "His warriors hunt our clan leaders in the forest.",
                        "One is already outside our camp. Stop him.",
                        facialExpression = FacialExpression.ANGRY,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ARIANWYN,
                journal = listOf("I must defeat ${red("Lord Iorwerth's elf warrior")} (level 90) outside", "${red("Lletya")}."),
                done = listOf("I defeated Lord Iorwerth's elf warrior."),
                place = "Lletya",
                location = LLETYA,
                tip = "The elf warrior shoots from range. Protect from Missiles helps.",
                fight = QuestFight(Npcs.ELF_WARRIOR, "elf warrior", 90, LLETYA, "For Lord Iorwerth!"),
                talk = {
                    chatNpc("He's close - I can hear his bowstring.", facialExpression = FacialExpression.SCARED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EDMOND,
                journal = listOf("I should tell ${red("Edmond")} in East Ardougne that Elena", "is safe and the elves are free."),
                done = listOf("Edmond thanked me for finding Elena."),
                place = "East Ardougne",
                location = EAST_ARDOUGNE,
                talk = {
                    chatNpc(
                        "She's safe? Thank Saradomin. And the elves are",
                        "singing crystal again in Prifddinas - the whole of",
                        "Ardougne will hear about this.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    true
                },
            ),
        )
}

// --------------------------------------------------------------------------------------------- Monkey Madness II

object MonkeyMadnessII : ShortQuest(
    name = "Monkey Madness II",
    startPoint = "Speak to King Narnode Shareen in the Grand Exchange hall.",
    rewards = "4 Quest Points, 80,000 Slayer, 60,000 Agility, 50,000 Thieving and 50,000 Hunter XP, zenyte and the heavy ballista.",
    pointReward = 4,
    questId = OsrsQuestVarps.MONKEY_MADNESS_II,
    slot = OsrsQuestSlots.MONKEY_MADNESS_II,
    completedValue = 10,
    usesVarbits = false,
    startNpc = Npcs.KING_NARNODE_SHAREEN,
    startNpcName = "King Narnode Shareen",
    startPlace = "the Grand Exchange hall",
    startLocation = GE_HALL,
) {
    override val reward =
        QuestReward(
            icon = Items.ZENYTE_SHARD,
            xp = listOf(Skills.SLAYER to 80_000.0, Skills.AGILITY to 60_000.0, Skills.THIEVING to 50_000.0, Skills.HUNTER to 50_000.0),
            // OSRS Wiki "Monkey Madness II" rewards: 2x 50,000 XP from Duke in Magic, Ranged, Attack, Defence, Strength or
            // Hitpoints; no level requirement is given.
            lamps = List(2) { QuestLamp(50_000, FoundationRewards.COMBAT_SKILLS, 1, "50,000 XP Duke combat training") },
            lines = listOf("80,000 Slayer XP", "60,000 Agility XP", "50,000 Thieving XP", "50,000 Hunter XP", "Two 50,000 XP combat rewards", "Ability to wield the heavy ballista", "Ability to craft and wear zenyte jewellery"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Glough is gone from his cell, and my glider pilots",
            "report monkeys where no monkey should be. I fear",
            "the old plot against Ape Atoll lives on.",
            facialExpression = FacialExpression.WORRIED,
        )
        chatNpc(
            "Anita was the last to see him - she lives near the",
            "Grand Tree. Start with her.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll stop Glough.", "Not right now.", title = "Start Monkey Madness II?") != 1) return false
        chatPlayer("I'll stop Glough, Your Majesty.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ANITA,
                journal = listOf("I should ask ${red("Anita")} at the ${red("Grand Tree")} about Glough."),
                done = listOf("Anita told me Glough had fled to Ape Atoll."),
                place = "the Grand Tree",
                location = GRAND_TREE,
                talk = {
                    chatNpc(
                        "Glough? He came here the night he escaped, raving",
                        "about gorillas and airships. He took a glider",
                        "south - to Ape Atoll, I'd bet my life on it.",
                        facialExpression = FacialExpression.SCARED,
                    )
                    chatNpc("Daero in the glider hangar will know.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.DAERO,
                journal = listOf("I should speak to ${red("Daero")} in the ${red("glider hangar")}", "under the Grand Tree."),
                done = listOf("Daero flew me to Ape Atoll."),
                place = "the glider hangar",
                location = GLIDER_HANGAR,
                talk = {
                    chatNpc(
                        "Glough's been building something on Ape Atoll -",
                        "demonic gorillas, if the pilots are right. Garkor",
                        "leads the 10th squad there. I'll fly you to him.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    if (!DangerWarning.confirmQuestTravel(this, APE_ATOLL)) return@QuestStep false
                    // Owner 2026-10-07: the trip starts on "Enter" - the move first, the line in the chatbox, no click in between.
                    player.moveTo(APE_ATOLL)
                    player.message("Daero flies you south to Ape Atoll.")
                    wait(2)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GARKOR,
                journal = listOf("I should speak to ${red("Garkor")} of the 10th squad on", "${red("Ape Atoll")}."),
                done = listOf("Garkor told me Glough had turned the monkey guards."),
                place = "Ape Atoll",
                location = APE_ATOLL,
                talk = {
                    chatNpc(
                        "Glough has bought one of Awowogei's guards. He",
                        "lets Glough's people come and go. Beat him and",
                        "we'll learn where the airship is kept.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GARKOR,
                journal = listOf("I must defeat the ${red("monkey guard")} (level 167) that Glough", "bought on ${red("Ape Atoll")}."),
                done = listOf("I beat the bought monkey guard; he talked of Crash Island."),
                place = "Ape Atoll",
                location = APE_ATOLL_GUARD,
                tip = "The monkey guard hits hard in melee. Protect from Melee helps.",
                fight = QuestFight(Npcs.MONKEY_GUARD_1459, "monkey guard", 167, APE_ATOLL_GUARD, "Ook! Glough pays better!"),
                talk = {
                    chatNpc("The guard is still at his post. Go!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.WAYDAR,
                journal = listOf("I should speak to ${red("Waydar")} on ${red("Crash Island")}, where", "Glough's archers have landed."),
                done = listOf("Waydar told me Glough's monkeys had landed."),
                place = "Crash Island",
                location = CRASH_ISLAND,
                talk = {
                    chatNpc(
                        "Glough's monkeys have landed here already. Their",
                        "archer leads them - take him down and the rest",
                        "will scatter.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.WAYDAR,
                journal = listOf("I must defeat Glough's ${red("monkey archer")} (level 86) on", "Crash Island."),
                done = listOf("I shot down Glough's monkey archer and his plan fell apart."),
                place = "Crash Island",
                location = CRASH_ISLAND,
                tip = "The monkey archer shoots from range. Protect from Missiles helps.",
                fight = QuestFight(Npcs.MONKEY_ARCHER_1457, "monkey archer", 86, CRASH_ISLAND, "Ook! Ook!"),
                talk = {
                    chatNpc("He's still on the island. Keep your head down!", facialExpression = FacialExpression.TOUGH)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_NARNODE_SHAREEN,
                journal = listOf("I should report to ${red("King Narnode Shareen")} in the", "Grand Exchange hall."),
                done = listOf("King Narnode thanked me for saving Ape Atoll."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "Glough's guard beaten, his archers scattered, and",
                        "Ape Atoll at peace! The gnomes will sing of this",
                        "for a generation.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    true
                },
            ),
        )
}

// -------------------------------------------------------------------------------- the list entries and the rest

/** The quest-list slots QuestListCacheTool gives the ten OSRS quests (enum 2252 keys 185-194). */
object OsrsQuestSlots {
    const val DRAGON_SLAYER_II = 185
    const val SONG_OF_THE_ELVES = 186
    const val DESERT_TREASURE_II = 187
    const val MONKEY_MADNESS_II = 188
    const val SINS_OF_THE_FATHER = 189
    const val CHILDREN_OF_THE_SUN = 190
    const val SECRETS_OF_THE_NORTH = 191
    const val BONE_VOYAGE = 192
    const val MAGE_ARENA_II = 193
    const val BENEATH_CURSED_SANDS = 194
}

/** Their progress varps (free in cache and server, see the file comment). */
object OsrsQuestVarps {
    const val DRAGON_SLAYER_II = 1910
    const val SONG_OF_THE_ELVES = 1911
    const val DESERT_TREASURE_II = 1912
    const val MONKEY_MADNESS_II = 1913
    const val SINS_OF_THE_FATHER = 1914
    const val CHILDREN_OF_THE_SUN = 1915
    const val SECRETS_OF_THE_NORTH = 1916
    const val BONE_VOYAGE = 1917
    const val MAGE_ARENA_II = 1918
    const val BENEATH_CURSED_SANDS = 1919
}

/** An OSRS quest that is simply complete for every account (owner: "Alle andere quests ... staan op voltooid"). */
class CompletedOsrsQuest(name: String, pointReward: Int, questId: Int, slot: Int, private val summary: String) :
    Quest(name, "Already completed.", emptyList(), "None.", "None.", summary, pointReward, questId, 0, slot, 1) {
    override fun getObjective(player: Player, stage: Int): QuestStage =
        QuestStage(listOf(striked(summary), "", "<col=FF0000>QUEST COMPLETE!"))

    override fun finishQuest(player: Player) = Unit
}

/** A quest npc the world did not spawn yet, on its 2011 post. */
data class QuestNpcPost(val npc: Int, val tile: Tile, val facing: gg.rsmod.game.model.Direction, val idle: List<String>)

object FoundationQuests {
    /**
     * Npcs of the short quests that the world did not spawn yet, on their 2011 posts (Void 2011 npc-spawns). Lokar moved one
     * tile south-west: his Void tile is covered by a solid dock loc (816) on this map (NewPlayerFoundationTests checks every post).
     */


    val NPC_POSTS: List<QuestNpcPost> =
        listOf(
            QuestNpcPost(Npcs.EBLIS, Tile(3185, 2983), gg.rsmod.game.model.Direction.SOUTH, listOf("The desert keeps its secrets, stranger.", "Most of them are buried with the people who asked.")),
            QuestNpcPost(Npcs.LOKAR_SEARUNNER, Tile(2620, 3687), gg.rsmod.game.model.Direction.WEST, listOf("Fair winds to you! If you ever need a ship to", "Pirates' Cove, you know where I'll be.")),
            QuestNpcPost(Npcs.ALI_THE_WISE, Tile(3420, 2938), gg.rsmod.game.model.Direction.SOUTH, listOf("Wisdom is knowing which doors to leave closed.", "Nardah has enough trouble without opening more.")),
            QuestNpcPost(Npcs.GOSSIP, Tile(2742, 3555), gg.rsmod.game.model.Direction.SOUTH, listOf("Did you hear about the Sinclairs? Oh, you were", "there? Then you know more than I do!")),
            QuestNpcPost(Npcs.ANNA, Tile(2734, 3575), gg.rsmod.game.model.Direction.SOUTH, listOf("Thank you again for believing me.", "Father would have been proud of you.")),
            QuestNpcPost(Npcs.ELENA, Tile(2592, 3336), gg.rsmod.game.model.Direction.WEST, listOf("There's always another sickness to cure.", "Mind how you go.")),
            QuestNpcPost(Npcs.DAERO, Tile(2648, 4519), gg.rsmod.game.model.Direction.SOUTH, listOf("The gliders run on time again.", "Glough's gorillas won't trouble the hangar now.")),
            QuestNpcPost(Npcs.WAYDAR, Tile(2891, 2724), gg.rsmod.game.model.Direction.EAST, listOf("Crash Island is quiet now.", "I rather miss the excitement.")),
            QuestNpcPost(Npcs.DR_NABANIK, DIGSITE, gg.rsmod.game.model.Direction.SOUTH, listOf("The Exam Centre keeps me busy.", "Mind the dig trenches on your way out.")),
            QuestNpcPost(Npcs.MERLIN, MERLIN_CELL, gg.rsmod.game.model.Direction.SOUTH, listOf("Camelot has never been safer.", "Well - not since the last time, anyway.")),
            QuestNpcPost(Npcs.ARIANWYN, LLETYA, gg.rsmod.game.model.Direction.SOUTH, listOf("Tirannwn breathes again.", "Prifddinas sings for all of us now.")),
        )


    val SHORT: List<ShortQuest> =
        listOf(DesertTreasure, LunarDiplomacy, TempleAtSenntisten, KingsRansom, DesertTreasureII, DragonSlayerII, SongOfTheElves, MonkeyMadnessII)

    /** OSRS Wiki quest points: Sins of the Father 2, Children of the Sun 1, Secrets of the North 2, Bone Voyage 1, Beneath Cursed Sands 2; Mage Arena II is a miniquest (0). */
    val COMPLETED: List<CompletedOsrsQuest> =
        listOf(
            CompletedOsrsQuest("Sins of the Father", 2, OsrsQuestVarps.SINS_OF_THE_FATHER, OsrsQuestSlots.SINS_OF_THE_FATHER, "Vanstrom Klause was defeated and Darkmeyer freed."),
            CompletedOsrsQuest("Children of the Sun", 1, OsrsQuestVarps.CHILDREN_OF_THE_SUN, OsrsQuestSlots.CHILDREN_OF_THE_SUN, "The plot in Varrock was uncovered."),
            CompletedOsrsQuest("Secrets of the North", 2, OsrsQuestVarps.SECRETS_OF_THE_NORTH, OsrsQuestSlots.SECRETS_OF_THE_NORTH, "The secrets of the Weiss mountains were revealed."),
            CompletedOsrsQuest("Bone Voyage", 1, OsrsQuestVarps.BONE_VOYAGE, OsrsQuestSlots.BONE_VOYAGE, "The Varrock Museum's barge reached Fossil Island."),
            CompletedOsrsQuest("Mage Arena II", 0, OsrsQuestVarps.MAGE_ARENA_II, OsrsQuestSlots.MAGE_ARENA_II, "Kolodion's enchanted god capes were earned."),
            CompletedOsrsQuest("Beneath Cursed Sands", 2, OsrsQuestVarps.BENEATH_CURSED_SANDS, OsrsQuestSlots.BENEATH_CURSED_SANDS, "The Tombs of Amascut were opened."),
        )

    /** Registers every quest with the quest tab (Quest.quests) exactly once, at plugin load. */
    fun register() {
        (SHORT + COMPLETED).forEach { quest -> if (Quest.quests.none { it.slot == quest.slot }) Quest.addQuest(quest) }
    }

    /** The existing "refresh" of the quest list (QuestExt.advanceToNextStage): the grouping varbit flips and back. */
    fun refreshQuestList(player: Player) {
        player.toggleVarbit(QUEST_LIST_GROUPING_VARBIT)
        player.toggleVarbit(QUEST_LIST_GROUPING_VARBIT)
    }

    /** Quest list grouping (enum 2243: 0 Free/Members, 1 Progress, 2 Difficulty) and its direction (0 normal). */
    const val QUEST_LIST_GROUPING_VARBIT = 4536
    const val QUEST_LIST_DIRECTION_VARBIT = 4538
    const val GROUP_BY_PROGRESS = 1

    val QUEST_LIST_ORDER_SET = gg.rsmod.game.model.attr.AttributeKey<Boolean>(persistenceKey = "foundation_quest_list_order_set")

    /**
     * Once per account: the quest list opens grouped by progress, unfinished quests on top (enum 2250 order, see
     * QuestListCacheTool). Only once, so a player who picks another grouping in the quest tab keeps it.
     */
    fun applyQuestListOrder(player: Player) {
        if (player.attr[QUEST_LIST_ORDER_SET] == true) return
        player.attr[QUEST_LIST_ORDER_SET] = true
        player.setVarbit(QUEST_LIST_GROUPING_VARBIT, GROUP_BY_PROGRESS)
        player.setVarbit(QUEST_LIST_DIRECTION_VARBIT, 0)
    }

    // ------------------------------------------------------------------------------------------- conversations

    private class Action(val label: String, val run: suspend QueueTask.() -> Unit)

    /**
     * The quest part of a conversation with [npc]: starting a quest, doing the open step, or a reminder with the offer to
     * travel. Returns false when [npc] has nothing to say about any foundation quest (or the player picked "Something
     * else"), so the caller carries on with the npc's own dialogue.
     */
    suspend fun talk(task: QueueTask, npc: Int): Boolean {
        val player = task.player
        val actions = mutableListOf<Action>()
        var direct = false
        for (quest in SHORT) {
            if (quest.isFinished(player)) continue
            val stage = quest.stage(player)
            if (stage <= 0) {
                if (quest.startNpc == npc) actions += Action("${quest.name}.") { runStart(this, quest) }
                continue
            }
            val step = quest.steps.getOrNull(stage - 1) ?: continue
            if (step.npc == npc) {
                actions += Action("${quest.name}.") { runStep(this, quest, stage) }
                direct = true
            } else if (quest.startNpc == npc || quest.steps.take(stage - 1).any { it.npc == npc }) {
                actions += Action("${quest.name}.") { runReminder(this, quest, stage) }
            }
        }
        if (actions.isEmpty()) return false
        if (actions.size == 1 && direct) {
            actions.single().run(task)
            return true
        }
        val labels = actions.take(4).map { it.label } + "Something else."
        val choice = task.options(*labels.toTypedArray(), title = "Quests")
        val action = actions.getOrNull(choice - 1) ?: return false
        action.run(task)
        return true
    }

    private suspend fun runStart(task: QueueTask, quest: ShortQuest) {
        val accepted = with(quest) { task.intro() }
        if (!accepted || quest.stage(task.player) != 0) return
        quest.setStage(task.player, 1)
        task.player.message("You've started a new quest: <col=5861e9>${quest.name}")
        QuestHelper.onStarted(task.player, quest)
        val first = quest.steps.first()
        if (first.location != null && first.place != null) with(quest) { task.offerTravel(first.place, first.location, first.npc.takeIf { first.fight == null }) }
    }

    private suspend fun runStep(task: QueueTask, quest: ShortQuest, stage: Int) {
        val player = task.player
        val step = quest.steps[stage - 1]
        if (step.fight != null) {
            // A fight step: the npc sends the player in; the kill completes it (QuestFights.onKilled).
            if (QuestFights.active(player) != null) {
                task.chatNpc("Go on, it's right there! Finish the fight.", facialExpression = FacialExpression.ANGRY)
                return
            }
            if (!step.talk(task) || quest.stage(player) != stage) return
            val fight = step.fight
            if (player.tile.height == fight.tile.height && player.tile.isWithinRadius(fight.tile, QuestFights.REACH)) {
                QuestFights.start(player, quest, stage)
            } else if (step.location != null && step.place != null) {
                with(quest) { task.offerTravel(step.place, step.location) }
            }
            return
        }
        if (!step.talk(task)) return
        // The conversation suspends: never advance a stage another path changed meanwhile.
        if (quest.stage(player) != stage) return
        completeStep(player, quest, stage)
    }

    /**
     * Step [stage] of [quest] is done: its fight items (a talk step hands its own items out in its conversation), then the
     * next stage - starting the next fight at once when the same npc sends the player straight into it - or the quest.
     */
    fun completeStep(player: Player, quest: ShortQuest, stage: Int) {
        val step = quest.steps[stage - 1]
        step.fight?.let { fight ->
            step.gives.forEach { (item, amount) -> FoundationRewards.grant(player, item, amount) }
            // The trip back is a quest move too: a Dangerous destination asks first; "No" stays (owner 2026-09-27).
            fight.returnTo?.let { back ->
                player.queue { if (DangerWarning.confirmQuestTravel(this, back)) player.moveTo(back) }
            }
        }
        if (stage == quest.steps.size) {
            FoundationRewards.complete(player, quest)
            return
        }
        quest.setStage(player, stage + 1)
        player.message("Your quest journal has been updated: <col=5861e9>${quest.name}")
        QuestHelper.start(player)
        val next = quest.steps[stage]
        if (next.fight != null && next.npc == step.npc) QuestFights.start(player, quest, stage + 1)
    }

    /** Names of the quest npcs, for messages that point the player back to one. */
    val NPC_NAMES: Map<Int, String> =
        mapOf(
            Npcs.EBLIS to "Eblis", Npcs.LOKAR_SEARUNNER to "Lokar Searunner", Npcs.ALI_THE_WISE to "Ali the Wise",
            Npcs.ANNA to "Anna", Npcs.NED to "Ned", Npcs.ELENA to "Elena", Npcs.WAYDAR to "Waydar", Npcs.GARKOR to "Garkor",
            Npcs.DR_NABANIK to "Dr Nabanik", Npcs.ARIANWYN to "Arianwyn", Npcs.BRUNDT_THE_CHIEFTAIN to "Brundt the Chieftain",
        )

    private suspend fun runReminder(task: QueueTask, quest: ShortQuest, stage: Int) {
        val player = task.player
        val step = quest.steps[stage - 1]
        // An npc of an earlier step hands its quest items out again when the player lost them (before they are used).
        val npc = task.player.getInteractingNpc().id
        // Owner 2026-09-26: no free copies. Only untradeable quest items come back (nothing that can be sold or traded on),
        // and only when the account holds none anywhere - inventory, bank or worn (the Seal of passage is an amulet).
        val lost =
            quest.steps.take(stage - 1).filter { it.npc == npc }.flatMap { it.gives }.filter { (item, _) ->
                val def = player.world.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, item)
                def != null && !def.tradeable &&
                    !player.inventory.contains(item) && !player.bank.contains(item) && !player.equipment.contains(item)
            }
        if (lost.isNotEmpty()) {
            task.chatNpc("You've lost what I gave you? Here, take it again - and be careful this time.")
            lost.forEach { (item, amount) -> FoundationRewards.grant(player, item, amount) }
        }
        task.chatPlayer(*step.journal.map { it.replace(Regex("<[^>]+>"), "") }.toTypedArray(), facialExpression = FacialExpression.THINKING)
        if (step.location != null && step.place != null) with(quest) { task.offerTravel(step.place, step.location, step.npc.takeIf { step.fight == null }) }
    }

    /**
     * Where a quest travel to [location] for a talk with [npc] lands (owner 2026-10-07, conform quests: Eblis, Anna, Edmond
     * could not be reached - "I can't reach that!" - from a landing outside their house): a free tile beside the npc's own
     * post when it stands near [location] on the same floor, in the walkable area of that post; otherwise [location].
     */
    fun landing(world: gg.rsmod.game.model.World, npc: Int?, location: Tile): Tile {
        if (npc == null) return location
        var post: Tile? = null
        world.npcs.forEach { n ->
            val t = n.spawnTile
            if (n.id != npc || n.owner != null || t.height != location.height || !t.isWithinRadius(location, QuestHelper.NPC_RANGE)) return@forEach
            if (post == null || t.getDistance(location) < post!!.getDistance(location)) post = t
        }
        val at = post ?: return location
        val area = gg.rsmod.game.model.collision.Landing.pocket(world.collision, at, 256)
        for (r in 1..3) {
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(Math.abs(dx), Math.abs(dz)) != r) continue
                val t = Tile(at.x + dx, at.z + dz, at.height)
                if (t in area) return t
            }
        }
        return location
    }

    /** The journal/stage summary the Quest Guide uses. */
    fun startPoint(quest: ShortQuest): Pair<String, Tile?> = quest.startPlace to quest.startLocation

    fun inProgress(player: Player, quest: ShortQuest): Boolean = quest.stage(player) in 1 until quest.completedValue

    fun started(player: Player, quest: Quest): Boolean = player.getCurrentStage(quest) > 0
}
