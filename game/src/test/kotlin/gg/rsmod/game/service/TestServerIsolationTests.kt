package gg.rsmod.game.service

import gg.rsmod.util.io.SharedDataFiles
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * ROOT_CAUSES #109 guard for the agent's live test server (owner 2026-10-02, tools\live\Start-TestServer.ps1): with
 * -Drsps.testServer=true the server is a test server, so world-state files resolve to scratch copies and the live server's
 * ban list, registries and player saves are never written.
 */
class TestServerIsolationTests {
    @AfterTest
    fun clear() {
        System.clearProperty("rsps.testServer")
    }

    @Test
    fun `the live test server switch makes a test server`() {
        assertFalse(SharedDataFiles.testServer(), "no test-server property set at the start")
        System.setProperty("rsps.testServer", "true")
        assertTrue(SharedDataFiles.testServer())
    }

    @Test
    fun `a test server writes the ban list and registries into its scratch folder, never the live files`() {
        System.setProperty("rsps.testServer", "true")
        for (path in listOf("banned_players.txt", "data/clans.txt", "data/friends_chat.json", "data/ge/offers.json")) {
            val resolved = SharedDataFiles.of(path)
            assertNotEquals(File(path).absoluteFile, resolved.absoluteFile, path)
            assertTrue(resolved.absolutePath.startsWith(File(System.getProperty("java.io.tmpdir")).absolutePath), path)
        }
    }

    @Test
    fun `a sibling of a scratch file is seeded from the real sibling (GE prices and history beside offers)`() {
        val dir = File("build/tmp/sibling-isolation")
        dir.mkdirs()
        File(dir, "offers.json").writeText("[]")
        File(dir, "guide_prices.json").writeText("{\"4151\":[1500000]}")
        System.setProperty("rsps.testServer", "true")
        val offers = SharedDataFiles.of("build/tmp/sibling-isolation/offers.json")
        val prices = SharedDataFiles.sibling(offers, "guide_prices.json")
        assertNotEquals(File(dir, "guide_prices.json").absoluteFile, prices.absoluteFile)
        assertTrue(prices.readText().contains("1500000"), "seeded from the real file")
        prices.writeText("{}")
        assertTrue(File(dir, "guide_prices.json").readText().contains("1500000"), "the real file is never written")
        assertFalse(SharedDataFiles.sibling(offers, "history.json").exists(), "no real sibling -> nothing seeded")
        System.clearProperty("rsps.testServer")
        assertTrue(SharedDataFiles.sibling(File(dir, "offers.json"), "guide_prices.json").absoluteFile == File(dir, "guide_prices.json").absoluteFile, "live: the file next to it")
        offers.parentFile.deleteRecursively()
        dir.deleteRecursively()
    }

    @Test
    fun `the ban list and account recovery go through the test-server switch`() {
        // Source-level check of the two writers outside SharedDataFiles.of found in the 2026-10-02 sweep.
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "game/src/main/kotlin").isDirectory }
        val bans = File(root, "game/src/main/kotlin/gg/rsmod/game/service/world/BannedPlayers.kt").readText()
        assertTrue(bans.contains("SharedDataFiles.of(\"banned_players.txt\")"), "BannedPlayers resolves its file through SharedDataFiles")
        val recovery = File(root, "game/src/main/kotlin/gg/rsmod/game/service/recovery/AccountRecoveryService.kt").readText()
        assertTrue(recovery.contains("SharedDataFiles.testServer()"), "AccountRecoveryService is off on a test server")
    }
}
