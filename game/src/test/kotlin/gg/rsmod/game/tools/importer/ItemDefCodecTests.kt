package gg.rsmod.game.tools.importer

import gg.rsmod.game.fs.def.ItemDef
import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate A8 of `RSPS_CURRENT_SPRINT.json`: the edits that turn a donor clone into a real imported item
 * are opcode surgery on a live cache definition, so they are proven here on synthetic streams before
 * being pointed at either real cache.
 *
 * The final assertion in each case is made with [ItemDef.decode] - the server's own decoder - so a
 * passing test means the produced bytes are readable by the thing that will actually read them, not
 * merely by [ItemDefCodec] itself.
 */
class ItemDefCodecTests {
    /** A minimal but representative definition: flag, cost, models, camera, name, recolour, links. */
    private fun donor(): ByteArray {
        val buf = Unpooled.buffer()
        buf.writeByte(16) // members flag, no payload
        buf.writeByte(12).writeInt(1600) // cost
        buf.writeByte(1).writeShort(2562) // inventory model
        buf.writeByte(4).writeShort(1200) // zoom2d
        buf.writeByte(23).writeShort(512) // male worn model
        buf.writeByte(25).writeShort(512) // female worn model
        buf.writeByte(40).writeByte(1).writeShort(6674).writeShort(31516) // recolour table
        buf.writeByte(2).writeBytes("Magic shortbow".toByteArray(Charsets.ISO_8859_1)).writeByte(0)
        buf.writeByte(97).writeShort(862) // noted link
        buf.writeByte(121).writeShort(13528) // lent link
        buf.writeByte(0) // terminator
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        buf.release()
        return bytes
    }

    private fun decodeServerSide(bytes: ByteArray): ItemDef {
        val def = ItemDef(22326)
        def.decode(Unpooled.wrappedBuffer(bytes))
        return def
    }

    @Test
    fun stringOverridesForOpcodesTheDonorLacksAreAppendedAsNewMenuOptions() {
        // The synthetic donor has no inventory-option opcodes (35..39) at all, like a real partyhat
        // that only carries "Wear" - the clone must gain the extra options, keep everything else.
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = donor(),
                stringOverrides = mapOf(2 to "Crown of Helios", 36 to "Wear", 37 to "Command", 38 to "Teleport"),
                removedOpcodes = setOf(97, 121),
            )

        val opcodes = ItemDefCodec.describeOpcodes(edited)
        assertTrue("appended option opcodes expected, got $opcodes", opcodes.contains("37=\"Command\""))
        assertTrue(opcodes.contains("38=\"Teleport\""))
        assertTrue("untouched opcodes must survive, got $opcodes", opcodes.contains("4=1200"))
        assertTrue("noted link must be dropped, got $opcodes", opcodes.none { it.startsWith("97=") })

        val def = decodeServerSide(edited)
        assertEquals("Crown of Helios", def.name)
        assertEquals(listOf(null, "Wear", "Command", "Teleport", null), def.inventoryMenu.toList())
        assertEquals(1600, def.cost)
    }

    @Test
    fun shortOverridesReplaceModelIdsWithoutDisturbingAnythingElse() {
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = donor(),
                stringOverrides = mapOf(2 to "Twisted bow"),
                shortOverrides = mapOf(1 to 65517, 23 to 65518, 25 to 65519),
            )

        val opcodes = ItemDefCodec.describeOpcodes(edited)
        assertTrue("inventory model should be the imported one, got $opcodes", opcodes.contains("1=65517"))
        assertTrue(opcodes.contains("23=65518"))
        assertTrue(opcodes.contains("25=65519"))
        assertTrue("untouched opcodes must survive, got $opcodes", opcodes.contains("4=1200"))
        assertEquals("Twisted bow", ItemDefCodec.readName(edited))

        val def = decodeServerSide(edited)
        assertEquals("Twisted bow", def.name)
        assertEquals(65518, def.maleWornModel)
        assertEquals(65519, def.maleWornModel2)
        assertEquals(1600, def.cost)
    }

    @Test
    fun anAbsentOpcodeIsAppendedRatherThanSilentlyDropped() {
        // The donor has no opcode 5/6/7/8 at all; an imported item that needs them must still get
        // them, because a TLV stream simply has no slot reserved for an opcode it never carried.
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = donor(),
                stringOverrides = emptyMap(),
                shortOverrides = mapOf(5 to 720, 6 to 1500, 7 to 65533, 8 to 1),
            )

        val opcodes = ItemDefCodec.describeOpcodes(edited)
        assertTrue("expected the appended camera opcodes, got $opcodes", opcodes.containsAll(listOf("5=720", "6=1500", "7=65533", "8=1")))
        assertEquals("the donor's own opcodes should be unchanged", "Magic shortbow", ItemDefCodec.readName(edited))
        // Still a well-formed stream for the real decoder, terminator and all.
        assertEquals(1600, decodeServerSide(edited).cost)
    }

    @Test
    fun removedOpcodesAreGoneEntirely() {
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = donor(),
                stringOverrides = emptyMap(),
                shortOverrides = emptyMap(),
                removedOpcodes = setOf(40, 97, 121),
            )

        val opcodes = ItemDefCodec.describeOpcodes(edited)
        assertTrue("the donor recolour table must not survive, got $opcodes", opcodes.none { it.startsWith("40=") })
        assertTrue("the donor noted link must not survive, got $opcodes", opcodes.none { it.startsWith("97=") })
        assertTrue("the donor lent link must not survive, got $opcodes", opcodes.none { it.startsWith("121=") })
        assertEquals("nothing else should have been dropped", 7, opcodes.size)
        assertEquals(0, decodeServerSide(edited).noteLinkId)
    }

    @Test
    fun intOverrideReplacesTheCost() {
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = donor(),
                stringOverrides = emptyMap(),
                intOverrides = mapOf(12 to 4000000),
            )
        assertEquals(4000000, decodeServerSide(edited).cost)
    }

    @Test
    fun anIdenticalCloneIsByteIdentical() {
        val source = donor()
        assertTrue(
            "a clone with no overrides must not perturb a single byte",
            source.contentEquals(ItemDefCodec.cloneWithOverrides(source, emptyMap())),
        )
    }

    /** A donor carrying an opcode-249 params block: a mix of int params, matching the exact shape
     * [gg.rsmod.game.fs.Definition.readParams] expects (count byte, then per entry isString byte +
     * 3-byte id + 4-byte int value). */
    private fun donorWithParams(): ByteArray {
        val buf = Unpooled.buffer()
        buf.writeByte(2).writeBytes("Twisted bow".toByteArray(Charsets.ISO_8859_1)).writeByte(0)
        buf.writeByte(249)
        buf.writeByte(5) // param count
        buf.writeByte(0).writeMedium(686).writeInt(16)
        buf.writeByte(0).writeMedium(687).writeInt(1)
        buf.writeByte(0).writeMedium(23).writeInt(50)
        buf.writeByte(0).writeMedium(749).writeInt(4)
        buf.writeByte(0).writeMedium(750).writeInt(50)
        buf.writeByte(0) // terminator
        val bytes = ByteArray(buf.readableBytes())
        buf.readBytes(bytes)
        buf.release()
        return bytes
    }

    /**
     * The Twisted Bow special-attack-bar gate (`RSPS_CURRENT_SPRINT.json`
     * FINAL_TWISTED_BOW_RUNTIME_VALIDATION_GATE, 2026-09-04): CS2 script 1136 (interface 884
     * component 2) reads item param 687 directly, independently of the server's
     * [gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks.hasSpecialAttack] map, to decide
     * whether to show the special-attack-bar layer. [removedParamIds] must drop exactly that one
     * param entry and leave every other opcode-249 entry - including unrelated ids the donor also
     * carries - untouched.
     */
    @Test
    fun removedParamIdsDropOnlyTheNamedParamEntry() {
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = donorWithParams(),
                stringOverrides = emptyMap(),
                removedParamIds = setOf(687),
            )

        val def = decodeServerSide(edited)
        assertEquals("param 687 must be gone", null, def.params.get(687))
        assertTrue("param 687 must be gone", !def.params.containsKey(687))
        assertEquals(16, def.params.get(686))
        assertEquals(50, def.params.get(23))
        assertEquals(4, def.params.get(749))
        assertEquals(50, def.params.get(750))
        assertEquals("Twisted bow", def.name)
    }

    @Test
    fun removingAParamIdTheDonorNeverHadIsANoOp() {
        val source = donorWithParams()
        val edited =
            ItemDefCodec.cloneWithOverrides(
                source = source,
                stringOverrides = emptyMap(),
                removedParamIds = setOf(999999),
            )
        assertTrue("removing an absent param id must not perturb a single byte", source.contentEquals(edited))
    }

    @Test
    fun contradictoryInstructionsAreRefused() {
        val removeAndOverride =
            runCatching {
                ItemDefCodec.cloneWithOverrides(
                    source = donor(),
                    stringOverrides = emptyMap(),
                    shortOverrides = mapOf(1 to 65517),
                    removedOpcodes = setOf(1),
                )
            }.exceptionOrNull()
        assertTrue("removing and overriding the same opcode must fail loudly, got $removeAndOverride", removeAndOverride is IllegalArgumentException)

        val wrongWidth =
            runCatching {
                ItemDefCodec.cloneWithOverrides(donor(), emptyMap(), shortOverrides = mapOf(12 to 5))
            }.exceptionOrNull()
        assertTrue("a four-byte opcode is not short-overridable, got $wrongWidth", wrongWidth is IllegalArgumentException)

        val outOfRange =
            runCatching {
                ItemDefCodec.cloneWithOverrides(donor(), emptyMap(), shortOverrides = mapOf(1 to 70000))
            }.exceptionOrNull()
        assertTrue("an id past the 16-bit model ceiling must fail, got $outOfRange", outOfRange is IllegalArgumentException)
    }
}
