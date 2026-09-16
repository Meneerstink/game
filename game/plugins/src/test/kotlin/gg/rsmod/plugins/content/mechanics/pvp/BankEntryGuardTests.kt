package gg.rsmod.plugins.content.mechanics.pvp

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Deadman source contract: every bank interface entry remains behind the shared guarded-city bank gate. */
class BankEntryGuardTests {
    @Test
    fun `bank and deposit box reject skulled players inside a guarded city before opening`() {
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
    fun `skulled entry into a guarded city closes an already open bank modal before guards spawn`() {
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
                    "CityGuards.kt",
                ),
            )
        val entry = source.substringAfter("if (existing.isEmpty()) {")
        val spawn = entry.indexOf("spawnReactiveGuards(world, player)")
        assertTrue(spawn >= 0)
        assertTrue(entry.indexOf("InterfaceDestination.MAIN_SCREEN") in 0 until spawn)
        assertTrue(entry.indexOf("InterfaceDestination.TAB_AREA") in 0 until spawn)
    }
}
