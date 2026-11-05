package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.cfg.Npcs

/** Arena boundaries already used by the lair controllers, not a second guessed radius around a spawn anchor. */
object WildernessBossTerritory {
    data class Territory(val source: String, val contains: (Tile) -> Boolean)
    private fun lair(name: String): Territory = Territory("WildernessLairs.LAIRS: $name") { t ->
        WildernessLairs.LAIRS.first { it.name == name }.contains(t)
    }
    fun territory(id: Int): Territory? = when (id) {
        OldWildernessBosses.CALLISTO -> lair("Callisto's Den")
        OldWildernessBosses.VETION, OldWildernessBosses.VETION_REBORN, OldWildernessBosses.HELLHOUND, OldWildernessBosses.GREATER_HELLHOUND -> lair("Vet'ion's Rest")
        ArtioCombat.NPC -> lair("Hunter's End")
        CalvarionCombat.NORMAL, CalvarionCombat.ENRAGED, CalvarionCombat.HOUND, CalvarionCombat.GREATER_HOUND -> lair("Skeletal Tomb")
        VenenatisCombat.NPC, VenenatisCombat.SPIDERLING -> Territory("VenenatisCombat.VENENATIS_ARENA", VenenatisCombat.VENENATIS_ARENA::inChasm)
        VenenatisCombat.SPINDEL, VenenatisCombat.SPINDEL_SPIDERLING -> Territory("VenenatisCombat.SPINDEL_ARENA", VenenatisCombat.SPINDEL_ARENA::inChasm)
        Npcs.SCORPIA, Npcs.SCORPIAS_OFFSPRING, Npcs.SCORPIAS_GUARDIAN -> Territory("OSRS cache Scorpion Pit map square 12961") { it.height == 0 && it.regionId == 12961 }
        RevenantMaledictus.NPC -> Territory("RevenantCaveFee.inCaves", RevenantCaveFee::inCaves)
        in EscapeCaveMonsters.ids -> Territory("WildernessLairs.inEscapeCaves", WildernessLairs::inEscapeCaves)
        else -> null
    }
    val SURFACE_BOSSES = setOf(Npcs.CHAOS_ELEMENTAL, Npcs.CHAOS_FANATIC, Npcs.CRAZY_ARCHAEOLOGIST)
    fun watches(id: Int): Boolean = territory(id) != null || id in SURFACE_BOSSES

    fun mayPursue(npc: Npc, target: Tile): Boolean? {
        val territory = territory(npc.id) ?: return null
        // Isolated administrative spawns outside their lair keep the ordinary spawn-based leash.
        if (!territory.contains(npc.spawnTile)) return null
        return territory.contains(target)
    }
}
