package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasReferences.Kind
import kotlin.test.*

/** Drives the real room queues with walked paths. Combat rolls/client packets remain Claude's in-game acceptance check. */
class ToaScabarasWalkingTests {
    private val solutions: Map<Int, List<Int>> = checkNotNull(javaClass.getResourceAsStream("/toa/scabaras-addition-solutions.tsv"))
        .bufferedReader().use { it.readLines() }.filterNot { it.startsWith("#") || it.isBlank() }.associate {
            val fields = it.split('\t'); fields[0].toInt() to fields[1].split(',').map(String::toInt)
        }

    private class Walker(val f: ToaPuzzleFixture, val geometry: ToaPuzzleSourceCollision) {
        fun refresh(solo: Boolean) {
            val dynamic = f.entities.filterIsInstance<GameObject>().map {
                DynamicObject(it.id - 50000, it.type, it.rot, checkNotNull(ToaMap.source(f.map, it.tile)))
            }
            geometry.refresh(dynamic, if (solo) setOf(Tile(3569, 5279) to 2) else emptySet())
        }
        fun tile(player: Player) = checkNotNull(ToaMap.source(f.map, player.tile))
        fun walk(player: Player, destination: Tile, avoid: Set<Tile> = emptySet()) {
            val route = geometry.route(tile(player), destination, stepOn = true, avoid = avoid - destination)
            assertTrue(route.success, "walk ${tile(player)}->$destination stops at${route.tail}")
            route.path.forEach { next -> f.at(player, next); f.advance() }
            assertEquals(destination, tile(player))
        }
        fun approach(player: Player, destination: Tile, reach: Int? = null, avoid: Set<Tile> = emptySet()) {
            val route = geometry.route(tile(player), destination, reach = reach, avoid = avoid)
            assertTrue(route.success, "click ${tile(player)}->$destination stops at${route.tail}")
            route.path.forEach { next -> f.at(player, next); f.advance() }
        }
    }

    @Test fun `all chamber assignments have a physically walked solo and eight player solve route`() {
        val saved = ScabarasReferences.profile
        val defaults = ScabarasDefaults.reference()
        val orders = permutations(listOf(Kind.MEMORY, Kind.LIGHTS, Kind.OBELISKS, Kind.ADDITION))
        try {
            //The last two repeats also exercise the remaining Wiki targets44/45 through actual intermediate walking tiles.
            for (size in listOf(1, 8)) for ((attempt, order) in (orders + orders.take(2)).withIndex()) {
                val layout = ScabarasDefaults.layoutForOrder(size, order)
                val initial = defaults.initial.value(size, attempt).copy(additionTarget = 20 + attempt)
                ScabarasReferences.profile = defaults.copy(
                    layout = ToaPuzzleReferences.Evidence({ layout }, "exhaustive source chamber arrangement"),
                    initial = ToaPuzzleReferences.Evidence({ _, _ -> initial }, "observed value domains, controlled target"),
                    scarabSpawns = ToaPuzzleReferences.Evidence(emptyList(), "isolate walked puzzle actions from nuisance combat"))
                lateinit var room: ScabarasRoom
                ToaPuzzleFixture(size, ToaPath.SCABARAS) { ScabarasRoom(it).also { room = it } }.use { f ->
                    val walking = Walker(f, ToaPuzzleSourceCollision("scabaras", Tile(3520, 5248)))
                    val players = f.players.take(if (size == 1) 1 else 2)
                    walking.refresh(size == 1)
                    fun solve(kind: Kind, player: Player) {
                        when (kind) {
                            Kind.MEMORY -> {
                                walking.approach(player, layout.memoryButton)
                                assertTrue(room.button(player, f.obj(45338, layout.memoryButton)))
                                val observed = mutableListOf<Int>()
                                var previous: Tile? = null
                                repeat(12) {
                                    f.advance()
                                    val shown = f.entities.filterIsInstance<GameObject>().filter { it.id == 50000 + 45341 }
                                        .mapNotNull { ToaMap.source(f.map, it.tile) }.singleOrNull()
                                    if (shown != null && shown != previous) observed += layout.memoryTiles.indexOf(shown)
                                    previous = shown
                                }
                                assertEquals(5, observed.size, "a player must actually see all five flashes")
                                for (cell in observed) walking.walk(player, layout.memoryTiles[cell], layout.memoryTiles.toSet())
                            }
                            Kind.LIGHTS -> {
                                val state = ScabarasPuzzle.Lights(initial.lights)
                                for (cell in state.solution()) walking.walk(player, layout.lights[cell], layout.lights.toSet())
                            }
                            Kind.OBELISKS -> {
                                for (cell in initial.obeliskOrder) {
                                    walking.approach(player, layout.obelisks[cell], reach = 10)
                                    val npc = room.scope.spawned[cell]
                                    val hit = Hit.Builder().addHit(1, 0).build().also { it.source = player }
                                    npc.hitModifier!!.invoke(hit); f.advance()
                                }
                            }
                            Kind.ADDITION -> {
                                //Walk around the number board before reading; walking across it can already contribute glyphs.
                                walking.approach(player, layout.tablet, avoid = layout.additionTiles.toSet())
                                assertTrue(room.inspect(player, f.obj(45339, layout.tablet)))
                                val witness = solutions.getValue(initial.additionTarget).map { layout.additionTiles[it] }.toSet()
                                //Any order is legal, but every intermediate walking tile also triggers the real room director.
                                for (tile in witness) walking.walk(player, tile, layout.additionTiles.toSet() - witness)
                            }
                            Kind.MATCHING -> error("matching is handled with both boards below")
                        }
                        walking.refresh(size == 1)
                    }
                    if (size == 1) {
                        //Strategies907: choose a route that skips obelisks, cross over via the passage, solve the opposite second room.
                        val first = if (order[0] == Kind.OBELISKS || order[3] == Kind.OBELISKS) 1 else 0
                        val p = players.single()
                        f.at(p, Tile(3533, if (first == 0) 5286 else 5274)) //After Pass on the static entrance barrier45135.
                        solve(order[first], p)
                        val shortcut = layout.shortcuts[first]
                        //45343 is a WALL, used from its bordering north/south tile (ObjectPathAction's wall approach), not a centrepiece.
                        walking.walk(p, shortcut.objectTile.transform(0, if (first == 0) 1 else -1))
                        val passage = DynamicObject(50000 + 45343, 0, if (first == 0) 1 else 3,
                            ToaMap.instance(f.map, shortcut.objectTile))
                        assertTrue(room.shortcut(p, passage))
                        f.advance(3)
                        solve(order[3 - first], p)
                    } else {
                        for (lane in 0..1) {
                            val p = players[lane]
                            f.at(p, Tile(3533, if (lane == 0) 5286 else 5274))
                            solve(order[lane], p)
                        }
                        for (lane in 0..1) solve(order[2 + lane], players[lane])
                    }
                    assertNull(f.raid.progress.boss(ToaPath.SCABARAS), "matching still gates the boss")
                    val panels = layout.matchingButtons.flatten().toSet()
                    for (symbol in 1..9) {
                        if (size == 1 && symbol in initial.soloPreSolved) continue
                        for (board in 0..1) {
                            val symbols = if (board == 0) initial.leftSymbols else initial.rightSymbols
                            val p = if (size == 1) players[0] else players[board]
                            walking.walk(p, layout.matchingButtons[board][symbols.indexOf(symbol)], panels)
                        }
                    }
                    assertTrue(f.raid.progress.challengeCleared(ToaPath.SCABARAS), "size$size order$order")
                    assertTrue(f.entities.isEmpty())
                    assertNotNull(f.raid.progress.boss(ToaPath.SCABARAS))
                }
            }
        } finally { ScabarasReferences.profile = saved }
    }

    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { head -> permutations(values - head).map { listOf(head) + it } }
}
