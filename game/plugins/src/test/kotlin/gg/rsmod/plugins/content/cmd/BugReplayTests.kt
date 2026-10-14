package gg.rsmod.plugins.content.cmd

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.TEAM_TEST_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.content.mechanics.moderation.TeamAccountPresets
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T1 (owner 2026-10-13: "bug-melding automatisch naspelen"): a bugs\bug-*.txt report is replayed onto a team test account on
 * the test server - position, every inventory and equipment slot exactly (Darkan Debug.java "copy": slots are SET, never
 * equipped through the equip action), skills, prayer book, spellbook, run, special energy, attack style, auto-retaliate.
 * The class: every field the report writes must come back identical, for EVERY report the owner has written, and the replay
 * must be impossible on the live server and for any account that is not a team test account.
 */
class BugReplayTests {
    /** The owner's report "goblin emotes lukken niet" (bugs\bug-20261013-165634-anudd.txt) - his OSRS gear and 168m coins. */
    private val ownerReport =
        """
        BUG REPORT 2026-10-13T16:56:34.123  by anudd
        What went wrong: goblin emotes lukken niet

        Position: 3173,3491,0  region=12598  chunk=ChunkCoords{x=390, z=430}
        Traces: input=on msg=on interface=on interact=on item=on combat=on npcpath=on var=off plugin=on tick=on summon=on sweep=on av=on breach=on
        Combat level=138  hp=99/99  lock=NONE
        Interfaces open: gg.rsmod.game.model.interf.InterfaceSet@129552b7

        Inventory: 0:22763x1 1:22933x1 2:22490x301 3:23002x1 4:2347x1 5:995x168796273 6:1713x1
        Equipment: 0:23895x1 1:22386x1 2:6585x1 3:11235x1 4:4940x1 7:4946x1 9:7462x1 10:22911x1 12:22407x1 13:11212x98
        """.trimIndent().lines()

    @Test
    fun `the owner's report replays slot-exact onto a team account that wore another set`() {
        val report = BugReplay.parse(ownerReport)
        assertEquals(Tile(3173, 3491, 0), report.position)
        val target = player("teamtest1")
        target.inventory[0] = Item(385, 10) // the preset's sharks
        target.inventory[27] = Item(995, 5)
        target.equipment[5] = Item(19867) // a shield the report does not have: must be gone afterwards
        target.equipment[3] = Item(22761)

        val result = BugReplay.apply(target, report)

        verify { target.moveTo(Tile(3173, 3491, 0)) }
        assertEquals(slots(ownerReport, "Inventory:"), BugReplay.containerLine(target.inventory))
        assertEquals(slots(ownerReport, "Equipment:"), BugReplay.containerLine(target.equipment))
        assertNull(target.equipment[5])
        assertNull(target.inventory[27])
        assertEquals(168796273, target.inventory[5]!!.amount)
        assertTrue(BugReplay.diff(target, report).isEmpty(), BugReplay.diff(target, report).joinToString())
        // Old reports carry no skills: the replay says so instead of claiming a full copy.
        assertTrue(result.any { it.contains("skills: not in report") }, result.joinToString(" | "))
    }

    @Test
    fun `every field of a new report comes back identical (round trip)`() {
        val source = player("anudd")
        source.tile = Tile(3093, 3493, 0)
        source.inventory[0] = Item(995, 168796273)
        source.inventory[13] = Item(22933)
        source.inventory[27] = Item(11212, 98)
        source.equipment[0] = Item(23895)
        source.equipment[3] = Item(11235)
        source.equipment[10] = Item(22911)
        source.equipment[13] = Item(11212, 98)
        for (i in 0 until source.skills.maxSkills) source.skills.setXp(i, 1000.0 * (i + 1))
        source.skills.setCurrentLevel(2, 118) // a boosted strength level
        source.runEnergy = 73.0
        source.varps.setState(173, 1)
        source.varps.setState(300, 550)
        source.varps.setState(43, 2)
        source.varps.setState(172, 1)
        // A curses player on the Ancient spellbook (teamtest1 starts on normal prayers and the standard book).
        source.attr[AncientCurses.UNLOCKED_ATTR] = true
        AncientCurses.switchBook(source, AncientCurses.PrayerBook.ANCIENT)
        source.setSpellbook(Spellbook.ANCIENT)
        val lines = listOf(BugReplay.positionLine(source)) + BugReplay.stateLines(source)
        assertTrue(lines.any { it.startsWith("Replay: ") }, lines.joinToString("\n"))

        val target = player("teamtest1")
        target.equipment[4] = Item(4736)
        val report = BugReplay.parse(lines)
        BugReplay.apply(target, report)

        assertEquals(BugReplay.stateLines(source).filterNot { it.startsWith("Position:") }, BugReplay.stateLines(target).filterNot { it.startsWith("Position:") })
        verify { target.moveTo(Tile(3093, 3493, 0)) }
        assertEquals(118, target.skills.getCurrentLevel(2))
        assertEquals(73.0, target.runEnergy)
        assertEquals(AncientCurses.PrayerBook.ANCIENT, AncientCurses.getBook(target))
        assertEquals(Spellbook.ANCIENT, target.getSpellbook())
        assertTrue(lines.last().contains("prayerbook=ANCIENT") && lines.last().contains("spellbook=ANCIENT"), lines.last())
        assertTrue(BugReplay.diff(target, report).isEmpty(), BugReplay.diff(target, report).joinToString())
    }

    @Test
    fun `every report the owner wrote parses completely and replays without a silent skip`() {
        val dir = Paths.get("..", "..", "..", "..", "bugs").toFile()
        val files = dir.listFiles { f -> f.name.startsWith("bug-") && f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        assertTrue(files.size >= 80, "expected the owner's bug reports in ${dir.absolutePath}, found ${files.size}")
        val failures = mutableListOf<String>()
        for (file in files) {
            val lines = file.readLines()
            val report = BugReplay.parse(lines)
            if (report.position == null) failures += "${file.name}: no position"
            if (report.problems.isNotEmpty()) failures += "${file.name}: ${report.problems}"
            val target = player("teamtest1")
            val result = BugReplay.apply(target, report)
            // Every slot in the report is either restored or named as a PROBLEM - never dropped silently.
            val expectInv = slots(lines, "Inventory:")
            val expectEq = slots(lines, "Equipment:")
            val problems = result.filter { it.startsWith("PROBLEM") }
            if (problems.isEmpty() && (BugReplay.containerLine(target.inventory) != expectInv || BugReplay.containerLine(target.equipment) != expectEq)) {
                failures += "${file.name}: containers differ without a PROBLEM line"
            }
            val instance = report.position != null && report.position!!.x >= 6400
            if (instance && result.none { it.contains("instance") }) failures += "${file.name}: instance position not refused"
            if (!instance && problems.isEmpty() && BugReplay.diff(target, report).isNotEmpty()) failures += "${file.name}: ${BugReplay.diff(target, report)}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `an instance (POH) position is refused with a clear line and never teleported to`() {
        val lines = ownerReport.map { if (it.startsWith("Position:")) "Position: 6480,104,0  region=1  chunk=x" else it }
        val target = player("teamtest1")
        val result = BugReplay.apply(target, BugReplay.parse(lines))
        verify(exactly = 0) { target.moveTo(any<Tile>()) }
        assertTrue(result.any { it.contains("instance") && it.contains("not restored") }, result.joinToString(" | "))
        assertEquals(slots(lines, "Equipment:"), BugReplay.containerLine(target.equipment))
    }

    @Test
    fun `the replay is refused on the live server, for other accounts and before the login preset`() {
        val team = player("teamtest1")
        assertNull(BugReplay.refusal("teamtest1", team, testServer = true))
        assertNotNull(BugReplay.refusal("teamtest1", team, testServer = false), "live server")
        val owner = player("anudd")
        assertNotNull(BugReplay.refusal("anudd", owner, testServer = true), "owner account")
        assertNotNull(BugReplay.refusal("sumtest", player("sumtest"), testServer = true), "saved account")
        assertNotNull(BugReplay.refusal("teamtest3", null, testServer = true), "not logged in")
        val early = player("teamtest2")
        early.attr.remove(TeamAccountPresets.PRESET_APPLIED_ATTR)
        assertTrue(BugReplay.refusal("teamtest2", early, testServer = true)!!.contains("preset"), "order trap: the preset would overwrite the replay")
        // Only bug report file names in bugs\ - no paths.
        assertNotNull(BugReplay.reportFile("bug-20261013-165634-anudd.txt", File("x")))
        assertNull(BugReplay.reportFile("..\\game.yml", File("x")))
        assertNull(BugReplay.reportFile("C:/RSPS/game/game/game.yml", File("x")))
    }

    @Test
    fun `the report's client log is the reporter's own, never an agent log on a live report`() {
        val dir = kotlin.io.path.createTempDirectory("bugreplay").toFile()
        try {
            val owner = File(dir, "client-20261014-100000.out.log").apply { writeText("[78] client runs from x\n"); setLastModified(1_000_000L) }
            val agent = File(dir, "client-20261014-110000.out.log").apply { writeText("[78] client runs from x\n[testhook] listening on 127.0.0.1:50290\n"); setLastModified(2_000_000L) }
            val err = File(dir, "client-20261014-120000.err.log").apply { writeText("x"); setLastModified(3_000_000L) }
            val logs = listOf(owner, agent, err)
            assertEquals(owner, BugReplay.pickClientLog(logs, testServer = false, reporterIsLocal = true), "live report: the newest NON-agent log")
            assertEquals(agent, BugReplay.pickClientLog(logs, testServer = true, reporterIsLocal = true), "test server: the agent's hook client")
            assertNull(BugReplay.pickClientLog(logs, testServer = false, reporterIsLocal = false), "a friend's client is not on this PC")
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun slots(
        lines: List<String>,
        prefix: String,
    ): String = lines.first { it.startsWith(prefix) }.removePrefix(prefix).trim()

    private fun player(name: String): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns world
        every { p.username } returns name
        val varps = VarpSet((0 until 8000).toSet())
        every { p.varps } returns varps
        every { p.varcs } returns MutableList(2000) { 0 }
        val skills = SkillSet(25)
        every { p.skills } returns skills
        val attr = AttributeMap()
        every { p.attr } returns attr
        if (name.startsWith("teamtest")) {
            attr[TEAM_TEST_ACCOUNT_ATTR] = true
            attr[TeamAccountPresets.PRESET_APPLIED_ATTR] = true
        }
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { p.inventory } returns inventory
        every { p.equipment } returns equipment
        var tile = Tile(3222, 3218, 0)
        every { p.tile } answers { tile }
        every { p.tile = any() } answers { tile = firstArg() }
        every { p.moveTo(any<Tile>()) } answers { tile = firstArg() }
        var run = 100.0
        every { p.runEnergy } answers { run }
        every { p.runEnergy = any() } answers { run = firstArg() }
        var hp = 990
        every { p.getCurrentLifepoints() } answers { hp }
        every { p.setCurrentLifepoints(any()) } answers { hp = firstArg() }
        var prayer = 990
        every { p.getCurrentPrayerPoints() } answers { prayer }
        every { p.setCurrentPrayerPoints(any()) } answers { prayer = firstArg() }
        return p
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun closeCache() {
            LIBRARY.close()
        }
    }
}
