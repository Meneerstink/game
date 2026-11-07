package gg.rsmod.plugins.content.activity.toa.puzzles

/** OSRS Strategies oldid15392814:903-940; local07-scabaras* contains the source snapshots and image coordinates. */
object ScabarasPuzzle {
    enum class Result { IGNORED, PROGRESS, WRONG, SOLVED }

    /** A pressure plate triggers when occupancy changes from zero to nonzero, including concurrent entrants. */
    class Contacts<K, C> {
        private var occupied = emptySet<C>()
        fun update(positions: List<Pair<K, C>>): List<Pair<K, C>> {
            val current = positions.distinctBy { it.second }
            val entered = current.filter { it.second !in occupied }
            occupied = current.map { it.second }.toSet()
            return entered
        }
        fun clear() { occupied = emptySet() }
    }

    class Memory(val tiles: Int, private val resetOnWrong: Boolean) {
        init { require(tiles > 0) }
        private var order = emptyList<Int>()
        private var cursor = 0
        var generation = 0
            private set
        var ready = false
            private set
        var complete = false
            private set
        var closed = false
            private set

        fun show(sequence: List<Int>): Int? {
            if (closed || complete) return null
            require(sequence.isNotEmpty() && sequence.all { it in 0 until tiles })
            order = sequence.toList(); cursor = 0; ready = false
            return ++generation // A second button press invalidates ALL flashes from the previous playback.
        }
        fun finishPlayback(token: Int): Boolean {
            if (closed || complete || token != generation || order.isEmpty()) return false
            ready = true; return true
        }
        fun step(tile: Int): Result {
            if (closed || complete || !ready || tile !in 0 until tiles) return Result.IGNORED
            if (order[cursor] != tile) {
                if (resetOnWrong) cursor = 0
                return Result.WRONG
            }
            cursor++
            if (cursor == order.size) { complete = true; ready = false; return Result.SOLVED }
            return Result.PROGRESS
        }
        fun close() { closed = true; generation++; ready = false; order = emptyList() }
    }

    class Obelisks(order: List<Int>, private val resetOnWrong: Boolean) {
        // Strategies916-917: exactly5 obelisks, all120 permutations are possible.
        private val order = order.toList().also { require(it.size == 5 && it.toSet() == (0..4).toSet()) }
        var cursor = 0
            private set
        var closed = false
            private set
        val complete: Boolean get() = cursor == order.size
        fun strike(obelisk: Int): Result {
            if (closed || complete || obelisk !in 0..4) return Result.IGNORED
            if (order[cursor] != obelisk) {
                if (resetOnWrong) cursor = 0
                return Result.WRONG // Caller schedules avoidable falling rocks, not unavoidable direct damage.
            }
            cursor++
            return if (complete) Result.SOLVED else Result.PROGRESS
        }
        fun close() { closed = true }
    }

    /**
     * Eight plates around an empty centre. The self+orthogonal-neighbour XOR rule is an inference from the Wiki's
     * nine complete image examples (07-scabaras-beeldoplossingen.tsv), checked against EVERY provided solution.
     * This proves those examples and the solver; it does not establish OSRS's random distribution of initial states.
     */
    class Lights(initial: Int) {
        init { require(initial in 0..255) }
        var lit = initial
            private set
        var closed = false
            private set
        val complete: Boolean get() = lit == 255
        fun step(cell: Int): Result {
            if (closed || complete || cell !in CELLS.indices) return Result.IGNORED
            lit = lit xor TOGGLES[cell]
            return if (complete) Result.SOLVED else Result.PROGRESS
        }
        fun flip(cell: Int): Result {
            if (closed || complete || cell !in CELLS.indices) return Result.IGNORED
            lit = lit xor (1 shl cell) // Strategies920: individual right-click Flip costs20 unavoidable damage.
            return if (complete) Result.SOLVED else Result.PROGRESS
        }
        fun solution(): List<Int> {
            for (mask in 0..255) {
                var result = lit
                for (cell in CELLS.indices) if (mask and (1 shl cell) != 0) result = result xor TOGGLES[cell]
                if (result == 255) return CELLS.indices.filter { mask and (1 shl it) != 0 }
            }
            error("Unsolvable lights state: $lit")
        }
        fun close() { closed = true }
        companion object {
            // Row/column are one-based, matching the source image; (2,2) is deliberately NOT a pressure plate.
            val CELLS = listOf(1 to 1, 1 to 2, 1 to 3, 2 to 1, 2 to 3, 3 to 1, 3 to 2, 3 to 3)
            val TOGGLES = CELLS.map { (r, c) -> CELLS.foldIndexed(0) { i, mask, (y, x) ->
                if (kotlin.math.abs(r - y) + kotlin.math.abs(c - x) <= 1) mask or (1 shl i) else mask
            } }
        }
    }

    class Addition(val target: Int, values: List<Int> = NUMBER_GRID) {
        init { require(target in 20..45 && values.isNotEmpty() && values.all { it in 1..9 }) }
        private val values = values.toList()
        private val selected = linkedSetOf<Int>()
        var unlocked = false
            private set
        var closed = false
            private set
        val total: Int get() = selected.sumOf { values[it] }
        val selectedCells: Set<Int> get() = selected.toSet()
        val complete: Boolean get() = unlocked && total == target
        fun unlock(previousComplete: Boolean): Boolean {
            if (closed || !previousComplete) return false
            unlocked = true; return true
        }
        fun step(cell: Int): Result {
            if (closed || complete || !unlocked || cell !in values.indices || cell in selected) return Result.IGNORED
            selected += cell
            if (total > target) { selected.clear(); return Result.WRONG }
            return if (complete) Result.SOLVED else Result.PROGRESS
        }
        fun solution(): List<Int>? {
            val paths = mutableMapOf(0 to emptyList<Int>())
            for ((cell, value) in values.withIndex()) for ((sum, path) in paths.toMap()) {
                if (sum + value <= target) paths.putIfAbsent(sum + value, path + cell)
            }
            return paths[target]
        }
        fun close() { closed = true; selected.clear() }
    }

    class Matching(left: List<Int>, right: List<Int>, partySize: Int, preSolved: Set<Int>) {
        private val boards = listOf(left.toList(), right.toList())
        private val solved = preSolved.toMutableSet()
        private val selected = arrayOfNulls<Int>(2)
        var closed = false
            private set
        init {
            require(partySize in 1..8 && left.size == 9 && right.size == 9)
            require(left.toSet().size == 9 && left.toSet() == right.toSet())
            require(preSolved.all { it in left })
            require(preSolved.size == if (partySize == 1) 4 else 0) // Strategies939: four of nine pairs pre-solved ONLY solo.
        }
        val complete: Boolean get() = solved.size == 9
        fun matched(symbol: Int): Boolean = symbol in solved
        fun activate(board: Int, cell: Int): Result {
            if (closed || complete || board !in 0..1 || cell !in 0..8) return Result.IGNORED
            val symbol = boards[board][cell]
            if (symbol in solved || selected[board] == cell) return Result.IGNORED
            selected[board] = cell
            val other = selected[1 - board] ?: return Result.PROGRESS
            val same = boards[1 - board][other] == symbol
            selected.fill(null)
            if (!same) return Result.WRONG
            solved += symbol
            return if (complete) Result.SOLVED else Result.PROGRESS
        }
        fun close() { closed = true; selected.fill(null) }
    }

    /**
     * INFERRED numeric board (facing EAST): the26 sums in the Wiki additions-key image have rank23 over25 cells.
     * Restricting every cell to its published symbol range1..9 leaves EXACTLY ONE board. Tests validate all26 sums.
     * Mapping a NUMBER to its cache glyph/model is separate and remains an explicit scene reference, not an id guess.
     */
    val NUMBER_GRID = listOf(9, 1, 5, 4, 3, 2, 1, 8, 2, 4, 7, 4, 3, 7, 5, 3, 6, 6, 2, 9, 9, 7, 8, 6, 1)
}
