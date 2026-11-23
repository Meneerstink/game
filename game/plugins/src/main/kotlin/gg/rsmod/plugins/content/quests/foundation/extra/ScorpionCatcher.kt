package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.*

/** OSRS Wiki Scorpion Catcher / Scorpion cage / Kharid Scorpion. No prerequisite is silently waived. */
object ScorpionCatcher : ShortQuest(
    name = "Scorpion Catcher", startPoint = "Speak to Thormac atop the Sorcerer's Tower.",
    rewards = "1 Quest Point, 6,625 Strength XP, Thormac's mystic staff service.", pointReward = 1,
    questId = Varps.SCORPION_CATCHER_PROGRESS, slot = 93, completedValue = 6, usesVarbits = false,
    startNpc = Npcs.THORMAC, startNpcName = "Thormac", startPlace = "the top of the Sorcerer's Tower",
    startLocation = Tile(2702, 3406, 3),
) {
    override val ownSteps = true
    override val showStartedInList = true
    // The miniquest Barcrawl has no quest-list entry: it belongs to the Armageddon bracket 3-50, so it is completed for every account
    // (QuestBrackets.givesUnlisted, BARCRAWL 24-11). Prayer 31 stays the one real requirement.
    override val needs = QuestRequirements(skills = listOf(Skills.PRAYER to 31), quests = listOf("Alfred Grimhand's Barcrawl"))
    override val reward = QuestReward(Items.SCORPION_CAGE_463, xp = listOf(Skills.STRENGTH to 6_625.0),
        lines = listOf("6,625 Strength XP", "Thormac can enchant battlestaves for 40,000 coins"))

    /** Bit order Taverley / Outpost / Monastery, as the eight wiki cage versions. */
    val CAGES = listOf(456, 457, 460, 458, 462, 459, 461, 463)
    val SCORPIONS = mapOf(385 to 1, 386 to 2, 387 to 4)
    val POSTS = mapOf(385 to Tile(2877, 9796, 0), 386 to Tile(2552, 3570, 0), 387 to Tile(3058, 3488, 1))

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc("My three rare Kharid Scorpions have escaped.", "Take a cage and ask a Seer in Seers' Village", "where they have gone.")
        if (options("I'll find them.", "Not now.", title = "Start Scorpion Catcher?") != 1) return false
        reclaimCage(player)
        return true
    }

    override val steps = listOf(
        QuestStep(Npcs.SEER, listOf("Ask a Seer in Seers' Village about the scorpions."), listOf("The Seer located the escaped scorpions."),
            place = "Seers' Village", location = Tile(2709, 3485, 0),
            talk = {
                chatNpc("One is behind a false wall in Taverley Dungeon.", "The others are at the Barbarian Outpost", "and upstairs in the Edgeville Monastery.")
                true
            }),
        catchStep(385, "Taverley Dungeon", "Use the cage on the scorpion behind the false wall."),
        catchStep(387, "the Edgeville Monastery", "Use the cage on the scorpion upstairs in the monastery."),
        catchStep(386, "the Barbarian Outpost", "Use the cage on the scorpion inside the agility course."),
        QuestStep(Npcs.THORMAC, listOf("Bring the full scorpion cage to Thormac."), listOf("I returned all three scorpions to Thormac."),
            place = startPlace, location = startLocation,
            talk = {
                if (!ExtraQuests.take(player, Items.SCORPION_CAGE_463)) false
                else { chatNpc("All three are safe! Thank you.", "I can now enchant your elemental battlestaves."); true }
            }),
    )

    private fun catchStep(id: Int, place: String, text: String) = QuestStep(
        -1, listOf(text), listOf("I caught the scorpion at $place."), place = place, location = POSTS.getValue(id),
        tip = "Use your scorpion cage on it. Picking it up stings for 3 Hitpoints.",
    )

    fun reclaimCage(p: Player) {
        if (CAGES.any { ExtraQuests.held(p, it) }) return
        FoundationRewards.grant(p, Items.SCORPION_CAGE, 1)
        // OSRS: losing a cage loses its occupants. The Seer's information remains known.
        if (stage(p) in 2 until completedValue) setStage(p, 2)
    }

    fun capture(p: Player, npc: Int): Boolean {
        if (isFinished(p) || stage(p) !in 2..4) return false
        val bit = SCORPIONS[npc] ?: return false
        val cage = CAGES.firstOrNull { p.inventory.contains(it) } ?: run {
            p.message("You need the scorpion cage from Thormac."); return false
        }
        val mask = CAGES.indexOf(cage)
        if (mask and bit != 0) { p.message("You have already caught this scorpion."); return false }
        p.inventory.remove(cage)
        p.inventory.add(CAGES[mask or bit], 1)
        p.message("You catch the scorpion in the cage.")
        // Catching in any order is allowed. Skip already caught helper steps when their turn arrives.
        val order = listOf(1, 4, 2)
        while (stage(p) in 2..4 && ((mask or bit) and order[stage(p) - 2]) != 0) {
            FoundationQuests.completeStep(p, this, stage(p))
        }
        return true
    }

    val STAVES = listOf(Items.AIR_BATTLESTAFF to Items.MYSTIC_AIR_STAFF, Items.WATER_BATTLESTAFF to Items.MYSTIC_WATER_STAFF,
        Items.EARTH_BATTLESTAFF to Items.MYSTIC_EARTH_STAFF, Items.FIRE_BATTLESTAFF to Items.MYSTIC_FIRE_STAFF)

    suspend fun enchant(task: QueueTask) {
        if (!isFinished(task.player)) return
        val choice = task.options("Air battlestaff.", "Water battlestaff.", "Earth battlestaff.", "Fire battlestaff.", "No thanks.",
            title = "Enchant a battlestaff for 40,000 coins?")
        val pair = STAVES.getOrNull(choice - 1) ?: return
        val p = task.player
        if (!p.inventory.contains(pair.first)) { p.message("Bring the elemental battlestaff you want enchanted."); return }
        if (!ExtraQuests.take(p, Items.COINS_995, 40_000)) return
        p.inventory.remove(pair.first)
        p.inventory.add(pair.second, 1)
    }
}
