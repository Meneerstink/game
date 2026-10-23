package gg.rsmod.plugins.content.inter.gameframe

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N240 (owner 2026-10-23): no gap between Trade and Report under the chatbox - Report takes the removed Assist tab's place. */
class ChatFilterBarTests {
    private fun u2(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    @Test
    fun `Report Abuse sits exactly where the hidden Assist tab was, straight after Trade`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val assist = library.data(3, 751, TabEvents.ASSIST_TAB)!!
            val trade = library.data(3, 751, 11)!!
            val report = library.data(3, 751, TabEvents.REPORT_TAB)!!
            assertEquals(u2(assist, 4), TabEvents.REPORT_X, "Report goes to the Assist tab's x")
            assertEquals(u2(trade, 4) + u2(trade, 8) + 1, TabEvents.REPORT_X, "right after Trade with the bar's 1 px spacing")
            assertTrue(u2(report, 8) <= u2(assist, 8), "Report fits the Assist slot")
        } finally {
            library.close()
        }
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/inter/gameframe/TabEvents.kt").readText()
        assertTrue("setComponentPosition(InterfaceDestination.CHATBOX_TABS.interfaceId, REPORT_TAB, REPORT_X, REPORT_Y)" in source)
    }
}
