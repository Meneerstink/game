package gg.rsmod.plugins.content.mechanics.pvp.breach

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.tools.importer.OsrsNpcImportTool
import gg.rsmod.plugins.content.combat.attack.AnimSkeletons
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS permanent Deadman breaches, proven for the whole roster against the real rev-667 cache (not one monster).
 */
class DeadmanBreachTests {
    @Test
    fun `the roster is exactly the imported deadman-breach npc batch, one local id each`() {
        val batch = OsrsNpcImportTool.BATCHES.getValue("deadman-breach")
        assertEquals(batch.toSet(), BreachMonsters.ROSTER.map { it.osrsId }.toSet(), "roster OSRS ids vs import batch")
        assertEquals(BreachMonsters.ROSTER.size, BreachMonsters.ROSTER.map { it.id }.toSet().size, "duplicate local ids")
        assertEquals(33, BreachMonsters.SPAWNABLE.size, "40 Permanent breach monsters minus the 7 skeletal-animation ones")
        assertEquals(6, BreachMonsters.SUMMONS.size, "six Zemouregal Summon versions")
    }

    @Test
    fun `every breach npc exists in the cache with its OSRS name and an Attack option`() {
        val failures = mutableListOf<String>()
        BreachMonsters.ROSTER.forEach { m ->
            val def = definitions.getNullable(NpcDef::class.java, m.id)
            when {
                def == null -> failures += "${m.name} ${m.id}: npc definition absent"
                def.name != m.name -> failures += "${m.name} ${m.id}: cache name '${def.name}'"
                def.options.none { it.equals("Attack", ignoreCase = true) } -> failures += "${m.name} ${m.id}: no Attack option ${def.options.toList()}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `every attack, block, death and section animation animates the monster's own skeleton`() {
        val rows = BreachMonsters.attackRows(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile()).associateBy { it.id }
        val failures = mutableListOf<String>()
        BreachMonsters.ROSTER.forEach { m ->
            val anims = listOf(m.attackAnim, m.blockAnim, m.deathAnim) + rows.getValue(m.id).attacks.map { it.anim }
            anims.filter { it >= 0 }.distinct().forEach { anim ->
                if (definitions.getNullable(AnimDef::class.java, anim) == null) {
                    failures += "${m.name} ${m.id}: anim $anim absent"
                } else if (!AnimSkeletons.fits(definitions, store, m.id, anim)) {
                    failures += "${m.name} ${m.id}: anim $anim does not fit its skeleton"
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} animation failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `every section gfx and projectile exists and every breach max hit is the wiki's`() {
        val rows = BreachMonsters.attackRows(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile()).associateBy { it.id }
        val failures = mutableListOf<String>()
        rows.values.forEach { row ->
            row.attacks.forEach { a ->
                ((a.gfx + a.targetGfx + a.impactGfx + a.missGfx).map { it.id } + a.projectiles.map { it.id }).filter { it >= 0 }.forEach { id ->
                    if (definitions.getNullable(SpotAnimDef::class.java, id) == null) failures += "${row.name}.${a.id}: gfx $id absent"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        // Wiki "Permanent" max hits (x10 in the attack table).
        val expected =
            mapOf(
                14438 to ("melee" to 260), 14439 to ("melee" to 250), 14441 to ("magic" to 260), 14442 to ("melee" to 600),
                14443 to ("melee" to 270), 14444 to ("melee" to 460), 14445 to ("melee" to 290), 14446 to ("magic" to 430),
                14447 to ("melee" to 430), 14448 to ("magic" to 430), 14449 to ("melee" to 270), 14453 to ("melee" to 490),
                14458 to ("melee" to 290), 14462 to ("melee" to 780), 14462 to ("magic" to 1080), 14467 to ("magic" to 260),
            )
        expected.forEach { (id, pair) ->
            val (section, max) = pair
            assertEquals(max, rows.getValue(id).attacks.single { it.id == section }.hits.maxOf { it.max }, "npc $id $section")
        }
        assertEquals(3, rows.getValue(14458).attacks.single().hits.size, "Cerberus: three hitsplats per attack")
        assertEquals(4, rows.getValue(14461).attacks.single().hits.size, "Jaguar warrior: 21 (x4)")
    }

    @Test
    fun `breach and boss spawn locs exist with the OSRS names`() {
        assertEquals("Breach", definitions.get(ObjectDef::class.java, DeadmanBreach.BREACH_LOC).name)
        assertEquals("Boss Spawn", definitions.get(ObjectDef::class.java, DeadmanBreach.BOSS_SPAWN_LOC).name)
        assertTrue(definitions.getNullable(SpotAnimDef::class.java, BreachMonsters.GFX_BREACH_PROJECTILE) != null)
    }

    @Test
    fun `schedule is every four hours from Saturday 02 00 to Sunday 22 00 GMT`() {
        var t = ZonedDateTime.of(2026, 9, 18, 12, 0, 0, 0, ZoneOffset.UTC) // Friday
        val slots = mutableListOf<ZonedDateTime>()
        repeat(13) {
            t = DeadmanBreach.nextStart(t)
            slots += t
        }
        val weekend = slots.take(12)
        assertEquals(listOf(2, 6, 10, 14, 18, 22, 2, 6, 10, 14, 18, 22), weekend.map { it.hour })
        assertEquals(List(6) { DayOfWeek.SATURDAY } + List(6) { DayOfWeek.SUNDAY }, weekend.map { it.dayOfWeek })
        assertEquals(ZonedDateTime.of(2026, 9, 26, 2, 0, 0, 0, ZoneOffset.UTC), slots[12], "after Sunday 22:00 the next is Saturday 02:00")
    }

    @Test
    fun `locations are the wiki tables - single-way sites have three spawners, multicombat one`() {
        val locs = DeadmanBreach.loadLocations(Paths.get("..", "..", "data", "cfg", "deadman", "breach-locations.json").toFile())
        assertEquals(72, locs.localised.size)
        assertEquals(9, locs.regional.size)
        locs.localised.forEach { assertEquals(if (it.multi) 1 else 3, it.spawners.size, it.name) }
        assertTrue(locs.regional.all { it.points.size >= DeadmanBreach.REGIONAL_MAX_NPCS })
    }

    @Test
    fun `loot table weights, eligibility and chitin replacement follow the wiki`() {
        assertEquals(BreachLoot.REGULAR_TOTAL, BreachLoot.REGULAR.sumOf { it.weight })
        assertEquals(133, BreachLoot.RARE_TOTAL, "listed x/9576 weights: about 1/72")
        assertEquals(16, BreachLoot.ELIGIBLE)
        val ids = (BreachLoot.REGULAR.map { it.item } + BreachLoot.RARE.map { it.item }.filter { it != -1 } + BreachLoot.MEGA_RARE + ItemIds.CHITIN)
        ids.forEach { assertTrue(definitions.getNullable(ItemDef::class.java, it) != null, "item $it absent") }
        BreachLoot.REGULAR.filter { it.noted }.forEach { assertTrue(definitions.get(ItemDef::class.java, it.item).noteLinkId > 0, "item ${it.item} has no note") }
        val random = Random(7)
        var chitin = 0
        val kills = 20000
        repeat(kills) {
            val drops = BreachLoot.roll(random)
            val regular = drops.takeLast(BreachLoot.REGULAR_ROLLS)
            assertEquals(BreachLoot.REGULAR_ROLLS, regular.size)
            if (regular.any { d -> d.item == ItemIds.CHITIN }) chitin++
        }
        assertTrue(chitin in (kills * 0.47).toInt()..(kills * 0.53).toInt(), "chitin replaces a regular drop 50% of the time: $chitin / $kills")
    }

    companion object {
        private lateinit var store: CacheLibrary
        private val definitions = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun load() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            store.close()
        }
    }
}
