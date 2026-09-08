package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.fs.def.ObjectDef

/**
 * A single door the object definitions describe unambiguously: which id is shut, which id is the
 * same door standing open, and the 0-based option slot the `Open`/`Close` pair occupies.
 */
data class DerivedDoor(
    val closed: Int,
    val opened: Int,
    val optionSlot: Int,
)

/**
 * Derives single-door pairs straight from the object definitions.
 *
 * `data/cfg/doors/single-doors.json` only ever listed 35 doors, which is why so much of Gielinor
 * answered `Nothing interesting happens`: the general-doors mechanic was present, its data was not.
 * The definitions already carry the information, so it is read rather than transcribed.
 *
 * The rule is the one the hand-written config encodes: a definition advertising `Open` is the shut
 * half, and its open half is a neighbouring id carrying `Close` in the same option slot under the
 * same name.
 *
 * What makes this safe is what it refuses. Neither neighbour may be preferred - the existing config
 * contains both `10527 -> 10528` and `24931 -> 24930` - so a pair is only produced when exactly one
 * neighbour qualifies, and when no other shut id claims the same open half. Both checks matter for
 * real data: a double door is stored as a run of ids (`1551` shut, `1552` open, `1553` shut, or
 * `24930`-`24933`), and preferring a direction there silently swings the wrong leaf and leaves the
 * tile half-blocked.
 *
 * Run against the production cache this reproduces 25 of the 35 hand-written entries exactly, in
 * both directions, with no disagreement; the remaining 10 fall into the ambiguous set it declines.
 */
object DoorPairing {
    private const val OPEN = "Open"
    private const val CLOSE = "Close"

    /**
     * @param ids every object id to consider, typically every id in the cache.
     * @param lookup resolves an id to its definition, or null when the id does not exist.
     * @param excluded ids that must never take part in a pair, in either role. Callers pass the
     * configured double-door and gate sets: those are multi-leaf doors, and treating any of their
     * halves as a single door is wrong even for the halves their own config does not bind.
     */
    fun derive(
        ids: Iterable<Int>,
        lookup: (Int) -> ObjectDef?,
        excluded: Set<Int> = emptySet(),
    ): List<DerivedDoor> {
        val candidates = mutableListOf<DerivedDoor>()
        val claims = HashMap<Int, Int>()

        ids.forEach { closed ->
            if (closed in excluded) {
                return@forEach
            }
            val def = lookup(closed) ?: return@forEach
            if (def.name.isBlank()) {
                return@forEach
            }
            val slot = def.options.indexOfFirst { it.equals(OPEN, ignoreCase = true) }
            if (slot == -1) {
                return@forEach
            }
            val matches =
                listOf(closed + 1, closed - 1).filter { candidate ->
                    if (candidate in excluded) {
                        return@filter false
                    }
                    val other = lookup(candidate) ?: return@filter false
                    other.name == def.name && other.options.getOrNull(slot).equals(CLOSE, ignoreCase = true)
                }
            val opened = matches.singleOrNull() ?: return@forEach
            candidates += DerivedDoor(closed = closed, opened = opened, optionSlot = slot)
            claims[opened] = (claims[opened] ?: 0) + 1
        }

        return candidates.filter { claims.getValue(it.opened) == 1 }
    }
}
