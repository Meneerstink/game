package gg.rsmod.plugins.api

/**
 * Overhead prayer/curse head icons.
 *
 * `OverlayManager.java` in `2011scape-client` renders `Sprites.headiconsPrayer[player.prayerIcon]`
 * - a plain array index into this cache's own `headicons_prayer` sprite sheet - so every id below
 * has to be this cache's real frame number, not a donor client's.
 *
 * 2026-09-06: all 27 frames were decoded and rendered with
 * `./gradlew :game:runSpriteSheetDumpTool --args="../data/cache headicons_prayer <outDir>"`, a
 * direct port of this revision's own `com.jagex.IndexedImage.load(byte[])`, and identified by
 * sight from the contact sheet it writes. That replaced the previous, explicitly-flagged
 * SOURCE_CONFLICT guess taken from RuneLite's `HeadIcon` enum, which was wrong for every single
 * curse: it had Wrath on frame 8 (really Protect Summoning + Melee), Deflect Summoning on 10
 * (really Protect Summoning + Magic), Deflect Magic on 11 (really a *blank* frame), Deflect
 * Missiles on 12 (really Deflect Melee) and Deflect Melee on 13 (really Deflect Magic). That is
 * the cause of the "incorrect overhead visuals" reported on human retest.
 *
 * The decoded sheet, frame by frame:
 *
 * ```
 *  0 sword                    7 wolf                  14 arrow + blue        21 red skull
 *  1 arrow                    8 wolf + sword          15 wolf + blue         22 sword + arrow
 *  2 staff                    9 wolf + arrow          16 wolf + sword + blue 23 sword + staff
 *  3 retribution              10 wolf + staff         17 wolf + arrow + blue 24 arrow + staff
 *  4 smite                    11 blank                18 wolf + hat + blue   25 white skull
 *  5 redemption               12 sword + blue         19 explosion           26 empty
 *  6 arrow + staff            13 wizard hat + blue    20 white star on red
 * ```
 *
 * The blue wedge is the Deflect motif and the wolf is the Summoning motif, which is what makes the
 * combined frames (8/9/10 for the normal book, 16/17/18 for the curse book) identifiable: real RS
 * renders one *combined* icon when a Summoning protection is active alongside a combat one, rather
 * than dropping one of them.
 */
enum class PrayerIcon(
    val id: Int,
) {
    NONE(id = -1),
    PROTECT_FROM_MELEE(id = 0),
    PROTECT_FROM_MISSILES(id = 1),
    PROTECT_FROM_MAGIC(id = 2),
    RETRIBUTION(id = 3),
    SMITE(id = 4),
    REDEMPTION(id = 5),
    PROTECT_FROM_MISSLES_AND_MAGIC(id = 6),
    PROTECT_FROM_SUMMONING(id = 7),
    PROTECT_FROM_SUMMONING_AND_MELEE(id = 8),
    PROTECT_FROM_SUMMONING_AND_MISSILES(id = 9),
    PROTECT_FROM_SUMMONING_AND_MAGIC(id = 10),

    DEFLECT_MELEE(id = 12),
    DEFLECT_MAGIC(id = 13),
    DEFLECT_MISSILES(id = 14),
    DEFLECT_SUMMONING(id = 15),
    DEFLECT_SUMMONING_AND_MELEE(id = 16),
    DEFLECT_SUMMONING_AND_MISSILES(id = 17),
    DEFLECT_SUMMONING_AND_MAGIC(id = 18),
    WRATH(id = 19),
    SOUL_SPLIT(id = 20),
    ;

    companion object {
        /**
         * The single frame that shows both a Summoning protection and a combat one. Real RS has a
         * dedicated combined frame for every one of these pairings, so neither has to be dropped.
         */
        fun combined(
            summoning: Boolean,
            combat: PrayerIcon?,
        ): PrayerIcon? {
            if (!summoning) return combat
            return when (combat) {
                null, NONE -> PROTECT_FROM_SUMMONING
                PROTECT_FROM_MELEE -> PROTECT_FROM_SUMMONING_AND_MELEE
                PROTECT_FROM_MISSILES -> PROTECT_FROM_SUMMONING_AND_MISSILES
                PROTECT_FROM_MAGIC -> PROTECT_FROM_SUMMONING_AND_MAGIC
                else -> combat
            }
        }

        /** The curse-book counterpart of [combined]. */
        fun combinedDeflect(
            summoning: Boolean,
            combat: PrayerIcon?,
        ): PrayerIcon? {
            if (!summoning) return combat
            return when (combat) {
                null, NONE -> DEFLECT_SUMMONING
                DEFLECT_MELEE -> DEFLECT_SUMMONING_AND_MELEE
                DEFLECT_MISSILES -> DEFLECT_SUMMONING_AND_MISSILES
                DEFLECT_MAGIC -> DEFLECT_SUMMONING_AND_MAGIC
                else -> combat
            }
        }
    }
}
