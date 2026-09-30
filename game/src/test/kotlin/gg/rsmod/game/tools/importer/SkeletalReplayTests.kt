package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ROOT_CAUSES #103 guard: baked skeletal animations replayed the way the rev-667 client does it, independent of [SkeletalBakeTool]'s own
 * replay - the model from the production cache upscaled x4 (`Mesh.upscale`, version < 13), then every frame group applied like
 * `AnimFrame` / `JavaModel.I` (implicit origin, translate, the Z-X-Y rotation of a 16384-step table, scale about the origin) - against the
 * OSRS skinning x4. File-unit translations (the old bug) put these parts hundreds of units off.
 */
class SkeletalReplayTests {
    private class Buf(val b: ByteArray, var p: Int = 0) {
        fun u8() = b[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun smarts() = if ((b[p].toInt() and 0xFF) < 128) u8() - 64 else u16() - 49152
    }

    /** Mean and max distance (client units) over every vertex of every frame of [localSeq]. */
    private fun replay(
        lib: CacheLibrary,
        reader: ModernCacheReader,
        models: List<Pair<Int, Int>>,
        localSeq: Int,
        osrsSeq: Int,
    ): Pair<Double, Double> {
        val m667 = models.map { Rev667ModelDecoder.decode(lib.data(7, it.first, 0)!!) }
        val raws = models.map { ModernModelDecoder.decode(reader.file(7, it.second, 0)!!, flattenTextures = true) }
        val ss = SkeletalBakeTool.decodeSkeletalSeq(reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, osrsSeq)!!)!!
        val clip = SkeletalBakeTool.decodeClip(reader.file(SkeletalBakeTool.INDEX_ANIMAYA, ss.animaya ushr 16, ss.animaya and 0xFFFF)!!)
        val bones = SkeletalBakeTool.skeletalBase(reader.file(SkeletalBakeTool.INDEX_SKELETONS, clip.baseId, 0)!!)!!
        val start = if (ss.start >= 0 && ss.end > ss.start) ss.start else 0
        val frames = OsrsFxImportTool.decode667SeqFrames(lib.data(OsrsFxImportTool.INDEX_SEQ, localSeq ushr 7, localSeq and 0x7F)!!)
        var max = 0.0
        var sum = 0.0
        var n = 0
        fun s(i: Int) = sin(i * 2 * Math.PI / 16384)
        fun c(i: Int) = cos(i * 2 * Math.PI / 16384)
        frames.forEachIndexed { fi, frame ->
            val f = Buf(lib.data(0, frame ushr 16, frame and 0xFFFF)!!, 1)
            val baseBytes = Buf(lib.data(1, f.u16(), 0)!!)
            val count = f.u8()
            val flags = IntArray(count) { f.u8() }
            val bn = baseBytes.u8()
            val types = IntArray(bn) { baseBytes.u8() }
            baseBytes.p += bn * 3
            val counts = IntArray(bn) { baseBytes.u8() }
            val labels = Array(bn) { g -> IntArray(counts[g]) { baseBytes.u8() }.toSet() }
            val pos = ArrayList<DoubleArray>()
            val lab = ArrayList<Int>()
            m667.forEach { m ->
                val up = if (m.version < 13) 4.0 else 1.0
                for (v in 0 until m.vertexCount) {
                    pos += doubleArrayOf(m.vertexX[v] * up, m.vertexY[v] * up, m.vertexZ[v] * up)
                    lab += m.vertexLabel!![v]
                }
            }
            fun origin(g: Int, a: Int, b: Int, cc: Int): DoubleArray {
                val members = pos.indices.filter { lab[it] in labels[g] }
                if (members.isEmpty()) return doubleArrayOf(a.toDouble(), b.toDouble(), cc.toDouble())
                return DoubleArray(3) { k -> members.sumOf { pos[it][k] } / members.size + intArrayOf(a, b, cc)[k] }
            }
            var o = doubleArrayOf(0.0, 0.0, 0.0)
            var lastOrigin = -1
            var appliedOrigin = -1
            for (g in 0 until count) {
                val t = types[g]
                if (t == 0) lastOrigin = g
                if (flags[g] == 0) continue
                val d = if (t == 3) 128 else 0
                val a = if (flags[g] and 1 != 0) f.smarts() else d
                val b = if (flags[g] and 2 != 0) f.smarts() else d
                val cc = if (flags[g] and 4 != 0) f.smarts() else d
                if (t == 0) {
                    o = origin(g, a, b, cc)
                    appliedOrigin = g
                    continue
                }
                if (lastOrigin > appliedOrigin) {
                    o = origin(lastOrigin, 0, 0, 0)
                    appliedOrigin = lastOrigin
                }
                pos.indices.filter { lab[it] in labels[g] }.forEach { v ->
                    val q = pos[v]
                    when (t) {
                        1 -> { q[0] = q[0] + a; q[1] = q[1] + b; q[2] = q[2] + cc }
                        3 -> for (k in 0..2) q[k] = (q[k] - o[k]) * intArrayOf(a, b, cc)[k] / 128.0 + o[k]
                        2 -> {
                            val rx = (a shl 2) and 0x3FFF
                            val ry = (b shl 2) and 0x3FFF
                            val rz = (cc shl 2) and 0x3FFF
                            var x = q[0] - o[0]
                            var y = q[1] - o[1]
                            var z = q[2] - o[2]
                            if (rz != 0) { val k = y * s(rz) + x * c(rz); y = y * c(rz) - x * s(rz); x = k }
                            if (rx != 0) { val k = y * c(rx) - z * s(rx); z = y * s(rx) + z * c(rx); y = k }
                            if (ry != 0) { val k = z * s(ry) + x * c(ry); z = z * c(ry) - x * s(ry); x = k }
                            q[0] = x + o[0]; q[1] = y + o[1]; q[2] = z + o[2]
                        }
                    }
                }
            }
            val mats = SkeletalBakeTool.finalMatrices(bones, clip, start + fi * SkeletalBakeTool.STEP)
            var vi = 0
            raws.forEach { r ->
                val sk = SkeletalBakeTool.skin(r, mats)
                for (v in 0 until r.vertexCount) {
                    val q = pos[vi++]
                    val e = sqrt((0..2).sumOf { k -> (q[k] - sk[v][k] * 4.0) * (q[k] - sk[v][k] * 4.0) })
                    max = maxOf(max, e)
                    sum += e
                    n++
                }
            }
        }
        return sum / n to max
    }

    @Test
    fun `baked breach and pet animations replay on the client within a few units of the OSRS skinning`() {
        val fx = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val lib = CacheLibrary(OsrsItemImportTool.TARGETS[0], false, null)
        try {
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                val cases =
                    listOf(
                        // Breach loc and its spawn spotanim (in place, tx-20260930-182901), Boss Spawn loc.
                        Triple(listOf(fx.getValue("osrs_loc_model:49487") to 49487), 15828, 10418),
                        Triple(listOf(fx.getValue("osrs_loc_model:49487") to 49487), 15830, 10417),
                        Triple(listOf(fx.getValue("osrs_loc_model:49488") to 49488), 15829, 10423),
                        // Tumeken's guardian (osrs npc 11812) stand, Vet'ion Jr. (5559) walk.
                        Triple(listOf(fx.getValue("pet_baked_model:${11812 * 16}") to 46337), fx.getValue("pet_baked_seq:${11812 * 16}"), 9655),
                        Triple(
                            listOf(fx.getValue("pet_baked_model:${5559 * 16}") to 47387, fx.getValue("pet_baked_model:${5559 * 16 + 1}") to 47384),
                            fx.getValue("pet_baked_seq:${5559 * 16 + 1}"),
                            9967,
                        ),
                    )
                cases.forEach { (models, local, osrs) ->
                    val (mean, max) = replay(lib, reader, models, local, osrs)
                    assertTrue(mean < 4.0 && max < 32.0, "seq $local (osrs $osrs): client replay mean $mean max $max units")
                }
            }
        } finally {
            lib.close()
        }
    }
}
