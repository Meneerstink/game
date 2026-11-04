package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.Server
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.DamageMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.items.ItemActionGuard
import gg.rsmod.plugins.content.quests.tab.CollectionLogPages
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.quests.tab.KillCounts
import gg.rsmod.plugins.content.skills.summoning.Pets
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Binds the actual compiled boss scripts; no server, network, accounts, region import or cache mutation. */
internal object WildernessBossTestSupport {
    // Drop builders load their nested classes lazily, after script construction. Keep this loader alive for the test JVM.
    private val scripts = URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader)
    val definitions: DefinitionSet by lazy {
        val path = System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toString()
        val defs = DefinitionSet()
        val cache = CacheLibrary(path)
        try {
            defs.load(cache, NpcDef::class.java)
            defs.load(cache, ItemDef::class.java)
            defs.load(cache, ObjectDef::class.java)
            defs.load(cache, AnimDef::class.java)
        } finally { cache.close() }
        defs
    }

    class Fixture {
        val world = mockk<World>(relaxed = true)
        val repo = PluginRepository(world)
        val players = PawnList(arrayOfNulls<Player>(64))
        val attributes = AttributeMap()
        val player = mockk<Player>(relaxed = true)
        val counts = mutableMapOf<Int, Int>()
        val messages = ArrayList<String>()

        init {
            every { world.definitions } returns definitions
            every { world.plugins } returns repo
            every { world.players } returns players
            every { player.world } returns world
            every { player.attr } returns attributes
            every { player.timers } returns TimerMap()
            every { player.combatLevel } returns 126
            every { player.isOnline } returns true
            every { player.getNpcKillCount(any()) } answers { counts[firstArg()] ?: 0 }
            every { player.incrementNpcKillCount(any(), any()) } answers {
                val id = firstArg<Int>()
                counts[id] = (counts[id] ?: 0) + secondArg<Int>()
            }
            attributes[ItemActionGuard.STATUS_CAPTURE] = messages
                listOf("Wilderness_bosses_plugin", "Old_wilderness_bosses_plugin", "Scorpia_plugin", "Venenatis_plugin", "Revenant_cave_revenants_plugin").forEach { name ->
                    scripts.loadClass("gg.rsmod.plugins.content.areas.wilderness.bosses.$name")
                        .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                        .newInstance(repo, world, mockk<Server>(relaxed = true))
                }
            val pets = Paths.get("..", "..", "data", "cfg", "pets").toFile()
            Pets.load(File(pets, "pets.tsv"), File(pets, "pet_tiers.tsv"))
            DropSources.reset()
            CollectionLogPages.reset()
        }

        fun npc(id: Int, tile: Tile = Tile(3200, 3800)): Npc {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.id } returns id
            every { npc.world } returns world
            every { npc.attr } returns AttributeMap()
            every { npc.damageMap } returns DamageMap()
            every { npc.tile } returns tile
            every { npc.getSize() } returns definitions.get(NpcDef::class.java, id).size
            every { npc.isSpawned() } returns true
            every { npc.isDead() } returns false
            return npc
        }
    }

    /** Every explicit Wiki row that the server can represent. Missing content is an explicit cache/dependency handoff. */
    fun assertDrops(f: Fixture, id: Int, wiki: String) {
        val source = DropSources.forNpc(f.player, id)!!
        val view = DropSources.view(f.player, source, 1.0)
        // The Wiki lists RDT sub-tables separately. Do not add their talisman/coin rolls to an explicit main-table row.
        val actual = view.always + view.lines.filter { it.tag != DropSources.Tag.TABLE }
        fun canonical(name: String): String = name.lowercase().replace("steel cannonball", "cannonball")
            .replace("grimy ranarr weed", "grimy ranarr").replace("antidote++", "antipoison++")
            .replace("zamorak monk top", "zamorak robe").replace("zamorak monk bottom", "zamorak robe")
            .replace(" (noted)", "").replace(" ", "").replace("'", "")
        fun q(text: String): String = text.replace(" (noted)", "").replace(",", "").replace("–", "-")
        // Looting bag: out of the game by owner decision (N424 2026-10-29, N561 2026-11-04) - it must never drop.
        val unsupported = setOf("Wilderness crabs teleport", "Dark fishing bait", "Ensouled scorpion head", "Brimstone key", "Skeleton champion scroll", "Looting bag")
        assertTrue(actual.none { it.item == Items.LOOTING_BAG || it.item == Items.LOOTING_BAG_OPEN }, "$wiki drops no looting bag (N561)")
        val expected = File("src/test/resources/wilderness-bosses/${wiki.replace(' ', '_')}.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
            .filter { it[0] !in unsupported }
            .filterNot { it[0] == "Bones" && wiki in setOf("Callisto", "Vetion", "Venenatis") } // group recipient, not solo MvP
            .groupBy { Triple(canonical(it[0]), q(it[1]), "(noted)" in it[1]) }
        expected.forEach { (key, rows) ->
            fun chance(s: String): Double = if (s == "Always") 1.0 else s.split('/').let { it[0].replace(",", "").toDouble() / it[1].replace(",", "").toDouble() }
            // Wiki main rows are conditional on failing the pre-roll (Wiki Drop table, Pre-roll section).
            // These fractions come from the independently recorded Wiki unique rows, not our drop builder.
            val mainWeight = when (wiki) {
                "Chaos Fanatic", "Crazy archaeologist", "Scorpia" -> 127.0 / 128
                "Callisto", "Vetion", "Venenatis" -> 1 - (1.0 / 196 + 1.0 / 256 + 1.0 / 256 + 1.0 / 360 + 1.0 / 512)
                "Spindel" -> 1 - (1.0 / 358 + 1.0 / 358 + 1.0 / 618 + 1.0 / 716 + 1.0 / 912)
                else -> 1.0
            }
            // Wiki rounds Scorpia's effective 127/128 * 1/2000 to 1/2015.75.
            val wanted = when (rows.first()[0]) {
                "Scorpia's offspring" -> 127.0 / 256000
                "Curved bone" -> (399.0 / 400) / 5000 // Wiki rounds Mod Ash's exact formula to 1/5012.5.
                else -> rows.sumOf { chance(it[2]) * if (it[3] == "main") mainWeight else 1.0 }.coerceAtMost(1.0)
            }
            val found = actual.filter { row ->
                val name = when (row.item) {
                    Items.CLUE_SCROLL_HARD -> "Clue scroll (hard)"
                    Items.CLUE_SCROLL_ELITE -> "Clue scroll (elite)"
                    else -> DropSources.itemName(f.world, row.item)
                }
                canonical(name) == key.first && q(row.quantity) == key.second && definitions.get(ItemDef::class.java, row.item).noted == key.third
            }.sumOf { it.normal }
            assertEquals(wanted, found, 1e-10, "$wiki ${rows.map { it[0] }.distinct()} qty ${key.second}")
        }
    }
}

/** The same aggro/stats/kc/drop contract, executed separately for each named boss below. */
abstract class WildernessBossContractTests(
    private val id: Int,
    private val hp: Int,
    private val levels: List<Int>,
    private val speed: Int,
    private val respawn: Int,
    private val wiki: String,
    private val petRate: Int?,
) {
    @Test fun `compiled combat definition is aggressive at every player level and has the sourced stats`() {
        val f = WildernessBossTestSupport.Fixture()
        val def = f.repo.allNpcCombatDefs().getValue(id)
        assertTrue(def.aggressiveRadius > 0)
        assertEquals(Int.MAX_VALUE, def.aggressiveTimer)
        assertEquals(hp, def.lifepoints)
        assertEquals(levels, listOf(NpcSkills.ATTACK, NpcSkills.STRENGTH, NpcSkills.DEFENCE, NpcSkills.MAGIC, NpcSkills.RANGED).map { def.stats[it] })
        assertEquals(speed, def.attackSpeed)
        assertEquals(respawn, def.respawnDelay)
    }

    @Test fun `an engine counted kill sends exactly one sourced count and an unfinished phase sends none`() {
        val f = WildernessBossTestSupport.Fixture()
        val source = DropSources.forNpc(f.player, id)!!
        CollectionLogPages.install(listOf(CollectionLogPages.Page(source.key, CollectionLogPages.Category.BOSSES, source.name, listOf(source.key), listOf(1))))
        val deathId = if (id == OldWildernessBosses.VETION) OldWildernessBosses.VETION_REBORN else id
        f.counts[deathId] = 1234
        if (id == OldWildernessBosses.VETION) {
            f.counts[id] = 2000 // includes incomplete old first phases; must not inflate the completed count
            KillCounts.onNpcKilled(f.player, id)
            assertTrue(f.messages.isEmpty())
        }
        KillCounts.onNpcKilled(f.player, deathId)
        assertEquals(listOf("Your ${source.name} kill count is: <col=ff0000>1,234</col>."), f.messages)
        assertEquals(1234, f.counts[deathId], "notification never increments the engine counter a second time")
    }

    @Test fun `every representable wiki drop has its solo normal chance and quantity in the real Drop Viewer`() {
        val f = WildernessBossTestSupport.Fixture()
        WildernessBossTestSupport.assertDrops(f, id, wiki)
        val expectedPet = if (id == gg.rsmod.plugins.api.cfg.Npcs.SCORPIA) 127.0 / 256000 else petRate?.let { 1.0 / it } ?: 0.0
        assertEquals(expectedPet, Pets.dropChance(id), 1e-12)
    }
}
