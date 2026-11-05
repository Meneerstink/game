package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N568 (nachtrun 2026-11-05): the rev-667 client reads optional "environment" records after the 4x64x64 tile records of a map square
 * (2011scape-client MapRegion.method7893: opcode 0 = Environment(packet) - sun / fog colour / fog depth, 1 = lights, 2 = bloom,
 * 128 = skybox, 129 = occlusion). The map squares imported from OSRS carry none, so the client falls back to its default daylight
 * fog and the void beyond the cave walls shows beige where OSRS shows black (A9 Callisto's Den / Hunter's End 2026-11-05).
 *
 * This tool copies the opcode-0 record (only that one: lights are tied to their own square) of a rev-667 underground square onto
 * squares that have no trailing records at all. Source: the Wilderness part of the Edgeville Dungeon, m48_156 (12444): flags 0x72 -
 * 0x0071 light, sun direction (0,-100,0), fog colour 0x1B150D, fog depth 400.
 *
 * Usage: `<sourceSquare> <targetSquare>... [--apply]` - without `--apply` only the plan and preflight.
 */
object MapEnvironmentTool {
    /** The opcode-0 record (opcode byte included) at the start of [trailing], or null. */
    fun opcode0Record(trailing: ByteArray): ByteArray? {
        if (trailing.isEmpty() || trailing[0].toInt() != 0) return null
        var p = 1
        val flags = trailing[p++].toInt() and 0xFF
        if (flags and 0x01 != 0) p += 4
        if (flags and 0x02 != 0) p += 2
        if (flags and 0x04 != 0) p += 2
        if (flags and 0x08 != 0) p += 2
        if (flags and 0x10 != 0) p += 6
        if (flags and 0x20 != 0) p += 4
        if (flags and 0x40 != 0) p += 2
        if (flags and 0x80 != 0) p += 12
        require(p <= trailing.size) { "truncated opcode-0 environment record" }
        return trailing.copyOfRange(0, p)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val squares = args.filter { it != "--apply" }.map { it.toInt() }
        require(squares.size >= 2) { "Usage: <sourceSquare> <targetSquare>... [--apply]" }
        val source = squares.first()
        val targets = OsrsItemImportTool.TARGETS
        val mutations = mutableListOf<CacheMutation>()
        val library = CacheLibrary(targets[0])
        try {
            val maps = library.index(FeroxImportTool.MAP_INDEX)
            val sourceMap = Rev667TileCodec.decode(maps.archive(OsrsRegionImportTool.mapName(source))!!.file(0)!!.data!!)
            val record = opcode0Record(sourceMap.trailing) ?: error("source square $source has no opcode-0 environment record")
            println("SOURCE $source ${OsrsRegionImportTool.mapName(source)} record=${record.joinToString("") { "%02x".format(it) }}")
            for (square in squares.drop(1)) {
                val archive = maps.archive(OsrsRegionImportTool.mapName(square)) ?: error("no map ${OsrsRegionImportTool.mapName(square)}")
                val old = archive.file(0)!!.data!!
                val map = Rev667TileCodec.decode(old)
                if (map.trailing.isNotEmpty()) {
                    println("SKIP $square ${OsrsRegionImportTool.mapName(square)}: already has ${map.trailing.size} trailing bytes")
                    continue
                }
                map.trailing = record.copyOf()
                val bytes = Rev667TileCodec.encode(map)
                check(Rev667TileCodec.decode(bytes).trailing.contentEquals(record)) { "encode/decode trailing mismatch for $square" }
                mutations += CacheMutation(FeroxImportTool.MAP_INDEX, archive.id, 0, bytes, "N568 environment record on ${OsrsRegionImportTool.mapName(square)} (from ${OsrsRegionImportTool.mapName(source)})", CacheItemProbeTool.sha1(old))
                println("PLAN $square ${OsrsRegionImportTool.mapName(square)} group=${archive.id} ${old.size} -> ${bytes.size} bytes")
            }
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = targets, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
    }
}
