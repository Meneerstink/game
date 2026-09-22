package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.items.osrs.MaxCapes

/**
 * Max, the Grand Exchange max-cape seller (owner 2026-09-20: "npc mac is in home he needs a trade option and dialog
 * he needs to sell the maxcape for 120k gp if players have max stats").
 *
 * NpcDef 3373 already carries Talk-to / Follow / Trade in the cache, so both options only needed a server handler.
 * OSRS sells the Max cape at 2,277,000 gp; 120,000 is an explicit owner price override for this server, so it is
 * written here once rather than guessed per call site. The cape and hood handed over are the wearable 667 pair
 * ([MaxCapes.MAX_CAPE] / [MaxCapes.MAX_HOOD]) - 20747/20751 are the unwearable copies.
 */
val MAX_CAPE_PRICE = 120_000

/**
 * "Max stats" is 99 in every trainable skill except Dungeoneering, which is left out of the requirement entirely
 * (owner 2026-09-22: "Max doesnt sell me a max cape he asks 99 dungeoneering please remove the dungeoering").
 */
fun maxCapeSkills(player: gg.rsmod.game.model.entity.Player): List<Int> =
    (0 until player.skills.maxSkills).filter { it != Skills.DUNGEONEERING }

fun hasMaxStats(player: gg.rsmod.game.model.entity.Player): Boolean =
    maxCapeSkills(player).all { player.skills.getMaxLevel(it) >= 99 }

fun missingSkills(player: gg.rsmod.game.model.entity.Player): List<String> =
    maxCapeSkills(player).filter { player.skills.getMaxLevel(it) < 99 }.map { Skills.getSkillName(player.world, it) }

suspend fun QueueTask.sellMaxCape() {
    if (!hasMaxStats(player)) {
        val missing = missingSkills(player)
        chatNpc(
            "Not yet, you're not.",
            "A Max cape is for those who have every skill at the",
            "very top. You still have ${missing.size} to finish.",
            facialExpression = FacialExpression.GRUMPY,
        )
        chatNpc(
            "Come back when you've maxed:",
            missing.take(3).joinToString(", ") + if (missing.size > 3) ", and ${missing.size - 3} more." else ".",
            facialExpression = FacialExpression.CALM_TALK,
        )
        return
    }
    if (player.inventory.freeSlotCount < 2) {
        chatNpc("You'll want two free hands to carry these.", facialExpression = FacialExpression.CALM_TALK)
        return
    }
    if (player.inventory.getItemCount(Items.COINS_995) < MAX_CAPE_PRICE) {
        chatNpc(
            "The cape and hood together come to 120,000 coins.",
            "Come back when you've got the gold on you.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        return
    }
    chatNpc(
        "Then it's yours, and you've earned every stitch of it.",
        "120,000 coins for the cape and the hood.",
        facialExpression = FacialExpression.HAPPY,
    )
    if (options("Buy the Max cape and hood (120,000 coins).", "Not right now.") != 1) return
    if (!player.inventory.remove(Items.COINS_995, MAX_CAPE_PRICE).hasSucceeded()) {
        chatNpc("You're short on coins.", facialExpression = FacialExpression.GRUMPY)
        return
    }
    player.inventory.add(MaxCapes.MAX_CAPE)
    player.inventory.add(MaxCapes.MAX_HOOD)
    chatNpc(
        "Wear it well. There's not many of us.",
        facialExpression = FacialExpression.HAPPY,
    )
}

on_npc_option(npc = Npcs.MAX, option = "talk-to") {
    player.queue {
        val maxed = hasMaxStats(player)
        if (maxed) {
            chatNpc(
                "Well now. I don't often meet someone who's",
                "finished the job. Every skill, all the way up.",
                facialExpression = FacialExpression.HAPPY,
            )
        } else {
            chatNpc(
                "You're looking at the only man in Gielinor who",
                "ran out of things to train. Name's Max.",
                facialExpression = FacialExpression.CALM_TALK,
            )
        }
        when (
            options(
                "Can I buy a Max cape?",
                "What does the Max cape do?",
                "Who are you, really?",
                "Nothing, thanks.",
            )
        ) {
            1 -> {
                chatPlayer("Can I buy a Max cape?", facialExpression = FacialExpression.CALM_TALK)
                sellMaxCape()
            }
            2 -> {
                chatPlayer("What does the Max cape actually do?", facialExpression = FacialExpression.THINKING)
                chatNpc(
                    "It carries the best of every skillcape at once.",
                    "Mostly, though, it tells the world you finished.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Bring me a Fire cape, an Infernal cape or an",
                    "assembler and I'll show you what it can become.",
                    facialExpression = FacialExpression.HAPPY,
                )
            }
            3 -> {
                chatPlayer("Who are you, really?", facialExpression = FacialExpression.THINKING)
                chatNpc(
                    "Nobody special. I just never stopped.",
                    "One log, one ore, one fish at a time, for years.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "That's the whole secret. People keep asking me",
                    "for a shortcut and I keep disappointing them.",
                    facialExpression = FacialExpression.LAUGH,
                )
            }
        }
    }
}

on_npc_option(npc = Npcs.MAX, option = "trade") {
    player.queue { sellMaxCape() }
}
