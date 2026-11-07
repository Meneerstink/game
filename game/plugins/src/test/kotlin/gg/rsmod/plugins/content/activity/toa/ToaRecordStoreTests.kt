package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.content.activity.toa.core.ToaMode
import gg.rsmod.plugins.content.activity.toa.ui.ToaScoreboardUi
import java.nio.file.Files
import gg.rsmod.game.model.PlayerUID
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class ToaRecordStoreTests {
    private fun tempFile() = Files.createTempDirectory("toa-record-test").toFile().resolve("records.json").also { it.parentFile.deleteOnExit(); it.deleteOnExit() }

    @Test fun `all 24 mode and group records survive restart with the original names`() {
        val file = tempFile()
        val store = ToaRecordStore(file)
        for (mode in ToaMode.values()) for (size in 1..8) {
            assertNull(store.get(mode, size))
            assertTrue(store.submit(mode, 1000 + mode.ordinal * 100 + size, (1..size).map { "Member $it" }))
        }
        val restarted = ToaRecordStore(file)
        for (mode in ToaMode.values()) for (size in 1..8) {
            val record = assertNotNull(restarted.get(mode, size))
            assertEquals(1000 + mode.ordinal * 100 + size, record.ticks)
            assertEquals((1..size).map { "Member $it" }, record.names)
        }
    }

    @Test fun `slower and tied times do not replace the first holders or rewrite the file`() {
        val file = tempFile()
        val store = ToaRecordStore(file)
        store.submit(ToaMode.NORMAL, 100, listOf("First"))
        val before = file.readBytes()
        assertFalse(store.submit(ToaMode.NORMAL, 101, listOf("Slow")))
        assertFalse(store.submit(ToaMode.NORMAL, 100, listOf("Tie")))
        assertContentEquals(before, file.readBytes())
        assertEquals(listOf("First"), store.get(ToaMode.NORMAL, 1)?.names)
        assertTrue(store.submit(ToaMode.NORMAL, 99, listOf("Fast")))
        assertEquals(listOf("Fast"), ToaRecordStore(file).get(ToaMode.NORMAL, 1)?.names)
    }

    @Test fun `rosters and returned values cannot mutate stored record names`() {
        val store = ToaRecordStore(tempFile())
        val names = mutableListOf("Alpha", "Bravo")
        store.submit(ToaMode.ENTRY, 300, names)
        names[0] = "Changed"
        val returned = assertNotNull(store.get(ToaMode.ENTRY, 2))
        (returned.names as MutableList)[0] = "Also changed"
        assertEquals(listOf("Alpha", "Bravo"), store.get(ToaMode.ENTRY, 2)?.names)
    }

    @Test fun `zero time invalid party duplicate name and markup cannot enter the registry`() {
        val file = tempFile()
        val store = ToaRecordStore(file)
        for (names in listOf(emptyList(), (1..9).map { "P$it" }, listOf("Same", "sAME"), listOf(" "), listOf("<br>"), listOf("A\nB"))) {
            assertFailsWith<IllegalArgumentException> { store.submit(ToaMode.ENTRY, 1, names) }
        }
        for (ticks in listOf(0, -1, Int.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException> { store.submit(ToaMode.EXPERT, ticks, listOf("Valid")) }
        }
        assertFalse(file.exists())
    }

    @Test fun `unreadable or duplicate registry fails closed preserving the original file`() {
        val file = tempFile()
        for (json in listOf("{bad", "{\"version\":2,\"records\":[]}",
            "{\"version\":1,\"records\":[{\"mode\":\"NORMAL\",\"ticks\":1,\"names\":[\"A\"]},{\"mode\":\"NORMAL\",\"ticks\":2,\"names\":[\"B\"]}]}")) {
            file.writeText(json)
            assertFailsWith<IllegalStateException> { ToaRecordStore(file) }
            assertEquals(json, file.readText())
        }
    }

    @Test fun `failed disk replacement keeps the previous memory and disk record`() {
        val file = tempFile()
        val store = ToaRecordStore(file)
        store.submit(ToaMode.EXPERT, 100, listOf("Original"))
        val before = file.readText()
        // AtomicFiles uses a sibling .tmp. A directory at that path makes its write fail on every platform.
        val obstruction = file.resolveSibling(file.name + ".tmp")
        obstruction.mkdir()
        try {
            assertFails { store.submit(ToaMode.EXPERT, 90, listOf("Unsaved")) }
            assertEquals(before, file.readText())
            assertEquals(listOf("Original"), store.get(ToaMode.EXPERT, 1)?.names)
        } finally { obstruction.delete() }
    }

    @Test fun `scoreboard uses native overall cells and preserves tick precision without overflow`() {
        assertEquals(listOf(55, 59, 63, 67, 71, 75, 79, 83), (1..8).map(ToaScoreboardUi::overallComponent))
        assertEquals("1:00.00", ToaScoreboardUi.time(100))
        assertEquals("0:00.60", ToaScoreboardUi.time(1))
        assertEquals("21474836:28.20", ToaScoreboardUi.time(Int.MAX_VALUE))
        assertEquals("-", ToaScoreboardUi.time(null))
        assertEquals(mapOf(85 to ToaMode.ENTRY, 86 to ToaMode.NORMAL, 87 to ToaMode.EXPERT), ToaScoreboardUi.TABS)
        assertFailsWith<IllegalArgumentException> { ToaScoreboardUi.overallComponent(9) }
    }

    @Test fun `completion credits the original eight names including disconnected and departed members once`() {
        val store = ToaRecordStore(tempFile())
        val raid = mockk<ToaRaid>()
        val roster = (1..8).map { PlayerUID(it) }
        every { raid.memberNames } returns HashMap(roster.associateWith { "Member ${it.value}" })
        every { raid.stats.roomCycles } returns LinkedHashMap((ToaPath.values().flatMap { listOf(ToaRoomKey(it, false), ToaRoomKey(it, true)) } + ToaRoomKey(null, true))
            .associateWith { 10 })
        val completion = ToaCompletion("one-raid", roster, ToaRaidSettings(raidLevel = 300), 900, 1)
        ToaRecordStore.completed(raid, completion, store)
        ToaRecordStore.completed(raid, completion, store)
        assertEquals(roster.map { "Member ${it.value}" }, store.get(ToaMode.EXPERT, 8)?.names)
        assertNull(store.get(ToaMode.EXPERT, 1))
    }
}
