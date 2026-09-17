package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.refreshBonuses
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PvpDeathBreakables]: an unprotected Avernic defender never becomes killer loot in a Wilderness
 * death; it breaks, stays with the victim and the killer gets the 600,000-coin repair cost once.
 */
class PvpDeathBreakablesTests {
    @Test
    fun `wilderness death breaks an inventory Avernic defender in place and drops the repair cost once`() {
        val victim = newPlayer()
        val killer = newPlayer()
        val world = mockk<World>(relaxed = true)
        victim.inventory[3] = Item(Items.AVERNIC_DEFENDER, 1)
        victim.inventory[4] = Item(Items.ABYSSAL_WHIP, 1)
        val lost =
            listOf(
                DeathSlotItem(DeathContainerSource.INVENTORY, 3, Item(Items.AVERNIC_DEFENDER, 1)),
                DeathSlotItem(DeathContainerSource.INVENTORY, 4, Item(Items.ABYSSAL_WHIP, 1)),
            )
        val resolved = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost))

        val (result, breaking) = PvpDeathBreakables.split(resolved)
        assertEquals(listOf(Items.ABYSSAL_WHIP), result.itemRisk.lost.map { it.item.id }, "the defender must never be dropped as loot")
        assertEquals(listOf(Items.AVERNIC_DEFENDER), breaking.map { it.item.id })

        assertEquals(1, PvpDeathBreakables.execute(world, result, breaking))
        assertEquals(Items.AVERNIC_DEFENDER_BROKEN, victim.inventory[3]?.id)
        verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == Items.COINS_995 && it.amount == 600_000 }) }
        assertEquals(0, PvpDeathBreakables.execute(world, result, breaking), "a second execution finds no defender to break")
    }

    @Test
    fun `an equipped Avernic defender is unequipped and kept broken in the inventory`() {
        val victim = newPlayer()
        val world = mockk<World>(relaxed = true)
        victim.equipment[5] = Item(Items.AVERNIC_DEFENDER, 1)
        val lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 5, Item(Items.AVERNIC_DEFENDER, 1)))
        val (result, breaking) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, null, DeathItemRiskResult(0, emptyList(), lost)))
        // refreshBonuses writes interface varcs/components a mocked player does not have.
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            every { victim.refreshBonuses() } just Runs

            PvpDeathBreakables.execute(world, result, breaking)

            assertNull(victim.equipment[5])
            assertEquals(1, victim.inventory.getItemCount(Items.AVERNIC_DEFENDER_BROKEN))
            verify(exactly = 0) { world.spawn(any<GroundItem>()) }
            verify(exactly = 1) { victim.refreshBonuses() }
        } finally {
            unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
    }

    @Test
    fun `every breakable item breaks in place and pays exactly its sourced repair cost`() {
        assertEquals(
            mapOf(
                Items.AVERNIC_DEFENDER to 600_000, Items.INFERNAL_CAPE to 225_000, Items.IMBUED_SARADOMIN_CAPE to 0, Items.IMBUED_GUTHIX_CAPE to 0, Items.IMBUED_ZAMORAK_CAPE to 0,
                // Ava's assembler (raw wiki): becomes broken and stays with the player; no coins for the killer are stated.
                Items.AVAS_ASSEMBLER to 0,
                // Batch capes (item pages): imbued max capes, assembler max capes, Masori assembler and Dizana's max cape break in place;
                // only Perdu's repair cost is stated, no coins for the killer.
                Items.IMBUED_SARADOMIN_MAX_CAPE to 0, Items.IMBUED_GUTHIX_MAX_CAPE to 0, Items.IMBUED_ZAMORAK_MAX_CAPE to 0,
                Items.ASSEMBLER_MAX_CAPE to 0, Items.MASORI_ASSEMBLER to 0, Items.MASORI_ASSEMBLER_MAX_CAPE to 0, Items.DIZANAS_MAX_CAPE to 0,
            ),
            PvpDeathBreakables.ALL.associate { it.itemId to it.killerCoins },
        )
        assertEquals(
            // Sourced Perdu repair costs (OSRS_IMPORT_MASTER.yml "Batch capesrings" / "Batch assembler" / "Batch capes" / pilot).
            mapOf(
                Items.AVERNIC_DEFENDER to 600_000, Items.INFERNAL_CAPE to 225_000,
                Items.IMBUED_SARADOMIN_CAPE to 96_000, Items.IMBUED_GUTHIX_CAPE to 96_000, Items.IMBUED_ZAMORAK_CAPE to 96_000,
                Items.AVAS_ASSEMBLER to 240_000,
                Items.IMBUED_SARADOMIN_MAX_CAPE to 99_000, Items.IMBUED_GUTHIX_MAX_CAPE to 99_000, Items.IMBUED_ZAMORAK_MAX_CAPE to 99_000,
                Items.ASSEMBLER_MAX_CAPE to 240_000, Items.MASORI_ASSEMBLER to 240_000, Items.MASORI_ASSEMBLER_MAX_CAPE to 240_000,
                Items.DIZANAS_MAX_CAPE to 400_000,
            ),
            PvpDeathBreakables.ALL.associate { it.itemId to it.repairCost },
        )
        PvpDeathBreakables.ALL.forEach { breakable ->
            val victim = newPlayer()
            val killer = newPlayer()
            val world = mockk<World>(relaxed = true)
            victim.inventory[2] = Item(breakable.itemId, 1)
            val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 2, Item(breakable.itemId, 1)))
            val (result, converting) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost)))

            PvpDeathBreakables.execute(world, result, converting)

            assertEquals(breakable.brokenId, victim.inventory[2]?.id, "${breakable.itemId} breaks in place")
            val coinDrops = if (breakable.killerCoins > 0) 1 else 0
            verify(exactly = coinDrops) { world.spawn(match<GroundItem> { it.item == Items.COINS_995 && it.amount == breakable.killerCoins }) }
            verify(exactly = 0) { world.spawn(match<GroundItem> { it.item == breakable.itemId }) }
        }
    }

    @Test
    fun `every item with a tradeable ornament or colour kit is dropped as base item plus kit on a wilderness death`() {
        // "Items Kept on Death": tradeable ornament kits -> "Dropped to the PKer as the tradeable, non-ornamented item. Also drops
        // the tradeable ornament kit to the PKer." Rows with an untradeable kit are covered by the next test.
        gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ALL.filter { it.pvpConvert }.forEach { ornament ->
            val victim = newPlayer()
            val killer = newPlayer()
            val world = mockk<World>(relaxed = true)
            victim.inventory[0] = Item(ornament.ornamented, 1)
            val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(ornament.ornamented, 1)))
            val (result, converting) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost)))
            assertTrue(result.itemRisk.lost.isEmpty(), "${ornament.ornamented} must not drop as the ornamented item")

            PvpDeathBreakables.execute(world, result, converting)

            assertNull(victim.inventory[0])
            verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == ornament.base && it.amount == 1 }) }
            verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == ornament.kit && it.amount == 1 }) }
            verify(exactly = 0) { world.spawn(match<GroundItem> { it.item == ornament.ornamented }) }
        }
    }

    @Test
    fun `ornamented items without the base plus kit rule keep the default death handling`() {
        val kept = gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ALL.filterNot { it.pvpConvert }
        assertEquals(
            setOf(
                // No "Items Kept on Death" rule covers a tradeable item carrying an untradeable kit (SOURCE_GAP, OSRS_IMPORT_MASTER.yml):
                // the elder chaos (or) and Dagon'hai (or) pieces stay in the normal lost list instead of being converted.
                Items.ELDER_CHAOS_TOP_OR, Items.ELDER_CHAOS_ROBE_OR, Items.ELDER_CHAOS_HOOD_OR,
                Items.DAGONHAI_HAT_OR, Items.DAGONHAI_ROBE_TOP_OR, Items.DAGONHAI_ROBE_BOTTOM_OR,
                // Heavy ballista (or): the Heavy ballista ornament kit is an untradeable Bounty Hunter reward (same SOURCE_GAP as above).
                Items.HEAVY_BALLISTA_OR,
                // Elidinis' ward (or): OSRS Wiki "Elidinis' ward (or)" - on a PvP death "the Menaphite ornament kit will be placed in their
                // gravestone" (no base + kit drop to the PKer).
                Items.ELIDINIS_WARD_OR,
                // Dragon defender (t): tradeable kit, but OSRS Wiki "Dragon defender (t)" - on a PvP death "it will remain in the player's
                // inventory, but will become broken" (the defender rule, not the base + kit drop); no defender breaks here yet.
                Items.DRAGON_DEFENDER_T,
            ),
            kept.map { it.ornamented }.toSet(),
        )
        kept.forEach { ornament ->
            val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(ornament.ornamented, 1)))
            val (result, converting) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, newPlayer(), newPlayer(), DeathItemRiskResult(0, emptyList(), lost)))
            assertTrue(converting.isEmpty(), "${ornament.ornamented} is not converted")
            assertEquals(lost, result.itemRisk.lost)
        }
    }

    @Test
    fun `a charged or full powered staff drops as its uncharged id on an unprotected wilderness death, charges lost`() {
        listOf(
            Items.TRIDENT_OF_THE_SEAS to Items.UNCHARGED_TRIDENT,
            Items.TRIDENT_OF_THE_SEAS_FULL to Items.UNCHARGED_TRIDENT,
            Items.TRIDENT_OF_THE_SEAS_E to Items.UNCHARGED_TRIDENT_E,
            Items.TRIDENT_OF_THE_SWAMP to Items.UNCHARGED_TOXIC_TRIDENT,
            Items.SANGUINESTI_STAFF to Items.SANGUINESTI_STAFF_UNCHARGED,
        ).forEach { (charged, uncharged) ->
            val victim = newPlayer()
            val killer = newPlayer()
            val world = mockk<World>(relaxed = true)
            victim.equipment[3] = Item(charged, 1)
            val lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 3, Item(charged, 1)))
            val (result, breaking) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost)))
            assertTrue(result.itemRisk.lost.isEmpty(), "$charged must never drop charged/intact")

            mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
            try {
                every { victim.refreshBonuses() } just Runs
                PvpDeathBreakables.execute(world, result, breaking)
                assertNull(victim.equipment[3])
                verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == uncharged && it.amount == 1 }) }
                verify(exactly = 0) { world.spawn(match<GroundItem> { it.item == charged }) }
            } finally {
                unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
            }
        }
    }

    @Test
    fun `forBroken round-trips every breakable's broken id back to its Breakable, and rejects anything else`() {
        PvpDeathBreakables.ALL.forEach { breakable ->
            assertEquals(breakable, PvpDeathBreakables.forBroken(breakable.brokenId))
        }
        assertNull(PvpDeathBreakables.forBroken(Items.AVERNIC_DEFENDER), "the unbroken item is not itself a broken id")
        assertNull(PvpDeathBreakables.forBroken(Items.COINS_995))
    }

    @Test
    fun `PvM deaths and protected defenders are untouched`() {
        val victim = newPlayer()
        val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(Items.AVERNIC_DEFENDER, 1)))
        val pvm = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, DeathItemRiskResult(0, emptyList(), lost))
        val (sameResult, breaking) = PvpDeathBreakables.split(pvm)
        assertTrue(breaking.isEmpty())
        assertEquals(pvm, sameResult)

        val kept = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(Items.AVERNIC_DEFENDER, 1)))
        val pvp = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, null, DeathItemRiskResult(1, kept, emptyList()))
        assertTrue(PvpDeathBreakables.split(pvp).second.isEmpty(), "a protected defender stays intact")
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(3200, 3700, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            assertTrue(DEFINITIONS.getCount(ItemDef::class.java) > Items.AVERNIC_DEFENDER_BROKEN)
        }
    }
}
