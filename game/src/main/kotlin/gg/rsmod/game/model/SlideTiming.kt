package gg.rsmod.game.model

import gg.rsmod.game.fs.def.AnimDef

/**
 * Owner priority #1 (2026-10-03 20:58, bug-20261003-205829: "agility shortcut animations are still not smooth ... the character seems
 * to stand on the tile rather than passing the obstacle"). How the 667 client draws a forced movement (exact move), measured with the
 * agent client's `[walktrace]` and the owner's own client log, and what the server must send so the body visibly travels across the
 * obstacle in step with its animation:
 *
 * 1. The client draws the player's walk / run about one game cycle behind the server (its path buffer). An obstacle starts its slide
 *    in the cycle right after the approach's last step, while the body is still one (walk) or two (run) tiles short of the start tile.
 *    With no phase 1 (`t1 = 0`) the client puts the body on the start tile at once: owner 20:41 Trollheim 9306 "exactmove from=2908
 *    drawn=2906" (a two-tile snap), GE tunnel 9312 "from=3144,3514 drawn=3145,3513", 9311 "from=3138 drawn=3137". The fix is the
 *    client's own phase 1: [APPROACH_LEAD] client cycles in which `exactMoveTick1` glides the body from wherever it is drawn onto the
 *    start tile, with the slide's animation delayed by the same amount so animation and slide still begin together.
 * 2. A one-shot animation that ends long before its slide leaves the body gliding over the obstacle in its standing pose for the
 *    rest of the slide (Trollheim 9305 / 9306: human climbing down 1148 = 10 + 112 client cycles on a 160-cycle, four-tile slide -
 *    38 cycles = 0.8 s of standing upright while passing through the rock). The slide is ended with its animation instead, never
 *    faster than running speed ([MIN_CYCLES_PER_TILE]).
 */
object SlideTiming {
    /** Client cycles of phase 1 when the player stepped just before the slide: one game cycle, a walk tile or a run's two tiles. */
    const val APPROACH_LEAD = 30

    /** A step taken this many game cycles before the slide (or fewer) may still be on its way on the client. */
    const val STEP_RECENT_CYCLES = 2

    /** A slide may outlast its animation by this many client cycles without the standing glide showing. */
    const val GLIDE_TOLERANCE = 10

    /** Never slide faster than running: 2 tiles per game cycle = 15 client cycles per tile. */
    const val MIN_CYCLES_PER_TILE = 15

    data class Plan(
        /** Phase-1 end and phase-2 end, client cycles after the update that carries the slide. */
        val clientDuration1: Int,
        val clientDuration2: Int,
        /** The delay the slide's animation gets (client cycles), null when no animation goes with it. */
        val animationDelay: Int?,
        /** Phase-1 lead added for an approach the client was still drawing (0 = none). */
        val lead: Int,
        /** The slide end before it was shortened to its animation, null when it was not. */
        val shortenedFrom: Int?,
    )

    /**
     * The timing to send for a slide of [distance] tiles with the script's [clientDuration1] / [clientDuration2], when the player took
     * a walk or run step [cyclesSinceStep] game cycles ago and [anim] (with [animationDelay]) was started for it in the same cycle.
     */
    fun plan(
        clientDuration1: Int,
        clientDuration2: Int,
        distance: Int,
        cyclesSinceStep: Int,
        anim: AnimDef?,
        animationDelay: Int,
    ): Plan {
        val lead = if (cyclesSinceStep in 0..STEP_RECENT_CYCLES) APPROACH_LEAD else 0
        var end = maxOf(clientDuration2, clientDuration1)
        var shortenedFrom: Int? = null
        if (anim != null && !anim.loops && anim.frameLength > 0) {
            val animEnd = animationDelay + anim.frameLength
            val fastest = clientDuration1 + distance * MIN_CYCLES_PER_TILE
            if (end - animEnd > GLIDE_TOLERANCE) {
                val shortened = maxOf(animEnd, fastest, clientDuration1 + 1)
                if (shortened < end) {
                    shortenedFrom = end
                    end = shortened
                }
            }
        }
        return Plan(
            clientDuration1 = clientDuration1 + lead,
            clientDuration2 = end + lead,
            animationDelay = if (anim != null) animationDelay + lead else null,
            lead = lead,
            shortenedFrom = shortenedFrom,
        )
    }
}
