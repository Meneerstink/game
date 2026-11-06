package gg.rsmod.plugins.content.activity.toa

import kotlin.test.*

class ToaRoomLifecycleTests {
    private fun <T> permutations(list: List<T>): List<List<T>> =
        if (list.isEmpty()) listOf(emptyList()) else list.flatMap { head -> permutations(list - head).map { listOf(head) + it } }

    @Test fun `all 24 path orders require challenge then boss and four clears before Wardens`() {
        for (order in permutations(ToaPath.values().toList())) {
            val raid = ToaRaidProgress(ToaRaidSettings())
            assertNull(raid.wardens())
            assertEquals(0, raid.elapsedCycles(100))
            for ((n, path) in order.withIndex()) {
                assertNull(raid.boss(path))
                val challenge = assertNotNull(raid.challenge(path, 100 + n * 20))
                assertSame(challenge, raid.challenge(path, 101 + n * 20))
                assertTrue(challenge.clear(110 + n * 20))
                assertFalse(challenge.clear(111 + n * 20))
                val boss = assertNotNull(raid.boss(path))
                assertTrue(boss.clear(119 + n * 20))
                assertNull(raid.challenge(path, 120 + n * 20))
                assertEquals(n + 1 == 4, raid.wardensUnlocked)
            }
            val wardens = assertNotNull(raid.wardens())
            assertTrue(wardens.clear(200))
            assertEquals(100, raid.elapsedCycles(900))
            assertNull(raid.wardens())
        }
    }

    @Test fun `wipe resets only current room and ignores stale retry callbacks`() {
        val events = mutableListOf<ToaRoomEvent>()
        val raid = ToaRaidProgress(ToaRaidSettings()) { e, _ -> events += e }
        raid.challenge(ToaPath.HET, 0)!!.clear(1)
        raid.boss(ToaPath.HET)!!.clear(2)
        val failed = raid.challenge(ToaPath.SCABARAS, 3)!!
        assertNull(raid.challenge(ToaPath.CRONDIS, 3))
        assertTrue(failed.wipe(4))
        assertFalse(raid.cycle(4))
        assertTrue(raid.cycle(5))
        assertEquals(setOf(ToaPath.HET), raid.completedPaths)
        assertEquals(listOf(ToaRoomEvent.WIPE, ToaRoomEvent.RESET), events.takeLast(2))
        val retry = raid.challenge(ToaPath.SCABARAS, 6)!!
        assertNotEquals(failed.generation, retry.generation)
        assertFalse(failed.clear(6))
        assertFalse(failed.wipe(6))
        assertEquals(1, raid.failedAttempts)
        assertTrue(retry.clear(7))
    }

    @Test fun `clear and wipe on same tick never spend attempt in either order`() {
        for (clearFirst in listOf(true, false)) {
            val raid = ToaRaidProgress(ToaRaidSettings(invocations = setOf("Hardcore Run")))
            val run = raid.challenge(ToaPath.APMEKEN, 0)!!
            if (clearFirst) { run.clear(1); run.wipe(1) } else { run.wipe(1); run.clear(1) }
            raid.cycle(2)
            assertFalse(raid.failed)
            assertEquals(0, raid.failedAttempts)
            assertNotNull(raid.boss(ToaPath.APMEKEN))
        }
    }

    @Test fun `attempt invocations are enforced on team wipes and default is unlimited`() {
        for ((name, limit) in listOf("Try Again" to 10, "Persistence" to 5, "Softcore Run" to 3, "Hardcore Run" to 1, null to 15)) {
            val raid = ToaRaidProgress(ToaRaidSettings(invocations = name?.let { setOf(it) } ?: emptySet()))
            repeat(limit) { n ->
                raid.challenge(ToaPath.CRONDIS, n * 3)!!.wipe(n * 3 + 1)
                assertTrue(raid.cycle(n * 3 + 2))
                assertEquals(name != null && n + 1 == limit, raid.failed)
            }
        }
        assertFailsWith<IllegalArgumentException> { ToaRaidSettings(invocations = setOf("Try Again", "Hardcore Run")) }
    }
}
