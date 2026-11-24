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
        // DMM2BOOT-OUDROOD 24-11: 250 published mappings (263 lines, 13 comments) - 159 + the later published rounds 8eeaf49c2 (Archive
        // items + Imbued ancient cape, tx 185343-185624) and 66f93fe59 (Soulflame horn); 120 of them items.
        assertEquals(250, parsed.size)
        assertEquals(120, parsed.keys.count { it.startsWith("item:") })
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
        // DMM2BOOT-OUDROOD 24-11: the Imbued ancient cape is published since the Archive/Soulflame rounds (Nigel refuses it, below).
        assertNotNull(Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.local)
        val pack = assertNotNull(DeadmanStarterPack.ids)
        assertEquals(listOf(24954, 24955, 24956, 24957, 24953, 24950, 24951, 25014),
            listOf(pack.sword, pack.bow, pack.staff, pack.tuna, pack.chronicle, pack.chest, pack.legs, pack.cape))
        assertEquals(7, store.contract.rewards.size)
        store.contract.rewards.forEach { (reward, id) -> assertEquals(parsed["item:${reward.sourceId}"], id) }
        assertEquals(25015, store.contract.exitBlueprint)
        // DMM2BOOT-OUDROOD 24-11: 17 since the Imbued ancient cape was published (its cosmetic 25646 is the 17th skin).
        assertEquals(17, store.contract.skins.size)
        assertEquals(setOf(24980,24981,24982,24983,24984,24985,24986,25001,25002,25003,25004,25005,25006,25007,25008,25009,
            Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.local),
            store.contract.skins.map { it.cosmetic }.toSet())
    }

    @Test fun `Wisp magic swing uses only the source mapping and safely omits a missing animation`() {
        val temp = kotlin.io.path.createTempDirectory("dmm2-wisp-boot").toFile()
        try {
            // Exercise absence even after Claude publishes this optional sequence; do not append a duplicate mapping.
            val published = File(directory, "ids.tsv").readLines()
                .filterNot { it.startsWith("seq\t${Dmm2Boot.WISP_MAGIC_ATTACK}\t") }.joinToString("\n") + "\n"
            for (mapped in listOf(false, true)) {
                File(temp, "ids.tsv").writeText(published + if (mapped) "\nseq\t${Dmm2Boot.WISP_MAGIC_ATTACK}\t30000\n" else "")
                Dmm2Boot.install(temp)
                val f = DeadmanPlayerFixture()
                val actor = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
                val target = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
                every { actor.world } returns f.world
                every { actor.tile } returns Tile(3200, 3200)
                every { target.tile } returns Tile(3201, 3200)
                assertEquals(1, assertNotNull(UndeadThralls.appearance).attack(actor, target, UndeadThralls.Choice.WISP))
                io.mockk.verify(exactly = if (mapped) 1 else 0) { actor.animate(30000, 0, false, true) }
                if (!mapped) io.mockk.verify(exactly = 0) { actor.animate(any(), any(), any(), any()) }
            }
        } finally {
            temp.listFiles()?.forEach { it.delete() }; temp.delete()
            Dmm2Boot.install(directory)
        }
    }

    @Test fun `all three published Nigel armour items follow D2 D4 and preserve their permanent unlocks`() {
        boot()
        val f = DeadmanPlayerFixture()
        val armour = BloodMoney.Armour.values().map { it.item.local!! }.toSet()
        armour.forEachIndexed { slot, id ->
            f.define(id).also { it.tradeable = false; it.cost = 1000 }
            f.inventory[slot] = Item(id)
        }
        every { f.definitions.getNullable(ItemDef::class.java, any()) } answers { f.defs[secondArg()] }
        BloodMoney.Armour.values().forEach { f.attr[it.unlocked] = 1 }
        for (id in armour) for (deep in listOf(false, true)) {
            assertTrue(gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.handles(f.definitions, id))
            val fate = gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.fateOf(f.definitions, Item(id), deep)
            assertEquals(gg.rsmod.plugins.content.mechanics.death.UntradeableFate.LOST_FOR_COINS, fate.fate)
            assertEquals(gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.lostCoins(f.definitions, Item(id)), fate.killerCoins)
        }
        for (context in gg.rsmod.plugins.content.mechanics.death.DeathContext.values()) for (skull in listOf(false, true)) {
            val result = gg.rsmod.plugins.content.mechanics.death.DeathResolver.resolve(f.player, null, skull,
                gg.rsmod.plugins.content.mechanics.death.ItemRiskValueProvider { 1000L }, contextOverride = context).itemRisk
            val kept = if (context == gg.rsmod.plugins.content.mechanics.death.DeathContext.PVM_SAFE && !skull) 3 else 0
            assertEquals(kept, result.protected.size); assertEquals(3 - kept, result.lost.size)
            assertEquals(armour, (result.protected + result.lost).map { it.item.id }.toSet())
            BloodMoney.Armour.values().forEach { assertEquals(1, f.attr[it.unlocked]) }
        }
        for (id in armour) for (deep in listOf(false, true)) {
            val fate = gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection.fateOf(f.definitions, Item(id), deep)
            assertEquals(gg.rsmod.plugins.content.mechanics.death.UntradeableFate.LOST_FOR_COINS, fate.fate)
            assertEquals(600L, fate.killerCoins) // 60% of the fixture value, existing D4 policy.
        }
        assertEquals(3, f.inventory.occupiedSlotCount)
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
                        assertEquals(gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.of(f.player).dropRate + if (carrying && npc == 50) 1.0 else 0.0, rate, "DMM2BOOT-FORTUITY: carrying=$carrying npc=$npc fortuity=${DeadmanTrinkets.fortuity(f.player)} " +
                            "source=${gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.sourceOf(f.world, npc)} tier=${gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.of(f.player).dropRate}")
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
            // DMM2BOOT-OUDROOD 24-11: the Imbued ancient cape (Deadman) is made with Ancient fabric, never by Nigel (Wiki rev15403804;
            // NigelRewardStore.convert) - it must refuse instead of round-tripping.
            if (Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.matches(skin.cosmetic)) {
                f.attr[skin.unlock.unlock!!] = 1
                f.inventory[0] = Item(skin.base)
                assertEquals(NigelRewardStore.Result.UNSUPPORTED, store.convert(f.player, 0, skin.base))
                assertEquals(skin.base, f.inventory[0]!!.id)
                continue
            }
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
