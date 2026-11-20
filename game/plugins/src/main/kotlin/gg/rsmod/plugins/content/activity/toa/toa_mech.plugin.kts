import gg.rsmod.game.model.priv.Privilege

/**
 * N606 12-11 (eigenaar 'tooling van grafisch flink uitbreiden'): '::toamech <mechanic>' starts ONE special of the boss the player is
 * fighting right now, so the photobook script (work/claude-scripts/toa_photobook.sh) can capture every mechanic close-up instead of
 * hoping a full run shows it. Test server only. '::toamech' alone lists the mechanics of the current boss.
 */
on_command("toamech", Privilege.ADMIN_POWER) {
    if (!gg.rsmod.plugins.content.activity.toa.ToaTestGate.allowed(player)) return@on_command
    gg.rsmod.plugins.content.activity.toa.ToaTestGate.jumped(player, "toamech ${player.getCommandArgs().joinToString(" ")}")
    val s = gg.rsmod.plugins.content.activity.toa.bosses.ToaEncounter.forPlayer(player)
    val arg = player.getCommandArgs().firstOrNull()?.lowercase()
    val options: Map<String, () -> Unit> = when (s) {
        is gg.rsmod.plugins.content.activity.toa.bosses.ZebakCombat.Session -> mapOf(
            "roar" to { s.specialDirector(s, gg.rsmod.plugins.content.activity.toa.bosses.ZebakCombat.Special.ROAR) },
            "waves" to { s.specialDirector(s, gg.rsmod.plugins.content.activity.toa.bosses.ZebakCombat.Special.WAVES) })
        is gg.rsmod.plugins.content.activity.toa.bosses.BabaCombat.Session -> mapOf(
            // 13-11 round 3: like the real 66%/33% trigger (BabaCombat.onTick) - without rolling=true rollingStones skips every row,
            // so the photobook showed no boulders at all.
            "rolling" to { if (!s.rolling) { s.rollingStage++; s.rolling = true; s.newPhase(); s.rollingDirector(s) } },
            "special" to { s.specialDirector(s) },
            // N610 14-11: the ToA Extended shockwave (gfx 1447) and sarcophagus (projectile 2246) overlays on demand
            "shockwave" to { s.shockwaveSpecial() }, "coffin" to { player.message("toamech coffin: " + s.testOpenCoffin(player)) })
        is gg.rsmod.plugins.content.activity.toa.bosses.KephriCombat.Session -> mapOf(
            "dung" to { s.dungDirector(s, s.players()) }, "special" to { s.specialDirector(s) },
            // 13-11 round 3: the director alone runs with shieldBreaks 0 -> swarmCount 0 (no swarms, no overlords in the photobook).
            // The real path is a broken shield: breakShield() (shieldBreaks++, overlords, then the recharge director).
            "recharge" to {
                check(s.phase == gg.rsmod.plugins.content.activity.toa.bosses.KephriCombat.Phase.SHIELD) { "geen schildfase (fase ${s.phase})" }
                s.breakShield()
            })
        is gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.Session -> mapOf(
            "special" to { s.specialDirector(s) },
            // N609 13-11: 'special' picks Memory Blast or Trail at random (solo) - the photobook / gelijke-camera shots need each one on
            // demand (Kaoz 1730 s = Memory Blast). Same pattern rule as defaultSpecial (4 flashes, adjacent quadrants).
            "memory" to {
                val qs = gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.Quadrant.values()
                val pattern = mutableListOf(qs.random())
                repeat(3) { pattern += qs.filter { q -> gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.adjacent(pattern.last(), q) }.random() }
                s.memoryBlast(pattern, 9, 22)
            },
            "trail" to {
                s.trail(s.players().associateWith { gg.rsmod.game.model.Tile(it.tile) },
                    gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.trailDuration(false), 1,
                    gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.STATS.speed, 22)
            },
            // 13-11 round 3: both directors are per-tick - orbs only fire when tick % speed == 0 and the shadow charge needs shadows
            // plus speed*6 calls, so one '::toamech' call mostly showed nothing. Same orb as defaultOrbs, and a full shadow charge.
            "orbs" to {
                val d = gg.rsmod.game.model.Direction.values().filter { it != gg.rsmod.game.model.Direction.NONE }.random()
                s.unstableOrb(s.centre.transform(-d.getDeltaX() * 8, -d.getDeltaZ() * 8), d, 17, 1, 22)
            },
            // TOA-1911 nachtrun 20-11: the real final stand (Akkha enrage: orbDirector runs every tick), to measure the orb density next to OSRS.
            "enrage" to { s.enterEnrage() },
            "shadow" to {
                if (s.shadowStage == 0) s.restoreShadows()
                repeat(gg.rsmod.plugins.content.activity.toa.bosses.AkkhaCombat.STATS.speed * 6) { s.shadowChargeDirector(s) }
            })
        is gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Session -> mapOf(
            "p1" to { s.p1Director(s) }, "p2" to { s.p2Director(s) },
            "ufo" to { s.ufos(gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Warden.ELIDINIS) }, "ufo2" to { s.ufos(gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Warden.TUMEKEN) },
            // N609 13-11: each P2 obelisk special on demand (wiki Strategies: Converging / Windmill / Lightning Skull; Kaoz 2243-2248 s).
            "converging" to { s.defaultObelisk(gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.ObeliskSpecial.CONVERGING) },
            "windmill" to { s.defaultObelisk(gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.ObeliskSpecial.WINDMILL) },
            "lightning" to { s.defaultObelisk(gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.ObeliskSpecial.LIGHTNING) }, "phase2" to { s.startPhaseTwo(); check(s.phase == gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Phase.P2) { "niet in P2 (fase ${s.phase})" } },
            "core" to { s.exposeCore(); check(s.phase == gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Phase.CORE) { "geen kern (fase ${s.phase}); alleen vanuit P2" } },
            // 13-11 round 3: startPhaseThree() silently does nothing outside Phase.CORE (the core window had closed -> back to P2), so
            // the photobook took 'P3' shots of P2 and siphon then failed. Go through the core first and fail loudly if P3 is not reached.
            "phase3" to {
                if (s.phase == gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Phase.P2) s.exposeCore()
                s.startPhaseThree()
                check(s.phase == gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Phase.P3) { "niet in P3 (fase ${s.phase})" }
            },
            // siphons start by the game itself below 80% in P3 (WardensCombat onTick) - calling the director outside Phase.SIPHONS
            // fails its require (13-11 photobook test): drop the hp just under the threshold instead.
            "siphon" to {
                when (s.phase) {
                    gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Phase.P3 -> s.npc.setCurrentLifepoints(s.npc.getMaximumLifepoints() * 79 / 100)
                    gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Phase.SIPHONS -> Unit // already running
                    else -> error("sifons alleen in P3 (fase ${s.phase}); eerst 'toamech phase3'")
                }
            },
            "enrage" to { s.enterEnrage() },
            "slam-east" to { s.slamDirector(s, gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Slam.EAST) },
            "slam-west" to { s.slamDirector(s, gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Slam.WEST) },
            "slam-centre" to { s.slamDirector(s, gg.rsmod.plugins.content.activity.toa.bosses.WardensCombat.Slam.CENTRE) })
        else -> emptyMap()
    }
    val action = arg?.let { options[it] }
    if (action == null) { player.message("toamech ${s?.let { it::class.java.name.substringAfterLast('.') } ?: "geen baas"}: ${options.keys.joinToString(" ")}"); return@on_command }
    // a failing test action must not look like a server crash: report it (the photobook logs it per mechanic)
    val error = runCatching { action() }.exceptionOrNull()
    player.message(if (error == null) "toamech $arg gestart" else "toamech $arg FAALDE: ${error::class.java.simpleName} ${error.message}")
}

/** N606: '::toaskip' marks the player's RUNNING puzzle room as cleared (test server only) so the photobook can reach every boss
 *  (enterBoss needs the path's challenge cleared). Same path as a real completion: ToaRoomRun.clear. */
on_command("toaskip", Privilege.ADMIN_POWER) {
    if (!gg.rsmod.plugins.content.activity.toa.ToaTestGate.allowed(player)) return@on_command
    gg.rsmod.plugins.content.activity.toa.ToaTestGate.jumped(player, "toaskip")
    val raid = gg.rsmod.plugins.content.activity.toa.ToaLobby.of(world).raid(player.uid) ?: return@on_command
    val run = raid.progress.current ?: return@on_command
    player.message("toaskip ${run.key}: ${run.clear(world.currentCycle)}")
}

/** N610: '::toascab [addition]' in the Path of Scabaras: every puzzle but the matching puzzle solved ('addition': up to the tablet)
 *  (ToA Extended matching-tile test). Test server, and on live only check78/check79 (ToaTestGate). */
on_command("toascab", Privilege.ADMIN_POWER) {
    if (!gg.rsmod.plugins.content.activity.toa.ToaTestGate.allowed(player)) return@on_command
    gg.rsmod.plugins.content.activity.toa.ToaTestGate.jumped(player, "toascab")
    val room = gg.rsmod.plugins.content.activity.toa.puzzles.ToaPuzzles.of(player) as? gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasRoom
    val args = player.getCommandArgs().map { it.lowercase() }
    if (args.firstOrNull() == "obelisk") {
        player.message("toascab ${room?.testObelisks(player, args.getOrNull(1)?.toIntOrNull() ?: 1, "wrong" in args) ?: "niet in de Scabaras-kamer"}")
        return@on_command
    }
    val upToAddition = args.firstOrNull() == "addition"
    // the full answer (buttons) is longer than one game message (GamePacketEncoder 'Message length 260 too long'): split it
    val text = room?.testFinishAllButMatching(upToAddition) ?: "niet in de Scabaras-kamer"
    text.chunked(240).forEach { player.message("toascab $it") }
}

/** N606: '::toawardens' clears every path (puzzle + boss, as ::toaskip would) and enters the Wardens - photobook route to the last
 *  room without playing 4 paths. Test server only. */
on_command("toawardens", Privilege.ADMIN_POWER) {
    if (!gg.rsmod.plugins.content.activity.toa.ToaTestGate.allowed(player)) return@on_command
    gg.rsmod.plugins.content.activity.toa.ToaTestGate.jumped(player, "toawardens")
    val raid = gg.rsmod.plugins.content.activity.toa.ToaLobby.of(world).raid(player.uid) ?: return@on_command
    for (path in gg.rsmod.plugins.content.activity.toa.ToaPath.values()) {
        if (path in raid.progress.completedPaths) continue
        raid.enterChallenge(player, path); raid.progress.current?.clear(world.currentCycle)
        raid.enterBoss(player, path); raid.progress.current?.clear(world.currentCycle)
    }
    player.message("toawardens paths=${raid.progress.completedPaths} unlocked=${raid.progress.wardensUnlocked} enter=${raid.enterWardens(player)}")
}

/**
 * N606 13-11 (eigenaar 'bouw de loopbaarheidskaart'): command-port 'toawalk <name> [radius]' - LOOPBAARHEID van de kamer waar de speler
 * staat, op de ECHTE server-botsing: flood fill (canStep, 8 richtingen) vanaf de speler; daarna elk KLIKBAAR object (def met een optie)
 * en elke npc met opties: bereikbaar = een tegel van zijn voetafdruk of de ring eromheen is bereikt. Onbereikbaar = de speler krijgt
 * 'I can't reach that!' -> 'toawalk ONBEREIKBAAR ...'. Plus het aantal bereikte tegels en vrije-maar-afgesloten tegels (eilanden).
 * Alleen lezen; alleen testserver (rsps.testServer). Script: work/claude-scripts/toa_walkmap.sh (alle kamers, rapport).
 */
gg.rsmod.game.CommandServerCommands.handlers["toawalk"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val r = a.getOrNull(1)?.toIntOrNull() ?: 40
    if (System.getProperty("rsps.testServer") != "true" || p == null) "usage: toawalk <name> [radius] (testserver)" else {
        val start = gg.rsmod.game.model.Tile(p.tile)
        // passages a player goes THROUGH with an option (ToA barriers 'Pass', doors): their tiles count as walkable (13-11: the
        // Crondis entry barrier stopped the flood after 24 tiles although every player passes it)
        // only the in-room ToA barriers ("Pass"); room exits ("Enter"/"Leave") lead into other areas and made the flood spill (5212 tiles)
        // + "jump-to": Scabaras obelisk area lies behind the Platform jump (walkmap-0040: 7 false alarms)
        val through = setOf("pass", "jump-to")
        val passTiles = HashSet<gg.rsmod.game.model.Tile>()
        for (dx in -r..r) for (dz in -r..r) {
            val t = start.transform(dx, dz)
            w.chunks.get(t, createIfNeeded = false)?.getEntities<gg.rsmod.game.model.entity.GameObject>(t,
                gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)?.forEach { o ->
                val d = runCatching { w.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, o.id) }.getOrNull() ?: return@forEach
                if (d.options.any { it != null && it.lowercase() in through }) {
                    val (sx, sz) = if (o.rot % 2 == 1) d.length to d.width else d.width to d.length
                    for (ix in 0 until sx) for (iz in 0 until sz) passTiles += o.tile.transform(ix, iz)
                }
            }
        }
        val reached = HashSet<gg.rsmod.game.model.Tile>()
        val queue = ArrayDeque<gg.rsmod.game.model.Tile>()
        reached += start; queue += start
        val dirs = gg.rsmod.game.model.Direction.values().filter { it != gg.rsmod.game.model.Direction.NONE }
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            for (d in dirs) {
                val n = t.step(d)
                if (!n.isWithinRadius(start, r) || n in reached) continue
                if (w.chunks.get(n, createIfNeeded = false) == null) continue // unloaded = no map here
                if (w.collision.canStep(t, d) || n in passTiles || (t in passTiles && !w.collision.isClipped(n))) { reached += n; queue += n }
            }
        }
        fun near(sw: gg.rsmod.game.model.Tile, sx: Int, sz: Int) =
            (-1..sx).any { dx -> (-1..sz).any { dz -> sw.transform(dx, dz) in reached } }
        val bad = ArrayList<String>(); var locs = 0; var npcs = 0
        val seen = HashSet<gg.rsmod.game.model.entity.GameObject>()
        for (dx in -r..r) for (dz in -r..r) {
            val t = start.transform(dx, dz)
            w.chunks.get(t, createIfNeeded = false)?.getEntities<gg.rsmod.game.model.entity.GameObject>(t,
                gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)?.forEach { o ->
                if (!seen.add(o)) return@forEach
                val def = runCatching { w.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, o.id) }.getOrNull() ?: return@forEach
                val ops = def.options.filterNotNull().filter { it.isNotBlank() && it != "null" }
                if (ops.isEmpty()) return@forEach
                locs++
                val (sx, sz) = if (o.rot % 2 == 1) def.length to def.width else def.width to def.length
                if (!near(o.tile, sx, sz)) bad += "loc ${o.id} '${def.name}' ${ops} at=${o.tile.x},${o.tile.z},${o.tile.height}"
            }
        }
        w.npcs.forEach { n ->
            if (n.tile.height != start.height || !n.tile.isWithinRadius(start, r) || n.isDead()) return@forEach
            val ops = n.def.options.filterNotNull().filter { it.isNotBlank() && it != "null" }
            if (ops.isEmpty()) return@forEach
            npcs++
            if (!near(n.tile, n.getSize(), n.getSize())) bad += "npc ${n.id} '${n.def.name}' ${ops} at=${n.tile.x},${n.tile.z},${n.tile.height}"
        }
        var islands = 0
        for (dx in -r..r) for (dz in -r..r) {
            val t = start.transform(dx, dz)
            if (t !in reached && w.chunks.get(t, createIfNeeded = false) != null && !w.collision.isClipped(t) &&
                dirs.any { d -> w.collision.canStep(t, d) }) islands++
        }
        "toawalk at=${start.x},${start.z},${start.height} r=$r bereikt=${reached.size} klikbaar locs=$locs npcs=$npcs onbereikbaar=${bad.size} " +
            "vrij-maar-afgesloten=$islands" + bad.take(30).joinToString("") { " | ONBEREIKBAAR $it" }
    }
}
