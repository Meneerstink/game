package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.plugins.content.mechanics.hud.ClientHudSync
import gg.rsmod.plugins.content.mechanics.restoration.BoostedCombatStats
import gg.rsmod.plugins.content.mechanics.restoration.RestorationRates
import io.mockk.*
import java.lang.ref.WeakReference
import kotlin.test.*

/** N601: Kephri -> Osmumten reuses an index. RuneLite OpponentInfoPlugin retains the Actor, with a 5s hold. */
class ToaOpponentHudTests {
    private class Fixture {
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        var cycle = 100
        val sent = mutableListOf<String>()
        init {
            every { player.world } returns world
            every { player.attr } returns AttributeMap()
            every { player.tile } returns Tile(6400, 256)
            every { world.currentCycle } answers { cycle }
            every { world.gameContext.cycleTime } returns 600
            ClientHudSync.reset(player)
        }
        fun npc() = mockk<Npc>(relaxed = true).also {
            every { it.index } returns 5
            every { it.isSpawned() } returns true
            every { it.tile } returns player.tile
            every { it.getMaximumLifepoints() } returns 30
            every { it.getCurrentLifepoints() } returns 20
        }
        fun otherPlayer() = mockk<Player>(relaxed = true).also {
            every { it.index } returns 5
            every { it.isOnline } returns true
            every { it.tile } returns player.tile
            every { it.getMaximumLifepoints() } returns 30
            every { it.getCurrentLifepoints() } returns 20
        }
        fun focus(pawn: Pawn) { player.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(pawn) }
        fun tick() { ClientHudSync.tick(player); cycle++ }
    }

    private fun checkHud(block: (Fixture) -> Unit) {
        val previous = ClientHud.observer
        mockkObject(BoostedCombatStats, RestorationRates)
        every { BoostedCombatStats.ticksUntilDecay(any()) } returns 100
        every { RestorationRates.ticksUntilLoweredRestore(any()) } returns -1
        every { RestorationRates.ticksUntilBoostedRestore(any()) } returns -1
        try {
            val f = Fixture()
            ClientHud.observer = { player, payload -> if (player === f.player && payload.startsWith("opp|")) f.sent += payload }
            block(f)
        } finally {
            ClientHud.observer = previous
            unmockkObject(BoostedCombatStats, RestorationRates)
        }
    }

    @Test fun `new NPC in a reused slot sends a new binding even with identical health`() = checkHud { f ->
        val first = f.npc()
        f.focus(first); f.tick(); f.tick()
        assertEquals(listOf("opp|n|5|170|20|30"), f.sent, "unchanged actor must not spam the client")
        every { first.isSpawned() } returns false
        f.player.attr.remove(COMBAT_TARGET_FOCUS_ATTR); f.tick()
        f.focus(f.npc()); f.tick(); f.tick()
        assertEquals(listOf("opp|n|5|170|20|30", "opp|n|5|170|20|30"), f.sent)
    }

    @Test fun `despawning an NPC preserves the existing five second hold`() = checkHud { f ->
        val first = f.npc()
        f.focus(first); f.tick()
        every { first.isSpawned() } returns false
        f.player.attr.remove(COMBAT_TARGET_FOCUS_ATTR)
        repeat(9) { f.tick() }
        assertEquals(listOf("opp|n|5|170|20|30"), f.sent)
        f.tick()
        assertEquals(listOf("opp|n|5|170|20|30", "opp|none"), f.sent)
    }

    @Test fun `reused player slot also refreshes an identical health payload`() = checkHud { f ->
        f.focus(f.otherPlayer()); f.tick(); f.tick()
        f.focus(f.otherPlayer()); f.tick(); f.tick()
        assertEquals(listOf("opp|p|5|170", "opp|p|5|170"), f.sent)
    }
}
