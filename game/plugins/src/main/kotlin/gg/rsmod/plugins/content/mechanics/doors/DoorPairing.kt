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
/**
 * A door whose "opened" half is a real, single, unambiguous middle state, but which is approached
 * from more than one physically-distinct "closed" id sharing that exact same middle state (e.g.
 * `4629`/`4631`, both `Door`/`Open`, both neighbours of the sole `4630` `Door`/`Close`). This is
 * exactly the shape [DoorPairing.derive] refuses under `claims.getValue(it.opened) == 1`, because
 * from the definitions alone there is no way to tell which closed id a given `opened` instance
 * should revert to. It is not a double/gate leaf pair either - `derive`'s [DoorPairing.derive]
 * caller already excludes every id a `gates.json`/`double-doors.json` set claims, e.g. `1551`/
 * `1553` share `1552`/`1556` but are two physically adjacent gate leaves (confirmed with
 * `ObjectPlacementProbeTool`), not two names for one door. A `MultiCloseDoor` group is only produced
 * when none of its ids overlap those configured multi-leaf sets.
 */
data class MultiCloseDoor(
    val closedIds: List<Int>,
    val opened: Int,
    val optionSlot: Int,
)

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

    /**
     * Finds every [MultiCloseDoor]: an `opened` id whose `Close` half is claimed, structurally, by
     * more than one `Open`-advertising neighbour of the same name in the same slot, where every one
     * of those ids is otherwise unclaimed (i.e. genuinely just "two closed variants, one open
     * state", not a three-or-more-way naming collision [derive] already treats as unresolvable, and
     * not one of the excluded multi-leaf ids). Runtime binding still has to decide which closed id a
     * given open instance reverts to - see `bind_ambiguous_single_doors` in `doors.plugin.kts`,
     * which records the originating id as an object attribute at open time rather than guessing.
     */
    fun deriveMultiClose(
        ids: Iterable<Int>,
        lookup: (Int) -> ObjectDef?,
        excluded: Set<Int> = emptySet(),
    ): List<MultiCloseDoor> {
        val byOpened = LinkedHashMap<Int, MutableList<Pair<Int, Int>>>()

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
            val candidates =
                listOf(closed + 1, closed - 1).filter { candidate ->
                    if (candidate in excluded) {
                        return@filter false
                    }
                    val other = lookup(candidate) ?: return@filter false
                    other.name == def.name && other.options.getOrNull(slot).equals(CLOSE, ignoreCase = true)
                }
            val opened = candidates.singleOrNull() ?: return@forEach
            byOpened.getOrPut(opened) { mutableListOf() }.add(closed to slot)
        }

        return byOpened.mapNotNull { (opened, closedWithSlot) ->
            if (closedWithSlot.size < 2) {
                return@mapNotNull null
            }
            val slots = closedWithSlot.map { it.second }.toSet()
            if (slots.size != 1) {
                return@mapNotNull null
            }
            MultiCloseDoor(closedIds = closedWithSlot.map { it.first }, opened = opened, optionSlot = slots.single())
        }
    }
}
