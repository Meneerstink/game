package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * N609 14-11 16:2x VLOERLICHT-PROEF (eigenaar 15:3x: vloeren ~2x lichter dan OSRS; advies A = eerst onderzoeken + proef, niets live zonder
 * zijn ja). The ToA map squares already carry a 16-byte environment trailer (Rev667WorldCodec 'trailing', client MapRegion.decodeEnvironment
 * -> Environment.method8386): code 0, flags 0x72, aFloat204 (0x2, globalAmbient via toolkit.xa) = 0x0071/256 = 0.44, then the 0x10/0x20/0x40
 * fields; 0x4 aFloat205 (sun diffuse via toolkit.ZA) is NOT set -> client default 0.70. This tool sets/changes 0x4 to <sun>/256 for the ToA
 * squares (raid + lobby) by splicing two bytes after the 0x2 value; 'revert' removes it again. Only effective at lightDetail==1.
 *
 * Usage: plan <sun256> | apply <sun256> | revert   (e.g. apply 90 = 0.35)
 */
object ToaEnvironmentTrialTool {
    private val EXPECTED_PREFIX = byteArrayOf(0x00, 0x72)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty() && args[0] in setOf("plan", "apply", "revert", "ambient-plan", "ambient-apply")) {
            "Usage: plan <sun256> | apply <sun256> | revert | ambient-plan <amb256> | ambient-apply <amb256>"
        }
        // N615 17-11: client RspsAreaLighting honours the ToA trailer at every light detail -> 'ambient-*' sets the 0x2 value (bytes 2..3).
        val ambient = args[0].startsWith("ambient-")
        val revert = args[0] == "revert"
        val sun = if (revert) -1 else requireNotNull(args.getOrNull(1)?.toIntOrNull()) { "value missing" }.also { require(it in 1..1024) }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        // optional 3rd arg = ToaUpperTileFlagTool scope (necropolis, cox ...) - for reading the trailers of other areas (plan only).
        val targets = args.getOrNull(2)?.let { require(args[0].endsWith("plan")) { "scope only with plan" }; ToaUpperTileFlagTool.targets(it) }
            ?: (ToaUpperTileFlagTool.targets("raid") + ToaUpperTileFlagTool.targets("lobby"))
        val mutations = mutableListOf<CacheMutation>()
        val game = CacheLibrary(paths[0])
        val files = CacheLibrary(paths[1])
        try {
            for ((_, name) in targets) {
                val archive = requireNotNull(game.index(5).archive(name)) { "Missing $name" }
                val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                val before = requireNotNull(archive.file(0)?.data)
                check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                val map = Rev667TileCodec.decode(before)
                val t = map.trailing
                val newTrailing: ByteArray = when {
                    ambient && t.size >= 4 && t[0] == 0.toByte() && (t[1].toInt() and 0x2) != 0 ->
                        if (((t[2].toInt() and 0xFF) shl 8 or (t[3].toInt() and 0xFF)) == sun) { println("SAME $name"); continue }
                        else t.copyOf().also { it[2] = (sun shr 8).toByte(); it[3] = sun.toByte() }
                    ambient -> { println("SKIP $name trailer ${t.joinToString("") { "%02x".format(it) }}"); continue }
                    t.size >= 4 && t[0] == 0.toByte() && (t[1].toInt() and 0xFF) == 0x72 && !revert -> // insert 0x4 after the 0x2 value (bytes 2..3)
                        byteArrayOf(0, 0x76) + t.copyOfRange(2, 4) + byteArrayOf((sun shr 8).toByte(), sun.toByte()) + t.copyOfRange(4, t.size)
                    t.size >= 6 && t[0] == 0.toByte() && (t[1].toInt() and 0xFF) == 0x76 && !revert -> // already trialled: replace value
                        t.copyOf().also { it[4] = (sun shr 8).toByte(); it[5] = sun.toByte() }
                    t.size >= 6 && t[0] == 0.toByte() && (t[1].toInt() and 0xFF) == 0x76 && revert ->
                        byteArrayOf(0, 0x72) + t.copyOfRange(2, 4) + t.copyOfRange(6, t.size)
                    else -> { println("SKIP $name trailer ${t.joinToString("") { "%02x".format(it) }}"); continue }
                }
                map.trailing = newTrailing
                val after = Rev667TileCodec.encode(map)
                println("ENV $name OUD ${t.joinToString("") { "%02x".format(it) }} -> NIEUW ${newTrailing.joinToString("") { "%02x".format(it) }}")
                if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                    "N609 env trial $name sun=${if (revert) "default" else "$sun/256"}", CacheItemProbeTool.sha1(before))
            }
        } finally { game.close(); files.close() }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan" || args[0] == "ambient-plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("ENV_TRIAL transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
