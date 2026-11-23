package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.*

/** OSRS Wiki Fishing Contest: vine -> contest -> return trophy; contest itself condensed into Bonzo's dialogue. */
object FishingContest : ShortQuest(
    name = "Fishing Contest", startPoint = "Speak to Austri east of White Wolf Mountain.",
    rewards = "1 Quest Point, 2,437 Fishing XP, access to the White Wolf Mountain passage.",
    pointReward = 1, questId = Varps.FISHING_CONTEST_PROGRESS, slot = 47,
    completedValue = 4, usesVarbits = false, startNpc = Npcs.AUSTRI,
    startNpcName = "Austri", startPlace = "east of White Wolf Mountain", startLocation = Tile(2876, 3483, 0),
) {
    override val ownSteps = true
    override val showStartedInList = true
    override val needs = QuestRequirements(skills = listOf(Skills.FISHING to 10))
    override val reward = QuestReward(Items.FISHING_TROPHY, xp = listOf(Skills.FISHING to 2_437.0),
        lines = listOf("2,437 Fishing XP", "Access to the White Wolf Mountain passage"))

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc("Win the Hemenster fishing trophy for us", "and we can be friends. Bring a fishing rod,", "garlic and five coins to the contest.")
        if (options("I'll win the trophy.", "Not now.", title = "Start Fishing Contest?") != 1) return false
        ExtraQuests.reclaim(player, Items.FISHING_PASS)
        return true
    }

    override val steps = listOf(
        QuestStep(-1, listOf("Take a spade to the red vine in McGrubor's Wood."), listOf("I collected a red vine worm."),
            place = "the red vine in McGrubor's Wood", location = Tile(2631, 3500, 0),
            search = QuestSearch(Tile(2631, 3500, 0), "You dig by the red vine and find a red vine worm.",
                radius = 6, tools = listOf(Items.SPADE to 1), gives = listOf(Items.RED_VINE_WORM to 1)),
            tip = "Bring a spade; then a fishing rod, garlic and 5 coins to Bonzo."),
        QuestStep(Npcs.BONZO, listOf("Pay Bonzo with my pass, rod, garlic, worm and 5 coins."), listOf("I won the fishing trophy."),
            place = "the Hemenster fishing contest", location = Tile(2641, 3437, 0), gives = listOf(Items.FISHING_TROPHY to 1),
            talk = { chatNpc("Use my Pay option to enter. Bring your pass,", "fishing rod, red vine worm, garlic and 5 coins."); false }),
        QuestStep(Npcs.AUSTRI, listOf("Return the fishing trophy to Austri or Vestri."), listOf("The dwarves accepted my trophy."),
            place = "east of White Wolf Mountain", location = startLocation,
            talk = {
                if (!ExtraQuests.take(player, Items.FISHING_TROPHY)) false
                else { chatNpc("You won! You're our friend now.", "You may use the passage beneath the mountain."); true }
            }),
    )

    suspend fun pay(task: QueueTask) {
        val p = task.player
        if (isFinished(p)) {
            p.message("You've already won the Fishing Competition and don't need to enter again.")
            return
        }
        if (stage(p) == 3) {
            task.chatNpc("You've already paid, you don't need to pay me again!")
            return
        }
        if (!enter(p)) return
        task.chatNpc("The garlic drives the stranger from the pipes.", "Your red vine worm catches the winning carp!", "Here is the fishing trophy.")
        FoundationQuests.offerNextStep(task, this, false)
    }

    /** Atomic entry, also used by the insufficient-coins and double-click regression tests. */
    fun enter(p: gg.rsmod.game.model.entity.Player): Boolean {
        if (isFinished(p) || stage(p) != 2) return false
        val required = listOf(Items.FISHING_PASS, Items.FISHING_ROD, Items.GARLIC, Items.RED_VINE_WORM)
        if (!required.all { p.inventory.contains(it) }) {
            p.message("Bring your fishing pass, fishing rod, garlic and a red vine worm.")
            return false
        }
        if (!ExtraQuests.take(p, Items.COINS_995, 5)) return false
        p.inventory.remove(Items.GARLIC)
        p.inventory.remove(Items.RED_VINE_WORM)
        FoundationRewards.grant(p, Items.FISHING_TROPHY, 1)
        // Stage and trophy are committed before the first suspending dialogue.
        FoundationQuests.completeStep(p, this, 2)
        return true
    }
}
