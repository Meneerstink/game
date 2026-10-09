package gg.rsmod.plugins.content.mechanics.worldedit

import com.google.gson.GsonBuilder
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.MessageStructureSet
import gg.rsmod.game.message.encoder.LocCustomiseEncoder
import gg.rsmod.game.message.impl.LocCustomiseMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.LocFit
import gg.rsmod.game.model.entity.Player
import gg.rsmod.net.packet.DataType
import gg.rsmod.net.packet.PacketType
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Place mode (owner 2026-10-06): the per-placement fit survives the world-edit file and the packet, old edits still
 * load, and the keys move a selection the way the overlay says - along the wall across tiles, to the other side of
 * the wall, within the client's ranges. The visual result is proven in the real client (handoff), not here.
 */
class DecoPlacerTests {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private data class EditFile(val edits: List<WorldEdits.Edit> = emptyList())

    @Test
    fun `an old edit without a fit loads as a plain placement and is written back without one`() {
        val json = """{"edits":[{"op":"spawn","id":62751,"x":3140,"z":3499,"level":0,"type":4,"rot":0,"group":1}]}"""
        val edit = gson.fromJson(json, EditFile::class.java).edits.single()
        assertNull(edit.fit)
        assertNull(edit.effectiveFit)
        assertFalse(gson.toJson(EditFile(listOf(edit))).contains("fit"))
    }

    @Test
    fun `a fit round-trips through the edit file and a scale of 0 reads as 100 percent`() {
        val edit = WorldEdits.Edit("spawn", 62754, 3098, 3489, 0, 4, 1, fit = LocFit(dx = 36, dy = -64, dz = 8, scale = 160, yaw = 230))
        val back = gson.fromJson(gson.toJson(EditFile(listOf(edit))), EditFile::class.java).edits.single()
        assertEquals(edit.fit, back.fit)
        // A hand-edited file that leaves the scale out (gson reads 0) still means an unscaled loc.
        val partial = gson.fromJson("""{"edits":[{"op":"spawn","id":1,"x":1,"z":1,"fit":{"dx":4}}]}""", EditFile::class.java).edits.single()
        assertEquals(LocFit(dx = 4), partial.effectiveFit)
        assertNull(WorldEdits.Edit("spawn", 1, 1, 1, fit = LocFit()).effectiveFit, "the identity fit is a plain placement")
    }

    @Test
    fun `the customise payload is the five fit values as big-endian shorts within range`() {
        val fit = LocFit(dx = -4, dy = 300, dz = 0, scale = 5000, yaw = -46)
        val bytes = LocCustomiseEncoder().extractBytes(LocCustomiseMessage(62754, 4 shl 2, 0x12, fit), "payload")
        assertEquals(10, bytes.size)
        fun short(i: Int) = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
        assertEquals(-4, short(0).toShort().toInt())
        assertEquals(300, short(2))
        assertEquals(0, short(4))
        assertEquals(LocFit.MAX_SCALE, short(6), "scale is clamped to the client range")
        assertEquals(LocFit.YAW_TURN - 46, short(8), "a negative yaw is sent as its positive turn")
        assertEquals(0, LocCustomiseEncoder().extractBytes(LocCustomiseMessage(1, 0, 0, null), "payload").size)
        assertEquals(LocCustomiseMessage.FLAG_RESET, LocCustomiseMessage(1, 0, 0, null).flags)
        assertEquals(LocCustomiseMessage.FLAG_FIT, LocCustomiseMessage(1, 0, 0, fit).flags)
    }

    @Test
    fun `packets yml carries LOC_CUSTOMISE as variable-byte opcode 99 in the client's field order`() {
        val structures = MessageStructureSet().load(File("../../data/packets.yml"))
        val structure = assertNotNull(structures.get(LocCustomiseMessage::class.java))
        assertEquals(PacketType.VARIABLE_BYTE, structure.type)
        assertEquals(99, structure.opcodes.single())
        assertEquals(listOf("settings", "flags", "tile", "id", "payload"), structure.values.keys.toList())
        assertEquals(DataType.BYTES, structure.values["payload"]!!.type)
    }

    @Test
    fun `a wall decoration's edge follows its rotation and back`() {
        for (rot in 0..3) assertEquals(rot, DecoPlacer.rotFor(DecoPlacer.edgeOf(rot)))
        assertEquals(1, DecoPlacer.edgeOf(0), "rotation 0 hangs on the north edge")
    }

    private fun player(at: Tile, customisable: Boolean = true): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns at
        every { player.lastKnownRegionBase } returns null
        val world = mockk<World>(relaxed = true)
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { world.definitions } returns definitions
        every { definitions.get(ObjectDef::class.java, any()) } returns ObjectDef(62754).apply { name = "78 logo"; this.customisable = customisable }
        every { player.world } returns world
        return player
    }

    @Test
    fun `a loc without opcode 177 is refused, it could not show a fit`() {
        val player = player(Tile(3200, 3200, 0), customisable = false)
        assertFalse(DecoPlacer.startNew(player, 62751, Tile(3200, 3201, 0), wall = null))
        assertNull(DecoPlacer.session(player))
    }

    @Test
    fun `more than half a tile along the wall becomes the next tile, for every rotation`() {
        for (rot in 0..3) {
            val player = player(Tile(3200, 3200, 0))
            assertTrue(DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null))
            val s = DecoPlacer.session(player)!!
            s.rot = rot
            s.type = 4
            repeat(9) { DecoPlacer.key(player, "along", +1) } // 9 x 4 = 36 units: well within the tile
            assertEquals(36, s.fit.dx)
            assertEquals(Tile(3200, 3201, 0), s.tile)
            s.stepLevel = 1
            repeat(8) { DecoPlacer.key(player, "along", +1) } // + 8 x 32 = 292 > 256: one tile further along
            assertEquals(292 - 512, s.fit.dx)
            assertEquals(Tile(3200 + DecoPlacer.ALONG_DX[rot], 3201 + DecoPlacer.ALONG_DZ[rot], 0), s.tile, "rotation $rot")
            DecoPlacer.end(player, restore = false)
        }
    }

    @Test
    fun `F puts the decoration on the other side of its wall at the same spot along it`() {
        val player = player(Tile(3200, 3200, 0))
        DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null)
        val s = DecoPlacer.session(player)!!
        s.type = 4
        s.rot = 0 // back north: hangs on the north edge of 3200,3201
        DecoPlacer.key(player, "along", +1)
        DecoPlacer.key(player, "out", +1)
        DecoPlacer.key(player, "flip", 0)
        assertEquals(Tile(3200, 3202, 0), s.tile, "across the north edge")
        assertEquals(2, s.rot, "back south")
        assertEquals(-4, s.fit.dx, "mirrored along the wall")
        assertEquals(-4, s.fit.dz, "same distance off the wall")
        DecoPlacer.key(player, "flip", 0)
        assertEquals(Tile(3200, 3201, 0), s.tile)
        assertEquals(0, s.rot)
        assertEquals(4, s.fit.dx)
    }

    @Test
    fun `values stay inside the client ranges and an unknown key changes nothing`() {
        val player = player(Tile(3200, 3200, 0))
        DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null)
        val s = DecoPlacer.session(player)!!
        s.stepLevel = 1
        repeat(200) { DecoPlacer.key(player, "scale", +1) }
        assertEquals(LocFit.MAX_SCALE, s.fit.scale)
        repeat(400) { DecoPlacer.key(player, "scale", -1) }
        assertEquals(LocFit.MIN_SCALE, s.fit.scale)
        repeat(100) { DecoPlacer.key(player, "up", +1) }
        assertEquals(-LocFit.MAX_OFFSET, s.fit.dy)
        repeat(80) { DecoPlacer.key(player, "yaw", -1) }
        assertTrue(s.fit.yaw in 0 until LocFit.YAW_TURN)
        val before = s.fit to s.tile
        assertFalse(DecoPlacer.key(player, "explode", +1))
        assertEquals(before, s.fit to s.tile)
        DecoPlacer.key(player, "cancel", 0)
        assertNull(DecoPlacer.session(player), "cancel ends the session")
        assertFalse(DecoPlacer.key(player, "along", +1), "no selection: nothing happens")
    }

    @Test
    fun `too far away is refused and walking away cancels`() {
        val player = player(Tile(3200, 3200, 0))
        assertFalse(DecoPlacer.startNew(player, 62754, Tile(3250, 3200, 0), wall = null))
        assertNull(DecoPlacer.session(player))
        assertFalse(DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 1), wall = null), "another level")
        assertTrue(DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null))
        every { player.tile } returns Tile(3300, 3200, 0)
        assertFalse(DecoPlacer.key(player, "along", +1))
        assertNull(DecoPlacer.session(player))
    }

    @Test
    fun `along and away follow the decoration's own angle`() {
        val player = player(Tile(3200, 3200, 0))
        DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null)
        val s = DecoPlacer.session(player)!!
        s.type = 4
        s.rot = 0
        s.fit = LocFit(yaw = LocFit.YAW_QUARTER) // a quarter turn: its own "along" is the loc frame's -z
        s.stepLevel = 1
        DecoPlacer.key(player, "along", +1)
        assertEquals(0, s.fit.dx, "no sideways drift")
        assertEquals(-32, s.fit.dz)
        DecoPlacer.key(player, "out", +1)
        assertEquals(-32, s.fit.dx, "away from its own back")
        assertEquals(-32, s.fit.dz)
    }

    @Test
    fun `three step sizes, T cycles them`() {
        val player = player(Tile(3200, 3200, 0))
        DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null)
        val s = DecoPlacer.session(player)!!
        val seen = ArrayList<Int>()
        repeat(4) {
            val before = s.fit.dy
            DecoPlacer.key(player, "up", +1)
            seen += before - s.fit.dy
            DecoPlacer.key(player, "step", 0)
        }
        assertEquals(listOf(4, 32, 128, 4), seen)
    }

    @Test
    fun `a diagonal wall gets the decoration at 45 degrees with its back on the wall line, F turns it on the spot`() {
        for (wallRot in 0..3) {
            val player = player(Tile(3200 + DecoPlacer.DIAGONAL_NX[wallRot] * 2, 3201 + DecoPlacer.DIAGONAL_NZ[wallRot] * 2, 0))
            val wall = gg.rsmod.game.model.entity.DynamicObject(1902, 9, wallRot, Tile(3200, 3201, 0))
            assertTrue(DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall))
            val s = DecoPlacer.session(player)!!
            assertEquals(4, s.type)
            assertEquals(Tile(3200, 3201, 0), s.tile, "on the wall's own tile")
            assertEquals(LocFit.YAW_QUARTER / 2, s.fit.yaw)
            assertEquals(-LocFit.UNITS_PER_TILE / 2, s.fit.dz, "back in the middle of the tile")
            // the back points away from the player's side: rotation r + 45 degrees -> NE, SE, SW, NW
            assertEquals(DecoPlacer.diagonalRot(-DecoPlacer.DIAGONAL_NX[wallRot], -DecoPlacer.DIAGONAL_NZ[wallRot]), s.rot, "wall rot $wallRot")
            val rot = s.rot
            DecoPlacer.key(player, "flip", 0)
            assertEquals(Tile(3200, 3201, 0), s.tile, "F stays on the tile")
            assertEquals((rot + 2) and 3, s.rot)
            DecoPlacer.end(player, restore = false)
        }
    }

    @Test
    fun `scenery such as a facade gets the decoration on its face toward the player`() {
        val player = player(Tile(3200, 3205, 0)) // north of the loc
        val facade = gg.rsmod.game.model.entity.DynamicObject(1902, 10, 0, Tile(3200, 3201, 0))
        assertTrue(DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), facade))
        val s = DecoPlacer.session(player)!!
        assertEquals(4, s.type)
        assertEquals(Tile(3200, 3202, 0), s.tile, "the tile in front of the north face")
        assertEquals(DecoPlacer.rotFor(3), s.rot, "hung on that tile's south edge, against the loc")
    }

    @Test
    fun `the editor's preview is recognised so Remove and Adjust act on it`() {
        val player = player(Tile(3200, 3200, 0))
        DecoPlacer.startNew(player, 62754, Tile(3200, 3201, 0), wall = null)
        val s = DecoPlacer.session(player)!!
        s.previewTile = s.tile
        assertTrue(DecoPlacer.isPreview(player, Tile(3200, 3201, 0), 62754))
        assertFalse(DecoPlacer.isPreview(player, Tile(3200, 3201, 0), 62755))
        assertFalse(DecoPlacer.isPreview(player, Tile(3201, 3201, 0), 62754))
        DecoPlacer.key(player, "delete", 0)
        assertNull(DecoPlacer.session(player), "Remove on an unsaved preview discards it")
        assertFalse(DecoPlacer.isPreview(player, Tile(3200, 3201, 0), 62754))
    }
}
