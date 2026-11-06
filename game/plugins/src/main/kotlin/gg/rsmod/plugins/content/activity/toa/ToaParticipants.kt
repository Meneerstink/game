package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID

/** OSRS ToA: an individual death spectates; only loss of the last living entrant wipes the room. */
class ToaParticipants {
    private val entrants = linkedSetOf<PlayerUID>()
    private val lost = mutableSetOf<PlayerUID>()
    private var generation: Int? = null

    fun begin(run: ToaRoomRun) {
        generation = run.generation
        entrants.clear()
        lost.clear()
    }

    fun enter(run: ToaRoomRun, uid: PlayerUID): Boolean {
        if (generation != run.generation || run.status != ToaRoomStatus.RUNNING || uid in lost) return false
        entrants += uid
        return true
    }

    fun participating(run: ToaRoomRun, uid: PlayerUID): Boolean = generation == run.generation && uid in entrants
    fun alive(run: ToaRoomRun, uid: PlayerUID): Boolean = participating(run, uid) && uid !in lost

    /** Includes logout and a teleport out of the encounter; repeated notifications cannot count twice. */
    fun lose(run: ToaRoomRun, uid: PlayerUID): Boolean = alive(run, uid) && lost.add(uid)
    fun wiped(run: ToaRoomRun): Boolean = generation == run.generation && entrants.isNotEmpty() && entrants.all { it in lost }
    fun clear() { generation = null; entrants.clear(); lost.clear() }
}
