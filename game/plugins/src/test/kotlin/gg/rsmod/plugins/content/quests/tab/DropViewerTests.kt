package gg.rsmod.plugins.content.quests.tab

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropBoost
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Rare
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * N108 / N104 night job 2026-10-24 (spec section E): the Drop Viewer's chances are the roll's own (same functions), the lines
 * stand in groups, the rarest first (N279), every drop table open under its line, a search finds an item inside a fold, the
 * Collection Log records only log items with the kill count, the favourites and the new attributes load from old saves (empty),
 * and a Deadman Task pays exactly once.
 */
class DropViewerTests {
    private class Account {
        val attributes = AttributeMap()
        val world = mockk<World>(relaxed = true)
        val plugins = mockk<PluginRepository>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        init {
            every { world.definitions } returns DEFINITIONS
            every { world.plugins } returns plugins
            every { plugins.allNpcCombatDefs() } returns emptyMap()
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.username } returns "dropviewer_test"
            every { player.combatLevel } returns 3
        }
    }

    private val id = 999_108

    private fun register() {
        DropTableFactory.register(
            DropTableFactory.build {
                guaranteed { obj(Items.DRAGON_BONES) }
                main {
                    total(128)
                    obj(Items.RUNE_PLATEBODY, slots = 4)
                    obj(Items.DRAGON_PICKAXE, slots = 1)
                    obj(Items.COINS_995, quantityRange = 100..200, slots = 59)
                    table(Rare.rareTable, slots = 2)
                    nothing(62)
                }
            },
            id,
        )
        DropSources.reset()
    }

    private fun source() = DropSources.Source("npc:$id", "Test dragon", -1, intArrayOf(id), 100, DropSources.Group.MONSTERS, DropSources.Kind.NPC, id)

    @Test
    fun `chances are the roll's own - lineChances at the player's rate and at 1x, a fold rolled at 1x inside`() {
        register()
        val account = Account()
        val view = DropSources.view(account.player, source(), rate = 5.0)
        val table = DropTableFactory.tables(account.player, id)!!.first { it.name == "main" }
        val your = DropTableFactory.lineChances(table, 5.0)
        val normal = DropTableFactory.lineChances(table, 1.0)
        fun line(item: Int) = view.lines.first { it.item == item && it.tag != DropSources.Tag.TABLE }
        assertEquals(your[1], line(Items.DRAGON_PICKAXE).your, 1e-12)
        assertEquals(normal[1], line(Items.DRAGON_PICKAXE).normal, 1e-12)
        assertEquals(1.0 / 128, line(Items.DRAGON_PICKAXE).normal, 1e-12)
        assertTrue(line(Items.DRAGON_PICKAXE).your > line(Items.DRAGON_PICKAXE).normal, "the rare line gets the bracket factor")
        // DropBoost is the one boost: the same function the roll's pick reads.
        assertEquals(DropBoost.chances(DoubleArray(table.entries.size) { normal[it] }, 5.0)[1], your[1], 1e-12)
        // The Always bar: the guaranteed item.
        assertEquals(listOf(Items.DRAGON_BONES), view.always.map { it.item })
        // The rare drop table: one line, its kids rolled at 1x inside (parent chance x the inner share).
        val rdt = view.lines.single { it.tag == DropSources.Tag.TABLE }
        assertEquals("Rare drop table", rdt.name)
        assertEquals(normal[3], rdt.normal, 1e-12)
        val inner = DropTableFactory.tables(account.player, id)!!.first { it.name == "main" }.entries[3].drop as gg.rsmod.plugins.content.drops.DropEntry.TableDrop
        val innerChances = DropTableFactory.lineChances(inner.table, 1.0)
        val nature = rdt.kids.first { it.item == Items.NATURE_RUNE }
        assertEquals(rdt.normal * innerChances[0], nature.normal, 1e-12)
        assertEquals(rdt.your * innerChances[0], nature.your, 1e-12)
    }

    @Test
    fun `a guaranteed line with a quantity range drops on every kill and shows in the Always bar`() {
        val rangeId = 999_109
        DropTableFactory.register(DropTableFactory.build { guaranteed { obj(Items.BIG_BONES, quantity = 1); obj(Items.COINS_995, quantityRange = 1..6) } }, rangeId)
        val account = Account()
        repeat(20) {
            val items = DropTableFactory.getDrop(account.player, rangeId)!!
            assertTrue(items.any { it.id == Items.COINS_995 && it.amount in 1..6 }, "the ranged guaranteed line drops: $items")
            assertTrue(items.any { it.id == Items.BIG_BONES })
        }
        val view = DropSources.view(account.player, DropSources.Source("npc:$rangeId", "Range", -1, intArrayOf(rangeId), 1, DropSources.Group.MONSTERS, DropSources.Kind.NPC, rangeId), 1.0)
        assertEquals(listOf("1", "1-6"), view.always.map { it.quantity })
    }

    @Test
    fun `N279 - groups with headings in the Wiki's order, the rarest first inside a group, the same in every bracket`() {
        register()
        val account = Account()
        val world = account.world
        for (rate in listOf(1.0, 3.0, 5.0)) {
            val view = DropSources.view(account.player, source(), rate)
            val groups = DropGroups.grouped(world, view.lines, emptySet())
            assertEquals(listOf("Weapons and armour", "Coins and other", "Drop tables"), groups.map { it.first.title }, "at ${rate}x")
            // Rune platebody 4/128 is commoner than the Dragon pickaxe 1/128: the pickaxe first.
            assertEquals(listOf(Items.DRAGON_PICKAXE, Items.RUNE_PLATEBODY), groups[0].second.map { it.item })
        }
        // The pet on top of a mixed list; then rarest first; the same chance keeps the same item together.
        val pet = DropSources.Line("pet", Items.BONES, "Pet", "1", 1.0 / 3000, 1.0 / 3000, DropSources.Tag.PET)
        val common = DropSources.Line("a", Items.BONES, "Bones", "1", 0.5, 0.5)
        val rare = DropSources.Line("b", Items.DRAGON_PICKAXE, "Dragon pickaxe", "1", 1.0 / 128, 1.0 / 128)
        val sameA = DropSources.Line("c", Items.RUNE_ARROW, "Rune arrow", "10", 0.01, 0.01)
        val other = DropSources.Line("d", Items.ADAMANT_ARROW, "Adamant arrow", "5", 0.01, 0.01)
        val sameB = DropSources.Line("e", Items.RUNE_ARROW, "Rune arrow", "20", 0.01, 0.01)
        assertEquals(listOf("pet", "b", "d", "c", "e", "a"), DropSources.sorted(world, listOf(common, sameA, rare, other, sameB, pet)).map { it.key })
        // The order follows the NORMAL chance: a boosted line does not move.
        val boosted = DropSources.Line("f", Items.RUNE_ARROW, "Rune arrow", "1", 0.9, 1.0 / 5000)
        assertEquals("f", DropSources.sorted(world, listOf(common, boosted)).first().key)
        // Charms keep their fixed order (gold, green, crimson, blue), whatever the chance.
        val charms = DropGroups.CHARMS.reversed().mapIndexed { i, c -> DropSources.Line("ch$i", c, "c$i", "1", 0.1 * (i + 1), 0.1 * (i + 1)) }
        assertEquals(DropGroups.CHARMS, DropGroups.sortGroup(DropGroups.Group.CHARMS, charms).map { it.item })
    }

    @Test
    fun `N279 - an item's group is its own category from the item definition, the doubtful ones from the fixed list`() {
        val world = Account().world
        fun g(id: Int) = DropGroups.category(world, id)
        assertEquals(DropGroups.Group.GEAR, g(Items.RUNE_PLATEBODY))
        assertEquals(DropGroups.Group.GEAR, g(Items.DRAGON_MED_HELM))
        assertEquals(DropGroups.Group.RUNES, g(Items.NATURE_RUNE))
        assertEquals(DropGroups.Group.RUNES, g(Items.RUNE_ARROW))
        assertEquals(DropGroups.Group.MATERIALS, g(Items.RUNITE_ORE))
        assertEquals(DropGroups.Group.MATERIALS, g(Items.MAGIC_LOGS))
        assertEquals(DropGroups.Group.MATERIALS, g(Items.UNCUT_DIAMOND))
        assertEquals(DropGroups.Group.MATERIALS, g(Items.DRAGON_BONES))
        assertEquals(DropGroups.Group.MATERIALS, g(Items.BLACK_DRAGONHIDE))
        assertEquals(DropGroups.Group.HERBS, g(Items.GRIMY_RANARR))
        assertEquals(DropGroups.Group.HERBS, g(Items.RANARR_SEED))
        assertEquals(DropGroups.Group.FOOD, g(Items.SHARK))
        assertEquals(DropGroups.Group.FOOD, g(Items.SUPER_RESTORE_4))
        assertEquals(DropGroups.Group.OTHER, g(Items.COINS_995))
        assertEquals(DropGroups.Group.OTHER, g(Items.LOOP_HALF_OF_A_KEY))
        assertEquals(DropGroups.Group.CHARMS, g(Items.GOLD_CHARM))
        // A note counts as its item.
        val noted = (0 until 30000).firstOrNull { id -> world.definitions.getNullable(ItemDef::class.java, id)?.let { it.noted && it.noteLinkId == Items.RUNITE_ORE } == true }
        if (noted != null) assertEquals(DropGroups.Group.MATERIALS, g(noted))
        // Every fixed-list name really is an item name, so no entry is dead.
        val names = (0 until world.definitions.getCount(ItemDef::class.java)).mapNotNull { world.definitions.getNullable(ItemDef::class.java, it)?.name?.lowercase() }.toSet()
        assertEquals(emptyList(), DropGroups.OVERRIDES.keys.filter { it !in names })
    }

    @Test
    fun `N279 - every drop table is open, its lines right under it, a heading over every group`() {
        register()
        val account = Account()
        val view = DropSources.view(account.player, source(), 1.0)
        val rows = DropViewer.rowsOf(account.player, source(), view)
        assertTrue(rows.first().header != null, "a heading first")
        val at = rows.indexOfFirst { it.line?.tag == DropSources.Tag.TABLE }
        val rdt = rows[at].line!!
        assertEquals("Rare drop table", rdt.name)
        assertEquals(rdt.kids.size, rows.drop(at + 1).takeWhile { it.level > 0 && it.line != null }.count { it.level == 1 }, "every kid under the table")
        assertTrue(rows.any { it.line?.item == Items.LOOP_HALF_OF_A_KEY && it.level == 1 }, "the loop half shows without a click")
        // The kids too: rarest first.
        val kids = rows.drop(at + 1).takeWhile { it.level > 0 }.filter { it.level == 1 }.map { 1.0 / it.line!!.normal }
        assertEquals(kids.sortedDescending(), kids, "rarest first")
        assertTrue(DropViewer.rowsOf(account.player, source(), view).size <= gg.rsmod.game.tools.importer.N108InterfaceImportTool.V.ROW_SLOTS)
    }

    @Test
    fun `the Deadman's Chest shows its own loc model - the closed chest's first model from the loc config`() {
        val account = Account()
        val lib = CacheLibrary(CACHE_PATH)
        try {
            every { account.world.filestore } returns lib
            // tools/cache667/locs.py 62946: one shape (10) with model 4969; the open chest 62947 has 4970.
            assertEquals(4969, N108Ui.locModel(account.world, 62946))
            assertEquals(4970, N108Ui.locModel(account.world, 62947))
        } finally {
            lib.close()
        }
        val viewer = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab/DropViewer.kt").readText()
        assertTrue("N108Ui.locModel(player.world, DeadmanChestIds.openLoc)" in viewer && "N108Ui.plainModel(" in viewer)
    }

    @Test
    fun `the server reads the same sprite list the cache import wrote - same names, same sizes, so every id matches`() {
        // N274: data/cfg/n108_sprites.json (what N108Ui reads) fell behind data/art/n108/manifest.json (what the import wrote): new
        // sprites came out blank in the game.
        val art = java.io.File("../../data/art/n108/manifest.json").readText()
        val server = java.io.File("../../data/cfg/n108_sprites.json").readText()
        val entry = Regex("\"([a-z0-9_]+)\":\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]")
        fun parse(t: String) = entry.findAll(t).associate { it.groupValues[1] to (it.groupValues[2] + "x" + it.groupValues[3]) }
        assertEquals(parse(art), parse(server))
        assertTrue(parse(server).containsKey("zi_big_chest"))
    }

    @Test
    fun `N282 - no picture in the log, the viewer or the tab gets a count from the client - only the real counter shows`() {
        // The 667 client draws "1" on a stackable item given amount 1 (Morrigan's javelin looked obtained); -1 = no count.
        assertEquals(-1, N108Ui.NO_COUNT)
        val dir = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/quests/tab")
        val call = Regex("""N108Ui\.item\(([^\n]*)\)""")
        val calls = dir.listFiles()!!.filter { it.name.endsWith(".kt") }.flatMap { f -> call.findAll(f.readText()).map { f.name + ": " + it.value }.toList() }
        assertTrue(calls.isNotEmpty())
        assertEquals(emptyList(), calls.filter { it.trimEnd(')').endsWith(", 1") }, "a picture with amount 1")
    }

    @Test
    fun `chance notation and rarity colours follow the design`() {
        assertEquals("Always", N108Ui.chance(1.0))
        assertEquals("1/1,000", N108Ui.chance(1.0 / 1000))
        assertEquals("1/5,000", N108Ui.chance(1.0 / 5000))
        assertEquals("1/12.8", N108Ui.chance(1.0 / 12.8))
        assertEquals("1/25.6", N108Ui.chance(1.0 / 25.6))
        assertEquals("1/600", N108Ui.chance(1.0 / 600))
        assertEquals("1/12.3K", N108Ui.chance(1.0 / 12_300))
        assertEquals("1/146K", N108Ui.chance(1.0 / 145_728))
        assertEquals("afeeee", N108Ui.rarityColour(1.0))
        assertEquals("56e156", N108Ui.rarityColour(1.0 / 25))
        assertEquals("ffed4c", N108Ui.rarityColour(1.0 / 33.4))
        assertEquals("ff863c", N108Ui.rarityColour(1.0 / 200))
        assertEquals("ff6262", N108Ui.rarityColour(1.0 / 1000))
    }

    @Test
    fun `the collection log records only log items, with the kill count, and says so once`() {
        val account = Account()
        CollectionLogPages.install(listOf(CollectionLogPages.Page("kbd", CollectionLogPages.Category.BOSSES, "King Black Dragon", emptyList(), listOf(Items.KBD_HEADS, Items.DRACONIC_VISAGE))))
        try {
            val fresh = CollectionLog.record(account.player, null, listOf(Items.KBD_HEADS to 1, Items.DRAGON_BONES to 1))
            assertEquals(listOf(Items.KBD_HEADS), fresh)
            assertTrue(CollectionLog.has(account.player, Items.KBD_HEADS))
            assertFalse(CollectionLog.has(account.player, Items.DRAGON_BONES))
            assertEquals(emptyList(), CollectionLog.record(account.player, null, listOf(Items.KBD_HEADS to 1)), "the second one is not new")
            assertEquals(2, CollectionLog.entries(account.player).getValue(Items.KBD_HEADS).count)
            assertEquals(Items.KBD_HEADS, CollectionLog.recent(account.player).first().item)
            // Saved as plain text: a new session reads the same log.
            val saved = account.attributes[CollectionLog.ATTR]!!
            assertEquals(2, CollectionLog.parse(saved).getValue(Items.KBD_HEADS).count)
        } finally {
            CollectionLogPages.reset()
        }
    }

    @Test
    fun `N268 N291 N293 - chest and breach pages weapons and armour first and the pets at the bottom, no God Wars pet, no Zaryte bow, Pest Control on Minigames`() {
        val account = Account()
        gg.rsmod.plugins.content.skills.summoning.Pets.load(java.io.File("../../data/cfg/pets/pets.tsv"), java.io.File("../../data/cfg/pets/pet_tiers.tsv"))
        CollectionLogPages.reset()
        DropSources.reset()
        try {
            val pets = gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot.pets().map { it.second }.distinct()
            assertTrue(pets.isNotEmpty(), "the chest gives pets")
            val pages = CollectionLogPages.all(account.player)
            val gwd = gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot.OWN_BOSS_ONLY.mapNotNull { gg.rsmod.plugins.content.skills.summoning.Pets.baseItem(it) }
            assertEquals(4, gwd.size)
            for (key in listOf("chest", "breach")) {
                val page = pages.single { it.key == key }
                assertEquals(pets, page.items.takeLast(pets.size), "$key: the pets at the bottom")
                assertTrue(page.items.none { it in gwd }, "$key: no God Wars pet")
                val gear = page.items.map { DropGroups.category(account.world, it) == DropGroups.Group.GEAR }
                assertTrue(gear.first(), "$key: weapons and armour first")
                assertEquals(gear.take(gear.indexOfFirst { !it }).size, gear.count { it }, "$key: every weapon and armour piece before the rest")
            }
            assertTrue("Zaryte bow" !in CollectionLogPages.OSRS.getValue("nex"), "N293: no Zaryte bow")
            val pc = pages.single { it.key == CollectionLogPages.PEST_CONTROL }
            assertEquals(CollectionLogPages.Category.MINIGAMES, pc.category)
            assertEquals(CollectionLogPages.PEST_CONTROL_ITEMS, pc.items)
            assertTrue(Items.ELITE_VOID_KNIGHT_TOP in pc.items && Items.VOID_SEAL_8 in pc.items)
            assertTrue("Zaryte crossbow" in CollectionLogPages.OSRS.getValue("nex"))
            // A reward the Void Knight hands over is logged, once.
            assertEquals(listOf(Items.VOID_KNIGHT_TOP), CollectionLog.record(account.player, CollectionLogPages.PEST_CONTROL, listOf(Items.VOID_KNIGHT_TOP to 1)))
            assertTrue(CollectionLog.has(account.player, Items.VOID_KNIGHT_TOP))
        } finally {
            CollectionLogPages.reset()
            DropSources.reset()
        }
    }

    @Test
    fun `an old save without the new attributes loads as empty - no kills of its own, empty log, no favourites, no tasks`() {
        val account = Account()
        assertEquals(0, KillCounts.get(account.player, "chest"))
        assertTrue(CollectionLog.entries(account.player).isEmpty())
        assertTrue(CollectionLog.recent(account.player).isEmpty())
        assertTrue(DropViewer.favourites(account.player).isEmpty())
        assertTrue(DropViewer.recent(account.player).isEmpty())
        assertTrue(DeadmanTasks.done(account.player).isEmpty())
        assertEquals(0, DeadmanTasks.earned(account.player))
        // The Barrows chest's own log (real records from before) counts as obtained.
        account.attributes[gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.COLLECTION_LOG] = "${Items.AHRIMS_HOOD}"
        assertTrue(CollectionLog.has(account.player, Items.AHRIMS_HOOD))
    }

    @Test
    fun `favourites and recently viewed are kept on the account`() {
        val account = Account()
        assertEquals("drop_viewer_favourites", DropViewer.FAVOURITES.persistenceKey)
        assertEquals("drop_viewer_recent", DropViewer.RECENT.persistenceKey)
        account.attributes[DropViewer.FAVOURITES] = "npc:50;breach:14439"
        assertEquals(listOf("npc:50", "breach:14439"), DropViewer.favourites(account.player))
        account.attributes[DropViewer.RECENT] = "npc:50;npc:6260;npc:2883"
        assertEquals(3, DropViewer.recent(account.player).size)
    }

    @Test
    fun `a Deadman Task pays once, only the valid PvP judge counts a kill, the points stop at the maximum`() {
        val account = Account()
        val dp = StoreCatalogue.Currency.DEADMAN.attr
        DeadmanTasks.onPvpKill(account.player, victimSkulled = true, multi = false)
        assertTrue("e_pk1" in DeadmanTasks.done(account.player))
        val afterFirst = account.attributes[dp] ?: 0
        assertEquals(DeadmanTasks.DP.getValue(DeadmanTasks.Tier.EASY), afterFirst)
        // Another kill: First blood is done and pays nothing again; Player hunter counts on.
        DeadmanTasks.onPvpKill(account.player, victimSkulled = false, multi = false)
        assertEquals(afterFirst, account.attributes[dp] ?: 0)
        assertEquals(2, DeadmanTasks.progress(account.player, DeadmanTasks.TASKS.first { it.id == "m_pk5" }))
        // Saturating: at the maximum it stays there.
        account.attributes[dp] = Int.MAX_VALUE - 1
        repeat(3) { DeadmanTasks.onPvpKill(account.player, victimSkulled = false, multi = false) }
        assertEquals(Int.MAX_VALUE, account.attributes[dp])
        // Every task id is unique and every tier has tasks.
        assertEquals(DeadmanTasks.TASKS.size, DeadmanTasks.TASKS.map { it.id }.toSet().size)
        DeadmanTasks.Tier.values().forEach { assertTrue(DeadmanTasks.tierTasks(it).isNotEmpty()) }
        assertNotEquals(0, DeadmanTasks.TASKS.size)
    }

    @Test
    fun `a skulled minute counts only while the skull counts down in a dangerous area - never standing still or in a guarded city`() {
        val account = Account()
        val timers = TimerMap()
        every { account.player.timers } returns timers
        every { account.player.tile } returns Tile(3100, 3600, 0)
        assertFalse(DeadmanTasks.skulledMinuteCounts(account.player), "no skull")
        timers[SKULL_ICON_DURATION_TIMER] = PvpSkull.SKULL_DURATION_CYCLES
        assertTrue(DeadmanTasks.skulledMinuteCounts(account.player), "skulled and moving in the Wilderness")
        // PvpSkull pauses the countdown after a minute on one tile: an AFK skull earns nothing.
        timers.pause(SKULL_ICON_DURATION_TIMER)
        assertFalse(DeadmanTasks.skulledMinuteCounts(account.player), "AFK")
        timers.resume(SKULL_ICON_DURATION_TIMER)
        every { account.player.tile } returns Tile(3212, 3428, 0)
        assertFalse(DeadmanTasks.skulledMinuteCounts(account.player), "inside Varrock")
    }

    companion object {
        private val CACHE_PATH = Paths.get("..", "..", "data", "cache").toFile().toString()
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(CACHE_PATH))
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
