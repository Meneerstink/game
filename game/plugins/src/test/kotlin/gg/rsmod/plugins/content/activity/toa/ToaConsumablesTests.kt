package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.items.ItemActionGuard
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.*
import kotlin.test.*

class ToaConsumablesTests {
    @Test fun `Expert locust pressure uses the pre-eat stack and only grants one food effect`() {
        mockkObject(ToaLobby.Companion, ToaConsumables)
        every { ToaConsumables.apply(any(), any()) } just Runs
        try {
            // Exact boundary, repeated wipes, and the capped-stack case; other modes retain ordinary consumption.
            for (level in listOf(0, 149, 150, 299, 300, 600)) for (amount in listOf(1, 2, 3, 4, 10, Int.MAX_VALUE)) {
                val f = Consumption(level, amount)
                val flies = level >= 300 && amount > ToaRaid.LOCUSTS_PER_WIPE
                assertTrue(ToaConsumables.use(f.player, ToaConsumables.Kind.HONEY_LOCUST, f.locust, 0))
                assertEquals(if (flies) 1 else amount - 1, f.inventory.getItemCount(f.locust), "level=$level amount=$amount")
                assertEquals(if (flies) listOf("Some of your honey locusts fly away under the immense pressure of the tomb.") else emptyList(), f.messages)
                verify(exactly = 1) { ToaConsumables.apply(f.player, ToaConsumables.Kind.HONEY_LOCUST); f.stats.ate(f.player) }
                assertTrue(f.timers.has(FOOD_DELAY))
                val after = f.inventory.getItemCount(f.locust)
                repeat(5) { assertFalse(ToaConsumables.use(f.player, ToaConsumables.Kind.HONEY_LOCUST, f.locust, 0)) }
                assertEquals(after, f.inventory.getItemCount(f.locust))
                assertEquals(if (flies) 1 else 0, f.messages.size, "double clicks cannot repeat the pressure message")
                // A fully occupied inventory has the same stack semantics and keeps all other items.
                assertEquals(27, f.inventory.getItemCount(385))
            }
        } finally { unmockkObject(ToaLobby.Companion, ToaConsumables) }
    }

    @Test fun `rejected locust use leaves the Expert stack and food statistics untouched`() {
        mockkObject(ToaLobby.Companion, ToaConsumables)
        every { ToaConsumables.apply(any(), any()) } just Runs
        try {
            for (reason in listOf("outside", "nonmember", "delay", "wrong-item", "empty-slot")) {
                val f = Consumption(300, 10)
                when (reason) {
                    "outside" -> every { f.lobby.raid(f.player.uid) } returns null
                    "nonmember" -> every { f.raid.contains(f.player) } returns false
                    "delay" -> f.timers[FOOD_DELAY] = 3
                    "empty-slot" -> f.inventory[1] = null
                }
                val slot = if (reason == "empty-slot") 1 else 0
                assertFalse(ToaConsumables.use(f.player, ToaConsumables.Kind.HONEY_LOCUST,
                    if (reason == "wrong-item") 385 else f.locust, slot), reason)
                assertEquals(10, f.inventory.getItemCount(f.locust), reason)
                assertEquals(if (reason == "empty-slot") 26 else 27, f.inventory.getItemCount(385), reason)
                assertTrue(f.messages.isEmpty(), reason)
                verify(exactly = 0) { ToaConsumables.apply(f.player, any()); f.stats.ate(f.player) }
            }
        } finally { unmockkObject(ToaLobby.Companion, ToaConsumables) }
    }

    @Test fun `Expert pressure leaves potions alone and a remaining locust can be eaten after the delay`() {
        mockkObject(ToaLobby.Companion, ToaConsumables)
        every { ToaConsumables.apply(any(), any()) } just Runs
        try {
            val f = Consumption(300, 4)
            f.inventory[1] = Item(assertNotNull(ToaIds.item(27315)))
            assertTrue(ToaConsumables.use(f.player, ToaConsumables.Kind.NECTAR, f.inventory[1]!!.id, 1))
            assertEquals(ToaIds.item(27317), f.inventory[1]!!.id)
            assertEquals(4, f.inventory.getItemCount(f.locust))
            assertTrue(f.messages.isEmpty())
            f.timers.remove(FOOD_DELAY)
            assertTrue(ToaConsumables.use(f.player, ToaConsumables.Kind.HONEY_LOCUST, f.locust, 0))
            assertEquals(1, f.inventory.getItemCount(f.locust))
            f.timers.remove(FOOD_DELAY)
            assertTrue(ToaConsumables.use(f.player, ToaConsumables.Kind.HONEY_LOCUST, f.locust, 0))
            assertEquals(0, f.inventory.getItemCount(f.locust))
            assertEquals(1, f.messages.size)
            verify(exactly = 2) { f.stats.ate(f.player) }
        } finally { unmockkObject(ToaLobby.Companion, ToaConsumables) }
    }

    @Test fun `dose items are the OSRS ids two apart, the noted id in between`() {
        // cache2686 / our import: Nectar (4) 27315 .. Nectar (1) 27321; Ambrosia (2) 27347, (1) 27349; Honey locust 27351 (stackable).
        assertEquals(listOf(27315, 27317, 27319, 27321), ToaConsumables.osrsDoses(ToaConsumables.Kind.NECTAR))
        assertEquals(listOf(27347, 27349), ToaConsumables.osrsDoses(ToaConsumables.Kind.AMBROSIA))
        assertEquals(listOf(27351), ToaConsumables.osrsDoses(ToaConsumables.Kind.HONEY_LOCUST))
        // Every dose lies inside the family ToaSupplies hands out and cleans up (27314..27351).
        ToaConsumables.Kind.values().flatMap(ToaConsumables::osrsDoses).forEach { assert(it in ToaSupplies.BAG_SOURCE..27351) }
    }

    @Test fun `liquid adrenaline halves the special attack cost in the energy varp's tenths`() {
        val p = mockk<Player>(relaxed = true)
        val timers = TimerMap()
        every { p.timers } returns timers
        assertEquals(250, AttackTab.specialCostTenths(p, 25))
        assertEquals(500, AttackTab.specialCostTenths(p, 50))
        timers[ToaConsumables.ADRENALINE_TIMER] = ToaConsumables.ADRENALINE_TICKS
        // Wiki Liquid adrenaline: 25% -> 12.5%, 75% -> 37.5%.
        assertEquals(125, AttackTab.specialCostTenths(p, 25))
        assertEquals(375, AttackTab.specialCostTenths(p, 75))
    }

    @Test fun `smelling salts run eight minutes in refreshes of 25 ticks, scarab and dressing as published`() {
        assertEquals(32, ToaConsumables.SALTS_EVENTS)
        assertEquals(36, ToaConsumables.SCARAB_EVENTS * ToaConsumables.SCARAB_INTERVAL)
        assertEquals(100, ToaConsumables.SILK_EVENTS * ToaConsumables.SILK_INTERVAL)
        assertEquals(250, ToaConsumables.ADRENALINE_TICKS)
    }

    private class Consumption(level: Int, amount: Int) {
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val lobby = mockk<ToaLobby>()
        val raid = mockk<ToaRaid>()
        val stats = mockk<ToaRaidStats>(relaxed = true)
        val timers = TimerMap()
        val messages = mutableListOf<String>()
        val locust = assertNotNull(ToaIds.item(ToaRaid.HONEY_LOCUST))
        private val definitions = mockk<DefinitionSet>().also { defs ->
            every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg<Int>()).apply { stacks = id == locust } }
        }
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)

        init {
            val attributes = AttributeMap().also { it[ItemActionGuard.STATUS_CAPTURE] = messages }
            every { player.uid } returns PlayerUID(1)
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.inventory } returns inventory
            every { player.timers } returns timers
            every { ToaLobby.of(world) } returns lobby
            every { lobby.raid(player.uid) } returns raid
            every { raid.contains(player) } returns true
            every { raid.settings } returns ToaRaidSettings(raidLevel = level)
            every { raid.stats } returns stats
            inventory.add(locust, amount)
            inventory.add(385, 27)
        }
    }
}
