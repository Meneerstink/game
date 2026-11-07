package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.combat.CombatClass

/** Strategies oldid15392814:1234-1327; Path_of_Apmeken oldid14932291 changelog (2023/2024 changes). */
class ApmekenPuzzle<K : Any>(val partySize: Int) {
    init { require(partySize in 1..8) }
    private val players = linkedSetOf<K>()
    private val alive = linkedMapOf<Any, Baboon>()
    var sight: K? = null
        private set
    var wave = 0
        private set
    var closed = false
        private set
    var task: Task<K>? = null
        private set
    val remaining: Int get() = alive.size
    val complete: Boolean get() = !closed && wave == WAVES.size && alive.isEmpty() && task == null

    fun enter(player: K) {
        if (closed) return
        if (players.add(player)) {
            if (sight == null) sight = player //Strategies1291: first entrant starts with Sight.
            task?.takeIf { it.type == Action.CORRUPTION && player != sight }?.corrupted?.add(player)
        }
    }

    /** A disconnect/death cannot leave a mandatory corruption target behind. Caller supplies the next living Sight holder. */
    fun leave(player: K, nextSight: K?) {
        players.remove(player)
        task?.corrupted?.remove(player)
        if (sight == player) sight = nextSight?.takeIf { it in players }
        finishTaskIfSatisfied()
    }

    fun transferSight(player: K): Boolean {
        if (closed || player !in players) return false
        sight = player
        return true
    }

    /**
     * The source gives the solo compositions, but not the distribution of Special, the extra group type,
     * small/large selection, spawn tiles or wave overlap thresholds (T22). These are explicit inputs.
     */
    fun nextWave(remainingThreshold: Int, spawn: (List<Baboon>) -> List<Pair<Any, Baboon>>): Boolean {
        require(remainingThreshold >= 0)
        if (closed || players.isEmpty() || wave >= WAVES.size || alive.size > remainingThreshold) return false
        val expected = WAVES[wave]
        val spawned = spawn(expected)
        if (spawned.isEmpty() || spawned.map { it.first }.distinct().size != spawned.size ||
            spawned.any { it.first in alive || it.second == Baboon.SPECIAL }) return false
        // Verify the fixed composition without inventing the unsourced random/group additions.
        val available = spawned.map { it.second }.toMutableList()
        for (type in expected) {
            val index = available.indexOfFirst { if (type == Baboon.SPECIAL) it.special else it.family == type.family }
            if (index < 0) return false
            available.removeAt(index)
        }
        if (partySize == 1 && available.isNotEmpty()) return false
        if (partySize > 1 && (available.isEmpty() || available.any { !it.combat })) return false
        spawned.forEach { (id, type) -> alive[id] = type }
        wave++
        return true
    }

    fun child(id: Any, type: Baboon): Boolean {
        if (closed || type != Baboon.THRALL || alive.containsKey(id)) return false
        alive[id] = type
        return true
    }
    fun killed(id: Any): Boolean = !closed && alive.remove(id) != null
    fun type(id: Any): Baboon? = alive[id]

    enum class Action { PILLAR, VENT, CORRUPTION }
    class Task<K>(val type: Action, val required: Int, val remainingSites: MutableMap<Int, Int>,
                  val corrupted: MutableSet<K>, val deadline: Int) {
        var performed = 0
            internal set
    }
    enum class Result { REFUSED, WRONG, PERFORMED, COMPLETE }

    fun beginTask(type: Action, required: Int, sites: Map<Int, Int>, deadline: Int): Boolean {
        require(required > 0 && sites.values.all { it > 0 })
        if (closed || complete || task != null || players.isEmpty() || (type == Action.CORRUPTION && partySize == 1)) return false
        if (type != Action.CORRUPTION && sites.values.sum() < required) return false
        task = Task(type, required, sites.toMutableMap(),
            if (type == Action.CORRUPTION) players.filter { it != sight }.toMutableSet() else mutableSetOf(), deadline)
        return true
    }

    fun fix(player: K, type: Action, site: Int, cycle: Int): Result {
        if (closed || player !in players || type == Action.CORRUPTION) return Result.REFUSED
        val current = task ?: return Result.WRONG
        if (cycle >= current.deadline || current.type != type || (current.remainingSites[site] ?: 0) <= 0) return Result.WRONG
        current.remainingSites[site] = current.remainingSites.getValue(site) - 1
        current.performed++
        return if (finishTaskIfSatisfied()) Result.COMPLETE else Result.PERFORMED
    }

    /** The Sight holder may either pour on the stack or use the potion directly on one corrupted teammate. */
    fun cleanse(player: K, onSameTile: Set<K>, cycle: Int): Result {
        if (closed || player !in players) return Result.REFUSED
        val current = task ?: return Result.WRONG
        if (cycle >= current.deadline || current.type != Action.CORRUPTION || player != sight) return Result.WRONG
        val removed = current.corrupted.intersect(onSameTile)
        if (removed.isEmpty()) return Result.WRONG
        current.corrupted.removeAll(removed)
        current.performed += removed.size
        return if (finishTaskIfSatisfied()) Result.COMPLETE else Result.PERFORMED
    }

    private fun finishTaskIfSatisfied(): Boolean {
        val current = task ?: return false
        val done = if (current.type == Action.CORRUPTION) current.corrupted.isEmpty() else current.performed >= current.required
        if (done) task = null
        return done
    }

    /** Number of missed actions for the caller's sourced group-damage formula, evaluated once only. */
    fun expireTask(cycle: Int): Int {
        val current = task ?: return 0
        if (closed || cycle < current.deadline) return 0
        task = null
        return if (current.type == Action.CORRUPTION) current.corrupted.size else (current.required - current.performed).coerceAtLeast(0)
    }

    fun close() { closed = true; players.clear(); alive.clear(); task = null; sight = null }

    enum class Baboon(val osrs: Int, val hp: Int, val family: Int, val weakness: CombatClass?, val special: Boolean = false) {
        // 09-apmeken + minion infoboxes: fixed HP after 29May2024, independent of RL and group.
        BRAWLER(11709, 25, 0, CombatClass.MAGIC), THROWER(11710, 30, 1, CombatClass.MELEE),
        MAGE(11711, 20, 2, CombatClass.RANGED), LARGE_BRAWLER(11712, 30, 0, CombatClass.MAGIC),
        LARGE_THROWER(11713, 35, 1, CombatClass.MELEE), LARGE_MAGE(11714, 25, 2, CombatClass.RANGED),
        SHAMAN(11715, 40, 3, CombatClass.RANGED, true), VOLATILE(11716, 8, 4, CombatClass.RANGED, true),
        CURSED(11717, 20, 5, CombatClass.RANGED, true),
        // Specific current Baboon_Thrall oldid infobox:2 HP; Strategies1274 still lists obsolete9.
        THRALL(11718, 2, 6, null), SPECIAL(-1, 0, -1, null, true);
        val combat: Boolean get() = family in 0..2
        //Brawler/Thrower/Mage articles: triangle max hits.25June2025: only Shaman/Cursed gain ranged maxima.
        fun maximumHit(style: CombatClass): Boolean = (combat && style == weakness) ||
            ((this == SHAMAN || this == CURSED) && style == CombatClass.RANGED)
    }

    companion object {
        // Strategies1304-1326, exactly8 SOLO waves. SPECIAL remains a caller-supplied observed selection, not uniform RNG.
        val WAVES = listOf(
            listOf(Baboon.BRAWLER, Baboon.BRAWLER, Baboon.SHAMAN),
            listOf(Baboon.THROWER, Baboon.THROWER, Baboon.VOLATILE),
            listOf(Baboon.MAGE, Baboon.MAGE, Baboon.CURSED),
            listOf(Baboon.THROWER, Baboon.THROWER, Baboon.SPECIAL, Baboon.SPECIAL, Baboon.SPECIAL),
            listOf(Baboon.MAGE, Baboon.MAGE, Baboon.SHAMAN, Baboon.SPECIAL, Baboon.SPECIAL),
            listOf(Baboon.BRAWLER, Baboon.BRAWLER, Baboon.SHAMAN, Baboon.SPECIAL, Baboon.SPECIAL),
            listOf(Baboon.BRAWLER, Baboon.THROWER, Baboon.SHAMAN, Baboon.CURSED, Baboon.CURSED),
            listOf(Baboon.SHAMAN, Baboon.VOLATILE, Baboon.VOLATILE, Baboon.SPECIAL, Baboon.SPECIAL),
        )
    }
}
