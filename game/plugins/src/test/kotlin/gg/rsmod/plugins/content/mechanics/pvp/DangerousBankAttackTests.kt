package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.inter.bank.Bank
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner 2026-09-19: "only 2 tick weapons should cancel the banking of every pker in a dangerous bank while being attacked"
 * ([BankSecurity.keepsBankOpen], used by `Combat.postAttack`).
 */
class DangerousBankAttackTests {
    private val edgevilleBank = Tile(3094, 3491, 0) // dangerous (no guarded zone)
    private val varrockWestBank = Tile(3185, 3436, 0) // guarded

    @Test
    fun `in a dangerous bank only attacks of at most two ticks close the bank or deposit box`() {
        listOf(Bank.BANK_INTERFACE_ID, Bank.DEPOSIT_BOX_INTERFACE_ID).forEach { modal ->
            for (delay in 1..6) {
                val kept = BankSecurity.keepsBankOpen(player(Tile(0, 0, 0)), player(edgevilleBank, modal), delay)
                assertEquals(delay > 2, kept, "interface $modal, $delay-tick attack")
            }
        }
    }

    @Test
    fun `npc attacks, guarded banks and other interfaces keep the old close-on-attack behaviour`() {
        val npc = mockk<Npc>(relaxed = true)
        assertFalse(BankSecurity.keepsBankOpen(npc, player(edgevilleBank, Bank.BANK_INTERFACE_ID), 5), "npc attacker")
        assertFalse(BankSecurity.keepsBankOpen(player(Tile(0, 0, 0)), player(varrockWestBank, Bank.BANK_INTERFACE_ID), 5), "guarded bank")
        assertFalse(BankSecurity.keepsBankOpen(player(Tile(0, 0, 0)), player(edgevilleBank, 670), 5), "equipment stats, not a bank")
        assertFalse(BankSecurity.keepsBankOpen(player(Tile(0, 0, 0)), player(edgevilleBank, -1), 5), "nothing open")
    }

    private fun player(
        tile: Tile,
        modal: Int = -1,
    ): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.tile } returns tile
        every { p.interfaces.getModal() } returns modal
        return p
    }
}
