package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * GRAFISCH 17-11 (ToA Wardens-arena: rasterlijnen ONDERBROKEN, OSRS/Kaoz 2020 s = doorlopend rooster). The floor tile models
 * (OSRS 45432/45469/45471 = ons 16567/16980/17016) carry 32 black grid faces (priority 3/4, OSRS face bias 2) on the same plane
 * as the red base (priority 2). OSRS-GPU draws them on top via the face bias; this client depth-tests opaque faces, and the
 * N601 lift of 1-2 units (OsrsDecalLiftTool CLEARANCE 1) is not enough for its depth precision at normal camera distance ->
 * z-fighting = dashes. This tool lifts the faces of ONE priority a further [dy] units up (y -= dy), only when every vertex of
 * those faces is used by no face of another priority (the base never moves). Read-back check: re-encode must reproduce bytes.
 * Usage: ModelFaceLiftTool plan|apply <dy> <priority> <modelId> [modelId ...]
 */
object ModelFaceLiftTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 4 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply <dy> <priority> <modelId>..." }
        val dy = args[1].toInt().also { require(it in 1..16) }
        val prio = args[2].toInt() // -1 = every face (whole model)
        val ids = args.drop(3).map { it.toInt() }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val library = CacheLibrary(paths[0])
        val mutations = ArrayList<CacheMutation>()
        try {
            for (id in ids) {
                val current = requireNotNull(ModelArchive.read(library, id)) { "model $id missing" }
                val model = Rev667ModelDecoder.decode(current)
                check(Rev667ModelEncoder.encode(model).contentEquals(current)) { "model $id: codec does not round-trip, refusing" }
                val pr = model.facePriority ?: ByteArray(model.faceCount) { model.globalPriority }.also { require(prio == -1) { "model $id has no per-face priorities" } }
                val mine = HashSet<Int>(); val others = HashSet<Int>()
                for (f in 0 until model.faceCount) {
                    val set = if (prio == -1 || pr[f].toInt() == prio) mine else others
                    set += model.faceA[f]; set += model.faceB[f]; set += model.faceC[f]
                }
                check(mine.isNotEmpty()) { "model $id: no faces of priority $prio" }
                check(mine.none { it in others }) { "model $id: priority-$prio faces share vertices with other faces, refusing" }
                val before = mine.map { model.vertexY[it] }.distinct().sorted()
                val sign = System.getenv("NORMAL")?.toIntOrNull()
                if (sign == null) {
                    for (v in mine) model.vertexY[v] -= dy
                } else {
                    // N613: decal on a vertical wall - move the layer along its own average face normal (sign +1/-1), dy units.
                    var nx = 0.0; var ny = 0.0; var nz = 0.0
                    for (f in 0 until model.faceCount) {
                        if (prio != -1 && pr[f].toInt() != prio) continue
                        val a = model.faceA[f]; val b = model.faceB[f]; val c = model.faceC[f]
                        val ux = (model.vertexX[b] - model.vertexX[a]).toDouble(); val uy = (model.vertexY[b] - model.vertexY[a]).toDouble(); val uz = (model.vertexZ[b] - model.vertexZ[a]).toDouble()
                        val vx = (model.vertexX[c] - model.vertexX[a]).toDouble(); val vy = (model.vertexY[c] - model.vertexY[a]).toDouble(); val vz = (model.vertexZ[c] - model.vertexZ[a]).toDouble()
                        nx += uy * vz - uz * vy; ny += uz * vx - ux * vz; nz += ux * vy - uy * vx
                    }
                    val len = Math.sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0 } ?: error("model $id: no normal")
                    val mx = Math.round(sign * dy * nx / len).toInt(); val my = Math.round(sign * dy * ny / len).toInt(); val mz = Math.round(sign * dy * nz / len).toInt()
                    println("NORMAL model $id: avg normal (${"%.2f".format(nx / len)}, ${"%.2f".format(ny / len)}, ${"%.2f".format(nz / len)}) move ($mx,$my,$mz)")
                    for (v in mine) { model.vertexX[v] += mx; model.vertexY[v] += my; model.vertexZ[v] += mz }
                }
                val after = mine.map { model.vertexY[it] }.distinct().sorted()
                println("LIFT model $id: ${mine.size} vertices of priority $prio  y $before -> $after")
                mutations += CacheMutation(7, id, 0, Rev667ModelEncoder.encode(model), "model $id: priority $prio faces +$dy up (z-fight)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(current))
            }
        } finally { library.close() }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} models=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "verify failed ${tx.id}" }
        println("FACE_LIFT transaction=${result.transactionId} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
