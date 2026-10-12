package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner A7 (2026-10-10 / 2026-10-11): a chat line when a timer ends - OSRS text and colour where OSRS has one, else a red line;
 * skull, Tele Block and every buff timer; never the combat / PJ timer.
 */
class TimerExpiryTests {
    @Test
    fun `OSRS texts are word for word, the rest is red`() {
        assertEquals("<col=8f4808>Your stamina potion has expired.</col>", TimerExpiry.STAMINA_EXPIRED)
        assertEquals("<col=4f006f>Your Tele Block will expire in 30 seconds.</col>", TimerExpiry.TELEBLOCK_WARNING)
        assertEquals("<col=4f006f>Your Tele Block has expired.</col>", TimerExpiry.TELEBLOCK_EXPIRED)
        assertEquals("Your Magic Imbue charge has ended.", TimerExpiry.MAGIC_IMBUE_EXPIRED)
        assertEquals("<col=ef1020>Your magical charge fades away.</col>", TimerExpiry.CHARGE_EXPIRED)
        assertTrue(TimerExpiry.SOTD_EXPIRED.contains("Your protection fades away."))
        listOf(TimerExpiry.SKULL_EXPIRED, TimerExpiry.POISON_IMMUNITY_EXPIRED, TimerExpiry.VENOM_IMMUNITY_EXPIRED,
            TimerExpiry.VENGEANCE_READY, TimerExpiry.DIVINE_EXPIRED).forEach {
            assertTrue(it.startsWith("<col=${TimerExpiry.RED}>") && it.endsWith("</col>"), it)
        }
    }

    @Test
    fun `a teleblock arms the 30-second warning with it, a short one has none`() {
        val player = mockk<Player>(relaxed = true)
        val timers = TimerMap()
        every { player.timers } returns timers
        TimerExpiry.teleblock(player, 500)
        assertEquals(500, timers[TELEBLOCK_TIMER])
        assertEquals(450, timers[TimerExpiry.TELEBLOCK_WARNING_TIMER])
        TimerExpiry.teleblock(player, 250)
        assertEquals(200, timers[TimerExpiry.TELEBLOCK_WARNING_TIMER])
        TimerExpiry.teleblock(player, 40)
        assertFalse(timers.has(TimerExpiry.TELEBLOCK_WARNING_TIMER))
    }

    @Test
    fun `every timer of the list has its expiry line, the combat timers none, and every teleblock route arms the warning`() {
        val content = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content").toFile()
        val sources = content.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.toList()
        fun hook(key: String) = sources.any { f -> f.readText().contains(Regex("""on_timer\((\w+\.)*$key\)""")) }
        listOf("STAMINA_TIMER", "TELEBLOCK_TIMER", "TELEBLOCK_WARNING_TIMER", "POISON_IMMUNITY", "VENOM_IMMUNITY", "VENGEANCE_COOLDOWN",
            "STAFF_OF_LIGHT_TIMER", "SKULL_ICON_DURATION_TIMER", "GOD_SPELL_CHARGE_TIMER", "MAGIC_IMBUE_TIMER").forEach {
            assertTrue(hook(it), "no expiry hook for $it")
        }
        assertFalse(sources.any { it.readText().contains("TimerExpiry") && it.readText().contains("on_timer(ACTIVE_COMBAT_TIMER)") })
        // Game code sets a teleblock only through TimerExpiry.teleblock (conformance suites and the HUD tour stage timers directly).
        val direct = sources.filter { f ->
            !f.path.contains("conformance") && !f.name.startsWith("hud_tour") &&
                f.readLines().any { it.contains("timers[TELEBLOCK_TIMER] =") || it.contains("timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] =") }
        }.map(File::getName)
        assertTrue(direct.isEmpty() || direct == listOf("TimerExpiry.kt"), "teleblock set without its warning in: $direct")
    }
}
