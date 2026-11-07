package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player

/**
 * Published facts and explicitly labelled estimates for server-only observations (N462 T18/T22).
 * Claude07Nov01:5x requires a playable default profile; ONZEKER evidence identifies what still needs OSRS comparison.
 */
object ToaPuzzleReferences {
    data class Evidence<T>(val value: T, val source: String) {
        init { require(source.isNotBlank()) }
    }

    data class Crondis(
        val containerTiles: Evidence<List<Tile>>,
        val refillCycles: Evidence<List<Int>>,
        val fillSequence: Evidence<Int>,
        val pourSequence: Evidence<Int>,
        val showWaterPercent: Evidence<(Player, Int) -> Unit>,
        val crocodileSpawns: Evidence<List<CrocodileSpawn>>,
        val crocodileRepeatCycles: Evidence<Int>,
        val crocodileAggroRange: Evidence<Int>,
        val crocodileAttackSequence: Evidence<Int>,
        val crocodileDeathSequence: Evidence<Int>,
        val palmBiteWater: Evidence<Double>,
        val trapEvents: Evidence<List<Trap>>,
        val trapRepeatCycles: Evidence<Int>,
        val minorSpillChance: Evidence<Double>,
    ) {
        init {
            require(containerTiles.value.isNotEmpty())
            require(crocodileSpawns.value.isNotEmpty() && trapEvents.value.isNotEmpty())
            require(refillCycles.value.size == 8 && refillCycles.value.all { it > 0 })
            require(crocodileRepeatCycles.value > 0 && crocodileAggroRange.value > 0 && palmBiteWater.value > 0)
            require(trapRepeatCycles.value > 0)
            require(minorSpillChance.value in 0.0..1.0)
        }
    }
    data class CrocodileSpawn(val cycle: Int, val tile: Tile, val minimumPartySize: Int = 1) {
        init { require(cycle >= 0 && minimumPartySize in 1..8) }
    }
    data class Trap(val cycle: Int, val warningCycles: Int, val tiles: Set<Tile>, val maxHit: Int,
                    val warningLoc: Int?, val activeLoc: Int?, val duration: Int = 1,
                    val cueLoc: Int? = null, val cueTile: Tile? = null, val cueRotation: Int = 0,
                    val warningSequence: Int? = null, val activeSequence: Int? = null) {
        init { require(cycle >= 0 && warningCycles >= 0 && tiles.isNotEmpty() && maxHit >= 0 && duration > 0) }
    }

    /** Missing measured values have explicit ONZEKER evidence; a normal raid never waits for profile installation. */
    var crondis: Crondis? = CrondisDefaults.reference()
}
