package gg.rsmod.plugins.content.mechanics.pvp

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** M1 source contract: every bank interface entry remains behind the shared PvP bank gate. */
class BankEntryGuardTests {
    @Test
    fun `bank and deposit box reject blocked players before opening`() {
        val source =
            Files.readString(
                Path.of(
                    "src",
                    "main",
                    "kotlin",
                    "gg",
                    "rsmod",
                    "plugins",
                    "content",
                    "inter",
                    "bank",
                    "Bank.kt",
                ),
            )
        val bankOpen = source.substringAfter("fun open(player: Player)").substringBefore("fun openDepositBox")
        val depositOpen = source.substringAfter("fun openDepositBox(player: Player)")

        assertTrue("BankSecurity.denyBank(player)" in bankOpen)
        assertTrue("BankSecurity.denyBank(player)" in depositOpen)
        assertTrue(bankOpen.indexOf("denyBank(player)") < bankOpen.indexOf("openInterface(BANK_INTERFACE_ID"))
        assertTrue(depositOpen.indexOf("denyBank(player)") < depositOpen.indexOf("openInterface(DEPOSIT_BOX_INTERFACE_ID"))
    }

    @Test
    fun `blocked entry closes an already open bank modal before guards attack`() {
        val source =
            Files.readString(
                Path.of(
                    "src",
                    "main",
                    "kotlin",
                    "gg",
                    "rsmod",
                    "plugins",
                    "content",
                    "mechanics",
                    "pvp",
                    "BankSecurity.kt",
                ),
            )
        val entry = source.substringAfter("if (!inBank || wasInBank || !isBankBlocked(player)) return")
        val guard = entry.indexOf("player.timers[BANK_ENTRY_TIMER]")
        assertTrue(guard >= 0)
        assertTrue(entry.indexOf("InterfaceDestination.MAIN_SCREEN") in 0 until guard)
        assertTrue(entry.indexOf("InterfaceDestination.TAB_AREA") in 0 until guard)
    }
}
