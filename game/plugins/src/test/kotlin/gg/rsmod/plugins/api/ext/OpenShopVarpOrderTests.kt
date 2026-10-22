package gg.rsmod.plugins.api.ext

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N199 (owner 2026-10-22: a new player's first Quartermaster window showed no items, only the Stock/Price key): the shop window's
 * load script reads the container varps, which went out at the end of the cycle - after IF_OPENSUB. A player whose varps were
 * still 0 (every new account) got an empty grid and the sample headings until he opened the shop a second time.
 */
class OpenShopVarpOrderTests {
    @Test
    fun `the shop varps are flushed before the shop window opens`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/api/ext/PlayerExt.kt").readText()
        val body = source.substringAfter("fun Player.openShop(").substringBefore("\nfun ")
        val container = body.indexOf("setVarp(Varps.CURRENT_CONTAINER_ID, 4)")
        val samples = body.indexOf("setVarp(Varps.SECONDARY_CONTAINER_ID, -1)")
        val flush = body.indexOf("flushVarpsNow()")
        val open = body.indexOf("openInterface(interfaceId = 620")
        assertTrue(container in 0 until flush && samples in 0 until flush, "the varps are set before the flush")
        assertTrue(flush in 0 until open, "the flush comes before the window opens")

        val player = File("../src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        assertTrue("fun flushVarpsNow() = updateVarps()" in player, "the flush is the same varp sync the cycle runs")
    }
}
