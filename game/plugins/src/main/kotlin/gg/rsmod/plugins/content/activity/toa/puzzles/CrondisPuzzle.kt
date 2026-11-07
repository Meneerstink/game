package gg.rsmod.plugins.content.activity.toa.puzzles

import kotlin.math.ceil

/**
 * Path of Crondis' shared state, independent of packets and scene timers.
 * Sources: N462/09b-crondis-brontekst.txt, Palm_of_Resourcefulness oldid15356947,
 * Water_container_(Tombs_of_Amascut) oldid15191030, Crocodile_(Tombs_of_Amascut) oldid15375196,
 * and Strategies oldid15392814:783-795. All cycles below are 600ms game ticks.
 */
class CrondisPuzzle<K : Any>(val partySize: Int) {
    init { require(partySize in 1..8) }

    // Strategies:783: 175 solo, +125 per additional raid member. Never scales with raid level.
    val requiredWater = 175 + 125 * (partySize - 1)
    var growth = 0.0
        private set
    var closed = false
        private set
    val complete: Boolean get() = growth >= requiredWater
    // Palm's cache variants11700..11704 represent 0/25/50/75/100%, not separate NPCs to spawn.
    val palmStage: Int get() = (growth * 4 / requiredWater).toInt().coerceIn(0, 4)

    private class Carrier(var container: Any?, var water: Double = 0.0) {
        val obstacles = ArrayDeque<Int>()
        var lastAction: Int? = null
    }
    private val carriers = linkedMapOf<K, Carrier>()
    private val waterfallReady = hashMapOf<Int, Int>()

    /** The actual item identity prevents destroying/replacing a container from restoring its contents. */
    fun container(player: K, identity: Any) {
        if (closed || complete) return
        val previous = carriers[player]
        if (previous == null) carriers[player] = Carrier(identity)
        else if (previous.container !== identity) { previous.container = identity; previous.water = 0.0 }
    }

    fun water(player: K): Double = carriers[player]?.water ?: 0.0
    //Wiki guarantees nonempty>=1%. ONZEKER T18: ceil rounding of fractions (the exact fractional display rule is not published).
    fun displayedWater(player: K): Int = ceil(water(player)).toInt().coerceIn(0, 100)

    /** Refill duration is supplied by a measured reference; T18 explicitly has no published duration. */
    fun fill(player: K, waterfall: Int, cycle: Int, refillCycles: Int): Boolean {
        require(waterfall >= 0 && refillCycles > 0)
        val carrier = carriers[player] ?: return false
        if (closed || complete || carrier.lastAction == cycle || carrier.water >= 100.0 ||
            cycle < (waterfallReady[waterfall] ?: Int.MIN_VALUE)) return false
        carrier.water = 100.0 // Water-container percentage scale, independent of the palm's requiredWater.
        carrier.lastAction = cycle
        waterfallReady[waterfall] = cycle + refillCycles
        return true
    }

    fun waterfallAvailable(waterfall: Int, cycle: Int): Boolean = !closed && !complete &&
        cycle >= (waterfallReady[waterfall] ?: Int.MIN_VALUE)

    fun waterPalm(player: K, cycle: Int): Double {
        val carrier = carriers[player] ?: return 0.0
        if (closed || complete || carrier.lastAction == cycle || carrier.water <= 0.0) return 0.0
        val amount = minOf(carrier.water, requiredWater - growth)
        growth += amount
        // The container is poured out, including water in excess of the palm's remaining requirement.
        carrier.water = 0.0
        carrier.lastAction = cycle
        return amount
    }

    fun empty(player: K): Boolean {
        val carrier = carriers[player] ?: return false
        if (closed || complete || carrier.water == 0.0) return false
        carrier.water = 0.0
        return true
    }

    /** Palm wiki: ordinary watering cans or ten Gricoller charges contribute exactly one water point. */
    fun waterFromCan(player: K, cycle: Int, consume: () -> Boolean): Boolean {
        if (closed || complete) return false
        val carrier = carriers.getOrPut(player) { Carrier(null) }
        if (carrier.lastAction == cycle || !consume()) return false
        growth = minOf(requiredWater.toDouble(), growth + 1.0)
        carrier.lastAction = cycle
        return true
    }

    /** Trap incidence is per player. Warning clouds never call this method. */
    fun obstacle(player: K, cycle: Int, retained: Double = 0.5) {
        require(retained in 0.0..1.0)
        if (closed || complete) return
        val carrier = carriers.getOrPut(player) { Carrier(null) }
        if (carrier.water > 0.0) carrier.water = (carrier.water * retained).coerceAtLeast(Double.MIN_VALUE)
        carrier.obstacles.addLast(cycle)
        expireObstacles(carrier, cycle)
    }

    private fun expireObstacles(carrier: Carrier, cycle: Int) {
        // Crocodile wiki: only obstacles in the last30s affect its hit, i.e.50 game ticks.
        while (carrier.obstacles.firstOrNull()?.let { cycle - it >= 50 } == true) carrier.obstacles.removeFirst()
    }

    fun crocodileDamage(player: K, cycle: Int, protectMelee: Boolean): Int {
        if (closed || complete) return 0
        val carrier = carriers[player]
        carrier?.let { expireObstacles(it, cycle) }
        // Crocodile wiki: fixed18, +3 per obstacle, cap36; prayer reduces to exactly one third.
        val damage = minOf(36, 18 + 3 * (carrier?.obstacles?.size ?: 0))
        return if (protectMelee) damage / 3 else damage
    }

    fun crocodileBite(player: K) {
        if (closed || complete) return
        val carrier = carriers[player] ?: return
        // Water-container page: once below15%, crocodile bites remove10%, rather than50%.
        if (carrier.water > 0.0) carrier.water = (carrier.water * if (carrier.water < 15.0) 0.9 else 0.5).coerceAtLeast(Double.MIN_VALUE)
    }

    fun eatPalm(amount: Double): Double {
        require(amount >= 0.0)
        if (closed || complete) return 0.0
        val eaten = minOf(growth, amount)
        growth -= eaten
        return eaten
    }

    /** Specific crocodile article takes precedence over the conflicting general Strategies paragraph. */
    fun target(inRange: List<K>, attackers: Set<K>): Target<K>? {
        if (closed || complete) return null
        inRange.firstOrNull { water(it) > 0.0 }?.let { return Target.Player(it) }
        if (growth > 0.0) return Target.Palm
        // Holding an EMPTY container makes even an attacking player ineligible (Crocodile wiki:49).
        return inRange.firstOrNull { it in attackers && carriers[it]?.container == null }?.let { Target.Player(it) }
    }

    sealed interface Target<out K> {
        data class Player<K>(val player: K) : Target<K>
        object Palm : Target<Nothing>
    }

    fun remove(player: K) { carriers.remove(player) }
    fun containerGone(player: K) { carriers[player]?.let { it.container = null; it.water = 0.0 } }
    fun close() { closed = true; carriers.clear(); waterfallReady.clear() }
}
