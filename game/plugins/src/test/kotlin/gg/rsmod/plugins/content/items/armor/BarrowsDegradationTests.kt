package gg.rsmod.plugins.content.items.armor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whole-set checks over every Barrows/Akrisae piece: id table, stage arithmetic and repair pricing. */
class BarrowsDegradationTests {
    @Test
    fun everyPieceHasSixDistinctIdsAndNoTwoPiecesOverlap() {
        val seen = mutableMapOf<Int, BarrowsPiece>()
        assertEquals(28, BarrowsPiece.values.size)
        for (piece in BarrowsPiece.values) {
            assertEquals("${piece.name} id count", 6, piece.ids.toSet().size)
            assertTrue("${piece.name} degraded ids must follow the base id", piece.firstDegradedId > piece.baseId)
            piece.ids.forEach { id ->
                val clash = seen.put(id, piece)
                assertTrue("id $id of ${piece.name} also belongs to ${clash?.name}", clash == null)
            }
            assertEquals("${piece.name} lookup", piece, BarrowsPiece.forId(piece.baseId))
            assertEquals("${piece.name} lookup of broken id", piece, BarrowsPiece.forId(piece.idForStage(BarrowsPiece.BROKEN)))
        }
    }

    @Test
    fun stagesAdvanceFromPristineToBroken() {
        for (piece in BarrowsPiece.values) {
            assertEquals(BarrowsPiece.PRISTINE, piece.stageOf(piece.baseId))
            assertEquals(BarrowsPiece.FULL, piece.stageOf(piece.firstDegradedId))
            assertEquals(BarrowsPiece.BROKEN, piece.stageOf(piece.firstDegradedId + 4))
            assertEquals(-1, piece.stageOf(piece.firstDegradedId + 5))
        }
        assertEquals("15 hours of combat across four stages", 15 * 60 * 60 * 1000 / 600, BarrowsDegradation.STAGE_CYCLES * 4)
    }

    @Test
    fun repairCostScalesWithMissingStages() {
        for (piece in BarrowsPiece.values) {
            assertEquals("${piece.name} pristine", 0, piece.repairCost(piece.baseId))
            assertEquals("${piece.name} 100", 0, piece.repairCost(piece.idForStage(BarrowsPiece.FULL)))
            assertEquals("${piece.name} 75", piece.fullRepairCost / 4, piece.repairCost(piece.idForStage(2)))
            assertEquals("${piece.name} 0", piece.fullRepairCost, piece.repairCost(piece.idForStage(BarrowsPiece.BROKEN)))
        }
        assertEquals(60_000, BarrowsPiece.DHAROKS_HELM.repairCost(BarrowsPiece.DHAROKS_HELM.idForStage(BarrowsPiece.BROKEN)))
        assertEquals(100_000, BarrowsPiece.KARILS_CROSSBOW.repairCost(BarrowsPiece.KARILS_CROSSBOW.idForStage(BarrowsPiece.BROKEN)))
    }
}
