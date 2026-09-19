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

    @Test
    fun `a 20k-plus deposit is refused for 40 ticks after combat, only outside the guarded zones`() {
        val p = player(edgevilleBank)
        every { p.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        assertFalse(BankSecurity.blocksDeposit(p, 50_000L, now = 100), "never in combat")
        p.attr[BankSecurity.LAST_COMBAT_CYCLE_ATTR] = 100
        assertEquals(true, BankSecurity.blocksDeposit(p, 20_000L, now = 100), "exactly 20k right after combat")
        assertEquals(true, BankSecurity.blocksDeposit(p, 20_000L, now = 139), "tick 39 of 40")
        assertFalse(BankSecurity.blocksDeposit(p, 20_000L, now = 140), "24 seconds later")
        assertFalse(BankSecurity.blocksDeposit(p, 19_999L, now = 100), "below 20k")
        val safe = player(varrockWestBank)
        every { safe.attr } returns gg.rsmod.game.model.attr.AttributeMap().also { it[BankSecurity.LAST_COMBAT_CYCLE_ATTR] = 100 }
        assertFalse(BankSecurity.blocksDeposit(safe, 1_000_000L, now = 100), "safe-zone bank")
    }

    @Test
    fun `closing a bank outside a safe zone blocks eating and drinking for 5 ticks`() {
        val keys = listOf(gg.rsmod.game.model.timer.FOOD_DELAY, gg.rsmod.game.model.timer.COMBO_FOOD_DELAY, gg.rsmod.game.model.timer.POTION_DELAY)
        val danger = player(edgevilleBank)
        val dangerTimers = gg.rsmod.game.model.timer.TimerMap()
        every { danger.timers } returns dangerTimers
        BankSecurity.onBankClosed(danger)
        keys.forEach { assertEquals(5, dangerTimers[it]) }
        val safe = player(varrockWestBank)
        val safeTimers = gg.rsmod.game.model.timer.TimerMap()
        every { safe.timers } returns safeTimers
        BankSecurity.onBankClosed(safe)
        keys.forEach { assertFalse(safeTimers.has(it), "no block at a safe-zone bank") }
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
