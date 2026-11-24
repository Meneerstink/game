package gg.rsmod.plugins.content.mechanics.multi

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.plugins.content.areas.wilderness.ClanWarsFfaCombat
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.api.ext.isMulti
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner decision 2026-09-27: the surface follows the OSRS Deadman: Annihilation multicombat map (OSRS Wiki
 * Module:DeadmanAnnihilationMulticombatZones.json). Guard: every surface breach location is multi or single exactly as the wiki's
 * Deadman: Annihilation breach table says (data/cfg/deadman/breach-locations.json), which the old multi.yml got wrong for 16.
 */
class MultiCombatZonesTests {
    @Test
    fun `FFA north halves use lookup hashes and preserve the exact arena edges`() {
        val zones = ClanWarsFfaCombat
        val world = mockk<World>(relaxed = true)
        every { world.instanceAllocator.getMap(any()) } returns null
        every { world.getMultiCombatRegions() } returns emptySet()
        every { world.getMultiCombatChunks() } returns zones.multiChunks
        for ((xs, top) in listOf(2756..2878 to 5630, 2948..3071 to 5631)) {
            for (x in (xs.first - 1)..(xs.last + 1)) {
                for (z in 5567..(top + 1)) {
                    val tile = Tile(x, z)
                    assertEquals(x in xs && z in 5571..top,
                        tile.isMulti(world), "$x,$z")
                }
            }
        }
        assertFalse(Tile(2900, 5660).isMulti(world))
    }

    private val root = Paths.get("..", "..", "data", "cfg")

    @Test
    fun `every surface breach location has the combat type of the wiki breach table`() {
        assertTrue(MultiCombatZones.load(root.resolve("areas").resolve("dmm-multicombat-zones.json").toFile()) > 10000)
        val failures = mutableListOf<String>()
        DeadmanBreach.loadLocations(root.resolve("deadman").resolve("breach-locations.json").toFile()).localised.forEach { site ->
            // SOURCE CONFLICT (reported to the owner): the breach table calls North-west of the Champions' Guild single-way, but
            // its first spawner 3133,3395 lies inside a multicombat polygon of the map; the map wins (owner: "DMM map everywhere").
            site.spawners.filter { MultiCombatZones.covers(it.x, it.z) && !(it.x == 3133 && it.z == 3395) }.forEach { s ->
                val multi = MultiCombatZones.contains(s.x, s.z)
                if (multi != site.multi) failures += "${site.name} spawner ${s.x},${s.z}: map multi=$multi, breach table multi=${site.multi}"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `known places`() {
        MultiCombatZones.load(root.resolve("areas").resolve("dmm-multicombat-zones.json").toFile())
        assertTrue(MultiCombatZones.contains(3331, 3902), "Fountain of Rune breach site")
        assertFalse(MultiCombatZones.contains(3052, 3767), "Forgotten Cemetery breach site is single")
        assertFalse(MultiCombatZones.covers(2900, 5300), "God Wars is not on the surface map")
    }

    /** MULTI-GEBIEDEN 24-11: the low-z dungeons follow multi.yml, the whole surface map stays covered. */
    @Test
    fun `low z dungeons are not surface and every surface polygon tile stays covered`() {
        MultiCombatZones.load(root.resolve("areas").resolve("dmm-multicombat-zones.json").toFile())
        assertFalse(MultiCombatZones.covers(2986, 4383), "Corporeal Beast's lair")
        assertFalse(MultiCombatZones.covers(2906, 4441), "Dagannoth Kings")
        assertFalse(MultiCombatZones.covers(1900, 4380), "Waterbirth Island Dungeon route")
        assertTrue(MultiCombatZones.covers(1630, 4000), "Wintertodt (surface)")
        val json = com.google.gson.Gson().fromJson(root.resolve("areas").resolve("dmm-multicombat-zones.json").toFile().readText(),
            com.google.gson.JsonObject::class.java)
        val outside = mutableListOf<String>()
        json.getAsJsonArray("features").forEach { f ->
            f.asJsonObject.getAsJsonObject("geometry").getAsJsonArray("coordinates").forEach { ring ->
                ring.asJsonArray.forEach { p ->
                    val x = p.asJsonArray[0].asInt; val z = p.asJsonArray[1].asInt
                    if (!MultiCombatZones.covers(x, z)) outside += "$x,$z"
                }
            }
        }
        assertTrue(outside.isEmpty(), "surface map points outside covers(): ${outside.take(5)}")
    }
}
