package gg.rsmod.game.tools.importer

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

/**
 * A byte-faithful walker/rewriter for the raw item-definition opcode stream this cache's item
 * archives (index [gg.rsmod.game.fs.ArchiveType.ITEM]) use.
 *
 * This is the write-side counterpart to [gg.rsmod.game.fs.def.ItemDef.decode] - modern-content
 * import pipeline, `RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION" entry. The
 * server's own [gg.rsmod.game.fs.def.ItemDef.decode] only interprets the opcodes it needs
 * (name, stack/cost/members flags, worn-model ids, note links, GE flag, team cape, lend ids,
 * recolour ids, the generic params block) and silently ignores everything else (the ~15
 * inventory-icon render opcodes - base model id, zoom, rotation, translation, colour/texture
 * replacement tables - the server never needs those, only the client does). That means this
 * codec cannot *originate* a correct-looking new item purely server-side: those render opcodes
 * would have to be authored as raw bytes with no way to verify what they'd actually look like
 * client-side, which is exactly the kind of guessed binary data this pipeline must not produce.
 *
 * What it *can* do safely: clone an existing, known-good donor item's entire byte stream
 * unchanged except for specific opcodes this tool deliberately overrides (currently: the name at
 * opcode 2, and the ground/inventory/equipment option-menu strings at opcodes 30-39) - matching
 * the standing authorization's "when no canonical representation exists, create the smallest
 * collision-safe server representation compatible with the existing client/cache architecture."
 * The donor's full render/model data carries over byte-for-byte, so the clone renders exactly
 * like its donor (a deliberate, documented placeholder - see each importer manifest entry's
 * `visualStatus` field) until real model/icon data is ported by a future, dedicated model-import
 * pass.
 *
 * The per-opcode field-width table below is transcribed directly from
 * [gg.rsmod.game.fs.def.ItemDef.decode]'s own `when` branches, so any opcode this codec skips
 * over is skipped by exactly as many bytes as the server's real decoder would consume - this
 * codec never has to guess a width.
 */
object ItemDefCodec {
    /** Opcodes whose payload is a fixed 2 bytes (`short`/`unsigned short`). */
    private val FIXED_2 =
        setOf(1, 4, 5, 6, 7, 8, 18, 23, 24, 25, 26, 78, 79, 90, 91, 92, 93, 95, 97, 98, 110, 111, 112, 121, 122, 139, 140)

    /** Opcodes whose payload is a fixed single byte. */
    private val FIXED_1 = setOf(96, 113, 114, 115, 134)

    /** Opcodes whose payload is a fixed four bytes (`int`) - in this revision, only the cost. */
    private val FIXED_4 = setOf(12)

    /** Opcodes 100..109 inclusive: two unsigned shorts (4 bytes) each, per the decode `when` branch. */
    private val RANGE_100_109 = 100..109

    /** Opcodes with no payload at all - the opcode's presence alone is the flag. */
    private val FLAG_ONLY = setOf(11, 16, 65)

    /** Opcodes whose payload is a single null-terminated string (see [gg.rsmod.util.io.BufferUtils.readString]). */
    private val STRING_OPCODES = setOf(2, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39)

    /**
     * Walks every opcode in [source] (a single item archive's decompressed bytes) and rebuilds an
     * identical byte stream, except that any opcode present as a key in [stringOverrides] has its
     * string payload replaced with the given value instead of copied from [source], and any opcode
     * present as a key in [shortOverrides] has its two-byte payload replaced with that value.
     * Opcodes not present in [source] and not listed in any override map are simply absent from the
     * output too, exactly as with any TLV format - this never invents an opcode by accident; an
     * override for an opcode the donor lacks is appended before the terminator.
     *
     * Valid override keys are [STRING_OPCODES] (name and the ground/inventory/equipment menu text)
     * and, for [shortOverrides], only [FIXED_2] opcodes - the ones whose payload really is a single
     * unsigned short. Every other opcode carries the donor's raw bytes through unchanged.
     *
     * [shortOverrides] is what lifts this codec past donor cloning: opcodes 1/23/24/25/26 are the
     * model ids and 4-8 the inventory-icon camera, so overriding them is how an imported item stops
     * rendering as its donor. The class doc's warning still stands - these values must come from a
     * real source (the pinned upstream definition) and never be authored by hand - which is why the
     * override is a caller-supplied map rather than anything this codec computes.
     *
     * [removedOpcodes] drops an opcode the donor carried but the imported item genuinely does not
     * have. That is not cosmetic: a donor's recolour table (opcode 40) would repaint faces of a
     * freshly imported mesh, and a donor's noted/lent links (97 / 121) would point the new item at
     * the donor's noted and lent variants. Silently inheriting those is exactly the half-truth this
     * pipeline exists to prevent, so removal is explicit and per-opcode rather than automatic.
     *
     * [removedParamIds] is the same idea one level finer, for opcode 249 specifically: that opcode
     * is not one value but a generic id->value map (see [gg.rsmod.game.fs.Definition.readParams]),
     * and a donor's individual param entries can carry independent, unrelated meanings - e.g. the
     * 2026-09-04 Twisted Bow special-attack-bar gate (`RSPS_CURRENT_SPRINT.json`
     * FINAL_TWISTED_BOW_RUNTIME_VALIDATION_GATE) proved param 686 (weapon-style-category, read by
     * CS2 script 1142 to pick the Accurate/Rapid/Longrange widget set) must be kept, while param 687
     * (read independently by CS2 script 1136, interface 884 component 2, to show/hide the special-
     * attack-bar layer component 884:19) must not be, for a donor whose own special attack the
     * imported item does not share. Dropping the whole opcode-249 block over one bad entry would
     * also drop every other, unrelated param the block carries - exactly the kind of coarse fix this
     * pipeline must avoid. Every id in [removedParamIds] not present in the donor's params block is
     * simply a no-op, same as [removedOpcodes] on an absent opcode.
     */
    fun cloneWithOverrides(
        source: ByteArray,
        stringOverrides: Map<Int, String>,
        shortOverrides: Map<Int, Int> = emptyMap(),
        intOverrides: Map<Int, Int> = emptyMap(),
        removedOpcodes: Set<Int> = emptySet(),
        removedParamIds: Set<Int> = emptySet(),
        /** String params (opcode 249) to set; an existing entry with the same id is replaced. */
        stringParams: Map<Int, String> = emptyMap(),
    ): ByteArray {
        require(stringOverrides.keys.all { it in STRING_OPCODES }) {
            "Only string opcodes ($STRING_OPCODES) can be overridden by this codec."
        }
        require(shortOverrides.keys.all { it in FIXED_2 }) {
            "Only two-byte opcodes ($FIXED_2) can be short-overridden by this codec."
        }
        require(shortOverrides.values.all { it in 0..0xFFFF }) {
            "A short override outside 0..65535 cannot be encoded: $shortOverrides"
        }
        require(intOverrides.keys.all { it in FIXED_4 }) {
            "Only four-byte opcodes ($FIXED_4) can be int-overridden by this codec."
        }
        val overridden = shortOverrides.keys + intOverrides.keys + stringOverrides.keys
        require(removedOpcodes.none { it in overridden }) {
            "An opcode cannot be both removed and overridden: ${removedOpcodes.filter { it in overridden }}"
        }

        val input = Unpooled.wrappedBuffer(source)
        val output = Unpooled.buffer(source.size)
        val seen = mutableSetOf<Int>()

        while (true) {
            val opcode = input.readUnsignedByte().toInt()
            if (opcode == 0) {
                // Opcodes the donor never had are appended just before the terminator; a TLV stream
                // has no significant ordering, and the server's and client's decoders both loop.
                shortOverrides.filterKeys { it !in seen }.forEach { (missing, value) ->
                    output.writeByte(missing)
                    output.writeShort(value)
                }
                intOverrides.filterKeys { it !in seen }.forEach { (missing, value) ->
                    output.writeByte(missing)
                    output.writeInt(value)
                }
                // Menu text the donor never carried (e.g. a second inventory option on an item that
                // only had "Wear") is appended the same way; the name (opcode 2) is always present
                // on a real donor so this only ever adds option slots.
                stringOverrides.filterKeys { it !in seen }.forEach { (missing, value) ->
                    output.writeByte(missing)
                    writeNullTerminatedString(output, value)
                }
                if (249 !in seen && stringParams.isNotEmpty()) {
                    output.writeByte(249)
                    output.writeByte(0)
                    appendStringParams(output, output.writerIndex() - 1, stringParams)
                }
                output.writeByte(opcode)
                break
            }
            seen += opcode
            // Decoded into a scratch buffer first so a removed opcode can be dropped whole, without
            // this codec ever having to know an opcode's width twice.
            val field = Unpooled.buffer()
            copyOrOverrideField(input, field, opcode, stringOverrides, shortOverrides, intOverrides, removedParamIds + stringParams.keys)
            if (opcode == 249 && stringParams.isNotEmpty()) appendStringParams(field, 0, stringParams)
            if (opcode !in removedOpcodes) {
                output.writeByte(opcode)
                output.writeBytes(field)
            }
            field.release()
        }

        val result = ByteArray(output.readableBytes())
        output.readBytes(result)
        input.release()
        output.release()
        return result
    }

    /**
     * Every opcode in [source] in stream order, rendered as `opcode=payloadHex` (plus the unsigned
     * value for two-byte opcodes). Read-only evidence for the import gates: it makes "which render
     * opcodes does this donor actually carry" answerable from the real bytes instead of assumed.
     */
    fun describeOpcodes(source: ByteArray): List<String> {
        val input = Unpooled.wrappedBuffer(source)
        val described = mutableListOf<String>()
        while (true) {
            val opcode = input.readUnsignedByte().toInt()
            if (opcode == 0) break
            val scratch = Unpooled.buffer()
            copyOrOverrideField(input, scratch, opcode, emptyMap(), emptyMap())
            val payload = ByteArray(scratch.readableBytes())
            scratch.readBytes(payload)
            scratch.release()
            val hex = payload.joinToString("") { "%02x".format(it) }
            described +=
                if (opcode in FIXED_2) {
                    "$opcode=${((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)}"
                } else if (opcode in STRING_OPCODES) {
                    "$opcode=\"${String(payload, 0, maxOf(payload.size - 1, 0), Charsets.ISO_8859_1)}\""
                } else if (payload.isEmpty()) {
                    "$opcode=FLAG"
                } else {
                    "$opcode=0x$hex"
                }
        }
        input.release()
        return described
    }

    /** Reads the field the [source]-derived [opcode] introduces from [input] and writes it back to
     * [output], substituting a string from [stringOverrides] instead of the original bytes if this
     * opcode is being overridden. */
    private fun copyOrOverrideField(
        input: ByteBuf,
        output: ByteBuf,
        opcode: Int,
        stringOverrides: Map<Int, String>,
        shortOverrides: Map<Int, Int> = emptyMap(),
        intOverrides: Map<Int, Int> = emptyMap(),
        removedParamIds: Set<Int> = emptySet(),
    ) {
        when {
            opcode in STRING_OPCODES -> {
                val original = readNullTerminatedString(input)
                writeNullTerminatedString(output, stringOverrides[opcode] ?: original)
            }
            opcode in FLAG_ONLY -> {
                // No payload to copy.
            }
            opcode in FIXED_1 -> copyBytes(input, output, 1)
            opcode in FIXED_2 && shortOverrides.containsKey(opcode) -> {
                input.skipBytes(2)
                output.writeShort(shortOverrides.getValue(opcode))
            }
            opcode in FIXED_2 -> copyBytes(input, output, 2)
            opcode in FIXED_4 && intOverrides.containsKey(opcode) -> {
                input.skipBytes(4)
                output.writeInt(intOverrides.getValue(opcode))
            }
            opcode in FIXED_4 -> copyBytes(input, output, 4)
            opcode in RANGE_100_109 -> copyBytes(input, output, 4)
            opcode == 124 -> copyBytes(input, output, 12)
            opcode == 125 -> copyBytes(input, output, 3)
            opcode == 126 -> copyBytes(input, output, 3)
            opcode == 127 -> copyBytes(input, output, 3)
            opcode == 128 -> copyBytes(input, output, 3)
            opcode == 129 -> copyBytes(input, output, 3)
            opcode == 130 -> copyBytes(input, output, 3)
            opcode == 40 -> copyCountPrefixed(input, output, entryWidth = 4)
            opcode == 41 -> copyCountPrefixed(input, output, entryWidth = 4)
            opcode == 42 -> copyCountPrefixed(input, output, entryWidth = 1)
            opcode == 132 -> copyCountPrefixed(input, output, entryWidth = 2)
            opcode == 249 -> copyParams(input, output, removedParamIds)
            else ->
                throw IllegalArgumentException(
                    "Unknown item-def opcode $opcode - this codec's field-width table is derived from " +
                        "ItemDef.decode() and must be extended there and here together before this cache " +
                        "revision can produce it safely.",
                )
        }
    }

    private fun copyBytes(
        input: ByteBuf,
        output: ByteBuf,
        length: Int,
    ) {
        output.writeBytes(input, length)
    }

    /** Copies an unsigned-byte entry count followed by `count * entryWidth` bytes - opcodes 40/41/42/132's shape. */
    private fun copyCountPrefixed(
        input: ByteBuf,
        output: ByteBuf,
        entryWidth: Int,
    ) {
        val count = input.readUnsignedByte()
        output.writeByte(count.toInt())
        output.writeBytes(input, count.toInt() * entryWidth)
    }

    /** Copies opcode 249's params block, matching [gg.rsmod.game.fs.Definition.readParams]'s exact
     * shape: count(byte), then per entry isString(byte) + id(3-byte medium) + value (null-terminated
     * string if isString, else a 4-byte int). Entries whose id is in [removedParamIds] are dropped
     * and excluded from the rewritten count - every other entry, and their relative order, carries
     * over unchanged. Buffered per-entry first (rather than streamed straight through, as every
     * other opcode here is) because the leading count byte has to reflect the post-removal total,
     * which isn't known until every entry has been read. */
    private fun copyParams(
        input: ByteBuf,
        output: ByteBuf,
        removedParamIds: Set<Int>,
    ) {
        val count = input.readUnsignedByte()
        val kept = mutableListOf<ByteArray>()
        for (i in 0 until count) {
            val entry = Unpooled.buffer()
            val isString = input.readUnsignedByte()
            entry.writeByte(isString.toInt())
            val idBuf = Unpooled.buffer()
            copyBytes(input, idBuf, 3) // id (unsigned medium)
            val id = ((idBuf.getByte(0).toInt() and 0xFF) shl 16) or
                ((idBuf.getByte(1).toInt() and 0xFF) shl 8) or
                (idBuf.getByte(2).toInt() and 0xFF)
            entry.writeBytes(idBuf)
            idBuf.release()
            if (isString.toInt() == 1) {
                writeNullTerminatedString(entry, readNullTerminatedString(input))
            } else {
                copyBytes(input, entry, 4)
            }
            if (id in removedParamIds) {
                entry.release()
            } else {
                val bytes = ByteArray(entry.readableBytes())
                entry.readBytes(bytes)
                entry.release()
                kept += bytes
            }
        }
        output.writeByte(kept.size)
        kept.forEach { output.writeBytes(it) }
    }

    /** Appends [params] as string entries to a params block whose count byte sits at [countIndex] of [block]. */
    private fun appendStringParams(
        block: ByteBuf,
        countIndex: Int,
        params: Map<Int, String>,
    ) {
        block.setByte(countIndex, block.getUnsignedByte(countIndex) + params.size)
        params.forEach { (id, value) ->
            block.writeByte(1)
            block.writeMedium(id)
            writeNullTerminatedString(block, value)
        }
    }

    private fun readNullTerminatedString(buf: ByteBuf): String {
        val start = buf.readerIndex()
        while (buf.readByte().toInt() != 0) {
            // scan for terminator
        }
        val length = buf.readerIndex() - start
        val bytes = ByteArray(length)
        buf.readerIndex(start)
        buf.readBytes(bytes)
        return String(bytes, 0, length - 1, Charsets.ISO_8859_1)
    }

    private fun writeNullTerminatedString(
        buf: ByteBuf,
        value: String,
    ) {
        buf.writeBytes(value.toByteArray(Charsets.ISO_8859_1))
        buf.writeByte(0)
    }

    /** Reads just the name (opcode 2) out of a raw item archive, for post-write validation - mirrors
     * [gg.rsmod.game.fs.def.ItemDef.decode]'s own opcode-2 handling without needing a full [gg.rsmod.game.fs.def.ItemDef]. */
    fun readName(source: ByteArray): String? {
        val input = Unpooled.wrappedBuffer(source)
        var name: String? = null
        while (true) {
            val opcode = input.readUnsignedByte().toInt()
            if (opcode == 0) break
            if (opcode == 2) {
                name = readNullTerminatedString(input)
            } else {
                val scratch = Unpooled.buffer()
                copyOrOverrideField(input, scratch, opcode, emptyMap())
                scratch.release()
            }
        }
        input.release()
        return name
    }
}
