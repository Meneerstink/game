package gg.rsmod.plugins.api

import gg.rsmod.game.model.combat.CombatClass

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
 *
 * 2026-09-12: the whole table was corroborated against a second, independent rev-667 source -
 * Novite's `game/player/Prayer.java`, `getPrayerHeadIcon()`, which starts at -1 and adds a
 * per-prayer offset. It yields normal-book Protect Summoning alone = 7, combined with
 * Melee/Missiles/Magic = 8/9/10, Retribution = 3, Redemption = 5, Smite = 4; and curse-book
 * Deflect Melee/Magic/Missiles = 12/13/14, Deflect Summoning alone = 15, combined with
 * Melee/Missiles/Magic = 16/17/18, Wrath = 19, Soul Split = 20. That is every id below, derived
 * from server code rather than from the sprite sheet, and it agrees with the sheet frame for
 * frame.
 */
enum class PrayerIcon(
    val id: Int,
    /**
     * Which combat classes this overhead actually protects against.
     *
     * This is deliberately a **property of the icon**, not of the prayer, because the overhead is
     * the only thing the combat formulas ever see: a [gg.rsmod.game.model.entity.Pawn] carries an
     * `prayerIcon` int and nothing else. Before this was modelled, every formula tested
     * `prayerIcon == PROTECT_FROM_MELEE.id` (and the two equivalents), which is true for exactly
     * one of the **eight** frames in this sheet that really block melee - so a curse-book Deflect
     * Melee (12), a Protect from Summoning + Melee (8) and a Deflect Summoning + Melee (16) all
     * silently lost the player every bit of their protection, in PvM and PvP alike. Same for the
     * ranged and magic families, and for the normal book's own combined Missiles + Magic frame (6),
     * which protected against neither of the two things it draws.
     *
     * Deflects protect exactly like the Protect prayers of the same style - that is what
     * wiki.darkan.org means by "acts as the corresponding protection prayer, and additionally
     * reflects damage"; the reflection half lives in
     * [gg.rsmod.plugins.content.mechanics.prayer.AncientCurses.onIncomingHit].
     */
    val protects: Set<CombatClass> = emptySet(),
    /**
     * Whether this overhead is one of the Summoning protections (normal book Protect from
     * Summoning, curse book Deflect Summoning, and the six combined frames). Familiar damage is
     * checked against this rather than against [protects], because Summoning is not one of the
     * three [CombatClass] styles.
     */
    val protectsSummoning: Boolean = false,
) {
    NONE(id = -1),
    PROTECT_FROM_MELEE(id = 0, protects = setOf(CombatClass.MELEE)),
    PROTECT_FROM_MISSILES(id = 1, protects = setOf(CombatClass.RANGED)),
    PROTECT_FROM_MAGIC(id = 2, protects = setOf(CombatClass.MAGIC)),
    RETRIBUTION(id = 3),
    SMITE(id = 4),
    REDEMPTION(id = 5),
    PROTECT_FROM_MISSLES_AND_MAGIC(id = 6, protects = setOf(CombatClass.RANGED, CombatClass.MAGIC)),
    PROTECT_FROM_SUMMONING(id = 7, protectsSummoning = true),
    PROTECT_FROM_SUMMONING_AND_MELEE(id = 8, protects = setOf(CombatClass.MELEE), protectsSummoning = true),
    PROTECT_FROM_SUMMONING_AND_MISSILES(id = 9, protects = setOf(CombatClass.RANGED), protectsSummoning = true),
    PROTECT_FROM_SUMMONING_AND_MAGIC(id = 10, protects = setOf(CombatClass.MAGIC), protectsSummoning = true),

    DEFLECT_MELEE(id = 12, protects = setOf(CombatClass.MELEE)),
    DEFLECT_MAGIC(id = 13, protects = setOf(CombatClass.MAGIC)),
    DEFLECT_MISSILES(id = 14, protects = setOf(CombatClass.RANGED)),
    DEFLECT_SUMMONING(id = 15, protectsSummoning = true),
    DEFLECT_SUMMONING_AND_MELEE(id = 16, protects = setOf(CombatClass.MELEE), protectsSummoning = true),
    DEFLECT_SUMMONING_AND_MISSILES(id = 17, protects = setOf(CombatClass.RANGED), protectsSummoning = true),
    DEFLECT_SUMMONING_AND_MAGIC(id = 18, protects = setOf(CombatClass.MAGIC), protectsSummoning = true),
    WRATH(id = 19),
    SOUL_SPLIT(id = 20),
    ;

    companion object {
        private val BY_ID = values().associateBy { it.id }

        /** The overhead a raw `prayerIcon` int denotes, or `null` for an unmapped frame. */
        fun byId(id: Int): PrayerIcon? = BY_ID[id]

        /**
         * Whether the overhead currently rendered as [iconId] blocks [style]. This is the single
         * question every combat formula should ask - see [protects].
         */
        fun protectsAgainst(
            iconId: Int,
            style: CombatClass,
        ): Boolean = BY_ID[iconId]?.protects?.contains(style) == true

        /** Whether the overhead currently rendered as [iconId] is a Summoning protection. */
        fun protectsAgainstSummoning(iconId: Int): Boolean = BY_ID[iconId]?.protectsSummoning == true

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
