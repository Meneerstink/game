package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.attr.*
import gg.rsmod.plugins.content.skills.slayer.data.SlayerMaster
import gg.rsmod.plugins.content.skills.slayer.data.slayerData

/**
 * @author Alycia <https://github.com/alycii>
 */

/**
 * Every Slayer master the server knows ([SlayerMaster]) gets the same routes, from this one list: Talk-to, Get-task,
 * Trade (Slayer Equipment) and Rewards (points shop, `slayer_rewards.plugin.kts`). 2026-09-22 npc census: Chaeldar,
 * Sumona, Duradel and Kuradal advertised Get-task/Trade/Rewards in the cache with nothing bound, and Chaeldar/Sumona/
 * Duradel only answered Talk-to with the generic one-line fallback greeting.
 */
val npcIds = SlayerMaster.values().map { it.id }.toTypedArray()

// Sumona is spawned as her varbit-transforming wrapper npc 7779 (-> 7780); bind the wrapper too, so her options work
// whichever id the click resolves to.
val SUMONA_WRAPPER = 7779

fun hasOption(npcId: Int, option: String): Boolean =
    world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, npcId).options.any { it.equals(option, ignoreCase = true) }

(npcIds + SUMONA_WRAPPER).forEach { npcId ->
    val slayerMaster = if (npcId == SUMONA_WRAPPER) SlayerMaster.SUMONA else SlayerMaster.getMaster(npcId)
    slayerMaster?.let {

        if (hasOption(npcId, "get-task")) {
            on_npc_option(npc = npcId, option = "get-task") {
                player.queue { giveTask(this, slayerMaster) }
            }
        }

        on_npc_option(npc = npcId, option = "talk-to") {
            player.queue {
                chatNpc("'Ello, and what are you after then?")
                val firstOption =
                    when (player.attr.has(STARTED_SLAYER)) {
                        true -> "I need another assignment"
                        false -> "Who are you?"
                    }

                when (options(firstOption, "Do you have anything for trade?", "Er... Nothing...")) {
                    FIRST_OPTION -> {
                        if (player.attr.has(STARTED_SLAYER)) {
                            giveTask(this, slayerMaster)
                        } else {
                            chatPlayer("Who are you?")
                            chatNpc("I'm one of the elite Slayer Masters.")
                            when (options("What's a slayer?", "Never heard of you...")) {
                                FIRST_OPTION -> {
                                    chatPlayer("What's a slayer?")
                                    chatNpc("Oh dear, what do they teach you in school?")
                                    chatPlayer("Well.. er...")
                                    chatNpc(
                                        "I suppose I'll have to educate you then. A slayer is",
                                        "someone who is trained to fight specific creatures. They",
                                        "know these creatures' every weakness and strength. As",
                                        "you can guess it makes killing them a lot easier.",
                                    )
                                    tutorialDialogue(this, slayerMaster)
                                }

                                SECOND_OPTION -> {
                                    chatPlayer("Never heard of you...")
                                    chatNpc(
                                        "That's because my foe never lives to tell of me. We",
                                        "slayers are a dangerous bunch.",
                                    )
                                    tutorialDialogue(this, slayerMaster)
                                }
                            }
                        }
                    }

                    SECOND_OPTION -> {
                        chatPlayer("Do you have anything for trade?")
                        chatNpc("I have a wide selection of Slayer equipment; take a look!")
                        player.openShop("Slayer Equipment")
                    }
                }
            }
        }
    }
}

suspend fun tutorialDialogue(
    it: QueueTask,
    slayerMaster: SlayerMaster,
) {
    when (it.options("Wow, can you teach me?", "Sounds useless to me..")) {
        FIRST_OPTION -> {
            it.chatPlayer("Wow, can you teach me?")
            it.chatNpc("Hmm well I'm not so sure...")
            it.chatPlayer("Pleeeaasssse!")
            it.chatNpc(
                "Oh okay then, you twisted my arm. You'll have to train",
                "against specific groups of creatures.",
            )
            it.chatPlayer("Okay, what's first?")
            giveTask(it, slayerMaster)
        }
        SECOND_OPTION -> {
            it.chatPlayer("Sounds useless to me.")
            it.chatNpc("Suit yourself.")
        }
    }
}

suspend fun tipsDialogue(it: QueueTask) {
    // TODO: Find all the slayer tip dialogues
    when (it.options("Got any tips for me?", "Okay, great!")) {
        FIRST_OPTION -> {
            it.chatPlayer("Got any tips for me?")
            it.chatNpc("As you're a beginner, I recommend bringing food", "and a good weapon of your choice.")
            it.chatPlayer("Great, thanks!")
        }
        SECOND_OPTION -> {
            it.chatPlayer("Okay, great!")
        }
    }
}

suspend fun giveTask(
    it: QueueTask,
    slayerMaster: SlayerMaster,
) {
    val player = it.player

    if (!player.attr.has(BLOCKED_TASKS)) {
        // Initialize with an empty list
        player.attr[BLOCKED_TASKS] = mutableListOf()
    }

    val blockedTasks = player.attr[BLOCKED_TASKS] as MutableList<String>

    if (player.getSlayerAssignment() != null) {
        if (slayerMaster == SlayerMaster.TURAEL && Krystilia.active(player) &&
            slayerData.getAssignmentsForMaster(slayerMaster).none { it.assignment == player.getSlayerAssignment() }) {
            it.chatNpc("Although, it's not an assignment that I'd normally give... I guess I could give you a new assignment, if you'd like.", wrap = true)
            // OSRS Krystilia: a Turael replacement resets the Wilderness streak, never the standard streak.
            if (it.options("Yes, please.", "No, thanks.", title = "Reset your Wilderness task streak of ${player.attr[Krystilia.STREAK] ?: 0}?") != 1) return
            player.attr[Krystilia.STREAK] = 0
            player.removeSlayerAssignment()
        } else {
        it.chatNpc(
            "You're still hunting ${player.getSlayerAssignment()!!.identifier.lowercase()}, you have ${player.attr[SLAYER_AMOUNT]} to go. Come back when you've finished your task.",
            wrap = true,
        )
        return
        }
    }

    // K03 N410: the Slayer cape "Allows bypassing combat level requirements for new tasks" (OSRS Wiki "Cape of Accomplishment").
    val slayerCape = gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.SLAYER)
    if (player.skills.getMaxLevel(Skills.SLAYER) < slayerMaster.reqSlayerLevel ||
        player.combatLevel < slayerMaster.requiredCombatLevel && !slayerCape
    ) {
        it.chatNpc(
            "You are not yet experienced enough to receive my tasks. Come back when you're stronger.",
            facialExpression = FacialExpression.CHEERFUL,
            wrap = true,
        )
        return
    }

    val assignments = slayerData.getAssignmentsForMaster(slayerMaster)

    // Exclude blocked tasks from the valid assignments
    val validAssignments =
        assignments.filter { assignment ->
            assignment.requirement.all { it.hasRequirement(player) } &&
                !blockedTasks.contains(assignment.assignment.identifier)
        }

    if (validAssignments.isNotEmpty()) {
        // K03 N410: the Slayer cape gives a "10% chance to be offered the option to pick the previous task" (OSRS Wiki). CUSTOM wording.
        val previous = player.attr[SLAYER_PREVIOUS_TASK]?.let { last -> validAssignments.firstOrNull { it.assignment.identifier == last } }
        val repeat =
            previous != null && slayerCape && world.randomDouble() < 0.1 &&
                it.options("Yes, ${previous.assignment.identifier.lowercase()} again.", "No, a new task.", title = "Repeat your previous task?") == 1
        val selectedAssignment =
            if (repeat) {
                previous!!
            } else {
                val totalWeight = validAssignments.sumOf { it.weight }
                var weightSum = 0.0
                val randomWeight = Math.random() * totalWeight
                validAssignments.first { assignment ->
                    weightSum += assignment.weight
                    weightSum >= randomWeight
                }
            }

        val amount =
            if (selectedAssignment.amount.first == 0 && selectedAssignment.amount.last == 0) {
                slayerMaster.defaultAmount
            } else {
                selectedAssignment.amount
            }

        player.attr[SLAYER_ASSIGNMENT] = selectedAssignment.assignment.identifier
        player.attr[SLAYER_PREVIOUS_TASK] = selectedAssignment.assignment.identifier
        player.attr[SLAYER_AMOUNT] = world.random(amount)
        player.attr[SLAYER_MASTER] = slayerMaster.id

        if (!player.attr.has(STARTED_SLAYER)) {
            player.inventory.add(Item(Items.ENCHANTED_GEM))
            it.chatNpc(
                "We'll start you off hunting ${selectedAssignment.assignment.identifier.lowercase()}, you'll need to kill ${player.attr[SLAYER_AMOUNT]} of them.",
                wrap = true
            )
            it.chatNpc(
                "You'll also need this enchanted gem, it allows Slayer Masters like myself to contact you and update you on your progress. Don't worry if you lose it, you can buy another from any Slayer Master.",
                wrap = true
            )
            player.attr[STARTED_SLAYER] = true
            tipsDialogue(it)
        } else {
            it.chatNpc(
                "Excellent, you're doing great. Your new task is to kill ${player.attr[SLAYER_AMOUNT]} ${selectedAssignment.assignment.identifier}.",
                npc = player.attr[SLAYER_MASTER]!!,
                wrap = true,
            )
            tipsDialogue(it)
        }
    } else {
        it.chatNpc("It seems I don't have any tasks for you right now. Please check back later.")
    }
}
