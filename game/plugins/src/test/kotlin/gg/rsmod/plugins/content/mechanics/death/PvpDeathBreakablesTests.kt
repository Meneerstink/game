package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
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
 * [PvpDeathBreakables] and [UntradeableDeathProtection] (owner 2026-09-26): an unprotected Avernic defender never becomes
 * killer loot; at or below level 20 it breaks, stays with the victim and the killer gets its repair price once.
 */
class PvpDeathBreakablesTests {
    @Test
    fun `wilderness death breaks an inventory Avernic defender in place and pays the repair cost once`() {
        val victim = newPlayer()
        val killer = newPlayer()
        victim.inventory[3] = Item(Items.AVERNIC_DEFENDER, 1)
        victim.inventory[4] = Item(Items.COINS_995, 1)
        val lost =
            listOf(
                DeathSlotItem(DeathContainerSource.INVENTORY, 3, Item(Items.AVERNIC_DEFENDER, 1)),
                DeathSlotItem(DeathContainerSource.INVENTORY, 4, Item(Items.COINS_995, 1)),
            )
        val resolved = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost))

        val (afterConversions, converting) = PvpDeathBreakables.split(resolved)
        assertTrue(converting.isEmpty(), "the defender has no conversion")
        val (result, untradeables) = UntradeableDeathProtection.splitPvp(DEFINITIONS, afterConversions, deepWilderness = false)
        assertEquals(listOf(Items.COINS_995), result.itemRisk.lost.map { it.item.id }, "the defender must never be dropped as loot")
        assertEquals(listOf(Items.AVERNIC_DEFENDER), untradeables.map { it.slotItem.item.id })

        val loot = UntradeableDeathProtection.execute(victim, untradeables)
        assertEquals(Items.AVERNIC_DEFENDER_BROKEN, victim.inventory[3]?.id)
        assertEquals(listOf(Items.COINS_995 to 600_000), loot.map { it.id to it.amount })
        assertTrue(UntradeableDeathProtection.execute(victim, untradeables).isEmpty(), "a second execution finds no defender to break")
    }

    @Test
    fun `an equipped Avernic defender is unequipped and kept broken in the inventory`() {
        val victim = newPlayer()
        victim.equipment[5] = Item(Items.AVERNIC_DEFENDER, 1)
        val lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 5, Item(Items.AVERNIC_DEFENDER, 1)))
        val (_, untradeables) =
            UntradeableDeathProtection.splitPvp(
                DEFINITIONS,
                DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, null, DeathItemRiskResult(0, emptyList(), lost)),
                deepWilderness = false,
            )
        // refreshBonuses writes interface varcs/components a mocked player does not have.
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            every { victim.refreshBonuses() } just Runs

            val loot = UntradeableDeathProtection.execute(victim, untradeables)

            assertNull(victim.equipment[5])
            assertEquals(1, victim.inventory.getItemCount(Items.AVERNIC_DEFENDER_BROKEN))
            assertEquals(600_000, loot.single().amount, "no killer: the coins still go to the (public) loot sink")
            verify(exactly = 1) { victim.refreshBonuses() }
        } finally {
            unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
    }

    @Test
    fun `every breakable item breaks in place and pays exactly its sourced repair cost`() {
        assertEquals(
            // Sourced Perdu repair costs (OSRS_IMPORT_MASTER.yml "Batch capesrings" / "Batch assembler" / "Batch capes" / pilot).
            mapOf(
                Items.AVERNIC_DEFENDER to 600_000, Items.INFERNAL_CAPE to 225_000,
                Items.IMBUED_SARADOMIN_CAPE to 96_000, Items.IMBUED_GUTHIX_CAPE to 96_000, Items.IMBUED_ZAMORAK_CAPE to 96_000,
                Items.AVAS_ASSEMBLER to 240_000,
                Items.IMBUED_SARADOMIN_MAX_CAPE to 99_000, Items.IMBUED_GUTHIX_MAX_CAPE to 99_000, Items.IMBUED_ZAMORAK_MAX_CAPE to 99_000,
                Items.ASSEMBLER_MAX_CAPE to 240_000, Items.MASORI_ASSEMBLER to 240_000, Items.MASORI_ASSEMBLER_MAX_CAPE to 240_000,
                Items.DIZANAS_MAX_CAPE to 400_000,
                // N247: OSRS Wiki "(broken)" PvP breakable table.
                Items.FIRE_CAPE to 150_000, Items.FIRE_MAX_CAPE to 150_000, Items.BRONZE_DEFENDER to 1_000, Items.IRON_DEFENDER to 2_000,
                Items.STEEL_DEFENDER to 2_500, Items.BLACK_DEFENDER to 5_000, Items.MITHRIL_DEFENDER to 15_000, Items.ADAMANT_DEFENDER to 25_000,
                Items.RUNE_DEFENDER to 35_000, Items.DRAGON_DEFENDER to 240_000, Items.DRAGON_DEFENDER_T to 240_000, Items.RUNE_DEFENDER_T to 35_000, Items.VOID_KNIGHT_TOP to 180_000, Items.VOID_KNIGHT_TOP_10611 to 180_000,
                Items.VOID_KNIGHT_ROBE to 180_000, Items.VOID_KNIGHT_GLOVES to 120_000, Items.VOID_MAGE_HELM to 160_000,
                Items.VOID_MAGE_HELM_11674 to 160_000, Items.VOID_RANGER_HELM to 160_000, Items.VOID_RANGER_HELM_11675 to 160_000,
                Items.VOID_MELEE_HELM to 160_000, Items.VOID_MELEE_HELM_11676 to 160_000, Items.ELITE_VOID_KNIGHT_TOP to 250_000,
                Items.ELITE_VOID_KNIGHT_TOP_19787 to 250_000, Items.ELITE_VOID_KNIGHT_TOP_19789 to 250_000, Items.ELITE_VOID_KNIGHT_ROBE to 250_000,
                Items.ELITE_VOID_KNIGHT_ROBE_19788 to 250_000, Items.ELITE_VOID_KNIGHT_ROBE_19790 to 250_000, Items.FIGHTER_TORSO to 150_000,
                Items.INFERNAL_MAX_CAPE to 225_000, Items.DIZANAS_QUIVER to 270_000, Items.DIZANAS_QUIVER_UNCHARGED to 270_000,
                Items.BLESSED_DIZANAS_QUIVER to 400_000,
            ),
            PvpDeathBreakables.ALL.associate { it.itemId to it.repairCost },
        )
        PvpDeathBreakables.ALL.forEach { breakable ->
            val victim = newPlayer()
            val killer = newPlayer()
            victim.inventory[2] = Item(breakable.itemId, 1)
            val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 2, Item(breakable.itemId, 1)))
            val (result, untradeables) =
                UntradeableDeathProtection.splitPvp(DEFINITIONS, DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost)), false)
            assertTrue(result.itemRisk.lost.isEmpty())

            val loot = UntradeableDeathProtection.execute(victim, untradeables)

            assertEquals(breakable.brokenId, victim.inventory[2]?.id, "${breakable.itemId} breaks in place")
            assertEquals(listOf(Items.COINS_995 to breakable.repairCost), loot.map { it.id to it.amount }, "${breakable.itemId}: killer gets the repair cost")
            assertEquals(breakable.repairCost.toLong(), UntradeableDeathProtection.repairCost(DEFINITIONS, victim.inventory[2]!!))
            // A 667 copy (second Void helm set, Elite void variants) repairs into the canonical piece.
            assertEquals(PvpDeathBreakables.forBroken(breakable.brokenId)!!.itemId, UntradeableDeathProtection.repaired(victim.inventory[2]!!).id)
        }
    }
    @Test
    fun `every kitted item drops its plain item to the killer and sends its kit to the victim's gravestone`() {
        // K01 deel 5 (owner 2026-10-25: every kit untradeable and never lost): "Items Kept on Death" drops the non-ornamented item to the
        // PKer; the kit goes to the victim's gravestone (the OSRS Bounty Hunter kit rule, now for every kit). The Nightmare staff
        // orbs are no kits: the PKer gets the staff and the orb.
        val kits = gg.rsmod.plugins.content.items.osrs.OrnamentKitRules.KITS
        gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ALL.filter { it.pvpConvert }.forEach { ornament ->
            val victim = newPlayer()
            val killer = newPlayer()
            val world = mockk<World>(relaxed = true)
            victim.inventory[0] = Item(ornament.ornamented, 1)
            val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(ornament.ornamented, 1)))
            val (result, converting) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost)))
            assertTrue(result.itemRisk.lost.isEmpty(), "${ornament.ornamented} must not drop as the ornamented item")
            val grave = mutableListOf<Item>()

            PvpDeathBreakables.execute(world, result, converting, toGrave = { grave += it })

            assertNull(victim.inventory[0])
            verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == ornament.base && it.amount == 1 }) }
            verify(exactly = 0) { world.spawn(match<GroundItem> { it.item == ornament.ornamented }) }
            if (ornament.kit in kits) {
                verify(exactly = 0) { world.spawn(match<GroundItem> { it.item == ornament.kit }) }
                assertEquals(listOf(ornament.kit), grave.map { it.id }, "${ornament.ornamented}: the kit goes to the victim's gravestone")
            } else {
                verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == ornament.kit && it.amount == 1 }) }
            }
        }
    }

    @Test
    fun `a carried kit is never lost on a wilderness death - it goes to the victim's gravestone`() {
        gg.rsmod.plugins.content.items.osrs.OrnamentKitRules.KITS.forEach { kit ->
            val victim = newPlayer()
            val world = mockk<World>(relaxed = true)
            victim.inventory[0] = Item(kit, 1)
            val lost = listOf(DeathSlotItem(DeathContainerSource.INVENTORY, 0, Item(kit, 1)))
            val (result, converting) = PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, newPlayer(), DeathItemRiskResult(0, emptyList(), lost)))
            assertTrue(result.itemRisk.lost.isEmpty(), "kit $kit is not dropped for the killer")
            val grave = mutableListOf<Item>()
            PvpDeathBreakables.execute(world, result, converting, toGrave = { grave += it })
            assertNull(victim.inventory[0])
            verify(exactly = 0) { world.spawn(any<GroundItem>()) }
            assertEquals(listOf(kit), grave.map { it.id }, "kit $kit goes to the gravestone")
            assertTrue(!UntradeableDeathProtection.handles(DEFINITIONS, kit), "kit $kit is not lost for coins")
        }
    }

    @Test
    fun `ornamented items without the base plus kit rule keep the default death handling`() {
        val kept = gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ALL.filterNot { it.pvpConvert }
        assertEquals(
            setOf(
                // Bounty Hunter kits (untradeable): the kit goes to the victim's gravestone, not to the killer - their own row since owner
                // 2026-10-06 (PvpDeathBreakables.BOUNTY_HUNTER_KITTED, conversion-set test below).
                Items.ELDER_CHAOS_TOP_OR, Items.ELDER_CHAOS_ROBE_OR, Items.ELDER_CHAOS_HOOD_OR,
                Items.DAGONHAI_HAT_OR, Items.DAGONHAI_ROBE_TOP_OR, Items.DAGONHAI_ROBE_BOTTOM_OR,
                Items.HEAVY_BALLISTA_OR,
                // Elidinis' ward (or): OSRS Wiki "Elidinis' ward (or)" - on a PvP death "the Menaphite ornament kit will be placed in their
                // gravestone" (no base + kit drop to the PKer).
                Items.ELIDINIS_WARD_OR,
                // Dragon defender (t): tradeable kit, but OSRS Wiki "Dragon defender (t)" - on a PvP death "it will remain in the player's
                // inventory, but will become broken" (the defender rule, not the base + kit drop); no defender breaks here yet.
                Items.DRAGON_DEFENDER_T,
                // K01 deel 5: Rune defender (t) - OSRS Wiki "Rune defender (t)", the same broken rule.
                Items.RUNE_DEFENDER_T,
            ),
            kept.map { it.ornamented }.toSet(),
        )
        // Elidinis' ward (or) (owner Q14=A) and the Bounty Hunter kits (owner 2026-10-06) have their own rows: see the conversion-set test below.
        kept.filterNot { it.ornamented == Items.ELIDINIS_WARD_OR || it.ornamented in PvpDeathBreakables.BOUNTY_HUNTER_KITTED }.forEach { ornament ->
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
            val back = PvpDeathBreakables.forBroken(breakable.brokenId)!!
            assertEquals(breakable.brokenId, back.brokenId)
            assertEquals(breakable.repairCost, back.repairCost)
        }
        assertEquals(Items.VOID_MELEE_HELM, PvpDeathBreakables.forBroken(Items.VOID_MELEE_HELM_BROKEN)!!.itemId, "the canonical piece")
        assertNull(PvpDeathBreakables.forBroken(Items.AVERNIC_DEFENDER), "the unbroken item is not itself a broken id")
        assertNull(PvpDeathBreakables.forBroken(Items.COINS_995))
    }

    @Test
    fun `N247 above level 20 a fire cape or defender breaks, Void is lost for coins - OSRS broken table`() {
        assertEquals(UntradeableFate.BROKEN, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.FIRE_CAPE), deepWilderness = true).fate)
        assertEquals(UntradeableFate.BROKEN, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.RUNE_DEFENDER), deepWilderness = true).fate)
        assertEquals(UntradeableFate.DESTROYED, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.VOID_KNIGHT_TOP), deepWilderness = true).fate)
        assertEquals(UntradeableFate.BROKEN, UntradeableDeathProtection.fateOf(DEFINITIONS, Item(Items.VOID_KNIGHT_TOP), deepWilderness = false).fate)
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

    private fun charged(id: Int, charges: Int) = Item(id, 1).also { it.attr[gg.rsmod.game.model.item.ItemAttribute.CHARGES] = charges }

    private fun runConversions(
        victim: Player,
        lost: List<DeathSlotItem>,
        protected: List<DeathSlotItem> = emptyList(),
        grave: MutableList<Item> = mutableListOf(),
        prices: ItemRiskValueProvider = ItemRiskValueProvider { 0L },
    ): List<Item> {
        val killer = newPlayer()
        val world = mockk<World>(relaxed = true)
        val (result, breaking) =
            PvpDeathBreakables.split(DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(protected.size, protected, lost)))
        val loot = mutableListOf<Item>()
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            every { victim.refreshBonuses() } just Runs
            every { victim.message(any(), any(), any()) } just Runs
            PvpDeathBreakables.execute(world, result, breaking, toGrave = { grave += it }, prices = prices) { loot += it }
            loot += PvpDeathBreakables.stripProtectedEther(victim, result)
        } finally {
            unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
        return loot
    }

    @Test
    fun `an unprotected serpentine helm drops uncharged with its scales`() {
        val victim = newPlayer()
        victim.equipment[0] = charged(Items.SERPENTINE_HELM, 5_000)
        val loot = runConversions(victim, listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 0, victim.equipment[0]!!)))
        assertNull(victim.equipment[0])
        assertEquals(listOf(Items.SERPENTINE_HELM_UNCHARGED to 1, Items.ZULRAHS_SCALES to 5_000), loot.map { it.id to it.amount })
    }

    @Test
    fun `lost revenant bows - Craw's activation disappears, Webweaver's goes to the killer`() {
        val craws = newPlayer()
        craws.equipment[3] = charged(Items.CRAWS_BOW, 400)
        val crawsLoot = runConversions(craws, listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 3, craws.equipment[3]!!)))
        assertEquals(listOf(Items.CRAWS_BOW_U to 1, Items.REVENANT_ETHER to 400), crawsLoot.map { it.id to it.amount })

        val webweaver = newPlayer()
        webweaver.equipment[3] = charged(Items.WEBWEAVER_BOW, 400)
        val webLoot = runConversions(webweaver, listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 3, webweaver.equipment[3]!!)))
        assertEquals(listOf(Items.WEBWEAVER_BOW_U to 1, Items.REVENANT_ETHER to 1_400), webLoot.map { it.id to it.amount })
    }

    @Test
    fun `protected revenant weapons still drop their stored ether`() {
        val victim = newPlayer()
        victim.equipment[3] = charged(Items.VIGGORAS_CHAINMACE, 250)
        victim.inventory[0] = charged(Items.CRAWS_BOW, 100)
        val protected =
            listOf(
                DeathSlotItem(DeathContainerSource.EQUIPMENT, 3, victim.equipment[3]!!),
                DeathSlotItem(DeathContainerSource.INVENTORY, 0, victim.inventory[0]!!),
            )
        val loot = runConversions(victim, emptyList(), protected)
        assertEquals(listOf(Items.REVENANT_ETHER to 250, Items.REVENANT_ETHER to 100), loot.map { it.id to it.amount })
        assertEquals(Items.VIGGORAS_CHAINMACE, victim.equipment[3]?.id, "the chainmace stays activated")
        assertEquals(0, victim.equipment[3]?.attr?.get(gg.rsmod.game.model.item.ItemAttribute.CHARGES))
        assertEquals(Items.CRAWS_BOW_U, victim.inventory[0]?.id, "the kept Craw's bow loses its activation")
    }

    @Test
    fun `a used amulet of the damned turns to dust on any death, the full one does not`() {
        val victim = newPlayer()
        victim.equipment[2] = Item(Items.AMULET_OF_THE_DAMNED, 1)
        victim.inventory[5] = Item(Items.AMULET_OF_THE_DAMNED_FULL, 1)
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            every { victim.refreshBonuses() } just Runs
            assertEquals(1, PvpDeathBreakables.crumbleUsedAmulets(victim))
        } finally {
            unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        }
        assertNull(victim.equipment[2])
        assertEquals(Items.AMULET_OF_THE_DAMNED_FULL, victim.inventory[5]?.id)
    }

    /** One row of the owner Q14=A (2026-10-06) set: what the killer, the victim's gravestone and the victim's points receive. */
    private data class Expected(val item: Int, val killer: List<Pair<Int, Int>>, val grave: List<Int> = emptyList(), val deadmanRefund: Int = 0, val loyaltyRefund: Int = 0)

    @Test
    fun `owner Q14 conversions - killer loot, victim gravestone share and imbue refund for the whole set`() {
        // Granite maul + ornate maul handle guide prices: "converted to 90% of Grand Exchange price of the granite maul and the
        // ornate maul handle".
        val prices = ItemRiskValueProvider { if (it == Items.GRANITE_MAUL) 100_000L else if (it == Items.ORNATE_MAUL_HANDLE) 1_000_000L else 0L }
        val imbue = 600 // StoreCatalogue "Imbue services": base ring + 600 Deadman Points.
        val fullHelmetImbue = listOf(Items.HEXCREST, Items.FOCUS_SIGHT)
        val set =
            listOf(
                Expected(Items.RING_OF_THE_GODS_I, listOf(Items.RING_OF_THE_GODS to 1), deadmanRefund = imbue),
                Expected(Items.TYRANNICAL_RING_I, listOf(Items.TYRANNICAL_RING to 1), deadmanRefund = imbue),
                Expected(Items.TREASONOUS_RING_I, listOf(Items.TREASONOUS_RING to 1), deadmanRefund = imbue),
                Expected(Items.RING_OF_SUFFERING_I, listOf(Items.RING_OF_SUFFERING to 1), deadmanRefund = imbue),
                Expected(Items.RING_OF_SUFFERING_RI, listOf(Items.RING_OF_SUFFERING to 1), deadmanRefund = imbue),
                // Owner 2026-10-06: Berserker / Warrior ring (i), imbued for 20,000 Loyalty Points (StoreCatalogue "Imbue services").
                Expected(Items.BERSERKER_RING_I, listOf(Items.BERSERKER_RING to 1), loyaltyRefund = 20_000),
                Expected(Items.WARRIOR_RING_I, listOf(Items.WARRIOR_RING to 1), loyaltyRefund = 20_000),
                // Owner 2026-10-06 ("Yes, 20,000 Loyalty"): Seers / Archers ring (i), the same route.
                Expected(Items.SEERS_RING_I, listOf(Items.SEERS_RING to 1), loyaltyRefund = 20_000),
                Expected(Items.ARCHERS_RING_I, listOf(Items.ARCHERS_RING to 1), loyaltyRefund = 20_000),
                Expected(Items.FULL_SLAYER_HELMET, listOf(Items.BLACK_MASK to 1), grave = fullHelmetImbue),
                Expected(Items.FULL_SLAYER_HELMET_E, listOf(Items.BLACK_MASK to 1), grave = fullHelmetImbue),
                Expected(Items.FULL_SLAYER_HELMET_CHARGED, listOf(Items.BLACK_MASK to 1), grave = fullHelmetImbue),
                // Not imbued (plain helmet, Summoning-enchanted helmets, recoil ring without imbue, magic shortbow (i)): no refund.
                Expected(Items.SLAYER_HELMET, listOf(Items.BLACK_MASK to 1)),
                Expected(Items.SLAYER_HELMET_E, listOf(Items.BLACK_MASK to 1)),
                Expected(Items.SLAYER_HELMET_CHARGED, listOf(Items.BLACK_MASK to 1)),
                Expected(Items.RING_OF_SUFFERING_R, listOf(Items.RING_OF_SUFFERING to 1)),
                Expected(Items.MAGIC_SHORTBOW_I, listOf(Items.MAGIC_SHORTBOW to 1)),
                Expected(Items.ELIDINIS_WARD_F, listOf(Items.ELIDINIS_WARD to 1, Items.ARCANE_SIGIL to 1)),
                Expected(Items.ELIDINIS_WARD_OR, listOf(Items.ELIDINIS_WARD to 1, Items.ARCANE_SIGIL to 1), grave = listOf(Items.MENAPHITE_ORNAMENT_KIT)),
                Expected(Items.GRANITE_MAUL_ORNATE_HANDLE, listOf(Items.COINS_995 to 990_000)),
                // Owner 2026-10-06: ferocious gloves -> hydra leather; Bounty Hunter (or) items -> base to the killer, kit to the gravestone
                // (OSRS Wiki kit pages: "the item will be lost to the killer but the kit will be sent to the gravestone").
                Expected(Items.FEROCIOUS_GLOVES, listOf(Items.HYDRA_LEATHER to 1)),
                Expected(Items.HEAVY_BALLISTA_OR, listOf(Items.HEAVY_BALLISTA to 1), grave = listOf(Items.HEAVY_BALLISTA_ORNAMENT_KIT)),
                Expected(Items.ELDER_CHAOS_TOP_OR, listOf(Items.ELDER_CHAOS_TOP to 1), grave = listOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT)),
                Expected(Items.ELDER_CHAOS_ROBE_OR, listOf(Items.ELDER_CHAOS_ROBE to 1), grave = listOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT)),
                Expected(Items.ELDER_CHAOS_HOOD_OR, listOf(Items.ELDER_CHAOS_HOOD to 1), grave = listOf(Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT)),
                Expected(Items.DAGONHAI_HAT_OR, listOf(Items.DAGONHAI_HAT to 1), grave = listOf(Items.DAGONHAI_ROBES_ORNAMENT_KIT)),
                Expected(Items.DAGONHAI_ROBE_TOP_OR, listOf(Items.DAGONHAI_ROBE_TOP to 1), grave = listOf(Items.DAGONHAI_ROBES_ORNAMENT_KIT)),
                Expected(Items.DAGONHAI_ROBE_BOTTOM_OR, listOf(Items.DAGONHAI_ROBE_BOTTOM to 1), grave = listOf(Items.DAGONHAI_ROBES_ORNAMENT_KIT)),
            )
        assertEquals(PvpDeathBreakables.BOUNTY_HUNTER_KITTED, set.map { it.item }.filter { it in PvpDeathBreakables.BOUNTY_HUNTER_KITTED }.toSet(), "every Bounty Hunter kitted item has a row")
        for (row in set) {
            assertTrue(PvpDeathBreakables.converts(row.item), "${row.item} converts on a PvP death")
            assertTrue(!UntradeableDeathProtection.handles(DEFINITIONS, row.item), "${row.item} never takes the untradeable coins route")
            val victim = newPlayer()
            victim.equipment[3] = Item(row.item, 1)
            val grave = mutableListOf<Item>()
            val loot = runConversions(victim, listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 3, victim.equipment[3]!!)), grave = grave, prices = prices)
            assertNull(victim.equipment[3], "${row.item} leaves the victim")
            assertEquals(row.killer, loot.map { it.id to it.amount }, "killer loot for ${row.item}")
            assertEquals(row.grave, grave.map { it.id }, "victim gravestone share for ${row.item}")
            assertEquals(row.deadmanRefund, victim.attr[StoreCatalogue.Currency.DEADMAN.attr] ?: 0, "Deadman Points refunded for ${row.item}")
            assertEquals(row.loyaltyRefund, victim.attr[StoreCatalogue.Currency.LOYALTY.attr] ?: 0, "Loyalty Points refunded for ${row.item}")
            // The Items Kept on Death screen and the risk skull read the same table.
            assertEquals(row.killer, PvpDeathBreakables.killerItems(Item(row.item, 1), prices)?.map { it.id to it.amount })
            assertEquals(row.grave, PvpDeathBreakables.victimGraveItems(Item(row.item, 1)).map { it.id })
        }
        // Every refunded imbue is priced by exactly one StoreCatalogue route, in the currency the player paid.
        for (ring in PvpDeathBreakables.IMBUE_REFUNDED) {
            val route = StoreCatalogue.ENTRIES.single { it.kind == StoreCatalogue.Kind.UPGRADE && it.purchaseItem == ring }
            assertEquals(PvpDeathBreakables.ImbueRefund(route.currency, route.price), PvpDeathBreakables.imbueRefund(ring))
            val loyalty = setOf(Items.BERSERKER_RING_I, Items.WARRIOR_RING_I, Items.SEERS_RING_I, Items.ARCHERS_RING_I)
            val paid = if (ring in loyalty) StoreCatalogue.Currency.LOYALTY else StoreCatalogue.Currency.DEADMAN
            assertEquals(paid, route.currency)
        }
    }

    @Test
    fun `a lost charged slayer helmet drops its stored Summoning scrolls to the killer, the screen shows them`() {
        val headgear = gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear
        val scroll = 12_425 // any id: the per-player stored scroll is read back as-is
        val victim = newPlayer()
        victim.attr[headgear.SCROLL_ATTR] = scroll
        victim.attr[headgear.COUNT_ATTR] = 37
        victim.equipment[0] = Item(Items.SLAYER_HELMET_CHARGED, 1)
        // Items Kept on Death screen / risk skull: the same call with the victim.
        assertEquals(listOf(Items.BLACK_MASK to 1, scroll to 37), PvpDeathBreakables.killerItems(Item(Items.SLAYER_HELMET_CHARGED, 1), null, victim)?.map { it.id to it.amount })
        val loot = runConversions(victim, listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 0, victim.equipment[0]!!)))
        assertEquals(listOf(Items.BLACK_MASK to 1, scroll to 37), loot.map { it.id to it.amount })
        assertNull(victim.attr[headgear.SCROLL_ATTR], "the scrolls left the victim")
        assertNull(victim.attr[headgear.COUNT_ATTR])

        // Only the charged id holds scrolls: the (e) helmet, the plain helmet and the full slayer helmet (charged) (not a scroll helmet
        // here, EnchantedHeadgear SOURCE_GAP) drop none and leave the count alone.
        for (id in listOf(Items.SLAYER_HELMET_E, Items.SLAYER_HELMET, Items.FULL_SLAYER_HELMET_CHARGED)) {
            val other = newPlayer()
            other.attr[headgear.SCROLL_ATTR] = scroll
            other.attr[headgear.COUNT_ATTR] = 5
            other.equipment[0] = Item(id, 1)
            val otherLoot = runConversions(other, listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 0, other.equipment[0]!!)))
            assertEquals(listOf(Items.BLACK_MASK to 1), otherLoot.map { it.id to it.amount }, "$id drops no scrolls")
            assertEquals(5, other.attr[headgear.COUNT_ATTR])
        }
    }

    @Test
    fun `granite maul (or) without a Grand Exchange price gives nothing instead of a wrong amount`() {
        assertEquals(emptyList<Item>(), PvpDeathBreakables.killerItems(Item(Items.GRANITE_MAUL_ORNATE_HANDLE, 1), null))
        assertEquals(emptyList<Item>(), PvpDeathBreakables.killerItems(Item(Items.GRANITE_MAUL_ORNATE_HANDLE, 1), ItemRiskValueProvider { 0L }))
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(3200, 3700, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
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
