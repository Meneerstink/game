package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.toa.puzzles.ToaPuzzleReferences.Evidence

/** Dynamic layouts, server timings and glyph mapping are not contained in40/50 (N462 A1/T01/T02). */
object ScabarasReferences {
    enum class Kind { MEMORY, OBELISKS, LIGHTS, ADDITION, MATCHING }
    data class Gate(val tile: Tile, val shape: Int, val rotation: Int, val closedLoc: Int,
                    val requires: Set<Kind>)
    data class Shortcut(val objectTile: Tile, val from: Area, val destination: Tile, val requires: Set<Kind>,
                        val sequence: Int, val duration: Int)
    data class Layout(
        val memoryButton: Tile, val memoryTiles: List<Tile>, val obelisks: List<Tile>, val lights: List<Tile>,
        val tablet: Tile, val additionTiles: List<Tile>, val matchingButtons: List<List<Tile>>, val finalButton: Tile,
        val gates: List<Gate>, val shortcuts: List<Shortcut>,
        val soloSkipsOnClear: Map<Kind, Set<Kind>>, val numberPrerequisites: Set<Kind>,
        val soloStatueFrom: Tile, val soloStatueTo: Tile,
    ) {
        init {
            require(memoryTiles.isNotEmpty() && obelisks.size == 5 && lights.size == 8 && additionTiles.size == 25)
            require(matchingButtons.size == 2 && matchingButtons.all { it.size == 9 })
            require(listOf(memoryTiles, obelisks, lights, additionTiles).all { it.distinct().size == it.size })
            require(soloSkipsOnClear.none { (key, skips) -> key in skips || Kind.MATCHING in skips })
        }
    }
    data class Initial(val obeliskOrder: List<Int>, val lights: Int, val additionTarget: Int,
                       val leftSymbols: List<Int>, val rightSymbols: List<Int>, val soloPreSolved: Set<Int>)
    data class ScarabSpawn(val cycle: Int, val tile: Tile, val minimumPartySize: Int)
    data class Profile(
        val layout: Evidence<(partySize: Int) -> Layout>,
        val initial: Evidence<(partySize: Int, generation: Int) -> Initial>,
        val memoryOrder: Evidence<(generation: Int) -> List<Int>>,
        val memoryResetOnWrong: Evidence<Boolean>, val obelisksResetOnWrong: Evidence<Boolean>,
        val flashCycles: Evidence<Int>, val betweenFlashesCycles: Evidence<Int>,
        val memoryDamage: Evidence<Int>, val additionDamage: Evidence<Int>,
        val rockDelay: Evidence<Int>, val rockMaxHit: Evidence<Int>, val rockWarningSpot: Evidence<Int>,
        val rockSafeTiles: Evidence<Set<Tile>>,
        val lightsLitLoc: Evidence<Int>, val lightsUnlitLoc: Evidence<Int>,
        val memoryShownLoc: Evidence<Int>, val memoryNeutralLoc: Evidence<Int>, val memoryPressedLoc: Evidence<Int>,
        val numberLocs: Evidence<Map<Int, Int>>,
        val numberSelectedLocs: Evidence<Map<Int, Int>>,
        val matchingSymbolLocs: Evidence<Map<Int, Int>>, val matchingButtonLocs: Evidence<Map<Int, Int>>,
        val matchingSymbolFx: Evidence<Map<Int, Int>>,
        val matchingMismatchCycles: Evidence<Int>, val requiresFinalButton: Evidence<Boolean>,
        val scarabSpawns: Evidence<List<ScarabSpawn>>, val scarabRepeatCycles: Evidence<Int>,
        val scarabRange: Evidence<Int>, val scarabAttackSequence: Evidence<Int>, val scarabDeathSequence: Evidence<Int>,
        val scarabHitDelay: Evidence<Int>, val scarabLaunch: Evidence<(Npc, Player) -> Unit>,
        /** The exact OSRS tablet text is a missing transcript; do not invent one in the plugin. */
        val showTablet: Evidence<(Player, target: Int) -> Unit>,
    ) {
        init {
            require(flashCycles.value > 0 && betweenFlashesCycles.value >= 0 && rockDelay.value > 0)
            require(memoryDamage.value >= 0 && additionDamage.value >= 0 && rockMaxHit.value >= 0)
            require(scarabRepeatCycles.value > 0 && scarabRange.value > 0 && scarabHitDelay.value >= 0)
            require(matchingMismatchCycles.value >= 0)
            require((1..9).all { it in numberLocs.value && it in numberSelectedLocs.value &&
                it in matchingSymbolLocs.value && it in matchingButtonLocs.value && it in matchingSymbolFx.value })
        }
    }
    /** Four chamber positions derive from the RuneLite observer; all inferred scene/timer details carry ONZEKER evidence. */
    var profile: Profile? = ScabarasDefaults.reference()
}
