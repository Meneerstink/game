package gg.rsmod.game.sync.segment

import gg.rsmod.game.model.Hit

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

    fun of(hits: List<Hit>): List<Entry> {
        val entries = ArrayList<Entry>()
        hits.forEach { hit ->
            if (hit.hitmarks.isEmpty()) {
                entries += Entry(NO_HITMARK, 0, hit.clientDelay)
            } else {
                hit.hitmarks.forEach { entries += Entry(it.type, it.damage, hit.clientDelay) }
            }
        }
        // The count is written as one unsigned byte.
        return if (entries.size > 255) entries.subList(0, 255) else entries
    }
}
