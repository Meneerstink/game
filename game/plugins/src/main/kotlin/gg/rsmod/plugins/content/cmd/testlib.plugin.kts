package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.CommandServerCommands
import gg.rsmod.game.model.Probe
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.RuinousPowers

/*
 * N620 TESTBIBLIOTHEEK - command-port commands every test script uses (bash: work\claude-scripts\lib\testlib.sh).
 *
 *   teststate <name>             everything a script needs to know about the player in ONE line (tile + standable, wildy, multi,
 *                                hp/prayer/run, book + active prayers, lock, queues, path, modal, target, inventory, worn, npcs near,
 *                                newest event number)
 *   testevents <name> [since]    the numbered events after <since> (messages, interfaces, sounds, own anim/gfx/hits, denies);
 *                                the first call starts watching the player
 *   testunwatch <name>           stop watching
 *   tp <name> <x> <z> [h] [force] safe teleport (testserver only): refuses tiles outside the world (15-11 'tele 4 1' crashed the
 *                                client) and tiles you cannot stand on (14-11 teamtest1 in a rock at 3108,3958) - it names the
 *                                nearest standable tile instead; 'force' skips the stand check
 */

fun who(t: Pawn?): String =
    when (t) {
        null -> "-"
        is Player -> t.username
        is Npc -> "${t.id}#${t.index}"
        else -> "?"
    }

fun findPlayer(a: List<String>): Player? = world.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")

fun stateLine(p: Player): String {
    val w = p.world
    val t = p.tile
    val s = p.skills
    val book = AncientCurses.getBook(p).name.lowercase()
    val active =
        if (RuinousPowers.isBook(p)) {
            RuinousPowers.active(p).joinToString(",") { it.named }
        } else {
            runCatching { Prayer.values().filter { Prayers.isActive(p, it) }.joinToString(",") { it.name } }.getOrDefault("?")
        }
    val internals = Probe.internals(p)
    val inv = p.inventory.rawItems.filterNotNull()
    val invText = inv.groupBy { it.id }.entries.take(8).joinToString(",") { (id, items) -> "$id x${items.sumOf { it.amount }}" }
    val worn = p.equipment.rawItems.mapIndexedNotNull { i, it -> it?.let { "$i:${it.id}" } }.joinToString(",")
    val near =
        ArrayList<Npc>().also { l -> w.npcs.forEach { l += it } }
            .filter { n -> n.tile.height == t.height && n.tile.isWithinRadius(t, 6) && !n.isDead() }
            .sortedBy { it.tile.getDistance(t) }
            .take(6)
            .joinToString(",") { n -> "${n.id}#${n.index}@${n.tile.x},${n.tile.z} hp=${n.getCurrentLifepoints()} t=${who(n.getCombatTarget())}" }
    val seq = if (TestLib.watched(p)) TestLib.events(p, Long.MAX_VALUE).first else -1
    return "tick=${w.currentCycle} ${p.username}@${t.x},${t.z},${t.height} stand=${TestLib.standable(w, t)} wild=${t.getWildernessLevel()} multi=${t.isMulti(w)}" +
        " hp=${p.getCurrentLifepoints()}/${p.getMaximumLifepoints()} pray=${s.getCurrentLevel(5)}/${s.getMaxLevel(5)} run=${p.runEnergy.toInt()}" +
        " book=$book prayers=[$active] lock=${internals["lock"]} queues=[${internals["queues"]}] path=[${internals["path"]?.take(80)}]" +
        " modal=${p.interfaces.getModal()} target=${who(p.getCombatTarget())} familiar=${p.attr[gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR]?.get()?.let { "${it.id}#${it.index}" } ?: "-"} inv=${inv.size}/28 [$invText] worn=[$worn] npcs=[$near] events=$seq"
}

CommandServerCommands.handlers["teststate"] = { _, a ->
    findPlayer(a)?.let { stateLine(it) } ?: "usage: teststate <name> (player not online)"
}

// `npcstats <npc index>`: the npc's CURRENT combat levels (attack/strength/defence/magic/ranged) + hp - to prove stat drains (CoX 16-11:
// Elder maul Pulverize -35% Defence, Dinh's bulwark Shield Bash -5%).
// `kill <name>` (CoX breaktest 16-11): a hit of all the player's lifepoints + 10 with no source, as `toabot kill` does - a death that no
// PvP rule blocks (a player's ::damage on a player is refused in guarded areas such as the raids).
CommandServerCommands.handlers["kill"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    if (p == null) "usage: kill <name>" else { p.hit(p.getCurrentLifepoints() + 10); "kill ${p.username}: hit ${p.getCurrentLifepoints() + 10}" }
}
CommandServerCommands.handlers["npcstats"] = { w, a ->
    val n = a.getOrNull(0)?.toIntOrNull()?.let { i -> w.npcs.entries.filterNotNull().firstOrNull { it.index == i } }
    if (n == null) "usage: npcstats <npc index>" else {
        val s = n.stats
        "npc ${n.id}#${n.index} att=${s.getCurrentLevel(0)} str=${s.getCurrentLevel(1)} def=${s.getCurrentLevel(2)} mag=${s.getCurrentLevel(3)} rng=${s.getCurrentLevel(4)} hp=${n.getCurrentLifepoints()}"
    }
}

// `npchp <npc index> <hp>` (DMM-ARMAGEDDON 20-11, owner 'testcommando maken'): sets one npc's CURRENT lifepoints (at most its maximum,
// at least 1) so a fight that matters only at the kill - a 3000-hp breach monster's MvP/loot/DP - can be tested by bots in seconds.
// The npc keeps everything else (breach ledger, attrs); only the test server (rsps.testServer) accepts it.
CommandServerCommands.handlers["npchp"] = { w, a ->
    val n = a.getOrNull(0)?.toIntOrNull()?.let { i -> w.npcs.entries.filterNotNull().firstOrNull { it.index == i } }
    val hp = a.getOrNull(1)?.toIntOrNull()
    if (!gg.rsmod.util.io.SharedDataFiles.testServer()) {
        "npchp: only on the test server"
    } else if (n == null || hp == null) {
        "usage: npchp <npc index> <hp>"
    } else {
        n.setCurrentLifepoints(hp.coerceIn(1, n.getMaximumLifepoints()))
        "npchp ${n.id}#${n.index}: hp=${n.getCurrentLifepoints()}/${n.getMaximumLifepoints()}"
    }
}

CommandServerCommands.handlers["testevents"] = { _, a ->
    val p = findPlayer(a)
    if (p == null) {
        "usage: testevents <name> [since] (player not online)"
    } else {
        val (seq, lines) = TestLib.events(p, a.getOrNull(1)?.toLongOrNull() ?: 0L)
        "events=$seq" + lines.joinToString("") { " || $it" }
    }
}

CommandServerCommands.handlers["testunwatch"] = { _, a ->
    val p = findPlayer(a)
    if (p == null) "usage: testunwatch <name>" else { TestLib.unwatch(p); "not watching ${p.username}" }
}

// `droproll <name> <npc id> [n=1000]` (N620/N614): rolls the npc's REAL registered kill table n times for that player (rate 1.0, nothing
// spawned) and lists per item: count of drops, total amount and 1/x - to check a table against the wiki in seconds instead of killing.
CommandServerCommands.handlers["droproll"] = { _, a ->
    val p = findPlayer(a)
    val npcId = a.getOrNull(1)?.toIntOrNull()
    val n = (a.getOrNull(2)?.toIntOrNull() ?: 1000).coerceIn(1, 200_000)
    if (p == null || npcId == null) {
        "usage: droproll <name> <npc id> [n]"
    } else if (!gg.rsmod.plugins.content.drops.DropTableFactory.hasTable(npcId)) {
        "droproll: no kill table registered for npc $npcId"
    } else {
        val hits = HashMap<Int, Int>()
        val amounts = HashMap<Int, Long>()
        repeat(n) {
            gg.rsmod.plugins.content.drops.DropTableFactory.getDrop(p, npcId, rate = 1.0)?.forEach { item ->
                hits.merge(item.id, 1, Int::plus)
                amounts.merge(item.id, item.amount.toLong(), Long::plus)
            }
        }
        val defs = p.world.definitions
        "droproll npc $npcId x$n: " + hits.entries.sortedByDescending { it.value }.joinToString(" | ") { (id, c) ->
            val name = runCatching { defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name }.getOrDefault("?")
            "$id $name ${c}x (1/${"%.1f".format(n.toDouble() / c)}) amt=${amounts[id]}"
        }
    }
}

// `dropview <name> <source key>` (N620): opens the Drop Viewer straight on one source (key = '<name lowercase>|<level>', spaces as _,
// e.g. nechryael|115) - a screenshot of one table without clicking through the list.
CommandServerCommands.handlers["dropview"] = { _, a ->
    val p = findPlayer(a)
    val key = a.getOrNull(1)?.replace("_", " ")
    if (p == null || key == null) "usage: dropview <name> <source name or key, e.g. nechryael>" else {
        // 15-11: the key's level is OUR npc level (not the wiki's) - match the exact key, else the first key starting with the name.
        val all = gg.rsmod.plugins.content.quests.tab.DropSources.all(p)
        // keys are 'npc:<id>' / 'barrows' / the ToA page; the shown name is Source.name -> exact key, else exact name, else name contains
        val hit = all.firstOrNull { it.key == key } ?: all.firstOrNull { it.name.equals(key, true) }
            ?: all.firstOrNull { it.name.contains(key, true) }
        if (hit == null) "dropview: no source matching '$key'" else {
            gg.rsmod.plugins.content.quests.tab.DropViewer.open(p, hit.key)
            "dropview ${p.username} -> ${hit.key}"
        }
    }
}

/**
 * PROJECT48 scenario aid: `tpnpc <name> <npc id> <x> <z> [height]` puts the bot on a free tile NEXT to the npc of that id nearest to
 * (x, z) from which the npc can be walked to (a post inside a building or behind a wall made every "talk to" step fail with
 * "I can't reach that!" when the script's own tile was on the wrong side of a wall).
 */
CommandServerCommands.handlers["tpnpc"] = { w, a ->
    val p = findPlayer(a)
    val id = a.getOrNull(1)?.toIntOrNull()
    val x = a.getOrNull(2)?.toIntOrNull()
    val z = a.getOrNull(3)?.toIntOrNull()
    val h = a.getOrNull(4)?.toIntOrNull() ?: 0
    when {
        System.getProperty("rsps.testServer") != "true" -> "tpnpc: testserver only"
        p == null || id == null || x == null || z == null -> "usage: tpnpc <name> <npc id> <x> <z> [height]"
        else -> {
            val centre = Tile(x, z, h)
            val npc = w.npcs.entries.filterNotNull().filter { it.id == id && it.tile.height == h && it.tile.getDistance(centre) <= 40 }.minByOrNull { it.tile.getDistance(centre) }
            if (npc == null) {
                "tpnpc: no npc $id within 40 tiles of $x,$z,$h"
            } else {
                val dirs =
                    listOf(
                        Triple(0, 1, gg.rsmod.game.model.Direction.SOUTH),
                        Triple(0, -1, gg.rsmod.game.model.Direction.NORTH),
                        Triple(1, 0, gg.rsmod.game.model.Direction.WEST),
                        Triple(-1, 0, gg.rsmod.game.model.Direction.EAST),
                    )
                val spot =
                    dirs.map { (dx, dz, toward) -> npc.tile.transform(dx, dz) to toward }
                        .firstOrNull { (t, toward) -> TestLib.standable(w, t) && w.collision.canTraverse(tile = t, direction = toward, projectile = false, water = false) }
                if (spot == null) {
                    "tpnpc: no tile next to npc $id at ${npc.tile.x},${npc.tile.z} can walk to it"
                } else {
                    p.moveTo(spot.first)
                    TestLib.add(p, "tpnpc ${spot.first.x},${spot.first.z},${spot.first.height}")
                    "tpnpc ${p.username} -> ${spot.first.x},${spot.first.z},${spot.first.height} (npc $id at ${npc.tile.x},${npc.tile.z})"
                }
            }
        }
    }
}

CommandServerCommands.handlers["tp"] = { w, a ->
    val p = findPlayer(a)
    val n = a.drop(1).filter { it != "force" }.map { it.toIntOrNull() }
    val force = "force" in a
    when {
        System.getProperty("rsps.testServer") != "true" -> "tp: testserver only (live: lc.sh teleport)"
        p == null || n.size < 2 || n.any { it == null } -> "usage: tp <name> <x> <z> [height] [force]"
        !TestLib.inWorld(n[0]!!, n[1]!!, n.getOrNull(2) ?: 0) -> "REFUSED tp ${n.joinToString(",")}: outside the world (check the script's variables)"
        else -> {
            val target = Tile(n[0]!!, n[1]!!, n.getOrNull(2) ?: 0)
            if (!force && !TestLib.mapped(w, target)) {
                "REFUSED tp ${target.x},${target.z},${target.height}: no map loaded there (check the script's variables)"
            } else if (!force && !TestLib.standable(w, target)) {
                val alt = TestLib.nearestStandable(w, target)
                "REFUSED tp ${target.x},${target.z},${target.height}: cannot stand there" + (alt?.let { " - nearest standable ${it.x},${it.z},${it.height}" } ?: " - nothing standable within 6")
            } else {
                p.moveTo(target)
                TestLib.add(p, "tp ${target.x},${target.z},${target.height}")
                "tp ${p.username} -> ${target.x},${target.z},${target.height}"
            }
        }
    }
}
