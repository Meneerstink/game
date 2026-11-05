package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.DamageMap
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.content.areas.wilderness.WildernessBossTestSupport
import gg.rsmod.plugins.content.drops.DropEntry
import gg.rsmod.plugins.content.drops.DropTable
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.quests.tab.DropSources
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

internal object WildernessNpcTestSupport {
    private val loader = URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader)
    val defs get() = WildernessBossTestSupport.definitions
    class Fixture {
        val world = mockk<World>(relaxed = true)
        val repo = PluginRepository(world)
        val player = mockk<Player>(relaxed = true)
        val attr = AttributeMap()
        init {
            every { world.definitions } returns defs
            every { world.plugins } returns repo
            every { world.players } returns PawnList(arrayOfNulls<Player>(16))
            every { world.gameContext.npcStatCount } returns 5
            every { world.spawn(any<Npc>()) } returns true
            every { player.world } returns world
            every { player.attr } returns attr
            every { player.timers } returns TimerMap()
            every { player.equipmentBonuses } returns IntArray(18)
            every { player.equipment[any<Int>()] } returns null
            every { player.isOnline } returns true
            every { player.username } returns "n522killer"
            every { player.isDead() } returns false
            every { player.tile } returns Tile(3200, 3800)
            every { player.getVarp(1913) } returns 10 // MM2 complete, independent report alternative tested separately
            listOf("Dark_warrior", "Scorpion", "King_scorpion", "Zombie_pirate", "Rogue", "Lava_dragon", "Ent", "Mammoth", "Elder_chaos_druid", "Runite_golem").forEach {
                loader.loadClass("gg.rsmod.plugins.content.areas.wilderness.npcs.${it}_plugin")
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(repo, world, mockk<Server>(relaxed = true))
            }
        }

        fun npc(id: Int, tile: Tile = Tile(3200, 3800)): Npc {
            val npc = mockk<Npc>(relaxed = true)
            val attrs = AttributeMap()
            val damage = DamageMap()
            every { npc.id } returns id
            every { npc.world } returns world
            every { npc.attr } returns attrs
            every { npc.tile } returns tile
            every { npc.spawnTile } returns tile
            every { npc.damageMap } returns damage
            every { npc.combatDef } returns repo.allNpcCombatDefs().getValue(id)
            every { npc.equipmentBonuses } returns IntArray(18)
            every { npc.isSpawned() } returns true
            every { npc.isDead() } returns false
            every { npc.getSize() } returns defs.get(NpcDef::class.java, id).size
            return npc
        }

        fun view(id: Int): List<DropSources.Line> {
            val def = defs.get(NpcDef::class.java, id)
            val source = DropSources.forNpc(player, id)!! // use the actual registry/grouping path seen by players
            val view = DropSources.view(player, source, 1.0)
            fun flatten(line: DropSources.Line): List<DropSources.Line> =
                if (line.tag == DropSources.Tag.TABLE) line.kids.flatMap(::flatten) else listOf(line)
            return view.always + view.lines.flatMap(::flatten)
        }
    }

    fun leaves(table: DropTable): List<DropEntry> = table.entries.flatMap {
        val d = it.drop
        if (d is DropEntry.TableDrop) leaves(d.table) else listOf(d)
    }

    fun hit(vararg amounts: Int): Hit = Hit.Builder().apply { amounts.forEach { addHit(it, 0) } }.build()
}

/** Each concrete family runs the real compiled script plus all variant-specific infobox/drop rows from the research report. */
abstract class WildernessNpcContractTests(private val ids: List<Int>) {
    @Test fun `compiled definitions preserve all sourced stats bonuses animations aggro and resistance`() {
        val f = WildernessNpcTestSupport.Fixture()
        val rows = File("src/test/resources/wilderness-npcs/stats.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
        ids.forEach { id ->
            val r = rows.single { it[0].toInt() == id }
            val d = f.repo.allNpcCombatDefs().getValue(id)
            assertEquals(r[1].toInt(), d.lifepoints, "$id HP x10 DSL conversion")
            assertEquals(r.slice(2..6).map(String::toInt), listOf(NpcSkills.ATTACK,NpcSkills.STRENGTH,NpcSkills.DEFENCE,NpcSkills.MAGIC,NpcSkills.RANGED).map { d.stats[it] }, "$id levels")
            assertEquals(r[7].toInt(), d.attackSpeed)
            assertEquals(if (r[8] == "ONBEKEND") 0 else r[8].toInt(), d.respawnDelay, "$id respawn; unknown is disabled")
            assertEquals(r[9].toBoolean(), d.aggressiveRadius > 0, "$id aggro")
            if (id == 15102) assertEquals(Int.MAX_VALUE, d.aggressiveTimer)
            if (!r[9].toBoolean()) assertEquals(Int.MIN_VALUE, d.aggressiveTimer)
            assertEquals(r[10] == "100", d.poisonImmunity)
            assertEquals(r[11] == "100", d.venomImmunity)
            assertTrue(d.poisonDamage <= 0 && d.venomDamage <= 0)
            assertEquals(r[13].toInt(), d.attackAnimation)
            assertEquals(r[14].toInt(), d.blockAnimation)
            assertEquals(listOf(r[15].toInt()), d.deathAnimation)
            // NPC melee attack/strength occupy the same indices the DSL's dedicated bonus setters use.
            val slots = listOf(gg.rsmod.plugins.api.ext.NPC_ATTACK_BONUS_INDEX, gg.rsmod.plugins.api.ext.NPC_STRENGTH_BONUS_INDEX,
                BonusSlot.DEFENCE_STAB.id, BonusSlot.DEFENCE_SLASH.id, BonusSlot.DEFENCE_CRUSH.id, BonusSlot.DEFENCE_MAGIC.id, BonusSlot.DEFENCE_RANGED.id)
            assertEquals(r.slice(16..22).map(String::toInt), slots.map { d.bonuses[it] }, "$id bonuses")
        }
    }

    @Test fun `every representable report drop has the exact quantity and fraction in the real Drop Viewer`() {
        val f = WildernessNpcTestSupport.Fixture()
        ids.forEach { id ->
            val actual = f.view(id)
            val rows = File("src/test/resources/wilderness-npcs/$id.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
            val expected = rows.groupBy { it[0].toInt() to it[1] }
            expected.forEach { (key, entries) ->
                val wanted = entries.sumOf { row -> row[2].split('/').let { it[0].toDouble()/it[1].toDouble() } }.coerceAtMost(1.0)
                val found = actual.filter { it.item == key.first && it.quantity == key.second }.sumOf { it.normal }
                assertEquals(wanted, found, 1e-10, "$id ${entries.map { it[3] }}")
            }
            val tables = DropTableFactory.tables(f.player, id)!!
            tables.forEach { table ->
                fun check(t: DropTable) {
                    if (t.name != "guaranteed") assertTrue((t.entries.lastOrNull()?.index ?: 0) <= t.totalSlots, "$id ${t.name} overfilled")
                    t.entries.forEach { (it.drop as? DropEntry.TableDrop)?.table?.let(::check) }
                }
                check(table)
            }
            val leaves = tables.flatMap(WildernessNpcTestSupport::leaves)
            val itemIds = leaves.flatMap { when(it) {
                is DropEntry.ItemDrop -> listOf(it.item.id)
                is DropEntry.ItemRangeDrop -> listOf(it.item.id)
                is DropEntry.MultiDrop -> it.items.map { item -> item.id }
                else -> emptyList()
            } }
            assertTrue(itemIds.none { it in setOf(Items.LOOTING_BAG, Items.LOOTING_BAG_OPEN) })
            assertTrue(actual.none { it.item == Items.RUNITE_ORE && id == 15103 }, "Golem ore is skilling loot")
            // Missing report items are deliberately nothing(), never substituted with unrelated items.
            val sourceItems = expected.keys.map { it.first }.toSet()
            assertTrue(itemIds.all { it in sourceItems }, "$id unexpected kill loot ${itemIds.toSet()-sourceItems}")
        }
    }
}
