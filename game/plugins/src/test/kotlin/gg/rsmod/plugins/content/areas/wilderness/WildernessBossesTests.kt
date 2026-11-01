package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.areas.wilderness.bosses.ChaosFanaticCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.CrazyArchaeologistCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.ScorpiaCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaves
import gg.rsmod.plugins.content.areas.wilderness.bosses.ScorpiaPit
import gg.rsmod.plugins.content.areas.wilderness.bosses.VolcanicForge
import gg.rsmod.plugins.content.quests.tab.CollectionLogPages
import gg.rsmod.plugins.content.skills.summoning.Pets
import java.io.File
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N522 bundle 1: the OSRS Chaos Fanatic and Crazy archaeologist - the imported cache definitions, the OSRS Wiki attack patterns,
 * the blast areas, the pet rate per boss and the Collection log lists.
 */
class WildernessBossesTests {
    private val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())

    @AfterTest
    fun close() {
        library.close()
    }

    @Test
    fun `the imported bosses, the clouds and the fedora decode from the game cache with their OSRS names and options`() {
        val definitions = DefinitionSet()
        definitions.load(library, NpcDef::class.java)
        definitions.load(library, ItemDef::class.java)
        val npcs =
            listOf(
                Triple(Npcs.CHAOS_FANATIC, "Chaos Fanatic", listOf(null, "Attack", null, null, null)),
                Triple(Npcs.CRAZY_ARCHAEOLOGIST, "Crazy archaeologist", listOf(null, "Attack", null, null, null)),
                Triple(Npcs.MINIATURE_CHAOTIC_CLOUDS, "Miniature chaotic clouds", listOf<String?>(null, null, null, null, null)),
            )
        npcs.forEach { (id, name, ops) ->
            val def = definitions.get(NpcDef::class.java, id)
            assertEquals(name, def.name, "$id")
            assertEquals(ops, def.options.map { it?.takeIf { o -> o.isNotEmpty() } }, "$id $name options")
            assertTrue(def.basId >= 0, "$id $name has a BAS")
        }
        assertEquals("Fedora", definitions.get(ItemDef::class.java, Items.FEDORA).name)
        assertEquals(Items.FEDORA, definitions.get(ItemDef::class.java, Items.FEDORA_NOTED).noteLinkId)
    }

    @Test
    fun `the Chaos Fanatic follows the OSRS Wiki pattern - 12 red attacks after an explosion, else 10, 4 and 1 out of 15`() {
        assertEquals(10, (0..14).count { ChaosFanaticCombat.choose(0, it) == ChaosFanaticCombat.Attack.RED })
        assertEquals(4, (0..14).count { ChaosFanaticCombat.choose(0, it) == ChaosFanaticCombat.Attack.EXPLOSION })
        assertEquals(1, (0..14).count { ChaosFanaticCombat.choose(0, it) == ChaosFanaticCombat.Attack.DISARM })
        (1..ChaosFanaticCombat.COOLDOWN_ATTACKS).forEach { left ->
            assertTrue((0..14).all { ChaosFanaticCombat.choose(left, it) == ChaosFanaticCombat.Attack.RED }, "cooldown $left")
        }
        assertEquals(12, ChaosFanaticCombat.COOLDOWN_ATTACKS)
        assertEquals(6, ChaosFanaticCombat.SHOUTS.size)
    }

    @Test
    fun `the Crazy archaeologist kicks only from a cardinal neighbour and his books blast a 3x3 area`() {
        val c = Tile(2977, 3702, 0)
        assertTrue(CrazyArchaeologistCombat.isAdjacent(c, c.transform(1, 0)))
        assertTrue(CrazyArchaeologistCombat.isAdjacent(c, c.transform(0, -1)))
        assertFalse(CrazyArchaeologistCombat.isAdjacent(c, c.transform(1, 1)), "diagonal is no melee reach")
        assertFalse(CrazyArchaeologistCombat.isAdjacent(c, c.transform(2, 0)))
        assertFalse(CrazyArchaeologistCombat.isAdjacent(c, Tile(2978, 3702, 1)), "other plane")
        assertEquals(9, (-3..3).sumOf { dx -> (-3..3).count { dz -> CrazyArchaeologistCombat.inBlast(c, c.transform(dx, dz)) } })
        assertEquals(CrazyArchaeologistCombat.Attack.SPECIAL, CrazyArchaeologistCombat.choose(adjacent = true, specialRoll = 0, meleeRoll = 0))
        assertEquals(CrazyArchaeologistCombat.Attack.MELEE, CrazyArchaeologistCombat.choose(adjacent = true, specialRoll = 3, meleeRoll = 0))
        assertEquals(CrazyArchaeologistCombat.Attack.RANGED, CrazyArchaeologistCombat.choose(adjacent = false, specialRoll = 3, meleeRoll = 0))
        assertEquals(CrazyArchaeologistCombat.Attack.RANGED, CrazyArchaeologistCombat.choose(adjacent = true, specialRoll = 3, meleeRoll = 1))
    }

    @Test
    fun `Pet chaos elemental drops at 1 in 300 from the Chaos Elemental and 1 in 1000 from the Chaos Fanatic`() {
        val dir = Paths.get("..", "..", "data", "cfg", "pets").toFile()
        Pets.load(File(dir, "pets.tsv"), File(dir, "pet_tiers.tsv"))
        val info = Pets.dropFor(Npcs.CHAOS_FANATIC)
        assertEquals("Pet chaos elemental", info?.pet)
        assertEquals(1000, info!!.rateFor(Npcs.CHAOS_FANATIC))
        assertEquals(300, info.rateFor(Npcs.CHAOS_ELEMENTAL))
        assertEquals(1.0 / 1000, Pets.dropChance(Npcs.CHAOS_FANATIC), 1e-12)
        assertEquals(1.0 / 300, Pets.dropChance(Npcs.CHAOS_ELEMENTAL), 1e-12)
        val parsed = Pets.parseTiers(listOf("X\t2\t1,2:50\t10"))
        assertEquals(10, parsed[0].rateFor(1))
        assertEquals(50, parsed[0].rateFor(2))
    }

    @Test
    fun `the Collection log lists the OSRS items of both bosses`() {
        assertEquals(listOf("Odium shard 1", "Malediction shard 1", "Pet chaos elemental"), CollectionLogPages.OSRS["chaos fanatic"])
        assertEquals(listOf("Odium shard 2", "Malediction shard 2", "Fedora"), CollectionLogPages.OSRS["crazy archaeologist"])
    }

    @Test
    fun `N522 bundle 2 - Scorpia, her offspring and guardians and the pit locs decode from the game cache`() {
        val definitions = DefinitionSet()
        definitions.load(library, NpcDef::class.java)
        definitions.load(library, ObjectDef::class.java)
        val attack = listOf(null, "Attack", null, null, null)
        listOf(
            Triple(Npcs.SCORPIA, "Scorpia", 5),
            Triple(Npcs.SCORPIAS_OFFSPRING, "Scorpia's offspring", 1),
            Triple(Npcs.SCORPIAS_GUARDIAN, "Scorpia's guardian", 1),
        ).forEach { (id, name, size) ->
            val def = definitions.get(NpcDef::class.java, id)
            assertEquals(name, def.name, "$id")
            assertEquals(size, def.size, "$id $name size")
            assertEquals(attack, def.options.map { it?.takeIf { o -> o.isNotEmpty() } }, "$id $name options")
        }
        val cavern = definitions.get(ObjectDef::class.java, ScorpiaPit.CAVERN)
        assertEquals("Cavern", cavern.name)
        assertTrue(cavern.options.any { it.equals("Enter", ignoreCase = true) }, "Cavern: Enter")
        val crevice = definitions.get(ObjectDef::class.java, ScorpiaPit.CREVICE)
        assertEquals("Crevice", crevice.name)
        assertTrue(crevice.options.any { it.equals("Use", ignoreCase = true) }, "Crevice: Use")
    }

    @Test
    fun `N522 bundle 2 - every surface cavern leads into the pit and every crevice back to the surface by that cavern`() {
        assertEquals(3, ScorpiaPit.CAVERNS.size)
        assertEquals(3, ScorpiaPit.CREVICES.size)
        ScorpiaPit.CAVERNS.forEach { c ->
            assertEquals(c.exit, ScorpiaPit.cavernExit(c.tile))
            assertEquals(12961, c.exit.regionId, "cavern ${c.tile} leads into the pit")
            assertTrue(c.tile.z in 3900..3999, "cavern ${c.tile} stands in the level 50+ Wilderness")
        }
        ScorpiaPit.CREVICES.forEach { (loc, exit) ->
            assertEquals(12961, loc.regionId)
            assertEquals(exit, ScorpiaPit.creviceExit(loc))
            val nearest = ScorpiaPit.CAVERNS.minOf { maxOf(kotlin.math.abs(it.tile.x - exit.x), kotlin.math.abs(it.tile.z - exit.z)) }
            assertTrue(nearest <= 3, "crevice $loc comes out next to a cavern ($exit)")
        }
        assertEquals(null, ScorpiaPit.cavernExit(Tile(3000, 3900)))
    }

    @Test
    fun `N522 bundle 2 - guardians heal from 3 tiles of her 5x5 and appear inside it`() {
        val o = Tile(3234, 10340)
        assertEquals(0, ScorpiaCombat.distanceToArea(o.transform(2, 2), o, 5), "under her")
        assertEquals(3, ScorpiaCombat.distanceToArea(o.transform(7, 0), o, 5), "3 east of her east edge")
        assertEquals(4, ScorpiaCombat.distanceToArea(o.transform(-4, 1), o, 5))
        assertEquals(3, ScorpiaCombat.distanceToArea(o.transform(-3, -3), o, 5), "diagonal")
        assertEquals(Int.MAX_VALUE, ScorpiaCombat.distanceToArea(Tile(o.x, o.z, 1), o, 5), "other plane")
        assertEquals(99, ScorpiaCombat.GUARDIAN_THRESHOLD)
        assertEquals(8, ScorpiaCombat.HEAL)
        assertEquals(3, ScorpiaCombat.HEAL_TICKS)
        assertEquals(25, ScorpiaCombat.GUARDIAN_IDLE_TICKS)
        assertEquals(2, ScorpiaCombat.PRAYER_DRAIN)
        assertEquals(16, ScorpiaCombat.MAX_HIT)
    }

    @Test
    fun `N522 bundle 2 - Scorpia's offspring pet drops from Scorpia at 1 in 2016, the pit is multi-combat, the log lists her items`() {
        val dir = Paths.get("..", "..", "data", "cfg", "pets").toFile()
        Pets.load(File(dir, "pets.tsv"), File(dir, "pet_tiers.tsv"))
        assertEquals("Scorpia's offspring", Pets.dropFor(Npcs.SCORPIA)?.pet)
        assertEquals(1.0 / 2016, Pets.dropChance(Npcs.SCORPIA), 1e-12)
        val multi = Paths.get("..", "..", "data", "cfg", "areas", "multi.yml").toFile().readLines().map { it.trim() }
        assertTrue("- 12961" in multi, "Scorpion Pit region in multi.yml")
        // The pit is Wilderness (PvP rules, teleport limits, loot timers): OSRS level 53 at Scorpia, 54 at the north crevices.
        assertEquals(53, Tile(3234, 10340).getWildernessLevel())
        assertEquals(55, Tile(3232, 10352).getWildernessLevel())
        assertEquals(listOf("Odium shard 3", "Malediction shard 3", "Scorpia's offspring"), CollectionLogPages.OSRS["scorpia"])
    }

    @Test
    fun `N522 bundle 2 - the Volcanic Forge decodes from the cache and turns each set of three shards into its own ward`() {
        val definitions = DefinitionSet()
        definitions.load(library, ObjectDef::class.java)
        assertEquals("Volcanic Forge", definitions.get(ObjectDef::class.java, VolcanicForge.FORGE).name)
        assertEquals(6, VolcanicForge.SHARDS.size)
        listOf(Items.ODIUM_SHARD_1, Items.ODIUM_SHARD_2, Items.ODIUM_SHARD_3).forEach { assertEquals(VolcanicForge.Ward.ODIUM, VolcanicForge.SHARDS[it]) }
        listOf(Items.MALEDICTION_SHARD_1, Items.MALEDICTION_SHARD_2, Items.MALEDICTION_SHARD_3).forEach {
            assertEquals(VolcanicForge.Ward.MALEDICTION, VolcanicForge.SHARDS[it])
        }
        assertEquals(Items.ODIUM_WARD, VolcanicForge.Ward.ODIUM.ward)
        assertEquals(Items.MALEDICTION_WARD, VolcanicForge.Ward.MALEDICTION.ward)
        assertTrue(VolcanicForge.TILES.all { it.getWildernessLevel() >= 50 }, "the forge stands in the deep Wilderness")
    }

    @Test
    fun `N522 bundle 3 - the Revenant Caves locs decode and the ways in and out sit at their OSRS Wilderness levels`() {
        val definitions = DefinitionSet()
        definitions.load(library, ObjectDef::class.java)
        mapOf(
            RevenantCaves.CAVERN_17 to ("Cavern" to "Enter"),
            RevenantCaves.CAVERN_40 to ("Cavern" to "Enter"),
            RevenantCaves.STAIRS_SOUTH to ("Stairs" to "Climb-up"),
            RevenantCaves.STAIRS_NORTH to ("Stairs" to "Climb-up"),
            RevenantCaves.PILLAR to ("Pillar" to "Jump-to"),
        ).forEach { (id, v) ->
            val def = definitions.get(ObjectDef::class.java, id)
            assertEquals(v.first, def.name, "$id")
            assertTrue(def.options.any { it.equals(v.second, ignoreCase = true) }, "$id ${v.first}: ${v.second}")
        }
        assertEquals("Trapdoor", definitions.get(ObjectDef::class.java, RevenantCaves.TRAPDOOR).name)
        // OSRS Wiki: the caves run from level 17 to 40; the southern stairs come out in level 17, the northern in level 36.
        // The level 17 cavern lands at y 10056: (10056 - 9920) / 8 + 1 = 18 (the caves start at level 17 on y 10048).
        assertEquals(17, gg.rsmod.game.model.Tile(3196, 10048).getWildernessLevel())
        assertEquals(18, RevenantCaves.ENTRANCES.getValue(RevenantCaves.CAVERN_17).getWildernessLevel())
        assertEquals(40, RevenantCaves.ENTRANCES.getValue(RevenantCaves.CAVERN_40).getWildernessLevel())
        assertEquals(17, RevenantCaves.EXITS.getValue(RevenantCaves.STAIRS_SOUTH).getWildernessLevel())
        assertEquals(36, RevenantCaves.EXITS.getValue(RevenantCaves.STAIRS_NORTH).getWildernessLevel())
        // "exclusively a singles-plus combat area": none of the cave squares is multi.
        val multi = Paths.get("..", "..", "data", "cfg", "areas", "multi.yml").toFile().readLines().map { it.trim() }
        listOf(12701, 12702, 12703, 12957, 12958, 12959).forEach { assertFalse("- $it" in multi, "cave square $it must stay single-way") }
    }

    @Test
    fun `N522 bundle 3 - a pillar is jumped from one take-off tile two away to the other, at its OSRS Agility level`() {
        assertEquals(5, RevenantCaves.PILLARS.size)
        assertEquals(listOf(65, 75, 75, 75, 89), RevenantCaves.PILLARS.map { it.agility }.sorted())
        RevenantCaves.PILLARS.forEach { p ->
            assertEquals(p.sideB, RevenantCaves.landing(p, p.sideA))
            assertEquals(p.sideA, RevenantCaves.landing(p, p.sideB))
            assertEquals(null, RevenantCaves.landing(p, p.tile), "standing on the pillar is no take-off")
            assertEquals(4, kotlin.math.abs(p.sideA.x - p.sideB.x) + kotlin.math.abs(p.sideA.z - p.sideB.z))
            assertEquals(p, RevenantCaves.pillarAt(p.tile))
        }
    }
}
