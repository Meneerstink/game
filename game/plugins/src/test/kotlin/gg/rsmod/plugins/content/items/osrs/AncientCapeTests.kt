package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.wilderness.MageArenaII
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class AncientCapeTests {
    private val assets = AncientCape.Assets(item = { 50_000 + it }, variant = {
        when (it) { "ancient-cape-locked-broken" -> 90_000; "ancient-cape-locked-mangled" -> 90_001; else -> null }
    })
    private class Account {
        val attrs = AttributeMap()
        val player = mockk<Player>(relaxed = true)
        val defs = mockk<DefinitionSet>()
        val inventory = ItemContainer(defs, INVENTORY_KEY)
        init {
            every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { player.attr } returns attrs
            every { player.inventory } returns inventory
        }
    }

    @Test fun `fabric unlock uses earned MA2 and handles repeat and full inventory without another reward credit`() {
        val a = Account()
        assertEquals(AncientCape.Result.NOT_COMPLETED, AncientCape.fabric(a.player, assets))
        a.attrs[MageArenaII.STARTED] = true; a.attrs[MageArenaII.DELIVERED] = 7
        repeat(2) { assertEquals(AncientCape.Result.CONVERTED, AncientCape.fabric(a.player, assets)) }
        assertEquals(2, a.inventory.getItemCount(assets.fabric!!))
        assertNull(a.attrs[MageArenaII.FIRST_IMBUE])
        a.inventory.add(Items.SHARK, 26)
        assertEquals(AncientCape.Result.INVENTORY_FULL, AncientCape.fabric(a.player, assets))
        assertEquals(2, a.inventory.getItemCount(assets.fabric!!))
    }

    @Test fun `all three normal colours convert irreversibly in a full inventory with one fabric each`() {
        AncientCape.GOD_CAPES.forEach { godCape ->
            val a = Account()
            a.inventory.add(godCape); a.inventory.add(assets.fabric!!); a.inventory.add(Items.SHARK, 26)
            assertEquals(AncientCape.Result.CONVERTED, AncientCape.convert(a.player, 0, assets))
            assertEquals(1, a.inventory.getItemCount(assets.cape!!))
            assertEquals(0, a.inventory.getItemCount(godCape))
            assertEquals(0, a.inventory.getItemCount(assets.fabric!!))
            assertEquals(1, a.inventory.freeSlotCount)
            assertEquals(AncientCape.Result.MISSING_ITEMS, AncientCape.convert(a.player, 0, assets))
        }
    }

    @Test fun `all max colours require the matching hood and consume it atomically with fabric`() {
        AncientCape.MAX_HOODS.forEach { (cape, hood) ->
            val a = Account()
            a.inventory.add(cape); a.inventory.add(assets.fabric!!)
            assertEquals(AncientCape.Result.MISSING_HOOD, AncientCape.convert(a.player, 0, assets))
            assertEquals(1, a.inventory.getItemCount(assets.fabric!!))
            a.inventory.add(hood); a.inventory.add(Items.SHARK, 25)
            assertEquals(AncientCape.Result.CONVERTED, AncientCape.convert(a.player, 0, assets))
            assertEquals(1, a.inventory.getItemCount(assets.max!!))
            assertEquals(1, a.inventory.getItemCount(assets.hood!!))
            assertEquals(0, a.inventory.getItemCount(cape))
            assertEquals(0, a.inventory.getItemCount(hood))
            assertEquals(0, a.inventory.getItemCount(assets.fabric!!))
        }
    }

    @Test fun `cancelled or stale recipe missing source and wrong cape cannot consume fabric`() {
        val a = Account()
        a.inventory.add(Items.IMBUED_SARADOMIN_CAPE); a.inventory.add(assets.fabric!!)
        assertEquals(AncientCape.Result.ASSETS_MISSING, AncientCape.convert(a.player, 0, AncientCape.Assets(item = { null })))
        a.inventory[0] = Item(Items.FIRE_CAPE)
        assertEquals(AncientCape.Result.MISSING_ITEMS, AncientCape.convert(a.player, 0, assets))
        assertEquals(1, a.inventory.getItemCount(assets.fabric!!))
        assertEquals(AncientCape.Result.MISSING_ITEMS, AncientCape.convert(a.player, -1, assets))
        a.inventory[0] = Item(Items.IMBUED_SARADOMIN_CAPE_L)
        assertEquals(AncientCape.Result.MISSING_ITEMS, AncientCape.convert(a.player, 0, assets), "locked or damaged capes cannot silently lose their lock")
    }

    @Test fun `mapped ancient capes use existing max knife and death repair families with sourced prices`() {
        val variant = AncientCape.maxVariant(assets)!!
        assertEquals(assets.cape, variant.component)
        assertEquals(assets.max, variant.cape)
        assertEquals(assets.hood, variant.hood)
        val breakables = AncientCape.breakables(assets)
        assertEquals(listOf(96_000, 99_000), breakables.map { it.repairCost })
        assertEquals(listOf(assets.broken, assets.maxBroken), breakables.map { it.brokenId })
        assertTrue(breakables.none { it.breaksAboveTwenty }, "existing N522 deep-Wilderness rules apply")
        val locked = AncientCape.lockables(assets).single()
        assertEquals(assets.cape, locked.baseItemId)
        assertEquals(assets.locked, locked.lockedItemId)
        assertEquals(assets.lockedBroken, locked.brokenItemId)
        assertEquals(assets.lockedMangled, locked.mangledItemId)
        assertTrue(AncientCape.lockables(AncientCape.Assets(item = assets.item) { null }).isEmpty(),
            "do not expose a half-imported lockable with invisible damaged states")
        assertTrue(AncientCape.breakables(AncientCape.Assets(item = { null })).isEmpty())
        assertTrue(AncientCape.lockables(AncientCape.Assets(item = { null })).isEmpty())
        assertNull(AncientCape.maxVariant(AncientCape.Assets(item = { null })))
    }
}
