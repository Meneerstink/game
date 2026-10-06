package gg.rsmod.game.model.entity

/**
 * Per-placement fit of one loc: where exactly it sits, how big it is and how it is turned, on top of its tile, shape
 * and quarter-turn. Sent to the client with the loc (LOC_CUSTOMISE flag 0x10, the 667 client's own per-instance loc
 * customisation - `LocTypeCustomisation` - extended with these five values) and applied by the client to that one
 * instance's model copy, so two locs with the same id on different tiles can have different fits.
 *
 * Units are the client's model units: 512 per tile (the 667 client upscales cache models, 128 per tile, by four), so
 * 4 units = 1/128 tile. [dx]/[dy]/[dz] are in the loc's own frame before its quarter-turn: at rotation 0 a wall shape
 * hangs on the north edge of its tile, so +dx runs east along the wall, -dz goes away from the wall into the tile and
 * -dy is up. [scale] is uniform, 128 = 100%. [yaw] is a turn around the vertical axis, 16384 = a full turn (clockwise
 * from above, like the quarter-turns: 4096 = one quarter). Wall shapes scale and turn about their wall contact.
 */
data class LocFit(
    val dx: Int = 0,
    val dy: Int = 0,
    val dz: Int = 0,
    val scale: Int = SCALE_ONE,
    val yaw: Int = 0,
) {
    val isIdentity: Boolean get() = dx == 0 && dy == 0 && dz == 0 && (scale == SCALE_ONE || scale == 0) && yaw % YAW_TURN == 0

    /** The same fit with every value inside the ranges the client packet carries (and a scale of 0 read as 100%). */
    fun normalised(): LocFit =
        LocFit(
            dx.coerceIn(-MAX_OFFSET, MAX_OFFSET),
            dy.coerceIn(-MAX_OFFSET, MAX_OFFSET),
            dz.coerceIn(-MAX_OFFSET, MAX_OFFSET),
            (if (scale == 0) SCALE_ONE else scale).coerceIn(MIN_SCALE, MAX_SCALE),
            ((yaw % YAW_TURN) + YAW_TURN) % YAW_TURN,
        )

    companion object {
        const val UNITS_PER_TILE = 512
        const val SCALE_ONE = 128
        const val MIN_SCALE = 16
        const val MAX_SCALE = 1024
        const val YAW_TURN = 16384
        const val YAW_QUARTER = 4096

        /** Offsets are kept within four tiles; a loc further off its tile than that belongs on another tile. */
        const val MAX_OFFSET = 4 * UNITS_PER_TILE

        /** Degrees of [yaw] units (rounded to one decimal). */
        fun degrees(yaw: Int): Double = Math.round(yaw * 3600.0 / YAW_TURN) / 10.0
    }
}
