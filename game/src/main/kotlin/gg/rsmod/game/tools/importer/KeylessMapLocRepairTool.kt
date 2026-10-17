package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N10: replaces every map-square loc file `l<x>_<z>` that crashes the rev-667 client - still XTEA-encrypted while no key
 * exists for it, so the server sends 0,0,0,0 and the client reads the ciphertext ([ClientMapLocDecode]) - by an EMPTY,
 * UNENCRYPTED loc file, in both caches through [CacheTransaction].
 *
 * Donor: Void `tools/cache/RemoveXteas.kt:9-28` stores every loc file unencrypted (decrypt with the known key, put back
 * without one) and `tools/map/MapPacker.kt:36-62` (packMissingMaps) handles a square whose loc file cannot be decrypted:
 * copy it from a revision that has the key, otherwise "Can't find map". Squares that have a key in some 667 cache got it in
 * data/xteas/xteas.json (51 OpenRS2-278 keys, N10 step 1); for the rest no 646..759 OpenRS2 cache or Novite key opens the
 * rev-667 file, so the only readable content is the empty loc file. The server already loads no locs there (its own decrypt
 * fails), so client and server then agree. Empty loc file = the rev-667 loc terminator `00`, exactly what the 50 empty
 * keyed squares of OpenRS2 278 inflate to (e.g. l42_39 with its key).
 *
 * Squares that HAVE a key and still do not decode are a different problem (wrong key) and stop the tool.
 *
 * Usage: `./gradlew :game:runKeylessMapLocRepairTool --args="plan|apply [<cache>;<cache>]"` (default: both production
 * caches; apply needs both servers on 50015/50016 down, [CacheTransaction] checks it).
 */
object KeylessMapLocRepairTool {
    /** The rev-667 empty loc file: only the big-smart 0 that ends the loc-id list. */
    val EMPTY_LOC_FILE = byteArrayOf(0)

    class Repair(val square: Int, val group: Int, val outcome: ClientMapLocDecode.Outcome, val storedSha1: String)

    private class XteaEntry(val mapsquare: Int = 0, val key: IntArray = IntArray(4))

    fun loadKeys(file: File = File(RspsPaths.data("xteas/xteas.json"))): Map<Int, IntArray> =
        com.google.gson.Gson().fromJson(file.readText(), Array<XteaEntry>::class.java).associate { it.mapsquare to it.key }

    /** Every loc file of the cache at [path] that crashes the client with the key the server sends. */
    fun findRepairs(
        path: String,
        keys: Map<Int, IntArray>,
    ): List<Repair> {
        val library = CacheLibrary(path)
        try {
            val index = library.index(5)
            val byName = index.archives().associate { it.hashName to it.id }
            val repairs = mutableListOf<Repair>()
            val wrongKeys = mutableListOf<String>()
            for (x in 0 until 256) for (z in 0 until 256) {
                val group = byName["l${x}_$z".hashCode()] ?: continue
                val stored = index.readArchiveSector(group)?.data ?: continue
                val square = (x shl 8) or z
                val key = keys[square]
                val outcome = ClientMapLocDecode.classify(stored, key ?: IntArray(4))
                if (!outcome.crashesClient) continue
                if (key != null && key.any { it != 0 }) {
                    wrongKeys += "$square l${x}_$z $outcome"
                    continue
                }
                repairs += Repair(square, group, outcome, CacheItemProbeTool.sha1(stored))
            }
            check(wrongKeys.isEmpty()) { "$path: loc files that do not decode WITH their key (wrong key, not this tool's case): $wrongKeys" }
            return repairs
        } finally {
            library.close()
        }
    }

    fun mutations(
        targets: List<String>,
        keys: Map<Int, IntArray>,
    ): List<CacheMutation> {
        val perTarget = targets.map { findRepairs(it, keys) }
        val first = perTarget.first()
        perTarget.drop(1).forEachIndexed { i, other ->
            check(other.map { Triple(it.square, it.group, it.storedSha1) } == first.map { Triple(it.square, it.group, it.storedSha1) }) {
                "${targets[i + 1]} holds other loc files than ${targets[0]}: the caches must be identical before this repair"
            }
        }
        return first.map {
            CacheMutation(
                indexId = 5,
                groupId = it.group,
                fileId = 0,
                newBytes = EMPTY_LOC_FILE,
                label = "N10 keyless loc file l${it.square shr 8}_${it.square and 0xFF} (${it.outcome}) -> empty unencrypted loc file",
                undecodableCurrentSha1 = it.storedSha1,
            )
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply [<cache>;<cache>]" }
        val targets =
            args.getOrNull(1)?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutations(targets, loadKeys())
        mutations.forEach { println("REPAIR ${it.describeLocation()} ${it.label}") }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO every loc file decodes for the client")
            return
        }
        val tx = CacheTransaction(targets, mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        println("PREFLIGHT_SUMMARY ${preflight.groupingBy { it.outcome }.eachCount()} targets=$targets")
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK every target holds the intended bytes (journal ${tx.journalDir})")
    }
}
