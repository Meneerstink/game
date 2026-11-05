package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.*
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.quests.tab.DropSources
import io.mockk.*
import java.security.SecureRandom
import kotlin.test.*

class WildernessSharedLootTests {
    @Test fun `all three group bosses distribute one unique with their own table bones and unscaled unique quantity`() {
        val f = WildernessBossTestSupport.Fixture()
        for ((id, unique) in listOf(VenenatisCombat.NPC to Items.VOIDWAKER_GEM,
            OldWildernessBosses.CALLISTO to Items.VOIDWAKER_HILT, OldWildernessBosses.VETION_REBORN to Items.VOIDWAKER_BLADE)) {
            val other = mockk<Player>(relaxed = true)
            every { other.world } returns f.world
            every { other.attr } returns AttributeMap()
            every { other.combatLevel } returns 126
            f.players.entries[1] = f.player
            f.players.entries[2] = other
            val npc = f.npc(id)
            npc.damageMap.add(f.player, 99)
            npc.damageMap.add(other, 1)
            every { f.world.randomDouble() } returns 0.9999
            val received = mutableMapOf<Player, List<Item>>()
            val previous = DropTableFactory.prng
            mockkObject(DropTableFactory)
            try {
                DropTableFactory.prng = mockk<SecureRandom>().also { rng ->
                    every { rng.nextInt(any<Int>()) } returns 0
                    every { rng.nextDouble() } returns 0.0
                }
                every { DropTableFactory.rollMainReplacement(any(), any(), any()) } returns listOf(Item(unique))
                every { DropTableFactory.giveDrop(any(), any(), any(), any(), any(), any(), any()) } answers {
                    assertFalse(arg(6), "group recipients must not pass through LootShare a second time")
                    assertEquals(WildernessBossLoot.contract(id)!!.rewardNpc, thirdArg())
                    received[secondArg()] = arg(4)
                }
                WildernessBossLoot.drop(npc)
                assertEquals(2, received.size)
                assertEquals(1, received.values.flatten().count { it.id == unique })
                assertTrue(received.getValue(other).any { it.id == unique && it.amount == 1 })
                assertTrue(received.getValue(f.player).any { it.id == Items.BIG_BONES })
                assertTrue(received.getValue(other).any { it.id == Items.BONES })
                assertTrue(received.getValue(f.player).any { it.id != Items.BIG_BONES }, "non-unique owner retains its main roll")
            } finally { DropTableFactory.prng = previous; unmockkObject(DropTableFactory) }
        }
        assertNull(WildernessBossLoot.contract(OldWildernessBosses.VETION), "intermediate form gives no group reward")
    }

    @Test fun `Scorpia and every sourced boss use the same personal clue chance in roll viewer and item search`() {
        val f = WildernessBossTestSupport.Fixture()
        val ringId = 999522
        f.world.definitions // definitions remain read-only; mock the ring definition lookup only.
        val defs = f.world.definitions
        mockkObject(defs)
        try {
            every { defs.get(ItemDef::class.java, ringId) } returns ItemDef(ringId).apply { name = "Ring of wealth (i)" }
            every { f.player.tile } returns Tile(3234, 10332)
            val ring = Item(ringId)
            for (id in WildernessBossClues.NPCS) {
                val source = DropSources.forNpc(f.player, id)!!
                every { f.player.equipment[EquipmentType.RING.id] } returns null
                val plain = DropSources.view(f.player, source, 1.0)
                val clue = plain.lines.single { it.item in setOf(Items.CLUE_SCROLL_HARD, Items.CLUE_SCROLL_ELITE) }
                every { f.player.equipment[EquipmentType.RING.id] } returns ring
                val boosted = DropSources.view(f.player, source, 1.0).lines.single { it.item == clue.item }
                assertEquals(clue.normal * 2, boosted.normal, 1e-12, source.name)
                val table = DropTableFactory.tables(f.player, id)!!.single { it.name == "hard_clue" || it.name == "elite_clue" }
                assertEquals(boosted.normal, DropTableFactory.lineChances(table, 1.0).first(), 1e-12)
                every { f.player.equipment[EquipmentType.RING.id] } returns null
                assertEquals(clue.normal, DropSources.view(f.player, source, 1.0).lines.single { it.item == clue.item }.normal)
            }
            assertFalse(50 in WildernessBossClues.NPCS, "KBD lair is outside the Wilderness")
        } finally { unmockkObject(defs); DropSources.reset() }
    }
}
