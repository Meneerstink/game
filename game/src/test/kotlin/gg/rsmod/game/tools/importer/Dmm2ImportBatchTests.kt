package gg.rsmod.game.tools.importer

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Dmm2ImportBatchTests {
    @Test fun itemBatchPreservesProvenAllocationOrderAndNotes() {
        val expected = listOf(13307, 13317, 13318, 13319, 13660, 22331, 22333, 22335, 26149,
            28531, 28534, 28537, 28540, 28543, 28545, 28547, 28549, 28561, 28567, 29599, 29602,
            29605, 29607, 29609, 29611, 29613, 29615, 29617, 29619, 29622, 29625, 29628,
            33012, 33015, 33018, 33021, 33023, 33025, 33027, 33029, 33031, 33033, 33035, 33036,
            33041, 33050, 33063, 33122, 34422, 34425, 13658)
        val specs = OsrsItemImportTool.BATCHES.getValue("dmm2-items")
        assertEquals(expected, specs.map { it.upstreamId })
        assertEquals(51, specs.map { it.upstreamId }.distinct().size)
        assertTrue(specs.all { it.noted })
        // Existing trinkets and source placeholders must not be reallocated as usable items.
        assertFalse(specs.any { it.upstreamId in setOf(28563, 28565, 33044, 33047, 34607) })
    }

    @Test fun unsupportedWispIsOnlyInSkeletalRoute() {
        assertEquals(listOf(6516, 6485, 8391, 8390, 15569, 15570), OsrsNpcImportTool.BATCHES.getValue("dmm2-npcs"))
        assertEquals(listOf(15571 to emptyList<Int>()), OsrsPetImportTool.SKELETAL_NPC_BATCHES.getValue("dmm2-wisp"))
    }

    @Test fun fxNamespacesAndBlockedFontStaySeparate() {
        assertEquals(listOf(11444, 11810, 11811, 11812, 11935, 11936, 11937, 13701, 13702, 13703,
            13704, 13705, 13706, 13707, 13708, 13709, 13710, 13711, 13712, 13713, 13714, 14497),
            OsrsFxImportTool.SEQ_BATCHES.getValue("dmm2-seqs"))
        assertEquals(listOf(3030, 3031, 3032, 3033, 3565, 3566, 3567, 3568, 3569, 3570, 3571, 4020),
            OsrsFxImportTool.BATCHES.getValue("dmm2-spots"))
        assertFalse(OsrsFxImportTool.BATCHES.containsKey("dmm2-seqs"))
        assertFalse(OsrsFxImportTool.SEQ_BATCHES.containsKey("dmm2-spots"))
        assertEquals(listOf(54114, 54115, 54116), OsrsLocImportTool.BATCHES.getValue("dmm2-locs"))
        assertEquals(listOf(232), OsrsInterfacePortTool.DMM2)
    }
}
