package gg.rsmod.plugins.content.mechanics.exchange

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N71 (owner 10-16): a buy offer paid with banked coins says so in the chat. */
class GeBankCoinsMessageTests {
    @Test
    fun `the message names the banked coins`() {
        assertEquals("1,250,000 coins were taken from your bank to pay for this offer.", GrandExchangeInterface.bankCoinsMessage(1_250_000))
        assertEquals("7 coins were taken from your bank to pay for this offer.", GrandExchangeInterface.bankCoinsMessage(7))
    }

    @Test
    fun `it is sent only after the offer is placed and only when the bank paid`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/exchange/GrandExchangeInterface.kt").readText()
        val buy = source.substringAfter("if (selection.type == OfferType.BUY) {").substringBefore("} else {")
        val placed = buy.indexOf("fills = submitted.second")
        val message = buy.indexOf("if (fromBank > 0) player.message(bankCoinsMessage(fromBank))")
        assertTrue(placed in 0 until message, "message after a placed offer")
    }
}
