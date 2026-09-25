package gg.rsmod.game.model

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.TimerMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit T-02: a throwing timer plugin must fire once, be removed, and not stop the pawn's other timers,
 * hits or varp flush - with the timer timing itself unchanged. Driven through [Pawn.cycleTimerMap], the
 * pass [Pawn.timerCycle] runs; a [Pawn] cannot be built in this module's tests without a full World.
 */
class PawnTimerTickTests {
    private class Clock(val timers: TimerMap = TimerMap()) {
        var tick = 0
        val fired = mutableListOf<Pair<TimerKey, Int>>()
        val failures = mutableListOf<TimerKey>()
        var plugin: (TimerKey) -> Unit = {}

        fun tick() {
            tick++
            Pawn.cycleTimerMap(
                timers,
                fire = { key ->
                    fired += key to tick
                    plugin(key)
                },
                onFailure = { key, _ -> failures += key },
            )
        }
    }

    @Test
    fun `timer timing is unchanged - N passes after being set`() {
        val key = TimerKey()
        val clock = Clock()
        clock.timers[key] = 3
        repeat(5) { clock.tick() }
        assertEquals(listOf(key to 3), clock.fired)
        assertFalse(clock.timers.exists(key))
    }

    @Test
    fun `a re-armed repeating timer keeps its period`() {
        val key = TimerKey()
        val clock = Clock()
        clock.plugin = { if (it == key) clock.timers[key] = 5 }
        clock.timers[key] = 5
        repeat(15) { clock.tick() }
        assertEquals(listOf(5, 10, 15), clock.fired.map { it.second })
        assertTrue(clock.timers.has(key))
    }

    @Test
    fun `a throwing timer plugin fires once, is removed, and does not stop other timers`() {
        val broken = TimerKey()
        val healthy = TimerKey()
        val clock = Clock()
        clock.plugin = { if (it == broken) throw IllegalStateException("plugin bug") }
        clock.timers[broken] = 1
        clock.timers[healthy] = 1
        repeat(3) { clock.tick() }
        assertEquals(1, clock.fired.count { it.first == broken }, "fired exactly once, not every tick")
        assertEquals(1, clock.fired.count { it.first == healthy })
        assertEquals(listOf(broken), clock.failures)
        assertFalse(clock.timers.exists(broken))
    }

    @Test
    fun `a throwing timer that is not removed on zero is still removed`() {
        val key = TimerKey(removeOnZero = false)
        val clock = Clock()
        clock.plugin = { throw NotImplementedError("TODO() in a timer") }
        clock.timers[key] = 1
        repeat(3) { clock.tick() }
        assertEquals(1, clock.fired.size)
        assertFalse(clock.timers.exists(key))
    }

    @Test
    fun `paused and count-up timers never expire`() {
        val paused = TimerKey()
        val forward = TimerKey(tickForward = true)
        val clock = Clock()
        clock.timers[paused] = 1
        clock.timers.pause(paused)
        clock.timers[forward] = 0
        repeat(3) { clock.tick() }
        assertEquals(emptyList<Pair<TimerKey, Int>>(), clock.fired)
        assertEquals(1, clock.timers[paused])
        assertEquals(3, clock.timers[forward])
    }

    @Test
    fun `timerCycle uses the isolated pass`() {
        val pawn = File("src/main/kotlin/gg/rsmod/game/model/entity/Pawn.kt").readText()
        val timerCycle = pawn.substringAfter("fun timerCycle() {").substringBefore("\n    }")
        assertTrue("cycleTimerMap(" in timerCycle)
        assertTrue("onFailure" in timerCycle)
    }

    @Test
    fun `hits and the varp flush run even when the timer pass fails`() {
        // Audit T-02: Player.cycle / Npc.cycle keep hitsCycle out of the timer pass's failure path.
        val player = File("src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        val playerCycle = player.substringAfter("override fun cycle() {").substringBefore("\n    }\n")
        assertTrue(playerCycle.indexOf("timerCycle()") < playerCycle.indexOf("} finally {"))
        assertTrue(playerCycle.substringAfter("} finally {").contains("hitsCycle()"))
        assertTrue(playerCycle.substringAfterLast("} finally {").contains("updateVarps()"))
        val npc = File("src/main/kotlin/gg/rsmod/game/model/entity/Npc.kt").readText()
        val npcCycle = npc.substringAfter("override fun cycle() {").substringBefore("\n    }\n")
        assertTrue(npcCycle.substringAfter("} finally {").contains("hitsCycle()"))
    }

    @Test
    fun `a throwing world timer is isolated too`() {
        val world = File("src/main/kotlin/gg/rsmod/game/model/World.kt").readText()
        val timers = world.substringAfter("timersCopy.forEach").substringBefore("Tick all timers down")
        assertTrue("catch (e: Throwable)" in timers)
        assertTrue("if (failed || !timers.has(key))" in timers)
    }
}
