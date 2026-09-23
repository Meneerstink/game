package gg.rsmod.plugins.content.npcs

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player

/**
 * Conversations that sell something, ported from the Void donor (2011-era, `content/area/**/Bartender*.kt`), for npcs that have no
 * hand-written dialogue here. The Talk-to fallback (talk_to_fallback.plugin.kts) plays these before the wiki transcript, because a
 * transcript records the words but not the purchase. Alfred Grimhand's Barcrawl branches are left out (quests are parked).
 */
object ServiceDialogues {
    private val E_QUIZ = FacialExpression.UNCERTAIN
    private val E_NEUTRAL = FacialExpression.CALM_TALK
    private val E_ANGRY = FacialExpression.ANGRY
    private val E_HAPPY = FacialExpression.HAPPY_TALKING

    private val conversations: Map<Int, suspend QueueTask.(Npc) -> Unit> =
        mapOf(
            731 to { _ -> jollyBoar() },
            733 to { _ -> blueMoon() },
            734 to { _ -> rustyAnchor() },
            11706 to { _ -> rustyAnchor() },
            11707 to { _ -> rustyAnchor() },
            737 to { _ -> forestersArms() },
            738 to { _ -> flyingHorse() },
        )

    fun has(npcId: Int): Boolean = npcId in conversations

    suspend fun play(
        task: QueueTask,
        npc: Npc,
    ): Boolean {
        val conversation = conversations[npc.id] ?: return false
        conversation(task, npc)
        return true
    }

    /** Void `Player.buy`: coins out, item in, or the player's complaint (inventory full: the standard message). */
    private suspend fun QueueTask.buy(
        item: Int,
        cost: Int,
        complaint: String = "Oh dear. I don't seem to have enough money.",
    ): Boolean {
        if (player.inventory.getItemCount(Items.COINS_995) < cost) {
            chatPlayer(complaint, facialExpression = FacialExpression.SAD)
            return false
        }
        val coinsStayStacked = player.inventory.getItemCount(Items.COINS_995) > cost
        if (player.inventory.freeSlotCount == 0 && coinsStayStacked) {
            player.message("You don't have enough inventory space.")
            return false
        }
        player.inventory.remove(Items.COINS_995, cost)
        player.inventory.add(item)
        return true
    }

    private suspend fun QueueTask.jollyBoar() {
        chatNpc("Can I help you?", facialExpression = E_QUIZ)
        when (options("I'll have a beer please.", "Any hints where I can go adventuring?", "Heard any good gossip?")) {
            1 -> {
                chatPlayer("I'll have a pint of beer please.", facialExpression = E_NEUTRAL)
                chatNpc("Ok, that'll be two coins please.", facialExpression = E_NEUTRAL)
                if (buy(Items.BEER, 2)) {
                    chatPlayer("Ok, here you go.", facialExpression = E_NEUTRAL)
                    player.message("You buy a pint of beer!")
                }
            }
            2 -> {
                chatPlayer("Any hints where I can go adventuring?", facialExpression = E_NEUTRAL)
                chatNpc("Ooh, now. Let me see...", facialExpression = E_NEUTRAL)
                chatNpc(
                    "Well there is the Varrock sewers. There are tales of untold horrors coming out at night and stealing babies from houses.",
                    facialExpression = E_NEUTRAL,
                )
                chatPlayer("Sounds perfect! Where's the entrance?", facialExpression = E_QUIZ)
                chatNpc("It's just to the east of the palace.", facialExpression = E_NEUTRAL)
            }
            3 -> {
                chatPlayer("Heard any good gossip?", facialExpression = E_NEUTRAL)
                chatNpc(
                    "I'm not that well up on the gossip out here. I've heard that the bartender in the Blue Moon Inn has gone a little crazy, he keeps claiming he is part of something called an online game.",
                    facialExpression = E_NEUTRAL,
                )
                chatNpc("What that means, I don't know. That's probably old news by now though.", facialExpression = E_NEUTRAL)
            }
        }
    }

    private suspend fun QueueTask.blueMoon() {
        chatNpc("What can I do yer for?", facialExpression = E_QUIZ)
        when (options("A glass of your finest ale please.", "Can you recommend where an adventurer might make his fortune?")) {
            1 -> {
                chatPlayer("A glass of your finest ale please.", facialExpression = E_NEUTRAL)
                chatNpc("No problemo. That'll be 2 coins.", facialExpression = E_NEUTRAL)
                if (buy(Items.BEER, 2)) player.message("You buy a pint of beer.")
            }
            2 -> {
                chatPlayer("Can you recommend where an adventurer might make his fortune?", facialExpression = E_QUIZ)
                chatNpc("Ooh I don't know if I should be giving away information, makes the game too easy.", facialExpression = E_ANGRY)
                when (options("Oh ah well...", "Game? What are you talking about?", "Just a small clue?", "Do you know where I can get some good equipment?")) {
                    1 -> chatPlayer("Oh ah well...", facialExpression = E_NEUTRAL)
                    2 -> {
                        chatPlayer("Game? What are you talking about?", facialExpression = E_QUIZ)
                        chatNpc("This world around us... is an online game... called ${player.world.gameContext.name}.", facialExpression = E_ANGRY)
                        chatPlayer("Nope, still don't understand what you are talking about. What does 'online' mean?", facialExpression = E_QUIZ)
                        chatNpc(
                            "It's a sort of connection between magic boxes across the world, big boxes on people's desktops and little ones people can carry. They can talk to each other to play games.",
                            facialExpression = E_ANGRY,
                        )
                        chatPlayer("I give up. You're obviously completely mad!", facialExpression = E_ANGRY)
                    }
                    3 -> {
                        chatPlayer("Just a small clue?", facialExpression = E_QUIZ)
                        chatNpc("Go and talk to the bartender at the Jolly Boar Inn, he doesn't seem to mind giving away clues.", facialExpression = E_ANGRY)
                    }
                    4 -> {
                        chatPlayer("Do you know where I can get some good equipment?", facialExpression = E_QUIZ)
                        chatNpc("Well, there's the sword shop across the road, or there's also all sorts of shops up around the market.", facialExpression = E_NEUTRAL)
                    }
                }
            }
        }
    }

    private suspend fun QueueTask.rustyAnchor() {
        when (options("Could I buy a beer please?", "Have you heard any rumours here?")) {
            1 -> {
                chatPlayer("Could I buy a beer please?", facialExpression = E_QUIZ)
                chatNpc("Sure, that will be 2 gold coins please.", facialExpression = E_NEUTRAL)
                if (buy(Items.BEER, 2, "I don't have enough coins.")) player.message("You buy a pint of beer!")
            }
            2 -> {
                chatPlayer("Have you heard any rumours here?", facialExpression = E_NEUTRAL)
                chatNpc(
                    "Well, there was a guy in here earlier saying the goblins up by the mountain are arguing again, about the colour of their armour of all things.",
                    facialExpression = E_NEUTRAL,
                )
            }
        }
    }

    private suspend fun QueueTask.flyingHorse() {
        chatNpc("Would you like to buy a drink?", facialExpression = E_QUIZ)
        chatPlayer("What do you serve?", facialExpression = E_QUIZ)
        chatNpc("Beer!", facialExpression = E_HAPPY)
        when (options("I'll have a beer then.", "I'll not have anything then.")) {
            1 -> {
                chatPlayer("I'll have a beer then.", facialExpression = E_NEUTRAL)
                chatNpc("Ok, that'll be two coins.", facialExpression = E_NEUTRAL)
                if (buy(Items.BEER, 2)) player.message("You buy a pint of beer.")
            }
            2 -> chatPlayer("I'll not have anything then.", facialExpression = E_NEUTRAL)
        }
    }

    private suspend fun QueueTask.forestersArms() {
        chatNpc("Good morning, what would you like?", facialExpression = E_QUIZ)
        when (options("What do you have?", "I'll have a beer then.", "I don't really want anything thanks.")) {
            1 -> {
                chatPlayer("What do you have?", facialExpression = E_QUIZ)
                chatNpc("Well we have beer, or if you want some food, we have our home made stew and meat pies.", facialExpression = E_NEUTRAL)
                when (options("Beer please.", "I'll try the meat pie.", "Could I have some stew please?", "I don't really want anything thanks.")) {
                    1 -> {
                        chatPlayer("Beer please.", facialExpression = E_NEUTRAL)
                        chatNpc("One beer coming up. Ok, that'll be two coins.", facialExpression = E_NEUTRAL)
                        if (buy(Items.BEER, 2)) player.message("You buy a pint of beer.")
                    }
                    2 -> {
                        chatPlayer("I'll try the meat pie.", facialExpression = E_NEUTRAL)
                        chatNpc("Ok, that'll be 16 coins.", facialExpression = E_NEUTRAL)
                        if (buy(Items.MEAT_PIE, 16)) player.message("You buy a nice hot meat pie.")
                    }
                    3 -> {
                        chatPlayer("Could I have some stew please?", facialExpression = E_QUIZ)
                        chatNpc("A bowl of stew, that'll be 20 coins please.", facialExpression = E_NEUTRAL)
                        if (buy(Items.STEW, 20)) player.message("You buy a bowl of home made stew.")
                    }
                    4 -> chatPlayer("I don't really want anything thanks.", facialExpression = E_NEUTRAL)
                }
            }
            2 -> {
                chatPlayer("I'll have a beer then.", facialExpression = E_NEUTRAL)
                chatNpc("Ok, that'll be two coins.", facialExpression = E_NEUTRAL)
                // Void charges 20 here (a typo against its own "two coins" line); the spoken price is used.
                if (buy(Items.BEER, 2)) player.message("You buy a pint of beer.")
            }
            3 -> chatPlayer("I don't really want anything thanks.", facialExpression = E_NEUTRAL)
        }
    }
}
