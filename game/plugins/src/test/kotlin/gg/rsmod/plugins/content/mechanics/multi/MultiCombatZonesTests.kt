package gg.rsmod.plugins.content.mechanics.multi

import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
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
}
