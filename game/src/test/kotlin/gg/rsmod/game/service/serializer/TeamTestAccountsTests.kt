package gg.rsmod.game.service.serializer

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T3 (owner 2026-10-13): the agent team's fixed test accounts (team\accounts.txt) exist only on a test server
 * (-Drsps.testServer=true): every login is a fresh, never-saved account with the owner rank and its preset. On the live
 * server the same names are ordinary accounts without any rank.
 */
class TeamTestAccountsTests {
    @AfterTest
    fun clear() {
        System.clearProperty("rsps.testServer")
    }

    @Test
    fun `team names are team accounts on a test server only`() {
        assertFalse(TeamTestAccounts.isTeamAccount("teamtest1"), "live server: an ordinary account")
        System.setProperty("rsps.testServer", "true")
        for (name in listOf("teamtest1", "teamtest2", "teamtest3", "TeamTest4")) {
            assertTrue(TeamTestAccounts.isTeamAccount(name), name)
        }
        for (name in listOf("teamtest5", "teamtest", "sumtest", "anudd", "teamtest1x")) {
            assertFalse(TeamTestAccounts.isTeamAccount(name), name)
        }
    }

    @Test
    fun `the serializer builds a team account before it looks for a save and never writes one`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "game/src/main/kotlin").isDirectory }
        val src = File(root, "game/src/main/kotlin/gg/rsmod/game/service/serializer/json/JsonPlayerSerializer.kt").readText()
        val team = src.indexOf("TeamTestAccounts.isTeamAccount(client.loginUsername)")
        val exists = src.indexOf("if (!characterExists(client.loginUsername))")
        assertTrue(team in 0 until exists, "a live save with a team name must never be loaded on the test server")
        assertTrue(src.contains("if (gg.rsmod.util.io.SharedDataFiles.testServer()) return true"), "a test server never writes a save")
        val base = File(root, "game/src/main/kotlin/gg/rsmod/game/service/serializer/PlayerSerializerService.kt").readText()
        val configure = base.substringAfter("fun configureTeamTestAccount(").substringBefore("\n    }")
        assertTrue(configure.contains("TEAM_TEST_ACCOUNT_ATTR"), "the plugins see the team account")
        assertFalse(configure.contains("NEW_ACCOUNT_ATTR"), "no intro, starter kit or beginner protection (PvP tests)")
        assertTrue(configure.contains("TeamTestAccounts.PRIVILEGE_ID"), "owner rank for the test commands")
    }
}
