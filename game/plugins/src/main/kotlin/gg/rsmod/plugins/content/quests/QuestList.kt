package gg.rsmod.plugins.content.quests

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp

/**
 * How the revision-667 quest list reads one quest: clientscript 2193 has one case per list slot that returns 0 (not
 * started), 1 (in progress) or 2 (complete) from the quest's varp or varbit - usually through clientscript 2158
 * (`value < start` -> 0, `value < complete` -> 1, else 2). [read] is that exact rule, so the server knows which colour and
 * which part of the list the client will give the quest; [complete] writes the value that makes [read] return 2 and only
 * ever raises a value, never lowers one.
 */
class QuestProgress(val read: (Player) -> Int, val complete: (Player) -> Unit)

/** One row of the quest list: its slot (enum 2252 key), name, quest points and how the client reads its progress. */
class QuestListEntry(val slot: Int, val name: String, val points: Int, val progress: QuestProgress)

private fun range(value: Int, start: Int, complete: Int): Int =
    when {
        value < start -> 0
        value < complete -> 1
        else -> 2
    }

private fun varp(id: Int, complete: Int, start: Int = 1, write: Int = complete) =
    QuestProgress(
        read = { range(it.getVarp(id), start, complete) },
        complete = { if (it.getVarp(id) < complete) it.setVarp(id, write) },
    )

private fun varbit(id: Int, complete: Int, start: Int = 1) =
    QuestProgress(
        read = { range(it.getVarbit(id), start, complete) },
        complete = { if (it.getVarbit(id) < complete) it.setVarbit(id, complete) },
    )

/** Clientscript 2159 (slot 15): complete when either Shield of Arrav varp reached its end (7 or 4). */
private fun shieldOfArrav() =
    QuestProgress(
        read = {
            val a = it.getVarp(Varps.SHIELD_OF_ARRAV_PROGRESS_1)
            val b = it.getVarp(Varps.SHIELD_OF_ARRAV_PROGRESS_2)
            when {
                a == 0 && b == 0 -> 0
                a < 7 && b < 4 -> 1
                else -> 2
            }
        },
        complete = {
            if (it.getVarp(Varps.SHIELD_OF_ARRAV_PROGRESS_1) < 7) it.setVarp(Varps.SHIELD_OF_ARRAV_PROGRESS_1, 7)
            if (it.getVarp(Varps.SHIELD_OF_ARRAV_PROGRESS_2) < 4) it.setVarp(Varps.SHIELD_OF_ARRAV_PROGRESS_2, 4)
        },
    )

/** Slot 26: varp 399 above 6 is complete; varp 399 or varbit 203 set is in progress. */
private fun creatureOfFenkenstrain() =
    QuestProgress(
        read = {
            val stage = it.getVarp(Varps.CREATURE_OF_FENKENSTRAIN_PROGRESS)
            when {
                stage > 6 -> 2
                stage != 0 || it.getVarbit(203) != 0 -> 1
                else -> 0
            }
        },
        complete = { if (it.getVarp(Varps.CREATURE_OF_FENKENSTRAIN_PROGRESS) <= 6) it.setVarp(Varps.CREATURE_OF_FENKENSTRAIN_PROGRESS, 7) },
    )

/** Slot 37: bit 20 of varp 299 is complete, bit 1 in progress. */
private fun elementalWorkshopI() =
    QuestProgress(
        read = {
            val flags = it.getVarp(Varps.ELEMENTAL_WORKSHOP_I_PROGRESS)
            when {
                flags and (1 shl 20) != 0 -> 2
                flags and (1 shl 1) != 0 -> 1
                else -> 0
            }
        },
        complete = {
            val flags = it.getVarp(Varps.ELEMENTAL_WORKSHOP_I_PROGRESS)
            if (flags and (1 shl 20) == 0) it.setVarp(Varps.ELEMENTAL_WORKSHOP_I_PROGRESS, flags or (1 shl 20))
        },
    )

/** Slot 114: varp 161 at 10 is complete; bit 11 of varp 162 is in progress. */
private fun undergroundPass() =
    QuestProgress(
        read = {
            when {
                it.getVarp(Varps.UNDERGROUND_PASS_PROGRESS) == 10 -> 2
                it.getVarp(162) and (1 shl 11) != 0 -> 1
                else -> 0
            }
        },
        complete = { if (it.getVarp(Varps.UNDERGROUND_PASS_PROGRESS) != 10) it.setVarp(Varps.UNDERGROUND_PASS_PROGRESS, 10) },
    )

/**
 * Every row of the quest tab's list, in slot order: the 178 revision-667 quests (enum 2252) and the ten OSRS quests
 * QuestListCacheTool added (slots 185-194, varps 1910-1919).
 *
 * Sources (2026-09-27): the progress rule of each slot is clientscript 2193 of the 667 cache; quest points are the 667
 * cache's own quest configs (config archive 35, opcode 9 - Recipe for Disaster counts its nine subquests, 10 in all);
 * four quests the cache gives 0 use the RS Wiki value (owner 2026-09-27): Gunnar's Ground 5, Black Knights' Fortress 3,
 * The Chosen Commander 3, The Curse of Arrav 1. The OSRS quests use the OSRS Wiki (Mage Arena II is a miniquest, 0).
 * Priest in Peril writes 61, the value its own quest code ends on (the client counts 60 and up as complete).
 */
object QuestList {
    val ENTRIES: List<QuestListEntry> =
        listOf(
            QuestListEntry(1, "Cook's Assistant", 1, varp(Varps.COOKS_ASSISTANT_PROGRESS, complete = 2)),
            QuestListEntry(2, "Demon Slayer", 3, varbit(Varbits.DEMON_SLAYER_PROGRESS, complete = 3)),
            QuestListEntry(3, "Doric's Quest", 1, varp(Varps.DORICS_QUEST_PROGRESS, complete = 100)),
            QuestListEntry(4, "Dragon Slayer", 2, varp(Varps.DRAGON_SLAYER_PROGRESS, complete = 10)),
            QuestListEntry(5, "Ernest the Chicken", 4, varp(Varps.ERNEST_THE_CHICKEN_PROGRESS, complete = 3)),
            QuestListEntry(6, "Goblin Diplomacy", 5, varbit(Varbits.GOBLIN_DIPLOMACY_PROGRESS, complete = 6)),
            QuestListEntry(7, "Imp Catcher", 1, varp(Varps.IMP_CATCHER_PROGRESS, complete = 2)),
            QuestListEntry(8, "The Knight's Sword", 1, varp(Varps.THE_KNIGHTS_SWORD_PROGRESS, complete = 7)),
            QuestListEntry(9, "Pirate's Treasure", 2, varp(Varps.PIRATES_TREASURE_PROGRESS, complete = 4)),
            QuestListEntry(10, "Prince Ali Rescue", 3, varp(Varps.PRINCE_ALI_RESCUE_PROGRESS, complete = 110)),
            QuestListEntry(11, "The Restless Ghost", 1, varp(Varps.THE_RESTLESS_GHOST_PROGRESS, complete = 5)),
            QuestListEntry(12, "The Prisoner of Glouphrie", 1, varbit(Varbits.THE_PRISONER_OF_GLOUPHRIE_PROGRESS, complete = 120)),
            QuestListEntry(13, "Rune Mysteries", 1, varp(Varps.RUNE_MYSTERIES_PROGRESS, complete = 6)),
            QuestListEntry(14, "King of the Dwarves", 2, varbit(Varbits.KING_OF_THE_DWARVES_PROGRESS, complete = 90)),
            QuestListEntry(15, "Shield of Arrav", 1, shieldOfArrav()),
            QuestListEntry(16, "Vampyre Slayer", 3, varbit(Varbits.VAMPYRE_SLAYER_PROGRESS, complete = 3)),
            QuestListEntry(17, "Gunnar's Ground", 5, varbit(Varbits.GUNNARS_GROUND_PROGRESS, complete = 100)),  // RS Wiki (owner 2026-09-27); the 667 cache says 0
            QuestListEntry(18, "Animal Magnetism", 1, varbit(Varbits.ANIMAL_MAGNETISM_PROGRESS, complete = 240)),
            QuestListEntry(19, "Between a Rock...", 2, varbit(Varbits.BETWEEN_A_ROCK_PROGRESS, complete = 110)),
            QuestListEntry(20, "Big Chompy Bird Hunting", 2, varp(Varps.BIG_CHOMPY_BIRD_HUNTING_PROGRESS, complete = 65)),
            QuestListEntry(21, "Biohazard", 3, varp(Varps.BIOHAZARD_PROGRESS, complete = 16)),
            QuestListEntry(22, "Cabin Fever", 2, varp(Varps.CABIN_FEVER_PROGRESS, complete = 140)),
            QuestListEntry(23, "Clock Tower", 1, varp(Varps.CLOCK_TOWER_PROGRESS, complete = 8)),
            QuestListEntry(24, "Contact!", 1, varbit(Varbits.CONTACT_PROGRESS, complete = 130)),
            QuestListEntry(25, "Zogre Flesh Eaters", 1, varbit(Varbits.ZOGRE_FLESH_EATERS_PROGRESS, complete = 14)),
            QuestListEntry(26, "Creature of Fenkenstrain", 2, creatureOfFenkenstrain()),
            QuestListEntry(27, "Darkness of Hallowvale", 2, varbit(Varbits.DARKNESS_OF_HALLOWVALE_PROGRESS, complete = 320)),
            QuestListEntry(28, "Death to the Dorgeshuun", 1, varbit(Varbits.DEATH_TO_THE_DORGESHUUN_PROGRESS, complete = 13)),
            QuestListEntry(29, "Death Plateau", 1, varp(Varps.DEATH_PLATEAU_PROGRESS, complete = 80)),
            QuestListEntry(30, "Desert Treasure", 3, varbit(Varbits.DESERT_TREASURE_PROGRESS, complete = 15)),
            QuestListEntry(31, "Devious Minds", 1, varbit(Varbits.DEVIOUS_MINDS_PROGRESS, complete = 80)),
            QuestListEntry(32, "The Dig Site", 2, varp(Varps.THE_DIG_SITE_PROGRESS, complete = 9)),
            QuestListEntry(33, "Druidic Ritual", 4, varp(Varps.DRUIDIC_RITUAL_PROGRESS, complete = 4)),
            QuestListEntry(34, "Dwarf Cannon", 1, varp(Varps.DWARF_CANNON_PROGRESS, complete = 11)),
            QuestListEntry(35, "Eadgar's Ruse", 1, varp(Varps.EADGARS_RUSE_PROGRESS, complete = 110)),
            QuestListEntry(36, "Eagles' Peak", 2, varbit(Varbits.EAGLES_PEAK_PROGRESS, complete = 40)),
            QuestListEntry(37, "Elemental Workshop I", 1, elementalWorkshopI()),
            QuestListEntry(38, "Elemental Workshop II", 1, varbit(Varbits.ELEMENTAL_WORKSHOP_II_PROGRESS, complete = 11)),
            QuestListEntry(39, "Enakhra's Lament", 2, varbit(Varbits.ENAKHRAS_LAMENT_PROGRESS, complete = 70)),
            QuestListEntry(40, "Enlightened Journey", 1, varbit(Varbits.ELIGHTENED_JOURNEY_PROGRESS, complete = 200)),
            QuestListEntry(41, "The Eyes of Glouphrie", 2, varbit(Varbits.THE_EYES_OF_GLOUPHRIE_PROGRESS, complete = 60)),
            QuestListEntry(42, "Fairy Tale I - Growing Pains", 2, varbit(Varbits.FAIRY_TALE_I_PROGRESS, complete = 90)),
            QuestListEntry(43, "Fairy Tale II - Cure a Queen", 2, varbit(Varbits.FAIRY_TALE_II_PROGRESS, complete = 90)),
            QuestListEntry(44, "Family Crest", 1, varp(Varps.FAMILY_CREST_PROGRESS, complete = 11)),
            QuestListEntry(45, "The Feud", 1, varbit(Varbits.THE_FUED_PROGRESS, complete = 28)),
            QuestListEntry(46, "Fight Arena", 2, varp(Varps.FIGHT_ARENA_PROGRESS, complete = 14)),
            QuestListEntry(47, "Fishing Contest", 1, varp(Varps.FISHING_CONTEST_PROGRESS, complete = 5)),
            QuestListEntry(48, "Forgettable Tale...", 2, varbit(Varbits.FORGETTABLE_TALE_PROGRESS, complete = 140)),
            QuestListEntry(49, "The Fremennik Trials", 3, varp(Varps.THE_FREMENNIK_TRIALS_PROGRESS, complete = 10)),
            QuestListEntry(50, "Waterfall Quest", 1, varp(Varps.WATERFALL_QUEST_PROGRESS, complete = 10)),
            QuestListEntry(51, "Garden of Tranquillity", 2, varbit(Varbits.GARDEN_OF_TRANQUILITY_PROGRESS, complete = 60)),
            QuestListEntry(52, "Gertrude's Cat", 1, varp(Varps.GERTRUDES_CAT_PROGRESS, complete = 6)),
            QuestListEntry(53, "Ghosts Ahoy", 2, varbit(Varbits.GHOSTS_AHOY_PROGRESS, complete = 8)),
            QuestListEntry(54, "The Giant Dwarf", 2, varbit(Varbits.THE_GIANT_DWARF_PROGRESS, complete = 50)),
            QuestListEntry(55, "The Golem", 1, varbit(Varbits.THE_GOLEM_PROGRESS, complete = 10)),
            QuestListEntry(56, "The Grand Tree", 5, varp(Varps.THE_GRAND_TREE_PROGRESS, complete = 160)),
            QuestListEntry(57, "The Hand in the Sand", 1, varbit(Varbits.THE_HAND_IN_THE_SAND_PROGRESS, complete = 160)),
            QuestListEntry(58, "Haunted Mine", 2, varp(Varps.HAUNTED_MINE_PROGRESS, complete = 11)),
            QuestListEntry(59, "Hazeel Cult", 1, varp(Varps.HAZEEL_CULT_PROGRESS, complete = 9)),
            QuestListEntry(60, "Heroes' Quest", 1, varp(Varps.HEROES_QUEST_PROGRESS, complete = 15)),
            QuestListEntry(61, "Holy Grail", 2, varp(Varps.HOLY_GRAIL_PROGRESS, complete = 10)),
            QuestListEntry(62, "Horror from the Deep", 2, varbit(Varbits.HORROR_FROM_THE_DEEP_PROGRESS, complete = 10)),
            QuestListEntry(63, "Icthlarin's Little Helper", 2, varbit(Varbits.ICTHLARINS_LITTLE_HELPER_PROGRESS, complete = 26)),
            QuestListEntry(64, "In Aid of the Myreque", 2, varbit(Varbits.IN_AID_OF_THE_MYREQUE_PROGRESS, complete = 430)),
            QuestListEntry(65, "In Search of the Myreque", 2, varp(Varps.IN_SEARCH_OF_THE_MYREQUE_PROGRESS, complete = 105)),
            QuestListEntry(66, "Jungle Potion", 1, varp(Varps.JUNGLE_POTION_PROGRESS, complete = 12)),
            QuestListEntry(67, "Legends' Quest", 4, varp(Varps.LEGENDS_QUEST_PROGRESS, complete = 75)),
            QuestListEntry(68, "Lost City", 3, varp(Varps.LOST_CITY_PROGRESS, complete = 6)),
            QuestListEntry(69, "The Lost Tribe", 1, varbit(Varbits.THE_LOST_TRIBE_PROGRESS, complete = 11)),
            QuestListEntry(70, "Lunar Diplomacy", 2, varbit(Varbits.LUNAR_DIPLOMACY_PROGRESS, complete = 190)),
            QuestListEntry(71, "Making History", 3, varbit(Varbits.MAKING_HISTORY_PROGRESS, complete = 4)),
            QuestListEntry(72, "Merlin's Crystal", 6, varp(Varps.MERLINS_CRYSTAL_PROGRESS, complete = 7)),
            QuestListEntry(73, "Monkey Madness", 3, varp(Varps.MONKEY_MADNESS_PROGRESS, complete = 9, start = 1)),
            QuestListEntry(74, "Monk's Friend", 1, varp(Varps.MONKS_FRIEND_PROGRESS, complete = 80)),
            QuestListEntry(75, "Mountain Daughter", 2, varbit(Varbits.MOUNTAIN_DAUGHTER_PROGRESS, complete = 70)),
            QuestListEntry(76, "Mourning's Ends Part I", 2, varp(Varps.MOURNINGS_END_PART_I_PROGRESS, complete = 8)),
            QuestListEntry(77, "Mourning's Ends Part II", 2, varbit(Varbits.MOURNINGS_END_PART_II_PROGRESS, complete = 60, start = 9)),
            QuestListEntry(78, "Murder Mystery", 3, varp(Varps.MURDER_MYSTERY_PROGRESS, complete = 2)),
            QuestListEntry(79, "My Arm's Big Adventure", 1, varbit(Varbits.MY_ARMS_BIG_ADVENTURE_PROGRESS, complete = 320)),
            QuestListEntry(80, "Nature Spirit", 2, varp(Varps.NATURE_SPIRIT_PROGRESS, complete = 110)),
            QuestListEntry(81, "Observatory Quest", 2, varp(Varps.OBSERVATORY_QUEST_PROGRESS, complete = 7)),
            QuestListEntry(82, "One Small Favour", 2, varp(Varps.ONE_SMALL_FAVOUR_PROGRESS, complete = 280)),
            QuestListEntry(83, "Plague City", 1, varp(Varps.PLAGUE_CITY_PROGRESS, complete = 29)),
            QuestListEntry(84, "Priest in Peril", 1, varp(Varps.PRIEST_IN_PERIL_PROGRESS, complete = 60, write = 61)),
            QuestListEntry(85, "Rag and Bone Man", 2, varbit(Varbits.RAG_AND_BONE_MAN_PROGRESS, complete = 4)),
            QuestListEntry(86, "Ratcatchers", 2, varbit(Varbits.RATCATCHERS_PROGRESS, complete = 127)),
            QuestListEntry(87, "Recipe for Disaster", 10, varbit(Varbits.RECIPE_FOR_DISASTER_PROGRESS, complete = 5)),
            QuestListEntry(88, "Recruitment Drive", 1, varbit(Varbits.RECRUITMENT_DRIVE_PROGRESS, complete = 2)),
            QuestListEntry(89, "Regicide", 3, varp(Varps.REGICIDE_PROGRESS, complete = 15)),
            QuestListEntry(90, "Roving Elves", 1, varp(Varps.ROVING_ELVES_PROGRESS, complete = 6)),
            QuestListEntry(91, "Royal Trouble", 1, varbit(Varbits.ROYAL_TROUBLE_PROGRESS, complete = 30)),
            QuestListEntry(92, "Rum Deal", 2, varp(Varps.RUM_DEAL_PROGRESS, complete = 19)),
            QuestListEntry(93, "Scorpion Catcher", 1, varp(Varps.SCORPION_CATCHER_PROGRESS, complete = 6)),
            QuestListEntry(94, "Sea Slug", 1, varp(Varps.SEA_SLUG_PROGRESS, complete = 12)),
            QuestListEntry(95, "The Slug Menace", 1, varbit(Varbits.THE_SLUG_MENACE_PROGRESS, complete = 14)),
            QuestListEntry(96, "Shades of Mort'ton", 3, varp(Varps.SHADES_OF_MORTON_PROGRESS, complete = 85)),
            QuestListEntry(97, "Shadow of the Storm", 1, varbit(Varbits.SHADOW_OF_THE_STORM_PROGRESS, complete = 125)),
            QuestListEntry(98, "Sheep Herder", 4, varp(Varps.SHEEP_HERDER_PROGRESS, complete = 3)),
            QuestListEntry(99, "Shilo Village", 2, varp(Varps.SHILO_VILLAGE_PROGRESS, complete = 15)),
            QuestListEntry(100, "A Soul's Bane", 1, varbit(Varbits.A_SOULS_BANE_PROGRESS, complete = 13)),
            QuestListEntry(101, "Spirits of the Elid", 2, varbit(Varbits.SPIRITS_OF_THE_ELID_PROGRESS, complete = 60)),
            QuestListEntry(102, "Swan Song", 2, varbit(Varbits.SWAN_SONG_PROGRESS, complete = 200)),
            QuestListEntry(103, "Tai Bwo Wannai Trio", 2, varp(Varps.TAI_BWO_WANNAI_TRIO_PROGRESS, complete = 6, start = 3)),
            QuestListEntry(104, "A Tail of Two Cats", 2, varbit(Varbits.A_TAIL_OF_TWO_CATS_PROGRESS, complete = 70)),
            QuestListEntry(105, "Tears of Guthix", 1, varbit(Varbits.TEARS_OF_GUTHIXS_PROGRESS, complete = 2)),
            QuestListEntry(106, "Temple of Ikov", 1, varp(Varps.TEMPLE_OF_IKOV_PROGRESS, complete = 80)),
            QuestListEntry(107, "Throne of Miscellania", 1, varp(Varps.THRONE_OF_MISCELLANIA_PROGRESS, complete = 100)),
            QuestListEntry(108, "The Tourist Trap", 2, varp(Varps.THE_TOURIST_TRAP_PROGRESS, complete = 30)),
            QuestListEntry(109, "Witch's House", 4, varp(Varps.WITCHS_HOUSE_PROGRESS, complete = 7)),
            QuestListEntry(110, "Tree Gnome Village", 2, varp(Varps.TREE_GNOME_VILLAGE_PROGRESS, complete = 9)),
            QuestListEntry(111, "Tribal Totem", 1, varp(Varps.TRIBAL_TOTEM_PROGRESS, complete = 5)),
            QuestListEntry(112, "Troll Romance", 2, varp(Varps.TROLL_ROMANCE_PROGRESS, complete = 45)),
            QuestListEntry(113, "Troll Stronghold", 1, varp(Varps.TROLL_STRONGHOLD_PROGRESS, complete = 50)),
            QuestListEntry(114, "Underground Pass", 5, undergroundPass()),
            QuestListEntry(115, "Wanted!", 1, varbit(Varbits.WANTED_PROGRESS, complete = 11)),
            QuestListEntry(116, "Watchtower", 4, varp(Varps.WATCHTOWER_PROGRESS, complete = 13)),
            QuestListEntry(117, "Cold War", 1, varbit(Varbits.COLD_WAR_PROGRESS, complete = 135)),
            QuestListEntry(118, "The Fremennik Isles", 1, varbit(Varbits.THE_FREMENNIK_ISLES_PROGRESS, complete = 340)),
            QuestListEntry(119, "Tower of Life", 2, varbit(Varbits.TOWER_OF_LIFE_PROGRESS, complete = 18)),
            QuestListEntry(120, "The Great Brain Robbery", 2, varp(Varps.THE_GREAT_BRAIN_ROBBERY_PROGRESS, complete = 130)),
            QuestListEntry(121, "What Lies Below", 1, varbit(Varbits.WHAT_LIES_BELOW_PROGRESS, complete = 150)),
            QuestListEntry(122, "Olaf's Quest", 1, varbit(Varbits.OLAFS_QUEST_PROGRESS, complete = 80)),
            QuestListEntry(123, "Another Slice of H.A.M.", 1, varbit(Varbits.ANOTHER_SLICE_OF_HAM_PROGRESS, complete = 11)),
            QuestListEntry(124, "Dream Mentor", 2, varbit(Varbits.DREAM_MENTOR_PROGRESS, complete = 28)),
            QuestListEntry(125, "Grim Tales", 1, varbit(Varbits.GRIM_TALES_PROGRESS, complete = 60)),
            QuestListEntry(126, "King's Ransom", 1, varbit(Varbits.KINGS_RANSOM_PROGRESS, complete = 90)),
            QuestListEntry(127, "The Path of Glouphrie", 1, varbit(Varbits.THE_PATH_OF_GLOUPHRIE_PROGRESS, complete = 200)),
            QuestListEntry(128, "Back to my Roots", 1, varbit(Varbits.BACK_TO_MY_ROOTS_PROGRESS, complete = 65)),
            QuestListEntry(129, "Land of the Goblins", 1, varbit(Varbits.LAND_OF_THE_GOBLINS_PROGRESS, complete = 18)),
            QuestListEntry(130, "Dealing with Scabaras", 1, varbit(Varbits.DEALING_WITH_SCABARAS_PROGRESS, complete = 700)),
            QuestListEntry(131, "Wolf Whistle", 1, varbit(Varbits.WOLF_WHISTLE_PROGRESS, complete = 110)),
            QuestListEntry(132, "As a First Resort", 1, varbit(Varbits.AS_A_FIRST_RESORT_PROGRESS, complete = 200, start = 10)),
            QuestListEntry(133, "Catapult Construction", 2, varbit(Varbits.CATAPULT_CONSTRUCTION_PROGRESS, complete = 60)),
            QuestListEntry(134, "Kennith's Concerns", 1, varbit(Varbits.KENNITHS_CONCERNS_PROGRESS, complete = 100)),
            QuestListEntry(135, "Legacy of Seergaze", 2, varbit(Varbits.LEGACY_OF_SEERGAZE_PROGRESS, complete = 500)),
            QuestListEntry(136, "Perils of Ice Mountain", 1, varbit(Varbits.PERILS_OF_ICE_MOUNTAIN_PROGRESS, complete = 150)),
            QuestListEntry(137, "TokTz-Ket-Dill", 1, varbit(Varbits.TOKTZ_KET_DILL_PROGRESS, complete = 63)),
            QuestListEntry(138, "Smoking Kills", 1, varbit(Varbits.SMOKING_KILLS_PROGRESS, complete = 250)),
            QuestListEntry(139, "Rocking Out", 2, varbit(Varbits.ROCKING_OUT_PROGRESS, complete = 100)),
            QuestListEntry(140, "Spirit of Summer", 1, varbit(Varbits.SPIRIT_OF_SUMMER_PROGRESS, complete = 80)),
            QuestListEntry(141, "Meeting History", 1, varbit(Varbits.MEETING_HISTORY_PROGRESS, complete = 20)),
            QuestListEntry(142, "All Fired Up", 1, varbit(Varbits.ALL_FIRED_UP_PROGRESS, complete = 90)),
            QuestListEntry(143, "Summer's End", 1, varbit(Varbits.SUMMERS_END_PROGRESS, complete = 35)),
            QuestListEntry(144, "Defender of Varrock", 2, varbit(Varbits.DEFENDER_OF_VARROCK_PROGRESS, complete = 250)),
            QuestListEntry(145, "While Guthix Sleeps", 5, varbit(Varbits.WHILE_GUTHIX_SLEEPS_PROGRESS, complete = 910)),
            QuestListEntry(146, "In Pyre Need", 1, varbit(Varbits.IN_PYRE_NEED_PROGRESS, complete = 30)),
            QuestListEntry(147, "Unstable Foundations", 1, varp(Varps.UNSTABLE_FOUNDATIONS_PROGRESS, complete = 1000)),
            QuestListEntry(148, "Myths of the White Lands", 2, varbit(Varbits.MYTHS_OF_THE_WHITE_LANDS_PROGRESS, complete = 60)),
            QuestListEntry(149, "Glorious Memories", 1, varbit(Varbits.GLORIOUS_MEMORIES_PROGRESS, complete = 45, start = 3)),
            QuestListEntry(150, "The Tale of the Muspah", 1, varbit(Varbits.THE_TALE_OF_THE_MUSPAH_PROGRESS, complete = 250, start = 10)),
            QuestListEntry(151, "Hunt for Red Raktuber", 1, varbit(Varbits.HUNT_FOR_RED_RAKTUBER_PROGRESS, complete = 140, start = 2)),
            QuestListEntry(152, "The Chosen Commander", 3, varbit(Varbits.THE_CHOSEN_COMMANDER_PROGRESS, complete = 110)),  // RS Wiki (owner 2026-09-27); the 667 cache says 0
            QuestListEntry(153, "Swept Away", 2, varbit(Varbits.SWEPT_AWAY_PROGRESS, complete = 50)),
            QuestListEntry(154, "Fur 'n' Seek", 2, varbit(Varbits.FUN_N_SEEK_PROGRESS, complete = 5, start = 2)),
            QuestListEntry(155, "Missing My Mummy", 1, varbit(Varbits.MISSING_MY_MUMMY_PROGRESS, complete = 60)),
            QuestListEntry(156, "The Curse of Arrav", 1, varbit(Varbits.THE_CURSE_OF_ARRAV_PROGRESS, complete = 240)),  // RS Wiki (owner 2026-09-27); the 667 cache says 0
            QuestListEntry(157, "The Temple at Senntisten", 2, varbit(Varbits.THE_TEMPLE_AT_SENNTISTEN_PROGRESS, complete = 90)),
            QuestListEntry(158, "Fairy Tale III - Orks Rift", 2, varbit(Varbits.FAIRY_TALE_III_PROGRESS, complete = 180, start = 10)),
            QuestListEntry(159, "Black Knights' Fortress", 3, varp(Varps.BLACK_KNIGHTS_FORTRESS_PROGRESS, complete = 4)),  // RS Wiki (owner 2026-09-27); the 667 cache says 0
            QuestListEntry(160, "Forgiveness of a Chaos Dwarf", 2, varbit(Varbits.FORGIVENESS_OF_A_CHAOS_DWARF_PROGRESS, complete = 90)),
            QuestListEntry(161, "Within the Light", 2, varbit(Varbits.WITHIN_THE_LIGHT_PROGRESS, complete = 46)),
            QuestListEntry(162, "Nomad's Requiem", 3, varbit(Varbits.NOMADS_REQUIEM_PROGRESS, complete = 12)),
            QuestListEntry(163, "Blood Runs Deep", 2, varbit(Varbits.BLOOD_RUNS_DEEP_PROGRESS, complete = 147, start = 3)),
            QuestListEntry(165, "Rune Mechanics", 1, varbit(Varbits.RUNE_MECHANICS_PROGRESS, complete = 35)),
            QuestListEntry(167, "Buyers and Cellars", 1, varbit(Varbits.BUYERS_AND_CELLARS_PROGRESS, complete = 30)),
            QuestListEntry(168, "Love Story", 2, varbit(Varbits.LOVE_STORY_PROGRESS, complete = 150)),
            QuestListEntry(170, "The Blood Pact", 1, varbit(Varbits.THE_BLOOD_PACT_PROGRESS, complete = 60)),
            QuestListEntry(171, "Quiet Before the Swarm", 1, varbit(Varbits.QUIET_BEFORE_THE_STORM_PROGRESS, complete = 90)),
            QuestListEntry(172, "Elemental Workshop III", 1, varbit(Varbits.ELEMENTAL_WORKSHOP_III_PROGRESS, complete = 5)),
            QuestListEntry(173, "A Void Dance", 1, varbit(Varbits.A_VOID_DANCE_PROGRESS, complete = 63, start = 2)),
            QuestListEntry(174, "The Void Stares Back", 1, varbit(Varbits.THE_VOID_STARES_BACK_PROGRESS, complete = 40)),
            QuestListEntry(176, "Ritual of the Mahjarrat", 3, varbit(Varbits.RITUAL_OF_THE_MAHJARRAT_PROGRESS, complete = 400)),
            QuestListEntry(179, "Do No Evil", 1, varbit(Varbits.DO_NO_EVIL_PROGRESS, complete = 315, start = 10)),
            QuestListEntry(180, "Elemental Workshop IV", 1, varbit(Varbits.ELEMENTAL_WORKSHOP_IV_PROGRESS, complete = 9)),
            QuestListEntry(181, "A Clockwork Syringe", 1, varbit(Varbits.A_CLOCKWORK_SYRINGE_PROGRESS, complete = 210, start = 10)),
            QuestListEntry(182, "Deadliest Catch", 1, varbit(Varbits.DEADLIEST_CATCH_PROGRESS, complete = 50)),
            QuestListEntry(183, "Salt in the Wound", 2, varbit(Varbits.SALT_IN_THE_WOUND_PROGRESS, complete = 250, start = 5)),
            QuestListEntry(184, "The Branches of Darkmeyer", 2, varbit(Varbits.THE_BRANCHES_OF_DARKMEYER_PROGRESS, complete = 170)),
            QuestListEntry(185, "Dragon Slayer II", 5, varp(1910, complete = 10)),
            QuestListEntry(186, "Song of the Elves", 4, varp(1911, complete = 10)),
            QuestListEntry(187, "Desert Treasure II", 5, varp(1912, complete = 10)),
            QuestListEntry(188, "Monkey Madness II", 4, varp(1913, complete = 10)),
            QuestListEntry(189, "Sins of the Father", 2, varp(1914, complete = 1)),
            QuestListEntry(190, "Children of the Sun", 1, varp(1915, complete = 1)),
            QuestListEntry(191, "Secrets of the North", 2, varp(1916, complete = 1)),
            QuestListEntry(192, "Bone Voyage", 1, varp(1917, complete = 1)),
            QuestListEntry(193, "Mage Arena II", 0, varp(1918, complete = 1)),
            QuestListEntry(194, "Beneath Cursed Sands", 2, varp(1919, complete = 1)),
        )

    private val bySlot = ENTRIES.associateBy { it.slot }

    fun entry(slot: Int): QuestListEntry? = bySlot[slot]
}