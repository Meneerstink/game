package gg.rsmod.game.fs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.tools.importer.ClientMapLocDecode
import gg.rsmod.game.tools.importer.ClientMapLocDecode.Outcome
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N10 (Keurmeester LC01 2026-10-14: the client died three times in an hour with `OutOfMemoryError: Java heap space` at
 * js5.decodeContainer:151 <- Static489.method6548:90 on a map load around Pest Control). Class: a map square whose loc
 * file `l<x>_<z>` is XTEA-encrypted while the server has no key for it. The server then sends 0,0,0,0, the client reads
 * the ciphertext as a plain container, takes a garbage ulen and allocates it (OutOfMemoryError) or inflates garbage (T3).
 *
 * The whole class: EVERY loc file in index 5 of BOTH caches (game server and file server, which the client downloads),
 * decoded exactly the way the client decodes it ([ClientMapLocDecode]) with the key the server sends for its square
 * (data/xteas/xteas.json, or none), must not crash the client.
 *
 * `RSPS_N10_CACHES=<cache>;<cache>` points the check at other copies (scratch copies with a pending cache transaction).
 */
class MapLocClientDecodeTests {
    private class XteaEntry(val mapsquare: Int = 0, val key: IntArray = IntArray(4))

    private val keys: Map<Int, IntArray> by lazy {
        com.google.gson.Gson().fromJson(File("../data/xteas/xteas.json").readText(), Array<XteaEntry>::class.java)
            .associate { it.mapsquare to it.key }
    }

    private val caches: List<String> =
        System.getenv("RSPS_N10_CACHES")?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: listOf(File("../data/cache").path, RspsPaths.root("file-server/cache"))

    /** mapsquare -> outcome for every loc file of the cache at [path]. The stored bytes are read raw: never decoded by displee. */
    private fun scan(path: String): Map<Int, Outcome> {
        val library = CacheLibrary(path)
        try {
            val index = library.index(5)
            val byName = index.archives().associate { it.hashName to it.id }
            val out = sortedMapOf<Int, Outcome>()
            for (x in 0 until 256) for (z in 0 until 256) {
                val group = byName["l${x}_$z".hashCode()] ?: continue
                val stored = index.readArchiveSector(group)?.data ?: continue
                val square = (x shl 8) or z
                out[square] = ClientMapLocDecode.classify(stored, keys[square] ?: IntArray(4))
            }
            return out
        } finally {
            library.close()
        }
    }

    private fun describe(square: Int) = "$square l${square shr 8}_${square and 0xFF}"

    @Test
    fun `every loc file the client loads decodes with the key the server sends`() {
        val results = caches.associateWith { scan(it) }
        val failures = mutableListOf<String>()
        results.forEach { (path, outcomes) ->
            val counts = outcomes.values.groupingBy { it }.eachCount()
            println("N10-LOC-DECODE $path squares=${outcomes.size} $counts")
            assertTrue(outcomes.size > 2000, "$path: only ${outcomes.size} loc files read")
            val crashes = outcomes.filterValues { it.crashesClient }
            if (crashes.isNotEmpty()) {
                crashes.forEach { (square, outcome) -> println("N10-LOC-CRASH $path ${describe(square)} $outcome key=${square in keys}") }
                failures += "$path: ${crashes.size} loc files crash the client (" +
                    crashes.entries.groupingBy { it.value }.eachCount() + "), first: " +
                    crashes.entries.take(12).joinToString { "${describe(it.key)} ${it.value}" }
            }
        }
        val all = results.values.toList()
        val first = all.first()
        val second = all.last()
        assertEquals(first, second, "both caches must give the client the same loc files")
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `the Pest Control square south of the island from LC01 decodes`() {
        // 10791 = l42_39 (x 2688..2751, z 2496..2559): inside the map of every LC01 crash (bases 2608,2544 / 2656,2552 /
        // 2592,2544) and of the repro `tele 2700 2560 0` (base 2648,2512).
        caches.forEach { path ->
            val outcome = scan(path)[10791]
            assertTrue(outcome == Outcome.DECODES || outcome == Outcome.PLAIN, "$path: l42_39 gives $outcome")
        }
    }
}
