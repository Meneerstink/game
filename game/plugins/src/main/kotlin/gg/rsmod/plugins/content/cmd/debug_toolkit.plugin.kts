package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import java.io.File
import java.time.LocalDateTime

/*
 * Debug toolkit, in-game side (owner 2026-09-28; manual for every agent: C:\RSPS\TRACES.md).
 *
 *   traces            on/off for everything (server categories, avtrace, breachtrace, and the client via C:\RSPS\trace.on)
 *   traces on|off     explicit
 *   traces status     which categories are on
 *   traces <cat>      one category on/off (input msg interface interact item combat npcpath var plugin tick)
 *   problems          the last PROBLEM lines in the chatbox
 *   rendertest        render A/B switches in your own client (OpenGL): "rendertest cull off" draws back faces too,
 *                     "rendertest alpha on" lets translucent faces write depth again as the 667 client did (since
 *                     2026-10-01 they do not: they hid parts of pets / translucent gear by camera angle), "rendertest reset"
 *                     (also on every login). If a missing part comes back or goes away with a switch, that mechanism hides it.
 *                     The client's [modeltrace] lines (built npc/player/loc models, hiding frames) need the traces on.
 *   bug <text>        a bug report in C:\RSPS\bugs\ (position, surroundings, inventory, equipment, the last traced lines of
 *                     the server and the client) - the owner types what went wrong, the agent reads the file
 *
 * While the traces are on, every PROBLEM line also appears live in the chat of whoever turned them on (red, rate-limited).
 */

val liveViewers: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
val lastShown = java.util.concurrent.ConcurrentHashMap<String, Long>()
val sending = ThreadLocal.withInitial { false }

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
                    // The Quest Tutor's camera tour is a deliberate lock, never a stuck action (TutorIntro: 3-4 minutes, the hidden player waits at each camera stop).
                    val touring = gg.rsmod.plugins.content.quests.foundation.TutorIntro.isActive(p)
                    if (p.lock == gg.rsmod.game.model.LockState.NONE || touring) {
                        lockSince.remove(p.username)
                    } else if (since == null) {
                        lockSince[p.username] = world.currentCycle
                    } else if (world.currentCycle - since > LOCK_STUCK_TICKS) {
                        reportOnce("lock:${p.username}:$since", Trace.Cat.INPUT) {
                            "${p.username} has been locked (${p.lock}) for ${world.currentCycle - since} ticks - cannot act; last click: ${(p as? gg.rsmod.game.model.entity.Client)?.gameSystem?.lastAction}"
                        }
                    }
                    if (!touring && stuck(p.tile)) {
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
    // Off the game thread (WorldScan.runInBackground: it stalled a boot tick for 7.7 s); the reply comes back on the game thread.
    WorldScan.runInBackground(world) { result ->
        world.getPlayerForName(name)?.message(
            result.getOrNull()?.let { "<col=178000>World scan done:</col> ${it.readLines().getOrNull(1) ?: ""} - ${it.name}" } ?: "World scan failed - see C:\\RSPS\\bugs\\crashes-*.log",
        )
    }
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
        null -> player.message("conform summoning|curses|equipment|combat|hud|systems|deadman|traversal|quests [name] | stop")
        "stop" -> {
            gg.rsmod.plugins.content.cmd.conformance.Conformance.stopRequested = true
            player.message("The conformance run stops after its current entry.")
        }
        else -> gg.rsmod.plugins.content.cmd.conformance.Conformance.start(world, player, domain, args.drop(1))
    }
}

// The same from the command port (tools\conformance\Send-GameCommand.ps1 conform summoning), for agents.
gg.rsmod.game.CommandServerCommands.handlers["conform"] = { w, a -> gg.rsmod.plugins.content.cmd.conformance.Conformance.command(w, a) }

// Agent driving its own logged-in client from the command port (tools\conformance\Send-GameCommand.ps1, token required; the
// developer console often will not close again - tools\live\Con3.ps1): `oploc <name> <slot 1-5> <id> <x> <z>` clicks a loc option
// for that player through the real handler path (his client draws the real movement), `as <name> <command...>` runs an in-game
// command as him. Owner priority #1 2026-10-03: obstacles are verified in the agent's own client.
gg.rsmod.game.CommandServerCommands.handlers["oploc"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "") as? gg.rsmod.game.model.entity.Client
    val slot = a.getOrNull(1)?.toIntOrNull()
    val id = a.getOrNull(2)?.toIntOrNull()
    val x = a.getOrNull(3)?.toIntOrNull()
    val z = a.getOrNull(4)?.toIntOrNull()
    if (p == null || slot == null || id == null || x == null || z == null) {
        "usage: oploc <name> <slot> <id> <x> <z>"
    } else {
        val msg =
            when (slot) {
                1 -> gg.rsmod.game.message.impl.OpLoc1Message(0, x, id, z)
                2 -> gg.rsmod.game.message.impl.OpLoc2Message(id, x, z, 0)
                3 -> gg.rsmod.game.message.impl.OpLoc3Message(id, x, z, 0)
                4 -> gg.rsmod.game.message.impl.OpLoc4Message(id, x, z, 0)
                else -> gg.rsmod.game.message.impl.OpLoc5Message(id, x, z, 0)
            }
        if (p.gameSystem.inject(msg)) "clicked option $slot of $id at $x,$z for ${p.username} (at ${p.tile.x},${p.tile.z},${p.tile.height})" else "no handler"
    }
}
// `oplocitem <name> <inventory slot> <loc id> <x> <z>`: uses the item in that inventory slot on a loc through the real
// OpLocT handler path (2026-10-29: the Warriors' Guild animator checked in the agent's own client).
gg.rsmod.game.CommandServerCommands.handlers["oplocitem"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "") as? gg.rsmod.game.model.entity.Client
    val slot = a.getOrNull(1)?.toIntOrNull()
    val id = a.getOrNull(2)?.toIntOrNull()
    val x = a.getOrNull(3)?.toIntOrNull()
    val z = a.getOrNull(4)?.toIntOrNull()
    val item = slot?.let { p?.inventory?.get(it)?.id }
    if (p == null || slot == null || id == null || x == null || z == null || item == null) {
        "usage: oplocitem <name> <inventory slot with an item> <loc id> <x> <z>"
    } else {
        // The 667 inventory is interface 679 (component 0), as the client sends it.
        val msg = gg.rsmod.game.message.impl.OpLocTMessage((679 shl 16), slot, item, x, z, id, 0)
        if (p.gameSystem.inject(msg)) "used item $item (slot $slot) on $id at $x,$z for ${p.username}" else "no handler"
    }
}
// `findloc <name> <loc id> [radius]`: where the locs with that id stand around the player (agent client checks).
gg.rsmod.game.CommandServerCommands.handlers["findloc"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val id = a.getOrNull(1)?.toIntOrNull()
    val r = a.getOrNull(2)?.toIntOrNull() ?: 15
    if (p == null || id == null) {
        "usage: findloc <name> <loc id> [radius]"
    } else {
        val found = ArrayList<String>()
        for (dx in -r..r) for (dz in -r..r) {
            val t = p.tile.transform(dx, dz)
            w.chunks.get(t, createIfNeeded = false)
                ?.getEntities<gg.rsmod.game.model.entity.GameObject>(t, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                ?.filter { it.id == id }?.forEach { found += "${it.tile.x},${it.tile.z},${it.tile.height} type=${it.type} rot=${it.rot}" }
        }
        "loc $id near ${p.tile.x},${p.tile.z}: ${found.joinToString("; ").ifEmpty { "none" }}"
    }
}
// `cwplay <name> <sara|zam>`: puts the agent's test character straight into a Castle Wars round on that team (K07: a real round needs
// two players a team; one test account cannot start one). Team cape/hood and the play flag as joinPortal + startMatch set them.
gg.rsmod.game.CommandServerCommands.handlers["cwplay"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val team =
        when (a.getOrNull(1)) {
            "sara" -> gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsTeam.SARADOMIN
            "zam" -> gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsTeam.ZAMORAK
            else -> null
        }
    if (p == null || team == null) {
        "usage: cwplay <name> <sara|zam>"
    } else {
        w.queue {
            p.equipment[EquipmentType.CAPE.id] = gg.rsmod.game.model.item.Item(team.capeId)
            p.equipment[EquipmentType.HEAD.id] = gg.rsmod.game.model.item.Item(team.hoodId)
            p.attr[gg.rsmod.plugins.content.areas.kandarin.castlewars.CW_TEAM] = team
            p.attr[gg.rsmod.plugins.content.areas.kandarin.castlewars.CW_PLAYING] = true
            gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsRound.playing[team]!!.add(p)
            gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsHandler.chargeBrace(p)
            p.moveTo(team.baseTile)
        }
        "${p.username} plays Castle Wars for $team at ${team.baseTile}"
    }
}
// `petops`: every pet follower npc option (cache) with no handler bound - a dead option says "Nothing interesting happens" (N407).
gg.rsmod.game.CommandServerCommands.handlers["petops"] = { w, _ ->
    val dead = sortedMapOf<String, MutableList<Int>>()
    gg.rsmod.plugins.content.skills.summoning.Pets.variants.forEach { v ->
        // N423: the Upgraded copy of every form too (its Store and the pet's own ops).
        listOfNotNull(v.npc, gg.rsmod.plugins.content.skills.summoning.Pets.upgradedNpc(v.npc)).forEach { npc ->
            val def = w.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, npc) ?: return@forEach
            val bound = w.plugins.boundNpcOptions(npc)
            def.options.forEachIndexed { i, o ->
                if (o != null && o.isNotBlank() && (i + 1) !in bound) dead.getOrPut("$o (${def.name})") { mutableListOf() } += npc
            }
        }
    }
    if (dead.isEmpty()) "petops: every pet option is bound" else "petops: " + dead.entries.joinToString("; ") { "${it.key} ${it.value.take(4)}" }
}
// `petvoices <file>`: every pet form with the voice it makes on appearing, Talk-to and now and then (PetVoices.idleVoiceOf) and its
// step sound, as a TSV for the N405 sound audit (synth id -1 = silent).
gg.rsmod.game.CommandServerCommands.handlers["petvoices"] = { _, a ->
    val file = java.io.File(a.getOrNull(0) ?: "petvoices.tsv")
    val v = gg.rsmod.plugins.content.skills.summoning.PetVoices
    file.writeText(
        "pet\tvariant\tnpc\tclassic\tvoice\tstep\n" +
            gg.rsmod.plugins.content.skills.summoning.Pets.variants.joinToString("") {
                "${it.pet}\t${it.name}\t${it.npc}\t${it.classic}\t${v.idleVoiceOf(it.npc) ?: -1}\t${v.stepSound(it) ?: -1}\n"
            },
    )
    "petvoices: ${gg.rsmod.plugins.content.skills.summoning.Pets.variants.size} forms -> ${file.absolutePath}"
}
// `cwend <sara score> <zam score>`: ends the running Castle Wars round now with that score (K07: tickets per result), through the
// round's own last-minute tick.
gg.rsmod.game.CommandServerCommands.handlers["cwend"] = { w, a ->
    val sara = a.getOrNull(0)?.toIntOrNull()
    val zam = a.getOrNull(1)?.toIntOrNull()
    if (sara == null || zam == null) {
        "usage: cwend <sara score> <zam score>"
    } else {
        w.queue {
            val round = gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsRound
            round.score[gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsTeam.SARADOMIN] = sara
            round.score[gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsTeam.ZAMORAK] = zam
            round.active = true
            round.minutesLeft = 1
            gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsHandler.tickMinute(w)
        }
        "Castle Wars round ended at $sara-$zam"
    }
}
// `opnpc <name> <slot 1-5> <npc id>`: an npc option (Attack, Talk-to...) on the nearest npc with that id, through the real handler
// path (Barrows facing E21 checked in the agent's own client).
gg.rsmod.game.CommandServerCommands.handlers["opnpc"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "") as? gg.rsmod.game.model.entity.Client
    val slot = a.getOrNull(1)?.toIntOrNull()
    val id = a.getOrNull(2)?.toIntOrNull()
    var npc: gg.rsmod.game.model.entity.Npc? = null
    if (p != null && id != null) {
        w.npcs.forEach { n -> if (n.id == id && n.tile.height == p.tile.height && (npc == null || n.tile.getDistance(p.tile) < npc!!.tile.getDistance(p.tile))) npc = n }
    }
    val target = npc
    if (p == null || slot == null || target == null) {
        "usage: opnpc <name> <slot> <npc id> (an npc with that id on his plane)"
    } else {
        val msg =
            when (slot) {
                1 -> gg.rsmod.game.message.impl.OpNpc1Message(target.index, 0)
                2 -> gg.rsmod.game.message.impl.OpNpc2Message(target.index, 0)
                3 -> gg.rsmod.game.message.impl.OpNpc3Message(target.index, 0)
                4 -> gg.rsmod.game.message.impl.OpNpc4Message(target.index, 0)
                else -> gg.rsmod.game.message.impl.OpNpc5Message(target.index, 0)
            }
        if (p.gameSystem.inject(msg)) "clicked option $slot of npc $id#${target.index} at ${target.tile.x},${target.tile.z} for ${p.username}" else "no handler"
    }
}
// `opheld <name> <option 1-5> <item id>`: an inventory item option (Dig with a spade, Rub, Eat...) through the real handler path.
gg.rsmod.game.CommandServerCommands.handlers["opheld"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "") as? gg.rsmod.game.model.entity.Client
    val option = a.getOrNull(1)?.toIntOrNull()
    val item = a.getOrNull(2)?.toIntOrNull()
    val slot = if (p != null && item != null) p.inventory.rawItems.indexOfFirst { it?.id == item } else -1
    if (p == null || option == null || item == null || slot < 0) {
        "usage: opheld <name> <option> <item id> (item in the inventory)"
    } else {
        // 667 sends inventory options as IF_BUTTON (IfButton1Handler); this runs the same item path on the game thread.
        val inv = p.inventory[slot]!!
        w.queue {
            p.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM] = java.lang.ref.WeakReference(inv)
            p.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_ID] = inv.id
            p.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT] = slot
            p.fullInterruption(movement = false, interactions = true, animations = false, queue = true)
            if (!w.plugins.executeItem(p, inv.id, option)) gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) { "opheld: no handler for item ${inv.id} option $option" }
        }
        "item option $option of $item (slot $slot) for ${p.username} at ${p.tile.x},${p.tile.z},${p.tile.height}"
    }
}
// `equip <name> <item id>...`: wears inventory items through the real equip path (EquipAction, as the client's Wear click) - the
// agent's own client checks gear around obstacles with 667 and OSRS-ported items (AGENTS.md 3, OSRS GEAR IS PORTED).
gg.rsmod.game.CommandServerCommands.handlers["equip"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val ids = a.drop(1).mapNotNull { it.toIntOrNull() }
    if (p == null || ids.isEmpty()) {
        "usage: equip <name> <item id>..."
    } else {
        val results =
            ids.map { id ->
                val slot = p.inventory.rawItems.indexOfFirst { it?.id == id }
                if (slot < 0) "$id not in inventory" else "$id ${gg.rsmod.game.action.EquipAction.equip(p, p.inventory[slot]!!, slot)}"
            }
        results.joinToString("; ")
    }
}
// `slide <name> <anim> <x> <z> <startDelay> <delay> [anim2 x2 z2 startDelay2 delay2]`: plays an animation with one exact move (and an
// optional second step) on the agent's own test character, so an obstacle's motion can be compared by eye before it is coded
// (owner priority #1, noclip: the Trollheim Cliffsides had no donor motion to port).
gg.rsmod.game.CommandServerCommands.handlers["slide"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val n = a.drop(1).map { it.toIntOrNull() }
    if (p == null || n.size < 5 || n.any { it == null }) {
        "usage: slide <name> <anim> <x> <z> <startDelay> <delay> [anim2 x2 z2 startDelay2 delay2]"
    } else {
        val v = n.map { it!! }
        p.queue {
            for (step in v.chunked(5)) {
                if (step.size < 5) break
                val target = gg.rsmod.game.model.Tile(step[1], step[2], p.tile.height)
                if (step[0] >= 0) p.animate(step[0])
                gg.rsmod.plugins.content.skills.agility.exactMoveFrom(this, p, target, delay = step[4], startDelay = step[3])
            }
        }
        "slide ${p.username} from ${p.tile.x},${p.tile.z} steps=${v.chunked(5).size}"
    }
}
// `gwdkc <name> <god> <amount>`: a God Wars kill count, so the agent can walk through the chamber doors in its own client.
gg.rsmod.game.CommandServerCommands.handlers["gwdkc"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val god = gg.rsmod.plugins.content.areas.godwars.GodWars.God.values().firstOrNull { it.name.equals(a.getOrNull(1), ignoreCase = true) }
    val amount = a.getOrNull(2)?.toIntOrNull()
    if (p == null || god == null || amount == null) {
        "usage: gwdkc <name> <bandos|armadyl|saradomin|zamorak|zaros> <amount>"
    } else {
        gg.rsmod.plugins.content.areas.godwars.GodWars.setKillCount(p, god, amount)
        "${p.username} $god kc=$amount"
    }
}
// `magearena <name>`: marks the Mage Arena completed (the sparkling pools need it), for own-client checks.
gg.rsmod.game.CommandServerCommands.handlers["magearena"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    if (p == null) {
        "usage: magearena <name>"
    } else {
        p.attr[gg.rsmod.game.model.attr.COMPLETED_MAGE_ARENA] = true
        "${p.username} Mage Arena completed"
    }
}
// `pouchfill <name> <rune> <amount> [<rune> <amount>...]`: sets the carried rune pouch's contents (spellbook checks in the agent's
// own client; the real deposit asks "How many?" in a dialogue).
gg.rsmod.game.CommandServerCommands.handlers["pouchfill"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val slot = if (p != null) gg.rsmod.plugins.content.magic.RunePouch.pouchSlot(p.inventory) else -1
    val runes = a.drop(1).chunked(2).mapNotNull { c -> if (c.size == 2) gg.rsmod.game.model.item.Item(c[0].toInt(), c[1].toInt()) else null }
    if (p == null || slot < 0) {
        "usage: pouchfill <name> <rune> <amount>... (a pouch in the inventory)"
    } else {
        p.inventory[slot] = gg.rsmod.plugins.content.magic.RunePouch.withContents(p.inventory[slot]!!, runes)
        "${p.username} pouch: $runes"
    }
}
// `bankpin <name>`: opens the bank PIN settings (Bank "Set a Bank PIN"), for own-client checks of the keypad.
gg.rsmod.game.CommandServerCommands.handlers["bankpin"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    if (p == null) "usage: bankpin <name>" else { w.queue { gg.rsmod.plugins.content.inter.bank.BankPin.manage(p) }; "${p.username} bank PIN settings" }
}
// `hud <name> <payload>`: one HUD side-channel payload to that player's client (client-side checks, e.g. "grave|x|z|plane|seconds").
gg.rsmod.game.CommandServerCommands.handlers["hud"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    if (p == null || a.size < 2) "usage: hud <name> <payload>" else { gg.rsmod.game.model.hud.ClientHud.send(p, a.drop(1).joinToString(" ")); "sent to ${p.username}" }
}
// `chatplayer <name> <text>`: the player's own chathead line (chathead checks with worn gear in the agent's client).
gg.rsmod.game.CommandServerCommands.handlers["chatplayer"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    if (p == null || a.size < 2) "usage: chatplayer <name> <text>" else { p.queue { chatPlayer(a.drop(1).joinToString(" ")) }; "chathead for ${p.username}" }
}
// `chatnpc <name> <npc id> <anim|auto> <text>`: an npc dialogue head with a chosen head animation (chathead rig checks).
gg.rsmod.game.CommandServerCommands.handlers["chatnpc"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val npc = a.getOrNull(1)?.toIntOrNull()
    if (p == null || npc == null || a.size < 4) {
        "usage: chatnpc <name> <npc id> <anim|auto> <text>"
    } else {
        p.queue { chatNpc(a.drop(3).joinToString(" "), npc = npc, animationOverride = a[2].toIntOrNull()) }
        "npc $npc head for ${p.username}"
    }
}
// T1 `replay <team account> <bug-*.txt>`: puts a bugs\ report's position, gear, inventory, skills, books, run/spec and traces on
// a team test account (test server only; BugReplay). `replaycheck <team account> <bug-*.txt>`: what still differs.
gg.rsmod.game.CommandServerCommands.handlers["replay"] = { w, a -> bugReplayCommand(w, a, check = false) }
gg.rsmod.game.CommandServerCommands.handlers["replaycheck"] = { w, a -> bugReplayCommand(w, a, check = true) }

fun bugReplayCommand(
    w: gg.rsmod.game.model.World,
    a: List<String>,
    check: Boolean,
): String {
    val replay = gg.rsmod.plugins.content.cmd.BugReplay
    val verb = if (check) "replaycheck" else "replay"
    if (a.size < 2) return "usage: $verb <teamtest1-4> <bug-*.txt in bugs\\>"
    val p = w.getPlayerForName(a[0].replace("_", " "))
    replay.refusal(a[0], p, gg.rsmod.util.io.SharedDataFiles.testServer())?.let {
        Trace.log(Trace.Cat.INPUT) { "$verb ${a[0]} ${a[1]}: $it" }
        return it
    }
    val file = replay.reportFile(a[1], File(Trace.BUGS_DIR))
    if (file == null || !file.isFile) return "refused: no bug report '${a[1]}' in ${Trace.BUGS_DIR}"
    val report = replay.parse(file.readLines())
    val player = p!!
    if (check) {
        val diff = replay.diff(player, report)
        return if (diff.isEmpty()) {
            val instance = (report.position?.x ?: 0) >= replay.INSTANCE_X
            "replaycheck ${file.name} on ${player.username}: IDENTICAL (${if (instance) "position NOT checked (instance)" else "position"}, ${report.inventory.size} inventory + ${report.equipment.size} equipment slots" +
                "${if (report.replay.isEmpty()) ", old report without a Replay line" else ", skills/books/run/spec/style"})"
        } else {
            "replaycheck ${file.name} on ${player.username}: DIFF ${diff.joinToString(" | ")}"
        }
    }
    val result = replay.apply(player, report)
    result.filter { it.startsWith("PROBLEM") }.forEach { line -> Trace.problem(Trace.Cat.INPUT) { "replay ${file.name} -> ${player.username}: ${line.removePrefix("PROBLEM ")}" } }
    Trace.log(Trace.Cat.INPUT) { "replay ${file.name} -> ${player.username}: ${result.joinToString(" | ")}" }
    return "replay ${file.name} onto ${player.username}: ${result.joinToString(" | ")}"
}

gg.rsmod.game.CommandServerCommands.handlers["as"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    if (p == null || a.size < 2) {
        "usage: as <name> <command> [args]"
    } else {
        val handled = w.plugins.executeCommand(p, a[1].lowercase(), a.drop(2).toTypedArray().ifEmpty { null })
        "${a[1]} as ${p.username}: handled=$handled (at ${p.tile.x},${p.tile.z},${p.tile.height})"
    }
}

// Headless agent run only (-Drsps.conformance=<domain>, tools\conformance\Run-Conformance.ps1): never active in a normal start.
on_world_init { gg.rsmod.plugins.content.cmd.conformance.Conformance.startHeadless(world) }

on_world_init {
    if (File(gg.rsmod.game.RspsPaths.root("scan.on")).exists()) {
        world.queue {
            wait(10)
            // One-shot: the switch file is removed, so only the next start pays the few seconds again when it is recreated.
            File(gg.rsmod.game.RspsPaths.root("scan.on")).delete()
            WorldScan.runInBackground(world) { result -> result.getOrNull()?.let { logger.info("World scan written to {}", it) } }
        }
    }
}

/*
 * Render A/B switches for the owner's own client (client RspsRenderTest, sent on the HUD side channel). Owner 2026-09-30:
 * parts of npcs / pets / the player / the mansion vanish, some by camera angle; each switch removes one mechanism.
 */
on_command("rendertest", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs().map { it.lowercase() }
    val payload =
        when {
            args.isEmpty() -> null
            args[0] == "reset" -> "reset"
            args.size >= 2 && args[0] == "cull" && args[1] in setOf("on", "off") -> "cull=${if (args[1] == "on") 1 else 0}"
            args.size >= 2 && args[0] == "alpha" && args[1] in setOf("on", "off") -> "alphadepth=${if (args[1] == "on") 1 else 0}"
            else -> null
        }
    if (payload == null) {
        player.message("rendertest cull off|on (draw back faces) - rendertest alpha on|off (translucent faces write depth as in 667 / not, default off) - rendertest reset")
        return@on_command
    }
    gg.rsmod.game.model.hud.ClientHud.send(player, "render|$payload")
    Trace.log(Trace.Cat.INPUT) { "rendertest ${player.username}: $payload" }
    player.message("Render test: $payload (look at the missing part now; 'rendertest reset' to undo).")
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
    // T2: the reporter's client saves its next frame as <report>.png (RspsBugShot; RuneLite ScreenshotPlugin route).
    BugShot.request(player, file)
}

// T2 round 4 (AUDIT T2-93): a session that ends closes the player's open screenshot uploads (the 78 client drops them on a new
// connection as well, so nothing of an old session is ever sent over a new login).
on_logout {
    BugShot.sessionEnded(player)
}

fun writeBugReport(
    player: Player,
    text: String,
): File {
    val dir = File(Trace.BUGS_DIR)
    dir.mkdirs()
    // T2: a second report in the same second gets its own stem, so neither report nor screenshot is overwritten.
    val file = BugShot.reportFile(dir, LocalDateTime.now(), player.username)
    val t = player.tile
    val out = StringBuilder()
    out.appendLine("BUG REPORT ${LocalDateTime.now()}  by ${player.username}")
    out.appendLine("What went wrong: $text")
    out.appendLine()
    out.appendLine(gg.rsmod.plugins.content.cmd.BugReplay.positionLine(player))
    out.appendLine("Traces: ${Trace.status()}")
    out.appendLine("Combat level=${player.combatLevel}  hp=${player.getCurrentLifepoints()}/${player.getMaximumLifepoints()}  lock=${player.lock}")
    // T1: the open interfaces as parent:child=interface ids (was the InterfaceSet object's toString) and the display mode.
    out.appendLine("Interfaces open: ${runCatching { player.interfaces.describeVisible() + "  mode=" + player.interfaces.displayMode }.getOrDefault("?")}")
    out.appendLine(BugShot.reportLine(file))
    out.appendLine()
    out.appendLine("Inventory: " + player.inventory.rawItems.mapIndexedNotNull { i, it -> it?.let { "$i:${it.id}x${it.amount}" } }.joinToString(" "))
    out.appendLine("Equipment: " + player.equipment.rawItems.mapIndexedNotNull { i, it -> it?.let { "$i:${it.id}x${it.amount}" } }.joinToString(" "))
    // T1 bug replay (tools\team\Replay-Bug.ps1): skills, prayer book, spellbook, run, spec, style, familiar ... in one line.
    // Never let it cost the report itself (every player's `bug` command on the live server).
    out.appendLine(runCatching { gg.rsmod.plugins.content.cmd.BugReplay.stateLines(player).last() }.getOrElse { "Replay: unavailable (${it.javaClass.simpleName})" })
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
    out.appendLine("---- client log tail (the reporter's client log in launcher-logs, T1) ----")
    runCatching {
        val address = ((player as? gg.rsmod.game.model.entity.Client)?.channel?.remoteAddress() as? java.net.InetSocketAddress)?.address
        val local = address != null && (address.isLoopbackAddress || java.net.NetworkInterface.getByInetAddress(address) != null)
        val logs = File(gg.rsmod.game.RspsPaths.root("launcher-logs")).listFiles()?.toList().orEmpty()
        val log = gg.rsmod.plugins.content.cmd.BugReplay.pickClientLog(logs, gg.rsmod.util.io.SharedDataFiles.testServer(), local)
        if (log == null) {
            out.appendLine("(not available: ${if (local) "no matching client log" else "the reporter's client does not run on the server PC"})")
        } else {
            out.appendLine("(${log.name})")
            log.readLines().takeLast(200).forEach { out.appendLine(it) }
        }
    }
    file.writeText(out.toString())
    Trace.log(Trace.Cat.INPUT) { "bug report ${file.name} by ${player.username}: $text" }
    return file
}
