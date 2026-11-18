package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player

/**
 * CoX release gate + test commands (same rule as ToaTestGate, owner 14-11 idea 1): the test server as before; on LIVE only the own check
 * accounts check78 / check79 (owner 11-06). Until the raid is complete and live-proven the entrance itself also uses this gate.
 */
object CoxTestGate {
    private val LIVE_CHECK_ACCOUNTS = setOf("check78", "check79")

    fun testServer(): Boolean = System.getProperty("rsps.testServer") == "true"

    fun allowed(player: Player): Boolean = testServer() || player.username.lowercase() in LIVE_CHECK_ACCOUNTS

    /** The entrance steps and the recruiting board: everyone once the raid is released (CoxRelease.OPEN), else as [allowed]. */
    fun entrance(player: Player): Boolean = CoxRelease.OPEN || allowed(player)
}
