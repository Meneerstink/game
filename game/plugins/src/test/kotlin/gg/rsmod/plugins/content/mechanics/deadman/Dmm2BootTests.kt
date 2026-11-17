package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.quests.tab.DropViewer
import io.mockk.every
import gg.rsmod.plugins.content.starter.DeadmanStarterPack
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import java.io.File
import kotlin.test.*

/** Actual published transaction output, not synthetic source/local IDs. No cache writes or running server. */
class Dmm2BootTests {
    private val directory = File("../../data/cfg/dmm2")

    private fun boot(): NigelRewardStore {
        assertTrue(File(directory, "ids.tsv").isFile, "Run from game/plugins, as Gradle does")
        Dmm2Boot.install(directory)
        return assertNotNull(NigelRewardStore.active)
    }

    @Test fun `published transaction mappings install starter rewards NPCs and exact cosmetic states`() {
        val parsed = Dmm2Ids.parse(File(directory, "ids.tsv").readLines())
        assertEquals(151, parsed.size)
        assertEquals(86, parsed.keys.count { it.startsWith("item:") })
        Dmm2Ids.install(emptyList()) // A prior lookup must not hide the subsequently published file.
        val store = boot()
        assertEquals(24949, Dmm2Ids.ItemId.BLOOD_MONEY.local)
        assertEquals(15316, Dmm2Ids.npc(8391))
        assertEquals(15314, Dmm2Ids.npc(6516))
        assertEquals(15315, Dmm2Ids.npc(6485))
        assertEquals(1203, Dmm2Ids.interfaceId(232))
        assertEquals(1204, Dmm2Ids.interfaceId(230))
        assertEquals(1205, Dmm2Ids.interfaceId(819))
        assertEquals(1206, Dmm2Ids.interfaceId(806))
        assertSame(Dmm2SafeDepositUi, Dmm2Interactions.safeUi)
        assertSame(Dmm2RewardUi, Dmm2Interactions.rewardUi)
        assertNull(Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.local)
        val pack = assertNotNull(DeadmanStarterPack.ids)
        assertEquals(listOf(24954, 24955, 24956, 24957, 24953, 24950, 24951, 25014),
            listOf(pack.sword, pack.bow, pack.staff, pack.tuna, pack.chronicle, pack.chest, pack.legs, pack.cape))
        assertEquals(7, store.contract.rewards.size)
        store.contract.rewards.forEach { (reward, id) -> assertEquals(parsed["item:${reward.sourceId}"], id) }
        assertEquals(25015, store.contract.exitBlueprint)
        assertEquals(16, store.contract.skins.size)
        assertEquals(setOf(24980,24981,24982,24983,24984,24985,24986,25001,25002,25003,25004,25005,25006,25007,25008,25009),
            store.contract.skins.map { it.cosmetic }.toSet())
    }

    @Test fun `drop viewer defaults use the actual roll rate for bosses and ordinary NPCs independently`() {
        boot()
        val f = DeadmanPlayerFixture()
        val fortuity = assertNotNull(Dmm2Ids.ItemId.FORTUITY.local)
        f.define(fortuity); f.define(Items.DRAGON_PICKAXE); f.define(Items.COINS_995)
        val npcs = mapOf(50 to NpcDef(50).also { it.name = "King black dragon" },
            1643 to NpcDef(1643).also { it.name = "Infernal Mage" })
        every { f.definitions.getNullable(ItemDef::class.java, any()) } answers { f.defs[secondArg()] }
        every { f.definitions.getNullable(NpcDef::class.java, any()) } answers { npcs[secondArg()] }
        every { f.world.plugins.allNpcCombatDefs() } returns emptyMap()
        every { f.player.combatLevel } returns 3
        every { f.player.tile } returns Tile(3200, 3200)
        val table = DropTableFactory.build { main {
            total(128); obj(Items.DRAGON_PICKAXE, slots = 1); obj(Items.COINS_995, slots = 127)
        } }
        DropTableFactory.register(table, 50, 1643)
        DropSources.reset()
        try {
            repeat(5) {
                for (carrying in listOf(true, false)) {
                    f.inventory[0] = if (carrying) Item(fortuity) else null
                    for (npc in listOf(50, 1643)) {
                        val source = DropSources.Source("npc:$npc", npcs.getValue(npc).name, npc, intArrayOf(npc), 1,
                            DropSources.Group.MONSTERS, DropSources.Kind.NPC, npc)
                        val rate = DropTableFactory.killRate(f.player, npcId = npc)
                        assertEquals(if (carrying && npc == 50) 4.0 else 3.0, rate)
                        assertEquals(rate, DropViewer.sourceRate(f.player, source))
                        val line = DropSources.view(f.player, source).lines.single { it.item == Items.DRAGON_PICKAXE }
                        val built = assertNotNull(DropTableFactory.tables(f.player, npc)).single()
                        assertEquals(DropTableFactory.lineChances(built, rate)[0], line.your, 1e-12)
                        assertEquals(1.0 / 128.0, line.normal, 1e-12)
                    }
                }
            }
        } finally { DropSources.reset() }
    }

    @Test fun `all actual skins round trip without losing charges or creating items`() {
        val store = boot()
        val f = DeadmanPlayerFixture()
        for (skin in store.contract.skins) {
            f.define(skin.base); f.define(skin.cosmetic)
            f.attr[skin.unlock.unlock!!] = 1
            f.inventory[0] = Item(skin.base).putAttr(ItemAttribute.CHARGES, 137)
            repeat(5) {
                assertEquals(NigelRewardStore.Result.OK, store.convert(f.player, 0, skin.base))
                assertEquals(skin.cosmetic, f.inventory[0]!!.id)
                assertEquals(137, f.inventory[0]!!.getAttr(ItemAttribute.CHARGES))
                assertEquals(skin.base, DeadmanCosmetics.base(skin.cosmetic))
                assertEquals(NigelRewardStore.Result.OK, store.convert(f.player, 0, skin.cosmetic))
                assertEquals(skin.base, f.inventory[0]!!.id)
                assertEquals(137, f.inventory[0]!!.getAttr(ItemAttribute.CHARGES))
                assertEquals(1, f.inventory.occupiedSlotCount)
            }
        }
    }

    @Test fun `actual reward IDs buy and refund all seven prices without point or item inflation`() {
        val store = boot()
        val f = DeadmanPlayerFixture()
        f.attr[StoreCatalogue.Currency.DEADMAN.attr] = 100_000
        for ((reward, id) in store.contract.rewards) {
            f.define(id)
            assertEquals(NigelRewardStore.Result.OK, store.buy(f.player, reward))
            assertEquals(100_000 - reward.price, NigelRewardStore.balance(f.player))
            assertEquals(id, f.inventory[0]!!.id)
            assertEquals(NigelRewardStore.Result.OK, store.sellBack(f.player, 0, id))
            assertEquals(100_000, NigelRewardStore.balance(f.player))
            assertEquals(0, f.inventory.occupiedSlotCount)
            assertEquals(NigelRewardStore.Result.ITEM_CHANGED, store.sellBack(f.player, 0, id))
        }
    }
}
