package gg.rsmod.plugins.content.activity.duel_arena

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.InterfaceHookProbeTool
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-010 C2 deepening: every Duel Arena component the plugin binds carries the matching op label in the 667 cache.
 * (First cache read found Novite's decline ids 107/86 are not buttons on 631/637; the real ones are 51/22.)
 */
class DuelArenaCacheOpsTests {
    private fun op1(interfaceId: Int, component: Int): String? =
        LIBRARY.data(3, interfaceId, component)?.let { InterfaceHookProbeTool.componentOps(it).firstOrNull() }

    @Test
    fun `every bound duel component has the expected cache op`() {
        val I = DuelArenaInterfaces
        val expected = mutableListOf<Triple<Int, Int, String>>()
        DuelRule.values().forEach {
            expected += Triple(I.STAKE_RULES, it.id631, "Toggle")
            expected += Triple(I.FRIENDLY_RULES, it.id637, "Toggle")
        }
        DuelEquipLock.values().forEach {
            expected += Triple(I.STAKE_RULES, it.id631, "Toggle slot")
            expected += Triple(I.FRIENDLY_RULES, it.id637, "Toggle slot")
        }
        expected += Triple(I.STAKE_RULES, I.STAKE_ACCEPT, "Accept")
        expected += Triple(I.STAKE_RULES, I.STAKE_DECLINE, "Decline")
        expected += Triple(I.FRIENDLY_RULES, I.FRIENDLY_ACCEPT, "Accept")
        expected += Triple(I.FRIENDLY_RULES, I.FRIENDLY_DECLINE, "Decline")
        expected += Triple(I.STAKE_CONFIRM, I.STAKE_CONFIRM_ACCEPT, "Accept")
        expected += Triple(I.STAKE_CONFIRM, I.STAKE_CONFIRM_DECLINE, "Decline")
        expected += Triple(I.FRIENDLY_CONFIRM, I.FRIENDLY_CONFIRM_ACCEPT, "Accept")
        expected += Triple(I.FRIENDLY_CONFIRM, I.FRIENDLY_CONFIRM_DECLINE, "Decline")
        expected += Triple(I.SPOILS, I.SPOILS_CLAIM, "Claim")
        I.CHALLENGE_FRIENDLY_BUTTONS.zip(listOf("Select", "Ok")).forEach { (c, l) -> expected += Triple(I.CHALLENGE_SCREEN, c, l) }
        I.CHALLENGE_STAKE_BUTTONS.zip(listOf("Select", "Ok")).forEach { (c, l) -> expected += Triple(I.CHALLENGE_SCREEN, c, l) }
        expected += Triple(I.CHALLENGE_SCREEN, I.CHALLENGE_SEND, "Next-Screen")
        val wrong = expected.mapNotNull { (i, c, label) -> op1(i, c).let { if (it == label) null else "$i:$c expected '$label' got '$it'" } }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
        // The spoils item layer is the one carrying item ops.
        assertTrue(LIBRARY.data(3, I.SPOILS, I.SPOILS_ITEMS) != null, "634:${I.SPOILS_ITEMS} missing")
    }

    companion object {
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun load() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        }

        @AfterClass
        @JvmStatic
        fun close() = LIBRARY.close()
    }
}
