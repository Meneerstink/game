package gg.rsmod.plugins.content.mechanics.pathing

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.attr.INTERACTING_PLAYER_ATTR
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.path.MeleeReach
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.game.task.QueueHandlerTask
import gg.rsmod.game.task.WorldRemoveTask
import gg.rsmod.game.task.sequential.SequentialNpcCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerCycleTask
import gg.rsmod.game.task.sequential.SequentialPlayerPostCycleTask
import gg.rsmod.game.task.sequential.SequentialSynchronizationTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference
import java.net.URLClassLoader
import java.nio.file.Paths

/**
 * N245 / N448 (owner 2026-10-31 + 2026-11-06: "als je interact met een monster, npc of player dansen ze soort van een lus"): the
 * whole class of approaches on the real tick order (queues -> player cycle -> npc cycle -> players move -> npcs move) with the
 * real world, collision and combat plugin. Every interaction that walks to a pawn (attack npc, npc attacks back, attack player,
 * both players attack, trade/talk on a player or an npc, a follower) must END beside its target within a few ticks, from every
 * start (diagonal, knight's move, far), walking and running, 1x1 and larger npcs - never step round each other for ever.
 * Follow is not tested here: a mutual Follow may dance as in OSRS (Wiki Pathfinding "Double follow").
 */
class InteractionApproachLoopTests {
    private val players = mutableListOf<Client>()
    private val npcs = mutableListOf<Npc>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        npcs.forEach { world.remove(it) }
        players.clear()
        npcs.clear()
    }

    /** The starts every scenario runs from (offset of the second pawn from the first). */
    private val starts = listOf(
        1 to 1, -1 to 1, 1 to -1, -1 to -1, // diagonal
        2 to 1, 1 to 2, -2 to 1, 2 to -1, // knight's move
        2 to 2, -2 to -2, 3 to 3, 3 to 1, 5 to 2, -4 to 3, 6 to 6,
    )

    private fun limit(dx: Int, dz: Int) = maxOf(Math.abs(dx), Math.abs(dz)) + 4

    @Test
    fun `a player attacking an npc that attacks him back ends beside it`() {
        val failures = mutableListOf<String>()
        for (size in listOf(1, 2, 3)) for (run in listOf(false, true)) for ((dx, dz) in starts) {
            val npc = spawnNpc(size)
            val p = player("pa", ARENA.transform(dx + (if (dx > 0) size - 1 else 0), dz + (if (dz > 0) size - 1 else 0)), run)
            p.attack(npc)
            npc.attack(p)
            val r = settle(p, npc, limit(dx, dz) + size)
            if (r != null) failures += "npc size $size run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `a player attacking a passive npc ends beside it`() {
        val failures = mutableListOf<String>()
        for (size in listOf(1, 2, 3)) for (run in listOf(false, true)) for ((dx, dz) in starts) {
            val npc = spawnNpc(size)
            val p = player("pp", ARENA.transform(dx + (if (dx > 0) size - 1 else 0), dz + (if (dz > 0) size - 1 else 0)), run)
            p.attack(npc)
            val r = settle(p, npc, limit(dx, dz) + size)
            if (r != null) failures += "npc size $size run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `two players attacking each other end beside each other`() {
        val failures = mutableListOf<String>()
        for (bFirst in listOf(false, true)) for (run in listOf(false, true)) for ((dx, dz) in starts) {
            // Both orders of the player list: the first one moves first.
            val b0 = if (bFirst) player("b", ARENA.transform(dx, dz), run) else null
            val a = player("a", ARENA, run)
            val b = b0 ?: player("b", ARENA.transform(dx, dz), run)
            a.attack(b)
            b.attack(a)
            val r = settle(a, b, limit(dx, dz))
            if (r != null) failures += "bFirst=$bFirst run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `two players clicking an option on each other at the same time both get there`() {
        val failures = mutableListOf<String>()
        for (bFirst in listOf(false, true)) for (run in listOf(false, true)) for ((dx, dz) in starts) {
            val b0 = if (bFirst) player("tb", ARENA.transform(dx, dz), run) else null
            val a = player("ta", ARENA, run)
            val b = b0 ?: player("tb", ARENA.transform(dx, dz), run)
            clickPlayer(a, b)
            clickPlayer(b, a)
            val r = interactionEnds(listOf(a to b, b to a), limit(dx, dz))
            if (r != null) failures += "bFirst=$bFirst run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `a player chasing a player who walks away keeps chasing and ends beside him`() {
        val failures = mutableListOf<String>()
        for (run in listOf(false, true)) for ((dx, dz) in listOf(1 to 1, 2 to 0, 0 to 3, -2 to 2)) {
            val a = player("ca", ARENA, run)
            val b = player("cb", ARENA.transform(dx, dz), false)
            b.walkTo(b.tile.transform(8, 0))
            a.attack(b)
            tick(4)
            val r = settle(a, b, 14)
            if (r != null) failures += "run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `talking to an npc that attacks back or walks gets there`() {
        val failures = mutableListOf<String>()
        for (size in listOf(1, 2)) for (run in listOf(false, true)) for ((dx, dz) in starts) {
            val npc = spawnNpc(size)
            val p = player("tk", ARENA.transform(dx + (if (dx > 0) size - 1 else 0), dz + (if (dz > 0) size - 1 else 0)), run)
            npc.attack(p) // an npc that walks to the player while he walks to it
            clickNpc(p, npc)
            val r = interactionEnds(listOf(p to npc), limit(dx, dz) + size)
            if (r != null) failures += "npc size $size run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `talking to your own follower gets there and the follower never stands on you`() {
        val failures = mutableListOf<String>()
        for (size in listOf(1, 2)) for (run in listOf(false, true)) for ((dx, dz) in starts) {
            val npc = spawnNpc(size, follower = true)
            val p = player("fo", ARENA.transform(dx + (if (dx > 0) size - 1 else 0), dz + (if (dz > 0) size - 1 else 0)), run)
            follower = p to npc
            clickNpc(p, npc)
            val r = interactionEnds(listOf(p to npc), limit(dx, dz) + size)
            if (r != null) failures += "follower size $size run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    @Test
    fun `a follower never steps onto its walking owner`() {
        val failures = mutableListOf<String>()
        // A walk with turns, U-turns and crossings: east 6, north 4, west 3, south 6, east 2, north 1, back west 5.
        val legs = listOf(6 to 0, 0 to 4, -3 to 0, 0 to -6, 2 to 0, 0 to 1, -5 to 0)
        for (size in listOf(1, 2)) for (run in listOf(false, true)) for (fast in listOf(false, true)) {
            val npc = spawnNpc(size, follower = true)
            val p = player("ow", ARENA.transform(size, 0), run)
            follower = p to npc
            followerRuns = fast
            var at = p.tile
            for ((lx, lz) in legs) {
                at = at.transform(lx, lz)
                p.walkTo(at)
                repeat(Math.max(Math.abs(lx), Math.abs(lz)) + 1) {
                    tick(1)
                    if (overlaps(p, npc)) failures += "size $size run=$run fast=$fast: follower at ${npc.tile} on its owner at ${p.tile}"
                }
            }
            tick(4)
            if (!MeleeReach.bordersWithoutWall(world.collision, npc.tile, size, size, p.tile, 1, 1)) {
                failures += "size $size run=$run: the follower did not end beside its owner (${npc.tile} vs ${p.tile})"
            }
            reset()
        }
        report(failures)
    }

    /** Breaktest: a wall between them - both players walk round it to the free side and meet, no loop at the corner. */
    @Test
    fun `two players on either side of a wall meet round its end`() {
        val failures = mutableListOf<String>()
        // A wall 5 tiles long between z=0 and z=1 (x 0..4); the players stand on both sides of its middle.
        val wall = (0..4).map { ARENA.transform(it, 0) }
        walls(wall, add = true)
        try {
            for (bFirst in listOf(false, true)) for (run in listOf(false, true)) for (option in listOf("attack", "trade")) {
                val b0 = if (bFirst) player("wb", ARENA.transform(2, 1), run) else null
                val a = player("wa", ARENA.transform(2, 0), run)
                val b = b0 ?: player("wb", ARENA.transform(2, 1), run)
                val r =
                    if (option == "attack") {
                        a.attack(b)
                        b.attack(a)
                        settle(a, b, 14)
                    } else {
                        clickPlayer(a, b)
                        clickPlayer(b, a)
                        interactionEnds(listOf(a to b, b to a), 14)
                    }
                if (r != null) failures += "$option bFirst=$bFirst run=$run: $r"
                reset()
            }
        } finally {
            walls(wall, add = false)
        }
        report(failures)
    }

    /**
     * N596 (2026-11-06, Pest Control 6427,34 -> shifter 6427,35: 'no route' yet the swing landed): a player walled in on all four
     * sides attacks an npc on the next tile beyond the wall (all four sides, 1x1 and 2x2). OSRS: no melee over a wall or fence -
     * he never swings; without the wall the same attack does fire.
     */
    @Test
    fun `no melee swing over a wall`() {
        val failures = mutableListOf<String>()
        val cell = ARENA.transform(4, 4)
        for (size in listOf(1, 2)) for ((dx, dz) in listOf(0 to 1, 1 to 0, 0 to -size, -size to 0)) {
            boxIn(cell, add = true)
            try {
                val npc = spawnNpcAt(size, cell.transform(dx, dz))
                val p = player("mw", cell, false)
                if (gg.rsmod.plugins.content.combat.Combat.inMeleeReach(p, npc, 1)) failures += "size $size at ($dx,$dz): inMeleeReach over the wall"
                p.attack(npc)
                repeat(6) {
                    tick(1)
                    if (p.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) failures += "size $size at ($dx,$dz): swung over the wall at tick ${it + 1}"
                }
                reset()
            } finally {
                boxIn(cell, add = false)
            }
        }
        // The control: no wall, same spot - the swing fires.
        val npc = spawnNpcAt(1, cell.transform(0, 1))
        val p = player("mo", cell, false)
        assertTrue("in reach without a wall", gg.rsmod.plugins.content.combat.Combat.inMeleeReach(p, npc, 1))
        p.attack(npc)
        var swung = false
        repeat(4) { tick(1); swung = swung || p.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY) }
        if (!swung) failures += "control without a wall: no swing"
        reset()
        report(failures)
    }

    /** Walls on all four edges of [cell] (both sides of each edge). */
    private fun boxIn(
        cell: Tile,
        add: Boolean,
    ) {
        val n = gg.rsmod.game.model.Direction.NORTH
        val s = gg.rsmod.game.model.Direction.SOUTH
        val e = gg.rsmod.game.model.Direction.EAST
        val w = gg.rsmod.game.model.Direction.WEST
        val update = gg.rsmod.game.model.collision.CollisionUpdate.Builder()
        update.setType(if (add) gg.rsmod.game.model.collision.CollisionUpdate.Type.ADD else gg.rsmod.game.model.collision.CollisionUpdate.Type.REMOVE)
        update.putTile(cell, false, n, s, e, w)
        update.putTile(cell.transform(0, 1), false, s)
        update.putTile(cell.transform(0, -1), false, n)
        update.putTile(cell.transform(1, 0), false, w)
        update.putTile(cell.transform(-1, 0), false, e)
        world.collision.applyUpdate(update.build())
    }

    /**
     * Breaktest: three npcs stand round the target. A player is not blocked by npc bodies (osrs-docs Entity Collision: only npcs check
     * the entity flags), so he may end on any side - also on another npc - as long as he ends beside the target without a loop.
     */
    @Test
    fun `attacking an npc surrounded by other npcs ends beside it`() {
        val failures = mutableListOf<String>()
        for (run in listOf(false, true)) for ((dx, dz) in listOf(0 to 4, 4 to 0, -3 to -3)) {
            val target = spawnNpc(1)
            listOf(1 to 0, -1 to 0, 0 to 1).forEach { (x, z) -> spawnNpcAt(1, ARENA.transform(x, z)) }
            val p = player("cr", ARENA.transform(dx, dz), run)
            p.attack(target)
            val r = settle(p, target, 16)
            if (r != null) failures += "run=$run start ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    /** Breaktest: a 3x3 npc stands on the player - the player steps out from under it and fights beside it. */
    @Test
    fun `a player under a big npc steps out and fights beside it`() {
        val failures = mutableListOf<String>()
        for (run in listOf(false, true)) for ((dx, dz) in listOf(1 to 1, 0 to 0, 2 to 1)) {
            val npc = spawnNpc(3)
            val p = player("un", ARENA.transform(dx, dz), run)
            p.attack(npc)
            val r = settle(p, npc, 10)
            if (r != null) failures += "run=$run under at ($dx,$dz): $r"
            reset()
        }
        report(failures)
    }

    /** Follow is no approach: a mutual Follow may dance (OSRS Wiki "Double follow") but stays close and never throws. */
    @Test
    fun `a mutual Follow stays close together`() {
        val a = player("fa", ARENA, false)
        val b = player("fb", ARENA.transform(3, 2), false)
        // following.plugin.kts is not loaded here: its cycle is a plain walk to the tile behind the target, every tick.
        repeat(20) {
            if (!a.hasMoveDestination()) a.walkTo(b.tile.transform(-b.faceDirection.getDeltaX(), -b.faceDirection.getDeltaZ()))
            if (!b.hasMoveDestination()) b.walkTo(a.tile.transform(-a.faceDirection.getDeltaX(), -a.faceDirection.getDeltaZ()))
            tick(1)
        }
        assertTrue("a Follow walk is no approach", a.approach == null && b.approach == null)
        val gap = maxOf(Math.abs(a.tile.x - b.tile.x), Math.abs(a.tile.z - b.tile.z))
        assertTrue("mutual Follow stays within 2 tiles (was $gap: ${a.tile} / ${b.tile})", gap <= 2)
    }

    private fun walls(
        tiles: List<Tile>,
        add: Boolean,
    ) {
        val update = gg.rsmod.game.model.collision.CollisionUpdate.Builder()
        update.setType(if (add) gg.rsmod.game.model.collision.CollisionUpdate.Type.ADD else gg.rsmod.game.model.collision.CollisionUpdate.Type.REMOVE)
        tiles.forEach { update.putTile(it, false, gg.rsmod.game.model.Direction.NORTH) }
        tiles.forEach { update.putTile(it.transform(0, 1), false, gg.rsmod.game.model.Direction.SOUTH) }
        world.collision.applyUpdate(update.build())
    }

    private fun spawnNpcAt(
        size: Int,
        at: Tile,
    ): Npc {
        val npc = Npc(ids.getValue(size), at, world)
        assertTrue("npc spawned", world.spawn(npc))
        npcs += npc
        return npc
    }
    // --- harness -------------------------------------------------------------------------------------------------------------

    private var follower: Pair<Client, Npc>? = null

    /** A follower that keeps up with a running owner (Pets.FAST_PETS: Nexling, Babi - two steps a tick). */
    private var followerRuns = false

    private fun report(failures: List<String>) {
        if (failures.isNotEmpty()) fail("${failures.size} approach loop(s):\n" + failures.joinToString("\n"))
    }

    private fun reset() {
        follower = null
        followerRuns = false
        cleanUp()
        tick(1)
    }

    /**
     * Ticks until [a] stands beside [b] (melee reach) for 3 ticks in a row with neither moving; null = ok, else what happened.
     */
    private fun settle(
        a: Pawn,
        b: Pawn,
        maxTicks: Int,
    ): String? {
        val trail = StringBuilder()
        var still = 0
        for (t in 1..maxTicks + 3) {
            val ba = Tile(a.tile)
            val bb = Tile(b.tile)
            tick(1)
            trail.append(" ${a.tile.x - ARENA.x},${a.tile.z - ARENA.z}|${b.tile.x - ARENA.x},${b.tile.z - ARENA.z}")
            val reach = MeleeReach.bordersWithoutWall(world.collision, a.tile, a.getSize(), a.getSize(), b.tile, b.getSize(), b.getSize())
            if (reach && a.tile == ba && b.tile == bb) still++ else still = 0
            if (still >= 2) return if (t - 2 > maxTicks) "beside only after ${t - 2} ticks (limit $maxTicks):$trail" else null
        }
        return "never settled beside it in ${maxTicks + 3} ticks:$trail"
    }

    /** Ticks until every clicker's interaction ran (its face target is cleared at the end of PawnPathAction.walk) - in reach. */
    private fun interactionEnds(
        pairs: List<Pair<Client, Pawn>>,
        maxTicks: Int,
    ): String? {
        val trail = StringBuilder()
        val done = HashMap<Client, Int>()
        for (t in 1..maxTicks + 6) {
            tick(1)
            trail.append(pairs.joinToString(" ", " [") { (p, o) -> "${p.tile.x - ARENA.x},${p.tile.z - ARENA.z}>${o.tile.x - ARENA.x},${o.tile.z - ARENA.z}" } + "]")
            for ((p, o) in pairs) {
                if (p in done) continue
                if (p.attr[FACING_PAWN_ATTR]?.get() == null) {
                    if (!MeleeReach.bordersWithoutWall(world.collision, p.tile, 1, 1, o.tile, o.getSize(), o.getSize())) {
                        return "${p.username}'s interaction ran out of reach at tick $t:$trail"
                    }
                    done[p] = t
                }
            }
            if (done.size == pairs.size) {
                val worst = done.values.max()
                return if (worst > maxTicks) "interaction only after $worst ticks (limit $maxTicks):$trail" else null
            }
        }
        return "interaction never ran in ${maxTicks + 6} ticks:$trail"
    }

    private fun overlaps(
        a: Pawn,
        b: Pawn,
    ): Boolean {
        val sa = a.getSize()
        val sb = b.getSize()
        return a.tile.height == b.tile.height && a.tile.x < b.tile.x + sb && b.tile.x < a.tile.x + sa && a.tile.z < b.tile.z + sb && b.tile.z < a.tile.z + sa
    }

    private fun clickNpc(
        p: Client,
        npc: Npc,
    ) {
        p.fullInterruption(movement = true, interactions = true, queue = true)
        p.attr[INTERACTING_OPT_ATTR] = 1
        p.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
        p.executePlugin(PawnPathAction.walkPlugin)
    }

    private fun clickPlayer(
        p: Client,
        other: Client,
    ) {
        p.fullInterruption(movement = true, interactions = true, queue = true)
        p.attr[INTERACTING_PLAYER_ATTR] = WeakReference(other)
        p.attr[INTERACTING_OPT_ATTR] = 4
        p.executePlugin(PawnPathAction.walkPlugin)
    }

    private fun spawnNpc(
        size: Int,
        follower: Boolean = false,
    ): Npc {
        val id = ids.getValue(size)
        val npc = Npc(id, ARENA, world)
        if (follower) npc.ignoresEntityCollision = true
        assertTrue("npc spawned", world.spawn(npc))
        npcs += npc
        return npc
    }

    private fun player(
        name: String,
        at: Tile,
        run: Boolean,
    ): Client {
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.tile = at
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION).forEach { client.skills.setBaseLevel(it, 99) }
        client.setCurrentLifepoints(client.getMaximumLifepoints())
        client.runEnergy = 100.0
        val system = mockk<GameSystem>(relaxed = true)
        val out = mutableListOf<Message>()
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        assertTrue("registered $name", world.register(client))
        client.varps.setState(173, if (run) 1 else 0)
        players += client
        return client
    }

    private fun tick(n: Int) {
        repeat(n) {
            for (task in tasks) {
                if (task is SequentialPlayerCycleTask) {
                    // SequentialPlayerCycleTask without its timing map (a relaxed mock's Map.merge answers with a mock of Any, which
                    // mockk cannot always build): every player's cycle in PID order.
                    world.playersInPidOrder().forEach { it.cycle() }
                    // The familiar timer runs inside the player cycle (familiar.plugin.kts), before anyone moves.
                    follower?.let { (p, f) -> Familiar.follow(p, f, runs = followerRuns) }
                    continue
                }
                task.execute(world, service)
            }
            players.forEach { it.setCurrentLifepoints(it.getMaximumLifepoints()) }
            npcs.forEach { it.setCurrentLifepoints(it.getMaximumLifepoints()) }
            worldCycle.invoke(world)
        }
    }

    companion object {
        /** An open floor: the first tile whose box x -8..11, z -6..11 has no collision at all (nothing else spawned there). */
        val ARENA: Tile by lazy {
            val dirs = listOf(gg.rsmod.game.model.Direction.NORTH, gg.rsmod.game.model.Direction.EAST, gg.rsmod.game.model.Direction.SOUTH, gg.rsmod.game.model.Direction.WEST)
            val x0 = 3200
            val z0 = 2960
            val w = 160
            val open = Array(w) { x -> BooleanArray(w) { z -> val t = Tile(x0 + x, z0 + z); !world.collision.isClipped(t) && dirs.all { d -> world.collision.canTraverse(t, d, projectile = false, water = false) } } }
            var found: Tile? = null
            loop@ for (cx in 8 until w - 12) for (cz in 6 until w - 12) {
                if ((-8..11).all { dx -> (-6..11).all { dz -> open[cx + dx][cz + dz] } }) { found = Tile(x0 + cx, z0 + cz); break@loop }
            }
            println("InteractionApproachLoopTests arena: $found")
            found ?: error("no open arena near $x0,$z0")
        }

        private val ids: Map<Int, Int> by lazy {
            val defs = world.definitions
            val out = HashMap<Int, Int>()
            for (id in 1 until 14000) {
                val d = defs.getNullable(NpcDef::class.java, id) ?: continue
                if (d.combatLevel <= 0 || d.size !in 1..3 || out.containsKey(d.size)) continue
                if (d.options.none { it == "Attack" }) continue
                out[d.size] = id
                if (out.size == 3) break
            }
            out
        }

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.combat.Combat_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts",
            )

        private val service = mockk<GameService>(relaxed = true)

        private val tasks =
            listOf(
                QueueHandlerTask(),
                SequentialPlayerCycleTask(),
                WorldRemoveTask(),
                SequentialNpcCycleTask(),
                SequentialSynchronizationTask(),
                SequentialPlayerPostCycleTask(),
            )

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
                .invoke(world, Paths.get("..", "..", "data", "blocks.yml").toFile())
            World::class.java.getDeclaredField("random").apply { isAccessible = true }.set(world, java.util.Random(245L))
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), InteractionApproachLoopTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }
    }
}
