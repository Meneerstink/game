package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream

/**
 * Player-owned house (owner request 2026-09-19, `content/areas/poh`): cleans up the menus of the four rev-667 POH
 * furniture locs the house places, which in the cache still carry their Construction build-mode options.
 *
 *  * 13480 ornamental fountain -> the house rejuvenation pool (owner choice: 667 fountain instead of an OSRS
 *    import): drop "Remove" (op 34), add name "Rejuvenation pool" (op 2) and "Drink" (op 30), as the OSRS
 *    ornate rejuvenation pool shows.
 *  * 13405 house portal: drop "Lock" (op 31) and "Remove" (op 34); "Enter" stays.
 *  * 13199 gilded altar: drop "Remove" (op 34); "Pray" stays.
 *  * 13523 mounted amulet of glory: drop "Remove" (op 34); "Rub" stays.
 *  * 13594/13595 opulent rug and 13255 stained-glass window (chapel finish): drop "Remove" (op 34).
 *
 * The client (LocType.decode / MiniMenu) only hides an option that is null, so the option's own bytes
 * `[opcode, text, 0]` are cut out of the definition: the run must occur exactly once, and the full re-decode
 * ([Rev667LocType]) must show only the intended option/name change with identical models, size and animation.
 * These locs are only used by Construction rooms, which this server does not otherwise place.
 *
 * Usage: `./gradlew :game:runPohObjectOptionTool --args="plan|apply"` (A7 CacheTransaction on both caches).
 */
object PohObjectOptionTool {
    const val LOC_INDEX = 16

    class Edit(val id: Int, val remove: List<Pair<Int, String>>, val append: List<Pair<Int, String>>, val expectName: String?, val expectOptions: List<String?>)

    val EDITS =
        listOf(
            Edit(13480, listOf(34 to "Remove"), listOf(2 to "Rejuvenation pool", 30 to "Drink"), "Rejuvenation pool", listOf("Drink", null, null, null, null)),
            Edit(13405, listOf(31 to "Lock", 34 to "Remove"), emptyList(), null, listOf("Enter", null, null, null, null)),
            Edit(13199, listOf(34 to "Remove"), emptyList(), null, listOf("Pray", null, null, null, null)),
            Edit(13523, listOf(34 to "Remove"), emptyList(), null, listOf("Rub", null, null, null, null)),
            // Chapel finish: opulent rug corner/end and the basic-wood stained-glass window.
            Edit(13594, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13595, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13255, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            // Chapel statue and icon, portal chamber marble Varrock/Falador/Camelot portals.
            Edit(13282, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13175, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13629, listOf(34 to "Remove"), emptyList(), null, listOf("Enter", null, null, null, null)),
            Edit(13631, listOf(34 to "Remove"), emptyList(), null, listOf("Enter", null, null, null, null)),
            Edit(13632, listOf(34 to "Remove"), emptyList(), null, listOf("Enter", null, null, null, null)),
            // 13596 is the opulent rug's CENTRE piece, which the Grand Exchange gambling pit lays between its edge
            // and corner pieces; the corner and end were already cleaned up above.
            Edit(13596, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            // 2026-09-21 Superior Garden: its hedge and gazebo.
            Edit(13476, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13477, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            // 2026-09-21 Superior Garden room: its trees and ferns.
            Edit(13417, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13424, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13427, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13433, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            // 2026-09-21 costume room: the six containers keep their Open/Search and lose Construction's Remove.
            Edit(18771, listOf(34 to "Remove"), emptyList(), null, listOf("Search", null, null, null, null)),
            Edit(18782, listOf(34 to "Remove"), emptyList(), null, listOf("Open", null, null, null, null)),
            Edit(18796, listOf(34 to "Remove"), emptyList(), null, listOf("Open", null, null, null, null)),
            Edit(18802, listOf(34 to "Remove"), emptyList(), null, listOf("Open", null, null, null, null)),
            Edit(18776, listOf(34 to "Remove"), emptyList(), null, listOf("Open", null, null, null, null)),
            Edit(18808, listOf(34 to "Remove"), emptyList(), null, listOf("Open", null, null, null, null)),
            // 2026-09-21 study.
            Edit(13648, listOf(34 to "Remove"), emptyList(), null, listOf("Study", null, null, null, null)),
            Edit(13652, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13661, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13664, listOf(34 to "Remove"), emptyList(), null, listOf("Study", null, null, null, null)),
            Edit(13658, listOf(34 to "Remove"), emptyList(), null, listOf("Observe", null, null, null, null)),
            Edit(13599, listOf(34 to "Remove"), emptyList(), null, listOf("Search", null, null, null, null)),
            // 2026-09-21 house: lit marble incense burner, armour repair stand, jewellery box, teleport nexus and the
            // stained-glass window of each of the five other house styles.
            Edit(13213, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13715, listOf(34 to "Remove"), listOf(30 to "Repair"), null, listOf("Repair", null, null, null, null)),
            Edit(40173, emptyList(), listOf(30 to "Teleport"), null, listOf("Teleport", null, null, null, null)),
            Edit(13639, listOf(34 to "Remove"), emptyList(), null, listOf("Direct-portal", "Scry", null, null, null)),
            Edit(13228, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13237, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13246, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13219, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
            Edit(13264, listOf(34 to "Remove"), emptyList(), null, listOf(null, null, null, null, null)),
        )

    fun isApplied(edit: Edit, def: Rev667LocType): Boolean =
        def.options.toList() == edit.expectOptions && (edit.expectName == null || def.name == edit.expectName)

    private fun run(opcode: Int, text: String): ByteArray = byteArrayOf(opcode.toByte()) + text.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    fun mutate(edit: Edit, current: ByteArray): ByteArray {
        check(current.last() == 0.toByte()) { "loc ${edit.id} does not end with the opcode-0 terminator" }
        var data = current
        edit.remove.forEach { (opcode, text) ->
            val pattern = run(opcode, text)
            val at = indexOf(data, pattern)
            check(at >= 0) { "loc ${edit.id}: option run op$opcode '$text' not found" }
            check(indexOf(data, pattern, at + 1) < 0) { "loc ${edit.id}: option run op$opcode '$text' is not unique; refusing" }
            data = data.copyOfRange(0, at) + data.copyOfRange(at + pattern.size, data.size)
        }
        val out = ByteArrayOutputStream()
        out.write(data, 0, data.size - 1)
        edit.append.forEach { (opcode, text) -> out.write(run(opcode, text)) }
        out.write(0)
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val mutations =
            try {
                EDITS.filter { edit ->
                    val current = library.data(LOC_INDEX, edit.id ushr 8, edit.id and 0xFF) ?: error("loc ${edit.id} absent")
                    val applied = isApplied(edit, Rev667LocType.decode(edit.id, current))
                    if (applied) println("ALREADY_APPLIED loc=${edit.id}")
                    !applied
                }.map { edit ->
                    val group = edit.id ushr 8
                    val file = edit.id and 0xFF
                    val current = library.data(LOC_INDEX, group, file) ?: error("loc ${edit.id} absent")
                    val before = Rev667LocType.decode(edit.id, current)
                    println("CURRENT loc=${edit.id} bytes=${current.size} sha1=${CacheItemProbeTool.sha1(current)} name='${before.name}' options=${before.options.toList()}")
                    val updated = mutate(edit, current)
                    val after = Rev667LocType.decode(edit.id, updated)
                    check(after.options.toList() == edit.expectOptions) { "loc ${edit.id}: re-decoded options ${after.options.toList()}" }
                    check(after.name == (edit.expectName ?: before.name)) { "loc ${edit.id}: re-decoded name '${after.name}'" }
                    check(after.allModels == before.allModels && after.sizeX == before.sizeX && after.sizeZ == before.sizeZ && after.animation == before.animation) {
                        "loc ${edit.id}: non-option fields changed"
                    }
                    println("INTENDED loc=${edit.id} bytes=${updated.size} sha1=${CacheItemProbeTool.sha1(updated)} name='${after.name}' options=${after.options.toList()}")
                    CacheMutation(LOC_INDEX, group, file, updated, "POH loc ${edit.id} menu cleanup", CacheItemProbeTool.sha1(current))
                }
            } finally {
                library.close()
            }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
