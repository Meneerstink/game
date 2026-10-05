package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices
import gg.rsmod.plugins.content.mechanics.trouver.TrouverLockable
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner bugs 032003 / 032029 (2026-10-04, priority 1: "items kept on death needs to work exactly 100%"): the owner's own gear,
 * with tradeable flags from `data/cfg/items.yml` exactly as the server loads them (ItemMetadataService). OSRS Wiki "Items Kept on
 * Death": tradeables are lost to the PKer, Barrows/Moon armour drops as its broken version, ornamented items as base item + kit;
 * a Trouver-locked untradeable breaks (<= 20) or is mangled (> 20) and stays with the victim; pets stay with the owner.
 */
class ItemsKeptOnDeathOwnerGearTests {
    @Test
    fun `owner gear - the screen shows what the killer really receives`() {
        val defs = DefinitionSet()
        defs.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        // The server sets tradeable from data/cfg/items.yml (ItemMetadataService), not from the cache.
        val tradeable = HashMap<Int, Boolean>()
        var id = -1
        java.io.File("../../data/cfg/items.yml").forEachLine { line ->
            if (line.startsWith("- id: ")) id = line.removePrefix("- id: ").trim().toInt()
            else if (line.startsWith("  tradeable: ") && id >= 0) tradeable[id] = line.endsWith("true")
        }
        tradeable.forEach { (i, t) -> defs.getNullable(ItemDef::class.java, i)?.tradeable = t }
        // The pet table, as pets.plugin.kts loads it at server start (the owner's TzRek-Zuk stays with him).
        gg.rsmod.plugins.content.skills.summoning.Pets.load(java.io.File("../../data/cfg/pets/pets.tsv"), java.io.File("../../data/cfg/pets/pet_tiers.tsv"))
        runCatching { TrouverRegistry.register(TrouverLockable(baseItemId = Items.IMBUED_GUTHIX_MAX_CAPE, lockedItemId = Items.IMBUED_GUTHIX_MAX_CAPE_L)) }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.tile } returns Tile(3094, 3469, 0)
        every { player.inventory } returns ItemContainer(defs, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(defs, EQUIPMENT_KEY)
        every { player.bank } returns ItemContainer(defs, BANK_KEY)
        every { player.deathRecovery } returns ItemContainer(defs, DEATH_RECOVERY_KEY)
        every { player.gravestone } returns ItemContainer(defs, GRAVESTONE_KEY)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        every { player.world } returns world
        listOf(22878, 22880, 23961, 22372, 22876, 23083, 22724).forEachIndexed { i, id -> player.inventory[i] = Item(id, 1) }
        mapOf(0 to 23101, 1 to 23059, 2 to 22330, 3 to 23128, 4 to 23102, 7 to 23103, 9 to 22355, 10 to 22911, 12 to 22946)
            .forEach { (slot, id) -> player.equipment[slot] = Item(id, 1) }
        player.equipment[13] = Item(23134, 6480)
        val guide = ItemRiskValueProvider { id -> defs.getNullable(ItemDef::class.java, id)?.let { OsrsGuidePrices.seed(it).toLong() } ?: 0L }
        val value = DeathRules.rankValue(defs, guide)
        fun ids(p: ItemsKeptOnDeath.Preview, section: ItemsKeptOnDeath.Section) = p.entries.filter { it.section == section }.map { it.item.id }.toSet()
        val pvp = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(false, false, true, false), value)
        assertEquals(setOf(Items.IMBUED_GUTHIX_MAX_CAPE_L, 23961), ids(pvp, ItemsKeptOnDeath.Section.KEPT), "only the locked cape (broken) and the pet stay")
        val lost = ids(pvp, ItemsKeptOnDeath.Section.LOST)
        // Tradeable OSRS gear as itself; Moon armour broken (23110/23112/23114); Harmonised staff = staff + orb; (or) = base + kit.
        assertTrue(lost.containsAll(listOf(22878, 22880, 22876, 22946, 22372, 22911, 22724, 23128, 23134)), "$lost")
        assertTrue(lost.containsAll(listOf(23110, 23112, 23114, 23081, 23086, 22351, 22353, 22328, 22331)), "$lost")
        assertTrue(lost.none { it in listOf(23101, 23102, 23103, 23083, 22330, 22355) }, "converting items never show as themselves: $lost")
        assertTrue(pvp.entries.single { it.item.id == Items.IMBUED_GUTHIX_MAX_CAPE_L }.message.contains("break"))

        val deep = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(true, false, true, true), value)
        val kept = deep.entries.filter { it.section == ItemsKeptOnDeath.Section.KEPT }
        assertEquals(22878, kept.first().item.id, "Protect Item keeps the most valuable item (Ancestral robe top)")
        assertTrue(kept.single { it.item.id == Items.IMBUED_GUTHIX_MAX_CAPE_L }.message.contains("mangled"))

        val pvm = ItemsKeptOnDeath.preview(player, ItemsKeptOnDeath.Toggles(false, false, false, false), value)
        assertTrue(ids(pvm, ItemsKeptOnDeath.Section.LOST).isEmpty(), "a PvM death gives nothing away")
    }
}
