package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.Skills
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests

create_shop(
    "Ancient Magicks Shop",
    CoinCurrency(),
    containsSamples = false,
    purchasePolicy = PurchasePolicy.BUY_STOCK,
) {
    items[0] = ShopItem(Items.ANCIENT_STAFF, 10)
}

/*
 * Owner 2026-09-20: "all dialoge is not interesting make it interesting like for azzanadra say something like you
 * have unlocked a mysterious prayer please open your prayer book". Every line below is kept short enough to sit on
 * one chatbox row, and each one carries the facial expression that fits it instead of the default HAPPY_TALKING.
 */
/*
 * Owner 2026-09-26 (new-player foundation): Azzanadra no longer hands out the Ancient Curses or Desert Treasure II. He
 * takes part in the short quests (The Temple at Senntisten, Desert Treasure, Desert Treasure II - quests/foundation);
 * their rewards come only from those quests or the Quest Guide's permanent choice. Switching books stays here.
 */
on_npc_option(npc = Npcs.AZZANADRA, option = "talk-to") {
    player.queue {
        if (FoundationQuests.talk(this, Npcs.AZZANADRA)) return@queue
        val curses = player.attr[AncientCurses.UNLOCKED_ATTR] == true
        if (curses) {
            chatNpc(
                "The old words still sit well on your tongue,",
                "mortal. Zaros is not so easily forgotten.",
                facialExpression = FacialExpression.CALM_TALK,
            )
            when (options("Switch me to the Ancient Curses.", "Switch me to the normal prayers.", "I'll leave you to your rest.")) {
                1 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
                2 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)
            }
        } else {
            chatNpc(
                "You stand before Azzanadra, Mahjarrat of Zaros.",
                "The curses of my god are earned in his temple,",
                "not given away to passers-by.",
                facialExpression = FacialExpression.CALM_TALK,
            )
        }
    }
}

on_item_option(item = Items.ANCIENT_HYMNAL, option = "read") {
    player.queue {
        if (player.attr[AncientCurses.UNLOCKED_ATTR] == true) {
            messageBox("The hymnal's verses are the Ancient Curses you learned at the temple of Senntisten.")
        } else {
            messageBox("The hymnal is written in an ancient Zarosian tongue you cannot follow.")
        }
    }
}
// The reward lamps are real cache items, but their selection is server-owned. Keep the choice
// deliberately short and grouped so the player never has to click through a long flat list.
on_item_option(item = Items.EXPERIENCE_LAMP, option = "rub") {
    player.queue { useGeLamp(Items.EXPERIENCE_LAMP, 23_000.0, combatOnly = false) }
}

on_item_option(item = Items.COMBAT_LAMP_15390, option = "rub") {
    player.queue { useGeLamp(Items.COMBAT_LAMP_15390, 20_000.0, combatOnly = true) }
}

suspend fun QueueTask.useGeLamp(item: Int, xp: Double, combatOnly: Boolean) {
    val groups =
        if (combatOnly) {
            arrayOf(
                "Attack" to Skills.ATTACK,
                "Strength" to Skills.STRENGTH,
                "Defence" to Skills.DEFENCE,
                "Ranged" to Skills.RANGED,
                "Magic" to Skills.MAGIC,
                "Constitution" to Skills.CONSTITUTION,
            )
        } else {
            arrayOf(
                "Attack" to Skills.ATTACK,
                "Defence" to Skills.DEFENCE,
                "Strength" to Skills.STRENGTH,
                "Ranged" to Skills.RANGED,
                "Magic" to Skills.MAGIC,
                "Prayer" to Skills.PRAYER,
                "Cooking" to Skills.COOKING,
                "Woodcutting" to Skills.WOODCUTTING,
                "Fletching" to Skills.FLETCHING,
                "Fishing" to Skills.FISHING,
                "Firemaking" to Skills.FIREMAKING,
                "Crafting" to Skills.CRAFTING,
                "Smithing" to Skills.SMITHING,
                "Mining" to Skills.MINING,
                "Herblore" to Skills.HERBLORE,
                "Agility" to Skills.AGILITY,
                "Thieving" to Skills.THIEVING,
                "Slayer" to Skills.SLAYER,
                "Runecrafting" to Skills.RUNECRAFTING,
                "Hunter" to Skills.HUNTER,
                "Construction" to Skills.CONSTRUCTION,
                "Summoning" to Skills.SUMMONING,
                "Dungeoneering" to Skills.DUNGEONEERING,
            )
        }
    val pageSize = 5
    var page = 0
    while (true) {
        val pageItems = groups.drop(page * pageSize).take(pageSize)
        val labels = pageItems.map { it.first }.toMutableList()
        val hasMore = (page + 1) * pageSize < groups.size
        if (hasMore) labels += "More skills"
        labels += "Cancel"
        val choice = options(*labels.toTypedArray(), title = "Choose a skill (level 50+).")
        when {
            choice <= 0 -> return
            choice == labels.size -> return
            hasMore && choice == labels.size - 1 -> {
                page++
                continue
            }
            else -> {
                val selected = pageItems[choice - 1]
                if (player.skills.getMaxLevel(selected.second) < 50) {
                    player.message("You need at least level 50 in that skill.")
                    return
                }
                if (!player.inventory.remove(item).hasSucceeded()) return
                player.addXp(selected.second, xp)
                player.message("The lamp grants you ${xp.toInt()} ${selected.first} experience.")
                return
            }
        }
    }
}

on_npc_option(npc = Npcs.PIKKUPSTIX, option = "talk-to") {
    player.queue {
        chatNpc("Hello! I can unlock the Summoning skill and explain how to use your familiars.")
        when (options("Unlock Summoning.", "How does Summoning work?", "Goodbye.")) {
            1 -> {
                chatPlayer("Please unlock Summoning for me.")
                // One grant per account, shared with the Quest Guide (Wolf Whistle + supplies to level 55).
                gg.rsmod.plugins.content.newplayer.SummoningKit.claim(player)
            }
            2 -> chatNpc("Use pouches from my shop to summon familiars. Your Summoning tab will be available once it is unlocked.")
        }
    }
}

on_npc_option(npc = Npcs.ARCHAEOLOGIST, option = "talk-to") {
    player.queue {
        // Desert Treasure and Desert Treasure II start and end here (quests/foundation).
        if (FoundationQuests.talk(this, Npcs.ARCHAEOLOGIST)) return@queue
        chatNpc(
            "Asgarnia Smith, at your service - archaeologist",
            "extraordinaire, voted best in the field four years running.",
            facialExpression = FacialExpression.HAPPY,
        )
        when (options("Switch me to Ancient Magicks.", "Sell me an ancient staff.", "Good luck with the dig.")) {
            1 -> {
                chatPlayer("Switch me to Ancient Magicks.", facialExpression = FacialExpression.CALM_TALK)
                Spellbooks.select(player, Spellbook.ANCIENT)
            }
            2 -> player.openShop("Ancient Magicks Shop")
        }
    }
}
/*
 * Owner 2026-09-20: "when talking to oneiromancer and if u select one of the option the client crashes... he should
 * only unlock lunar spellbook". The crash was Recipe for Disaster's 13 reward lines overflowing the quest-finish
 * interface (fixed in QuestExt.buildQuestFinish); the Oneiromancer is now lunar-only regardless, and Recipe for
 * Disaster moved to Evil Dave below, who is an actual Recipe for Disaster character and already stands at the hub.
 */
on_npc_option(npc = Npcs.ONEIROMANCER, option = "talk-to") {
    player.queue {
        // Lunar Diplomacy starts and ends here (quests/foundation).
        if (FoundationQuests.talk(this, Npcs.ONEIROMANCER)) return@queue
        chatNpc(
            "You dream loudly, you know. I heard you from",
            "Lunar Isle.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        when (options("Switch me to the Lunar spellbook.", "What is a Lunar spell good for?", "Let me dream on it.")) {
            1 -> {
                chatPlayer("Switch me to the Lunar spellbook.", facialExpression = FacialExpression.CALM_TALK)
                Spellbooks.select(player, Spellbook.LUNAR)
            }
            2 -> {
                chatPlayer("What is a Lunar spell good for?", facialExpression = FacialExpression.THINKING)
                chatNpc(
                    "Not for killing. For mending, for carrying,",
                    "for sharing what you have with a friend.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc("Humans always ask what a thing kills first.", facialExpression = FacialExpression.DISDAIN)
            }
        }
    }
}
/*
 * Recipe for Disaster's unlock, moved off the Oneiromancer at the owner's request. Evil Dave is one of the
 * Culinaromancer's captives in that quest, so the Lumbridge-cellar chest access comes from the right character.
 */
on_npc_option(npc = Npcs.EVIL_DAVE, option = "talk-to") {
    player.queue {
        chatNpc(
            "Muahahaha! You dare approach the Evil Dave?",
            facialExpression = FacialExpression.EVIL,
        )
        chatNpc(
            "...mum says I have to be back upstairs by six.",
            facialExpression = FacialExpression.SAD,
        )
        when (
            options(
                "About the Culinaromancer's captives...",
                "Let me into the Culinaromancer's chest.",
                "Evil, you say?",
                "I'll leave you to it.",
            )
        ) {
            1, 2 -> {
                chatPlayer(
                    "About the Culinaromancer and his captives...",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Freed, the lot of us. Took some cooking.",
                    "The chest under Lumbridge is open to you now.",
                    facialExpression = FacialExpression.HAPPY,
                )
                UnlockNpcRewards.completeRecipeForDisaster(player)
            }
            3 -> {
                chatPlayer("Evil, you say?", facialExpression = FacialExpression.THINKING)
                chatNpc(
                    "EVIL! I once dyed every stew in Lumbridge black.",
                    facialExpression = FacialExpression.EVIL,
                )
                chatNpc(
                    "Nobody noticed. It was already stew.",
                    facialExpression = FacialExpression.DEPRESSED,
                )
            }
        }
    }
}

on_npc_option(npc = Npcs.LUCIEN, option = "talk-to") {
    player.queue {
        chatNpc("I can record your While Guthix Sleeps victory and unlock the Tormented demons and demonbane weapons.")
        when (options("Complete While Guthix Sleeps.", "Goodbye.")) {
            1 -> {
                chatPlayer("Please complete While Guthix Sleeps for me.")
                UnlockNpcRewards.completeWhileGuthixSleeps(player)
            }
        }
    }
}

listOf(Items.EMBERLIGHT, Items.SCORCHING_BOW, Items.PURGING_STAFF).forEach { weapon ->
    can_equip_item(weapon) {
        if (player.attr[UnlockNpcRewards.DEMONBANE_WEAPONS_UNLOCKED] == true) {
            true
        } else {
            player.message("You must complete While Guthix Sleeps before you can wear that weapon.")
            false
        }
    }
}

can_attack { attacker, target ->
    if (attacker is Player && target is Npc && target.id in 8349..8369 && attacker.attr[UnlockNpcRewards.TORMENTED_DEMONS_UNLOCKED] != true) {
        attacker.message("You must complete While Guthix Sleeps before you can attack Tormented demons.")
        false
    } else {
        true
    }
}

on_npc_option(npc = Npcs.KING_NARNODE_SHAREEN, option = "talk-to") {
    player.queue {
        // Monkey Madness II starts and ends here (quests/foundation); Monkey Madness itself stays below.
        if (FoundationQuests.talk(this, Npcs.KING_NARNODE_SHAREEN)) return@queue
        chatNpc("Welcome. I can record your Monkey Madness victory and award the experience you earned.")
        when (options("Complete Monkey Madness.", "Choose my experience focus.", "Goodbye.")) {
            1, 2 -> {
                when (options("35,000 Strength and Constitution each.", "35,000 Attack and Defence each.")) {
                    1 -> UnlockNpcRewards.completeMonkeyMadness(
                        player,
                        UnlockNpcRewards.MonkeyPair.STRENGTH_CONSTITUTION,
                    )
                    2 -> UnlockNpcRewards.completeMonkeyMadness(
                        player,
                        UnlockNpcRewards.MonkeyPair.ATTACK_DEFENCE,
                    )
                }
            }
        }
    }
}
