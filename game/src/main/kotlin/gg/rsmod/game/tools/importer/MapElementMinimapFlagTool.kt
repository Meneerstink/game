package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * N618 17-11 (ToA-ingang: raid-icoon niet op de minimap). Client-debug (Minimap.drawMapElement) bewees: de minimap VINDT loc 65762 en het
 * statische element 1149, maar tekent niets omdat MapElementType 1149 'aBoolean218' (opcode 7 bit 0x2 = op de minimap) mist.
 * OsrsMapAreaIconTool (1149, raid-icoon CoX/ToA) en DeathsOfficeMapImportTool (1108) schreven alleen op1 (sprite). Een bewezen werkend
 * geimporteerd minimap-icoon (612, OSRS-sprite 1504) heeft '07 03' (bit 0x1 enabled + bit 0x2 minimap). Deze tool voegt precies
 * '07 03' toe voor de afsluitende 0 als het element nog GEEN opcode 7 heeft; alleen elementen van de vorm [01 hi lo 00].
 * Usage: MapElementMinimapFlagTool plan|apply <elementId> [...]
 */
object MapElementMinimapFlagTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <elementId>..." }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val library = CacheLibrary(paths[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            for (id in args.drop(1).map { it.toInt() }) {
                val cur = requireNotNull(library.data(2, 36, id)) { "mapelement $id missing" }
                if (cur.size == 6 && cur[3] == 7.toByte()) { println("SAME mapelement $id already ${cur.joinToString("") { "%02x".format(it) }}"); continue }
                check(cur.size == 4 && cur[0] == 1.toByte() && cur[3] == 0.toByte()) {
                    "mapelement $id has unexpected form ${cur.joinToString("") { "%02x".format(it) }}, refusing"
                }
                val next = byteArrayOf(cur[0], cur[1], cur[2], 7, 3, 0)
                println("ELEMENT $id OUD ${cur.joinToString("") { "%02x".format(it) }} -> NIEUW ${next.joinToString("") { "%02x".format(it) }}")
                mutations += CacheMutation(2, 36, id, next, "mapelement $id: op7 flags 3 (enabled + minimap)", CacheItemProbeTool.sha1(cur))
            }
        } finally { library.close() }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} elements=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "verify failed ${tx.id}" }
        println("MAPELEMENT_FLAG transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
