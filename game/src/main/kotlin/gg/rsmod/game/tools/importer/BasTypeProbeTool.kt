package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled

/**
 * Read-only recovery of an npc's real render animations.
 *
 * ## Why this tool exists
 *
 * Every previous Summoning run concluded that a familiar's Follower Details idle animation and
 * its world animations were unrecoverable. They were not: they were being looked for in the wrong
 * place. `NPCType` opcode 127 is **`basId`**, a body-animation-set id - not an animation id - and
 * this server's [NpcDef] decoded it into a field called `walkAnim`, so nothing ever followed it
 * through to the animations it keys. The real sequences live in `BASType`, **config group 32**,
 * whose opcode 1 carries `ready` and `walk` as a pair, with `crawl` (2) and `run` (6) separate.
 *
 * Both facts are read straight out of the revision 667 client in `C:\RSPS\2011scape-client`:
 * `com/jagex/game/runetek6/config/npctype/NPCType.java:424` for the opcode, and
 * `com/jagex/game/runetek6/config/bastype/BASType.java:239` plus
 * `com/jagex/game/runetek6/config/Js5ConfigGroup.java:16` for the group. Nothing here is guessed.
 *
 * ## Usage
 *
 * ```
 * ./gradlew :game:runBasTypeProbeTool --args="<cachePath> npc <npcId> [npcId ...]"
 * ./gradlew :game:runBasTypeProbeTool --args="<cachePath> bas <basId> [basId ...]"
 * ```
 */
object BasTypeProbeTool {
    /** Config index; BASType lives here (`Js5ConfigGroup.BASTYPE = 32`). */
    private const val INDEX_CONFIG = 2
    private const val GROUP_BASTYPE = 32

    /** Npc definitions are their own modern, paged index: group = id ushr 7, file = id and 0x7f. */
    private const val INDEX_NPC = 18

    /** One BASType, limited to the movement sequences - the only fields this run needs. */
    data class Bas(
        val id: Int,
        val ready: Int,
        val walk: Int,
        val crawl: Int,
        val run: Int,
        val readyAnimations: List<Int>,
    ) {
        override fun toString(): String =
            "BAS_$id ready=$ready walk=$walk crawl=$crawl run=$run" +
                if (readyAnimations.isEmpty()) "" else " readyAnimations=$readyAnimations"
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) {
            "Usage: <cachePath> npc <npcId> [npcId ...] | <cachePath> bas <basId> [basId ...]"
        }
        val library = CacheLibrary(args[0])
        try {
            val ids = args.drop(2).map { it.toInt() }
            when (args[1].lowercase()) {
                "npc" ->
                    ids.forEach { npcId ->
                        val def = npcDef(library, npcId)
                        if (def == null) {
                            println("NPC_$npcId ABSENT")
                            return@forEach
                        }
                        val bas = def.basId.takeIf { it != -1 }?.let { basType(library, it) }
                        println("NPC_$npcId name='${def.name}' size=${def.size} basId=${def.basId} ${bas ?: "BAS_NONE"}")
                    }
                "bas" -> ids.forEach { println(basType(library, it) ?: "BAS_$it ABSENT") }
                else -> error("Unknown mode '${args[1]}', expected 'npc' or 'bas'")
            }
        } finally {
            library.close()
        }
    }

    fun npcDef(
        library: CacheLibrary,
        npcId: Int,
    ): NpcDef? {
        val data = library.data(INDEX_NPC, npcId ushr 7, npcId and 0x7f) ?: return null
        val def = NpcDef(npcId)
        def.decode(Unpooled.wrappedBuffer(data))
        return def
    }

    /**
     * Decodes one BASType, reading only the opcodes this run needs and skipping the rest by their
     * real widths so the stream stays aligned. Widths are taken from `BASType.decode`.
     */
    fun basType(
        library: CacheLibrary,
        basId: Int,
    ): Bas? {
        val data = library.data(INDEX_CONFIG, GROUP_BASTYPE, basId) ?: return null
        val buf = Unpooled.wrappedBuffer(data)
        var ready = -1
        var walk = -1
        var crawl = -1
        var run = -1
        var readyAnimations = emptyList<Int>()
        while (buf.isReadable) {
            when (val code = buf.readUnsignedByte().toInt()) {
                0 -> return Bas(basId, ready, walk, crawl, run, readyAnimations)
                1 -> {
                    ready = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                    walk = buf.readUnsignedShort().let { if (it == 65535) -1 else it }
                }
                2 -> crawl = buf.readUnsignedShort()
                3, 4, 5, 7, 8, 9 -> buf.readUnsignedShort()
                6 -> run = buf.readUnsignedShort()
                // 26 hillWidth/hillHeight, both g1.
                26 -> buf.skipBytes(2)
                // 27 wornTransformations: slot byte then six signed shorts.
                27 -> buf.skipBytes(1 + 12)
                // 28 invObjSlots: count byte then that many bytes.
                28 -> buf.skipBytes(buf.readUnsignedByte().toInt())
                // Single-byte scalars: yaw/roll/pitch acceleration and movementAcceleration.
                29, 31, 34, 37 -> buf.skipBytes(1)
                // Two-byte scalars: max speeds, target angles, turn sequences, bar sprites, height.
                30, 32, 33, 35, 36, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51 ->
                    buf.skipBytes(2)
                52 -> {
                    val count = buf.readUnsignedByte().toInt()
                    val anims = ArrayList<Int>(count)
                    repeat(count) {
                        anims += buf.readUnsignedShort()
                        buf.readUnsignedByte() // weight
                    }
                    readyAnimations = anims
                }
                // 53 animateShadow = false, no payload.
                53 -> Unit
                // 54 hillMaxAngleX/Y, both g1.
                54 -> buf.skipBytes(2)
                // 55 maxWornRotation: slot byte then a short.
                55 -> buf.skipBytes(3)
                // 56 graphicOffsets: slot byte then three signed shorts.
                56 -> buf.skipBytes(1 + 6)
                else -> {
                    // An unmodelled opcode desynchronises the stream, so stop rather than emit
                    // numbers that would be misread as animation ids.
                    println("BAS_$basId UNHANDLED_OPCODE=$code (stopped, fields so far are still valid)")
                    return Bas(basId, ready, walk, crawl, run, readyAnimations)
                }
            }
        }
        return Bas(basId, ready, walk, crawl, run, readyAnimations)
    }
}
