package gg.rsmod.game.tools.importer

import com.google.gson.JsonParser
import gg.rsmod.game.fs.def.ObjectDef
import io.netty.buffer.Unpooled
import java.nio.file.Paths
import java.util.Base64
import kotlin.test.*

class ClueMimicArenaImportTests {
    @Test fun `arena import preserves occupied native source and relocates all source planes without filtering`() {
        val batch=OsrsRegionImportTool.BATCHES.getValue("clue-mimic-arena")
        assertEquals(listOf(10819),batch.squares);assertEquals(4160,batch.target(10819))
        assertTrue(batch.replaceSquares.isEmpty());assertTrue(batch.excludeOsrsLocs.isEmpty())
        assertEquals(0,batch.planeShift);assertFalse(batch.plane0Only)
        assertTrue(batch.sourceCache.replace('\\','/').endsWith("import-source/openrs2-2686/cache"))
        val fixture=generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .map { it.resolve("docs/CLUE/cache/mimic-arena-source.json") }.first { it.toFile().isFile }
        val root=JsonParser().parse(fixture.toFile().readText()).asJsonObject
        val raw=Base64.getDecoder().decode(root["locationsBase64"].asString)
        val locs=ModernRegion.decodeLocs(raw)
        assertEquals(250,locs.size);assertEquals(43,locs.map { it.id }.distinct().size)
        val keyhole=locs.single { it.id==34727 }
        assertEquals(listOf(31,23,1,10,2),listOf(keyhole.localX,keyhole.localZ,keyhole.plane,keyhole.type,keyhole.rotation))
        val tiles=ModernRegion.decodeTiles(Base64.getDecoder().decode(root["terrainBase64"].asString))
        assertEquals(4,tiles.size)
    }
    @Test fun `source ambient radius follows the real opcode and native sound is preserved in both decoders`() {
        val source=ModernObjectDef.decode(16416,byteArrayOf(78,8,76,6,0,0)) // source2124, radius6, retain0.
        assertEquals(2124,source.ambientSoundId);assertEquals(6,source.ambientSoundRadius);assertEquals(0,source.ambientSoundRetain)
        val synth=byteArrayOf(1,2,3)
        val bytes=ClueMimicArenaAssets.preserveAmbient(byteArrayOf(0),source,synth,listOf(synth.copyOf(),synth.copyOf()))
        val buffer=Unpooled.wrappedBuffer(bytes);val def=ObjectDef(999).also { it.decode(buffer) }
        assertEquals(0,buffer.readableBytes());assertEquals(2124,def.ambientSound);assertEquals(6,def.ambientSoundRange)
        assertTrue(78 in Rev667LocType.decode(999,bytes).opcodes)
        assertFailsWith<IllegalArgumentException> { ClueMimicArenaAssets.preserveAmbient(byteArrayOf(0),source,synth,listOf(synth,byteArrayOf(4))) }
        source.ambientSoundRetain=1
        assertFailsWith<IllegalArgumentException> { ClueMimicArenaAssets.preserveAmbient(byteArrayOf(0),source,synth,listOf(synth,synth)) }
    }
}
