package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Tile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** CoX generator (dossier COX.txt fase 2/3; RuneLite LayoutSolver codes + tsps Layout.ChambersOfXeric.js). */
class CoxLayoutTests {
    @Test
    fun `all 27 OSRS layout codes parse into two floors from start to end`() {
        assertEquals(27, CoxLayouts.PATTERNS.size)
        assertEquals(3, CoxLayouts.LARGE_PATTERNS.size) // tsps: 'Large' = 8-room floors (5 combat + 3 puzzles)
        for (p in CoxLayouts.PATTERNS) {
            val (kinds, dirs) = p.code.split(" - ")
            assertEquals(kinds.length, dirs.length, p.code)
            val half = p.rooms.size / 2
            for (floor in listOf(p.rooms.subList(0, half), p.rooms.subList(half, p.rooms.size))) {
                assertEquals(CoxRoomKind.START, floor.first().kind, p.code)
                assertEquals(CoxRoomKind.END, floor.last().kind, p.code)
                assertTrue(floor.drop(1).all { it.direction != null }, p.code)
            }
        }
    }

    @Test
    fun `every pattern lays out as a connected path inside the 16x8-chunk floor block`() {
        for (p in CoxLayouts.PATTERNS) for (seed in 0 until 40) {
            val layout = CoxLayouts.normal(Random(seed), listOf(p))
            assertEquals(p.code, layout.pattern)
            for (floor in layout.floors) {
                assertEquals(floor.size, floor.map { it.gridX to it.gridZ }.toSet().size, "overlap ${p.code}")
                floor.zipWithNext().forEach { (a, b) ->
                    assertEquals(1, Math.abs(a.gridX - b.gridX) + Math.abs(a.gridZ - b.gridZ), "not adjacent ${p.code}")
                }
                assertTrue(floor.all { it.variant in 0..2 && it.rotation in 0..3 })
            }
            assertTrue(layout.widthChunks <= CoxMap.INSTANCE_CHUNKS, "too wide ${p.code}: ${layout.widthChunks}")
            assertTrue(layout.heightChunks <= CoxMap.OLM_AT_CHUNK_Z, "too high ${p.code}: ${layout.heightChunks}")
        }
    }

    @Test
    fun `a small raid has two different puzzles and its combat rooms run along one Wiki rotation`() {
        repeat(500) { seed ->
            val layout = CoxLayouts.normal(Random(seed))
            val rooms = layout.floors.flatten().map { it.room }
            val puzzles = rooms.filter { it.kind == CoxRoomKind.PUZZLE }
            assertTrue(puzzles.toSet().size <= 2, "seed $seed puzzles $puzzles")
            val combat = rooms.filter { it.kind == CoxRoomKind.COMBAT }
            assertEquals(combat.size, combat.toSet().size, "seed $seed combat repeated $combat")
            val fits = CoxLayouts.BOSS_ROTATIONS.flatMap { listOf(it, it.reversed()) }.any { rot ->
                val start = rot.indexOf(combat.first())
                combat.indices.all { rot[(start + it) % rot.size] == combat[it] }
            }
            assertTrue(fits, "seed $seed combat $combat")
            assertEquals(CoxRoom.RAID_START, layout.floors[0].first().room)
            assertEquals(CoxRoom.FLOOR_START_UPSTAIRS, layout.floors[1].first().room)
            assertEquals(0, layout.floors[0].last().variant) // hole 29734 above
            assertEquals(1, layout.floors[1].last().variant) // hole 29735 on the last floor
        }
    }

    @Test
    fun `room rotation turns chunks and tiles consistently`() {
        for (rot in 0..3) {
            val chunks = (0 until 4).flatMap { x -> (0 until 4).map { z -> CoxMap.rotateChunk(x, z, rot) } }
            assertEquals(16, chunks.toSet().size)
            // A tile in source chunk (cx,cz) must land in the chunk rotateChunk gives.
            for (cx in 0 until 4) for (cz in 0 until 4) {
                val (tx, tz) = CoxMap.rotateRoomTile(cx * 8 + 3, cz * 8 + 5, rot)
                assertEquals(CoxMap.rotateChunk(cx, cz, rot), (tx / 8) to (tz / 8), "rot $rot chunk $cx,$cz")
            }
        }
        val room = CoxLaidRoom(CoxRoom.TEKTON, 1, 0, CoxDirection.EAST, 1)
        // EAST = rotation 1: source south-west corner (0,0) -> (0,31) of the room.
        assertEquals(Tile(1000 + 32, 2000 + 31, 3), CoxMap.roomTile(1000, 2000, 3, room, Tile(412 * 8, 660 * 8)))
    }

    @Test
    fun `cache regions are the cox-map batch squares moved +24`() {
        assertEquals(15, CoxMap.SOURCE_REGIONS.size)
        assertTrue(CoxMap.CACHE_REGIONS.all { (it and 255) in 104..113 && (it shr 8) in 50..52 })
        assertEquals((50 shl 8) or 113, CoxMap.CACHE_REGIONS.last())
    }
}
