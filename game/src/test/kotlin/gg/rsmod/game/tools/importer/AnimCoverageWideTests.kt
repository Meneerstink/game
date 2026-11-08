package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.displee.compress.CompressionType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnimCoverageWideTests {
    @get:Rule val scratch = TemporaryFolder()

    @Test fun readsRealOverflowMeshLabelsForWideNpcAndMarkedSpotWithoutClaimingTheModelIsMissing() {
        val dir = scratch.newFolder("cache")
        File(dir, "main_file_cache.dat2").createNewFile()
        File(dir, "main_file_cache.idx255").createNewFile()
        val cache = CacheLibrary.create(dir.absolutePath)
        try {
            while (!cache.exists(37)) cache.createIndex(CompressionType.GZIP, 6, 0, false, false)
            val mesh = ModelData(3, 1, 0).apply {
                vertexX[1] = 32; vertexZ[2] = 32
                faceA[0] = 0; faceB[0] = 1; faceC[0] = 2; faceColour[0] = 127
                vertexLabel = intArrayOf(2, 3, 4); faceLabel = intArrayOf(5)
            }
            cache.put(37, 4, 0, Rev667ModelEncoder.encode(mesh))
            cache.put(18, 1, 0, byteArrayOf(241.toByte(), 1, 0, 1, 0, 4, 0))
            cache.put(18, 1, 1, byteArrayOf(1, 1, 1, 0xf4.toByte(), 0))
            cache.put(21, 3381 ushr 8, 3381 and 255, byteArrayOf(241.toByte(), 0, 1, 0, 4, 2, 0x43, 0xdc.toByte(), 242.toByte(), 0))
            val models = AnimCoverage.npcModels(cache, 128)
            assertEquals(listOf(65540), models)
            assertEquals(listOf(500), AnimCoverage.npcModels(cache, 129))
            assertEquals(65540 to 17372, AnimCoverage.spotanim(cache, 3381))
            val labels = AnimCoverage.modelLabels(cache, models + 65542)
            assertEquals(setOf(2, 3, 4), labels.vertex)
            assertEquals(setOf(5), labels.face)
            assertEquals(listOf(65542), labels.missing)
        } finally { cache.close() }
    }
}
