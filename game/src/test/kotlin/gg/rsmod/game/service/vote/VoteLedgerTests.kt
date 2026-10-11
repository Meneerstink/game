package gg.rsmod.game.service.vote

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Owner 2026-10-10 voting system: one reward per site per 12 hours per account, kept until the player is online. */
class VoteLedgerTests {
    private val hour = 60 * 60 * 1000L

    @Test
    fun `one reward per site per 12 hours, other sites count separately`() {
        val ledger = VoteLedger(null)
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.record("rspslist", "Anudd", now = 0))
        assertEquals(VoteLedger.Result.COOLDOWN, ledger.record("rspslist", "anudd", now = 11 * hour))
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.record("topg", "anudd", now = 11 * hour))
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.record("rspslist", "ANUDD", now = 12 * hour))
        assertEquals(3, ledger.pending("anudd"))
        assertEquals(hour, ledger.waitMillis("topg", "anudd", now = 22 * hour))
    }

    @Test
    fun `underscore and space are the same account and bad names are refused`() {
        val ledger = VoteLedger(null)
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.record("topg", "big_bob", now = 0))
        assertEquals(VoteLedger.Result.COOLDOWN, ledger.record("topg", "Big Bob", now = 1))
        assertEquals(VoteLedger.Result.BAD_NAME, ledger.record("topg", "<script>", now = 0))
        assertEquals(VoteLedger.Result.BAD_NAME, ledger.record("topg", "waytoolongname1", now = 0))
        assertEquals(1, ledger.takePending("big bob"))
        assertEquals(0, ledger.takePending("big_bob"))
    }

    @Test
    fun `pending rewards and cooldowns survive a restart`() {
        val file = Files.createTempDirectory("votes").resolve("ledger.json")
        VoteLedger(file).apply {
            record("rspslist", "anudd", now = 0)
            record("runelocus", "anudd", now = 0)
            giveBack("other", 2)
        }
        val reloaded = VoteLedger(file)
        assertEquals(2, reloaded.pending("anudd"))
        assertEquals(2, reloaded.pending("other"))
        assertEquals(VoteLedger.Result.COOLDOWN, reloaded.record("rspslist", "anudd", now = hour))
    }
}
