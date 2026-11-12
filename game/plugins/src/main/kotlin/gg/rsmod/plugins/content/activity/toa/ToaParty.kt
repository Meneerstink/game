package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID

/** OSRS Wiki Tombs of Amascut / Grouping Obelisk: one leader, up to eight members; roster freezes at entry. */
class ToaParty(val id: Long, leader: PlayerUID) {
    companion object { const val CAPACITY = 8 }

    private val roster = linkedSetOf(leader)
    val members: List<PlayerUID> get() = roster.toList()
    var leader: PlayerUID = leader
        private set
    var started = false
        private set
    var settings: ToaRaidSettings = ToaRaidSettings()
        set(value) { check(!started); field = value }

    fun join(member: PlayerUID): Boolean {
        if (started || member in roster || roster.size >= CAPACITY) return false
        return roster.add(member)
    }

    fun leave(member: PlayerUID): Boolean {
        if (started || !roster.remove(member)) return false
        if (leader == member && roster.isNotEmpty()) leader = roster.first()
        return true
    }

    /** N604 (OSRS party details, toa_partydetails_addapplicant 6727): players who applied and wait for the leader's Accept/Reject. */
    private val applied = linkedSetOf<PlayerUID>()
    val applicants: List<PlayerUID> get() = applied.toList()
    /** Kicked players cannot apply again until the leader presses Unblock (774 "Unblock", leader layout of bottom_modify 6730). */
    private val blocked = hashSetOf<PlayerUID>()
    /** "Preferred completions" (774 Completions / 772 sort_completions); 0 = none ("-"). */
    var preferredCompletions = 0
    /** For the party list age column (raids_partylist_age_write 1575: m:ss). */
    val createdAt = System.currentTimeMillis()

    fun canApply(uid: PlayerUID): Boolean = !started && uid !in roster && uid !in applied && uid !in blocked && roster.size < CAPACITY

    fun apply(uid: PlayerUID): Boolean = canApply(uid) && applied.add(uid)

    fun withdraw(uid: PlayerUID): Boolean = applied.remove(uid)

    fun accept(actor: PlayerUID, uid: PlayerUID): Boolean {
        if (actor != leader || uid !in applied || !join(uid)) return false
        applied.remove(uid)
        return true
    }

    fun reject(actor: PlayerUID, uid: PlayerUID): Boolean = actor == leader && applied.remove(uid)

    fun unblock(actor: PlayerUID): Boolean = actor == leader && !started && blocked.isNotEmpty().also { blocked.clear() }

    fun kick(actor: PlayerUID, member: PlayerUID): Boolean =
        (actor == leader && actor != member && leave(member)).also { if (it) blocked += member }

    fun transfer(actor: PlayerUID, member: PlayerUID): Boolean {
        if (started || actor != leader || member !in roster) return false
        leader = member
        return true
    }

    /** Called without suspension on the world thread, after every member has passed the lobby checks. */
    fun start(actor: PlayerUID, eligible: (PlayerUID) -> Boolean): List<PlayerUID>? {
        if (started || actor != leader || roster.isEmpty() || !roster.all(eligible)) return null
        started = true
        return members
    }

    internal fun allocationFailed() { started = false }
}

/** Narrow seam for n462-toa-kern. Names are OSRS invocation names (10-invocations.tsv). */
data class ToaRaidSettings(val raidLevel: Int = 0, val invocations: Set<String> = emptySet()) {
    init {
        require(raidLevel >= 0)
        require(invocations.count { it in ATTEMPTS } <= 1)
    }

    val maximumAttempts: Int? get() = ATTEMPTS.entries.firstOrNull { it.key in invocations }?.value

    companion object {
        // OSRS Wiki Tombs of Amascut, Attempts category; no invocation means unlimited attempts.
        private val ATTEMPTS = mapOf("Try Again" to 10, "Persistence" to 5, "Softcore Run" to 3, "Hardcore Run" to 1)
    }
}
