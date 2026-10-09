package gg.rsmod.game.sync.segment

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player

/**
 * The HITMARK update-block entries for a cycle's [Hit]s, one entry per hitmark.
 *
 * The rev-667 client (PlayerList/NPCList HITMARK) reads exactly one hitmark per entry: `smart type` then `smart damage`
 * (a second "soak" mark only behind a 32767 marker, 32766 for a hit with no mark), then `smart delay` and `g1 health`. The
 * server used to write up to two type/damage pairs inside one entry, which the client misread as the delay and health of
 * that entry, desynchronising every following entry and block - damage then applied with no visible hitsplat (owner
 * 2026-09-18: guards killed a skulled player "without seeing hits"). Each hitmark is now its own entry with its hit's delay.
 */
internal object HitmarkEntries {
    /** Marker the client reads as "no hitmark in this entry" (health bar update only). */
    const val NO_HITMARK = 32766

    data class Entry(val type: Int, val damage: Int, val delay: Int)

    /**
     * The entries [observer] receives for [hits] on [target]. Each mark is sent as the OSRS hitsplat its group resolves to
     * for this viewer (owner 2026-10-09, RSMod HitBuilder): lit for the target and the hit's source, tinted for everyone
     * else, block for a regular 0, max for a big player hit. A null [observer] counts as privileged (tests, replays).
     */
    fun of(
        hits: List<Hit>,
        target: Pawn? = null,
        observer: Player? = null,
    ): List<Entry> {
        val entries = ArrayList<Entry>()
        hits.forEach { hit ->
            if (hit.hitmarks.isEmpty()) {
                entries += Entry(NO_HITMARK, 0, hit.clientDelay)
            } else {
                val privileged = observer == null || observer === target || (hit.source != null && observer === hit.source)
                val threshold = hit.maxHitThreshold()
                hit.hitmarks.forEach {
                    val shown = it.displayDamage()
                    entries += Entry(it.group.resolve(shown, privileged, threshold), shown, hit.clientDelay)
                }
            }
        }
        // The count is written as one unsigned byte.
        return if (entries.size > 255) entries.subList(0, 255) else entries
    }
}
