package gg.rsmod.plugins.content.scrolls

import kotlin.math.abs
import kotlin.random.Random

/**
 * The 5x5 sliding board. Tile numbers are internal image indices; zero is the
 * blank, not an item/cache ID. Each snapshot is copied so a UI cannot mutate it.
 * Source: Treasure Trails/Guide/Puzzle boxes, pinned in the CLUE manifest.
 */
class TreasureTrailSlidingPuzzle private constructor(private val board: IntArray) {
    val solved: Boolean get() = board.indices.all { board[it] == (it + 1) % CELLS }
    fun tiles(): IntArray = board.copyOf()

    fun move(index: Int): Boolean {
        if (index !in board.indices) return false
        val blank = board.indexOf(0)
        if (abs(index / WIDTH - blank / WIDTH) + abs(index % WIDTH - blank % WIDTH) != 1) return false
        board[blank] = board[index]
        board[index] = 0
        return true
    }

    fun encode(): String = board.joinToString(",")

    companion object {
        const val WIDTH = 5
        const val CELLS = WIDTH * WIDTH

        fun restore(tiles: IntArray): TreasureTrailSlidingPuzzle {
            require(tiles.size == CELLS && tiles.toSet() == (0 until CELLS).toSet()) { "Invalid clue puzzle permutation" }
            // Odd-width sliding boards can be solved exactly when nonblank
            // inversions are even. Reject corrupted or forged persisted states.
            val values = tiles.filter { it != 0 }
            val inversions = values.indices.sumOf { left -> (left + 1 until values.size).count { values[left] > values[it] } }
            require(inversions % 2 == 0) { "Unsolvable clue puzzle permutation" }
            return TreasureTrailSlidingPuzzle(tiles.copyOf())
        }

        fun decode(encoded: String): TreasureTrailSlidingPuzzle = restore(encoded.split(',').map { it.toInt() }.toIntArray())

        /** Wiki Puzzle box / Mod Ash: standard trails scramble with exactly 101 legal slides. */
        fun create(random: Random): TreasureTrailSlidingPuzzle {
            val puzzle = restore(IntArray(CELLS) { (it + 1) % CELLS })
            repeat(101) {
                val blank = puzzle.board.indexOf(0)
                val neighbours = listOf(blank - WIDTH, blank - 1, blank + 1, blank + WIDTH).filter { index ->
                    index in 0 until CELLS && abs(index / WIDTH - blank / WIDTH) + abs(index % WIDTH - blank % WIDTH) == 1
                }
                check(puzzle.move(neighbours[random.nextInt(neighbours.size)]))
            }
            // Every slide swaps the blank's checkerboard colour. An odd number
            // cannot return to the solved board; no extra reroll changes the count.
            check(!puzzle.solved)
            return puzzle
        }
    }
}

/**
 * Five by five lamps / eight switches (Wiki Light boxes). The exact switch
 * wiring is input data, never an invented fixed OSRS pattern. It must come
 * from the source-backed puzzle generator/UI integration before activation.
 */
class TreasureTrailLightBox(masks: IntArray, switches: Int = 0, private val baseLights: Int = ALL_ON) {
    private val wiring = masks.copyOf()
    var switches: Int = switches
        private set
    init {
        require(wiring.size == SWITCHES && wiring.all { it >= 0 && it and ALL_ON == it })
        require(switches in 0 until (1 shl SWITCHES) && baseLights >= 0 && baseLights and ALL_ON == baseLights)
        require((0 until (1 shl SWITCHES)).any { lights(it) == ALL_ON }) { "Unsolvable clue light box" }
    }

    private fun lights(state: Int): Int = wiring.indices.fold(baseLights) { lit, index ->
        if (state and (1 shl index) != 0) lit xor wiring[index] else lit
    }
    val lights: Int get() = lights(switches)
    val solved: Boolean get() = lights == ALL_ON
    fun toggle(index: Int): Boolean {
        if (index !in wiring.indices) return false
        switches = switches xor (1 shl index)
        return true
    }
    fun masks(): IntArray = wiring.copyOf()

    companion object {
        const val SWITCHES = 8
        const val ALL_ON = (1 shl 25) - 1
    }
}
