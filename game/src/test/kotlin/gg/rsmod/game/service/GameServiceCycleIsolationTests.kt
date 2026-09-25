package gg.rsmod.game.service

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameServiceCycleIsolationTests {
    private val service = File("src/main/kotlin/gg/rsmod/game/service/GameService.kt").readText()

    @Test
    fun `world cycle failure is isolated from the game loop`() {
        val worldCycle = service.indexOf("world.cycle()")
        assertTrue(worldCycle >= 0, "GameService must invoke the world cycle")

        val boundary = service.substring(worldCycle.coerceAtLeast(0), (worldCycle + 260).coerceAtMost(service.length))
        // Audit T-01: any non-fatal Throwable, not only Exception.
        assertTrue(boundary.contains("catch (e: Throwable)"), "world cycle lacks an exception boundary")
        assertTrue(boundary.contains("Error with world cycle"), "world cycle failure is not logged")
    }

    @Test
    fun `the game loop catches every throwable and always schedules the next tick`() {
        // Audit T-01: scheduleAtFixedRate silently cancelled every later tick when a Throwable escaped.
        assertFalse("executor.scheduleAtFixedRate(" in service)
        val tick = service.substringAfter("private fun tick() {").substringBefore("\n    }")
        assertTrue("catch (t: Throwable)" in tick)
        assertTrue(tick.substringAfter("} finally {").contains("scheduleNextTick()"))
        val cycle = service.substringAfter("private fun cycle() {")
        assertFalse("catch (e: Exception)" in cycle, "every safety net in the cycle catches Throwable")
        assertTrue("game-watchdog" in service, "a stalled loop is reported by the watchdog")
    }

    @Test
    fun `missed ticks are dropped instead of being run as a burst`() {
        // Audit T-08: next = max(previous deadline + period, now).
        val period = TimeUnit.MILLISECONDS.toNanos(600)
        var deadline = 0L
        val starts = mutableListOf<Long>()
        var now = 0L
        repeat(6) { i ->
            starts += now
            val work = if (i == 1) TimeUnit.MILLISECONDS.toNanos(3_000) else TimeUnit.MILLISECONDS.toNanos(10)
            now += work
            deadline = GameService.nextCycleDeadline(deadline, period, now)
            now = maxOf(now, deadline)
        }
        val gapsMs = starts.zipWithNext { a, b -> TimeUnit.NANOSECONDS.toMillis(b - a) }
        // tick 1 overran by 2.4 s: tick 2 starts right after it, and from then on 600 ms apart again.
        assertEquals(listOf(600L, 3_000L, 600L, 600L, 600L), gapsMs)
    }

    @Test
    fun `an on-time loop keeps a fixed period without drift`() {
        val period = TimeUnit.MILLISECONDS.toNanos(600)
        assertEquals(period, GameService.nextCycleDeadline(0L, period, TimeUnit.MILLISECONDS.toNanos(50)))
        assertEquals(2 * period, GameService.nextCycleDeadline(period, period, period + TimeUnit.MILLISECONDS.toNanos(599)))
    }
}
