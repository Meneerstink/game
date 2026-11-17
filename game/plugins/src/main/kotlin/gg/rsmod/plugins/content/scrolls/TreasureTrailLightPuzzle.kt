package gg.rsmod.plugins.content.scrolls

import kotlin.random.Random

/** Wiki Guide/Light boxes: eight switches toggle fixed subsets of a 25-light grid. */
class TreasureTrailLightPuzzle private constructor(private val masks: IntArray, private var lights: Int) {
    val bits: Int get() = lights
    val solved: Boolean get() = lights == ALL
    fun effects(): IntArray = masks.copyOf()
    fun toggle(button: Int): Boolean {
        if (button !in masks.indices) return false
        lights = lights xor masks[button]
        return true
    }
    fun lit(cell: Int): Boolean = cell in 0 until CELLS && lights and (1 shl cell) != 0

    companion object {
        const val CELLS = 25
        const val SWITCHES = 8
        const val ALL = (1 shl CELLS) - 1
        fun restore(effects: IntArray, bits: Int): TreasureTrailLightPuzzle {
            require(effects.size == SWITCHES && effects.all { it in 0..ALL } && bits in 0..ALL)
            // Lost/corrupt item attributes may not create an impossible puzzle.
            require((0 until (1 shl SWITCHES)).any { combination ->
                var value = bits
                effects.forEachIndexed { index, mask -> if (combination and (1 shl index) != 0) value = value xor mask }
                value == ALL
            }) { "Unsolvable light box state" }
            return TreasureTrailLightPuzzle(effects.copyOf(), bits)
        }
        fun create(random: Random = Random.Default): TreasureTrailLightPuzzle {
            val masks = IntArray(SWITCHES) { random.nextInt(1, ALL + 1) }
            var bits = ALL
            val combination = random.nextInt(1, 1 shl SWITCHES)
            masks.forEachIndexed { index, mask -> if (combination and (1 shl index) != 0) bits = bits xor mask }
            if (bits == ALL) bits = ALL xor masks[0]
            return TreasureTrailLightPuzzle(masks, bits)
        }
    }
}
