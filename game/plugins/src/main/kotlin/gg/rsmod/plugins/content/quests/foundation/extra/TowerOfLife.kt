package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.*

/** Wiki Tower of Life: builders' costume, condensed repairs, reconcile the Homunculus's mind. */
object TowerOfLife : ShortQuest(
    name = "Tower of Life", startPoint = "Speak to Effigy outside the Tower of Life.",
    rewards = "2 Quest Points, 1,000 Construction XP, 500 Crafting XP, 500 Thieving XP and builder's costume.", pointReward = 2,
    questId = Varbits.TOWER_OF_LIFE_PROGRESS, slot = 119, completedValue = 4, usesVarbits = true,
    startNpc = Npcs.EFFIGY, startNpcName = "Effigy", startPlace = "outside the Tower of Life", startLocation = Tile(2640, 3218, 0),
) {
    override val ownSteps = true
    override val showStartedInList = true
    override val needs = QuestRequirements(skills = listOf(Skills.CONSTRUCTION to 10))
    override val reward = QuestReward(Items.HARD_HAT,
        xp = listOf(Skills.CONSTRUCTION to 1_000.0, Skills.CRAFTING to 500.0, Skills.THIEVING to 500.0),
        lines = listOf("1,000 Construction XP", "500 Crafting XP", "500 Thieving XP", "Builder's costume and access to the basement"))
    val OUTFIT_REQUESTED = AttributeKey<Boolean>(persistenceKey = "extra_tower_of_life_outfit_requested")
    val OUTFIT = listOf(Items.HARD_HAT, Items.BUILDERS_SHIRT, Items.BUILDERS_TROUSERS, Items.BUILDERS_BOOTS)
    val REPAIR_POST = Tile(2647, 3222, 1)
    val HOMUNCULUS_POST = Tile(2646, 3218, 3)
    /** Beside the basement ladder 17974 (OSRS map 3038,4375); the old 3039,4400 was not a standable tile (TOWER-KELDERLUIK 24-11). */
    val BASEMENT = Tile(3038, 4376, 0)
    /** TOWER-KELDERLUIK: the trapdoor on the ground floor (owner 24-11) and the tile the basement ladder brings the player back to. */
    val TRAPDOOR = Tile(2651, 3220, 0)
    val TRAPDOOR_EXIT = Tile(2651, 3221, 0)

    override fun onReset(player: Player) { player.attr.remove(OUTFIT_REQUESTED) }

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc("Our builders have gone on strike.", "Speak to Bonafido and help finish the tower.")
        return options("I'll help finish the tower.", "Not now.", title = "Start Tower of Life?") == 1
    }

    override val steps = listOf(
        QuestStep(Npcs.BONAFIDO, listOf("Obtain and wear a builder's costume, then speak to Bonafido."),
            listOf("Bonafido accepted me as a builder."), place = "Bonafido outside the tower", location = Tile(2649, 3226, 0),
            tip = "Hat: Black-eye. Shirt: The Guns. Trousers: plants. Boots: No fingers.",
            talk = {
                player.attr[OUTFIT_REQUESTED] = true
                if (!OUTFIT.all { player.equipment.contains(it) }) {
                    chatNpc("Wear a hard hat, shirt, trousers and boots.", "The other builders can help you find them.")
                    false
                } else {
                    chatNpc("A builder needs the right attitude.", "What do you drink when you need a quick break?")
                    if (options("Tea.", "Orange juice.", "Water.") != 1) false
                    else { chatNpc("That's the spirit. Bring a hammer and saw", "and finish the machinery upstairs."); true }
                }
            }),
        QuestStep(-1, listOf("Bring a hammer and saw to repair the tower's machinery."), listOf("I repaired the machinery and the alchemists created a Homunculus."),
            place = "the tower's pressure machine", location = REPAIR_POST,
            search = QuestSearch(REPAIR_POST, "You finish the pressure machine, pipes and cage. The alchemists create a Homunculus.",
                tools = listOf(Items.HAMMER to 1, Items.SAW to 1))),
        QuestStep(Npcs.HOMUNCULUS, listOf("Help the Homunculus understand magic and logic."), listOf("The Homunculus is free and at peace with itself."),
            place = "the top of the Tower of Life", location = HOMUNCULUS_POST,
            talk = {
                chatNpc("Magic... logic... what am I?", "Can you help me understand?")
                val answer = options("Trust logic and what you can observe.", "Trust the power of magic.", "I'll come back later.")
                if (answer == 3) false
                else { chatNpc("Now I understand. I can choose for myself.", "The alchemists can no longer control me.", "Visit me in the basement when you return."); true }
            }),
    )

    fun canGetOutfit(p: Player): Boolean = isFinished(p) || (stage(p) > 0 && p.attr[OUTFIT_REQUESTED] == true)

    /** Quest boots are a flat one-in-four roll, independent of level, boosts and rogue loot multipliers (Mod Ash/wiki). */
    fun wantsBoots(p: Player, npc: Int): Boolean = npc == Npcs.NO_FINGERS && canGetOutfit(p) && !ExtraQuests.held(p, Items.BUILDERS_BOOTS)

    fun awardBoots(p: Player): Boolean {
        if (!wantsBoots(p, Npcs.NO_FINGERS)) return false
        FoundationRewards.grant(p, Items.BUILDERS_BOOTS, 1)
        p.message("You pick the man's pocket and find some boots!")
        return true
    }

    suspend fun hardHat(task: QueueTask) {
        val p = task.player
        if (!canGetOutfit(p)) return
        if (ExtraQuests.held(p, Items.HARD_HAT)) { ExtraQuests.reclaim(p, Items.HARD_HAT); return }
        task.chatNpc("Prove your Construction knowledge first.")
        if (task.options("Three.", "One.", "Five.", title = "Nails for a rocking chair?") != 1) return
        if (task.options("Torn curtains.", "A brown rug.", "Opulent curtains.", title = "Three planks, cloths and nails?") != 1) return
        if (task.options("10 clay pieces.", "10 limestone bricks.", "8 clay and 3 limestone bricks.", title = "Materials for a pond?") != 1) return
        ExtraQuests.reclaim(p, Items.HARD_HAT)
    }

    fun shirt(p: Player): Boolean {
        if (!canGetOutfit(p)) return false
        if (ExtraQuests.held(p, Items.BUILDERS_SHIRT)) { ExtraQuests.reclaim(p, Items.BUILDERS_SHIRT); return false }
        if (!ExtraQuests.take(p, Items.BEER)) return false
        FoundationRewards.grant(p, Items.BUILDERS_SHIRT, 1)
        return true
    }
}
