package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance
import gg.rsmod.plugins.content.mechanics.hud.ClientHudSync

/**
 * Information overlays (owner 2026-09-29: Boosts Information, Opponent Information, Ground Items, Timers & Buffs). The
 * client only draws what the server sends, so this suite checks the server side end to end: every `__RSPS_HUD__:` payload
 * a bot's client would receive is fed into a model of the client (exactly as RspsHud* decodes it - a countdown becomes
 * "received cycle + ticks"), and each tick the model is compared with the live server state it must mirror.
 *
 *   timers     Vengeance active / cooldown, Teleblock (full and shortened), freeze and its break by distance, death
 *   boosts     a real Saradomin brew: the predicted cycle of the next boost drop / stat restore vs the cycle the level
 *              really changes
 *   opponent   real attack on an npc, damage, heal, target switch, combat stop, npc death; a player target
 *   ground     spawn, stack, value (price checker), several drops of one item and a partial pickup of them, full pickup,
 *              natural despawn, private -> public, leaving and re-entering the area (the relog/teleport resend path)
 *
 * References: the server's own live state for "follows the server" (that is the property under test: the overlay must
 * show this server's mechanics), plus the OSRS Wiki values where the mechanic itself has one (Vengeance cooldown 30 s).
 */
object HudSuite : Conformance.Suite {
    /** The sync runs at the end of every cycle, so the client's countdowns must match the server exactly. */
    private const val JITTER = 0

    /** Client model of one bot, decoding payloads like the client does. */
    private class Model {
        var vengeance = 0
        var cooldownSent = 0
        var cooldownEnd = -1
        var teleblockEnd = -1
        var frozenEnd = -1
        var decayEnd = -1
        var restoreEnd = -1
        var opponent = "opp|none"
        var configs = 0
        val ground = HashMap<String, IntArray>()
        val groundReceived = HashMap<String, Int>()
        val groundRemovedAt = HashMap<String, Int>()

        fun apply(
            payload: String,
            cycle: Int,
        ) {
            val f = payload.split('|')
            fun end(ticks: String) = ticks.toInt().let { if (it <= 0) -1 else cycle + it }
            when (f[0]) {
                "tmr" -> {
                    vengeance = f[1].toInt()
                    cooldownSent = f[2].toInt()
                    cooldownEnd = end(f[2])
                    teleblockEnd = end(f[3])
                    frozenEnd = end(f[4])
                }
                "boost" -> {
                    decayEnd = end(f[1])
                    restoreEnd = end(f[2])
                }
                "opp" -> opponent = payload
                "cfg" -> configs++
                "gi" ->
                    payload.substring(3).split(';').forEach { entry ->
                        val e = entry.split(',')
                        val key = "${e[0]},${e[1]},${e[2]},${e[3]}"
                        groundReceived[key] = (groundReceived[key] ?: 0) + 1
                        val ticks = e[4].toInt()
                        if (ticks == -2) {
                            ground.remove(key)
                            groundRemovedAt[key] = cycle
                        } else {
                            ground[key] = intArrayOf(if (ticks < 0) -1 else cycle + ticks, e[5].toInt())
                        }
                    }
            }
        }

        fun left(
            end: Int,
            now: Int,
        ): Int = if (end < 0) 0 else maxOf(0, end - now)
    }

    /**
     * The cycle whose end state the client last received: payloads go out at the end of a cycle (Player.postCycle), and this
     * suite runs at the start of the next one, before that cycle's timers tick.
     */
    private fun seen(run: Conformance.Run) = run.world.currentCycle - 1

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val models = HashMap<Player, Model>()
        ClientHud.observer = { p, payload -> synchronized(models) { models.getOrPut(p) { Model() }.apply(payload, run.world.currentCycle) } }
        try {
            val arena = SummoningSuite.arenaFor(run, run.origin.transform(6, 0))
            run.note("arena ${arena.x},${arena.z},${arena.height}")
            val a = run.bot(arena, "U1")
            val b = run.bot(arena.transform(1, 0), "U2")
            wait(3)
            val ma = models.getOrPut(a) { Model() }
            val mb = models.getOrPut(b) { Model() }
            run.expectTrue("hud login", "settings pushed on login", ma.configs > 0, "cfg payloads ${ma.configs}")

            val only = args.joinToString(" ").lowercase()
            suspend fun scenario(
                name: String,
                block: suspend QueueTask.() -> Unit,
            ) {
                if (Conformance.stopRequested || (only.isNotEmpty() && !name.contains(only))) return
                val from = run.world.currentCycle
                run.progress("hud: $name")
                try {
                    block()
                } catch (e: Throwable) {
                    run.add(Result("hud $name", "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
                }
                for (p in listOf(a, b)) {
                    p.resetInteractions()
                    Combat.reset(p)
                    p.clearHits()
                    p.timers.remove(FROZEN_TIMER)
                    p.timers.remove(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER)
                    p.timers.remove(TELEBLOCK_TIMER)
                    p.setCurrentLifepoints(p.getMaximumLifepoints())
                }
                a.moveTo(arena)
                b.moveTo(arena.transform(1, 0))
                wait(2)
                run.window("hud $name", from)
            }
            scenario("vengeance") { vengeance(run, a, b, ma) }
            scenario("teleblock") { teleblock(run, a, ma) }
            scenario("freeze") { freeze(run, a, b, ma, arena) }
            scenario("relog") { relog(run, a, ma) }
            scenario("boosts") { boosts(run, a, ma) }
            scenario("opponent npc") { opponentNpc(run, a, ma, arena) }
            scenario("opponent player") { opponentPlayer(run, a, b, ma) }
            scenario("ground items") { ground(run, a, b, ma, mb, arena) }
            scenario("death") { death(run, a, ma) }
            run.notTested("hud client", "drawing, projection, colours, hidden/highlight matching", "client side - LIVE-AV in the client")
        } finally {
            ClientHud.observer = null
        }
    }

    private fun serverLeft(
        p: Player,
        key: TimerKey,
    ): Int = if (p.timers.has(key)) p.timers[key] else 0

    /** Follows the timers for [ticks] ticks; returns the largest model/server difference and any flag mismatch. */
    private suspend fun QueueTask.followTimers(
        run: Conformance.Run,
        p: Player,
        m: Model,
        ticks: Int,
        until: () -> Boolean = { false },
    ): Pair<Int, String?> {
        // This suite runs at the start of a cycle, after that cycle's player/npc queues (a death, a hit's effect) may already
        // have changed the state; the client gets that change at the end of the same cycle. So a difference only counts
        // when it is still there one tick later - i.e. the overlay must show every change within the cycle it happens in.
        var worst = 0
        var flag: String? = null
        var pending: String? = null
        var pendingDiff = 0
        repeat(ticks + 1) { tick ->
            wait(1)
            val now = run.world.currentCycle
            var mismatch: String? = null
            var diffNow = 0
            for ((name, end, key) in listOf(Triple("cooldown", m.cooldownEnd, VENGEANCE_COOLDOWN), Triple("teleblock", m.teleblockEnd, TELEBLOCK_TIMER), Triple("frozen", m.frozenEnd, FROZEN_TIMER))) {
                val diff = kotlin.math.abs(m.left(end, now - 1) - serverLeft(p, key))
                if (diff > JITTER && diff > diffNow) {
                    diffNow = diff
                    mismatch = "cycle $now $name shown ${m.left(end, now - 1)} server ${serverLeft(p, key)}"
                }
            }
            val active = if (Vengeance.isActive(p)) 1 else 0
            if (m.vengeance != active) mismatch = mismatch ?: "cycle $now vengeance shown ${m.vengeance} server $active"
            if (mismatch != null && pending != null) {
                worst = maxOf(worst, pendingDiff, diffNow)
                flag = flag ?: "$pending, still at $mismatch"
            }
            pending = mismatch
            pendingDiff = diffNow
            if (tick >= ticks || (until() && mismatch == null)) return worst to flag
        }
        return worst to flag
    }

    private fun report(
        run: Conformance.Run,
        entry: String,
        check: String,
        result: Pair<Int, String?>,
    ) = run.expectTrue(entry, check, result.first <= JITTER && result.second == null, "max difference ${result.first} ticks${result.second?.let { ", $it" } ?: ""}", "<= $JITTER tick")

    private suspend fun QueueTask.vengeance(
        run: Conformance.Run,
        a: Client,
        b: Client,
        m: Model,
    ) {
        val e = "hud vengeance"
        a.timers.remove(VENGEANCE_COOLDOWN)
        Vengeance.activate(a)
        wait(1)
        run.expectTrue(e, "ACTIVE shown after the cast", m.vengeance == 1, "shown ${m.vengeance}")
        // OSRS Wiki "Vengeance": 30 second cooldown = 50 ticks; the payload carries what the server set.
        // Sent at the end of the cast's own cycle, so the cast cycle itself is already over: +1 is the whole cooldown.
        run.check(e, "cooldown at the cast (ticks, incl. the cast cycle)", m.cooldownSent + 1, "osrs wiki 30 s" to 50, same = { x, y -> kotlin.math.abs(x.toString().toInt() - y.toString().toInt()) <= JITTER })
        report(run, e, "cooldown and state follow the server (primed)", followTimers(run, a, m, 5))
        Vengeance.onIncomingHit(b, a, 20)
        report(run, e, "proc clears ACTIVE, cooldown keeps running", followTimers(run, a, m, 5))
        run.expectTrue(e, "ACTIVE gone after the proc", m.vengeance == 0, "shown ${m.vengeance}")
        report(run, e, "cooldown runs out with the server", followTimers(run, a, m, 60) { !a.timers.has(VENGEANCE_COOLDOWN) })
        wait(1)
        run.expectTrue(e, "cooldown gone when the server's is", m.left(m.cooldownEnd, seen(run)) == 0, "shown ${m.left(m.cooldownEnd, seen(run))}")
    }

    private suspend fun QueueTask.teleblock(
        run: Conformance.Run,
        a: Client,
        m: Model,
    ) {
        val e = "hud teleblock"
        a.timers[TELEBLOCK_TIMER] = 500
        report(run, e, "full teleblock follows the server", followTimers(run, a, m, 8))
        // Shortened (MagicCombatStrategy: 250 ticks under Protect from Magic / Deflect Magic): a new value, resent at once.
        a.timers[TELEBLOCK_TIMER] = 250
        report(run, e, "shortened teleblock follows the server", followTimers(run, a, m, 5))
        a.timers.remove(TELEBLOCK_TIMER)
        report(run, e, "teleblock removed", followTimers(run, a, m, 3))
    }

    private suspend fun QueueTask.freeze(
        run: Conformance.Run,
        a: Client,
        b: Client,
        m: Model,
        arena: Tile,
    ) {
        val e = "hud freeze"
        a.freeze(30, b)
        report(run, e, "freeze follows the server", followTimers(run, a, m, 6))
        // FreezeLeash: a player's freeze on a player breaks once they are more than 10 tiles apart.
        b.moveTo(arena.transform(14, 0))
        report(run, e, "freeze broken by distance follows the server", followTimers(run, a, m, 12) { !a.timers.has(FROZEN_TIMER) })
        wait(1)
        run.expectTrue(e, "frozen gone when the server's is", m.left(m.frozenEnd, seen(run)) == serverLeft(a, FROZEN_TIMER), "shown ${m.left(m.frozenEnd, seen(run))} server ${serverLeft(a, FROZEN_TIMER)}")
    }

    private suspend fun QueueTask.relog(
        run: Conformance.Run,
        a: Client,
        m: Model,
    ) {
        val e = "hud relog"
        a.timers[TELEBLOCK_TIMER] = 300
        wait(2)
        // A new session: the client starts empty (RspsHud.reset) and on_login resets the sync state.
        m.teleblockEnd = -1
        m.vengeance = 0
        m.cooldownEnd = -1
        m.frozenEnd = -1
        m.opponent = "stale"
        ClientHudSync.reset(a)
        report(run, e, "everything resent after a relog", followTimers(run, a, m, 3))
        run.expectTrue(e, "opponent state resent after a relog", m.opponent != "stale", "shown ${m.opponent}")
    }

    private suspend fun QueueTask.boosts(
        run: Conformance.Run,
        a: Client,
        m: Model,
    ) {
        val e = "hud boosts"
        for (s in listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)) a.skills.setCurrentLevel(s, 99)
        a.inventory.removeAll()
        a.inventory.add(Items.SARADOMIN_BREW_4, 1)
        wait(1)
        val drank = Bots.itemOption(a, a.inventory.getItemIndex(Items.SARADOMIN_BREW_4, false), "Drink")
        wait(3)
        val defence = a.skills.getCurrentLevel(Skills.DEFENCE)
        val attack = a.skills.getCurrentLevel(Skills.ATTACK)
        if (!drank || defence <= 99 || attack >= 99) {
            run.add(Result(e, "setup: Saradomin brew boosts Defence and drains Attack", Verdict.FAIL, "defence > 99, attack < 99", "drank=$drank defence=$defence attack=$attack"))
            return
        }
        // The first real change of each: the cycle the overlay predicted (at the moment of the change) vs the real one.
        var decayPredicted = -1
        var restorePredicted = -1
        var decayActual = -1
        var restoreActual = -1
        var guard = 0
        while ((decayActual < 0 || restoreActual < 0) && guard++ < 130) {
            val predictedDecay = m.decayEnd
            val predictedRestore = m.restoreEnd
            wait(1)
            val now = run.world.currentCycle
            // A change seen now happened during the previous cycle (this suite runs at the start of a cycle).
            if (decayActual < 0 && a.skills.getCurrentLevel(Skills.DEFENCE) < defence) {
                decayActual = now - 1
                decayPredicted = predictedDecay
            }
            if (restoreActual < 0 && a.skills.getCurrentLevel(Skills.ATTACK) > attack) {
                restoreActual = now - 1
                restorePredicted = predictedRestore
            }
        }
        run.expectTrue(e, "boost drop comes when the countdown ends", decayActual > 0 && kotlin.math.abs(decayActual - decayPredicted) <= JITTER, "predicted $decayPredicted, dropped $decayActual", "<= $JITTER tick")
        run.expectTrue(e, "stat restore comes when the countdown ends", restoreActual > 0 && kotlin.math.abs(restoreActual - restorePredicted) <= JITTER, "predicted $restorePredicted, restored $restoreActual", "<= $JITTER tick")
        wait(2)
        val now = run.world.currentCycle
        // Still boosted after the first drop: the next drop is one full interval (100 ticks, 115 with Berserker) later.
        run.expectTrue(e, "next countdown sent after the change", m.decayEnd >= decayActual + 99 || a.skills.getCurrentLevel(Skills.DEFENCE) <= 99, "decay end ${m.decayEnd}, first drop $decayActual, now $now")
        for (s in listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)) a.skills.setCurrentLevel(s, 99)
        wait(2)
        run.expectTrue(e, "no countdown once every stat is back", m.decayEnd < 0 && m.restoreEnd < 0, "decay ${m.decayEnd} restore ${m.restoreEnd}")
    }

    /** Model vs the payload the server state gives now (or one tick ago: the change is sent on the next sync tick). */
    private suspend fun QueueTask.followOpponent(
        run: Conformance.Run,
        a: Client,
        m: Model,
        ticks: Int,
    ): String? {
        // As in followTimers: a difference must be gone one tick later (changes made by this cycle's queues reach the client
        // at the end of the cycle).
        var bad: String? = null
        var pending: String? = null
        repeat(ticks + 1) {
            wait(1)
            val now = ClientHudSync.opponentPayload(a)
            val mismatch = if (m.opponent != now) "cycle ${run.world.currentCycle} shown '${m.opponent}' server '$now'" else null
            if (mismatch != null && pending != null && bad == null) bad = "$pending, still $mismatch"
            pending = mismatch
        }
        return bad
    }

    private suspend fun QueueTask.opponentNpc(
        run: Conformance.Run,
        a: Client,
        m: Model,
        arena: Tile,
    ) {
        val e = "hud opponent npc"
        // Hill giants (not the 7-hp man): the fight must last long enough to see damage, a heal and a switch.
        val n1 = run.npc(gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT, arena.transform(0, 2), "UN1")
        val n2 = run.npc(gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT, arena.transform(2, 2), "UN2")
        val strength = a.skills.getCurrentLevel(Skills.STRENGTH)
        a.skills.setCurrentLevel(Skills.STRENGTH, 1)
        wait(1)
        a.attack(n1)
        val seen = HashSet<String>()
        var bad: String? = null
        repeat(12) {
            bad = bad ?: followOpponent(run, a, m, 1)
            if (m.opponent.startsWith("opp|n|${n1.index}|")) seen += m.opponent
        }
        run.expectTrue(e, "target, damage and health follow the server", bad == null && seen.size >= 2 && m.opponent.startsWith("opp|n|${n1.index}|"),
            bad ?: "health states shown ${seen.size}: ${seen.joinToString(" ")}")
        n1.setCurrentLifepoints(n1.getMaximumLifepoints())
        bad = followOpponent(run, a, m, 2)
        run.expectTrue(e, "healing shows at once", bad == null && m.opponent.endsWith("|${n1.getMaximumLifepoints()}|${n1.getMaximumLifepoints()}"), bad ?: m.opponent)
        a.attack(n2)
        bad = followOpponent(run, a, m, 3)
        run.expectTrue(e, "target switch", bad == null && m.opponent.startsWith("opp|n|${n2.index}|"), bad ?: m.opponent)
        // Combat really stops only when nobody keeps fighting: the first giant goes, the second stops, and the bot's auto
        // retaliate is off (it would otherwise - correctly - start a new fight and a new opponent).
        run.remove(n1)
        a.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        Combat.reset(n2)
        n2.resetInteractions()
        Combat.reset(a)
        a.resetInteractions()
        a.resetFacePawn()
        bad = followOpponent(run, a, m, 2)
        run.expectTrue(e, "gone when combat stops", bad == null && m.opponent == "opp|none", bad ?: m.opponent)
        a.attack(n2)
        wait(2)
        n2.hit(damage = n2.getCurrentLifepoints() + 10)
        bad = followOpponent(run, a, m, 12)
        run.expectTrue(e, "gone after the npc died", bad == null && m.opponent == "opp|none", bad ?: m.opponent)
        a.skills.setCurrentLevel(Skills.STRENGTH, strength)
        a.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
        run.remove(n2)
    }

    private suspend fun QueueTask.opponentPlayer(
        run: Conformance.Run,
        a: Client,
        b: Client,
        m: Model,
    ) {
        val e = "hud opponent player"
        a.attack(b)
        wait(2)
        if (a.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() !== b) {
            run.notTested(e, "player target", "the server refused the attack at the arena (safe area?)")
            return
        }
        b.setCurrentLifepoints(b.getMaximumLifepoints() / 2)
        val bad = followOpponent(run, a, m, 4)
        val ratio = b.getCurrentLifepoints() * 255 / b.getMaximumLifepoints()
        run.expectTrue(e, "player target with the hit-mask ratio, no exact hp", bad == null && m.opponent == "opp|p|${b.index}|$ratio", bad ?: m.opponent)
    }

    private fun key(
        tile: Tile,
        id: Int,
    ) = "${tile.height},${tile.x},${tile.z},$id"

    private suspend fun QueueTask.ground(
        run: Conformance.Run,
        a: Client,
        b: Client,
        ma: Model,
        mb: Model,
        arena: Tile,
    ) {
        val e = "hud ground items"
        val world = run.world
        val delay = world.gameContext.gItemDespawnDelay
        val t = arena.transform(2, 1)
        val spawned = ArrayList<GroundItem>()
        fun spawn(item: GroundItem): GroundItem {
            world.spawn(item)
            spawned += item
            return item
        }
        fun left(m: Model, k: String) = m.ground[k]?.let { if (it[0] < 0) -1 else it[0] - seen(run) }
        // World.cycle counts ground items after the cycle's flush, so the client gets the removal one cycle after the counter
        // reaches the delay: the time the client must show is the counter's remaining time + 1.
        fun server(item: GroundItem) = delay - item.currentCycle + 1

        // Spawn: despawn time and value.
        val whip = spawn(GroundItem(Items.ABYSSAL_WHIP, 1, t))
        wait(2)
        val wk = key(t, Items.ABYSSAL_WHIP)
        run.check(e, "whip value (price checker)", ma.ground[wk]?.get(1), "price checker" to PriceChecker.value(a, Item(Items.ABYSSAL_WHIP)))
        run.expectTrue(e, "whip despawn time follows the server", left(ma, wk)?.let { kotlin.math.abs(it - server(whip)) <= JITTER } == true, "shown ${left(ma, wk)} server ${server(whip)}")

        // A stackable item: a second drop grows the stack (OBJ_COUNT) and keeps its entry.
        spawn(GroundItem(Items.COINS_995, 100, t))
        wait(1)
        val ck = key(t, Items.COINS_995)
        val coinsFirst = ma.groundReceived[ck] ?: 0
        world.spawn(GroundItem(Items.COINS_995, 50, t))
        wait(1)
        run.expectTrue(e, "stack increase resent", (ma.groundReceived[ck] ?: 0) > coinsFirst && ma.ground.containsKey(ck), "entries received ${ma.groundReceived[ck]}")

        // Two more whips later: the tile's entry is the soonest despawn; picking up the oldest moves it on.
        wait(3)
        val whip2 = spawn(GroundItem(Items.ABYSSAL_WHIP, 1, t))
        wait(2)
        run.expectTrue(e, "several drops: soonest despawn shown", left(ma, wk)?.let { kotlin.math.abs(it - server(whip)) <= JITTER } == true, "shown ${left(ma, wk)} oldest ${server(whip)}")
        world.remove(whip)
        wait(1)
        run.expectTrue(e, "partial pickup (one of the drops): next despawn shown", left(ma, wk)?.let { kotlin.math.abs(it - server(whip2)) <= JITTER } == true, "shown ${left(ma, wk)} remaining ${server(whip2)}")
        world.remove(whip2)
        wait(1)
        run.expectTrue(e, "full pickup removes the entry", !ma.ground.containsKey(wk), "entry ${ma.ground[wk]?.toList()}")

        // Natural despawn.
        val tb = arena.transform(3, 1)
        val bones = GroundItem(Items.DRAGON_BONES, 1, tb).also { it.currentCycle = delay - 4 }
        spawn(bones)
        wait(1)
        val bk = key(tb, Items.DRAGON_BONES)
        run.expectTrue(e, "near-despawn item shows its short timer", left(ma, bk)?.let { it in 1..4 } == true, "shown ${left(ma, bk)}")
        val predicted = ma.ground[bk]?.get(0) ?: -1
        wait(6)
        run.expectTrue(e, "despawn removes the entry", !world.isSpawned(bones) && !ma.ground.containsKey(bk), "spawned=${world.isSpawned(bones)} entry=${ma.ground[bk]?.toList()}")
        run.expectTrue(e, "despawn arrives when the countdown ends", ma.groundRemovedAt[bk] == predicted, "predicted cycle $predicted, removal received ${ma.groundRemovedAt[bk]}", "same cycle")

        // Private drop: only the owner sees it; once public, the other player gets it with the remaining time.
        val tp = arena.transform(4, 1)
        val own = GroundItem(Items.ABYSSAL_WHIP, 1, tp, a).also { it.currentCycle = world.gameContext.gItemPublicDelay - 3 }
        spawn(own)
        wait(1)
        val pk = key(tp, Items.ABYSSAL_WHIP)
        run.expectTrue(e, "private drop: owner only", ma.ground.containsKey(pk) && !mb.ground.containsKey(pk), "owner=${ma.ground.containsKey(pk)} other=${mb.ground.containsKey(pk)}")
        wait(5)
        run.expectTrue(e, "public: the other player gets it with the remaining time",
            left(mb, pk)?.let { kotlin.math.abs(it - server(own)) <= JITTER } == true, "shown ${left(mb, pk)} server ${server(own)}")

        // Leaving the area and coming back (teleport / region change / relog all enter through the zone resend).
        val before = ma.groundReceived[ck] ?: 0
        a.moveTo(arena.transform(0, 160))
        wait(4)
        a.moveTo(arena)
        wait(4)
        run.expectTrue(e, "re-entering the area resends every stack", (ma.groundReceived[ck] ?: 0) > before && ma.ground.containsKey(ck), "coins entries before $before after ${ma.groundReceived[ck]}")

        spawned.forEach { if (world.isSpawned(it)) world.remove(it) }
        wait(1)
    }

    private suspend fun QueueTask.death(
        run: Conformance.Run,
        a: Client,
        m: Model,
    ) {
        val e = "hud death"
        a.timers.remove(VENGEANCE_COOLDOWN)
        a.attr.remove(gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR)
        a.timers[TELEBLOCK_TIMER] = 500
        a.timers[VENGEANCE_COOLDOWN] = 50
        wait(2)
        a.hit(damage = a.getCurrentLifepoints() + 50)
        val result = followTimers(run, a, m, 25) { !a.timers.has(TELEBLOCK_TIMER) }
        wait(2)
        report(run, e, "death clears what the server clears", result)
        run.expectTrue(e, "teleblock gone after death", m.left(m.teleblockEnd, seen(run)) == 0 && !a.timers.has(TELEBLOCK_TIMER),
            "shown ${m.left(m.teleblockEnd, seen(run))} server ${serverLeft(a, TELEBLOCK_TIMER)}")
    }
}
