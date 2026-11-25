package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * TORTURE-AMULET-ONZICHTBAAR (owner 2026-11-25 'ja, tool + fix'): OSRS neck items are modelled against the thinner OSRS chest. On
 * the 667 body the chest stands further forward, so a pendant that reaches only z -41..-57 lies INSIDE the chest and the amulet is
 * invisible (Amulet of torture -54, Glory(6) -41) or half visible (Necklace of anguish -49); every neck model reaching z <= -60
 * shows (Amulet of rancour -61, Salve amulet(ei) -61, the 667 Amulet of glory -80). Front = negative z.
 *
 * This tool stretches only the FRONT half of a worn neck model forward (z' = z * target / zMin for z < 0), so the pendant reaches
 * [target] and the chain behind the neck (z >= 0) does not move. Codec read-back must reproduce the stored bytes first.
 * Usage: NeckDepthTool plan|apply <targetFrontZ, e.g. -64> <modelId> [modelId ...]
 */
object NeckDepthTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <targetFrontZ> <modelId>..." }
        val target = args[1].toInt().also { require(it in -120..-50) { "target $it outside -120..-50" } }
        val ids = args.drop(2).map { it.toInt() }.distinct()
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val library = CacheLibrary(paths[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            for (id in ids) {
                val current = requireNotNull(ModelArchive.read(library, id)) { "model $id missing" }
                val model = Rev667ModelDecoder.decode(current)
                check(Rev667ModelEncoder.encode(model).contentEquals(current)) { "model $id: codec does not round-trip, refusing" }
                val zMin = (0 until model.vertexCount).minOf { model.vertexZ[it] }
                if (zMin <= target) {
                    println("SKIP model $id: front z $zMin already reaches $target")
                    continue
                }
                require(zMin < 0) { "model $id: no front half (z min $zMin)" }
                val factor = target.toDouble() / zMin
                var moved = 0
                for (v in 0 until model.vertexCount) {
                    val z = model.vertexZ[v]
                    if (z < 0) {
                        model.vertexZ[v] = Math.round(z * factor).toInt()
                        moved++
                    }
                }
                println("STRETCH model $id: front z $zMin -> ${(0 until model.vertexCount).minOf { model.vertexZ[it] }} (x${"%.2f".format(factor)}, $moved of ${model.vertexCount} vertices)")
                mutations += CacheMutation(7, id, 0, Rev667ModelEncoder.encode(model), "model $id: neck pendant front $zMin -> $target (667 chest)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
        } finally { library.close() }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} models=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "verify failed ${tx.id}" }
        println("NECK_DEPTH transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
