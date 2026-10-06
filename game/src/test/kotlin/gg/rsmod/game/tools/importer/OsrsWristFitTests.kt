package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-10-02 ("the osrs armour still invisible at the hands"): every imported OSRS glove / gauntlet / vambrace cuff lay
 * inside the 667 skin forearm and was covered by it. Guard over the whole set in the game cache: each hands-slot worn mesh of
 * each imported OSRS item clears the skin forearm of its gender ([OsrsWristFit]), and the repair tool has nothing left to do.
 */
class OsrsWristFitTests {
    @Test
    fun `every imported OSRS hands mesh clears the 667 skin forearm`() {
        val library = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        try {
            val skins = WornForearm.pieces(library).associate { it.female to Rev667ModelDecoder.decode(it.bytes, geometryOnly = true) }
            val targets = OsrsWristTool.targets(reader, ImportBatchOrchestrator.readExistingMapping(File(OsrsItemImportTool.ASSET_MAP)))
            assertTrue(targets.size >= 32, "only ${targets.size} hands meshes found")
            val covered = mutableListOf<String>()
            targets.forEach { t ->
                val glove = Rev667ModelDecoder.decode(library.data(7, t.local, 0)!!, geometryOnly = true)
                val skin = skins.getValue(t.female)
                OsrsWristFit.sides(skin).forEach { side ->
                    val left = OsrsWristFit.requiredScale(glove, skin, side)
                    if (left >= 1.05) covered += "model ${t.local} ${t.item} ${if (t.female) "female" else "male"} label ${side.label} still needs x${"%.2f".format(left)}"
                }
            }
            assertTrue(covered.isEmpty(), "OSRS cuffs still inside the 667 forearm: $covered")
            // Owner 2026-10-06 (invisible hands): the hand (labels 27/28) hangs below the end of the 667 forearm of its gender, not
            // inside it (the pre-2026-10-06 conversion put the male hand's centre exactly at the forearm's end).
            val inside = mutableListOf<String>()
            targets.forEach { t ->
                val glove = Rev667ModelDecoder.decode(library.data(7, t.local, 0)!!, geometryOnly = true)
                val skin = skins.getValue(t.female)
                val labels = glove.vertexLabel ?: return@forEach
                val forearmEnd = OsrsWristFit.sides(skin).maxOf { it.bottom }
                for (hand in listOf(27, 28)) {
                    val ys = (0 until glove.vertexCount).filter { labels[it] == hand }.map { glove.vertexY[it] }
                    if (ys.isNotEmpty() && ys.average() < forearmEnd + 10) {
                        inside += "model ${t.local} ${t.item} ${if (t.female) "female" else "male"} label $hand centre ${ys.average().toInt()}, forearm ends $forearmEnd"
                    }
                }
            }
            assertTrue(inside.isEmpty(), "OSRS hands inside the 667 forearm (run OsrsWristTool --apply): $inside")
        } finally {
            reader.close()
            library.close()
        }
    }

    @Test
    fun `a cuff inside a slanted skin forearm is sheared onto it and widened until it clears`() {
        // Skin: a square prism around x=0 whose centre moves from z=20 (top, y=-500) to z=0 (bottom, y=-420), radius 20.
        val skin = prism(centreTop = 20, centreBottom = 0, half = 20, label = 19)
        // Cuff: a vertical square prism, half size 10, centred at z=10, inside the skin's height - inside the skin, not slanted.
        val cuff = prism(centreTop = 10, centreBottom = 10, half = 10, label = 19, top = -490, bottom = -430)
        val side = OsrsWristFit.sides(skin).single()
        val applied = OsrsWristFit.fit(cuff, skin)
        assertTrue(applied.getValue(19) > 1.5)
        assertTrue(OsrsWristFit.requiredScale(cuff, skin, side) < 1.05, "after the fit the cuff clears the skin")
    }

    private fun prism(
        centreTop: Int,
        centreBottom: Int,
        half: Int,
        label: Int,
        top: Int = -500,
        bottom: Int = -420,
    ): ModelData {
        val ys = intArrayOf(top, bottom)
        val cz = intArrayOf(centreTop, centreBottom)
        val corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1)
        val m = ModelData(8, 8, 0)
        m.version = OsrsBodyFrame.FRAME_VERSION
        m.vertexLabel = IntArray(8) { label }
        for (level in 0..1) {
            corners.forEachIndexed { i, (sx, sz) ->
                val v = level * 4 + i
                m.vertexX[v] = sx * half
                m.vertexY[v] = ys[level]
                m.vertexZ[v] = cz[level] + sz * half
            }
        }
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            m.faceA[i * 2] = i; m.faceB[i * 2] = j; m.faceC[i * 2] = 4 + i
            m.faceA[i * 2 + 1] = j; m.faceB[i * 2 + 1] = 4 + j; m.faceC[i * 2 + 1] = 4 + i
        }
        return m
    }
}
