package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.wilderness.bosses.ChaosFanaticCombat
import gg.rsmod.plugins.content.areas.wilderness.bosses.CrazyArchaeologistCombat
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
}
