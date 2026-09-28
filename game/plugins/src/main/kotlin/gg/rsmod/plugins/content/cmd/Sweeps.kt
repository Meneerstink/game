package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.service.GameService
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.skills.summoning.FAMILIAR_SPECIAL_POINTS_ATTR
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.skills.summoning.FamiliarSpecialTarget
import gg.rsmod.plugins.content.skills.summoning.SummoningPouchData
import gg.rsmod.plugins.content.skills.summoning.SummoningSpecialMoves
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.ReferenceCountUtil
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug toolkit phase 4 - automatic sweeps (manual: C:\RSPS\TRACES.md; owner 2026-09-28: "veel summoningbugs, veel combat bugs
 * pvp/pvm en sound bugs en veel visueel"). A headless test player ("Sweepbot", never saved, like BreachSim) goes through a whole
 * content set on the live server while every trace is captured, and writes one verdict per entry:
 *
 *   sweep weapons [from] [count] [pvp]   every wieldable weapon: equip, attack a dummy (or a second bot with pvp), then its special
 *   sweep spells                         every combat spell cast on the dummy (infinite runes, the spell's own book)
 *   sweep familiars [from] [count]       every pouch: summon, follow (bot walks away), special move on the dummy or instant, dismiss
 *   sweep npcs [from] [count] [name]     every combat npc type: it attacks, is hit (block), dies - attack/block/death anim and sounds
 *   sweep stop                           ends the running sweep after the current entry
 *
 * The sweep runs next to the admin who starts it (stand in an empty single-way field). Evidence per entry = the traced lines of
 * its window (swing/npcattack/anim/graphic/areasound/PROBLEM). Report: C:\RSPS\bugs\sweep-<what>-<stamp>.txt; every failed entry
 * is also a `[trace:sweep] PROBLEM` line, so tools\Show-Problems.ps1 lists them.
 */
object Sweeps {
    @Volatile
    var running: String? = null

    @Volatile
    private var stopRequested = false

    /** Marks the sweep's dummy target (the tick invariants skip it: its hitpoints are held above max on purpose). */
    val SWEEP_DUMMY_ATTR = AttributeKey<Boolean>()

    private const val DUMMY_NPC = 1 // Man: a plain attackable humanoid
    private const val BOT_LIFE = 20000
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun stop() {
        stopRequested = true
    }

    fun start(
        world: World,
        admin: Player,
        what: String,
        args: List<String>,
    ) {
        if (running != null) {
            admin.message("A sweep is already running ($running). Type sweep stop first.")
            return
        }
        val base = admin.tile.transform(3, 0)
        running = what
        stopRequested = false
        val report = mutableListOf<String>()
        val counts = linkedMapOf("ok" to 0, "problem" to 0)
        val previous = Trace.Cat.values().associateWith { Trace.enabled(it) }
        val previousAv = AvTrace.enabled
        Trace.Cat.values().forEach { Trace.set(it, true) }
        AvTrace.enabled = true
        val bot = bot(world, base, "Sweepbot")
        val adminName = admin.username
        admin.message("<col=178000>Sweep '$what' started</col> next to you. Progress in the chat; report in C:\\RSPS\\bugs\\. sweep stop ends it.")
        world.queue {
            try {
                when (what) {
                    "weapons" -> weapons(world, bot, base, args, report, counts)
                    "spells" -> spells(world, bot, base, report, counts)
                    "familiars" -> familiars(world, bot, base, args, report, counts)
                    "npcs" -> npcs(world, bot, base, args, report, counts)
                    "dialogues" -> dialogues(world, bot, args, report, counts)
                    else -> report += "unknown sweep '$what' (weapons, spells, familiars, npcs)"
                }
            } catch (e: Throwable) {
                Trace.crash("sweep $what", e)
                report += "sweep aborted: $e"
            } finally {
                Trace.stopCapture()
                runCatching { Familiar.dismiss(bot) }
                runCatching { world.unregister(bot) }
                previous.forEach { (cat, on) -> Trace.set(cat, on) }
                AvTrace.enabled = previousAv
                val file = write(what, report, counts)
                running = null
                world.getPlayerForName(adminName)?.message("<col=178000>Sweep '$what' done:</col> ${counts["ok"]} ok, ${counts["problem"]} with problems - ${file.name}")
            }
        }
    }

    // ---- sweeps ------------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.weapons(
        world: World,
        bot: Client,
        base: Tile,
        args: List<String>,
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
    ) {
        val pvp = args.any { it.equals("pvp", true) }
        val from = args.getOrNull(0)?.toIntOrNull() ?: 0
        val count = args.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
        val target: gg.rsmod.game.model.entity.Pawn = if (pvp) bot(world, base.transform(1, 0), "Sweeptarget") else dummy(world, base.transform(1, 0))
        val weapons =
            (0 until world.definitions.getCount(ItemDef::class.java)).mapNotNull { id ->
                val def = runCatching { world.definitions.getNullable(ItemDef::class.java, id) }.getOrNull() ?: return@mapNotNull null
                if (def.equipSlot != 3 || def.noted || def.isPlaceholder || def.name.isBlank() || def.name == "null") null else def
            }.drop(from).take(count)
        report += "weapons: ${weapons.size} (from index $from)${if (pvp) " vs a player" else " vs npc $DUMMY_NPC"}"
        for ((n, def) in weapons.withIndex()) {
            if (stopRequested) break
            keepAlive(bot, target)
            bot.resetInteractions()
            Combat.reset(bot)
            bot.equipment[3] = Item(def.id, if (def.stackable) 5000 else 1)
            bot.equipment[13] = ammoFor(def.name)?.let { Item(it, 5000) }
            bot.addBlock(UpdateBlockType.APPEARANCE)
            wait(1)
            val normal = window(4) { bot.attack(target) }
            val verdicts = mutableListOf<String>()
            verdicts += judgeSwing(normal, "normal")
            if (SpecialAttacks.hasSpecialAttack(def.id)) {
                Combat.reset(bot)
                keepAlive(bot, target)
                bot.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
                AttackTab.setEnergy(bot, 100)
                bot.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
                val special = window(4) { bot.attack(target) }
                verdicts += judgeSwing(special, "special")
            }
            Combat.reset(bot)
            record("weapon ${def.id} '${def.name}'", verdicts, report, counts)
            if (n % 50 == 0) progress(world, "weapons ${n + 1}/${weapons.size}")
        }
        if (target is Client) runCatching { world.unregister(target) } else if (target is Npc) world.remove(target)
    }

    private suspend fun QueueTask.spells(
        world: World,
        bot: Client,
        base: Tile,
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
    ) {
        val dummy = dummy(world, base.transform(4, 0))
        bot.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        bot.skills.setBaseLevel(Skills.MAGIC, 99)
        bot.equipment[3] = null
        bot.addBlock(UpdateBlockType.APPEARANCE)
        report += "spells: ${CombatSpell.values.size}"
        for (spell in CombatSpell.values) {
            if (stopRequested) break
            keepAlive(bot, dummy)
            Combat.reset(bot)
            bot.setSpellbook(if (spell.interfaceId == 193) Spellbook.ANCIENT else Spellbook.STANDARD)
            spell.requiredWeapons.firstOrNull()?.let { bot.equipment[3] = Item(it) } ?: run { bot.equipment[3] = null }
            bot.addBlock(UpdateBlockType.APPEARANCE)
            wait(1)
            val lines =
                window(6) {
                    bot.attr[Combat.CASTING_SPELL] = spell
                    bot.attack(dummy)
                }
            val verdicts = judgeSwing(lines, "cast").toMutableList()
            if (spell.projectile >= 0 && lines.none { it.contains("projectile gfx=${spell.projectile}") }) verdicts += "projectile ${spell.projectile} never sent"
            spell.impactGfx?.let { g -> if (lines.none { it.contains("id=${g.id}") }) verdicts += "impact graphic ${g.id} never shown" }
            record("spell ${spell.name}", verdicts, report, counts)
        }
        world.remove(dummy)
    }

    private suspend fun QueueTask.familiars(
        world: World,
        bot: Client,
        base: Tile,
        args: List<String>,
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
    ) {
        val dummy = dummy(world, base.transform(4, 0))
        bot.skills.setBaseLevel(Skills.SUMMONING, 99)
        val from = args.getOrNull(0)?.toIntOrNull() ?: 0
        val count = args.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
        val pouches = SummoningPouchData.values().drop(from).take(count)
        report += "familiars: ${pouches.size}"
        for ((n, data) in pouches.withIndex()) {
            if (stopRequested) break
            keepAlive(bot, dummy)
            runCatching { Familiar.dismiss(bot) }
            bot.moveTo(base)
            bot.skills.setCurrentLevel(Skills.SUMMONING, 99)
            bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = 60
            bot.inventory.removeAll()
            bot.inventory.add(data.pouch, 1)
            wait(1)
            val verdicts = mutableListOf<String>()
            val summonLines = window(3) { if (!Familiar.summon(bot, data)) verdicts += "summon refused" }
            val familiar = Familiar.current(bot)
            if (familiar == null) {
                verdicts += "no familiar after summon"
                record("familiar ${data.name}", verdicts, report, counts)
                continue
            }
            if (summonLines.none { it.contains("graphic") || it.contains("anim") }) verdicts += "summon showed no graphic or animation"
            if (summonLines.none { it.contains("sound") }) verdicts += "summon made no sound"
            // Follow: the bot walks 6 tiles away; the familiar must be within 3 tiles of it afterwards.
            bot.moveTo(base.transform(0, 6))
            wait(6)
            if (!familiar.tile.isWithinRadius(bot.tile, 3)) verdicts += "did not follow (still ${familiar.tile.getDistance(bot.tile)} tiles away)"
            // Special move.
            val binding = SummoningSpecialMoves.resolveBinding(bot)
            if (binding == null) {
                verdicts += "no special move binding"
            } else {
                bot.inventory.add(binding.scroll.scroll, 5)
                bot.timers.remove(SummoningSpecialMoves.SPECIAL_MOVE_DELAY_TIMER)
                val specialLines =
                    window(4) {
                        val cast =
                            when (binding.target) {
                                FamiliarSpecialTarget.INSTANT -> SummoningSpecialMoves.castInstant(bot)
                                FamiliarSpecialTarget.NPC -> SummoningSpecialMoves.castOnNpc(bot, dummy)
                                else -> null
                            }
                        if (cast == false) verdicts += "special ${binding.scroll.name} (${binding.target}) refused"
                        if (cast == null) verdicts += "special ${binding.scroll.name} targets ${binding.target} (not swept)"
                    }
                specialLines.filter { it.contains("PROBLEM") }.forEach { verdicts += it.substringAfter("PROBLEM").trim().take(160) }
            }
            runCatching { Familiar.dismiss(bot) }
            record("familiar ${data.name}", verdicts, report, counts)
            if (n % 10 == 0) progress(world, "familiars ${n + 1}/${pouches.size}")
        }
        world.remove(dummy)
    }

    private suspend fun QueueTask.npcs(
        world: World,
        bot: Client,
        base: Tile,
        args: List<String>,
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
    ) {
        val from = args.getOrNull(0)?.toIntOrNull() ?: 0
        val count = args.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
        val filter = args.drop(2).joinToString(" ").lowercase()
        val ids = sortedSetOf<Int>()
        world.npcs.forEach { if (it.combatDef !== gg.rsmod.game.model.combat.NpcCombatDef.DEFAULT) ids += it.id }
        gg.rsmod.plugins.content.combat.attack.NpcAttacks.rows().forEach { ids += it.id }
        val list =
            ids.filter { id ->
                filter.isEmpty() || runCatching { world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, id).name.lowercase().contains(filter) }.getOrDefault(false)
            }.drop(from).take(count)
        report += "npcs: ${list.size}${if (filter.isNotEmpty()) " matching '$filter'" else ""}"
        for ((n, id) in list.withIndex()) {
            if (stopRequested) break
            bot.moveTo(base)
            bot.setCurrentLifepoints(BOT_LIFE)
            Combat.reset(bot)
            val npc = Npc(id, base.transform(2, 0), world)
            npc.respawnOverride = false
            npc.walkRadius = 0
            world.spawn(npc)
            wait(1)
            val name = runCatching { npc.def.name }.getOrDefault("?")
            val tag = "#${npc.index}"
            val verdicts = mutableListOf<String>()
            val attack = window(6) { npc.attack(bot); bot.setCurrentLifepoints(BOT_LIFE) }
            val mine = attack.filter { it.contains(tag) || it.contains("index=${npc.index} ") }
            if (mine.none { it.contains("npcattack") || it.contains("attack.swing") }) verdicts += "never attacked within 6 ticks"
            mine.filter { it.contains("PROBLEM") }.forEach { verdicts += it.substringAfter("PROBLEM").trim().take(140) }
            val block = window(4) { bot.attack(npc) }
            val blockAnim = npc.combatDef.blockAnimation
            if (blockAnim >= 0 && block.none { it.contains("index=${npc.index} ") && it.contains("id=$blockAnim") }) verdicts += "block animation $blockAnim not seen when hit"
            Combat.reset(bot)
            val death = window(npc.combatDef.deathDelay + 5) { npc.hit(npc.getCurrentLifepoints()) }
            val deaths = npc.combatDef.deathAnimation.filter { it >= 0 }
            if (deaths.isNotEmpty() && death.none { l -> deaths.any { l.contains("id=$it") } }) verdicts += "death animation ${deaths.joinToString("/")} not seen"
            if (death.none { it.contains("sound.death") || it.contains("areasound") }) verdicts += "no death sound traced"
            if (!npc.isDead() && world.npcs.contains(npc)) world.remove(npc)
            record("npc $id '$name'", verdicts, report, counts)
            if (n % 25 == 0) progress(world, "npcs ${n + 1}/${list.size}")
        }
    }

    /**
     * Talks to one spawned npc of every type that offers Talk-to: a generic one-line greeting (ROOT_CAUSES #27), "Nothing
     * interesting happens", an exception, or no dialogue at all within 3 ticks is reported. The bot stands next to the npc.
     */
    private suspend fun QueueTask.dialogues(
        world: World,
        bot: Client,
        args: List<String>,
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
    ) {
        val from = args.getOrNull(0)?.toIntOrNull() ?: 0
        val count = args.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
        val seen = HashSet<Int>()
        val targets = mutableListOf<Npc>()
        world.npcs.forEach { n ->
            val def = runCatching { n.def }.getOrNull() ?: return@forEach
            if (def.options.any { it.equals("Talk-to", true) } && seen.add(n.id)) targets += n
        }
        val list = targets.drop(from).take(count)
        report += "dialogues: ${list.size} npc types with Talk-to"
        for ((i, npc) in list.withIndex()) {
            if (stopRequested) break
            if (!world.npcs.contains(npc)) continue
            bot.interruptQueues()
            bot.moveTo(npc.tile.transform(npc.getSize(), 0))
            wait(1)
            val slot = npc.def.options.indexOfFirst { it.equals("Talk-to", true) } + 1
            val lines =
                window(3) {
                    bot.attr[gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR] = java.lang.ref.WeakReference(npc)
                    bot.attr[gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR] = slot
                    if (!world.plugins.executeNpc(bot, npc.id, slot)) verdicts(report, counts, "npc ${npc.id} '${npc.def.name}'", listOf("Talk-to has no handler"))
                }
            val v = mutableListOf<String>()
            if (lines.any { it.contains("generic one-line greeting") }) v += "only a generic one-line greeting"
            if (lines.any { it.contains("Nothing interesting happens") }) v += "Nothing interesting happens"
            lines.filter { it.contains("PROBLEM crash") }.forEach { v += it.substringAfter("PROBLEM").trim().take(140) }
            if (lines.none { it.contains("IfOpenSub") || it.contains("[trace:msg]") } && v.isEmpty()) v += "no dialogue opened within 3 ticks"
            record("npc ${npc.id} '${npc.def.name}' at ${npc.tile.x},${npc.tile.z}", v, report, counts)
            if (i % 50 == 0) progress(world, "dialogues ${i + 1}/${list.size}")
        }
        bot.interruptQueues()
    }

    private fun verdicts(
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
        what: String,
        v: List<String>,
    ) = record(what, v, report, counts)

    // ---- helpers -----------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.window(
        ticks: Int,
        action: () -> Unit,
    ): List<String> {
        val lines = mutableListOf<String>()
        Trace.capture(lines)
        try {
            action()
            wait(ticks)
        } finally {
            Trace.stopCapture()
        }
        return synchronized(lines) { lines.toList() }
    }

    /** The verdicts of one swing window: no swing at all, or the swing audit's PROBLEM lines (animation, silence). */
    private fun judgeSwing(
        lines: List<String>,
        kind: String,
    ): List<String> {
        val swings = lines.filter { it.contains("swing Sweepbot") }
        if (swings.isEmpty()) return listOf("$kind: no swing within the window (refused, out of range, or needs ammo/runes)")
        return lines.filter { it.contains("PROBLEM") && it.contains("Sweepbot") }.map { "$kind: " + it.substringAfter("PROBLEM").trim().take(150) }.distinct()
    }

    private fun record(
        what: String,
        verdicts: List<String>,
        report: MutableList<String>,
        counts: MutableMap<String, Int>,
    ) {
        if (verdicts.isEmpty()) {
            report += "OK      $what"
            counts["ok"] = (counts["ok"] ?: 0) + 1
        } else {
            report += "PROBLEM $what: ${verdicts.distinct().joinToString(" | ")}"
            counts["problem"] = (counts["problem"] ?: 0) + 1
            Trace.problem(Trace.Cat.SWEEP) { "$what: ${verdicts.distinct().joinToString(" | ").take(220)}" }
        }
    }

    private fun write(
        what: String,
        report: List<String>,
        counts: Map<String, Int>,
    ): File {
        val dir = File("C:/RSPS/bugs")
        dir.mkdirs()
        val file = File(dir, "sweep-$what-${LocalDateTime.now().format(STAMP)}.txt")
        file.writeText("RSPS sweep '$what' ${LocalDateTime.now()}  ok=${counts["ok"]} problem=${counts["problem"]}\n\n" + report.joinToString("\n") + "\n")
        logger.info("Sweep {} written to {}", what, file)
        return file
    }

    private fun progress(
        world: World,
        text: String,
    ) {
        world.players.forEach { if (it !is Client || !it.username.startsWith("Sweep")) it.message("<col=7f7f7f>sweep: $text</col>") }
    }

    private fun keepAlive(
        bot: Player,
        target: gg.rsmod.game.model.entity.Pawn,
    ) {
        bot.setCurrentLifepoints(BOT_LIFE)
        if (target is Npc) target.setCurrentLifepoints(target.getMaximumLifepoints())
        if (target is Player) target.setCurrentLifepoints(BOT_LIFE)
    }

    private fun ammoFor(weaponName: String): Int? {
        val n = weaponName.lowercase()
        return when {
            n.contains("crossbow") || n.contains("c'bow") -> Items.RUNITE_BOLTS
            n.contains("karil") -> Items.BOLT_RACK
            n.contains("ogre") && n.contains("bow") -> Items.OGRE_ARROW
            n.contains("bow") && !n.contains("crystal") -> Items.RUNE_ARROW
            else -> null
        }
    }

    private fun dummy(
        world: World,
        tile: Tile,
    ): Npc {
        val npc = Npc(DUMMY_NPC, tile, world)
        npc.respawnOverride = false
        npc.walkRadius = 0
        npc.attr[SWEEP_DUMMY_ATTR] = true
        world.spawn(npc)
        return npc
    }

    private fun bot(
        world: World,
        tile: Tile,
        name: String,
    ): Client {
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
        for (skill in 0..Skills.DUNGEONEERING) bot.skills.setBaseLevel(skill, 99)
        check(world.register(bot)) { "sweep player not registered" }
        bot.login()
        // Never saved: the autosave timer would write a save file for the test player.
        bot.timers.remove(gg.rsmod.game.model.timer.SAVE_TIMER)
        gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection.forfeit(bot)
        bot.setCurrentLifepoints(BOT_LIFE)
        return bot
    }
}
