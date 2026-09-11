package gg.rsmod.plugins.content.areas.draynor

import gg.rsmod.plugins.content.quests.*
import gg.rsmod.plugins.content.quests.impl.PrinceAliRescue

/**
 * Ported from Void donor `content/area/misthalin/draynor_village/{DraynorJailGuard,Joe}.kt`.
 * Drives the Prince Ali Rescue stage 4-6 hand-off: talking to Prince Ali's personal guard reveals
 * his weakness for beer, then giving him beer (1 to soften him up, 2 more to get him properly
 * drunk) advances the quest to stage 6, after which Lady Keli can be tied up.
 */
val guardPrinceAliRescue = PrinceAliRescue

on_npc_option(npc = Npcs.JAIL_GUARD_917, option = "talk-to") {
    player.queue {
        when (player.getCurrentStage(guardPrinceAliRescue)) {
            4 -> {
                chatPlayer("Hi. Who are you guarding here?")
                chatNpc("Can't say. It's all very secret. You should get out of here. I am not supposed to talk while I guard.")
                when (
                    options(
                        "Hey, chill out. I won't cause you trouble.",
                        "Tell me about the life of a guard.",
                        "I had better leave, I don't want trouble.",
                    )
                ) {
                    FIRST_OPTION -> {
                        chatPlayer("Hey, chill out, I won't cause you trouble. I was just wondering what you do to relax.")
                        chatNpc("You never relax with these people, but it's a good career for a young man.")
                    }

                    SECOND_OPTION -> {
                        chatPlayer("Tell me about the life of a guard.")
                        chatNpc("Well, the hours are good, but most of those hours are a drag.")
                        chatNpc("Really, after working here, there's only time for a drink or three. All us guards go to the same pub and drink ourselves stupid. I can't resist the sight of a really cold beer.")
                    }

                    THIRD_OPTION -> chatPlayer("I had better leave, I don't want trouble.")
                }
            }

            5 -> {
                chatPlayer("How are you? Still ok? Not too drunk?")
                chatNpc("No, I don't get drunk from only one drink. I reckon I'd need at least two more for that. Still, thanks for the beer.")
            }

            in 6..7 -> chatNpc("Franksh! That wash jusht what I needed. No more beersh for me, thanksh.")

            else ->
                if (player.finishedQuest(guardPrinceAliRescue)) {
                    chatNpc("The Prince got away, I am in trouble. I better not talk to you, they are not sure I was drunk.")
                } else {
                    chatNpc("Can't say. It's all very secret. You should get out of here. I am not supposed to talk while I guard.")
                }
        }
    }
}

on_item_on_npc(item = Items.BEER, npc = Npcs.JAIL_GUARD_917) {
    player.queue {
        when (player.getCurrentStage(guardPrinceAliRescue)) {
            4 -> {
                chatPlayer("I have some beer here, fancy one?")
                chatNpc("Ah, that would be lovely. Only one though, just to wet my throat.")
                chatPlayer("Of course. It must be tough being here without a drink.")
                player.inventory.remove(Items.BEER)
                itemMessageBox("You hand a beer to the guard. He drinks it in seconds.", Items.BEER)
                chatNpc("That was perfect! I can't thank you enough.")
                player.advanceToNextStage(guardPrinceAliRescue)
            }

            5 -> {
                if (player.inventory.getItemCount(Items.BEER) >= 2) {
                    chatPlayer("Would you care for another beer, my friend?")
                    chatNpc("I'd better not. I don't want to be drunk on duty.")
                    chatPlayer("Here, just keep these for later. I hate to see a thirsty guard.")
                    player.inventory.remove(Items.BEER, 2)
                    itemMessageBox("You hand two more beers to the guard. He takes a sip of one, and then he quickly drinks them both.", Items.BEER)
                    chatNpc("Franksh! That wash jusht what I need to shtay on guard. No more beersh, I don't want to get drunk.")
                    player.advanceToNextStage(guardPrinceAliRescue)
                } else {
                    chatPlayer("Would you care for another beer, my friend?")
                    chatNpc("I reckon I'd need at least two more to get properly drunk. One's not going to cut it.")
                }
            }

            else -> chatPlayer("I don't see any need to give the guard my beer. I'll keep it for myself.")
        }
    }
}
