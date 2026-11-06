package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID

/** Helpful Spirit wiki: personal choice after paths 2 and 4; advancing forfeits an uncollected offer. */
class ToaCheckpoints {
    private var checkpoint = 0
    val stage: Int get() = checkpoint
    private val claimed = mutableSetOf<PlayerUID>()
    private val forfeited = mutableSetOf<PlayerUID>()

    fun pathsCleared(count: Int): Boolean {
        if (count != 2 && count != 4 || checkpoint >= count) return false
        checkpoint = count
        claimed.clear()
        forfeited.clear()
        return true
    }

    fun offered(uid: PlayerUID): Boolean = checkpoint != 0 && uid !in claimed && uid !in forfeited
    /** Delivery must succeed before consuming the offer (full inventory keeps it available). */
    fun claim(uid: PlayerUID, deliver: () -> Boolean): Boolean {
        if (!offered(uid) || !deliver()) return false
        claimed += uid
        return true
    }

    fun forfeit(uid: PlayerUID) { if (offered(uid)) forfeited += uid }
}
