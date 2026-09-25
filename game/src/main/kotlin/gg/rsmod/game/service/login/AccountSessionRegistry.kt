package gg.rsmod.game.service.login

import java.util.concurrent.ConcurrentHashMap

/**
 * Audit S-03: the accounts that are logged in or in the middle of logging in or out.
 *
 * The login worker reads a save on its own thread, while the online check and the logout save
 * both run later on the game thread. Without a shared claim, a second client could read the save
 * in the tick between "logout clicked" and "logout save written" and log in with stale data
 * (item handed to a mule comes back: a dupe). An account is claimed here *before* its save is read
 * and released only after the logout save has been written, so a second login in that window is
 * refused instead.
 *
 * Thread-safe; every method may be called from any thread.
 */
class AccountSessionRegistry {
    private val active = ConcurrentHashMap.newKeySet<String>()

    /**
     * Claims [account] for a login (or for another writer of its save, such as the recovery page).
     *
     * @return false when the account is already online or in a login/logout transition.
     */
    fun tryClaim(account: String): Boolean = active.add(normalize(account))

    /** Releases a claim made with [tryClaim]. Releasing an unclaimed account does nothing. */
    fun release(account: String) {
        active.remove(normalize(account))
    }

    fun isActive(account: String): Boolean = active.contains(normalize(account))

    fun size(): Int = active.size

    companion object {
        /**
         * The key an account is known by. Saves are stored under the lower-cased login name; the
         * client and the recovery page may use either spaces or underscores.
         */
        fun normalize(account: String): String = account.trim().lowercase().replace('_', ' ')
    }
}
