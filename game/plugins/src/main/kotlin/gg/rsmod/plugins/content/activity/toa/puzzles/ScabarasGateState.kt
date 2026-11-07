package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasReferences.Kind

/**
 * Strategies903-940 table: memory/obelisks/lights precede the addition tablet; matching is the final puzzle.
 * Perfect_Scabaras Wiki corroborates that the last puzzle cannot damage players. No Combat Achievement is implemented.
 * Which branched rooms are skipped solo depends on the observed layout; there is deliberately no fabricated default.
 */
class ScabarasGateState(val partySize: Int, private val soloSkipsOnClear: Map<Kind, Set<Kind>>,
                       private val numberPrerequisites: Set<Kind> = setOf(Kind.MEMORY, Kind.OBELISKS, Kind.LIGHTS)) {
    init {
        require(partySize in 1..8)
        require(soloSkipsOnClear.none { (key, skips) -> key in skips || Kind.MATCHING in skips })
        require(Kind.ADDITION !in numberPrerequisites && Kind.MATCHING !in numberPrerequisites)
    }
    private val solved = linkedSetOf<Kind>()
    private val omitted = linkedSetOf<Kind>()
    val finished: Set<Kind> get() = solved.toSet()
    val skipped: Set<Kind> get() = omitted.toSet()
    val canReadNumber: Boolean get() = (solved + omitted).containsAll(numberPrerequisites)
    val canMatch: Boolean get() = (solved + omitted).containsAll(Kind.values().filter { it != Kind.MATCHING })
    val complete: Boolean get() = canMatch && Kind.MATCHING in solved

    fun finish(kind: Kind): Boolean {
        if (kind in solved || kind in omitted || kind == Kind.ADDITION && !canReadNumber || kind == Kind.MATCHING && !canMatch) return false
        solved += kind
        if (partySize == 1) omitted += soloSkipsOnClear[kind].orEmpty().filter { it !in solved }
        return true
    }
}
