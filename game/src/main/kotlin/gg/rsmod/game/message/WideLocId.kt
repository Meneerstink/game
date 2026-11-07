package gg.rsmod.game.message

/**
 * N462 (07-11): loc ids above 0xFFFF (the ToA import placed 627 locs at 65536..66163) in the u16 fields of the 667 loc packets. Like the
 * model ids (archive 37) the fix keeps every packet size and uses a bit that was always 0:
 *  - LOC_ADD_CHANGE (server -> client): settings = shape << 2 | rotation uses 7 bits; 0x80 = id + 65536 (client ServerConnectionReader).
 *  - OPLOC1-6 / OPLOCT (client -> server): the movement byte is 0/1 (ctrl-run); 0x02 = id + 65536 (client MiniMenu).
 * Ids up to 131071 (ModelArchive.MAX_MODEL_ID range) - an old client without the bit only ever saw ids <= 0xFFFF correctly anyway.
 */
object WideLocId {
    const val SETTINGS_WIDE = 0x80
    const val MOVEMENT_WIDE = 0x02

    fun settings(settings: Int, id: Int): Int = if (id > 0xFFFF) settings or SETTINGS_WIDE else settings
    fun low(id: Int): Int = id and 0xFFFF

    /** (movementType without the wide bit, full loc id) from the raw OPLOC fields. */
    fun fromClient(rawMovement: Int, rawId: Int): Pair<Int, Int> {
        val wide = (rawMovement and MOVEMENT_WIDE) != 0
        return (rawMovement and MOVEMENT_WIDE.inv()) to ((rawId and 0xFFFF) + if (wide) 0x10000 else 0)
    }
}
