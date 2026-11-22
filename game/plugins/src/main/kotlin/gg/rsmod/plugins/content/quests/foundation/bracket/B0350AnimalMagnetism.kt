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
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q07: Animal Magnetism (bracket 3-50). OSRS Wiki: Slayer 18, Crafting 19, Ranged 30, Woodcutting 35 + 3 quests.

private val AVA_POST = Tile(3099, 3355, 0)
private val WITCH_POST = Tile(3099, 3369, 0)
private val CHICKEN_FARM = Tile(3625, 3523, 0)
private val TURAEL_POST = Tile(2931, 3536, 0)

object AnimalMagnetism : ShortQuest(
    name = "Animal Magnetism",
    startPoint = "Speak to Ava in Draynor Manor (search the bookcase in the west wing).",
    rewards = "1 Quest Point, 1,000 Crafting/Fletching/Slayer XP, 2,500 Woodcutting XP, Ava's attractor or accumulator.",
    pointReward = 1,
    questId = Varbits.ANIMAL_MAGNETISM_PROGRESS,
    slot = 18,
    completedValue = 7, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.AVA,
    startNpcName = "Ava",
    startPlace = "Draynor Manor",
    startLocation = AVA_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.SLAYER to 18, Skills.CRAFTING to 19, Skills.RANGED to 30, Skills.WOODCUTTING to 35),
            quests = listOf("The Restless Ghost", "Ernest the Chicken", "Priest in Peril"),
        )

    override val reward =
        QuestReward(
            icon = Items.AVAS_ATTRACTOR,
            xp = listOf(Skills.CRAFTING to 1_000.0, Skills.FLETCHING to 1_000.0, Skills.SLAYER to 1_000.0, Skills.WOODCUTTING to 2_500.0),
            lines = listOf("1,000 Crafting, Fletching and Slayer XP", "2,500 Woodcutting XP", "Ava's attractor (Ava's accumulator with 50 Ranged)", "Ava's shop"),
            unlock = { p ->
                val device = if (p.skills.getMaxLevel(Skills.RANGED) >= 50) Items.AVAS_ACCUMULATOR else Items.AVAS_ATTRACTOR
                FoundationRewards.grant(p, device, 1)
            },
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Hello there and welcome to my humble",
            "abode. It is rather more humble than I",
            "would like. If you help me mend my bed,",
            "I will make something useful for you.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll help you, Ava.", "Not right now.", title = "Start Animal Magnetism?") != 1) return false
        chatNpc("Splendid. I need two undead chickens.", "The farm west of the Ectofuntus sells", "them - bring a ghostspeak amulet.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ALICES_HUSBAND,
                journal = listOf("I should buy two ${red("undead chickens")} (10 ecto-tokens each) from", "Alice's husband at the farm west of the Ectofuntus. I", "need a ghostspeak amulet and 20 ecto-tokens."),
                done = listOf("I bought two undead chickens."),
                place = "the undead chicken farm",
                location = CHICKEN_FARM,
                gives = listOf(Items.UNDEAD_CHICKEN to 2),
                talk = {
                    if (!player.equipment.contains(Items.GHOSTSPEAK_AMULET)) {
                        chatNpc("Woo woo wooo! (You cannot understand", "him - wear a ghostspeak amulet.)", facialExpression = FacialExpression.WORRIED)
                        false
                    } else if (player.inventory.getItemCount(Items.ECTOTOKEN) < 20) {
                        chatNpc("Undead chickens are 10 ecto-tokens each. You need", "20 ecto-tokens for two.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.ECTOTOKEN, 20)
                        chatNpc("Two undead chickens - and", "mind they do not peck you.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.UNDEAD_CHICKEN, 2)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.AVA,
                journal = listOf("I should bring the two undead chickens to ${red("Ava")}."),
                done = listOf("Ava has the chickens; she wants a bar magnet."),
                place = "Draynor Manor",
                location = AVA_POST,
                talk = {
                    if (player.inventory.getItemCount(Items.UNDEAD_CHICKEN) < 2) {
                        chatNpc("I need two undead chickens, please.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.UNDEAD_CHICKEN, 2)
                        chatNpc("Lovely! Now I need a bar magnet. The", "witch next door can turn five iron", "bars into the right kind of iron.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.WITCH_5200,
                journal = listOf("I should take 5 iron bars to the ${red("witch")} next to Ava and get", "a bar magnet."),
                done = listOf("The witch made me a bar magnet."),
                place = "Draynor Manor",
                location = WITCH_POST,
                gives = listOf(Items.BAR_MAGNET to 1),
                talk = {
                    if (player.inventory.getItemCount(Items.IRON_BAR) < 5) {
                        chatNpc("Bring me five iron bars and I will do the rest.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.IRON_BAR, 5)
                        chatNpc("Hee hee, a fine magnet it makes.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.BAR_MAGNET, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.AVA,
                journal = listOf("I should give the bar magnet to ${red("Ava")}."),
                done = listOf("Ava thanked me with 50 Crafting XP; she needs undead", "twigs."),
                place = "Draynor Manor",
                location = AVA_POST,
                talk = {
                    if (!player.inventory.contains(Items.BAR_MAGNET)) {
                        chatNpc("I need the bar magnet.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.BAR_MAGNET)
                        player.addXp(Skills.CRAFTING, 50.0, modifiers = false)
                        chatNpc("Now I need undead twigs. Turael in Burthorpe knows", "how to cut the undead trees.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.TURAEL,
                journal = listOf("I should visit ${red("Turael")} in Burthorpe. He needs a holy symbol", "to bless the way to the undead twigs."),
                done = listOf("Turael blessed my holy symbol and gave me undead twigs."),
                place = "Burthorpe",
                location = TURAEL_POST,
                gives = listOf(Items.UNDEAD_TWIGS to 1),
                talk = {
                    if (!player.inventory.contains(Items.HOLY_SYMBOL)) {
                        chatNpc("The undead trees need a blessed", "hand. Bring a holy symbol.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.HOLY_SYMBOL)
                        chatNpc("There. These twigs came from an undead tree I have", "tamed with a blessing. Take them to Ava.", facialExpression = FacialExpression.HAPPY)
                        FoundationRewards.grant(player, Items.UNDEAD_TWIGS, 1)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.AVA,
                journal = listOf("I should bring the undead twigs to ${red("Ava")}."),
                done = listOf("Ava built a device for me."),
                place = "Draynor Manor",
                location = AVA_POST,
                talk = {
                    if (!player.inventory.contains(Items.UNDEAD_TWIGS)) {
                        chatNpc("I need the undead twigs.", facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.UNDEAD_TWIGS)
                        chatNpc("Marvellous! Your reward is ready: a device to keep", "your arrows close.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
        )
}
