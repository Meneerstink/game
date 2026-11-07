package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.activity.toa.puzzles.ApmekenPuzzle.Baboon
import kotlin.math.ceil

/** Wiki/cache facts with explicit ONZEKER profiles for T01/T02/T22 (Claude07Nov01:5x: supply playable values, never an empty room). */
object ApmekenDefaults {
    private fun <T> e(value: T, source: String) = ToaPuzzleReferences.Evidence(value, source)
    private val sight = hashMapOf<Player, gg.rsmod.game.model.PlayerUID?>()
    private val corrupted = hashMapOf<Player, Set<Player>>()
    private val spawnTiles = listOf(Tile(3799, 5274), Tile(3817, 5286), Tile(3799, 5286), Tile(3817, 5274),
        Tile(3804, 5274), Tile(3812, 5286), Tile(3804, 5286), Tile(3812, 5274))

    fun reference(): ApmekenReferences.Profile = ApmekenReferences.Profile(
        waves = e({ size, wave -> plan(size, wave) },
            "ONZEKER T22: Strategies1304-1326 solo compositions and1298 one extra combat baboon for groups; uniform specialist/family selection and large-from-wave4 are provisional"),
        remainingThresholds = e(listOf(0, 1, 1, 2, 2, 2, 2, 0),
            "ONZEKER T22: Path_of_Apmeken30Aug2023 overlap increased; provisional thresholds within published wave sizes, final wave waits for every death"),
        tasks = e(listOf(
            task(26, ApmekenPuzzle.Action.PILLAR), task(52, ApmekenPuzzle.Action.VENT), task(78, ApmekenPuzzle.Action.CORRUPTION)),
            "ONZEKER T22: Strategies1291-1296 three task kinds and one action/member;13-tick window and26-tick spacing from long Sight clip384/30 rounded up and doubled; distributing more than4 actions over4 sites remains inferred"),
        taskRepeatCycles = e(78, "ONZEKER T22: three26-tick task slots; solo corruption slot is ignored"),
        sightCycle = e(26, "ONZEKER T22: two long Sight visual cycles (~13ticks each), server transfer interval unprovided"),
        chooseSight = e({ players -> players.random() }, "ONZEKER T22: Strategies1296 random Sight transfer; uniform among active entrants"),
        chooseTarget = e({ npc, players, type -> if (type == Baboon.VOLATILE) players.random() else players.minBy { npc.tile.getDistance(it.tile) } },
            "ONZEKER T02: Strategies1279 random volatile target; nearest live entrant for other combat baboons"),
        wrongActionDamage = e(8, "ONZEKER T22: Strategies1296 publishes damage but not its amount; use nearest published same-room hazard maximum8 (Cursed pools,1285) for a wrong pillar/vent"),
        missedActionDamage = e({ _, missed -> 8 * missed }, "ONZEKER T22: Strategies1296 scales with missed actions; use same-room published pool maximum8 per missing action until measured"),
        thrallCap = e(4, "ONZEKER T22: Strategies1267 says4 or5; choose published lower bound4"),
        summonCycles = e(8, "ONZEKER T22: use same shaman's published attack speed8 for its summon action"),
        thrallTile = e({ npc ->
            val room = checkNotNull(npc.attr[ToaPuzzles.OWNER])
            val tile = checkNotNull(ToaMap.source(room.raid.map, npc.tile))
            tile.transform(if (tile.x < 3818) 1 else -1, 0)
        }, "ONZEKER T02: adjacent shaman tile within40-loc arena bounds, exact summon offsets unprovided"),
        thrallPrayerDrain = e({ raw, _ -> raw }, "ONZEKER T22: thrall percentage unprovided; reuse same-family shaman's published100% prayer-to-hit ratio, including protected raw hits"),
        volatileChargeCycles = e(1, "ONZEKER T22: RuneLite NPC_MANDRILL_EXPLODE9756=60clientcycles/2ticks; midpoint1tick charge"),
        volatileDamage = e({ hp -> ceil(hp.coerceAtLeast(0) * 7.0 / 8.0).toInt() },
            "ONZEKER T22: volatile Wiki infobox max7, HP8; Strategies says remaining-HP dependent, provisional linear scaling"),
        venomPoolCycles = e(34, "ONZEKER T22 rounding only: Path_of_Apmeken25Aug2022 pool lifetime20sec, ceil20/0.6=34ticks"),
        cursedStepCycles = e(1, "ONZEKER T22: one walk step per600ms server cycle; source says wandering with venom trail"),
        cursedWalkTiles = e((3799..3819).flatMap { x -> (5273..5287).map { z -> Tile(x, z) } }
            .filterNot { it.x in 3807..3809 && it.z in 5279..5281 },
            "ONZEKER T02:40-loc15186 arena interior, exclude3x3 statue45496 and runtime vents/pillar fronts"),
        actors = e(actors(), "ONZEKER T01: RuneLite MANDRILL9742/44/46/47, DESPAWN9754; volatile charging uses nearest same-family SPECIAL_IDLE9750, timed detonation uses EXPLODE9756 once (normal kill despawns). Hit at clip midpoint, range8; standard projectile packet parameters"),
        drawSight = e(::drawSight, "ONZEKER T01 lifetime/slot: RuneLite SpotanimID2132/33/36/37 per viewer; Darkan player-index hash preserves moving corruption;50-loc45494/95 same model45425 permits9502/9503 LocAnim on existing45494 (shape10/rot3), preserving clickable ID. Missing clip uses same-room rubble2139 as labelled fallback"),
    )

    private fun task(cycle: Int, type: ApmekenPuzzle.Action): ApmekenReferences.TaskEvent = ApmekenReferences.TaskEvent(
        cycle, 13, type, (1..8).map { size -> (0 until size).groupingBy { it % 4 }.eachCount() },
        (1..8).map { size -> if (type == ApmekenPuzzle.Action.CORRUPTION) maxOf(1, size - 1) else size })

    private fun plan(size: Int, wave: Int): List<ApmekenReferences.Spawn> {
        val types = ApmekenPuzzle.WAVES[wave - 1].map { type ->
            if (type == Baboon.SPECIAL) listOf(Baboon.SHAMAN, Baboon.VOLATILE, Baboon.CURSED).random() else type
        }.toMutableList()
        if (size > 1) types += listOf(Baboon.BRAWLER, Baboon.THROWER, Baboon.MAGE).random()
        return types.mapIndexed { index, original ->
            val large = if (wave >= 4 && original.combat) Baboon.values().first { it.family == original.family && it.osrs in 11712..11714 } else original
            ApmekenReferences.Spawn(large, spawnTiles[index % spawnTiles.size])
        }
    }

    private fun actors(): Map<Baboon, ApmekenReferences.Actor> = Baboon.values().filter { it != Baboon.SPECIAL }.associateWith { type ->
        val seq = when (type.family) { 0, 6 -> 9742; 1 -> 9744; 2 -> 9746; 3 -> 9747; 4 -> 9750; else -> 9741 }
        ApmekenReferences.Actor(if (type.family in listOf(0, 6)) 1 else 8, if (type.family == 1) 2 else 1, seq,
            9754) { npc, player ->
            when (type.family) {
                1 -> ToaPuzzleVisuals.projectile(npc, player, if (type == Baboon.LARGE_THROWER) 2243 else 2242, ProjectileType.THROWN)
                2, 3 -> ToaPuzzleVisuals.projectile(npc, player, if (type == Baboon.LARGE_MAGE) 2248 else 2247, ProjectileType.MAGIC)
            }
        }
    }

    private fun drawSight(viewer: Player, holder: Player?, task: ApmekenPuzzle.Task<gg.rsmod.game.model.PlayerUID>?) {
        val old = sight[viewer]
        if (holder == null) sight.remove(viewer) else sight[viewer] = holder.uid
        val previousCorrupted = corrupted.remove(viewer).orEmpty()
        //Clear attached effects even when the entrant has already left the lobby's raid registry.
        if (holder == null || viewer !== holder || task?.type != ApmekenPuzzle.Action.CORRUPTION)
            previousCorrupted.forEach { ToaPuzzleVisuals.playerSpot(viewer, it, null) }
        val raid = ToaLobby.of(viewer.world).raid(viewer.uid) ?: return
        if (old != holder?.uid && (viewer === holder || old == viewer.uid))
            ToaPuzzleVisuals.playerSpot(viewer, viewer, if (viewer === holder) 2132 else 2133)
        ApmekenRoom.PILLARS.forEachIndexed { site, tile ->
            val broken = viewer === holder && task?.type == ApmekenPuzzle.Action.PILLAR && (task.remainingSites[site] ?: 0) > 0
            ToaPuzzleVisuals.pillar(viewer, ToaMap.instance(raid.map, tile), broken)
        }
        if (viewer !== holder) return
        when (task?.type) {
            ApmekenPuzzle.Action.VENT -> task.remainingSites.filterValues { it > 0 }.keys.forEach {
                ToaPuzzleVisuals.spot(viewer, 2136, ToaMap.instance(raid.map, ApmekenRoom.VENTS[it]))
            }
            ApmekenPuzzle.Action.CORRUPTION -> {
                val current = raid.activePlayers().filter { it.uid in task.corrupted }.toSet()
                (previousCorrupted - current).forEach { ToaPuzzleVisuals.playerSpot(viewer, it, null) }
                (current - previousCorrupted).forEach { ToaPuzzleVisuals.playerSpot(viewer, it, 2137) }
                if (current.isNotEmpty()) corrupted[viewer] = current
            }
            else -> Unit
        }
    }
}
