package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.content.magic.N72Fixture
import gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions
import gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import java.lang.ref.WeakReference
import kotlin.test.*

class MinigameTeleportTests {
    @Test fun `Pest Control requires40combat even though no quest or diary unlock is required`() {
        N72Fixture().use { f ->
            val destination = MinigameTeleport.DESTINATIONS.first { it.name == "Pest Control" }
            every { f.player.combatLevel } returns 39
            assertNotNull(MinigameTeleport.refusal(f.player, MinigameTeleport.SPELLS[0], destination))
            every { f.player.combatLevel } returns 40
            assertNull(MinigameTeleport.refusal(f.player, MinigameTeleport.SPELLS[0], destination))
        }
    }
    @Test fun `all three books cast slowly without runes orXP and share one20minute timer`() {
        MinigameTeleport.SPELLS.zip(listOf(Spellbook.STANDARD, Spellbook.ANCIENT, Spellbook.LUNAR)).forEach { (spell, book) ->
            N72Fixture(book).use { f ->
                f.inventory[0] = Item(Items.KNIFE)
                val start = f.tile
                MinigameTeleport.cast(f.player, spell, MinigameTeleport.DESTINATIONS[0])
                f.tick(20)
                assertEquals(start, f.tile)
                f.tick(7)
                assertEquals(MinigameTeleport.DESTINATIONS[0].tile, f.tile)
                assertEquals(Items.KNIFE, f.inventory[0]?.id)
                assertTrue(f.f.timers[MinigameTeleport.TIMER] in 1995..2000)
                f.player.setSpellbook(Spellbook.LUNAR)
                assertNotNull(MinigameTeleport.refusal(f.player, MinigameTeleport.SPELLS[2]))
                verify(exactly = 0) { f.player.addXp(any(), any<Double>()) }
            }
        }
    }

    @Test fun `wrong book wilderness at level1 deep wildy and teleblock cannot start`() {
        for (failure in 0..3) N72Fixture().use { f ->
            when (failure) {
                0 -> f.player.setSpellbook(Spellbook.LUNAR)
                1 -> f.tile = Tile(3100, 3525) // Shared Wilderness helper: first level-1 tile.
                2 -> f.tile = Tile(3100, 3900)
                3 -> f.f.timers[TELEBLOCK_TIMER] = 100
            }
            if (failure == 1) assertEquals(1, f.tile.getWildernessLevel())
            if (failure == 2) assertTrue(f.tile.getWildernessLevel() > 20)
            val start = f.tile
            assertNotNull(MinigameTeleport.refusal(f.player, MinigameTeleport.SPELLS[0]), "case$failure")
            MinigameTeleport.cast(f.player, MinigameTeleport.SPELLS[0], MinigameTeleport.DESTINATIONS[0])
            f.tick(35)
            assertEquals(start, f.tile, "case$failure")
            assertFalse(f.f.timers.has(MinigameTeleport.TIMER))
        }
    }

    @Test fun `movement hit attack new teleblock or book switch interrupts without spending cooldown`() {
        for (failure in 0..4) N72Fixture().use { f ->
            MinigameTeleport.cast(f.player, MinigameTeleport.SPELLS[0], MinigameTeleport.DESTINATIONS[0])
            f.tick(5)
            when (failure) {
                0 -> f.tile = Tile(3201, 3200)
                1 -> f.f.timers[TELEPORT_COMBAT_TIMER] = 20
                2 -> f.f.attrs[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(mockk<Npc>(relaxed = true))
                3 -> f.f.timers[TELEBLOCK_TIMER] = 100
                4 -> f.player.setSpellbook(Spellbook.LUNAR)
            }
            val start = f.tile
            f.tick(30)
            assertEquals(start, f.tile, "case$failure")
            assertFalse(f.f.timers.has(MinigameTeleport.TIMER))
        }
    }

    @Test fun `activity restrictions pass through the shared teleport gate`() {
        ActivityRestrictions.register { _, action -> if (action == RestrictedAction.TELEPORT) "No teleporting in this activity." else null }
        try { N72Fixture().use { f ->
            val start = f.tile
            MinigameTeleport.cast(f.player, MinigameTeleport.SPELLS[0], MinigameTeleport.DESTINATIONS[0])
            f.tick(35)
            assertEquals(start, f.tile)
            assertFalse(f.f.timers.has(MinigameTeleport.TIMER))
        } } finally { ActivityRestrictions.clearForTests() }
    }

    @Test fun `duplicate casts award one teleport and persistent timer has no privilege death or book bypass`() {
        N72Fixture().use { f ->
            val spell = MinigameTeleport.SPELLS[0]
            MinigameTeleport.cast(f.player, spell, MinigameTeleport.DESTINATIONS[0])
            MinigameTeleport.cast(f.player, spell, MinigameTeleport.DESTINATIONS[1])
            f.tick(35)
            assertEquals(MinigameTeleport.DESTINATIONS[0].tile, f.tile)
            verify(exactly = 1) { f.player.moveTo(any<Tile>()) }
            val saved = f.f.timers.toPersistentTimers().single { it.identifier == "minigame_teleport_delay" }
            assertTrue(saved.tickOffline); assertFalse(saved.resetOnDeath)
            // Production serializer reconstructs the key from these saved fields. Equality reconnects to the same timer.
            val restored = TimerMap()
            restored[TimerKey(saved.identifier, saved.tickOffline, saved.resetOnDeath, saved.tickForward, saved.removeOnZero)] = saved.timeLeft
            assertEquals(f.f.timers[MinigameTeleport.TIMER], restored[MinigameTeleport.TIMER])
            f.f.timers.removeIf { it.resetOnDeath }
            assertTrue(f.f.timers.has(MinigameTeleport.TIMER))
            assertNotNull(MinigameTeleport.refusal(f.player, spell))
        }
    }
}
