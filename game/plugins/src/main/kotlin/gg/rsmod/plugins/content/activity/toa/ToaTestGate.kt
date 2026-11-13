package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.Player
import mu.KLogging

/**
 * N609 14-11 00:5x (owner 'je ideeen zijn perfect alle 4' - idea 1: an A9 check on live must not cost a full 35-minute raid to see ONE
 * room): the ToA test jumps (::toaroom, ::toaskip, ::toawardens, ::toamech, ::toahp) run on the test server as before, and on LIVE only for
 * the own live check accounts (check78 / check79, owner 11-06: 'check78 (eigen live-controleaccount)'; ADMIN privilege is required on top
 * by every command). Every jump on live is logged, and a raid that used one is remembered here by its id ([wasJumped]): it never enters
 * the public records table (ToaRecordStore.completed). Loot/KC of check78 vanish with 'check78_max.sh down' (the save before the test).
 */
object ToaTestGate : KLogging() {
    private val LIVE_CHECK_ACCOUNTS = setOf("check78", "check79")
    private val jumpedRaids: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Did a test jump happen in raid [raidId]? (asked once at completion, then forgotten). */
    fun wasJumped(raidId: Long): Boolean = jumpedRaids.remove(raidId)

    /** Unit tests only: mark [raidId] as jumped without a player. */
    internal fun markJumpedForTest(raidId: Long) { jumpedRaids += raidId }

    fun testServer(): Boolean = System.getProperty("rsps.testServer") == "true"

    /** May [player] use a ToA test jump here? Test server: yes (as before). Live: only the own check accounts. */
    fun allowed(player: Player): Boolean = testServer() || player.username.lowercase() in LIVE_CHECK_ACCOUNTS

    /** Marks the player's raid as test-driven (never a record) and logs the jump on live. */
    fun jumped(player: Player, what: String) {
        val raidId = ToaLobby.of(player.world).raid(player.uid)?.id
        if (raidId != null) jumpedRaids += raidId
        if (!testServer()) logger.info { "[toa-a9-jump] ${player.username} $what raid=$raidId" }
    }
}
