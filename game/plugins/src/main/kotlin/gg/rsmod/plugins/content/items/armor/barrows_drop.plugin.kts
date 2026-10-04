package gg.rsmod.plugins.content.items.armor

/*
 * OSRS Wiki "Barrows equipment": "When dropped, any piece of Barrows equipment will automatically degrade to 0." Applied to the
 * used pieces (100 .. 25); a never-used piece is tradeable and drops as it is (audit 2026-10-04 degradation, Barrows item 4).
 */
gg.rsmod.game.message.handler.OpHeld5Handler.dropAs = { id ->
    val piece = BarrowsPiece.forId(id)
    val stage = piece?.stageOf(id) ?: -1
    if (piece != null && stage in BarrowsPiece.FULL until BarrowsPiece.BROKEN) piece.idForStage(BarrowsPiece.BROKEN) else null
}
