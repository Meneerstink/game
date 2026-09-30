package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RuneLite Loot Tracker, server half (owner 2026-09-30): one event per kill, repeated kills aggregate, remembered, reset. */
class LootTrackerTests {
    private val sent = mutableListOf<String>()

    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { player.attr } returns attrs
        val kbd = NpcDef(50).also {
            it.name = "King Black Dragon"
            it.combatLevel = 276
        }
        every { player.world.definitions.getNullable(NpcDef::class.java, 50) } returns kbd
        ClientHud.observer = { _, payload -> sent += payload }
        ClientHud.itemValue = { _, id -> if (id == 536) 1000 else 60_000 }
        ClientHud.itemAlch = { id -> if (id == 536) 0 else 36_000 }
        return player
    }

    @AfterTest
    fun reset() {
        ClientHud.observer = null
    }

    @Test
    fun `one kill is one loot event with merged stacks, prices and the npc level`() {
        val p = player()
        LootTracker.npcKill(p, 50, listOf(Item(536, 1), Item(1149, 1), Item(536, 1)))
        val loot = sent.filter { it.startsWith("loot|") }
        assertEquals(listOf("loot|King Black Dragon|276|1|536,2,1000,0;1149,1,60000,36000"), loot)
    }

    @Test
    fun `repeated kills aggregate in the remembered record and come back on login`() {
        val p = player()
        repeat(3) { LootTracker.npcKill(p, 50, listOf(Item(536, 1))) }
        val saved = LootTracker.load(p).single()
        assertEquals(3, saved.kills)
        assertEquals(3L, saved.items[536])
        sent.clear()
        LootTracker.push(p)
        assertEquals(listOf("lootr|King Black Dragon|276|3|536,3,1000,0"), sent)
    }

    @Test
    fun `a kill without loot still counts and reset clears what is remembered`() {
        val p = player()
        LootTracker.npcKill(p, 50, emptyList())
        assertTrue(sent.any { it == "loot|King Black Dragon|276|1|" })
        LootTracker.reset(p, "King Black Dragon", 276)
        assertTrue(LootTracker.load(p).isEmpty())
        assertTrue("lootclear|King Black Dragon|276" in sent)
    }

    @Test
    fun `quantityToStackSize matches RuneLite`() {
        assertEquals("9,999", LootTracker.stackSize(9_999))
        assertEquals("10K", LootTracker.stackSize(10_000))
        assertEquals("1.23M", LootTracker.stackSize(1_234_567))
        assertEquals("2.14B", LootTracker.stackSize(2_147_483_647))
    }
}
