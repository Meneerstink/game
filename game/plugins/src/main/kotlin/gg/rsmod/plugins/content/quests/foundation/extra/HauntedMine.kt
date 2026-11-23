package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.*

/** Wiki Haunted Mine: key, fungus, valve, Treus Dayth, crystal. Track puzzles and boss machinery are condensed. */
object HauntedMine : ShortQuest(
    name = "Haunted Mine", startPoint = "Speak to the Zealot outside the Abandoned Mine.",
    rewards = "2 Quest Points, 22,000 Strength XP, ability to make salve amulets.", pointReward = 2,
    questId = Varps.HAUNTED_MINE_PROGRESS, slot = 58, completedValue = 6, usesVarbits = false,
    startNpc = Npcs.ZEALOT, startNpcName = "the Zealot", startPlace = "the Abandoned Mine",
    startLocation = Tile(3444, 3258, 0),
) {
    override val ownSteps = true
    override val showStartedInList = true
    override val needs = QuestRequirements(skills = listOf(Skills.CRAFTING to 35), quests = listOf("Priest in Peril"),
        boostable = setOf(Skills.CRAFTING), extra = listOf("Ability to defeat Treus Dayth (level 95)."))
    override val recommendation = Recommendation.combat(50)
    override val reward = QuestReward(Items.SALVE_AMULET, xp = listOf(Skills.STRENGTH to 22_000.0),
        lines = listOf("22,000 Strength XP", "You can cut salve shards and make salve amulets"))
    val RETURNED_KEY = AttributeKey<Boolean>(persistenceKey = "extra_haunted_mine_returned_key")
    val FUNGUS = Tile(2792, 4493, 0)
    val VALVE = Tile(2807, 4496, 0)
    val FIGHT = Tile(2788, 4450, 0)
    val CRYSTALS = Tile(2788, 4429, 0)

    override fun onReset(player: Player) { player.attr.remove(RETURNED_KEY) }

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc("The mine hides crystals blessed by Saradomin.", "Treus Dayth guards them, even beyond death.", "My key opens the way into the lower mine.")
        return options("I'll investigate the mine.", "Not now.", title = "Start Haunted Mine?") == 1
    }

    override val steps = listOf(
        QuestStep(Npcs.ZEALOT, listOf("Pickpocket the Zealot for his key."), listOf("I borrowed the Zealot's key."),
            place = startPlace, location = startLocation, gives = listOf(Items.ZEALOTS_KEY to 1),
            talk = { chatNpc("My key is quite safe in my pocket."); false }),
        QuestStep(-1, listOf("Find glowing fungus in the fourth level of the mine."), listOf("I found glowing fungus to light the depths."),
            place = "the glowing fungus in the mine", location = FUNGUS,
            search = QuestSearch(FUNGUS, "You pick some glowing fungus.", gives = listOf(Items.GLOWING_FUNGUS to 1))),
        QuestStep(-1, listOf("Take the Zealot's key and glowing fungus to the water valve."), listOf("I opened the water supply to the lift."),
            place = "the mine's water valve", location = VALVE,
            search = QuestSearch(VALVE, "You use the Zealot's key to open the water supply to the lift.",
                tools = listOf(Items.ZEALOTS_KEY to 1, Items.GLOWING_FUNGUS to 1))),
        QuestStep(-1, listOf("Defeat Treus Dayth in the lowest chamber."), listOf("I defeated Treus Dayth and obtained the crystal-mine key."),
            place = "Treus Dayth's chamber", location = FIGHT, gives = listOf(Items.CRYSTALMINE_KEY to 1),
            fight = QuestFight(Npcs.TREUS_DAYTH, "Treus Dayth", 95, FIGHT, ""),
            tip = "Bring combat equipment and food. Death follows the normal rules here."),
        QuestStep(-1, listOf("Take a chisel and the crystal-mine key to the salve crystals."), listOf("I cut a salve shard from the crystals."),
            place = "the salve crystals", location = CRYSTALS,
            search = QuestSearch(CRYSTALS, "You cut a salve shard from the crystal outcrop.",
                tools = listOf(Items.CHISEL to 1, Items.CRYSTALMINE_KEY to 1), gives = listOf(Items.SALVE_SHARD to 1))),
    )

    fun stealKey(p: Player): Boolean {
        if (!isFinished(p) && stage(p) == 0) { p.message("I doubt he's got much of value on him."); return false }
        if (p.attr[RETURNED_KEY] == true) { p.message("I don't want to annoy him any further."); return false }
        ExtraQuests.reclaim(p, Items.ZEALOTS_KEY)
        if (!isFinished(p) && stage(p) == 1) FoundationQuests.completeStep(p, this, 1)
        return true
    }

    suspend fun returnKey(task: QueueTask) {
        val p = task.player
        if (!isFinished(p) || !p.inventory.contains(Items.ZEALOTS_KEY)) return
        if (task.options("Return the borrowed key.", "Keep it for now.") != 1) return
        if (ExtraQuests.take(p, Items.ZEALOTS_KEY)) p.attr[RETURNED_KEY] = true
    }
}
