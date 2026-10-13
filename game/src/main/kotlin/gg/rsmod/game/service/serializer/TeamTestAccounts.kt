package gg.rsmod.game.service.serializer

import gg.rsmod.util.io.SharedDataFiles

/**
 * T3 (owner 2026-10-13: "3-4 vaste testaccounts: max stats, curses, owner OSRS-gear sets, PvP tegen elkaar"): the agent
 * team's fixed test accounts, listed for the agents in C:\RSPS\team\accounts.txt. They exist only on a test server
 * (-Drsps.testServer=true, tools\live\Start-TestServer.ps1): every login is a fresh account that is never saved
 * ([PlayerSerializerService.configureTeamTestAccount]), with the owner rank so the test commands (::gear_*, ::osrsgear,
 * ::master, ::curse) work, and no intro / starter kit / beginner protection. The plugins put on its preset
 * (TeamAccountPresets). On the live server these names are ordinary accounts without any rank.
 */
object TeamTestAccounts {
    val NAMES = listOf("teamtest1", "teamtest2", "teamtest3", "teamtest4")

    /** The owner rank of game.yml (privileges id 3), the same as the sumtest test account. */
    const val PRIVILEGE_ID = 3

    fun isTeamAccount(name: String): Boolean = SharedDataFiles.testServer() && name.trim().lowercase() in NAMES
}
