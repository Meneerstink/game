package gg.rsmod.plugins.content.npcs.definitions.barrows

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.tools.importer.ObjectPlacementProbeTool
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.setVarc
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-10-03 20:4x-20:5x Barrows reports (bugs/bug-20261003-20*.txt): door noclip without a door swing, puzzle buttons doing
 * nothing, hidden brother blocking the other sarcophagi, crypt monsters not aggressive, Ahrim only casting curses, no chest count /
 * collection log. Guards the run rules on the real 667 map where they depend on it.
 */
class BarrowsRunTests {
    private data class Placement(val id: Int, val tile: Tile, val type: Int, val rot: Int)

    private val cache = Paths.get("..", "..", "data", "cache").toFile().toString()
    private val xtea = File("../../data").walkTopDown().maxDepth(3).first { it.isFile && it.name.contains("xtea", ignoreCase = true) }.path

    private fun placements(plane: Int): List<Placement> {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        try {
            ObjectPlacementProbeTool.main(arrayOf(cache, xtea, "tile", "3551", "9695", plane.toString(), "32"))
        } finally {
            System.setOut(original)
        }
        val line = Regex("""PLACEMENT id=(\d+) name='[^']*' options=.* tile=(\d+),(\d+),(\d+) type=(\d+) orientation=(\d+)""")
        return buffer.toString().lines().mapNotNull { line.find(it) }.map {
            val g = it.groupValues
            Placement(g[1].toInt(), Tile(g[2].toInt(), g[3].toInt(), g[4].toInt()), g[5].toInt(), g[6].toInt())
        }
    }

    private fun player(): Player {
        val attrs = AttributeMap()
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns attrs
        return player
    }

    @Test
    fun `every placed tunnel door swings open as a double door onto its outside tiles and stays a bounded crossing`() {
        val defs = DefinitionSet().also { it.loadAll(CacheLibrary(cache)) }
        val all = placements(0)
        val objects = all.map { DynamicObject(it.id, it.type, it.rot, it.tile) as GameObject }
        val at = objects.groupBy { Triple(it.tile.x, it.tile.z, it.type) }
        val doors = objects.filter { Barrows.doorLetter(it.id) != null }
        assertEquals(64, doors.size, "32 door multilocs x 2 placements")
        val problems = ArrayList<String>()
        doors.forEach { door ->
            val def = defs.getNullable(ObjectDef::class.java, door.id)!!
            val letter = Barrows.doorLetter(door.id)!!
            if (def.varbit != Barrows.doorVarbit(letter)) problems += "${door.id} varbit ${def.varbit} != letter $letter"
            if (def.transforms?.get(0) != Barrows.closedLeaf(door.id)) problems += "${door.id} child 0 ${def.transforms?.get(0)}"
            val plan =
                Barrows.doorSwing(door, objectAt = { tile, type -> at[Triple(tile.x, tile.z, type)]?.firstOrNull() }, defOf = { defs.getNullable(ObjectDef::class.java, it) })
            if (plan == null || plan.size != 2) {
                problems += "${door.id}@${door.tile} plan=${plan?.size}"
                return@forEach
            }
            plan.forEach { r ->
                val leaf = r.original
                if (Barrows.doorLetter(leaf.id) != letter) problems += "${door.id}@${door.tile} paired with ${leaf.id}"
                if (r.id != Barrows.openedLeaf(leaf.id)) problems += "${leaf.id} opens to ${r.id}"
                val outside = leaf.tile.step(DoorCrossing.faceDirection(leaf.rot))
                if (!r.tile.sameAs(outside)) problems += "${leaf.id}@${leaf.tile} rot ${leaf.rot} swings to ${r.tile}, outside $outside"
                // The crossing never leaves the two tiles of the door: outside <-> door tile (E17, no collision-free walk elsewhere).
                if (DoorCrossing.destination(outside, leaf)?.sameAs(leaf.tile) != true) problems += "${leaf.id} no crossing from $outside"
                if (DoorCrossing.destination(leaf.tile.transform(5, 5), leaf) != null) problems += "${leaf.id} crosses from afar"
            }
        }
        // Opened leaves exist in the cache (Void barrows_door_left_opened / right_opened).
        assertTrue(defs.getNullable(ObjectDef::class.java, Barrows.LEFT_DOOR_OPENED)?.name == "Door")
        assertTrue(defs.getNullable(ObjectDef::class.java, Barrows.RIGHT_DOOR_OPENED)?.name == "Door")
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `the near side of the owner's F2 double door is reached from the tile beside it`() {
        // Live log 2026-10-03 20:22: double door 6728/6747 at 3568-3569,9684 (south face) clicked from 3568,9685 -> "The door is blocked."
        val door = DynamicObject(6728, 0, 3, Tile(3568, 9684))
        val near = DoorCrossing.nearSide(Tile(3568, 9685), door)
        assertEquals(Tile(3568, 9684), near)
        assertEquals(Tile(3568, 9683), DoorCrossing.destination(near!!, door))
    }

    @Test
    fun `the locked door set is read from the run's letters`() {
        val player = player()
        player.attr[Barrows.LOCKED_DOORS] = "abc"
        assertTrue(Barrows.isLockedDoor(player, 6716)) // left a
        assertTrue(Barrows.isLockedDoor(player, 6736)) // right b
        assertFalse(Barrows.isLockedDoor(player, 6719)) // left d
        assertFalse(Barrows.isLockedDoor(player, 6714)) // a child id is no placed door
    }

    @Test
    fun `puzzle answer buttons are Void's choice components and a pick is judged against the shuffled answer`() {
        assertTrue(Barrows.PUZZLE_CHOICE_COMPONENTS.contentEquals(intArrayOf(2, 3, 5)))
        assertTrue(Barrows.PUZZLE_OPTION_COMPONENTS.contentEquals(intArrayOf(6, 7, 8)))
        Barrows.PUZZLES.forEach { assertTrue(it.answer in it.choices, "answer model ${it.answer} offered") }
        assertTrue(Barrows.puzzleAnswered(1, 1))
        assertFalse(Barrows.puzzleAnswered(0, 1))
        assertFalse(Barrows.puzzleAnswered(-1, -1))
    }

    @Test
    fun `the 667 puzzle interface has the sequence models and three clickable answer buttons`() {
        // Owner 2026-10-03 20:49 "when u try to select one puzzle nothing happens": a button without a cache op sends no click.
        val library = CacheLibrary(cache)
        try {
            val problems = ArrayList<String>()
            Barrows.PUZZLE_OPTION_COMPONENTS.forEach { if (library.data(3, Barrows.PUZZLE_INTERFACE, it) == null) problems += "25:$it missing" }
            Barrows.PUZZLE_CHOICE_COMPONENTS.forEach {
                val data = library.data(3, Barrows.PUZZLE_INTERFACE, it)
                val op = data?.let { d -> gg.rsmod.game.tools.importer.InterfaceHookProbeTool.componentOps(d).firstOrNull() }
                if (op.isNullOrBlank()) problems += "25:$it has no click op (data=${data != null})"
            }
            assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        } finally {
            library.close()
        }
    }

    @Test
    fun `every prayer drain sends one face, the brothers in turn (Darkan getAndIncreaseHeadIndex)`() {
        io.mockk.mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            val player = player()
            val sent = ArrayList<Pair<Int, Int>>()
            every { player.tile } returns Tile(3551, 9690, 0)
            every { player.setVarc(any(), any()) } answers { sent += secondArg<Int>() to thirdArg<Int>(); Unit }
            repeat(12) { Barrows.drainPrayer(player) }
            // One write per face (the old clear + face in one tick made the overlay play twice, owner 154545 "4 at once"); the timer
            // clears it after Barrows.HEAD_SHOW_TICKS. No brother killed yet: every brother in turn, so consecutive faces differ.
            assertEquals(12, sent.size)
            assertTrue(sent.all { it.first == Barrows.BROTHER_HEAD_VARC })
            assertTrue(sent.all { (_, face) -> Barrows.Brother.values().any { it.tunnelHeadValue == face } })
            assertTrue(sent.zipWithNext().none { (a, b) -> a.second == b.second }, "$sent")
            assertEquals(4761, Barrows.headValue(Barrows.Brother.AHRIM, Tile(3557, 9703, 3)), "crypt face (Void ahrims)")
            assertEquals(4772, Barrows.headValue(Barrows.Brother.VERAC, Tile(3551, 9690, 0)), "tunnel face (Void verac_tunnels)")
        } finally {
            io.mockk.unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
    }

    @Test
    fun `the hidden brother never blocks the other five sarcophagi`() {
        Barrows.Brother.values().forEach { hidden ->
            val player = player()
            player.attr[Barrows.SELECTED_BROTHER] = hidden.key
            Barrows.Brother.values().forEach { brother ->
                val expected = if (brother == hidden) Barrows.SearchResult.HIDDEN_TUNNEL else Barrows.SearchResult.WAKE_BROTHER
                assertEquals(expected, Barrows.searchResult(player, brother), "hidden ${hidden.key}, search ${brother.key}")
            }
            // A brother killed in this run stays down until the chest is looted.
            val killed = Barrows.Brother.values().first { it != hidden }
            player.attr[killed.killedAttr] = true
            assertEquals(Barrows.SearchResult.NOTHING, Barrows.searchResult(player, killed))
        }
    }

    @Test
    fun `chest count and collection log messages`() {
        assertEquals("Your Barrows chest count is: <col=ff0000>7</col>.", Barrows.chestCountMessage(7))
        assertEquals(25, Barrows.COLLECTION_LOG_ITEMS.size, "24 equipment pieces + bolt rack (OSRS collection log)")
        val player = player()
        val first = Barrows.newCollectionLogItems(player, listOf(Item(Items.DHAROKS_HELM), Item(Items.COINS_995, 50), Item(Items.BOLT_RACK, 35)))
        assertEquals(listOf(Items.DHAROKS_HELM, Items.BOLT_RACK), first)
        assertEquals(emptyList(), Barrows.newCollectionLogItems(player, listOf(Item(Items.BOLT_RACK, 40))))
    }

    @Test
    fun `crypt monsters are aggressive regardless of level only inside the crypts`() {
        fun npc(id: Int, tile: Tile): Npc = mockk<Npc>(relaxed = true).also {
            every { it.id } returns id
            every { it.tile } returns tile
        }
        Barrows.CRYPT_MONSTERS.forEach { assertTrue(Barrows.aggressiveRegardlessOfLevel(npc(it, Tile(3540, 9690, 0))), "$it") }
        assertFalse(Barrows.aggressiveRegardlessOfLevel(npc(2031, Tile(3540, 3290, 0))), "outside the crypts")
        assertFalse(Barrows.aggressiveRegardlessOfLevel(npc(2030, Tile(3540, 9690, 0))), "brothers keep their own rule")
    }

    @Test
    fun `an npc curse is not recast while the stat it lowered is still lowered`() {
        val player = player()
        var attack = 116
        every { player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.ATTACK) } answers { attack }
        assertFalse(NpcAttacks.stillLowered(player, gg.rsmod.plugins.api.Skills.ATTACK))
        NpcAttacks.noteLowered(player, gg.rsmod.plugins.api.Skills.ATTACK, 116)
        attack = 111
        assertTrue(NpcAttacks.stillLowered(player, gg.rsmod.plugins.api.Skills.ATTACK), "boosted 116 -> 111 is still lowered")
        attack = 118
        assertFalse(NpcAttacks.stillLowered(player, gg.rsmod.plugins.api.Skills.ATTACK), "re-boosted")
    }

    @Test
    fun `Ahrim's Fire Wave flies at melee distance and his curses drain on accuracy at 0 damage (owner 154222)`() {
        NpcAttacks.load(File("../../data/cfg/npcs/npc-attacks.json"))
        val ahrim = NpcAttacks.rowFor(2025)!!
        val fireWave = ahrim.attacks.first { it.id == "magic" }
        for (attack in ahrim.attacks) {
            for (p in attack.projectiles) {
                for (distance in 0..9) assertTrue(NpcAttacks.flightTime(p, distance) > 0, "${attack.id} projectile ${p.id} at distance $distance")
            }
        }
        val weaken = ahrim.attacks.first { it.id == "weaken" }
        assertTrue(NpcAttacks.drainSucceeds(weaken, landed = false, accurate = true), "accurate 0-damage Weaken drains")
        assertFalse(NpcAttacks.drainSucceeds(weaken, landed = false, accurate = false), "a splash does not drain")
        assertFalse(NpcAttacks.drainSucceeds(fireWave, landed = false, accurate = true), "a 0 Fire Wave is still a miss")
    }
}
