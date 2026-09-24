package gg.rsmod.plugins.content.npcs.bankers

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.content.inter.bank.BankPin
import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface

/**
 * The one banker conversation (Bank of Gielinor, cross-referenced with the RuneScape Wiki banker dialogue), shared by every bank npc:
 * the hand-listed bankers, and - through the Talk-to fallback - every other npc whose cache options include "Bank" and that has no
 * dialogue of its own (owner 2026-09-24: "controleer alle bankers op dialog ... nardah bankers geen dialog"; the Nardah bankers
 * 3046/5258/5260 only got the generic service greeting). PIN settings and the collection box open the real systems ([BankPin],
 * [GrandExchangeInterface]); they used to answer "Sorry, it is not implemented yet."
 */
object BankerDialogue {
    suspend fun chat(it: QueueTask) {
        it.chatNpc("Good day. How may I help you?", facialExpression = FacialExpression.HAPPY_TALKING)
        when (
            it.options(
                "I'd like to access my bank account, please.",
                "I'd like to check my PIN settings.",
                "I'd like to see my collection box.",
                "What is this place?",
            )
        ) {
            1 -> {
                it.chatPlayer("I'd like to access my bank account, please.", facialExpression = FacialExpression.HAPPY_TALKING)
                it.player.openBank()
            }
            2 -> {
                it.chatPlayer("I'd like to check my PIN settings.", facialExpression = FacialExpression.HAPPY_TALKING)
                BankPin.manage(it.player)
            }
            3 -> {
                it.chatPlayer("I'd like to see my collection box.", facialExpression = FacialExpression.HAPPY_TALKING)
                GrandExchangeInterface.openCollectionBox(it.player)
            }
            4 -> {
                it.chatPlayer("What is this place?", facialExpression = FacialExpression.UNCERTAIN)
                it.chatNpc(
                    "This is a branch of the Bank of Gielinor. We have branches",
                    "in many towns.",
                    facialExpression = FacialExpression.CHEERFUL,
                )
                when (it.options("And what do you do?", "Didn't you used to be called the Bank of Varrock?")) {
                    1 -> {
                        it.chatPlayer("And what do you do?", facialExpression = FacialExpression.DISREGARD)
                        it.chatNpc(
                            "We will look after your items and money for you. Leave",
                            "your valuables with us if you want to keep them safe.",
                            facialExpression = FacialExpression.CHEERFUL,
                        )
                    }
                    2 -> {
                        it.chatPlayer("Didn't you used to be called the Bank of Varrock?", facialExpression = FacialExpression.DISREGARD)
                        it.chatNpc(
                            "Yes we did, but people kept on coming into our branches",
                            "outside of Varrock and telling us that our signs were",
                            "wrong. They acted as if we didn't know what town we",
                            "were in or something.",
                            facialExpression = FacialExpression.TALKING,
                        )
                    }
                }
            }
        }
    }
}
