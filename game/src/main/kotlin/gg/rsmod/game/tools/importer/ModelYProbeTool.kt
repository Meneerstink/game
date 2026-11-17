package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * GRAFISCH 17-11 (CoX Mystics: plat paars stekelvlak rond de kuipen, OSRS-video niet): read-only - per model de Y-verdeling van de
 * vertices en per (kleur, textuur, alpha) hoeveel vlakken boven / plat / (deels) onder de vloer (y>0 = onder de grond).
 * Usage: ModelYProbeTool <osrsModelId | ons:localModelId> [...]
 */
object ModelYProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val osrs = ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache")))
        val ours = CacheLibrary(RspsPaths.data("cache").toString())
        try {
            for (arg in args) {
                val local = arg.startsWith("ons:")
                val id = arg.removePrefix("ons:").toInt()
                val data = (if (local) (if (id > 0xFFFF) ours.data(37, id - 65536) else ours.data(7, id)) else osrs.file(7, id, 0)) ?: run { println("model $arg: ontbreekt"); null } ?: continue
                val m = ModernModelDecoder.decode(data, flattenTextures = true)
                val ys = m.vertexY.take(m.vertexCount)
                println("model $arg: vertices=${m.vertexCount} faces=${m.faceCount} y min=${ys.minOrNull()} max=${ys.maxOrNull()} x ${m.vertexX.take(m.vertexCount).minOrNull()}..${m.vertexX.take(m.vertexCount).maxOrNull()} z ${m.vertexZ.take(m.vertexCount).minOrNull()}..${m.vertexZ.take(m.vertexCount).maxOrNull()} version=${m.version}")
                val per = sortedMapOf<String, IntArray>()
                for (f in 0 until m.faceCount) {
                    val y = maxOf(m.vertexY[m.faceA[f]], m.vertexY[m.faceB[f]], m.vertexY[m.faceC[f]])
                    val minY = minOf(m.vertexY[m.faceA[f]], m.vertexY[m.faceB[f]], m.vertexY[m.faceC[f]])
                    val bucket = when { y <= 0 && minY < 0 -> 0; minY == 0 && y == 0 -> 1; else -> 2 }
                    val key = "prio ${m.facePriority?.get(f)?.toInt() ?: ("g" + m.globalPriority)} hsl ${m.faceColour[f].toInt() and 0xFFFF} tex ${m.faceTexture?.get(f) ?: -1} alpha ${m.faceAlpha?.get(f)?.toInt()?.and(0xFF) ?: 0}"
                    per.getOrPut(key) { IntArray(3) }[bucket]++
                }
                for ((k, n) in per) println("  $k: boven ${n[0]} | plat op vloer ${n[1]} | (deels) onder ${n[2]}")
                if (System.getenv("FACES") != null) for (f in 0 until m.faceCount) {
                    val vs = listOf(m.faceA[f], m.faceB[f], m.faceC[f])
                    println("    face $f prio ${m.facePriority?.get(f)} hsl ${m.faceColour[f].toInt() and 0xFFFF} x ${vs.map { m.vertexX[it] }} z ${vs.map { m.vertexZ[it] }}")
                }
            }
        } finally { osrs.close(); ours.close() }
    }
}
