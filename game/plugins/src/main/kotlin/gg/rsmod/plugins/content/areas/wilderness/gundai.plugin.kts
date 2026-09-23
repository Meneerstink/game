import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.inter.bank.BankPin
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface

val gundai = Npcs.GUNDAI

on_npc_option(gundai, option = "talk-to") {
    player.queue {
        gundaiDialogue(this)
    }
}

on_npc_option(gundai, option = "bank") {
    player.openBank()
}

suspend fun gundaiDialogue(it: QueueTask) {
    it.chatPlayer("Hello, what are you doing out here?")
    it.chatNpc("I'm a banker, the only one around these dangerous parts.")
    val option =
        it.options(
            "Cool, I'd like to access my bank account please.",
            "Right, so can I check my PIN settings?",
            "I'd like to collect items.",
            "Well, now I know.",
        )
    when (option) {
        1 -> accessBankAccount(it)
        2 -> checkPINSettings(it)
        3 -> collectItems(it)
        4 -> wellNowIKnow(it)
    }
}

suspend fun accessBankAccount(it: QueueTask) {
    it.chatPlayer("Cool, I'd like to access my bank account please.", facialExpression = FacialExpression.HAPPY_TALKING)
    it.player.openBank()
}

suspend fun checkPINSettings(it: QueueTask) {
    it.chatPlayer("Right, so can I check my PIN settings?")
    BankPin.manage(it.player)
}

suspend fun collectItems(it: QueueTask) {
    it.chatPlayer("I'd like to collect items.")
    GrandExchangeInterface.openCollectionBox(it.player)
}

suspend fun wellNowIKnow(it: QueueTask) {
    it.chatPlayer("Well, now I know.")
    it.chatNpc("Knowledge is power my friend.")
    // (End of dialogue)
}
