package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N517 / N503 D9 (owner 2026-11-01): the Deadman chest after a PvP death - 78,000 coins + skull, one an hour. */
class PvpRespawnChestTests {
    private fun player(): Player {
        val attrs = AttributeMap()
        return mockk<Player>(relaxed = true).also { every { it.attr } returns attrs }
    }

    @Test
    fun `the owner's numbers - 78,000 coins, own item 24623, one chest per hour`() {
        assertEquals(78_000, PvpRespawnChest.COINS)
        assertEquals(24623, PvpRespawnChest.ITEM)
        assertEquals(60L * 60L * 1000L, PvpRespawnChest.COOLDOWN_MS)
    }

    @Test
    fun `a first death gives a chest, a second within the hour none, after the hour again`() {
        val p = player()
        val t = 1_000_000_000L
        assertTrue(PvpRespawnChest.eligible(p, t))
        p.attr[PvpRespawnChest.LAST_GRANTED] = t
        assertFalse(PvpRespawnChest.eligible(p, t + 1))
        assertFalse(PvpRespawnChest.eligible(p, t + PvpRespawnChest.COOLDOWN_MS - 1))
        assertTrue(PvpRespawnChest.eligible(p, t + PvpRespawnChest.COOLDOWN_MS))
        // A clock that went backwards (server moved) never locks the chest for good.
        assertTrue(PvpRespawnChest.eligible(p, t - 5))
    }

    @Test
    fun `the cooldown is saved with the player, so a relog or restart does not reset it`() {
        assertEquals("pvp_respawn_chest_at", PvpRespawnChest.LAST_GRANTED.persistenceKey)
    }

    @Test
    fun `the chest is its own untradeable item, the new-player pack keeps its 1m`() {
        val yml = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")
        val entry = yml.substringAfter("- id: 24623\n").substringBefore("\n- id:")
        assertTrue(entry.contains("name: \"Deadman chest\""), entry)
        assertTrue(entry.contains("tradeable: false"), entry)
        assertEquals(1_000_000, NewPlayerStart.STARTER_CASH)
    }

    @Test
    fun `only a player-killed victim gets one - the death plugin grants it after a resolved player killer`() {
        val plugin =
            File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/death.plugin.kts").readText()
        assertTrue(plugin.contains("if (executed && pvpDeath && killer != null) {"), "grant guarded by a resolved player killer")
        // An alt / staff kill (D10) nulls the killer before this point.
        assertTrue(plugin.contains("val killer = if (abuseKill) null else playerKiller"))
    }
}
