package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.ObjectDef
import io.netty.buffer.Unpooled

/**
 * N231 (owner 2026-10-23: "Summoning obelisk in de GE heeft geen minimap/worldmap-icoon; moet er komen en meebewegen als de
 * eigenaar hem verplaatst").
 *
 * The GE obelisk is loc [OBELISK] (a world-edit spawn the owner moves in developer mode). Its LocType gets the 667 Summoning
 * obelisk map element [SUMMONING_ELEMENT] - the blue star (sprite 1289) that marks the Taverley obelisk (marker loc 2739 beside
 * it, map square 45_53) - as opcode 107 `mapelement`. The minimap draws the element of every loc in the scene where the loc
 * stands; the world map gets the spawned loc's post from the server (NpcMapIcons.locPayload -> the client's NpcWorldMapIcons),
 * so both follow every move. Loc 50205 stands nowhere else.
 *
 * Usage: `./gradlew :game:runObeliskMapIconTool --args="plan|apply"`.
 */
object ObeliskMapIconTool {
    const val LOC_INDEX = 16
    const val OPCODE_MAP_ELEMENT = 107

    /** `Objs.SUMMONING_OBELISK_50205`, the Grand Exchange obelisk. */
    const val OBELISK = 50205

    /** MapElementType 561: sprite 1289, the Summoning obelisk icon (op 7 flags 3: minimap and world map). */
    const val SUMMONING_ELEMENT = 561

    fun patched(
        current: ByteArray,
        element: Int,
    ): ByteArray = current.copyOfRange(0, current.size - 1) + byteArrayOf(OPCODE_MAP_ELEMENT.toByte(), (element ushr 8).toByte(), (element and 0xFF).toByte(), 0)

    /** The loc's map element as the server's full LocType decoder reads it (a tail check misreads payload bytes as opcode 107). */
    fun mapElement(bytes: ByteArray): Int = ObjectDef(OBELISK).also { it.decode(Unpooled.wrappedBuffer(bytes)) }.mapElement

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val group = OBELISK ushr 8
        val file = OBELISK and 0xFF
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations =
            try {
                val current = library.data(LOC_INDEX, group, file) ?: error("loc $OBELISK missing")
                check(current.last() == 0.toByte()) { "loc $OBELISK does not end with the opcode-0 terminator" }
                val before = mapElement(current)
                if (before == SUMMONING_ELEMENT) {
                    println("NOTHING_TO_DO")
                    return
                }
                check(before == -1) { "loc $OBELISK already carries mapelement $before; refusing" }
                val updated = patched(current, SUMMONING_ELEMENT)
                check(mapElement(updated) == SUMMONING_ELEMENT)
                listOf(CacheMutation(LOC_INDEX, group, file, updated, "N231 loc $OBELISK mapelement=$SUMMONING_ELEMENT", CacheItemProbeTool.sha1(current)))
            } finally {
                library.close()
            }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
            println("DRY_RUN transaction=${transaction.id}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
