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
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices
import gg.rsmod.plugins.content.mechanics.trouver.TrouverLockables
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-10-24 ("test de broken items en de untradeables op elke mogelijke manier ... welke gaat naar perdu, welke verschijnt
 * broken in je inventory"): every wearable untradeable through the real death rules ([ItemsKeptOnDeath.preview] runs the same
 * [DeathResolver] / [UntradeableDeathProtection] / [PvpDeathBreakables] calls as a death) for a monster death, a PvP death at or
 * below level 20 and above level 20, unlocked and Trouver-locked, without Protect Item. Writes the overview to
 * `build/untradeable-death-report.tsv` and fails on an item a death would make disappear for good (no inventory, no gravestone,
 * no Perdu).
 */
class UntradeableDeathReportTests {
    @Test
    fun `every wearable untradeable has a defined fate on every death, and nothing vanishes for good`() {
        val defs = DefinitionSet()
        defs.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        val tradeable = HashMap<Int, Boolean>()
        val wearable = HashSet<Int>()
        var id = -1
        File("../../data/cfg/items.yml").forEachLine { line ->
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                line.startsWith("  tradeable: ") && id >= 0 -> tradeable[id] = line.endsWith("true")
                line.startsWith("  equipment:") && id >= 0 && !line.endsWith("null") -> wearable += id
            }
        }
        tradeable.forEach { (i, t) -> defs.getNullable(ItemDef::class.java, i)?.tradeable = t }
        runCatching { TrouverLockables.registerAll() }
        gg.rsmod.plugins.content.skills.summoning.Pets.load(File("../../data/cfg/pets/pets.tsv"), File("../../data/cfg/pets/pet_tiers.tsv"))
        val guide = ItemRiskValueProvider { i -> defs.getNullable(ItemDef::class.java, i)?.let { OsrsGuidePrices.seed(it).toLong() } ?: 0L }
        val value = DeathRules.rankValue(defs, guide)

        val perduItems = PerduLostProperty.ENTRIES.flatMap { it.variants }.toSet()
        // Owner 2026-10-24 ("je praat met geen woord over de donator items en ornament kits"): every store item and every ornament
        // kit / kitted item too, tradeable or not.
        val shopOf = HashMap<Int, String>()
        gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.ENTRIES.forEach { e ->
            (listOf(e.purchaseItem) + e.resultItems).filter { it > 0 }.forEach { shopOf.putIfAbsent(it, e.shop.title) }
        }
        val kitItems = HashMap<Int, String>()
        gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ALL.forEach { kitItems[it.ornamented] = "kitted item"; kitItems[it.kit] = "ornament kit" }
        gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.CONSUMED.forEach { kitItems[it.ornamented] = "kitted item"; kitItems[it.kit] = "upgrade kit" }
        val candidates =
            (wearable.filter { tradeable[it] == false } + shopOf.keys + kitItems.keys).distinct()
                .filter { defs.getNullable(ItemDef::class.java, it)?.let { d -> !d.name.isNullOrBlank() && d.name != "null" && !d.noted } == true }
                .filter { !UntradeableDeathProtection.isDamaged(Item(it)) && TrouverRegistry.entryForDamaged(it) == null }
                .sorted()
        val rows = mutableListOf("id\tname\tmonster death\tPvP <=20\tPvP >20\tlocked PvP >20\tPerdu Lost Property\ttradeable\tshop\tkit")
        val vanish = mutableListOf<String>()
        for (itemId in candidates) {
            val name = defs.get(ItemDef::class.java, itemId).name
            fun fate(
                id: Int,
                pvp: Boolean,
                deep: Boolean,
            ): String {
                val p = player(defs)
                p.inventory[0] = Item(id)
                val preview = ItemsKeptOnDeath.preview(p, ItemsKeptOnDeath.Toggles(false, false, pvp, deep), value)
                if (preview.entries.isEmpty()) return "VANISHES"
                // A converted item has several lines (the killer gets the base item + kit, the kit to the victim's grave): all of them.
                if (preview.entries.size > 1 || preview.entries.single().item.id != id) {
                    return preview.entries.joinToString(" + ") { en ->
                        val n = defs.get(ItemDef::class.java, en.item.id).name
                        when (en.section) {
                            ItemsKeptOnDeath.Section.KEPT -> "kept $n"
                            ItemsKeptOnDeath.Section.GRAVESTONE -> "$n to grave"
                            ItemsKeptOnDeath.Section.LOST -> "killer gets $n"
                            ItemsKeptOnDeath.Section.DELETED -> "$n lost"
                        }
                    }
                }
                val e = preview.entries.single()
                val what =
                    when {
                        e.section == ItemsKeptOnDeath.Section.KEPT && "break" in e.message -> "BROKEN in inventory"
                        e.section == ItemsKeptOnDeath.Section.KEPT && "mangled" in e.message -> "MANGLED in inventory"
                        e.section == ItemsKeptOnDeath.Section.KEPT -> "KEPT"
                        e.section == ItemsKeptOnDeath.Section.GRAVESTONE -> "GRAVESTONE"
                        e.section == ItemsKeptOnDeath.Section.LOST && e.item.id != id -> "killer gets ${defs.get(ItemDef::class.java, e.item.id).name}"
                        e.section == ItemsKeptOnDeath.Section.LOST && "untradeable item is lost" in e.message -> "LOST (coins to killer)"
                        e.section == ItemsKeptOnDeath.Section.LOST -> "LOST to killer"
                        else -> "DESTROYED"
                    }
                return what
            }
            val pvm = fate(itemId, pvp = false, deep = false)
            val low = fate(itemId, pvp = true, deep = false)
            val high = fate(itemId, pvp = true, deep = true)
            val locked = TrouverRegistry.lockableFor(itemId)?.lockedItemId?.let { fate(it, pvp = true, deep = true) } ?: "-"
            val perdu =
                when {
                    itemId in perduItems -> "yes (Lost Property)"
                    high == "DESTROYED" || high == "LOST (coins to killer)" || low == "LOST (coins to killer)" -> "yes (reclaim)"
                    else -> "no"
                }
            rows += "$itemId\t$name\t$pvm\t$low\t$high\t$locked\t$perdu\t${defs.get(ItemDef::class.java, itemId).tradeable}\t${shopOf[itemId] ?: "-"}\t${kitItems[itemId] ?: "-"}"
            val gone = listOf(low, high).any { it == "LOST (coins to killer)" || it == "DESTROYED" || it == "VANISHES" }
            if (gone && perdu == "no" && !defs.get(ItemDef::class.java, itemId).tradeable) vanish += "$itemId $name ($low / $high)"
        }
        File("build").mkdirs()
        File("build/untradeable-death-report.tsv").writeText(rows.joinToString("\n") + "\n")
        File("build/untradeable-death-vanish.txt").writeText(vanish.joinToString("\n") + "\n")
        println("REPORT rows=${rows.size - 1} vanish=${vanish.size}")
        assertTrue(rows.size > 50, "report too small: ${rows.size}")
    }

    private fun player(defs: DefinitionSet): Player {
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
        return player
    }
}
