package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.StockType
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N275 (owner 2026-10-24, bug-20261024-102252: "breach trader laat niet zien hoeveel punten een player heeft"): a points shop shows
 * the player's balance at the top of the window (the title) and on its help line, says it in the chat when it opens, and keeps
 * both up to date after a purchase.
 */
class PointShopBalanceTests {
    @Test
    fun `the Breach Trader's window title and help line carry the balance, the chat line says it`() {
        val attr = AttributeMap()
        attr[BreachPoints.BALANCE] = 12_345
        val sent = ArrayList<Message>()
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns attr
        every { player.write(*anyVararg<Message>()) } answers { sent += firstArg<Array<Message>>() }
        val currency = PointCurrency("breach point", "breach points", BreachPoints.BALANCE)
        val shop = Shop("Breach Trader", StockType.NORMAL, PurchasePolicy.BUY_NONE, currency, arrayOfNulls(1), arrayOfNulls(1), false)
        currency.showBalance(player, shop)
        val texts = sent.filterIsInstance<IfSetTextMessage>().associate { it.child to it.text }
        assertEquals("Breach Trader - Breach Points: <col=ffffff>12,345</col>", texts[20])
        assertEquals("You currently have 12,345 breach points.", texts[24])
        assertEquals("You have 12,345 Breach Points.", currency.balanceMessage(player))
    }

    @Test
    fun `every point shop shows the balance when it opens and after each purchase`() {
        val open = File("src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt").readText()
        assertTrue("points.showBalance(this, currentShop)" in open && "message(points.balanceMessage(this))" in open)
        val buy = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/shops/PointCurrency.kt").readText()
        assertTrue("showBalance(p, shop)" in buy)
    }
}
