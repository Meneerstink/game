package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.message.impl.MapAnimMessage
import gg.rsmod.game.message.impl.MapProjAnimMessage
import gg.rsmod.game.message.impl.SoundAreaMessage
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.BreachTrace
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.service.GameService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.getLastHitBy
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Breach OSRS-conformance run (owner 2026-09-27: "test literally every breach monster - pathing, single behaviour, multi
 * behaviour, everything"). Started only with `-Drsps.breachConformance=true` (never in the owner's start; the agent's headless
 * server). Four headless players with a packet recorder each (what their client would receive: area sounds, direct sounds,
 * projectiles, tile graphics) plus the [BreachTrace] lines (update blocks per viewer, attacks, targets, hits, steps, rulings)
 * go through scenarios for every spawnable breach monster on the single-way AND the multi-way site of a real breach:
 *
 * 1. fight A: first attack, animation asked vs played, every player in view receives the animation, sounds reach A and the
 *    observer O, projectiles reach A and O, multi-target sections hit exactly the players the OSRS rule allows (multi: all
 *    in the area, single: only the target), every target gets its hit;
 * 2. B attacks the monster while it fights A: multi - does it switch (recorded; OSRS documents switching only for Zemouregal),
 *    single - B must be allowed (OSRS Wiki: "Multiple players can attack the same boss ... in a single-way combat zone");
 * 3. C attacks A while the monster attacks A: single - refused, multi - allowed;
 * 4. A steps diagonally away: the monster's first step must be diagonal (OSRS npc pathing "diagonally ... then straight");
 * 5. A leaves the leash: the monster lets go and walks home;
 * 6. death: death animation reaches every player in view, the monster leaves the world.
 *
 * Invariants checked every tick: single-way never two monsters on one player; no player within a sound's radius misses it
 * (`zone.skip`). Results: `launcher-logs/breach-conformance-<stamp>.txt` (one row per monster x site, PASS/FAIL per check) and
 * the full trace next to it.
 */
object BreachConformance {
    const val PROPERTY = "rsps.breachConformance"

    fun enabled(): Boolean = System.getProperty(PROPERTY) == "true"

    private const val LIFE = 20000

    /** Everything one bot's client would have received, as `t=<cycle> <kind> k=v` lines. */
    class Recorder {
        val lines = java.util.concurrent.CopyOnWriteArrayList<String>()
    }

    private val recorders = HashMap<String, Recorder>()

    private fun bot(
        world: World,
        name: String,
        tile: Tile,
    ): Client {
        val rec = Recorder().also { recorders[name] = it }
        val channel =
            EmbeddedChannel(
                object : ChannelOutboundHandlerAdapter() {
                    override fun write(
                        ctx: ChannelHandlerContext,
                        msg: Any,
                        promise: ChannelPromise,
                    ) {
                        val t = BreachTrace.cycle
                        when (msg) {
                            is SoundAreaMessage -> rec.lines += "t=$t areasound id=${msg.id} radius=${msg.radius}"
                            is SynthSoundMessage -> rec.lines += "t=$t sound id=${msg.sound}"
                            is MapProjAnimMessage -> rec.lines += "t=$t projectile gfx=${msg.gfx}"
                            is MapAnimMessage -> rec.lines += "t=$t tilegfx id=${msg.id}"
                            is gg.rsmod.game.message.impl.LocAddChangeMessage -> rec.lines += "t=$t locadd id=${msg.id} tile=${msg.tile} settings=${msg.settings}"
                            is gg.rsmod.game.message.impl.LocDelMessage -> rec.lines += "t=$t locdel tile=${msg.tile} settings=${msg.settings}"
                        }
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
        check(world.register(bot)) { "conformance player $name not registered" }
        bot.login()
        bot.timers.remove(gg.rsmod.game.model.timer.SAVE_TIMER)
        gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.forfeit(bot)
        bot.setCurrentLifepoints(LIFE)
        return bot
    }

    // ---- trace parsing ----------------------------------------------------------------------------------------------------

    private val trace = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun tOf(line: String): Int = line.substringAfter("t=").substringBefore(' ').toIntOrNull() ?: -1

    private fun cat(line: String): String = line.substringAfter(' ').substringBefore(' ')

    private fun kv(
        line: String,
        key: String,
    ): String? = Regex("(?:^| )$key=(\\[[^\\]]*\\]|\\S+)").find(line)?.groupValues?.get(1)

    private fun list(value: String?): List<String> = value?.removePrefix("[")?.removeSuffix("]")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun npcTag(npc: Npc) = "npc=${npc.id}#${npc.index}"

    // ---- run ------------------------------------------------------------------------------------------------------------

    fun start(world: World) {
        world.queue {
            wait(20)
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val report = ArrayList<String>()
            BreachTrace.sink = { trace += it }
            val bots = ArrayList<Client>()
            val dir = File("../../launcher-logs").takeIf { it.isDirectory } ?: File("C:/RSPS/launcher-logs")
            val reportFile = File(dir, "breach-conformance-$stamp.txt")
            val traceFile = File(dir, "breach-conformance-$stamp.trace.log")
            var flushed = 0
            fun say(s: String) {
                report += s
                logger.info("BREACHCONF {}", s)
                // written as it goes, so a run that is stopped early still leaves its rows and trace behind
                reportFile.appendText(s + "\n")
                val end = trace.size
                if (end > flushed) {
                    traceFile.appendText(trace.subList(flushed, end).joinToString("\n") + "\n")
                    flushed = end
                }
            }
            try {
                DeadmanBreach.close(world)
                val opened = DeadmanBreach.open(world, DeadmanBreach.Kind.LOCALISED) ?: error("no localised breach opened")
                val sites = opened.sites.sortedBy { if (it.multi) 0 else 1 }
                say("sites: " + sites.joinToString(" | ") { "${it.name} multi=${it.multi} landing=${it.landing.size}" })
                // 0. the breach event itself (OSRS Wiki "Breach (scenery)", "Deadman Mode" #Breaches): two sites (single + multi),
                // spawner graphics, projectiles to the landing, a Boss Spawn there, monsters at most 9 tiles from a spawner, never
                // overlapping, and nothing left after closing
                run {
                    val watcher = bot(world, "BcEvent", sites.first().spawners.first().transform(2, 0))
                    bots += watcher
                    wait(3) // the watcher's map must be loaded before the projectiles fly
                    val t0 = BreachTrace.cycle
                    repeat(DeadmanBreach.LOCALISED_MAX_NPCS) { sites.forEach { s -> DeadmanBreach.fire(world, s) } }
                    // overlap is judged right after landing (excluded bosses may walk through others afterwards, like in OSRS)
                    var overlapsAtLanding = 0
                    val overlapPairs = LinkedHashSet<String>()
                    repeat(40) {
                        wait(1)
                        val now = sites.flatMap { it.npcs }.filter { it.id !in BreachMonsters.COLLISION_EXCLUDED && world.npcs.contains(it) }
                        now.forEach { a -> now.forEach { b ->
                            if (a.index < b.index && a.tile.height == b.tile.height && a.tile.x < b.tile.x + b.getSize() && b.tile.x < a.tile.x + a.getSize() && a.tile.z < b.tile.z + b.getSize() && b.tile.z < a.tile.z + a.getSize()) {
                                overlapPairs += "${a.id}#${a.index}@${a.tile.x},${a.tile.z}+${b.id}#${b.index}@${b.tile.x},${b.tile.z}(t=${BreachTrace.cycle})"
                            }
                        } }
                        overlapsAtLanding = overlapPairs.size
                    }
                    if (overlapPairs.isNotEmpty()) say("event overlap pairs: " + overlapPairs.take(6).joinToString(" "))
                    val spawned = sites.flatMap { it.npcs }
                    val far =
                        spawned.filter { n ->
                            val s = sites.first { it.npcs.contains(n) }
                            // the LANDING tile (2026-09-28: monsters now wander their walk radius around it, so the current tile may be further)
                            s.spawners.none { sp -> maxOf(0, sp.x - (n.spawnTile.x + n.getSize() - 1), n.spawnTile.x - sp.x).coerceAtLeast(maxOf(0, sp.z - (n.spawnTile.z + n.getSize() - 1), n.spawnTile.z - sp.z)) <= DeadmanBreach.LOCAL_SPAWN_RADIUS }
                        }
                    val overlaps = overlapsAtLanding
                    val rec = recorders.getValue("BcEvent").lines
                    fun got(kind: String, id: Int) = rec.count { it.contains(" $kind ") && (it.contains("id=$id ") || it.endsWith("id=$id") || it.contains("gfx=$id")) && tOf(it) >= t0 }
                    val projectiles = got("projectile", BreachMonsters.GFX_BREACH_PROJECTILE)
                    val bossIn = got("tilegfx", DeadmanBreach.GFX_BOSS_SPAWN)
                    val bossOut = got("tilegfx", DeadmanBreach.GFX_BOSS_DESPAWN)
                    // every Boss Spawn loc the watcher was sent must also be deleted again (a loc left in the client = "never disappears")
                    val bossAdds = rec.filter { it.contains(" locadd id=${DeadmanBreach.BOSS_SPAWN_LOC} ") && tOf(it) >= t0 }.map { kv(it, "tile") }
                    val bossDels = rec.filter { it.contains(" locdel ") && tOf(it) >= t0 }.map { kv(it, "tile") }
                    val bossLeft = bossAdds.count { tile -> bossDels.none { it == tile } }
                    val soundsIn = got("areasound", DeadmanBreach.SFX_BOSS_SPAWN)
                    val soundsOut = got("areasound", DeadmanBreach.SFX_BOSS_DESPAWN)
                    val singleMulti = sites.map { it.multi }.toSet() == setOf(true, false)
                    val removed = DeadmanBreach.close(world)
                    wait(3)
                    val despawnGfx = got("tilegfx", DeadmanBreach.GFX_BREACH_DESPAWN)
                    val left = DeadmanBreach.live.size + sites.sumOf { it.objects.size + it.bossSpawns.size + it.pending.size }
                    val fails =
                        listOfNotNull(
                            "one-single-one-multi".takeIf { !singleMulti },
                            "spawned=${spawned.size}".takeIf { spawned.isEmpty() },
                            "further-than-9=${far.size}".takeIf { far.isNotEmpty() },
                            "overlapping=$overlaps".takeIf { overlaps > 0 },
                            "bossSpawnNeverDeleted=$bossLeft".takeIf { bossLeft > 0 },
                            "bossSpawnSounds=$soundsIn/$soundsOut".takeIf { soundsIn == 0 || soundsOut == 0 },
                            "spawnerProjectiles=0".takeIf { projectiles == 0 },
                            "left-after-close=$left".takeIf { left > 0 },
                        )
                    say(
                        "event | localised | ${if (fails.isEmpty()) "PASS" else "FAIL(${fails.size})"} | sites single+multi=$singleMulti | spawned=${spawned.size} " +
                            "(max ${DeadmanBreach.LOCALISED_MAX_NPCS} per site) | furtherThan9=${far.size} | overlapping=$overlaps | watcher got projectiles=$projectiles " +
                            "bossSpawnGfx=$bossIn bossDespawnGfx=$bossOut bossLocAdd=${bossAdds.size} bossLocLeft=$bossLeft bossSounds=$soundsIn/$soundsOut breachDespawnGfx=$despawnGfx | close removed=$removed left=$left ${fails.joinToString(" ")}",
                    )
                    world.unregister(watcher)
                    bots -= watcher
                }
                DeadmanBreach.close(world)
                val only = System.getProperty("rsps.breachConformance.ids")?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
                say(
                    "columns: monster | site | attacks firstAttackTick | anim asked->played (cacheOk) | animSeenBy (A,B,C,O swings seen/total) | " +
                        "aoe (section: expected -> got) | hitsOnAllTargets | soundsHeard A/O (per swing) | projectilesSeen A/O | " +
                        "B-attacks (multi: switch? / single: allowed?) | C-PKs-A (single refused / multi allowed) | diagonalFirstStep | " +
                        "leash (released, homeGap) | death (animSeenBy, removed) | invariants",
                )
                var n = 0
                // The event above uses whatever sites the breach picked; the per-monster phases always use the same two roomy
                // reference sites, so a cramped random site (e.g. "West within Zanaris") cannot turn size-5 monsters into SKIPPED rows.
                val phaseSites =
                    listOf("West of the Ourania Altar", "North of Al Kharid").mapNotNull { DeadmanBreach.localisedSite(world, it) }
                        .takeIf { it.size == 2 } ?: sites
                for (site in phaseSites) {
                    val arena = site.landing.toHashSet()
                    val a = site.landing.minByOrNull { it.getDistance(site.spawners.first()) }!!
                    fun near(
                        from: Tile,
                        dx: Int,
                        dz: Int,
                    ): Tile = from.transform(dx, dz).takeIf { it in arena } ?: site.landing.filter { it != from }.minByOrNull { it.getDistance(from.transform(dx, dz)) }!!
                    val tb = near(a, -1, 0)
                    val tc = near(a, -2, 0)
                    val to = near(a, -6, 0)
                    val names = listOf("A", "B", "C", "O").map { "Bc${n}${it}" }
                    n++
                    val botA = bot(world, names[0], a)
                    val botB = bot(world, names[1], tb)
                    val botC = bot(world, names[2], tc)
                    val botO = bot(world, names[3], to)
                    val mine = listOf(botA, botB, botC, botO)
                    bots += mine
                    wait(3)
                    say("site '${site.name}' multi=${site.multi} (A.isMulti=${a.isMulti(world)}) A=${a} B=${tb} C=${tc} O=${to}")
                    for (m in BreachMonsters.SPAWNABLE.filter { only == null || it.id in only }) {
                        try {
                            say(runMonster(this, world, site, m, mine, a, arena))
                        } catch (e: Exception) {
                            say("${m.id} ${m.name} | ${if (site.multi) "multi" else "single"} | ERROR ${e}")
                            logger.error("BREACHCONF monster {} failed", m.id, e)
                        }
                    }
                    try {
                        say(runStaleAttacker(this, world, site, mine, a, arena))
                    } catch (e: Exception) {
                        say("stale-attacker | ${if (site.multi) "multi" else "single"} | ERROR $e")
                        logger.error("BREACHCONF stale-attacker phase failed", e)
                    }
                    try {
                        say(runFamiliar(this, world, site, mine, a, arena))
                    } catch (e: Exception) {
                        say("familiar | ${if (site.multi) "multi" else "single"} | ERROR $e")
                        logger.error("BREACHCONF familiar phase failed", e)
                    }
                    mine.forEach { world.unregister(it) }
                    bots -= mine
                }
            } catch (e: Exception) {
                logger.error("BREACHCONF failed: {}", e.toString(), e)
                say("FAILED $e")
            } finally {
                BreachTrace.sink = null
                bots.forEach { world.unregister(it) }
                DeadmanBreach.close(world)
                say("done")
                recorders.forEach { (name, r) -> File(dir, "breach-conformance-$stamp.recv-$name.log").writeText(r.lines.joinToString("\n") + "\n") }
                logger.info("BREACHCONF done: {} rows, {} trace lines -> {}", report.size, trace.size, dir.absolutePath)
                // The headless test server saves nothing (its bots are never saved) and simply ends (tools/Run-BreachConformance.ps1).
                if (System.getProperty("$PROPERTY.exit") == "true") Runtime.getRuntime().halt(0)
            }
        }
    }

    /**
     * Owner live test 2026-09-28 (Justiciar never attacked, TzTok-Jad-Rek could not be attacked in single-way): a monster that was
     * fighting A is removed (breachclose) and a new one spawns beside A at once. The removed one must not keep A "under attack":
     * the new monster attacks A and A can attack it, both within a few ticks.
     */
    private suspend fun runStaleAttacker(
        q: gg.rsmod.game.model.queue.QueueTask,
        world: World,
        site: DeadmanBreach.Site,
        bots: List<Client>,
        home: Tile,
        arena: Set<Tile>,
    ): String {
        val (a, b, c, o) = bots
        val siteName = if (site.multi) "multi" else "single"
        DeadmanBreach.close(world)
        bots.forEach { it.resetInteractions(); it.setCurrentLifepoints(LIFE) }
        a.moveTo(home)
        listOf(b, c, o).forEachIndexed { i, p -> p.moveTo(Tile(home.x - 40 - i, home.z, home.height)) }
        q.wait(18)
        fun spotFor(id: Int): Tile? {
            val size = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, id)?.size ?: 1
            return arena.filter { t ->
                val g = maxOf(0, t.x - home.x, home.x - (t.x + size - 1)).coerceAtLeast(maxOf(0, t.z - home.z, home.z - (t.z + size - 1)))
                t.x > home.x && g == 1 && DeadmanBreach.fits(world, t, size)
            }.minByOrNull { it.getDistance(home) }
        }
        val first = 14446 // Porazdir, as in the owner's session
        val next = 14447 // Justiciar Zachariah
        val s1 = spotFor(first) ?: return "stale-attacker | $siteName | SKIPPED no spot"
        val m1 = DeadmanBreach.spawnMonster(world, first, s1, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
        m1.attack(a)
        var hitA = false
        repeat(20) { q.wait(1); a.setCurrentLifepoints(LIFE); if (a.getLastHitBy() === m1) hitA = true }
        DeadmanBreach.close(world) // the owner's breachclose while it was still fighting him
        q.wait(1)
        val s2 = spotFor(next) ?: return "stale-attacker | $siteName | SKIPPED no spot 2"
        val m2 = DeadmanBreach.spawnMonster(world, next, s2, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
        val t2 = trace.size
        var m2Swung = -1
        repeat(12) { tick ->
            q.wait(1)
            a.setCurrentLifepoints(LIFE)
            if (m2Swung < 0 && trace.subList(t2, trace.size).any { cat(it) == "attack.swing" && it.contains(npcTag(m2)) }) m2Swung = tick + 1
        }
        // and A may attack it (no "single-attacker-under-attack" because of the removed monster)
        val t3 = trace.size
        a.attack(m2)
        q.wait(3)
        val aRuling = trace.subList(t3, trace.size).lastOrNull { cat(it) == "single" && kv(it, "attacker") == a.username }?.let { kv(it, "result") } ?: "no-ruling"
        a.resetInteractions()
        if (world.npcs.contains(m2)) world.remove(m2)
        val fails = ArrayList<String>()
        if (!hitA) fails += "setup:first-monster-never-hit-A"
        if (m2Swung < 0) fails += "new-monster-never-attacked"
        if (aRuling.startsWith("refused")) fails += "A-refused:$aRuling"
        return "stale-attacker | $siteName | ${if (fails.isEmpty()) "PASS" else "FAIL(${fails.size})"} | firstHitA=$hitA newMonsterFirstSwingAfter=${m2Swung}t A->new=$aRuling ${fails.joinToString(" ")}"
    }

    /**
     * Summoning at a breach (owner 2026-09-28 "kijk ook of summoning goed werkt op breaches"). A real Steel titan summoned by bot A
     * fights a Giant goblin that is frozen out of A's reach, so every point of damage on it comes from the familiar:
     *  - multi: the attack command is accepted, the familiar hits, and the damage is credited to A in the breach ledger (loot,
     *    emblem, points); a PvP-protected owner's familiar cannot be ordered onto it;
     *  - single: the command is refused ("cannot fight unless it is in a multi-way combat area") and nothing is credited.
     */
    private suspend fun runFamiliar(
        q: gg.rsmod.game.model.queue.QueueTask,
        world: World,
        site: DeadmanBreach.Site,
        bots: List<Client>,
        home: Tile,
        arena: Set<Tile>,
    ): String {
        val (a, b, c, o) = bots
        val siteName = if (site.multi) "multi" else "single"
        DeadmanBreach.close(world)
        bots.forEach { it.resetInteractions(); it.setCurrentLifepoints(LIFE) }
        a.moveTo(home)
        listOf(b, c, o).forEachIndexed { i, p -> p.moveTo(Tile(home.x - 40 - i, home.z, home.height)) }
        q.wait(18)
        val pouch = gg.rsmod.plugins.content.skills.summoning.SummoningPouchData.STEEL_TITAN
        a.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.SUMMONING, 99)
        a.inventory.add(pouch.pouch, 1)
        val summoned = gg.rsmod.plugins.content.skills.summoning.Familiar.summon(a, pouch)
        q.wait(3)
        val familiar = gg.rsmod.plugins.content.skills.summoning.Familiar.current(a) ?: return "familiar | $siteName | FAIL summon=$summoned"
        val goblin = 14450
        val size = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, goblin)?.size ?: 1
        val spot =
            arena.filter { t ->
                val g = maxOf(0, t.x - home.x, home.x - (t.x + size - 1)).coerceAtLeast(maxOf(0, t.z - home.z, home.z - (t.z + size - 1)))
                t.x > home.x && g in 4..6 && DeadmanBreach.fits(world, t, size) && world.collision.raycast(t, home, projectile = true)
            }.minByOrNull { it.getDistance(home) }
        if (spot == null) {
            gg.rsmod.plugins.content.skills.summoning.Familiar.dismiss(a)
            return "familiar | $siteName | SKIPPED no spot"
        }
        val npc = DeadmanBreach.spawnMonster(world, goblin, spot, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
        npc.freeze(200) {}
        q.wait(2)
        a.resetInteractions()

        // 1. a PvP-protected owner may not send his familiar onto a breach monster
        // the bots forfeit their protection at creation (bot()); give A a live one for this check only
        a.attr.remove(gg.rsmod.game.model.attr.PROTECTION_FORFEITED_ATTR)
        a.timers[gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER] = 500
        val protectedCommand = gg.rsmod.plugins.content.skills.summoning.FamiliarCombat.commandAttack(a, npc, silent = true)
        a.timers.remove(gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER)
        gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.forfeit(a)
        familiar.resetInteractions()
        q.wait(2)

        // 2. the real command
        val commanded = gg.rsmod.plugins.content.skills.summoning.FamiliarCombat.commandAttack(a, npc, silent = true)
        val hp0 = npc.getCurrentLifepoints()
        var familiarOnIt = 0
        repeat(30) {
            q.wait(1)
            a.setCurrentLifepoints(LIFE)
            familiar.setCurrentLifepoints(familiar.getMaximumLifepoints())
            if (familiar.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() === npc) familiarOnIt++
        }
        val lost = hp0 - npc.getCurrentLifepoints()
        val credited = DeadmanBreach.contribution(npc).damageOf(a.username)
        val aHitIt = a.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() === npc
        gg.rsmod.plugins.content.skills.summoning.Familiar.dismiss(a)
        if (world.npcs.contains(npc)) world.remove(npc)

        val fails = ArrayList<String>()
        if (protectedCommand) fails += "protected-owner-command-accepted"
        if (site.multi) {
            if (!commanded) fails += "command-refused"
            if (familiarOnIt == 0) fails += "familiar-never-on-target"
            if (lost <= 0) fails += "no-damage"
            if (credited != lost && !aHitIt) fails += "credit=$credited!=damage=$lost"
        } else {
            if (commanded) fails += "single-way-command-accepted"
            if (lost > 0 || credited > 0) fails += "single-way-damage=$lost credit=$credited"
        }
        return "familiar Steel titan | $siteName | ${if (fails.isEmpty()) "PASS" else "FAIL(${fails.size})"} | summoned=$summoned " +
            "protectedCommand=$protectedCommand command=$commanded ticksOnTarget=$familiarOnIt damage=$lost creditedToOwner=$credited ownerAttacked=$aHitIt " +
            fails.joinToString(" ")
    }

    private suspend fun runMonster(
        q: gg.rsmod.game.model.queue.QueueTask,
        world: World,
        site: DeadmanBreach.Site,
        m: BreachMonsters.Monster,
        bots: List<Client>,
        home: Tile,
        arena: Set<Tile>,
    ): String {
        val (a, b, c, o) = bots
        val siteName = if (site.multi) "multi" else "single"
        // reset: nothing of the previous monster may stay, every bot back on its tile, out of combat
        DeadmanBreach.close(world)
        val homes = listOf(home, b.tile, c.tile, o.tile)
        bots.forEachIndexed { i, p ->
            p.resetInteractions()
            p.setCurrentLifepoints(LIFE)
        }
        a.moveTo(home)
        q.wait(18)
        val size = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, m.id)?.size ?: 1
        val spot =
            arena.filter { t ->
                val g = maxOf(0, t.x - home.x, home.x - (t.x + size - 1)).coerceAtLeast(maxOf(0, t.z - home.z, home.z - (t.z + size - 1)))
                // east of A: the bots B, C and O stand west of A, never between the monster and its target
                t.x > home.x && g in 4..7 && DeadmanBreach.fits(world, t, size) && DeadmanBreach.canLeave(world, t, size) &&
                    // a clear walk and line of sight to A (otherwise it is a safespot - authentic OSRS, but then nothing gets tested)
                    world.collision.raycast(t, home, projectile = false) && world.collision.raycast(t, home, projectile = true) &&
                    // no other npc between the spot and A (a town npc in the way blocks it, as in OSRS, and the checks need a fight)
                    world.npcs.none { o -> o.tile.height == t.height && o.tile.x in minOf(home.x, t.x) - 1..maxOf(home.x, t.x + size) && o.tile.z in minOf(home.z, t.z) - 1..maxOf(home.z, t.z + size) } &&
                    bots.none { p -> p.tile.x in t.x until t.x + size && p.tile.z in t.z until t.z + size }
            }.minByOrNull { it.getDistance(home) } ?: return "${m.id} ${m.name} | $siteName | SKIPPED no free tile 4-7 away for size $size"
        val traceStart = trace.size
        val recStart = bots.associate { it.username to (recorders[it.username]?.lines?.size ?: 0) }
        val npc = DeadmanBreach.spawnMonster(world, m.id, spot, DeadmanBreach.LOCAL_SPAWN_RADIUS, test = true)
        val tag = npcTag(npc)
        npc.attack(a)
        val spawnT = BreachTrace.cycle
        var invariant = ""
        suspend fun ticks(count: Int) {
            repeat(count) {
                q.wait(1)
                bots.forEach { it.setCurrentLifepoints(LIFE) }
                if (!site.multi) {
                    // single-way invariant: never two breach monsters on one player
                    bots.forEach { p ->
                        val on = DeadmanBreach.live.count { it.getCombatTarget() === p && !it.isDead() }
                        if (on > 1 && !invariant.contains("pile")) invariant += " FAIL:pile-up(${p.username}=$on)"
                    }
                }
            }
        }
        // 1. fight A (B, C, O idle)
        ticks(40)
        val phase1End = trace.size
        val p1 = trace.subList(traceStart, phase1End).toList()
        val swings = p1.filter { cat(it) == "attack.swing" && it.contains(tag) }
        val attacks = swings.size
        val firstAttack = swings.firstOrNull()?.let { tOf(it) - spawnT } ?: -1
        val asked = swings.map { kv(it, "animAsked") }.distinct()
        val played = swings.map { kv(it, "animPlayed") }.distinct()
        val cacheOk = played.mapNotNull { it?.toIntOrNull() }.filter { it >= 0 }.all { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, it) != null }
        val animCol = "${asked.joinToString("/")}->${played.joinToString("/")} (${if (cacheOk) "ok" else "MISSING"})${if (asked != played) " FALLBACK" else ""}"
        // every player in view receives the attack animation on the swing's cycle (evaluated at the end: the last swing of this
        // phase is only synchronised after this tick's queue has run)
        val seen = {
            bots.joinToString(",") { p ->
                val got =
                    swings.count { s ->
                        val t = tOf(s)
                        val anim = kv(s, "animPlayed")
                        val all = trace.subList(traceStart, trace.size)
                        // an attack animation replaced on the same tick by a special's own animation (e.g. a summon) counts as sent
                        all.any { l -> cat(l) == "sync.block" && l.contains(tag) && kv(l, "viewer") == p.username && kv(l, "anim") == anim && tOf(l) in t..t + 1 } ||
                            all.any { l -> cat(l) == "anim.set" && l.contains(tag) && tOf(l) == t && kv(l, "replaces") == anim }
                    }
                "$got/$attacks"
            }
        }
        // multi-target sections: who SHOULD be hit (OSRS rule) vs who was
        val aoe =
            swings.filter { kv(it, "rule") != "single" }.groupBy { kv(it, "section") }.map { (section, group) ->
                val s = group.last()
                // every swing: multi-way hits exactly the players inside the attack's area at that tick, single-way only the target
                val bad =
                    group.count { sw ->
                        val got = list(kv(sw, "targets")).toSet()
                        val area = list(kv(sw, "area")).toSet()
                        if (site.multi) got != area else got.size != 1
                    }
                val sizes = group.map { list(kv(it, "targets")).size }.distinct().sorted()
                "$section:${kv(s, "rule")} ${if (site.multi) "area" else "target-only"} hit=${sizes.joinToString("/")}${if (bad == 0) "" else " FAIL($bad swings)"}"
            }.joinToString(" ").ifEmpty { "-" }
        // every target of every swing got a hit line (sections without hits - fireball specials - are skipped)
        val hitLines = p1.filter { cat(it) == "hit" && it.contains(tag) }
        // hitsplats per swing on A (OSRS Wiki: Cerberus "three hitsplats per attack", Jaguar warrior "21 (x4)")
        val swingsOnA = swings.count { list(kv(it, "targets")).contains(a.username) }
        val hitsOnA = hitLines.count { kv(it, "target") == a.username }
        val expectedHits = mapOf(14458 to 3, 14461 to 4)[m.id]
        val perSwing = if (swingsOnA > 0) hitsOnA.toDouble() / swingsOnA else 0.0
        val hitsCol = "%.1f/swing".format(perSwing) + if (expectedHits != null && kotlin.math.abs(perSwing - expectedHits) > 0.01) " FAIL(expected $expectedHits)" else ""
        val hitsOk =
            swings.all { s ->
                val t = tOf(s)
                list(kv(s, "targets")).all { name -> hitLines.any { h -> kv(h, "target") == name && tOf(h) in t..t + 1 } } ||
                    hitLines.none { tOf(it) in t..t + 1 }
            }
        fun recv(
            p: Player,
            kind: String,
            from: Int,
        ): List<String> {
            val lines = recorders[p.username]?.lines ?: return emptyList()
            val start = recStart[p.username] ?: 0
            return lines.subList(start.coerceAtMost(lines.size), lines.size).filter { it.contains(" $kind ") && tOf(it) >= from }
        }
        val soundsA = recv(a, "areasound", spawnT).size + recv(a, "sound", spawnT).size
        // attack sounds the animation itself carries (frame sounds with a radius, played by every client in range)
        val frameSounds = p1.count { cat(it) == "sound.attack" && it.contains(tag) && (kv(it, "result") ?: "").endsWith("-has-frame-sounds") }
        val soundsO = recv(o, "areasound", spawnT).size
        val skipped = p1.count { cat(it) == "zone.skip" && bots.any { p -> kv(it, "player") == p.username } }
        if (skipped > 0) invariant += " FAIL:zone.skip=$skipped"
        val projSections = swings.count { s -> list(kv(s, "projectiles")).any { (it.toIntOrNull() ?: -1) >= 0 } }
        val projA = recv(a, "projectile", spawnT).size
        val projO = recv(o, "projectile", spawnT).size
        val soundCol = "server A=$soundsA O=$soundsO frame=$frameSounds per $attacks swings${if (attacks > 0 && soundsA == 0 && frameSounds == 0) " FAIL:silent" else ""}"
        val projCol = if (projSections == 0) "-" else "A=$projA O=$projO of $projSections${if (projA < projSections || projO < projSections) " FAIL" else ""}"

        // 1b. monster-specific mechanics that need a setup (OSRS Wiki "Deadman: Annihilation/Breach monsters" + monster pages)
        val tMech = trace.size
        var mechSetup = ""
        when (m.id) {
            14445 -> { // Dharok: "increases his max hit ... for every Hitpoint he is missing"
                npc.setCurrentLifepoints((npc.getMaximumLifepoints() / 10).coerceAtLeast(1))
                // re-engage A: after the multi-way phase Dharok may be on another bot or idle, which left the sample empty
                npc.attack(a)
                ticks(24)
                val maxes = trace.subList(tMech, trace.size).filter { cat(it) == "hit" && it.contains(tag) }.mapNotNull { kv(it, "max")?.toDoubleOrNull() }
                mechSetup = "dharokMaxAt10%=${maxes.maxOrNull() ?: "no-hit"}${if ((maxes.maxOrNull() ?: 0.0) <= 29.0) " FAIL(not above 29)" else ""}"
                npc.setCurrentLifepoints(npc.getMaximumLifepoints())
            }
            14459 -> { // Bee Swarm: "Stepping beneath the bee swarm will result in taking damage"
                val back = a.tile
                a.moveTo(npc.tile)
                ticks(4)
                val stings = trace.subList(tMech, trace.size).count { cat(it) == "player.hit" && kv(it, "player") == a.username && (kv(it, "source") ?: "").contains("deadman_breach", ignoreCase = true) }
                mechSetup = "stungUnderSwarm=$stings${if (stings == 0) " FAIL" else ""}"
                a.moveTo(back)
            }
        }
        // freeze immunity (infobox freezeresistance 100) - the others must freeze
        run {
            val t0 = trace.size
            val took = npc.freeze(4) {}
            val resisted = trace.subList(t0, trace.size).any { cat(it) == "freeze.resisted" && it.contains(tag) }
            val immune = m.id in BreachMonsters.FREEZE_IMMUNE
            mechSetup += " freeze=${if (took) "frozen" else if (resisted) "resisted" else "no(timer)"}${if (immune && took) " FAIL(immune)" else if (!immune && resisted) " FAIL(not immune)" else ""}"
            ticks(6)
        }

        // 2. B attacks the monster while it fights A
        val t2 = trace.size
        b.attack(npc)
        ticks(12)
        val p2 = trace.subList(t2, trace.size).toList()
        val bCol =
            if (site.multi) {
                val switched = npc.getCombatTarget() === b || p2.any { cat(it) == "target.set" && it.contains(tag) && kv(it, "new") == b.username }
                "switch=${if (switched) "yes" else "no"} (OSRS: only Zemouregal documented)"
            } else {
                val ruling = p2.lastOrNull { cat(it) == "single" && kv(it, "attacker") == b.username && (kv(it, "target") ?: "").contains("${m.id}") }
                // refused because B is itself fought by another breach npc (a summon) is the correct single-way rule
                val busyWithOther = ruling?.let { kv(it, "result") == "refused:single-attacker-under-attack" } == true &&
                    (b.getLastHitBy() as? Npc)?.let { it !== npc && DeadmanBreach.isBreachNpc(it) } == true
                // B may already have been fighting it before this phase (no new ruling) and every hit may be a 0 (Bloat's 600/800
                // defences, 2026-09-28 run): still fighting it counts as allowed.
                val stillOnIt = b.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() === npc
                val ok = busyWithOther || stillOnIt || (ruling?.let { kv(it, "result") == "allowed" } ?: p2.any { cat(it) == "damage.in" && it.contains(tag) })
                "allowed=${ruling?.let { kv(it, "result") } ?: "no-ruling"}${if (ok) "" else " FAIL"}"
            }
        b.resetInteractions()

        // 3. C attacks A while the monster attacks A (the forced attack below ignores the single-way rules, so the monster's own
        // summons - Zemouregal Summons, TzTok-Jad-Rek, ADAPTED side monsters - leave first; they would be a pile-up made by the test)
        DeadmanBreach.live.filter { it !== npc && BreachMonsters.BY_ID[it.id]?.summon == true }.forEach { if (world.npcs.contains(it)) world.remove(it) }
        npc.attack(a)
        ticks(6)
        val t3 = trace.size
        c.attack(a)
        ticks(6)
        val p3 = trace.subList(t3, trace.size).toList()
        val pk = p3.lastOrNull { cat(it) == "single" && kv(it, "attacker") == c.username && kv(it, "target") == a.username }
        val pkResult = pk?.let { kv(it, "result") } ?: "no-ruling"
        val cCol =
            if (m.id == 14470) {
                "$pkResult (no direct attack - a PK is allowed)"
            } else if (site.multi) {
                "$pkResult${if (pk != null && pkResult != "allowed") " FAIL" else ""}"
            } else {
                "$pkResult${if (pkResult == "allowed") " FAIL" else ""}"
            }
        c.resetInteractions()
        a.resetInteractions()

        // 4. A steps diagonally away (inside the leash): the monster's first step must be diagonal when it can be
        // drop a route still queued towards another bot (multi phase), or its first step is measured as this one
        npc.stopMovement()
        ticks(1)
        val t4 = trace.size
        val diag =arena.filter { t -> val dx = t.x - npc.tile.x; val dz = t.z - npc.tile.z; kotlin.math.abs(dx) in 4..7 && kotlin.math.abs(dz) in 4..7 }.minByOrNull { it.getDistance(npc.tile) }
        var diagCol = "n/a"
        if (diag != null) {
            a.moveTo(diag)
            npc.attack(a)
            ticks(8)
            val steps = trace.subList(t4, trace.size).filter { cat(it) == "step" && it.contains(tag) }
            val first = steps.firstOrNull()
            diagCol =
                if (first == null) {
                    val refused = trace.subList(t4, trace.size).firstOrNull { cat(it) == "step.refused" && it.contains(tag) }
                    "no-step${refused?.let { " (${it.substringAfter("to=").substringAfter(' ').substringBefore(' ')})" } ?: ""}"
                } else {
                    val from = kv(first, "from")!!.split(',').map { it.toInt() }
                    val to = kv(first, "to")!!.split(',').map { it.toInt() }
                    if (from[0] != to[0] && from[1] != to[1]) {
                        "diagonal"
                    } else {
                        // straight is right when the diagonal was blocked (OSRS: "then straight"); wrong when it was open
                        val start = Tile(from[0], from[1], npc.tile.height)
                        // towards the target relative to the whole footprint (a 5x5 whose span already covers the target's x goes straight)
                        val dx = if (start.x + size - 1 < diag.x) 1 else if (start.x > diag.x) -1 else 0
                        val dz = if (start.z + size - 1 < diag.z) 1 else if (start.z > diag.z) -1 else 0
                        val dir = gg.rsmod.game.model.Direction.values().firstOrNull { it.getDeltaX() == dx && it.getDeltaZ() == dz }
                        val open = dir != null && dx != 0 && dz != 0 &&
                            (0 until size).all { x -> (0 until size).all { z -> world.collision.canTraverse(start.transform(x, z), dir, projectile = false, water = false) } }
                        // routefinders (Zemouregal, summons, Durial321) take a shortest route, which may start straight
                        when {
                            npc.smartPathfinding -> "straight (routefinder)"
                            open -> "straight FAIL(diagonal was open)"
                            else -> "straight (diagonal blocked)"
                        }
                    }
                }
        }

        // 5. leash: A goes 30 tiles from the monster's spawn, and the others leave too (else it rightly picks them next)
        bots.forEachIndexed { i, p -> p.resetInteractions(); p.moveTo(Tile(npc.spawnTile.x + 30 + i, npc.spawnTile.z, npc.spawnTile.height)) }
        ticks(45)
        val released = npc.getCombatTarget() !== a
        val homeGap = maxOf(kotlin.math.abs(npc.tile.x - npc.spawnTile.x), kotlin.math.abs(npc.tile.z - npc.spawnTile.z))
        val leashCol = "released=$released homeGap=$homeGap${if (!released || homeGap > DeadmanBreach.ROAM_RADIUS + size) " FAIL" else ""}"
        bots.forEachIndexed { i, p -> p.moveTo(homes[i]) }
        ticks(3)

        // 6. death seen by everyone in view
        val t6 = trace.size
        val deathAnims = npc.combatDef.deathAnimation.map { "$it" }
        if (m.id == 14468) {
            // Splatter's explosion hits the players next to its footprint (multi) or its target (single): put A west and B east of it.
            a.moveTo(Tile(npc.tile.x - 1, npc.tile.z, npc.tile.height))
            b.moveTo(Tile(npc.tile.x + size, npc.tile.z, npc.tile.height))
            npc.attack(a)
            ticks(2)
        }
        npc.hit(npc.getCurrentLifepoints() + 10)
        var gone = false
        repeat(15) {
            q.wait(1)
            if (!world.npcs.contains(npc)) gone = true
        }
        val p6 = trace.subList(t6, trace.size).toList()
        // Only bots that have the monster in view count (2026-09-28: since the walkMask fix a monster really wanders its whole
        // walk radius, so one bot's home can lie outside the 15-tile view of where it dies).
        val viewers = bots.filter { it.tile.height == npc.tile.height && it.tile.isWithinRadius(npc.tile, 14) }
        val deathSeen = viewers.count { p -> p6.any { l -> cat(l) == "sync.block" && l.contains(tag) && kv(l, "viewer") == p.username && kv(l, "anim") in deathAnims } }
        val deathCol = "anim ${deathAnims.joinToString("/")} seenBy=$deathSeen/${viewers.size} removed=$gone${if (deathSeen < viewers.size || viewers.isEmpty() || !gone) " FAIL" else ""}"
        if (world.npcs.contains(npc)) world.remove(npc)
        DeadmanBreach.pruneLive(world)
        bots.forEachIndexed { i, p -> p.moveTo(homes[i]) }

        val seenCol = seen()
        // mechanics proven from the monster's whole trace
        val all = trace.subList(traceStart, trace.size).toList()
        fun n(c: String) = all.count { cat(it) == c && (it.contains(tag) || !it.contains("npc=")) }
        val mechCol =
            (
                mechSetup + " " +
                    when (m.id) {
                        14451 -> { val burns = n("mech.burn"); val fives = all.count { cat(it) == "player.hit" && (kv(it, "damage")?.toIntOrNull() ?: 0) >= 5 && (kv(it, "source") ?: "").let { s -> s.contains("deadman_breach", ignoreCase = true) || s.contains("Burns", ignoreCase = true) } }; "burn=$burns burnHits=$fives${if (burns == 0 || fives < 2 * burns) " FAIL" else ""}" }
                        14447, 14448 -> { val tb = n("mech.teleblock"); "teleblock=$tb${if (tb == 0) " FAIL" else ""}" }
                        14456 -> { val kb = n("mech.knockback"); "knockback=$kb${if (kb == 0) " FAIL" else ""}" }
                        14464 -> { val h = n("mech.heal"); "heal=$h${if (h == 0) " FAIL" else ""}" }
                        14463 -> { val fz = all.count { cat(it) == "mech.freeze" && kv(it, "applied") == "true" }; "iceBarrageFreeze=$fz${if (fz == 0) " FAIL" else ""}" }
                        14469 -> { val sv = n("mech.sever"); "sever=$sv${if (sv == 0) " FAIL" else ""}" }
                        14449 -> "teleports=${n("mech.teleport")}"
                        14457 -> "fireballs=${all.count { cat(it) == "attack.swing" && it.contains(tag) && kv(it, "section") == "fireball" }}"
                        14466 -> { val s = all.count { cat(it) == "breach.spawn" && Regex("npc=1447[1-6]#").containsMatchIn(it) }; "summons=$s${if (s == 0) " FAIL" else ""}" }
                        14462 -> { val s = all.count { cat(it) == "breach.spawn" && it.contains("npc=14477#") }; "jadRek=$s${if (s == 0) " FAIL" else ""}" }
                        14470 -> { val fl = all.count { cat(it) == "player.hit" && (kv(it, "source") ?: "").contains("deadman_breach", ignoreCase = true) }; "flyHits=$fl${if (fl == 0) " FAIL" else ""}" }
                        14468 -> { val ex = all.filter { cat(it) == "player.hit" && (kv(it, "source") ?: "").contains("deadman_breach", ignoreCase = true) }.map { kv(it, "player") }.distinct().size; "explosionVictims=$ex${if (ex == 0 || (!site.multi && ex > 1)) " FAIL" else ""}" }
                        14446, 14447, 14448 -> ""
                        14439 -> {
                            // KBD: "normal dragonfire as well as shock, ice, and poison dragonfire" - each special must carry its effect
                            val imp = all.filter { cat(it) == "impact" && it.contains(tag) && kv(it, "landed") == "true" }
                            val bad = imp.count { i -> when (kv(i, "section")) { "toxic" -> kv(i, "poison") == "0"; "ice" -> kv(i, "freeze") == "0"; "shock" -> kv(i, "drains") == "0"; else -> false } }
                            "dragonfire=${imp.mapNotNull { kv(it, "section") }.distinct()}${if (bad > 0) " FAIL(effect missing $bad)" else ""}"
                        }
                        else -> ""
                    } +
                    if (m.immuneMelee) { val leak = all.count { cat(it) == "damage.in" && it.contains(tag) && (kv(it, "type") == "MELEE" || kv(it, "type") == "RANGE") && kv(it, "final") != "0" }; " meleeRangedImmune=${if (leak == 0) "ok" else "FAIL($leak)"}" } else ""
            ).trim()
        // overhead texts (OSRS: the God Wars generals shout, the Big Evil Chicken talks); a talker silent over 12+ swings is a FAIL
        val chats = trace.subList(traceStart, trace.size).filter { cat(it) == "sync.block" && it.contains(tag) && kv(it, "viewer") == a.username && !it.endsWith("chat=-") }.size
        val talkers = setOf(14442, 14443, 14444, 14467)
        val chatCol = "chats=$chats${if (m.id in talkers && attacks >= 12 && chats == 0) " FAIL:silent-talker" else ""}"
        val verdictFails = listOf(mechCol, chatCol, hitsCol, animCol, seenCol, aoe, soundCol, projCol, bCol, cCol, diagCol, leashCol, deathCol, invariant).count { it.contains("FAIL") || it.contains("MISSING") } +
            (if (!hitsOk) 1 else 0) + (if (attacks == 0 && m.id != 14470) 1 else 0) +
            (if (seenCol.split(',').any { s -> s.substringBefore('/') != s.substringAfter('/') }) 1 else 0)
        return "${m.id} ${m.name} | $siteName | ${if (verdictFails == 0) "PASS" else "FAIL($verdictFails)"} | attacks=$attacks first=$firstAttack | $animCol | seen $seenCol | aoe $aoe | " +
            "hits=${if (hitsOk) "ok" else "FAIL"} $hitsCol | sounds $soundCol | $chatCol | mech ${mechCol.ifEmpty { "-" }} | proj $projCol | B $bCol | C $cCol | step $diagCol | $leashCol | death $deathCol |$invariant"
    }
}
