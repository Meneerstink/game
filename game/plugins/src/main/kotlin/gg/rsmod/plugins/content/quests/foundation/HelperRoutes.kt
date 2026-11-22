package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.EXRTA_FINE_FLOUR
import gg.rsmod.game.model.attr.gaveHarlowBeer
import gg.rsmod.game.model.attr.killedCountDraynor
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.content.areas.karamja.Luthas
import gg.rsmod.plugins.content.areas.portsarim.Wydin
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.cfg.Varps

/**
 * PROJECT48 O1-O6: the Quest Helper for the six old full 2011Scape quests (quests\impl, class Quest). They are NOT rebuilt: a [RouteQuest]
 * is a read-only mirror that reads the OLD quest's own varp stage (same questId, slot and stage count) and has one step per stage with
 * the npc / tile the player must go to next, so the arrow, map icon and side panel work. Never added to [FoundationQuests.SHORT] (no
 * Talk-to actions, no rewards); the helper looks them up through [FoundationQuests.HELPERS]. A stage the quest skips (Rune Mysteries goes
 * 1, 3, 5) repeats the next target. Tiles that are not a spawn of ours are estimates (dossier BRACKET-QUESTS).
 */
class RouteQuest(
    name: String,
    questId: Int,
    slot: Int,
    stages: Int,
    startNpc: Int,
    startNpcName: String,
    startPlace: String,
    startLocation: Tile?,
    override val needs: QuestRequirements,
    routes: List<QuestStep>,
    usesVarbits: Boolean = false,
    /** O29: the step for what the player has done inside a stage (null = the stage's own step). */
    private val pick: ((Player, Int) -> QuestStep?)? = null,
) : ShortQuest(
        name = name,
        startPoint = "Speak to $startNpcName at $startPlace.",
        rewards = "See the quest journal.",
        pointReward = 0,
        questId = questId,
        slot = slot,
        completedValue = stages,
        usesVarbits = usesVarbits,
        startNpc = startNpc,
        startNpcName = startNpcName,
        startPlace = startPlace,
        startLocation = startLocation,
    ) {
    override val steps: List<QuestStep> = routes

    override fun helperStep(player: Player, stage: Int): QuestStep? = pick?.invoke(player, stage) ?: super.helperStep(player, stage)

    override val reward = QuestReward(icon = Items.COINS_995, lines = listOf("See the quest journal."))

    // Never used: the old quest's own conversations start and run the quest.
    override suspend fun QueueTask.intro(): Boolean = false
}

object HelperRoutes {
    private fun step(
        npc: Int,
        line: String,
        place: String,
        at: Tile,
    ) = QuestStep(npc = npc, journal = listOf(line), done = listOf(line), place = place, location = at, talk = { false })

    val RUNE_MYSTERIES: RouteQuest by lazy {
        val sedridor = step(Npcs.SEDRIDOR, "Take the Strange talisman to Sedridor in the Wizards' Tower basement.", "the Wizards' Tower basement", Tile(3105, 9569, 0))
        val aubury = step(Npcs.AUBURY, "Take the package to Aubury in his Rune Shop in south-east Varrock.", "south-east Varrock", Tile(3253, 3403, 0))
        val back = step(Npcs.SEDRIDOR, "Take Aubury's research notes back to Sedridor in the Wizards' Tower basement.", "the Wizards' Tower basement", Tile(3105, 9569, 0))
        RouteQuest(
            "Rune Mysteries", Varps.RUNE_MYSTERIES_PROGRESS, 13, 6, Npcs.DUKE_HORACIO, "Duke Horacio", "Lumbridge Castle", Tile(3212, 3220, 1), QuestRequirements.NONE,
            listOf(sedridor, sedridor, aubury, aubury, back),
        )
    }

    val DRUIDIC_RITUAL: RouteQuest by lazy {
        val sanfew = step(Npcs.SANFEW, "Speak to Sanfew in the village to the south of the stone circle.", "Taverley", Tile(2899, 3430, 1))
        val cauldron = step(-1, "Dip the raw meats in the Cauldron of Thunder in the dungeon south of Taverley.", "the dungeon south of Taverley", Tile(2892, 9830, 0))
        val kaqemeex = step(Npcs.KAQEMEEX, "Speak to Kaqemeex again and claim your reward.", "the Druids' Circle, Taverley", Tile(2925, 3485, 0))
        RouteQuest(
            "Druidic Ritual", Varps.DRUIDIC_RITUAL_PROGRESS, 33, 4, Npcs.KAQEMEEX, "Kaqemeex", "the Druids' Circle, Taverley", Tile(2925, 3485, 0), QuestRequirements.NONE,
            listOf(sanfew, cauldron, kaqemeex),
        )
    }

    val PRIEST_IN_PERIL: RouteQuest by lazy {
        // O29: one step per stage (PriestInPeril.kt journal + temple_of_saradomin / drezel plugins; tiles = findloc 22-11).
        val door = step(-1, "Knock at the large door of the temple east of Varrock and speak to Drezel through it.", "the Paterdomus temple east of Varrock", Tile(3406, 3489, 0))
        val dog = step(-1, "Go down the trapdoor north of the temple and kill the dog (the Temple Guardian) in the mausoleum.", "the mausoleum north of Paterdomus", Tile(3405, 3507, 0))
        val king = step(Npcs.KING_ROALD, "Tell King Roald in Varrock Palace that you killed the dog.", "Varrock Palace", Tile(3220, 3471, 0))
        val upstairs = step(Npcs.DREZEL_1049, "Return to the temple, go to the top floor (the monks of Zamorak attack) and find the real Drezel.", "the Paterdomus temple, top floor", Tile(3415, 3489, 2))
        val monks = step(-1, "Kill a monk of Zamorak in the temple for the golden key.", "the Paterdomus temple", Tile(3410, 3489, 0))
        val monument = step(-1, "Use the golden key on the monument in the temple's underground that shows the iron key to swap them.", "under Paterdomus", Tile(3427, 9894, 0))
        val cell = step(Npcs.DREZEL_1049, "Use the iron key on Drezel's cell door on the top floor of the temple.", "the Paterdomus temple, top floor", Tile(3415, 3489, 2))
        val well = step(-1, "Fill a bucket at the well in the temple's underground (Salve water).", "under Paterdomus", Tile(3423, 9890, 0))
        val bless = step(Npcs.DREZEL_1049, "Ask Drezel to bless the bucket of Salve water.", "the Paterdomus temple, top floor", Tile(3415, 3489, 2))
        val coffin = step(-1, "Pour the blessed water over the vampyre's coffin next to Drezel's cell.", "the Paterdomus temple, top floor", Tile(3410, 3488, 2))
        val salve = step(Npcs.DREZEL_7707, "Meet Drezel by the Salve in the temple's underground.", "under Paterdomus, by the Salve", Tile(3440, 9895, 0))
        val essence = step(Npcs.DREZEL_7707, "Bring Drezel the rune essence (or pure essence) he asks for to purify the Salve.", "under Paterdomus, by the Salve", Tile(3440, 9895, 0))
        RouteQuest(
            "Priest in Peril", Varps.PRIEST_IN_PERIL_PROGRESS, 84, 9, Npcs.KING_ROALD, "King Roald", "Varrock Palace", Tile(3220, 3471, 0),
            QuestRequirements(extra = listOf("Must be able to defeat a level 30 Temple Guardian.")),
            listOf(door, dog, king, upstairs, monks, coffin, salve, essence),
            pick = { p, stage ->
                when (stage) {
                    5 -> if (has(p, Items.IRON_KEY)) cell else if (has(p, Items.GOLDEN_KEY)) monument else monks
                    6 -> if (has(p, Items.BUCKET_OF_WATER_2954)) coffin else if (has(p, Items.BUCKET_OF_WATER_2953)) bless else well
                    else -> null
                }
            },
        )
    }

    val RESTLESS_GHOST: RouteQuest by lazy {
        val urhney = step(Npcs.FATHER_URHNEY, "Find Father Urhney in his shack in the south of Lumbridge Swamp.", "Lumbridge Swamp", Tile(3207, 3149, 0))
        val ghost = step(-1, "Talk to the ghost in the crypt south of Lumbridge church (wear the Ghostspeak amulet).", "the crypt south of Lumbridge church", Tile(3247, 3193, 0))
        val skull = step(-1, "Search for the skull in the Wizards' Tower basement.", "the Wizards' Tower basement", Tile(3112, 9566, 0))
        val back = step(-1, "Return the skull to the ghost in the graveyard crypt.", "the crypt south of Lumbridge church", Tile(3247, 3193, 0))
        RouteQuest(
            "The Restless Ghost", Varps.THE_RESTLESS_GHOST_PROGRESS, 11, 5, Npcs.FATHER_AERECK, "Father Aereck", "the church in Lumbridge", Tile(3244, 3205, 0), QuestRequirements.NONE,
            listOf(urhney, ghost, skull, back),
        )
    }

    val LOST_CITY: RouteQuest by lazy {
        val swamp = step(-1, "Find the leprechaun hiding in a tree in Lumbridge Swamp.", "Lumbridge Swamp", Tile(3160, 3175, 0))
        val entrana = step(-1, "Find the Dramen tree in the dungeon on Entrana and defeat the Tree spirit.", "the dungeon on Entrana", Tile(2819, 3373, 0))
        val shed = step(-1, "Enter the shed in Lumbridge Swamp with the Dramen staff to reach Zanaris.", "Lumbridge Swamp shed", Tile(3202, 3169, 0))
        RouteQuest(
            "Lost City", Varps.LOST_CITY_PROGRESS, 68, 6, 0, "an adventurer", "Lumbridge Swamp", Tile(3160, 3175, 0),
            QuestRequirements(skills = listOf(Skills.CRAFTING to 31, Skills.WOODCUTTING to 36), boostable = setOf(Skills.CRAFTING, Skills.WOODCUTTING), extra = listOf("Must be able to defeat a level 101 Tree spirit.")),
            listOf(swamp, swamp, entrana, entrana, shed),
        )
    }

    val PRINCE_ALI_RESCUE: RouteQuest by lazy {
        val osman = step(Npcs.OSMAN, "Speak to Osman outside the Al Kharid Palace.", "Al Kharid", Tile(3286, 3180, 0))
        val leela = step(-1, "Speak to Leela in Draynor Village, near the jail east of the village.", "Draynor Village", Tile(3114, 3260, 0))
        val ned = step(Npcs.NED, "Make the disguise: Ned in Draynor Village makes the wig.", "Draynor Village", Tile(3098, 3257, 0))
        val joe = step(Npcs.JOE_916, "Deal with the guard at the jail: Joe loves beer.", "the jail east of Draynor Village", Tile(3122, 3245, 0))
        val keli = step(Npcs.LADY_KELI, "Tie up Lady Keli in the jail.", "the jail east of Draynor Village", Tile(3128, 3245, 0))
        val ali = step(Npcs.PRINCE_ALI, "Free Prince Ali.", "the jail east of Draynor Village", Tile(3123, 3241, 0))
        RouteQuest(
            "Prince Ali Rescue", Varps.PRINCE_ALI_RESCUE_PROGRESS, 10, 8, Npcs.HASSAN, "Hassan", "the Al Kharid Palace", Tile(3302, 3163, 0), QuestRequirements.NONE,
            listOf(osman, leela, ned, ned, joe, keli, ali),
        )
    }

    // ---- O29 (night run 22-11): the other six old quests. Tiles = our spawns / objects (findloc 22-11), text = helper guidance in
    // the style of the RuneLite Quest Helper (not quest dialogue). Steps that depend on what the player carries use RouteQuest.pick.

    private fun has(player: Player, item: Int, amount: Int = 1) = player.inventory.getItemCount(item) >= amount

    val COOKS_ASSISTANT: RouteQuest by lazy {
        val cook = step(Npcs.COOK, "Bring the Cook a super large egg, a pot of extra fine flour and a bucket of top-quality milk.", "the Lumbridge Castle kitchen", Tile(3209, 3215, 0))
        val egg = step(-1, "Pick up the super large egg in the chicken pen north of Lumbridge.", "the chicken farm north of Lumbridge", Tile(3231, 3290, 0))
        val milk = step(-1, "Milk the prized dairy cow in the cow field north of Lumbridge with a bucket for top-quality milk.", "the cow field north of Lumbridge", Tile(3264, 3277, 0))
        val millie = step(Npcs.MILLIE_MILLER, "Ask Millie Miller in Mill Lane Mill about extra fine flour.", "Mill Lane Mill", Tile(3169, 3306, 0))
        val wheat = step(-1, "Pick some wheat in the field next to Mill Lane Mill.", "the wheat field by Mill Lane Mill", Tile(3158, 3299, 0))
        val hopper = step(-1, "Put the grain in the hopper on the top floor of the mill, then operate the hopper controls.", "Mill Lane Mill, top floor", Tile(3166, 3307, 2))
        val bin = step(-1, "Use an empty pot on the flour bin on the ground floor of the mill.", "Mill Lane Mill", Tile(3166, 3306, 0))
        RouteQuest(
            "Cook's Assistant", Varps.COOKS_ASSISTANT_PROGRESS, 1, 2, Npcs.COOK, "the Cook", "the Lumbridge Castle kitchen", Tile(3209, 3215, 0), QuestRequirements.NONE,
            listOf(cook),
            pick = { p, _ ->
                when {
                    !has(p, Items.SUPER_LARGE_EGG) -> egg
                    !has(p, Items.TOPQUALITY_MILK) -> milk
                    has(p, Items.EXTRA_FINE_FLOUR) -> cook
                    p.getVarp(695) > 0 -> bin
                    p.attr[EXRTA_FINE_FLOUR] != true -> millie
                    has(p, Items.GRAIN) -> hopper
                    else -> wheat
                }
            },
        )
    }

    val DORICS_QUEST: RouteQuest by lazy {
        val doric = step(Npcs.DORIC, "Bring Doric 6 clay, 4 copper ore and 2 iron ore.", "Doric's house north of Falador", Tile(2953, 3450, 0))
        val mine = step(-1, "Mine 6 clay, 4 copper ore and 2 iron ore (Rimmington mine has all three), or buy them.", "Rimmington mine", Tile(2977, 3240, 0))
        RouteQuest(
            "Doric's Quest", Varps.DORICS_QUEST_PROGRESS, 3, 2, Npcs.DORIC, "Doric", "his house north of Falador", Tile(2953, 3450, 0), QuestRequirements.NONE,
            listOf(doric),
            pick = { p, _ -> if (has(p, Items.CLAY, 6) && has(p, Items.COPPER_ORE, 4) && has(p, Items.IRON_ORE, 2)) doric else mine },
        )
    }

    val IMP_CATCHER: RouteQuest by lazy {
        val mizgog = step(Npcs.WIZARD_MIZGOG, "Take the black, red, white and yellow beads to Wizard Mizgog on the top floor of the Wizards' Tower.", "the Wizards' Tower, top floor", Tile(3103, 3163, 2))
        val beads = listOf(Items.BLACK_BEAD to "black", Items.RED_BEAD to "red", Items.WHITE_BEAD to "white", Items.YELLOW_BEAD to "yellow")
        RouteQuest(
            "Imp Catcher", Varps.IMP_CATCHER_PROGRESS, 7, 2, Npcs.WIZARD_MIZGOG, "Wizard Mizgog", "the Wizards' Tower", Tile(3103, 3163, 2), QuestRequirements.NONE,
            listOf(mizgog),
            pick = { p, _ ->
                val missing = beads.filter { !has(p, it.first) }.map { it.second }
                if (missing.isEmpty()) {
                    mizgog
                } else {
                    QuestStep(
                        npc = Npcs.IMP,
                        journal = listOf("Kill imps for the beads you still need: ${missing.joinToString(", ")}. Imps are found all over the kingdom."),
                        done = listOf("Collect the beads from imps."),
                        place = "anywhere imps roam",
                        talk = { false },
                    )
                }
            },
        )
    }

    val PIRATES_TREASURE: RouteQuest by lazy {
        val frank = step(Npcs.REDBEARD_FRANK, "Give the Karamjan rum to Redbeard Frank in Port Sarim.", "Port Sarim", Tile(3053, 3249, 0))
        val zambo = step(Npcs.ZAMBO, "Buy a Karamjan rum from Zambo at the Karamja Spirits bar in Musa Point.", "Musa Point, Karamja", Tile(2925, 3143, 0))
        val job = step(Npcs.LUTHAS, "Ask Luthas for a job on the banana plantation so you can smuggle the rum past the customs officers.", "the Karamja banana plantation", Tile(2939, 3156, 0))
        val hide = step(-1, "Use the Karamjan rum on the crate by the plantation to hide it.", "the Karamja banana plantation", Tile(2943, 3151, 0))
        val bananas = step(-1, "Fill the crate with 10 bananas from the trees on the plantation.", "the Karamja banana plantation", Tile(2943, 3151, 0))
        val ship = step(Npcs.LUTHAS, "Tell Luthas the crate is full so it is shipped to Wydin's store in Port Sarim.", "the Karamja banana plantation", Tile(2939, 3156, 0))
        val wydin = step(Npcs.WYDIN, "Ask Wydin for a job (wear a white apron) to get into the back room of his food store.", "Wydin's food store, Port Sarim", Tile(3013, 3203, 0))
        val crate = step(-1, "Search the crate in the back room of Wydin's store for your rum.", "Wydin's food store, Port Sarim", Tile(3009, 3207, 0))
        val chest = step(-1, "Open the chest upstairs in the Blue Moon Inn in Varrock with the chest key.", "the Blue Moon Inn, Varrock (first floor)", Tile(3219, 3396, 1))
        val dig = step(-1, "Dig with a spade by the red flowers in Falador Park (the gardener will attack you).", "Falador Park", Tile(2999, 3383, 0))
        RouteQuest(
            "Pirate's Treasure", Varps.PIRATES_TREASURE_PROGRESS, 9, 4, Npcs.REDBEARD_FRANK, "Redbeard Frank", "Port Sarim", Tile(3053, 3249, 0), QuestRequirements.NONE,
            listOf(zambo, chest, dig),
            pick = { p, stage ->
                if (stage != 1) {
                    null
                } else {
                    val onKaramja = p.tile.x in 2880..2970 && p.tile.z in 3130..3190
                    when {
                        has(p, Items.KARAMJAN_RUM) && !onKaramja -> frank
                        p.attr[Wydin.RUM_STASHED_ATTR] == true -> if (p.attr[Wydin.EMPLOYED_ATTR] == true) crate else wydin
                        p.attr[Luthas.RUM_STASHED_ATTR] == true -> if ((p.attr[Luthas.CRATED_BANANAS_ATTR] ?: 0) >= 10) ship else bananas
                        has(p, Items.KARAMJAN_RUM) -> if (p.attr[Luthas.EMPLOYED_ATTR] == true) hide else job
                        else -> zambo
                    }
                }
            },
        )
    }

    val THE_KNIGHTS_SWORD: RouteQuest by lazy {
        val reldo = step(Npcs.RELDO, "Ask Reldo in the Varrock Palace library about the Imcando dwarves.", "the Varrock Palace library", Tile(3208, 3496, 0))
        val pie = step(Npcs.THURGO, "Bring a redberry pie to Thurgo, the Imcando dwarf on the cliffs south of Port Sarim.", "south of Port Sarim", Tile(3001, 3144, 0))
        val ask = step(Npcs.THURGO, "Ask Thurgo to make a replica of Sir Vyvin's sword.", "south of Port Sarim", Tile(3001, 3144, 0))
        val squire = step(Npcs.SQUIRE, "Ask the Squire in the White Knights' Castle courtyard about a picture of the sword.", "the White Knights' Castle, Falador", Tile(2974, 3343, 0))
        val cupboard = step(-1, "Search the cupboard in Sir Vyvin's room on the top floor of the White Knights' Castle for the portrait (he must not see you).", "the White Knights' Castle, top floor", Tile(2984, 3336, 2))
        val portrait = step(Npcs.THURGO, "Take the portrait to Thurgo.", "south of Port Sarim", Tile(3001, 3144, 0))
        val blurite = step(-1, "Mine a blurite ore in the Asgarnian Ice Dungeon under Mudskipper Point (ice warriors and giants live there).", "the Asgarnian Ice Dungeon", Tile(3049, 9566, 0))
        val bars = step(Npcs.THURGO, "Bring Thurgo the blurite ore and two iron bars.", "south of Port Sarim", Tile(3001, 3144, 0))
        val back = step(Npcs.SQUIRE, "Give the blurite sword to the Squire in the White Knights' Castle.", "the White Knights' Castle, Falador", Tile(2974, 3343, 0))
        RouteQuest(
            "The Knight's Sword", Varps.THE_KNIGHTS_SWORD_PROGRESS, 8, 7, Npcs.SQUIRE, "the Squire", "the White Knights' Castle, Falador", Tile(2974, 3343, 0),
            QuestRequirements(skills = listOf(Skills.MINING to 10), boostable = setOf(Skills.MINING), extra = listOf("Must survive the ice monsters near the blurite.")),
            listOf(reldo, pie, ask, squire, cupboard, blurite),
            pick = { p, stage ->
                when (stage) {
                    5 -> if (has(p, Items.PORTRAIT)) portrait else cupboard
                    6 -> if (has(p, Items.BLURITE_SWORD)) back else if (has(p, Items.BLURITE_ORE) && has(p, Items.IRON_BAR, 2)) bars else blurite
                    else -> null
                }
            },
        )
    }

    val VAMPYRE_SLAYER: RouteQuest by lazy {
        val harlow = step(Npcs.DR_HARLOW, "Find Dr Harlow in the Blue Moon Inn in Varrock.", "the Blue Moon Inn, Varrock", Tile(3222, 3398, 0))
        val beer = step(Npcs.DR_HARLOW, "Buy Dr Harlow a beer from the bartender and ask him how to kill the vampyre.", "the Blue Moon Inn, Varrock", Tile(3222, 3398, 0))
        val count = step(-1, "Open the coffin in the basement of Draynor Manor and kill Count Draynor (bring the stake, the hammer and garlic).", "the Draynor Manor basement", Tile(3080, 9785, 0))
        val morgan = step(Npcs.MORGAN, "Tell Morgan in Draynor Village that the vampyre is dead.", "Draynor Village", Tile(3100, 3268, 0))
        RouteQuest(
            "Vampyre Slayer", Varbits.VAMPYRE_SLAYER_PROGRESS, 16, 3, Npcs.MORGAN, "Morgan", "Draynor Village", Tile(3100, 3268, 0),
            QuestRequirements(extra = listOf("Must be able to defeat Count Draynor (level 34).")),
            listOf(harlow, count),
            usesVarbits = true,
            pick = { p, stage ->
                if (stage != 2) null else if (p.attr.has(killedCountDraynor)) morgan else if (p.attr.has(gaveHarlowBeer)) count else beer
            },
        )
    }

    val ALL: List<RouteQuest> by lazy {
        listOf(
            RUNE_MYSTERIES, DRUIDIC_RITUAL, PRIEST_IN_PERIL, RESTLESS_GHOST, LOST_CITY, PRINCE_ALI_RESCUE,
            COOKS_ASSISTANT, DORICS_QUEST, IMP_CATCHER, PIRATES_TREASURE, THE_KNIGHTS_SWORD, VAMPYRE_SLAYER,
        )
    }

    fun forSlot(slot: Int): RouteQuest? = ALL.firstOrNull { it.slot == slot }
}
