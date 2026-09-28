package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Debug toolkit, in-game side (owner 2026-09-28; manual for every agent: C:\RSPS\TRACES.md).
 *
 *   traces            on/off for everything (server categories, avtrace, breachtrace, and the client via C:\RSPS\trace.on)
 *   traces on|off     explicit
 *   traces status     which categories are on
 *   traces <cat>      one category on/off (input msg interface interact item combat npcpath var plugin tick)
 *   problems          the last PROBLEM lines in the chatbox
 *   bug <text>        a bug report in C:\RSPS\bugs\ (position, surroundings, inventory, equipment, the last traced lines of
 *                     the server and the client) - the owner types what went wrong, the agent reads the file
 *
 * While the traces are on, every PROBLEM line also appears live in the chat of whoever turned them on (red, rate-limited).
 */

val liveViewers: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
val lastShown = java.util.concurrent.ConcurrentHashMap<String, Long>()
val sending = ThreadLocal.withInitial { false }
val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

fun showLive(line: String) {
    if (liveViewers.isEmpty() || sending.get()) return
    // The same problem at most once per 10 seconds, so a repeating fault does not flood the chat.
    val key = line.replace(Regex("\\d+"), "#").take(120)
    val now = System.currentTimeMillis()
    val last = lastShown[key]
    if (last != null && now - last < 10_000) return
    lastShown[key] = now
    if (lastShown.size > 2000) lastShown.clear()
    val text = "<col=ff3030>[!] " + line.replace("PROBLEM", "").replace(Regex("\\s+"), " ").trim().take(170) + "</col>"
    sending.set(true)
    try {
        liveViewers.forEach { name -> world.getPlayerForName(name)?.message(text) }
    } finally {
        sending.set(false)
    }
}

Trace.onProblem = { line -> showLive(line) }

/*
 * Tick invariants (debug toolkit phase 2), every INVARIANT_EVERY ticks while the traces are on, around online players only:
 * a player or a walking 1x1 npc that cannot step in any of the four directions is stuck (spawned/knocked/teleported into
 * scenery - ROOT_CAUSES #25, #42, #50, #77, #78); an npc alive with hitpoints outside 0..max. Each case is reported once.
 */
val INVARIANT_EVERY = 5
val LOCK_STUCK_TICKS = 50
val lockSince = java.util.concurrent.ConcurrentHashMap<String, Int>()
val reportedInvariants: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
val CARDINALS = listOf(gg.rsmod.game.model.Direction.NORTH, gg.rsmod.game.model.Direction.EAST, gg.rsmod.game.model.Direction.SOUTH, gg.rsmod.game.model.Direction.WEST)

fun stuck(tile: gg.rsmod.game.model.Tile): Boolean = CARDINALS.none { world.collision.canTraverse(tile = tile, direction = it, projectile = false, water = false) }

fun reportOnce(
    key: String,
    cat: Trace.Cat,
    text: () -> String,
) {
    if (reportedInvariants.size > 50_000) reportedInvariants.clear()
    if (reportedInvariants.add(key)) Trace.problem(cat) { text() }
}

/*
 * Phase 5 economy ledger (debug toolkit): every tick while the traces are on, each real player's inventory + equipment + bank
 * is compared with the previous tick. Every change is an `[trace:item] ledger` line with the click that caused it. A net
 * change of the total (all three together) during an action that only moves items (bank deposit/withdraw, wield/remove) is a
 * PROBLEM: something was duplicated or lost (ROOT_CAUSES #6, #52, #56).
 */
val ledger = java.util.concurrent.ConcurrentHashMap<String, Map<Int, Long>>()
val NEUTRAL_INTERFACES = setOf(762, 763, 11, 387, 670, 667) // bank, bank inventory, deposit box, equipment, equipment stats

fun totals(p: Player): Map<Int, Long> {
    val m = HashMap<Int, Long>()
    listOf(p.inventory, p.equipment, p.bank).forEach { c -> c.rawItems.forEach { it?.let { item -> m.merge(item.id, item.amount.toLong(), Long::plus) } } }
    return m
}

on_world_init {
    world.queue {
        while (true) {
            wait(1)
            if (!Trace.enabled(Trace.Cat.ITEM)) {
                if (ledger.isNotEmpty()) ledger.clear()
                continue
            }
            runCatching {
                world.players.forEach { p ->
                    if (p.username.startsWith("Sweep") || p.username.startsWith("Breachsim")) return@forEach
                    val now = totals(p)
                    val before = ledger.put(p.username, now) ?: return@forEach
                    if (before == now) return@forEach
                    val changes = (before.keys + now.keys).mapNotNull { id -> val d = (now[id] ?: 0L) - (before[id] ?: 0L); if (d != 0L) id to d else null }
                    if (changes.isEmpty()) return@forEach
                    val action = (p as? gg.rsmod.game.model.entity.Client)?.gameSystem?.lastAction ?: "-"
                    Trace.log(Trace.Cat.ITEM) { "ledger ${p.username} ${changes.joinToString(" ") { (id, d) -> "${if (d > 0) "+" else ""}$d x$id" }} after $action" }
                    val neutral = Regex("""IfButton\w*\(.*?hash=(\d+)""").find(action)?.groupValues?.get(1)?.toIntOrNull()?.let { (it shr 16) in NEUTRAL_INTERFACES } == true ||
                        action.startsWith("OpHeld") && (action.contains("option=2") || action.contains("Wield") || action.contains("Wear"))
                    if (neutral) {
                        Trace.problem(Trace.Cat.ITEM) { "items appeared or vanished during a move-only action: ${p.username} ${changes.joinToString(" ") { (id, d) -> "${if (d > 0) "+" else ""}$d x$id" }} after $action (dupe or loss?)" }
                    }
                }
            }.onFailure { Trace.crash("economy ledger", it) }
        }
    }
}

on_world_init {
    world.queue {
        while (true) {
            wait(INVARIANT_EVERY)
            if (!Trace.anyOn()) continue
            runCatching {
                val players = mutableListOf<Player>()
                world.players.forEach { players += it }
                players.forEach { p ->
                    // A lock that outlives any real action (cutscenes, agility, deaths take seconds, not half a minute):
                    // the player can no longer click anything - an action that never unlocked (a thrown plugin, a lost queue).
                    val since = lockSince[p.username]
                    if (p.lock == gg.rsmod.game.model.LockState.NONE) {
                        lockSince.remove(p.username)
                    } else if (since == null) {
                        lockSince[p.username] = world.currentCycle
                    } else if (world.currentCycle - since > LOCK_STUCK_TICKS) {
                        reportOnce("lock:${p.username}:$since", Trace.Cat.INPUT) {
                            "${p.username} has been locked (${p.lock}) for ${world.currentCycle - since} ticks - cannot act; last click: ${(p as? gg.rsmod.game.model.entity.Client)?.gameSystem?.lastAction}"
                        }
                    }
                    if (stuck(p.tile)) {
                        reportOnce("p:${p.username}:${p.tile}", Trace.Cat.NPCPATH) { "stuck: ${p.username} at ${p.tile.x},${p.tile.z},${p.tile.height} cannot step north, east, south or west" }
                    }
                }
                if (players.isEmpty()) return@runCatching
                world.npcs.forEach { n ->
                    if (n.isDead() || players.none { it.tile.height == n.tile.height && it.tile.isWithinRadius(n.tile, 20) }) return@forEach
                    if (n.getSize() == 1 && n.walkRadius > 0 && stuck(n.tile)) {
                        reportOnce("n:${n.index}:${n.tile}", Trace.Cat.NPCPATH) { "stuck: npc ${n.id} '${runCatching { n.def.name }.getOrDefault("?")}' #${n.index} at ${n.tile.x},${n.tile.z},${n.tile.height} cannot step in any direction (spawned or moved into scenery)" }
                    }
                    val hp = n.getCurrentLifepoints()
                    if ((hp < 0 || hp > n.getMaximumLifepoints()) && n.attr[Sweeps.SWEEP_DUMMY_ATTR] != true) {
                        reportOnce("hp:${n.index}:$hp", Trace.Cat.COMBAT) { "npc ${n.id} #${n.index} has hitpoints $hp outside 0..${n.getMaximumLifepoints()}" }
                    }
                }
            }.onFailure { Trace.crash("tick invariants", it) }
        }
    }
}

on_command("traces", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val first = args.getOrNull(0)?.lowercase()
    when {
        first == "status" -> player.message("Traces: ${Trace.status()}")
        first != null && first != "on" && first != "off" -> {
            val cat = Trace.Cat.values().firstOrNull { it.key == first }
            if (cat == null) {
                player.message("Unknown trace '$first'. Categories: ${Trace.Cat.values().joinToString(" ") { it.key }}, or on / off / status.")
            } else {
                val value = args.getOrNull(1)?.lowercase()?.let { it == "on" } ?: !Trace.enabled(cat)
                Trace.set(cat, value)
                player.message("Trace ${cat.key} is now ${if (value) "on" else "off"}: ${cat.what}.")
            }
        }
        else -> {
            val value = if (first == null) !Trace.anyOn() else first == "on"
            Trace.setAll(value)
            if (value) liveViewers.add(player.username) else liveViewers.remove(player.username)
            player.message(
                if (value) {
                    "<col=178000>All traces are ON</col> (server now; the client picks them up at its next start). Problems show here in red. Type bug <what went wrong> to save a report."
                } else {
                    "All traces are OFF."
                },
            )
        }
    }
}

/*
 * Phase 3: `scan` checks the whole loaded world without playing (WorldScan: census, npc spawns/combat/BAS, attack-table and
 * sound-table ids, worn item models) and writes C:\RSPS\bugs\scan-<stamp>.txt. At boot too when C:\RSPS\scan.on exists (one-shot).
 */
on_command("scan", Privilege.ADMIN_POWER) {
    player.message("World scan running in the background... (read-only; the game keeps running)")
    val name = player.username
    // Off the game thread: the scan reads files and every definition (it stalled a boot tick for 7.7 s on the game thread).
    Thread({
        val result = runCatching { WorldScan.run(world) }
        result.exceptionOrNull()?.let { Trace.crash("world scan", it) }
        world.getPlayerForName(name)?.message(
            result.getOrNull()?.let { "<col=178000>World scan done:</col> ${it.readLines().getOrNull(1) ?: ""} - ${it.name}" } ?: "World scan failed - see C:\\RSPS\\bugs\\crashes-*.log",
        )
    }, "world-scan").apply { isDaemon = true }.start()
}

/*
 * Phase 4: `sweep weapons|spells|familiars|npcs [from] [count] [...]` and `sweep stop` (Sweeps.kt). Stand in an empty
 * single-way field: a headless test player runs next to you and writes C:\RSPS\bugs\sweep-<what>-<stamp>.txt.
 */
on_command("sweep", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs().toList()
    val what = args.getOrNull(0)?.lowercase()
    when (what) {
        null -> player.message("sweep weapons [from] [count] [pvp] | spells | familiars [from] [count] | npcs [from] [count] [name] | dialogues [from] [count] | drops | stop")
        "stop" -> {
            Sweeps.stop()
            player.message("The running sweep stops after its current entry.")
        }
        else -> Sweeps.start(world, player, what, args.drop(1))
    }
}

/*
 * P8 conformance suite (conformance\Conformance.kt, catalogue C:\RSPS\docs\conformance_catalogue.md): `conform summoning
 * [name]` and `conform stop`. Measures everything per tick against the donor references; report in C:\RSPS\bugs\conformance\.
 */
on_command("conform", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs().toList()
    when (val domain = args.getOrNull(0)?.lowercase()) {
        null -> player.message("conform summoning [name] | stop")
        "stop" -> {
            gg.rsmod.plugins.content.cmd.conformance.Conformance.stopRequested = true
            player.message("The conformance run stops after its current entry.")
        }
        else -> gg.rsmod.plugins.content.cmd.conformance.Conformance.start(world, player, domain, args.drop(1))
    }
}

// The same from the command port (tools\conformance\Send-GameCommand.ps1 conform summoning), for agents.
gg.rsmod.game.CommandServerCommands.handlers["conform"] = { w, a -> gg.rsmod.plugins.content.cmd.conformance.Conformance.command(w, a) }

on_world_init {
    if (File("C:/RSPS/scan.on").exists()) {
        world.queue {
            wait(10)
            // One-shot: the switch file is removed, so only the next start pays the few seconds again when it is recreated.
            File("C:/RSPS/scan.on").delete()
            Thread({
                runCatching { logger.info("World scan written to {}", WorldScan.run(world)) }.onFailure { Trace.crash("world scan at boot", it) }
            }, "world-scan").apply { isDaemon = true }.start()
        }
    }
}

on_command("problems", Privilege.ADMIN_POWER) {
    val problems = Trace.recent().filter { it.contains("PROBLEM") }.takeLast(12)
    if (problems.isEmpty()) {
        player.message("No PROBLEM lines ${if (Trace.anyOn()) "yet" else "- the traces are off (type traces)"}.")
    } else {
        problems.forEach { player.message("<col=ff3030>" + it.replace("PROBLEM", "").replace(Regex("\\s+"), " ").take(170) + "</col>") }
    }
}

on_command("bug") {
    val text = player.getCommandArgs().joinToString(" ").trim()
    if (text.isEmpty()) {
        player.message("Type bug followed by what went wrong, e.g.: bug durial does not barrage when I run")
        return@on_command
    }
    val file = writeBugReport(player, text)
    player.message("<col=178000>Bug report saved</col> (${file.name}).${if (!Trace.anyOn()) " Tip: type traces first, then repeat the action, for a full trace." else ""}")
}

fun writeBugReport(
    player: Player,
    text: String,
): File {
    val dir = File(Trace.BUGS_DIR)
    dir.mkdirs()
    val file = File(dir, "bug-${LocalDateTime.now().format(STAMP)}-${player.username}.txt")
    val t = player.tile
    val out = StringBuilder()
    out.appendLine("BUG REPORT ${LocalDateTime.now()}  by ${player.username}")
    out.appendLine("What went wrong: $text")
    out.appendLine()
    out.appendLine("Position: ${t.x},${t.z},${t.height}  region=${t.regionId}  chunk=${t.chunkCoords}")
    out.appendLine("Traces: ${Trace.status()}")
    out.appendLine("Combat level=${player.combatLevel}  hp=${player.getCurrentLifepoints()}/${player.getMaximumLifepoints()}  lock=${player.lock}")
    out.appendLine("Interfaces open: ${runCatching { player.interfaces.toString() }.getOrDefault("?")}")
    out.appendLine()
    out.appendLine("Inventory: " + player.inventory.rawItems.mapIndexedNotNull { i, it -> it?.let { "$i:${it.id}x${it.amount}" } }.joinToString(" "))
    out.appendLine("Equipment: " + player.equipment.rawItems.mapIndexedNotNull { i, it -> it?.let { "$i:${it.id}x${it.amount}" } }.joinToString(" "))
    out.appendLine()
    out.appendLine("Npcs within 10 tiles:")
    world.npcs.forEach { n ->
        if (n.tile.height == t.height && n.tile.isWithinRadius(t, 10)) {
            out.appendLine("  ${n.id} '${runCatching { n.def.name }.getOrDefault("?")}' #${n.index} at ${n.tile.x},${n.tile.z} size=${n.getSize()} hp=${n.getCurrentLifepoints()} target=${runCatching { n.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() }.getOrNull()}")
        }
    }
    out.appendLine("Objects within 5 tiles:")
    for (dx in -5..5) for (dz in -5..5) {
        val tile = t.transform(dx, dz)
        world.chunks.getOrCreate(tile).getEntities<GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT).forEach { o ->
            out.appendLine("  ${o.id} '${runCatching { o.getDef(world.definitions).name }.getOrDefault("?")}' at ${o.tile.x},${o.tile.z} type=${o.type} rot=${o.rot}")
        }
    }
    out.appendLine()
    out.appendLine("---- last server trace lines (oldest first) ----")
    Trace.recent().forEach { out.appendLine(it) }
    out.appendLine()
    out.appendLine("---- client log tail (newest launcher-logs\\client-*.out.log) ----")
    runCatching {
        File("C:/RSPS/launcher-logs").listFiles { f -> f.name.startsWith("client-") && f.name.endsWith(".out.log") }
            ?.maxByOrNull { it.lastModified() }?.let { log ->
                out.appendLine("(${log.name})")
                log.readLines().takeLast(200).forEach { out.appendLine(it) }
            }
    }
    file.writeText(out.toString())
    Trace.log(Trace.Cat.INPUT) { "bug report ${file.name} by ${player.username}: $text" }
    return file
}
