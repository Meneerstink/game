package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.service.GameService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.combat.getCombatTarget
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil

/**
 * Breach audit 2026-09-27: an in-engine breach test run, because the agent cannot log in to the client.
 *
 * Runs only when the server is started with `-Drsps.breachSim=true` (never in the owner's normal start). It logs in a headless
 * test player (an [EmbeddedChannel] that swallows every packet), opens a real breach and fires its spawner, then puts every
 * spawnable breach monster one at a time a few tiles from the player and records per monster, from the real game loop:
 * when it aggroes, where it attacks from (the gap between the two footprints), which sections/animations it uses, whether it
 * ever stands in reach without attacking, whether it follows inside the leash, walks home outside it and keeps fighting while
 * the player hits it. Every result line starts with `BREACHSIM`. The player is removed without a save when the run ends.
 */
object BreachSim {
    const val PROPERTY = "rsps.breachSim"

    private const val NAME = "Breachsim"

    /** The test player's hitpoints are topped up to this every tick so no monster ends the run. */
    private const val LIFE = 20000

    fun enabled(): Boolean = System.getProperty(PROPERTY) == "true"

    private fun log(line: String) = logger.info("BREACHSIM {}", line)

    fun gap(
        a: Pawn,
        b: Pawn,
    ): Int {
        val sa = a.getSize()
        val sb = b.getSize()
        val dx = maxOf(0, a.tile.x - (b.tile.x + sb - 1), b.tile.x - (a.tile.x + sa - 1))
        val dz = maxOf(0, a.tile.z - (b.tile.z + sb - 1), b.tile.z - (a.tile.z + sa - 1))
        return maxOf(dx, dz)
    }

    /** Gap between [npc]'s footprint and a single tile. */
    fun gapTo(
        npc: Npc,
        tile: Tile,
    ): Int {
        val s = npc.getSize()
        return maxOf(maxOf(0, npc.tile.x - tile.x, tile.x - (npc.tile.x + s - 1)), maxOf(0, npc.tile.z - tile.z, tile.z - (npc.tile.z + s - 1)))
    }

    private fun gapTo2(
        a: Tile,
        b: Tile,
    ): Int = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    private var bots = 0

    private fun bot(
        world: World,
        tile: Tile,
    ): Client {
        val name = if (bots++ == 0) NAME else "$NAME${bots}"
        val channel =
            EmbeddedChannel(
                object : ChannelOutboundHandlerAdapter() {
                    override fun write(
                        ctx: ChannelHandlerContext,
                        msg: Any,
                        promise: ChannelPromise,
                    ) {
                        ReferenceCountUtil.release(msg)
                        promise.setSuccess()
                    }
                },
            )
        val bot = Client(channel, world)
        bot.loginUsername = name
        bot.username = name
        bot.passwordHash = ""
        bot.uid = gg.rsmod.game.model.PlayerUID(name)
        bot.tile = tile
        bot.gameSystem = GameSystem(channel, world, bot, world.getService(GameService::class.java)!!)
        for (skill in 0..6) bot.skills.setBaseLevel(skill, 99)
        check(world.register(bot)) { "test player not registered" }
        bot.login()
        // Never saved: the autosave timer would write a save file for the test player.
        bot.timers.remove(gg.rsmod.game.model.timer.SAVE_TIMER)
        gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.forfeit(bot)
        bot.setCurrentLifepoints(LIFE)
        return bot
    }

    private class Track(val npc: Npc) {
        var aggroTick = -1
        var firstAttackTick = -1
        val attackGaps = sortedSetOf<Int>()
        val anims = sortedSetOf<Int>()
        var attacks = 0
        var moves = 0
        var idleInReach = 0
        var worstIdleInReach = 0
        var lastTile: Tile = npc.tile
        var lastAttackCycle: Int? = null
    }

    fun start(world: World) {
        world.queue {
            wait(20)
            val lines = ArrayList<String>()
            fun out(s: String) {
                lines += s
                log(s)
            }
            var bot: Client? = null
            try {
                // ---- 1. a real breach: open, fire, land, close ------------------------------------------------------
                DeadmanBreach.close(world)
                val opened = DeadmanBreach.open(world, DeadmanBreach.Kind.LOCALISED) ?: error("no localised breach opened")
                val site = opened.sites.maxByOrNull { it.landing.size }!!
                out("open sites=${opened.sites.size} site='${site.name}' spawners=${site.spawners} objects=${site.objects.size} landing=${site.landing.size}")
                val before = site.npcs.size
                repeat(3) { DeadmanBreach.fire(world, site) }
                out("fired 3: pending=${site.pending.size}")
                var sawBossSpawn = false
                repeat(40) {
                    wait(1)
                    if (site.bossSpawns.isNotEmpty()) sawBossSpawn = true
                }
                out("after 40 ticks: spawned=${site.npcs.size - before} bossSpawnSeen=$sawBossSpawn bossSpawnsLeft=${site.bossSpawns.size} pending=${site.pending.size}")
                val landed = site.npcs.toList()
                landed.forEach { out("landed ${it.id} ${it.name} at ${it.tile} alive=${world.npcs.contains(it)}") }
                val singleSite = opened.sites.firstOrNull { !it.multi }
                val removed = DeadmanBreach.close(world)
                out("close: removed=$removed objectsLeft=${site.objects.size} live=${DeadmanBreach.live.size} active=${DeadmanBreach.active != null}")

                // ---- 2. every monster against the test player ------------------------------------------------------
                val arena = site.landing
                val home = arena.minByOrNull { it.getDistance(site.spawners.first()) }!!
                bot = bot(world, home)
                wait(2)
                out("test player at ${bot.tile} protected=${gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.isProtected(bot)} multi=${bot.tile.isMulti(world)}")
                val only = System.getProperty("rsps.breachSim.ids")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }
                for (m in BreachMonsters.SPAWNABLE.filter { only == null || it.id in only }) {
                    // Nothing of the previous monster may stay (a summoned TzTok-Jad-Rek kept the player "under attack" in single-way).
                    DeadmanBreach.close(world)
                    bot.resetInteractions()
                    bot.moveTo(home)
                    bot.setCurrentLifepoints(LIFE)
                    wait(18) // the "under attack" window of the previous monster (ACTIVE_COMBAT_TIMER 17)
                    val size = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, m.id)?.size ?: 1
                    val spot =
                        arena.filter { t ->
                            val g = maxOf(0, t.x - home.x, home.x - (t.x + size - 1)).coerceAtLeast(maxOf(0, t.z - home.z, home.z - (t.z + size - 1)))
                            g in 5..9 && DeadmanBreach.fits(world, t, size) && DeadmanBreach.canLeave(world, t, size)
                        }.firstOrNull()
                    if (spot == null) {
                        out("${m.id} ${m.name}: no free tile 5-9 away for size $size")
                        continue
                    }
                    val npc = DeadmanBreach.spawnMonster(world, m.id, spot, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
                    val t = Track(npc)
                    var tick = 0
                    val reach = NpcAttacks.attackRange(npc) ?: 1
                    fun step() {
                        tick++
                        if (npc.tile != t.lastTile) {
                            t.moves++
                            t.lastTile = npc.tile
                        }
                        if (t.aggroTick < 0 && npc.getCombatTarget() === bot) t.aggroTick = tick
                        val cycle = NpcAttacks.lastDataAttackCycle(npc)
                        if (cycle != null && cycle != t.lastAttackCycle) {
                            t.lastAttackCycle = cycle
                            t.attacks++
                            if (t.firstAttackTick < 0) t.firstAttackTick = tick
                            t.attackGaps += gap(npc, bot)
                            t.anims += NpcAttacks.attackAnimThisCycle(npc).takeIf { it >= 0 } ?: npc.combatDef.attackAnimation
                            t.idleInReach = 0
                        } else if (npc.getCombatTarget() === bot && NpcAttacks.hasValidAttack(npc, bot)) {
                            t.idleInReach++
                            t.worstIdleInReach = maxOf(t.worstIdleInReach, t.idleInReach)
                        } else {
                            t.idleInReach = 0
                        }
                        if (tick <= 8 && t.firstAttackTick < 0) {
                            out("  trace ${m.id} t=$tick npc=${npc.tile} bot=${bot.tile} gap=${gap(npc, bot)} target=${npc.getCombatTarget()?.let { if (it === bot) "bot" else it.toString() }} moving=${npc.hasMoveDestination()} ${NpcAttacks.explainReach(npc, bot)}")
                        }
                        bot.setCurrentLifepoints(LIFE)
                    }
                    // a) aggro, approach, attack
                    repeat(25) { wait(1); step() }
                    val phaseA = "aggro=${t.aggroTick} firstAttack=${t.firstAttackTick} attacks=${t.attacks} gaps=${t.attackGaps} anims=${t.anims} moves=${t.moves} worstIdleInReach=${t.worstIdleInReach} speed=${m.speed} reach=$reach"
                    // b) the player hits it: it must keep attacking
                    val attacksBefore = t.attacks
                    bot.attack(npc)
                    repeat(20) { wait(1); step() }
                    val keepsFighting = t.attacks > attacksBefore
                    bot.resetInteractions()
                    // c) player steps 10 tiles away (inside the leash): it follows and attacks again
                    val near = arena.filter { it.getDistance(npc.spawnTile) in 9..11 && !world.collision.isClipped(it) }.firstOrNull()
                    var follows = "n/a"
                    if (near != null) {
                        val a0 = t.attacks
                        bot.moveTo(near)
                        repeat(25) { wait(1); step() }
                        follows = "${t.attacks > a0} gap=${gap(npc, bot)}"
                    }
                    // d) player leaves the leash (30 tiles from the monster's spawn): it lets go and walks home
                    val far = Tile(npc.spawnTile.x + 30, npc.spawnTile.z, npc.spawnTile.height)
                    bot.moveTo(far)
                    repeat(30) { wait(1); step() }
                    val released = npc.getCombatTarget() !== bot
                    val homeGap = maxOf(kotlin.math.abs(npc.tile.x - npc.spawnTile.x), kotlin.math.abs(npc.tile.z - npc.spawnTile.z))
                    val verdict =
                        when {
                            m.id == 14470 -> "NO-DIRECT-ATTACK(bloat)"
                            t.firstAttackTick < 0 -> "BUG:never-attacked"
                            t.worstIdleInReach > m.speed + 1 -> "BUG:idle-in-reach"
                            !keepsFighting -> "BUG:stops-when-hit"
                            !released -> "BUG:no-leash"
                            homeGap > DeadmanBreach.LOCAL_SPAWN_RADIUS -> "BUG:not-home"
                            else -> "OK"
                        }
                    out("${m.id} ${m.name} size=$size $verdict | $phaseA | keepsFightingWhenHit=$keepsFighting follows10=$follows released30=$released homeGap=$homeGap")
                    // e) frozen: it may not step, and fights again once the freeze ends
                    // a reachable landing tile 4-6 tiles from the monster (flood-filled from the spawner, so never behind a wall)
                    val freezeSpot = arena.filter { a -> val g = gapTo(npc, a); g in 4..6 && !world.collision.isClipped(a) }.firstOrNull() ?: home
                    bot.moveTo(freezeSpot)
                    bot.attack(npc)
                    npc.freeze(8)
                    val frozenAt = npc.tile
                    repeat(6) { wait(1); step() }
                    val stayedFrozen = npc.tile == frozenAt
                    val a1 = t.attacks
                    repeat(20) { wait(1); step() }
                    val afterFreeze = t.attacks > a1
                    // f) death: the death sequence plays, the monster leaves the world, a test monster pays nothing
                    val deathAnims = npc.combatDef.deathAnimation
                    npc.hit(npc.getCurrentLifepoints() + 10)
                    var gone = false
                    repeat(15) {
                        wait(1)
                        if (!world.npcs.contains(npc)) gone = true
                    }
                    out("${m.id} ${m.name} freezeGap=${gapTo(npc, freezeSpot)} target=${npc.getCombatTarget() === bot} frozenStays=$stayedFrozen attacksAfterFreeze=$afterFreeze death=$deathAnims deathExists=${deathAnims.all { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, it) != null }} removedAfterDeath=$gone")
                    if (world.npcs.contains(npc)) world.remove(npc)
                    DeadmanBreach.pruneLive(world)
                }
                // ---- 3. blocked path: a standing npc between the monster and the player -------------------------------
                if (only == null || 14450 in only) {
                    DeadmanBreach.close(world)
                    val arenaSet = arena.toHashSet()
                    val start = arena.firstOrNull { h -> h.transform(6, 0) in arenaSet && h.transform(3, 0) in arenaSet && DeadmanBreach.fits(world, h.transform(6, 0), 2) }
                    val spot = start?.transform(6, 0)
                    val block = start?.transform(3, 0)
                    if (start != null) bot.moveTo(start)
                    wait(18)
                    if (spot != null && block != null) {
                        val blocker = gg.rsmod.game.model.entity.Npc(1, block, world).also { it.walkRadius = 0; it.respawnOverride = false }
                        world.spawn(blocker)
                        val goblin = DeadmanBreach.spawnMonster(world, 14450, spot, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
                        var attacked = -1
                        var last = NpcAttacks.lastDataAttackCycle(goblin)
                        for (tick in 1..30) {
                            wait(1)
                            bot.setCurrentLifepoints(LIFE)
                            val cyc = NpcAttacks.lastDataAttackCycle(goblin)
                            if (cyc != null && cyc != last && attacked < 0) attacked = tick
                            last = cyc
                            if (tick % 3 == 0) out("  trace blocked t=$tick goblin=${goblin.tile} bot=${bot.tile} man=${blocker.tile} target=${goblin.getCombatTarget() === bot} moving=${goblin.hasMoveDestination()} ${NpcAttacks.explainReach(goblin, bot)}")
                        }
                        out("blocked-path: Giant goblin with a man in between -> first attack tick=$attacked ${if (attacked > 0) "OK" else "BUG:blocked"}")
                        world.remove(goblin)
                        world.remove(blocker)
                    } else {
                        out("blocked-path: no straight arena line here")
                    }
                }
                // ---- 4. single-way: idle monsters must not all pile onto one player --------------------------------------
                if (singleSite != null && (only == null || 14438 in only)) {
                    val tiles = singleSite.landing
                    val a = tiles.first()
                    val b = tiles.firstOrNull { gapTo2(it, a) in 4..6 } ?: a
                    bot.moveTo(a)
                    val bot2 = bot(world, b)
                    wait(3)
                    val mons =
                        tiles.filter { gapTo2(it, a) in 3..5 && gapTo2(it, b) in 3..5 && DeadmanBreach.fits(world, it, 2) }.take(3).map {
                            DeadmanBreach.spawnMonster(world, 14450, it, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
                        }
                    repeat(20) { i ->
                        wait(1)
                        bot.setCurrentLifepoints(LIFE)
                        bot2.setCurrentLifepoints(LIFE)
                        out("  trace single t=$i targets=${mons.map { m -> when (m.getCombatTarget()) { bot -> "A"; bot2 -> "B"; null -> "-"; else -> "?" } }} A.lastHitBy=${(bot.attr[gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR]?.get() as? Npc)?.index} A.aggressor=${(bot.attr[gg.rsmod.game.model.attr.AGGRESSOR]?.get() as? Npc)?.index} mons=${mons.map { it.index }} underAttackA=${bot.timers.has(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER)}")
                    }
                    val onA = mons.count { it.getCombatTarget() === bot }
                    val onB = mons.count { it.getCombatTarget() === bot2 }
                    out("single-way: ${mons.size} goblins, multi=${a.isMulti(world)} on player A=$onA on player B=$onB ${if (onA <= 1 && onB <= 1) "OK" else "BUG:pile-up"}")
                    mons.forEach { if (world.npcs.contains(it)) world.remove(it) }
                    world.unregister(bot2)
                }
                // ---- 5. rewards: a real (non-test) breach monster killed by the player ------------------------------------
                run {
                    DeadmanBreach.close(world)
                    bot.moveTo(home)
                    wait(18)
                    val spot = arena.firstOrNull { gapTo2(it, home) in 2..4 && DeadmanBreach.fits(world, it, 1) } ?: home
                    val real = DeadmanBreach.spawnMonster(world, 14454, spot, DeadmanBreach.LOCAL_SPAWN_RADIUS) // Malevolent Mage, 1x1
                    val pointsBefore = BreachPoints.balance(bot)
                    DeadmanBreach.recordDamage(real, bot, 300)
                    real.hit(real.getCurrentLifepoints() + 10)
                    repeat(15) { wait(1); bot.setCurrentLifepoints(LIFE) }
                    val items = ArrayList<gg.rsmod.game.model.entity.GroundItem>()
                    for (dx in -2..2) for (dz in -2..2) {
                        val t = real.tile.transform(dx, dz)
                        world.chunks.get(t, createIfNeeded = false)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)?.let { items += it }
                    }
                    val mine = items.filter { it.isOwnedBy(bot) }
                    val emblem = mine.any { it.item == BreachIds.ARCHAIC_EMBLEM_TIER_5 }
                    val points = BreachPoints.balance(bot) - pointsBefore
                    out("rewards: real monster killed -> drops for player=${mine.size} emblem=$emblem points+=$points ${if (mine.isNotEmpty() && points > 0) "OK" else "BUG:no-rewards"}")
                    mine.forEach { world.remove(it) }
                }
            } catch (e: Exception) {
                logger.error("BREACHSIM failed: {}", e.toString(), e)
            } finally {
                bot?.let { world.unregister(it) }
                DeadmanBreach.close(world)
                log("done (${lines.size} lines)")
            }
        }
    }
}
