package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.toa.puzzles.ToaPuzzleReferences.Evidence

/** Missing observations are T22 in80-vragen.txt, not permission to invent an OSRS generator. */
object ApmekenReferences {
    data class Spawn(val type: ApmekenPuzzle.Baboon, val tile: Tile)
    data class TaskEvent(val cycle: Int, val duration: Int, val type: ApmekenPuzzle.Action,
                         val usesByPartySize: List<Map<Int, Int>>, val requiredByPartySize: List<Int>) {
        init {
            require(cycle >= 0 && duration > 0 && usesByPartySize.size == 8 && requiredByPartySize.size == 8)
            require(requiredByPartySize.all { it > 0 })
        }
    }
    data class Actor(val range: Int, val hitDelay: Int, val attackSequence: Int, val deathSequence: Int,
                     val launch: (Npc, Player) -> Unit) {
        init { require(range > 0 && hitDelay >= 0) }
    }
    data class Profile(
        val waves: Evidence<(partySize: Int, wave: Int) -> List<Spawn>>,
        val remainingThresholds: Evidence<List<Int>>,
        val tasks: Evidence<List<TaskEvent>>,
        val taskRepeatCycles: Evidence<Int>,
        val sightCycle: Evidence<Int>,
        val chooseSight: Evidence<(List<Player>) -> Player>,
        val chooseTarget: Evidence<(Npc, List<Player>, ApmekenPuzzle.Baboon) -> Player>,
        val wrongActionDamage: Evidence<Int>,
        val missedActionDamage: Evidence<(partySize: Int, missed: Int) -> Int>,
        val thrallCap: Evidence<Int>,
        val summonCycles: Evidence<Int>,
        val thrallTile: Evidence<(Npc) -> Tile>,
        val thrallPrayerDrain: Evidence<(rawDamage: Int, protected: Boolean) -> Int>,
        val volatileChargeCycles: Evidence<Int>,
        val volatileDamage: Evidence<(remainingHp: Int) -> Int>,
        val venomPoolCycles: Evidence<Int>,
        val cursedStepCycles: Evidence<Int>,
        val cursedWalkTiles: Evidence<List<Tile>>,
        val actors: Evidence<Map<ApmekenPuzzle.Baboon, Actor>>,
        /** Per-viewer Sight/vent/corruption effects need the original targeted packet semantics (T01), not a global gfx. */
        val drawSight: Evidence<(viewer: Player, holder: Player?, task: ApmekenPuzzle.Task<gg.rsmod.game.model.PlayerUID>?) -> Unit>,
    ) {
        init {
            require(remainingThresholds.value.size == 8 && remainingThresholds.value.all { it >= 0 })
            require(taskRepeatCycles.value > 0 && sightCycle.value > 0 && wrongActionDamage.value >= 0)
            require(thrallCap.value > 0 && summonCycles.value > 0 && volatileChargeCycles.value >= 0)
            require(venomPoolCycles.value > 0 && cursedStepCycles.value > 0 && cursedWalkTiles.value.isNotEmpty())
            require(ApmekenPuzzle.Baboon.values().filter { it != ApmekenPuzzle.Baboon.SPECIAL }.all { it in actors.value })
        }
    }
    /** Unmeasured values carry ONZEKER evidence, but never leave a normal encounter empty. */
    var profile: Profile? = ApmekenDefaults.reference()
}
