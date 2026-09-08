package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * Model-id reference walkers for the rev-667 definition types the model-namespace census must
 * trace before a hole below the physical ceiling can be called free. Opcode tables are the
 * client's (`npctype/NPCType.java`, `idktype/IDKType.java`), cross-checked against the server's
 * own boot-proven `NpcDef.decode` widths. Every walker hard-fails on an unknown opcode, so a
 * silently-misread definition cannot hide a reference.
 */
object Rev667ModelReferenceWalkers {
    const val NPC_INDEX = 18
    const val CONFIG_INDEX = 2
    const val IDK_GROUP = 3

    /** NPC id -> (body model ids, head model ids). Group = id ushr 7, file = id and 0x7F. */
    fun npcModelIds(
        library: CacheLibrary,
        record: (id: Int, role: String) -> Unit,
    ): Int {
        val index = library.index(NPC_INDEX)
        var count = 0
        index.archiveIds().forEach { group ->
            val archive = index.archive(group) ?: return@forEach
            archive.fileIds().forEach { file ->
                val data = archive.file(file)?.data ?: return@forEach
                val npcId = (group shl 7) or file
                count++
                walkNpc(npcId, data, record)
            }
        }
        return count
    }

    fun walkNpc(
        npcId: Int,
        data: ByteArray,
        record: (id: Int, role: String) -> Unit,
    ) {
        val buf = RegionBuffer(data)
        while (true) {
            val op = buf.u8()
            if (op == 0) break
            when (op) {
                1 -> repeat(buf.u8()) {
                    val m = buf.u16()
                    if (m != 0xFFFF) record(m, "npc_model")
                }
                2 -> buf.string()
                12 -> buf.u8()
                in 30..34 -> buf.string()
                40, 41 -> repeat(buf.u8()) {
                    buf.u16()
                    buf.u16()
                }
                42 -> repeat(buf.u8()) { buf.i8() }
                60 -> repeat(buf.u8()) { record(buf.u16(), "npc_head_model") }
                93, 99, 107, 109, 111, 141, 143, 158, 159, 162 -> {}
                95, 97, 98, 102, 103, 122, 123, 127, 137, 138, 139, 142 -> buf.u16()
                100, 101, 119, 125, 128, 140, 163, 165, 168 -> buf.i8()
                106, 118 -> {
                    buf.u16()
                    buf.u16()
                    if (op == 118) buf.u16()
                    val n = buf.u8()
                    repeat(n + 1) { buf.u16() }
                }
                113 -> {
                    buf.u16()
                    buf.u16()
                }
                114, 115 -> {
                    buf.i8()
                    buf.i8()
                }
                121 -> repeat(buf.u8()) {
                    buf.u8()
                    buf.i8()
                    buf.i8()
                    buf.i8()
                }
                134 -> {
                    repeat(4) { buf.u16() }
                    buf.u8()
                }
                135, 136 -> {
                    buf.u8()
                    buf.u16()
                }
                in 150..154 -> buf.string()
                155 -> repeat(4) { buf.i8() }
                160 -> repeat(buf.u8()) { buf.u16() }
                164 -> {
                    buf.u16()
                    buf.u16()
                }
                249 -> repeat(buf.u8()) {
                    val isString = buf.u8() == 1
                    buf.u24()
                    if (isString) buf.string() else buf.i32()
                }
                else -> error("NPC $npcId: unknown rev-667 npc opcode $op at ${buf.position}")
            }
        }
        check(buf.position == data.size) { "NPC $npcId: walker consumed ${buf.position} of ${data.size} bytes." }
    }

    /** Identity kits live in config archive 2 group 3, file = idk id. */
    fun idkModelIds(
        library: CacheLibrary,
        record: (id: Int, role: String) -> Unit,
    ): Int {
        val archive = library.index(CONFIG_INDEX).archive(IDK_GROUP) ?: return 0
        var count = 0
        archive.fileIds().forEach { file ->
            val data = archive.file(file)?.data ?: return@forEach
            count++
            walkIdk(file, data, record)
        }
        return count
    }

    fun walkIdk(
        idkId: Int,
        data: ByteArray,
        record: (id: Int, role: String) -> Unit,
    ) {
        val buf = RegionBuffer(data)
        while (true) {
            val op = buf.u8()
            if (op == 0) break
            when (op) {
                1 -> buf.u8()
                2 -> repeat(buf.u8()) { record(buf.u16(), "idk_model") }
                3 -> {}
                40, 41 -> repeat(buf.u8()) {
                    buf.u16()
                    buf.u16()
                }
                in 60..69 -> record(buf.u16(), "idk_head_model")
                else -> error("IDK $idkId: unknown rev-667 idk opcode $op at ${buf.position}")
            }
        }
        check(buf.position == data.size) { "IDK $idkId: walker consumed ${buf.position} of ${data.size} bytes." }
    }

    /** Loc types: archive 16, group = id ushr 8, file = id and 0xFF; both model lists of opcode 5 count. */
    fun locModelIds(
        library: CacheLibrary,
        record: (id: Int, role: String) -> Unit,
    ): Int {
        val index = library.index(Rev667RegionProbeTool.LOC_INDEX)
        var count = 0
        index.archiveIds().forEach { group ->
            val archive = index.archive(group) ?: return@forEach
            archive.fileIds().forEach { file ->
                val data = archive.file(file)?.data ?: return@forEach
                val id = (group shl 8) or file
                count++
                Rev667LocType.decode(id, data).allModels.forEach { record(it, "loc_model") }
            }
        }
        return count
    }
}
