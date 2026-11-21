package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
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
) : ShortQuest(
        name = name,
        startPoint = "Speak to $startNpcName at $startPlace.",
        rewards = "See the quest journal.",
        pointReward = 0,
        questId = questId,
        slot = slot,
        completedValue = stages,
        usesVarbits = false,
        startNpc = startNpc,
        startNpcName = startNpcName,
        startPlace = startPlace,
        startLocation = startLocation,
    ) {
    override val steps: List<QuestStep> = routes

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
        val drezel = step(Npcs.DREZEL_1049, "Go to the temple east of Varrock and speak to Drezel (see your quest journal).", "the temple east of Varrock", Tile(3415, 3489, 2))
        RouteQuest(
            "Priest in Peril", Varps.PRIEST_IN_PERIL_PROGRESS, 84, 9, Npcs.KING_ROALD, "King Roald", "Varrock Palace", Tile(3220, 3471, 0),
            QuestRequirements(extra = listOf("Must be able to defeat a level 30 Temple Guardian.")),
            List(8) { drezel },
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

    val ALL: List<RouteQuest> by lazy { listOf(RUNE_MYSTERIES, DRUIDIC_RITUAL, PRIEST_IN_PERIL, RESTLESS_GHOST, LOST_CITY, PRINCE_ALI_RESCUE) }

    fun forSlot(slot: Int): RouteQuest? = ALL.firstOrNull { it.slot == slot }
}
