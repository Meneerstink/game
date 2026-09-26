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
private val EAST_ARDOUGNE = Tile(2568, 3331, 0)
private val GLIDER_HANGAR = Tile(2648, 4517, 0)
private val CRASH_ISLAND = Tile(2894, 2726, 0)

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
            "Azzanadra, sealed in by four diamonds. Bandits",
            "carried the diamonds off before I could study them.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        chatNpc(
            "Eblis at the Bandit Camp knows who has them.",
            "Fetch them for me? I'd go myself, but I bruise.",
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
                done = listOf("Eblis gave me the four Diamonds of Azzanadra."),
                place = "the Bandit Camp",
                location = BANDIT_CAMP,
                gives = DIAMONDS.map { it to 1 },
                talk = {
                    chatNpc(
                        "So the digger sent you. The four of them fought",
                        "over those stones - Damis, Fareed, Kamil and",
                        "Dessous. None of them came back for them.",
                        facialExpression = FacialExpression.SECRETLY_TALKING,
                    )
                    chatNpc(
                        "Take them. Blood, ice, smoke and shadow. Bring",
                        "them to the one they were meant to hold down.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    DIAMONDS.forEach { FoundationRewards.grant(player, it, 1) }
                    player.message("Eblis hands you the four Diamonds of Azzanadra.")
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
            "The Fremennik and my people have not spoken in",
            "an age. I would end that silence, but they will",
            "not listen to a Moon Clan mage.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        chatNpc(
            "Lokar Searunner sails from Rellekka's west dock.",
            "Ask him for a Seal of passage and bring it to me.",
            facialExpression = FacialExpression.SECRETLY_TALKING,
        )
        if (options("I'll go and see Lokar.", "Not right now.", title = "Start Lunar Diplomacy?") != 1) return false
        chatPlayer("I'll go and see Lokar for you.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.LOKAR_SEARUNNER,
                journal = listOf("I should speak to ${red("Lokar Searunner")} on the west", "dock of ${red("Rellekka")} about a Seal of passage."),
                done = listOf("Lokar Searunner gave me a Seal of passage."),
                place = "Rellekka",
                location = RELLEKKA_DOCK,
                gives = listOf(Items.SEAL_OF_PASSAGE to 1),
                talk = {
                    chatNpc(
                        "The moon folk want to talk? Ha! Took them long",
                        "enough. My brother trades with them, you know.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                    chatNpc(
                        "Here - a Seal of passage. Wear it on the isle",
                        "and they'll know you come in peace.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    FoundationRewards.grant(player, Items.SEAL_OF_PASSAGE, 1)
                    player.message("Lokar hands you a Seal of passage.")
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ONEIROMANCER,
                journal = listOf("I should bring the Seal of passage to the", "${red("Oneiromancer")} in the Grand Exchange hall."),
                done = listOf("The Oneiromancer made peace in the dream world", "and taught me the Lunar spells."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    if (!player.inventory.contains(Items.SEAL_OF_PASSAGE) && !player.equipment.contains(Items.SEAL_OF_PASSAGE)) {
                        chatNpc(
                            "Without the Seal the Fremennik will not believe",
                            "you. Bring it to me.",
                            facialExpression = FacialExpression.UPSET,
                        )
                        false
                    } else {
                        chatNpc(
                            "The Seal... then the Fremennik have answered.",
                            "Sleep now. In the dream, the moon listens.",
                            facialExpression = FacialExpression.EYES_CLOSED,
                        )
                        messageBox("You drift into a dream of silver light, and wake knowing the Lunar spells.")
                        chatNpc(
                            "Welcome, friend of the Moon Clan. Keep the Seal;",
                            "Lunar Isle is open to you now.",
                            facialExpression = FacialExpression.HAPPY,
                        )
                        true
                    }
                },
            ),
        )
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
                    QuestLamp(23_000, FoundationRewards.ALL_SKILLS, 50, "23,000 XP lamp (any skill, level 50+)"),
                    QuestLamp(20_000, FoundationRewards.COMBAT_SKILLS, 50, "20,000 XP combat lamp (level 50+)"),
                    QuestLamp(20_000, FoundationRewards.COMBAT_SKILLS, 50, "20,000 XP combat lamp (level 50+)"),
                ),
            items = listOf(Items.ANCIENT_HYMNAL to 1),
            lines =
                listOf(
                    "10,000 Prayer XP",
                    "A 23,000 XP lamp (any skill, level 50+)",
                    "Two 20,000 XP combat lamps (level 50+)",
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
            "The temple of Zaros at Senntisten lies defiled.",
            "Before I can rededicate it I need the relics that",
            "Ali the Wise of Nardah has kept hidden.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        chatNpc(
            "Serve me in this and I will teach you prayers",
            "no Saradominist priest would dare to utter.",
            facialExpression = FacialExpression.SECRETLY_TALKING,
        )
        if (options("I'll fetch the relics.", "Not right now.", title = "Start The Temple at Senntisten?") != 1) return false
        chatPlayer("I'll fetch the relics from Ali the Wise.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ALI_THE_WISE,
                journal = listOf("I should speak to ${red("Ali the Wise")} in ${red("Nardah")}", "about the temple relics."),
                done = listOf("Ali the Wise entrusted the temple relics to me."),
                place = "Nardah",
                location = NARDAH,
                talk = {
                    chatNpc(
                        "Azzanadra sends a messenger now? He never did",
                        "learn patience. The relics are safe with me.",
                        facialExpression = FacialExpression.THINKING,
                    )
                    chatNpc(
                        "Tell him the wise man of Nardah keeps his word.",
                        "May the temple not bring ruin on us all.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    player.message("Ali the Wise entrusts the temple relics to you.")
                    true
                },
            ),
            QuestStep(
                npc = Npcs.AZZANADRA,
                journal = listOf("I should return to ${red("Azzanadra")} in the Grand", "Exchange hall with news of the relics."),
                done = listOf("Azzanadra rededicated the temple and taught me the", "Ancient Curses."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "The relics are placed and the temple breathes",
                        "again. Kneel, and hear the old words.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
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
                    QuestLamp(5_000, listOf(Skills.MAGIC), 45, "5,000 Magic XP lamp (level 45+)"),
                    QuestLamp(5_000, FoundationRewards.ALL_SKILLS, 50, "5,000 XP antique lamp (any skill, level 50+)"),
                    QuestLamp(33_000, listOf(Skills.DEFENCE), 65, "33,000 Defence XP lamp (level 65+)"),
                ),
            lines =
                listOf(
                    "A 5,000 Magic XP lamp (level 45+)",
                    "A 5,000 XP antique lamp (level 50+)",
                    "A 33,000 Defence XP lamp (level 65+)",
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
            "Anna's the one they say did it. Poor thing. She",
            "might talk to someone who isn't a gossip.",
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
                done = listOf("Anna told me the Black Knights hold King Arthur."),
                place = "Sinclair Mansion",
                location = SINCLAIR_MANSION,
                talk = {
                    chatNpc(
                        "I didn't kill anyone! It was a trick of the",
                        "Black Knights - they took King Arthur and they",
                        "want the Round Table to take the blame.",
                        facialExpression = FacialExpression.DISTRESSED,
                    )
                    chatNpc(
                        "Tell the knights at Camelot. Please.",
                        facialExpression = FacialExpression.SAD,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.KING_ARTHUR,
                journal = listOf("I should warn the knights at ${red("Camelot")} and", "speak to ${red("King Arthur")}."),
                done = listOf("King Arthur was rescued and cleared the Sinclairs."),
                place = "Camelot",
                location = CAMELOT,
                talk = {
                    chatNpc(
                        "Anna's word and yours, and my knights rode out",
                        "within the hour. I owe you my freedom, friend.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    chatNpc(
                        "Sir Lancelot keeps the training grounds upstairs.",
                        "Survive his Knight Waves and you shall learn the",
                        "prayers of the Round Table.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.SIR_LANCELOT,
                journal = listOf("I should speak to ${red("Sir Lancelot")} in Camelot", "and complete the Knight Waves Training Ground."),
                done = listOf("I defeated all eight Knight Waves."),
                place = "Camelot",
                location = CAMELOT,
                talk = {
                    chatNpc(
                        "Eight knights, one after another. Keep your",
                        "guard up and your head down.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    messageBox("You fight your way through all eight Knight Waves.")
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
                        60,
                        "100,000 XP ancient lamp (combat or Prayer, level 60+)",
                    )
                },
            items = listOf(Items.RING_OF_SHADOWS_UNCHARGED to 1),
            lines =
                listOf(
                    "Three 100,000 XP ancient lamps (level 60+)",
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
            "There's a vault north-east of Nardah that wasn't",
            "there last year. Old Zarosian work. Whatever sleeps",
            "inside, four of them woke up at once.",
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
                        "The vault holds the Forgotten Four: Vardorvis,",
                        "Duke Sucellus, the Leviathan and the Whisperer.",
                        "The Mahjarrat Azzanadra will know how to stop them.",
                        facialExpression = FacialExpression.WORRIED,
                    )
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
                        "Four of my kin's old servants walk again. So be",
                        "it - I will lend you my strength to break them.",
                        facialExpression = FacialExpression.ANGRY,
                    )
                    messageBox("With Azzanadra's power behind you, you break the Forgotten Four and take their rings.")
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
                        "The four of them, beaten? And the vault's all",
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
                        "behind it. You'll need a ship. Talk to Ned.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.NED,
                journal = listOf("I should ask ${red("Ned")} in ${red("Draynor Village")} to sail me", "to the dragon's island."),
                done = listOf("Ned sailed me out and I defeated Galvek."),
                place = "Draynor Village",
                location = DRAYNOR,
                talk = {
                    chatNpc(
                        "Another dragon? My knees! Still, the Lady Lumbridge",
                        "never lost a crew yet. Climb aboard.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                    messageBox("You sail out, face Galvek over the burning sea, and bring it down.")
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ALECK,
                journal = listOf("I should tell ${red("Aleck")} in the Grand Exchange hall", "that Galvek is dead."),
                done = listOf("Aleck welcomed me to the Myths' Guild."),
                place = "the Grand Exchange hall",
                location = GE_HALL,
                talk = {
                    chatNpc(
                        "Galvek, slain! The Myths' Guild will want your",
                        "name on its walls - and Ava owes you an",
                        "assembler, I'd wager.",
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
            "Elena's been gone for days, and the last thing she",
            "said was something about the elves and a song",
            "that could bring down a king. I'm worried sick.",
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
                done = listOf("Elena told me the elves have freed Prifddinas."),
                place = "East Ardougne",
                location = EAST_ARDOUGNE,
                talk = {
                    chatNpc(
                        "Father sent you? I'm fine - better than fine.",
                        "Lord Iorwerth has fallen and the elves have sung",
                        "their city back into the world.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                    chatNpc(
                        "The crystal singers owe you for helping me get",
                        "word out. Tell Father I'll be home soon.",
                        facialExpression = FacialExpression.HAPPY_TALKING,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.EDMOND,
                journal = listOf("I should tell ${red("Edmond")} that Elena is safe."),
                done = listOf("Edmond thanked me for finding Elena."),
                place = "East Ardougne",
                location = EAST_ARDOUGNE,
                talk = {
                    chatNpc(
                        "She's safe? Thank Saradomin. And the elves are",
                        "singing crystal again - the whole of Ardougne",
                        "will hear about this.",
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
            "Daero will tell you more. He's in the glider",
            "hangar, beneath the Grand Tree.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll stop Glough.", "Not right now.", title = "Start Monkey Madness II?") != 1) return false
        chatPlayer("I'll stop Glough, Your Majesty.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.DAERO,
                journal = listOf("I should speak to ${red("Daero")} in the ${red("glider hangar")}", "under the Grand Tree."),
                done = listOf("Daero sent me to Waydar on Crash Island."),
                place = "the glider hangar",
                location = GLIDER_HANGAR,
                talk = {
                    chatNpc(
                        "Glough's been building something on Ape Atoll -",
                        "demonic gorillas, if the pilots are right. Waydar",
                        "watches from Crash Island. Go to him.",
                        facialExpression = FacialExpression.WORRIED,
                    )
                    true
                },
            ),
            QuestStep(
                npc = Npcs.WAYDAR,
                journal = listOf("I should speak to ${red("Waydar")} on ${red("Crash Island")}."),
                done = listOf("With Waydar's help I stopped Glough's gorillas."),
                place = "Crash Island",
                location = CRASH_ISLAND,
                talk = {
                    chatNpc(
                        "There's a cavern under the crash site full of",
                        "his gorillas. Let's finish this together.",
                        facialExpression = FacialExpression.TOUGH,
                    )
                    messageBox("You storm the Crash Site Cavern with Waydar and put an end to Glough's plan.")
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
                        "Glough defeated, and Ape Atoll at peace! The",
                        "gnomes will sing of this for a generation.",
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
            QuestNpcPost(Npcs.EDMOND, Tile(2568, 3334), gg.rsmod.game.model.Direction.EAST, listOf("Ardougne is quieter these days, thank Saradomin.", "Elena sends her regards from Prifddinas.")),
            QuestNpcPost(Npcs.ELENA, Tile(2592, 3336), gg.rsmod.game.model.Direction.WEST, listOf("There's always another sickness to cure.", "Mind how you go.")),
            QuestNpcPost(Npcs.DAERO, Tile(2648, 4519), gg.rsmod.game.model.Direction.SOUTH, listOf("The gliders run on time again.", "Glough's gorillas won't trouble the hangar now.")),
            QuestNpcPost(Npcs.WAYDAR, Tile(2891, 2724), gg.rsmod.game.model.Direction.EAST, listOf("Crash Island is quiet now.", "I rather miss the excitement.")),
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

    fun completeListedQuests(player: Player) {
        COMPLETED.forEach { if (player.getVarp(it.questId) < 1) player.setVarp(it.questId, 1) }
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
            } else if (quest.startNpc == npc || quest.steps.getOrNull(stage - 2)?.npc == npc) {
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
        val first = quest.steps.first()
        if (first.location != null && first.place != null) with(quest) { task.offerTravel(first.place, first.location) }
    }

    private suspend fun runStep(task: QueueTask, quest: ShortQuest, stage: Int) {
        val player = task.player
        val step = quest.steps[stage - 1]
        if (!step.talk(task)) return
        // The conversation suspends: never advance a stage another path changed meanwhile.
        if (quest.stage(player) != stage) return
        if (stage == quest.steps.size) {
            FoundationRewards.complete(player, quest)
        } else {
            quest.setStage(player, stage + 1)
            player.message("Your quest journal has been updated: <col=5861e9>${quest.name}")
        }
    }

    private suspend fun runReminder(task: QueueTask, quest: ShortQuest, stage: Int) {
        val player = task.player
        val step = quest.steps[stage - 1]
        val previous = quest.steps.getOrNull(stage - 2)
        // The npc of the previous step hands its quest items out again when the player lost them.
        if (previous != null && previous.gives.isNotEmpty() && previous.gives.any { (item, _) -> !player.inventory.contains(item) && !player.bank.contains(item) }) {
            task.chatNpc("You've lost what I gave you? Here, take these again - and be careful this time.")
            previous.gives.forEach { (item, amount) -> if (!player.inventory.contains(item) && !player.bank.contains(item)) FoundationRewards.grant(player, item, amount) }
        }
        task.chatPlayer(*step.journal.map { it.replace(Regex("<[^>]+>"), "") }.toTypedArray(), facialExpression = FacialExpression.THINKING)
        if (step.location != null && step.place != null) with(quest) { task.offerTravel(step.place, step.location) }
    }

    /** The journal/stage summary the Quest Guide uses. */
    fun startPoint(quest: ShortQuest): Pair<String, Tile?> = quest.startPlace to quest.startLocation

    fun inProgress(player: Player, quest: ShortQuest): Boolean = quest.stage(player) in 1 until quest.completedValue

    fun started(player: Player, quest: Quest): Boolean = player.getCurrentStage(quest) > 0
}
