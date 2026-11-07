package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasReferences.Kind

/**
 * Source geometry: cache2686 5/14162/1; Wiki puzzle1..5 images; RuneLite observer NNourdin/toa-puzzle-solver
 * SHA0ce8157925bd9dd27c6142383e352fb1229f0d0e, ScarabPuzzle.java47-51/174-200/247-279.
 * Scene-to-source alignment is inferred from the four static chamber floors and labelled ONZEKER T02.
 */
object ScabarasDefaults {
    private fun <T> e(value: T, source: String) = ToaPuzzleReferences.Evidence(value, source)
    //Wiki lights-key configurations1..9, independently tested against their published solutions.
    val LIGHT_STATES = listOf(251, 250, 0, 253, 126, 4, 252, 125, 2)
    private val diamond = listOf(0 to 2, 1 to 1, 1 to 3, 2 to 0, 2 to 2, 2 to 4, 3 to 1, 3 to 3, 4 to 2)
    //LlemonDuck/toa AdditionPuzzleSolver.AdditionTile: numerical value equals TOTALTILES_TILE ordinal.
    private val ordinal = (1..9).associateWith { it }
    //Same observer maps selected numerals to FX05/06/07/08/09/10/03/11/12; loc.sym pins their OSRS IDs.
    private val selectedNumerals = mapOf(1 to 45388, 2 to 45389, 3 to 45390, 4 to 45391, 5 to 45392,
        6 to 45393, 7 to 45386, 8 to 45394, 9 to 45395)

    fun reference(): ScabarasReferences.Profile = ScabarasReferences.Profile(
        layout = e({ size -> layout(size) }, "ONZEKER T02 partial: LlemonDuck SkipObeliskOverlay scene flames28,54/42 align with cache45135 sources3532,5286/5274 -> scene base3504,5232; Addition/Light solvers NW36/53,56/44 therefore exact source3540/3557,5288/5276. Obelisk NW36/53,57/45 adds1 north. Other NPC/button pins, remaining obelisk offsets, shortcuts and uniform4-kind assignment still inferred from Wiki images/40-loc geometry"),
        initial = e({ _, _ -> ScabarasReferences.Initial((0..4).shuffled(), LIGHT_STATES.random(), (20..45).random(),
            (1..9).shuffled(), (1..9).shuffled(), setOf(1, 3, 5, 7)) },
            "ONZEKER T02 distributions: Wiki120 obelisk permutations,9 lights examples,20..45 target,9 matching symbols; LlemonDuck MatchingPuzzleSolver55-60 pins solo pre-solved line/crook/hand/bird=1/3/5/7"),
        memoryOrder = e({ _ -> (0..8).shuffled().take(5) }, "ONZEKER T02: Wiki image1 nine diamond plates; RuneLite observer174-180 exactly5 flashes, distinctness/distribution inferred"),
        memoryResetOnWrong = e(true, "ONZEKER T02: Wiki incorrect step damage; restart cursor for retry, exact partial-reset rule unprovided"),
        obelisksResetOnWrong = e(true, "ONZEKER T02: LlemonDuck ObeliskPuzzleSolver.onNpcChanged resets active count to0 when an obelisk reverts from HIT to unlit; infer wrong-strike reset from that observed reversion"),
        flashCycles = e(1, "ONZEKER T01: cache9496 glow idle3clientcycles; one600ms server tick per visible flash, not a claimed cache-derived server interval"),
        betweenFlashesCycles = e(1, "ONZEKER T01: one blank server tick distinguishes successive flashes; same visual cue as preceding field"),
        memoryDamage = e(20, "ONZEKER T02: exact memory failure damage unprovided, nearest published same-room penalty is Flip20"),
        additionDamage = e(20, "ONZEKER T02: exact sum overflow damage unprovided, nearest published same-room penalty is Flip20"),
        rockDelay = e(2, "ONZEKER T01: avoidable rocks; half of same-room scarab's published4-tick attack cycle for warning"),
        rockMaxHit = e(20, "ONZEKER T02: exact rock damage unprovided, nearest published same-room penalty is Flip20"),
        rockWarningSpot = e(302, "ONZEKER T01 for obelisks: LlemonDuck SequencePuzzleSolver.onGraphicsObjectCreated observes MM_ROOFCOLLAPSE302 on memory failure; nearest same-room falling-rock asset"),
        rockSafeTiles = e(chambers.map { Tile(it.first - 6, it.second - 2) }.toSet(),
            "ONZEKER T02: Wiki lower entrance step/range10,40-loc gateways3532/3549; choose gateway+2 at each branch centre"),
        lightsLitLoc = e(45384, "LlemonDuck LightPuzzleSolver GAME_OBJECT_LIGHT_ENABLED=TOA_SCABARAS_FX01; loc.sym45384; shape10 above ground45344"),
        lightsUnlitLoc = e(45344, "cache2/6/45344 lightsout_tile_off, shape22 with Flip option"),
        memoryShownLoc = e(45341, "cache45341 simonsays_tile_down, RuneLite GameObjectSpawned174; shape10 above ground45340"),
        memoryNeutralLoc = e(45340, "cache45340 simonsays_tile_up, shape22"),
        memoryPressedLoc = e(45342, "LlemonDuck SequencePuzzleSolver.onGameObjectSpawned: SIMONSAYS_TILE_DOWN_PLAYER45342 marks a correct player step; loc.sym"),
        numberLocs = e(ordinal.mapValues { 45344 + it.value }, "LlemonDuck AdditionPuzzleSolver.AdditionTile values1..9 match TOTALTILES_TILE1..9; loc.sym45345..45353"),
        numberSelectedLocs = e(selectedNumerals, "LlemonDuck AdditionPuzzleSolver.AdditionTile selected FX per numeral; loc.sym45386/45388..45395"),
        matchingSymbolLocs = e((1..9).associateWith { 45364 + it }, "cache45365..45373 memorygame_tile1..9; same symbol order as buttons45356..45364"),
        matchingButtonLocs = e((1..9).associateWith { 45355 + it }, "cache45356..45364 memorygame_button1..9; RuneLite observer138-163"),
        matchingSymbolFx = e(mapOf(1 to 45388, 2 to 45389, 3 to 45386, 4 to 45391, 5 to 45392,
            6 to 45387, 7 to 45393, 8 to 45394, 9 to 45395),
            "LlemonDuck MatchingPuzzleSolver FX_TO_GROUND_OBJECT maps FX05/06/03/08/09/04/10/11/12 to symbols1..9; loc.sym"),
        matchingMismatchCycles = e(2, "ONZEKER T02: show mismatched symbols for2 server ticks; exact reveal duration unprovided"),
        requiresFinalButton = e(false, "ONZEKER T02: Strategies939 clear final matching; auto-clear on ninth pair, no additional unsourced button action required"),
        scarabSpawns = e(listOf(ScabarasReferences.ScarabSpawn(8, Tile(3534, 5274), 1),
            ScabarasReferences.ScarabSpawn(8, Tile(3534, 5286), 2),
            ScabarasReferences.ScarabSpawn(8, Tile(3551, 5274), 5)),
            "ONZEKER T02: Strategies905 start of first rooms, can move up to second in larger groups; gateway+2 spawn neighbours, size thresholds unprovided"),
        scarabRepeatCycles = e(20, "ONZEKER T02: nuisance scarabs spawn interval unprovided; five published4-tick scarab attack cycles"),
        scarabRange = e(8, "ONZEKER T02: ranged scarab, use same ranged-room reach as imported baboons; exact range unprovided"),
        scarabAttackSequence = e(9588, "RuneLite NPC_SCARAB_RANGED_ATTACK9588; cacheNPC11697 stand9586/walk9584 same rig"),
        scarabDeathSequence = e(9590, "RuneLite NPC_SCARAB_DEATH9590, same rig asNPC11697"),
        scarabHitDelay = e(2, "ONZEKER T01: half published4-tick ranged attack cycle; packet flight delay not in cache"),
        scarabLaunch = e({ npc, player -> ToaPuzzleVisuals.projectile(npc, player, 2152, ProjectileType.ARROW) },
            "ONZEKER T01: RuneLite TOA_SCARAB_RANGED_PROJECTILE01=2152 with existing arrow packet parameters"),
        showTablet = e({ player, target -> player.message("The number <col=ffffff>$target</col> has been hastily chipped into the stone.") },
            "ONZEKER T02 colour only: LlemonDuck AdditionPuzzleSolver.TARGET_NUMBER_PATTERN gives complete sentence; use existing white colour markup"),
    )

    //Source floors40-loc: first east edge3544, second3561; north/south north edges5288/5276.
    private val chambers = listOf(3540 to 5288, 3540 to 5276, 3557 to 5288, 3557 to 5276)
    fun layout(partySize: Int): ScabarasReferences.Layout = layoutForOrder(partySize,
        listOf(Kind.MEMORY, Kind.LIGHTS, Kind.OBELISKS, Kind.ADDITION).shuffled())

    /** Public for exhaustive tests: all24 kind arrangements must be solvable for solo and8 members. */
    fun layoutForOrder(partySize: Int, order: List<Kind>): ScabarasReferences.Layout {
        require(partySize in 1..8 && order.toSet() == setOf(Kind.MEMORY, Kind.LIGHTS, Kind.OBELISKS, Kind.ADDITION) && order.size == 4)
        fun box(kind: Kind): Pair<Int, Int> = chambers[order.indexOf(kind)]
        fun cell(box: Pair<Int, Int>, row: Int, column: Int) = Tile(box.first + 4 - row, box.second - column)
        val mem = box(Kind.MEMORY); val light = box(Kind.LIGHTS); val number = box(Kind.ADDITION); val ob = box(Kind.OBELISKS)
        val first = order.take(2).toSet(); val all = order.toSet()
        val gates = listOf(
            ScabarasReferences.Gate(Tile(3549, 5286), 10, 3, 45336, first),
            ScabarasReferences.Gate(Tile(3549, 5274), 10, 3, 45336, first),
            ScabarasReferences.Gate(Tile(3564, 5286), 10, 3, 45336, all),
            ScabarasReferences.Gate(Tile(3564, 5274), 10, 3, 45336, all))
        val shortcuts = listOf(
            ScabarasReferences.Shortcut(Tile(3548, 5283), Area(3533, 5282, 3549, 5292), Tile(3547, 5276), setOf(order[0]), 769, 2),
            ScabarasReferences.Shortcut(Tile(3548, 5277), Area(3533, 5268, 3549, 5278), Tile(3547, 5284), setOf(order[1]), 769, 2),
            ScabarasReferences.Shortcut(Tile(3560, 5280), Area(3550, 5282, 3564, 5292), Tile(3560, 5276), setOf(order[2]), 769, 2),
            ScabarasReferences.Shortcut(Tile(3560, 5280), Area(3550, 5268, 3564, 5278), Tile(3560, 5284), setOf(order[3]), 769, 2))
        return ScabarasReferences.Layout(
            memoryButton = Tile(mem.first - 2, mem.second), memoryTiles = diamond.map { cell(mem, it.first, it.second) },
            obelisks = listOf(0 to 0, 0 to 4, 2 to 2, 4 to 0, 4 to 4).map { cell(ob, it.first, it.second).transform(0, 1) },
            lights = ScabarasPuzzle.Lights.CELLS.map { cell(light, (it.first - 1) * 2, (it.second - 1) * 2) },
            tablet = Tile(number.first - 2, number.second), additionTiles = (0..4).flatMap { row -> (0..4).map { col -> cell(number, row, col) } },
            matchingButtons = listOf((0..2).flatMap { row -> (0..2).map { col -> Tile(3574 - row * 2, 5288 - col * 2) } },
                (0..2).flatMap { row -> (0..2).map { col -> Tile(3574 - row * 2, 5276 - col * 2) } }),
            finalButton = Tile(3577, 5280), gates = gates, shortcuts = shortcuts,
            soloSkipsOnClear = mapOf(order[0] to setOf(order[1], order[2]), order[1] to setOf(order[0], order[3])),
            numberPrerequisites = if (order.indexOf(Kind.ADDITION) < 2) emptySet() else first,
            soloStatueFrom = Tile(3569, 5279), soloStatueTo = Tile(3570, 5279))
    }
}
