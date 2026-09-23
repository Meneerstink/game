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
on_npc_option(npc = Npcs.AZZANADRA, option = "talk-to") {
    player.queue {
        val curses = player.attr[UnlockNpcRewards.ANCIENT_CURSES_REWARDED] == true
        if (curses) {
            chatNpc(
                "The old words still sit well on your tongue,",
                "mortal. Zaros is not so easily forgotten.",
                facialExpression = FacialExpression.CALM_TALK,
            )
        } else {
            chatNpc(
                "You stand before Azzanadra, Mahjarrat of Zaros.",
                "I have slept four ages beneath the desert sand",
                "waiting for someone willing to learn what the",
                "gods buried. You have that look about you.",
                facialExpression = FacialExpression.CALM_TALK,
            )
        }
        when (
            options(
                "Teach me the Ancient Curses.",
                "Tell me of Desert Treasure II.",
                "Switch me to the Ancient Curses.",
                "I'll leave you to your rest.",
            )
        ) {
            1 -> {
                chatPlayer(
                    "Teach me the Ancient Curses.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Then listen, and do not flinch.",
                    "These are not prayers. They are demands.",
                    facialExpression = FacialExpression.SECRETLY_TALKING,
                )
                if (UnlockNpcRewards.giveAncientHymnal(player)) {
                    chatNpc(
                        "Take this Ancient hymnal. Read it, and the",
                        "Curses of Zaros will answer to you.",
                        facialExpression = FacialExpression.CALM_TALK,
                    )
                }
            }
            2 -> {
                chatPlayer(
                    "Tell me of Desert Treasure II.",
                    facialExpression = FacialExpression.THINKING,
                )
                chatNpc(
                    "Four of my kin walk again, and they do not",
                    "walk kindly. Deal with them and the rings of",
                    "the ancients are yours to wear.",
                    facialExpression = FacialExpression.SECRETLY_TALKING,
                )
                if (options("I have dealt with them.", "Another time.") == 1) {
                    UnlockNpcRewards.completeDesertTreasureII(player)
                }
            }
            3 -> {
                chatPlayer(
                    "Switch me to the Ancient Curses.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.switchBook(
                    player,
                    gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.PrayerBook.ANCIENT,
                )
            }
        }
    }
}

on_item_option(item = Items.ANCIENT_HYMNAL, option = "read") {
    player.queue {
        chatPlayer("I read the Ancient Hymnal.")
        // Unlocks, shows the "unlocked" scroll and consumes the book (UnlockNpcRewards.unlockAncientCurses).
        UnlockNpcRewards.unlockAncientCurses(player)
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
                UnlockNpcRewards.unlockSummoning(player)
            }
            2 -> chatNpc("Use pouches from my shop to summon familiars. Your Summoning tab will be available once it is unlocked.")
        }
    }
}

on_npc_option(npc = Npcs.ARCHAEOLOGIST, option = "talk-to") {
    player.queue {
        val done = player.attr[UnlockNpcRewards.ANCIENT_MAGIC_REWARDED] == true
        if (done) {
            chatNpc(
                "Asgarnia Smith, at your service - though I see",
                "you've already been down into the pyramid.",
                facialExpression = FacialExpression.HAPPY,
            )
        } else {
            chatNpc(
                "Asgarnia Smith! Archaeologist extraordinaire,",
                "voted best in the field four years running.",
                facialExpression = FacialExpression.HAPPY,
            )
            chatNpc(
                "I've been digging out a pyramid south of here.",
                "Four diamonds, four guardians, and a very cross",
                "mahjarrat at the bottom of it.",
                facialExpression = FacialExpression.CALM_TALK,
            )
        }
        when (
            options(
                "I'll finish the Desert Treasure for you.",
                "Switch me to Ancient Magicks.",
                "Sell me an ancient staff.",
                "Good luck with the dig.",
            )
        ) {
            1 -> {
                chatPlayer(
                    "I'll finish the Desert Treasure for you.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Ha! You've got the look of someone who means it.",
                    "Mind the diamonds - and mind Azzanadra.",
                    facialExpression = FacialExpression.HAPPY,
                )
                // Completes Desert Treasure itself; the Ancient Magicks spellbook is that quest's reward.
                if (UnlockNpcRewards.unlockAncientMagic(player)) {
                    chatNpc(
                        "The ancient words are yours. Try not to point",
                        "them at anything I still want to excavate.",
                        facialExpression = FacialExpression.LAUGH,
                    )
                }
            }
            2 -> {
                chatPlayer(
                    "Switch me to Ancient Magicks.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                Spellbooks.select(player, Spellbook.ANCIENT)
            }
            3 -> player.openShop("Ancient Magicks Shop")
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
        val done = player.attr[UnlockNpcRewards.LUNAR_MAGIC_REWARDED] == true
        if (done) {
            chatNpc(
                "The moon still turns for you, dreamer.",
                facialExpression = FacialExpression.CALM_TALK,
            )
        } else {
            chatNpc(
                "You dream loudly, you know. I heard you from",
                "Lunar Isle.",
                facialExpression = FacialExpression.CALM_TALK,
            )
            chatNpc(
                "My people gave up war for moonlight and sleep.",
                "What we learned in those dreams is not taught",
                "anywhere else in Gielinor.",
                facialExpression = FacialExpression.SECRETLY_TALKING,
            )
        }
        when (
            options(
                "Teach me the Lunar spells.",
                "Switch me to the Lunar spellbook.",
                "What is a Lunar spell good for?",
                "Let me dream on it.",
            )
        ) {
            1 -> {
                chatPlayer(
                    "Teach me the Lunar spells.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Then sleep, and wake up knowing.",
                    facialExpression = FacialExpression.EYES_CLOSED,
                )
                if (UnlockNpcRewards.unlockLunarMagic(player)) {
                    chatNpc(
                        "The Lunar spellbook is open to you, and the",
                        "boat to Lunar Isle will not turn you away.",
                        facialExpression = FacialExpression.HAPPY,
                    )
                }
            }
            2 -> {
                chatPlayer(
                    "Switch me to the Lunar spellbook.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                Spellbooks.select(player, Spellbook.LUNAR)
            }
            3 -> {
                chatPlayer(
                    "What is a Lunar spell good for?",
                    facialExpression = FacialExpression.THINKING,
                )
                chatNpc(
                    "Not for killing. For mending, for carrying,",
                    "for sharing what you have with a friend.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Humans always ask what a thing kills first.",
                    facialExpression = FacialExpression.DISDAIN,
                )
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
