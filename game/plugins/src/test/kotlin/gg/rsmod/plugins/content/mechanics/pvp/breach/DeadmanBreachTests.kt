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
        val batch = OsrsNpcImportTool.BATCHES.getValue("deadman-breach") + OsrsNpcImportTool.BATCHES.getValue("deadman-jad-rek")
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
    fun `every sound alias points at a rev-667 combat-sound row and only at breach monsters`() {
        val rows = com.google.gson.JsonParser().parse(Paths.get("..", "..", "data", "cfg", "npcs", "combat-sounds.json").toFile().readText())
            .asJsonArray.map { it.asJsonObject["id"].asInt }.toSet()
        BreachMonsters.SOUND_ALIASES.forEach { (id, source) ->
            assertTrue(id in BreachMonsters.BY_ID, "alias $id is not a breach monster")
            assertTrue(source in rows, "alias $id -> $source has no combat-sound row")
        }
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
        assertEquals(133, BreachLoot.RARE_TRIGGER, "listed x/9576 weights: about 1/72")
        // Owner 2026-09-19: no trinkets - their slots are gone, the six weapons (10/10/3/3/3/3) fill the rare table.
        assertEquals(32, BreachLoot.RARE_TOTAL)
        val trinkets = BreachLoot.EXCLUDED_TRINKETS
        assertEquals(4, trinkets.size)
        assertTrue(BreachLoot.RARE.none { it.item in trinkets } && BreachLoot.REGULAR.none { it.item in trinkets } && BreachLoot.MEGA_RARE.none { it in trinkets })
        val trinketRandom = Random(11)
        repeat(200_000) { assertTrue(BreachLoot.roll(trinketRandom).none { it.item in trinkets }, "a trinket dropped") }
        assertEquals(16, BreachLoot.ELIGIBLE)
        val ids = (BreachLoot.REGULAR.map { it.item } + BreachLoot.RARE.map { it.item } + BreachLoot.MEGA_RARE + ItemIds.CHITIN)
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

    /** Owner 2026-09-19 "sounds, animations, everything": no breach monster may fight silently. */
    @Test
    fun `every breach monster has combat sounds and every sound id exists in the cache`() {
        val rows = BreachMonsters.attackRows(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile()).associateBy { it.id }
        fun synthExists(id: Int) = store.data(4, id, 0) != null
        val failures = mutableListOf<String>()
        BreachMonsters.ROSTER.forEach { m ->
            val sectionSounds = rows.getValue(m.id).attacks.flatMap { (it.sounds + it.targetSounds + it.impactSounds).map { s -> s.id } }
            val row = BreachMonsters.SOUND_ROWS[m.id]
            val aliased = m.id in BreachMonsters.SOUND_ALIASES
            if (row == null && !aliased) failures += "${m.name} ${m.id}: no combat-sound row or alias"
            val attackSound = (row?.first ?: -1) >= 0 || sectionSounds.isNotEmpty() || aliased || m.id == 14470 // Bloat: flies only
            if (!attackSound) failures += "${m.name} ${m.id}: silent attack"
            (listOfNotNull(row?.first, row?.second, row?.third) + sectionSounds).filter { it >= 0 }.forEach {
                if (!synthExists(it)) failures += "${m.name} ${m.id}: sound $it absent from the cache"
            }
        }
        if (!synthExists(BreachMonsters.SFX_BLOAT_FLIES)) failures += "bloat flies sound absent"
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** OSRS Wiki "Night beast": initial magic, melee when it can reach, magic otherwise, 3x3 Fire Blast special. */
    @Test
    fun `night beast fights like the regular variant with its 3x3 fireball special`() {
        val row = BreachMonsters.attackRows(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile()).single { it.id == 14457 }
        assertEquals("breach_night_beast", row.combatDef)
        val byId = row.attacks.associateBy { it.id }
        assertEquals(setOf("melee", "magic", "fireball"), byId.keys)
        assertEquals("breach_nb_melee", byId.getValue("melee").condition)
        assertEquals(300, byId.getValue("melee").hits.single().max, "melee max 30")
        assertEquals("breach_nb_magic", byId.getValue("magic").condition)
        assertEquals(80, byId.getValue("magic").hits.single().max, "standard magic max 8 (regular variant, Mod Ash)")
        val fireball = byId.getValue("fireball")
        assertEquals("breach_nb_fireball", fireball.condition)
        assertEquals(1, fireball.multiTargetRadius, "3x3 around the target")
        assertTrue(fireball.hits.isEmpty(), "damage is floor(current HP / 3) from the plugin, never a rolled hit")
    }

    /** OSRS Wiki Deadman monster pages: demonbane vulnerability and the monsters that run after their target. */
    @Test
    fun `demonbane vulnerability and running follow the deadman pages`() {
        assertEquals(mapOf(14446 to 1, 14449 to 1, 14451 to 1, 14458 to 100), BreachMonsters.DEMONBANE_VULNERABILITY)
        assertEquals(setOf("Durial321", "I DSCIM YOU"), BreachMonsters.RUNNERS.map { BreachMonsters.BY_ID.getValue(it).name }.toSet())
        assertEquals(15669, BreachMonsters.BY_ID.getValue(14461).attackAnim, "Jaguar warrior: OSRS NPC_JAGUAR_RANGER_CLAWS_ATTACK")
    }

    /** OSRS Wiki "Blighted overload": formulas, doses and the imported items. */
    @Test
    fun `blighted overload follows the wiki`() {
        assertEquals(22, BlightedOverload.attackStrengthBoost(99))
        assertEquals(16, BlightedOverload.rangedBoost(99))
        assertEquals(10, BlightedOverload.magicBoost(99))
        assertEquals(10, BlightedOverload.defenceDrain(99))
        assertEquals(89, BlightedOverload.drainedDefence(99, 99), "drain 10, floor 90% = 89")
        assertEquals(89, BlightedOverload.drainedDefence(89, 99), "already at 90%: no further decay")
        assertEquals(8, BlightedOverload.attackStrengthBoost(1))
        assertEquals(7, BlightedOverload.rangedBoost(1))
        val names = listOf("Blighted overload (4)", "Blighted overload (3)", "Blighted overload (2)", "Blighted overload (1)")
        BlightedOverload.DOSES.forEachIndexed { i, id ->
            assertEquals(names[i], definitions.get(ItemDef::class.java, id).name)
            assertEquals(4 - i, BlightedOverload.doseOf(id))
            assertTrue(definitions.get(ItemDef::class.java, id).noteLinkId == id + 1, "noted copy of $id")
        }
        assertEquals(BlightedOverload.DOSES[1], BlightedOverload.afterSip(BlightedOverload.DOSES[0]))
        assertEquals(gg.rsmod.plugins.api.cfg.Items.VIAL, BlightedOverload.afterSip(BlightedOverload.DOSES[3]))
        assertEquals(500, BlightedOverload.DURATION_TICKS, "five minutes")
        assertEquals(25, BlightedOverload.REAPPLY_TICKS, "15 seconds")
        assertEquals(10, BlightedOverload.DAMAGE_PER_HIT * BlightedOverload.DAMAGE_HITS, "10 damage over 6 seconds")
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
