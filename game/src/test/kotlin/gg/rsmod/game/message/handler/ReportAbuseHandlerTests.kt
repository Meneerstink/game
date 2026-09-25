package gg.rsmod.game.message.handler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit S-14: one report per minute per player, one bounded log line per report. */
class ReportAbuseHandlerTests {
    @Test
    fun `a second report within the cooldown is refused`() {
        assertTrue(ReportAbuseHandler.cooldownElapsed(null, 500))
        assertFalse(ReportAbuseHandler.cooldownElapsed(500, 501))
        assertFalse(ReportAbuseHandler.cooldownElapsed(500, 500 + ReportAbuseHandler.COOLDOWN_CYCLES - 1))
        assertTrue(ReportAbuseHandler.cooldownElapsed(500, 500 + ReportAbuseHandler.COOLDOWN_CYCLES))
        assertTrue(ReportAbuseHandler.cooldownElapsed(500, 3), "the cycle counter wrapped")
    }

    @Test
    fun `comments are one line and capped`() {
        assertEquals("a b c", ReportAbuseHandler.sanitizeComment("a\tb\nc"))
        assertEquals(ReportAbuseHandler.MAX_COMMENT_LENGTH, ReportAbuseHandler.sanitizeComment("x".repeat(10_000)).length)
    }
}
