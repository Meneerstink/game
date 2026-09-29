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
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance
import gg.rsmod.plugins.content.mechanics.hud.ClientHudConfig
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

    /** Payloads / characters the observer saw (all players), for the load scenario. */
    @Volatile
    private var sentPayloads = 0L

    @Volatile
    private var sentChars = 0L

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
        val config = HashMap<String, String>()
        var nonCombatEnd = -1
        var opened = 0
        val lists = HashMap<String, MutableList<String>>()
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
                    nonCombatEnd = end(f.getOrElse(3) { "-1" })
                }
                "opp" -> opponent = payload
                "cfg" -> {
                    configs++
                    payload.substring(4).split(';').forEach { kv ->
                        val eq = kv.indexOf('=')
                        if (eq > 0) config[kv.substring(0, eq)] = kv.substring(eq + 1)
                    }
                }
                "open" -> opened++
                "hl", "hide" ->
                    if (f[1] == "clear") {
                        lists[f[0]] = ArrayList()
                    } else if (f[1] == "add") {
                        lists.getOrPut(f[0]) { ArrayList() }.addAll(f[2].split(','))
                    }
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
        ClientHud.observer = { p, payload ->
            synchronized(models) { models.getOrPut(p) { Model() }.apply(payload, run.world.currentCycle) }
            sentPayloads++
            sentChars += payload.length + ClientHud.PREFIX.length
        }
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
            scenario("tour restores") { tourRestores(run, a, ma, arena) }
            scenario("settings panel") { settingsPanel(run, a, ma) }
            scenario("load 100 players") { load(run, arena, models) }
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
        // RuneLite's "next change" ALWAYS mode shows the cycle with nothing boosted: the three cycles keep coming.
        run.expectTrue(e, "cycles keep coming with every stat back (ALWAYS mode)", m.decayEnd > 0 && m.restoreEnd > 0 && m.nonCombatEnd > 0,
            "decay ${m.decayEnd} restore ${m.restoreEnd} non-combat ${m.nonCombatEnd}")
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
        // RuneLite OpponentInfoPlugin: the last opponent stays WAIT = 5 s after the interaction ended, then goes.
        val hold = ClientHudSync.holdCycles(a)
        val stoppedAt = run.world.currentCycle
        var goneAt = -1
        var guard = 0
        while (goneAt < 0 && guard++ < hold + 10) {
            wait(1)
            if (m.opponent == "opp|none") goneAt = run.world.currentCycle
        }
        val shownFor = goneAt - stoppedAt
        run.check(e, "still shown after combat stops (cycles)", shownFor, "runelite WAIT 5 s" to hold, same = { x, y -> kotlin.math.abs(x.toString().toInt() - y.toString().toInt()) <= 2 })
        a.attack(n2)
        wait(2)
        n2.hit(damage = n2.getCurrentLifepoints() + 10)
        var deadShown = false
        goneAt = -1
        guard = 0
        val killedAt = run.world.currentCycle
        while (goneAt < 0 && guard++ < hold + 20) {
            wait(1)
            if (m.opponent.startsWith("opp|n|${n2.index}|0|0|")) deadShown = true
            if (m.opponent == "opp|none") goneAt = run.world.currentCycle
        }
        run.expectTrue(e, "the killed npc shows 0 hp, then goes after the hold", deadShown && goneAt > 0 && goneAt - killedAt <= hold + 12,
            "dead shown $deadShown, gone after ${goneAt - killedAt} cycles (hold $hold + death)")
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

    /**
     * `hudtour` works on the admin's own character (the owner's), so it must put everything back: levels, life points,
     * Vengeance, Teleblock, freeze, position, and remove its monster and ground items. Also checks it really produced the
     * eight screenshot requests.
     */
    private suspend fun QueueTask.tourRestores(
        run: Conformance.Run,
        a: Client,
        m: Model,
        arena: Tile,
    ) {
        val e = "hud tour"
        val tour = gg.rsmod.game.CommandServerCommands.handlers["hudtour"]
        if (tour == null) {
            run.add(Result(e, "hudtour registered on the command port", Verdict.FAIL, "handler", "missing"))
            return
        }
        a.skills.setCurrentLevel(Skills.ATTACK, 95)
        a.timers[TELEBLOCK_TIMER] = 123
        a.timers.remove(VENGEANCE_COOLDOWN)
        a.attr.remove(gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR)
        val life = a.getCurrentLifepoints()
        val tile = a.tile
        a.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
        val npcsBefore = run.world.npcs.count()
        val shots = ArrayList<String>()
        val shotsFrom = run.world.currentCycle
        val reply = tour(run.world, listOf(a.username))
        var guard = 0
        while (guard++ < 120 && !(a.tile.sameAs(tile) && run.recording.of("U1", "msg").any { it.cycle >= shotsFrom && it.text.contains("HUD tour done") })) wait(1)
        wait(2)
        run.recording.of("U1", "msg").filter { it.cycle >= shotsFrom && (it.text.contains("__RSPS_SHOT__:hud_") || it.text.contains("__RSPS_SHOT__:window_hud_")) }.forEach { shots += it.text.substringAfter("__RSPS_SHOT__:").substringBefore(")") }
        run.expectTrue(e, "started", reply.startsWith("HUD tour started"), reply)
        run.expectTrue(e, "twelve screenshots requested", shots.size == 12, shots.joinToString(" "))
        run.expectTrue(e, "position back", a.tile.sameAs(tile), "at ${a.tile}, was $tile")
        run.expectTrue(e, "levels back", a.skills.getCurrentLevel(Skills.ATTACK) == 95 && a.skills.getCurrentLevel(Skills.DEFENCE) == 99, "attack ${a.skills.getCurrentLevel(Skills.ATTACK)} defence ${a.skills.getCurrentLevel(Skills.DEFENCE)}")
        run.expectTrue(e, "life points back", a.getCurrentLifepoints() == life, "${a.getCurrentLifepoints()} was $life")
        run.expectTrue(e, "own teleblock back, no tour freeze/vengeance", serverLeft(a, TELEBLOCK_TIMER) in 1..(123 - (run.world.currentCycle - shotsFrom) + 1) && !a.timers.has(FROZEN_TIMER) &&
            !a.timers.has(VENGEANCE_COOLDOWN) && !Vengeance.isActive(a), "teleblock ${serverLeft(a, TELEBLOCK_TIMER)} frozen ${serverLeft(a, FROZEN_TIMER)} cd ${serverLeft(a, VENGEANCE_COOLDOWN)}")
        run.expectTrue(e, "auto retaliate back as it was", a.getVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP) == 0,
            "varp ${a.getVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP)}")
        // The tour's hill giant stands next to its spot (3222,3218); the world's own npc count moves by itself, so look there.
        val tourSpot = Tile(3222, 3218, 0)
        var giantsLeft = 0
        run.world.npcs.forEach { n -> if (n.id == gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT && n.tile.isWithinRadius(tourSpot, 4)) giantsLeft++ }
        run.expectTrue(e, "monster removed", giantsLeft == 0, "$giantsLeft hill giants left at the tour spot (world npcs $npcsBefore -> ${run.world.npcs.count()})")
        // The tour drops its items within 2 tiles of its spot (3222,3218): none may be left in the world.
        val spot = Tile(3222, 3218, 0)
        val left = ArrayList<String>()
        for (dx in -3..3) for (dz in -3..3) {
            val t = spot.transform(dx, dz)
            run.world.chunks.get(t)?.getEntities<GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)?.forEach { left += "${it.item}@${t.x},${t.z}" }
        }
        run.expectTrue(e, "ground items removed", left.isEmpty(), left.joinToString(" ").ifEmpty { "none left" })
        a.timers.remove(TELEBLOCK_TIMER)
        a.skills.setCurrentLevel(Skills.ATTACK, 99)
    }

    /**
     * The 78 Plugins panel's server side: every click arrives as a quiet `hud ...` command that must be saved with the
     * account and pushed back; values and list entries go through the game's own input box (answered here with the same
     * resume packets the client sends).
     */
    private suspend fun QueueTask.settingsPanel(
        run: Conformance.Run,
        a: Client,
        m: Model,
    ) {
        val e = "hud settings"
        fun hud(vararg args: String) = run.world.plugins.executeCommand(a, "hud", arrayOf(*args))
        hud("reset")
        wait(1)
        run.expectTrue(e, "every RuneLite setting pushed with its default", ClientHudConfig.SETTINGS.all { m.config[it.key] == it.default },
            "missing or different: " + ClientHudConfig.SETTINGS.filter { m.config[it.key] != it.default }.joinToString(" ") { "${it.key}=${m.config[it.key]}" })
        val opens = m.opened
        run.world.plugins.executeCommand(a, "hud", null)
        run.world.plugins.executeCommand(a, "plugins", null)
        wait(1)
        run.expectTrue(e, "hud / plugins open the sidebar", m.opened == opens + 2, "open payloads ${m.opened - opens}")

        // One value of every type, saved and pushed back; wrong values change nothing.
        val cases =
            listOf(
                "boosts.enabled" to "false", "timers.showTeleblock" to "false", "grounditems.priceDisplayMode" to "BOTH",
                "grounditems.groundItemTimers" to "PIE", "grounditems.lowValuePrice" to "5000", "grounditems.highValueColor" to "00ff00",
                "opponent.hitpointsDisplayStyle" to "BOTH", "boosts.boostThreshold" to "3", "grounditems.hotkey" to "SHIFT",
            )
        for ((key, value) in cases) {
            hud("set", key, value)
            wait(1)
            run.expectTrue(e, "$key = $value saved and pushed", ClientHudConfig.get(a, key) == value && m.config[key] == value, "saved ${ClientHudConfig.get(a, key)} pushed ${m.config[key]}")
        }
        hud("set", "grounditems.priceDisplayMode", "banana")
        hud("set", "boosts.boostThreshold", "500")
        hud("set", "grounditems.lowValueColor", "zz00zz")
        hud("set", "no.such.key", "1")
        wait(1)
        run.expectTrue(e, "wrong values change nothing", ClientHudConfig.get(a, "grounditems.priceDisplayMode") == "BOTH" &&
            ClientHudConfig.get(a, "boosts.boostThreshold") == "3" && ClientHudConfig.get(a, "grounditems.lowValueColor") == "66b2ff",
            "price ${ClientHudConfig.get(a, "grounditems.priceDisplayMode")} threshold ${ClientHudConfig.get(a, "boosts.boostThreshold")}")
        hud("set", "grounditems.pricedisplaymode", "ha")
        wait(1)
        run.expectTrue(e, "keys and values are case-insensitive", ClientHudConfig.get(a, "grounditems.priceDisplayMode") == "HA", ClientHudConfig.get(a, "grounditems.priceDisplayMode"))

        // Lists.
        hud("list", "highlight", "add", "Dragon*")
        hud("list", "highlight", "add", "Shark")
        wait(1)
        run.expectTrue(e, "two highlighted items added", m.lists["hl"] == listOf("dragon*", "shark"), "pushed ${m.lists["hl"]}")
        hud("list", "highlight", "remove", "dragon*")
        wait(1)
        run.expectTrue(e, "remove one", m.lists["hl"] == listOf("shark"), "pushed ${m.lists["hl"]}")
        hud("list", "hide", "add", "shark")
        wait(1)
        run.expectTrue(e, "an item is highlighted or hidden, never both", m.lists["hide"] == listOf("shark") && m.lists["hl"].isNullOrEmpty(), "hl ${m.lists["hl"]} hide ${m.lists["hide"]}")
        hud("list", "hide", "add", "<col=ff0000>bad;name")
        wait(1)
        run.expectTrue(e, "markup and separators are stripped from names", m.lists["hide"]?.contains("colff0000badname") == true, "hide ${m.lists["hide"]}")
        hud("list", "hide", "clear")
        wait(1)
        run.expectTrue(e, "clear empties the list", m.lists["hide"].isNullOrEmpty(), "pushed ${m.lists["hide"]}")

        // Reset one plugin, then everything.
        hud("reset", "grounditems")
        wait(1)
        run.expectTrue(e, "reset one plugin", ClientHudConfig.get(a, "grounditems.lowValuePrice") == "20000" && ClientHudConfig.get(a, "boosts.enabled") == "false",
            "ground low ${ClientHudConfig.get(a, "grounditems.lowValuePrice")} boosts.enabled ${ClientHudConfig.get(a, "boosts.enabled")}")
        hud("reset")
        wait(1)
        run.expectTrue(e, "reset everything", ClientHudConfig.SETTINGS.all { ClientHudConfig.get(a, it.key) == it.default && m.config[it.key] == it.default }, "after reset")
        hud("boosts", "off")
        wait(1)
        run.expectTrue(e, "typed shortcut: hud boosts off", m.config["boosts.enabled"] == "false", "pushed ${m.config["boosts.enabled"]}")
        hud("reset")
        wait(1)
    }

    /**
     * Owner 2026-09-29: "er gaan 100 players deze plugins gebruiken ... zeker met summoning". 100 bots, each with a real
     * familiar (summoned through the pouch's own "Summon" option), fighting 50 hill giants two by two, with a Vengeance,
     * a Teleblock, a freeze and boosted / drained stats, among 200 ground items and a 28-item death pile. Measured every
     * tick: the sync's own server time (Player.postCycle), payloads and characters per player; checked at the end for
     * every bot: timers, opponent (never its own familiar) and every ground stack in view.
     */
    private suspend fun QueueTask.load(
        run: Conformance.Run,
        arena: Tile,
        models: HashMap<Player, Model>,
    ) {
        val e = "hud load 100"
        val world = run.world
        val count = 100
        val centre = openField(run, arena.transform(0, 30), 13) ?: arena.transform(0, 30)
        run.note("load field centre ${centre.x},${centre.z}")
        val bots = ArrayList<Client>()
        for (i in 0 until count) bots += run.bot(centre.transform(-10 + (i % 10) * 2, -10 + (i / 10) * 2), "L%02d".format(i))
        wait(3)

        // Real familiars: a pouch and its "Summon" option.
        val pouches = listOf(Items.SPIRIT_WOLF_POUCH, Items.WAR_TORTOISE_POUCH, Items.STEEL_TITAN_POUCH, Items.PACK_YAK_POUCH)
        bots.forEachIndexed { i, b ->
            b.inventory.removeAll()
            b.inventory.add(pouches[i % pouches.size], 1)
            Bots.itemOption(b, b.inventory.getItemIndex(pouches[i % pouches.size], false), "Summon")
        }
        wait(4)
        val familiars = bots.count { it.attr[gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR]?.get() != null }
        run.expectTrue(e, "every bot has a familiar out", familiars == count, "$familiars / $count")

        // Two bots per hill giant, with fists at strength 1 so the giants live through the measurement.
        val giants = ArrayList<Npc>()
        for (i in 0 until count / 2) giants += run.npc(gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT, centre.transform(-10 + (i % 10) * 2, -9 + (i / 10) * 4), "LG$i")
        wait(1)
        bots.forEachIndexed { i, b ->
            b.skills.setCurrentLevel(Skills.STRENGTH, 1)
            b.skills.setCurrentLevel(Skills.ATTACK, 118)
            b.skills.setCurrentLevel(Skills.DEFENCE, 80)
            b.timers.remove(VENGEANCE_COOLDOWN)
            Vengeance.activate(b)
            b.timers[TELEBLOCK_TIMER] = 500
            b.timers[FROZEN_TIMER] = 60
            b.attack(giants[i / 2])
            // Flower poker / escape seeds: planted while frozen they move the planter one tile (flower_seeds.plugin.kts).
            b.inventory.add(Items.MITHRIL_SEEDS, 2)
            b.inventory.add(Items.ADAMANT_SEEDS, 2)
        }
        var planted = 0
        fun keepAlive() = giants.forEach { if (world.npcs.contains(it)) it.setCurrentLifepoints(it.getMaximumLifepoints()) }

        // 200 ground items spread over the area, stackable and not.
        val items = ArrayList<GroundItem>()
        val kinds = listOf(Items.ABYSSAL_WHIP, Items.COINS_995, Items.SHARK, Items.DRAGON_BONES, Items.DRAGONFIRE_SHIELD)
        for (i in 0 until 200) {
            val item = GroundItem(kinds[i % kinds.size], if (kinds[i % kinds.size] == Items.COINS_995) 1000 + i else 1, centre.transform(-12 + (i % 25), -12 + (i / 25) * 3))
            world.spawn(item)
            items += item
        }

        val perCycleNanos = ArrayList<Long>()
        val perCycleChars = ArrayList<Long>()
        var pileCycleNanos = 0L
        var pilePayloads = 0L
        val pileTile = centre.transform(1, 1)
        var charsBefore = sentChars
        repeat(60) { tick ->
            keepAlive()
            if (tick in 10..13) {
                // Every bot plants a seed (real "Plant" option), mithril and adamant in turn, one per tick.
                val seed = if (tick % 2 == 0) Items.MITHRIL_SEEDS else Items.ADAMANT_SEEDS
                bots.forEach { b -> Bots.itemOption(b, b.inventory.getItemIndex(seed, false), "Plant") }
            }
            if (tick == 30) {
                // A death pile: 28 different items land on one tile in one cycle.
                val payloadsBefore = sentPayloads
                val pile = listOf(Items.ABYSSAL_WHIP, Items.DRAGONFIRE_SHIELD, Items.SHARK, Items.DRAGON_BONES, Items.COINS_995, Items.THIRDAGE_PLATEBODY, Items.DRAGON_DAGGER_P_5698)
                for (j in 0 until 28) {
                    val item = GroundItem(pile[j % pile.size], 1, pileTile)
                    world.spawn(item)
                    items += item
                }
                wait(1)
                pileCycleNanos = ClientHud.cycleNanos
                pilePayloads = sentPayloads - payloadsBefore
                charsBefore = sentChars
                return@repeat
            }
            wait(1)
            // This suite runs at the start of a cycle: ClientHud's totals are the previous (complete) cycle's.
            if (tick > 5 && tick != 31) {
                perCycleNanos += ClientHud.cycleNanos
                perCycleChars += sentChars - charsBefore
            }
            charsBefore = sentChars
        }
        val avgMs = perCycleNanos.average() / 1_000_000.0
        val maxMs = (perCycleNanos.maxOrNull() ?: 0L) / 1_000_000.0
        val charsPerPlayer = perCycleChars.average() / count
        // The plant packets are handled on the next cycle: count what left the inventories.
        planted = bots.sumOf { 4 - it.inventory.getItemCount(Items.MITHRIL_SEEDS) - it.inventory.getItemCount(Items.ADAMANT_SEEDS) }
        run.expectTrue(e, "seeds planted during the fight (while frozen)", planted > count, "$planted seeds planted by $count frozen bots", "> $count")
        run.note("load: players=${world.players.count()} npcs=${world.npcs.count()} sync avg %.3f ms max %.3f ms per cycle, %.1f chars/player/cycle, pile cycle %.3f ms %d payloads".format(
            java.util.Locale.ENGLISH, avgMs, maxMs, charsPerPlayer, pileCycleNanos / 1_000_000.0, pilePayloads))
        run.expectTrue(e, "sync time per cycle, 100 players fighting with familiars", avgMs < 5.0 && maxMs < ClientHud.SLOW_CYCLE_NANOS / 1_000_000.0,
            "avg %.3f ms, max %.3f ms".format(java.util.Locale.ENGLISH, avgMs, maxMs), "avg < 5 ms, max < 20 ms")
        run.expectTrue(e, "steady traffic per player", charsPerPlayer < 120.0, "%.1f chars per player per cycle".format(java.util.Locale.ENGLISH, charsPerPlayer), "< 120 chars (a few small updates)")
        run.expectTrue(e, "28-item death pile seen by 100 players", pilePayloads <= count * 8L && pileCycleNanos < ClientHud.SLOW_CYCLE_NANOS,
            "$pilePayloads payloads, %.3f ms".format(java.util.Locale.ENGLISH, pileCycleNanos / 1_000_000.0), "<= 8 payloads per player, < 20 ms")

        // Correctness for every bot.
        var timerErrors = 0
        var opponentErrors = 0
        var ownFamiliar = 0
        var groundErrors = 0
        val now = world.currentCycle
        val firstErrors = ArrayList<String>()
        for (b in bots) {
            val m = models[b] ?: Model()
            for ((end, key) in listOf(m.cooldownEnd to VENGEANCE_COOLDOWN, m.teleblockEnd to TELEBLOCK_TIMER, m.frozenEnd to FROZEN_TIMER)) {
                if (kotlin.math.abs(m.left(end, now - 1) - serverLeft(b, key)) > 1) {
                    timerErrors++
                    if (firstErrors.size < 5) firstErrors += "${b.username} ${key} shown ${m.left(end, now - 1)} server ${serverLeft(b, key)}"
                }
            }
            if ((if (Vengeance.isActive(b)) 1 else 0) != m.vengeance) timerErrors++
            val expected = ClientHudSync.opponentPayload(b)
            if (m.opponent != expected) {
                opponentErrors++
                if (firstErrors.size < 5) firstErrors += "${b.username} opponent shown '${m.opponent}' server '$expected'"
            }
            val own = b.attr[gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR]?.get()
            if (own != null && m.opponent.startsWith("opp|n|${own.index}|")) ownFamiliar++
            for (item in items) {
                if (!world.isSpawned(item) || !b.tile.isWithinRadius(item.tile, 14)) continue
                val k = key(item.tile, item.item)
                if (!m.ground.containsKey(k)) {
                    groundErrors++
                    if (firstErrors.size < 5) firstErrors += "${b.username} misses $k"
                }
            }
        }
        firstErrors.forEach { run.note("load: $it") }
        run.expectTrue(e, "every bot's timers match the server", timerErrors == 0, "$timerErrors differences")
        run.expectTrue(e, "every bot's opponent matches the server", opponentErrors == 0, "$opponentErrors differences")
        run.expectTrue(e, "never the bot's own familiar as opponent", ownFamiliar == 0, "$ownFamiliar bots")
        run.expectTrue(e, "every ground stack in view is labelled for every bot", groundErrors == 0, "$groundErrors missing")

        items.forEach { if (world.isSpawned(it)) world.remove(it) }
        giants.forEach { run.remove(it) }
        bots.forEach { b ->
            Combat.reset(b)
            b.resetInteractions()
            runCatching { gg.rsmod.plugins.content.skills.summoning.Familiar.dismiss(b) }
        }
        wait(2)
    }

    /** The nearest tile (rings of 8, up to 400 away) whose (2r+1)-square has no blocked tile: room for 100 bots to step. */
    private fun openField(
        run: Conformance.Run,
        from: Tile,
        r: Int,
    ): Tile? {
        val world = run.world
        fun free(t: Tile) =
            (-r..r).all { dx -> (-r..r).all { dz -> !runCatching { world.collision.isClipped(t.transform(dx, dz)) }.getOrDefault(true) } }
        if (free(from)) return from
        for (ring in 8..400 step 8) {
            for (d in -ring..ring step 8) {
                for (t in listOf(from.transform(d, -ring), from.transform(d, ring), from.transform(-ring, d), from.transform(ring, d))) {
                    if (free(t)) return t
                }
            }
        }
        return null
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
