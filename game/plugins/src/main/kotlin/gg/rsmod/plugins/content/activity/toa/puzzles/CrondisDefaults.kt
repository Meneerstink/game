package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.ext.message

/**
 * Playable reference profile, per Claude's N462 instruction07Nov01:5x.
 * ONZEKER values are explicit estimates for client comparison, never claimed to be measured OSRS server timings.
 */
object CrondisDefaults {
    private fun <T> evidence(value: T, source: String) = ToaPuzzleReferences.Evidence(value, source)
    fun reference(): ToaPuzzleReferences.Crondis = ToaPuzzleReferences.Crondis(
        containerTiles = evidence(listOf(Tile(3941, 5284), Tile(3941, 5276)),
            "ONZEKER T18: Strategies791 northern container;40-loc15698 palm/bush-free NE/SE approach, exact ground spawn needs client comparison"),
        refillCycles = evidence(listOf(12, 10, 8, 6, 5, 4, 3, 2),
            "ONZEKER T18: Waterfall8Sep2022 change and Strategies787 team-size-dependent refill; provisional decreasing1..8-player table"),
        fillSequence = evidence(832, "ONZEKER T18: RuneLite HUMAN_PICKUPTABLE832, nearest existing reach animation for filling"),
        pourSequence = evidence(2293, "ONZEKER T18: RuneLite FARMING_WATERING2293, same watering action; container-specific sequence unproven"),
        showWaterPercent = evidence({ player, percent -> player.message("$percent%") },
            "ONZEKER T18: Water_container Check option + percentage; show actual value only, exact OSRS transcript unprovided"),
        crocodileSpawns = evidence(listOf(
            ToaPuzzleReferences.CrocodileSpawn(60, Tile(3925, 5274), 1),
            ToaPuzzleReferences.CrocodileSpawn(60, Tile(3945, 5284), 3),
            ToaPuzzleReferences.CrocodileSpawn(60, Tile(3925, 5284), 5),
            ToaPuzzleReferences.CrocodileSpawn(60, Tile(3945, 5274), 7)),
            "ONZEKER T18: Strategies789 corners/team-dependent number/~40sec first palm arrival;36sec spawn + walk;40-loc bush-free corner neighbours"),
        crocodileRepeatCycles = evidence(67, "ONZEKER T18: reuse published ~40sec first arrival as provisional repeated spawn interval (ceil40/0.6)"),
        crocodileAggroRange = evidence(11, "ONZEKER T18: Crocodile article within aggro range; half-width of main palm room from40-loc geometry"),
        crocodileAttackSequence = evidence(9804, "ONZEKER T18: RuneLite CROC_ATTACK_MERGE in ToA action cluster; fallback classic CROC_ATTACK2039"),
        crocodileDeathSequence = evidence(2038, "RuneLite CROC_DEATH2038;50-seq-defs classic crocodile rig"),
        palmBiteWater = evidence(18.0, "ONZEKER T18: nearest published same-crocodile fixed bite18; exact palm water reduction unprovided"),
        trapEvents = evidence(traps(), "ONZEKER T18:40-loc45416 exact spear anchors; water lanes inferred from45398 waterfall bounds; warning/hit phases provisional"),
        trapRepeatCycles = evidence(20, "ONZEKER T18: corridor flow across16 tiles + warning/recovery; sequence9547=1tick is a visual cue, not a proven repeat interval"),
        minorSpillChance = evidence(0.01, "ONZEKER T18: Water_container article25 publishes a slight chance of16% loss instead of50%; provisional1% chance, using same container's minimum displayed1% as nearest published percentage"),
    )

    private fun traps(): List<ToaPuzzleReferences.Trap> = buildList {
        //40-loc-placements: left/south spears at even5258..5266; right at odd5259..5267. North parity reverses.
        for (lane in listOf(3927, 3941)) for (north in listOf(false, true)) {
            val first = if (north) 5293 else 5258
            for (row in 0 until 10) {
                val left = row % 2 == 0
                val x = if (left) lane else lane + 2
                val z = first + row
                add(ToaPuzzleReferences.Trap(cycle = row * 2, warningCycles = 1,
                    tiles = (x..x + 2).map { Tile(it, z) }.toSet(), maxHit = 6,
                    warningLoc = null, activeLoc = null, cueLoc = 45416, cueTile = Tile(x, z),
                    cueRotation = if (left) 2 else 0, warningSequence = 9563, activeSequence = 9564))
            }
            //Strategies785: lighter warning precedes dark damaging poison flowing away from each waterfall.
            for (row in 0 until 16) {
                val z = if (north) 5305 - row else 5255 + row
                add(ToaPuzzleReferences.Trap(cycle = row, warningCycles = 1,
                    tiles = (lane..lane + 4).map { Tile(it, z) }.toSet(), maxHit = 6,
                    warningLoc = 45418, activeLoc = 45419))
            }
        }
    }
}
