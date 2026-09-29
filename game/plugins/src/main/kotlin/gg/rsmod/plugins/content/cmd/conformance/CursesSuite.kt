package gg.rsmod.plugins.content.cmd.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.sendWeaponComponentInformation
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import java.io.File

/**
 * Ancient Curses and normal prayers conformance (catalogue section 2). Reference: C:\RSPS\reference\prayer\curses.json
 * (Novite rev-667 Prayer/Player, Divergent667, curses-2011 effects, the 667 cache audio table). Every click is the real
 * prayer-book button 271:8 with the slot.
 *
 * Per curse: level gate (refused, message), activation (state, animation, graphic, sound audible - the gfx/anim sequence frame
 * sound from the cache or a sent sound), drain over 30 s at prayer bonus 0. Every ordered pair of curses: which stay on
 * (Novite groups). Combat, bot vs bot (PvP): sap/leech proc rate (statistical), caster animation, projectile, target graphic,
 * stat varbits 6857+ and cap, deflect reflection share and amount, soul split heal, turmoil boost varbits, wrath on death.
 * Normal prayers: level gates, every ordered pair (groups), drain.
 */
object CursesSuite : Conformance.Suite {
    private const val REF = "C:/RSPS/reference/prayer/curses.json"
    private const val BOOK = 271
    private const val BOOK_GRID = 8
    private const val HITS = 250

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val ref = ObjectMapper().readTree(File(REF))
        val arena = SummoningSuite.arenaFor(run, run.origin.transform(3, 0))
        run.note("arena ${arena.x},${arena.z},${arena.height}")
        val bot = run.bot(arena, "Conf1")
        val target = run.bot(arena.transform(1, 0), "Conf2")
        run.bot(arena.transform(-4, -4), "Bystander")
        wait(2)
        run.expectTrue("harness", "prayer book 271 open on the bot", bot.interfaces.isVisible(BOOK), "visible=${bot.interfaces.isVisible(BOOK)}")
        val filter = args.joinToString(" ").lowercase()
        val only = { name: String -> filter.isEmpty() || name.lowercase().contains(filter) }

        // ---- curses --------------------------------------------------------------------------------------------------------
        AncientCurses.unlock(bot)
        AncientCurses.switchBook(bot, AncientCurses.PrayerBook.ANCIENT)
        wait(1)
        val curses = ref["curses"].toList()
        for (c in curses) {
            if (Conformance.stopRequested) break
            val name = c["name"].asText()
            if (!only(name)) continue
            val from = run.world.currentCycle
            run.progress("curses: $name")
            try {
                activation(run, bot, c, ref, "curse ${name.lowercase()}", curse = true)
            } catch (e: Throwable) {
                run.add(Result("curse ${name.lowercase()}", "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
            }
            run.window("curse ${name.lowercase()}", from)
        }
        if (filter.isEmpty()) {
            val from = run.world.currentCycle
            pairs(run, bot, curses.map { it["slot"].asInt() to it["name"].asText() }, ref["novite_groups"].fields().asSequence().map { it.value.map { v -> v.asInt() } }.toList(), "curse pairs", curse = true)
            run.window("curse pairs", from)
        }
        for (c in curses) {
            if (Conformance.stopRequested) break
            val name = c["name"].asText()
            if (!only(name)) continue
            val from = run.world.currentCycle
            run.progress("curse combat: $name")
            try {
                combat(run, bot, target, arena, c, ref, "curse ${name.lowercase()}")
            } catch (e: Throwable) {
                run.add(Result("curse ${name.lowercase()}", "combat scenario", Verdict.FAIL, "runs to the end", "threw $e"))
            }
            run.window("curse ${name.lowercase()} combat", from)
        }

        // ---- normal prayers ------------------------------------------------------------------------------------------------
        AncientCurses.switchBook(bot, AncientCurses.PrayerBook.NORMAL)
        wait(1)
        val normal = ref["normal_prayers"]
        val levels = normal["levels_novite"].map { it.asInt() }
        val drains = normal["drain_s_per_tenth_novite"].map { it.asDouble() }
        for (p in Prayer.values().filter { !it.curse }) {
            if (Conformance.stopRequested) break
            if (!only(p.name)) continue
            val from = run.world.currentCycle
            val node =
                ObjectMapper().createObjectNode().apply {
                    put("slot", p.slot)
                    put("name", p.name)
                    putObject("level").put("novite", levels.getOrNull(p.slot) ?: -1)
                    putObject("drain_s_per_tenth").put("novite", drains.getOrNull(p.slot) ?: -1.0)
                }
            try {
                activation(run, bot, node, ref, "prayer ${p.name.lowercase()}", curse = false)
            } catch (e: Throwable) {
                run.add(Result("prayer ${p.name.lowercase()}", "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
            }
            run.window("prayer ${p.name.lowercase()}", from)
        }
        if (filter.isEmpty()) {
            val from = run.world.currentCycle
            pairs(run, bot, Prayer.values().filter { !it.curse }.map { it.slot to it.name }, normal["groups_novite"].map { g -> g.map { it.asInt() } }, "prayer pairs", curse = false)
            run.window("prayer pairs", from)
        }
        listOf(
            "headicons (overhead icon per deflect/wrath/soul split/protect prayer)",
            "quick curses / quick prayers setup and toggle",
            "berserker: slower stat decay",
            "protect item: items kept on death",
            "deflect summoning vs familiar attacks",
            "curses vs npcs (sap/leech procs, deflect protection %)",
            "prayer bonus effect on drain",
            "normal prayer stat boosts in the combat formulas (accuracy/max hit statistics)",
            "smite, retribution, redemption effects",
            "altar unlock and book switch through the real objects",
        ).forEach { run.notTested("prayers", it, "no scenario yet") }
    }

    // ---- activation, level gate, drain ------------------------------------------------------------------------------------

    private suspend fun QueueTask.activation(
        run: Conformance.Run,
        bot: Client,
        c: JsonNode,
        ref: JsonNode,
        e: String,
        curse: Boolean,
    ) {
        val world = run.world
        val slot = c["slot"].asInt()
        val level = c["level"]?.get("novite")?.asInt() ?: c["level"]?.get("divergent")?.asInt()
        offAll(bot, curse)
        wait(1)
        // Level gate.
        if (level != null && level > 1) {
            bot.skills.setBaseLevel(Skills.PRAYER, level - 1)
            bot.setCurrentPrayerPoints(level - 1)
            val c0 = world.currentCycle
            Bots.button(bot, BOOK, BOOK_GRID, 1, slot)
            wait(2)
            run.expectTrue(e, "refused at prayer level ${level - 1}", !active(bot, slot, curse), "active=${active(bot, slot, curse)}", "stays off")
            val said = messages(run, c0)
            run.expectTrue(e, "level refusal message names the level", said.any { it.contains("$level") }, said.joinToString(" / ").ifEmpty { "(no message)" }, "a message with $level")
            run.check(e, "level refusal message text", said.lastOrNull()?.let { text(it) }, "novite" to ref["level_message_novite"].asText().replace("{level}", "$level"))
            offAll(bot, curse)
        }
        bot.skills.setBaseLevel(Skills.PRAYER, 99)
        bot.setCurrentPrayerPoints(99)
        bot.equipment.removeAll()
        bot.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        // Activation.
        val c1 = world.currentCycle
        Bots.button(bot, BOOK, BOOK_GRID, 1, slot)
        wait(2)
        run.expectTrue(e, "activates at level 99", active(bot, slot, curse), "active=${active(bot, slot, curse)}")
        val out = run.recording.of("Conf1", "out").filter { it.cycle >= c1 }
        val anims = out.filter { it.text.startsWith("anim ") }.map { idOf(it.text) }
        val gfx = out.filter { it.text.startsWith("gfx ") }.map { idOf(it.text) }
        c["activation"]?.let { a ->
            run.check(e, "activation animation", anims.firstOrNull(), "divergent" to a["anim"]?.get("divergent")?.asInt())
            run.check(e, "activation graphic", gfx.firstOrNull(), "divergent" to a["gfx"]?.get("divergent")?.asInt())
        }
        activationSound(run, e, c, c1, anims, gfx)
        // Drain over 30 s at prayer bonus 0 (no equipment).
        c["drain_s_per_tenth"]?.get("novite")?.asDouble()?.takeIf { it > 0 }?.let { rate ->
            // Points are whole numbers (varbit 9816); 30 s gives 1.6 to 15 points, judged within 1 point.
            val p0 = bot.getCurrentPrayerPoints()
            val ms = 30_000L
            wait((ms / world.gameContext.cycleTime).toInt())
            val points = p0 - bot.getCurrentPrayerPoints()
            val expected = ms / 1000.0 / rate / 10.0
            run.check(e, "drain in 30 s (prayer points, bonus 0)", points, "novite" to "%.1f".format(java.util.Locale.ENGLISH, expected), same = { a, b -> a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 1.0 })
        }
        // Off again (click), and it really goes off.
        Bots.button(bot, BOOK, BOOK_GRID, 1, slot)
        wait(2)
        run.expectTrue(e, "deactivates on a second click", !active(bot, slot, curse), "active=${active(bot, slot, curse)}")
        bot.setCurrentPrayerPoints(99)
    }

    /** Is the sound of the activation audible, and is it the cache's own synth for that curse (gfx/anim sequence frame sound)? */
    private fun activationSound(
        run: Conformance.Run,
        e: String,
        c: JsonNode,
        from: Int,
        anims: List<Int>,
        gfx: List<Int>,
    ) {
        val world = run.world
        val sent =
            run.recording.of("Conf1", "msg").filter { it.cycle >= from && it.text.contains("Sound") && !it.text.startsWith("SoundSongEnd") }
                .mapNotNull { Regex("(?:sound|id)=(\\d+)").find(it.text)?.groupValues?.get(1)?.toInt() }
        val seqs = anims + gfx.mapNotNull { g -> runCatching { world.definitions.getNullable(gg.rsmod.game.fs.def.SpotAnimDef::class.java, g)?.sequence }.getOrNull() }
        val frame = seqs.mapNotNull { s -> runCatching { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, s) }.getOrNull()?.takeIf { it.hasAudibleFrameSounds }?.firstAudibleFrameSound }
        run.expectTrue(e, "sound: activation audible", sent.isNotEmpty() || frame.isNotEmpty(), "sent=$sent frameSounds=$frame (anims=$anims gfx=$gfx)", "a sent sound or a frame sound")
        c["cache_sound"]?.let { cs ->
            val synth = cs["synth"].asInt()
            run.expectTrue(e, "sound: the cache's own synth $synth plays (gfx ${cs["gfx"].asInt()} seq ${cs["seq"].asInt()})", synth in frame || synth in sent, "sent=$sent frameSounds=$frame", "$synth (667 cache)")
            run.expectTrue(e, "sound: not heard twice", !(synth in frame && synth in sent), "sent=$sent frameSounds=$frame", "the frame sound is not also sent")
        }
        sent.filter { !world.soundExists(it) }.forEach { run.add(Result(e, "sound: id $it exists in the 667 cache", Verdict.FAIL, "exists", "missing")) }
    }

    /** Every ordered pair: A on, then B on - A must stay on unless A and B share a Novite group. */
    private suspend fun QueueTask.pairs(
        run: Conformance.Run,
        bot: Client,
        entries: List<Pair<Int, String>>,
        groups: List<List<Int>>,
        e: String,
        curse: Boolean,
    ) {
        bot.skills.setBaseLevel(Skills.PRAYER, 99)
        val wrong = ArrayList<String>()
        var tested = 0
        for ((a, an) in entries) for ((b, bn) in entries) {
            if (a == b || Conformance.stopRequested) continue
            offAll(bot, curse)
            bot.setCurrentPrayerPoints(99)
            Bots.button(bot, BOOK, BOOK_GRID, 1, a)
            wait(1)
            if (!active(bot, a, curse)) continue
            Bots.button(bot, BOOK, BOOK_GRID, 1, b)
            wait(1)
            tested++
            val shouldClose = groups.any { a in it && b in it }
            val stillOn = active(bot, a, curse)
            if (stillOn == shouldClose) wrong += "$an then $bn: $an ${if (stillOn) "stayed on" else "turned off"} (novite: ${if (shouldClose) "off" else "on"})"
        }
        offAll(bot, curse)
        run.expectTrue(e, "exclusive groups over $tested ordered pairs", wrong.isEmpty(), if (wrong.isEmpty()) "all as novite" else "${wrong.size} differ: " + wrong.take(25).joinToString(" | "), "novite closePrayers groups")
    }

    // ---- combat effects (PvP) -----------------------------------------------------------------------------------------------

    private suspend fun QueueTask.combat(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
        c: JsonNode,
        ref: JsonNode,
        e: String,
    ) {
        val slot = c["slot"].asInt()
        val proc = c["proc"]
        val deflect = c["deflect"]
        val soul = c["soul_split"]
        val turmoil = c["turmoil"]
        if (proc == null && deflect == null && soul == null && turmoil == null) return
        for (p in listOf(bot, target)) {
            offAll(p, true)
            p.skills.setBaseLevel(Skills.PRAYER, 99)
            p.setCurrentPrayerPoints(99)
            p.moveTo(if (p === bot) arena else arena.transform(1, 0))
            p.resetInteractions()
            Combat.reset(p)
        }
        AncientCurses.unlock(target)
        wait(1)
        val attacker = if (deflect != null) target else bot
        val defender = if (deflect != null) bot else target
        val style = (proc?.get("hit_types")?.firstOrNull()?.asText() ?: deflect?.get("hit_type")?.asText() ?: "melee")
        arm(attacker, style)
        Bots.button(bot, BOOK, BOOK_GRID, 1, slot)
        wait(1)
        if (!active(bot, slot, true)) {
            run.add(Result(e, "combat: curse on for the fight", Verdict.FAIL, "on", "off"))
            return
        }
        val c0 = run.world.currentCycle
        val hp0 = defender.getCurrentLifepoints()
        val lp = HashMap<Int, Int>()
        var hits = 0
        var guard = 0
        // Only the attacker fights: auto retaliation turned a caster into a staff-basher (Deflect Magic saw melee hits).
        for (p in listOf(bot, target)) p.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        castIfMagic(attacker, defender, style)
        attacker.attack(defender)
        while (hits < HITS && guard++ < HITS * 8) {
            bot.setCurrentPrayerPoints(99)
            target.setCurrentPrayerPoints(99)
            for (p in listOf(bot, target)) if (p.getCurrentLifepoints() < p.getMaximumLifepoints() / 2) p.setCurrentLifepoints(p.getMaximumLifepoints())
            lp[run.world.currentCycle] = bot.getCurrentLifepoints()
            wait(1)
            hits = run.recording.of(if (defender === bot) "Conf1" else "Conf2", "out").count { it.cycle >= c0 && it.text.startsWith("hits ") }
            // A manual cast ends the attack: cast again once the previous cast is spent and the attack delay is over.
            val castDone = style == "magic" && !attacker.attr.has(Combat.CASTING_SPELL) && !attacker.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)
            if (attacker.getCombatTargetOrNull() == null || castDone) {
                castIfMagic(attacker, defender, style)
                attacker.attack(defender)
            }
        }
        attacker.resetInteractions()
        Combat.reset(attacker)
        for (p in listOf(bot, target)) p.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
        val defenderLabel = if (defender === bot) "Conf1" else "Conf2"
        val splats =
            run.recording.of(defenderLabel, "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") }
                .flatMap { Regex("type=(\\d+) dmg=(\\d+)").findAll(it.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
        val landed = splats.filter { it.second > 0 && it.first != gg.rsmod.plugins.api.HitType.REFLECTED.id }
        run.expectTrue(e, "combat: the fight produced hits", landed.size >= 20, "hitsplats=${splats.size} landed=${landed.size} (defender hp0 $hp0)", ">= 20 landed hits of $HITS")
        if (proc != null) procChecks(run, bot, target, c, proc, ref, landed.size, c0, e)
        if (deflect != null) deflectChecks(run, deflect, landed, c0, e)
        if (soul != null) soulSplitChecks(run, soul, c0, e)
        if (turmoil != null) turmoilChecks(run, bot, target, turmoil, ref, e)
        Bots.button(bot, BOOK, BOOK_GRID, 1, slot)
        wait(1)
        offAll(bot, true)
    }

    private fun procChecks(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        c: JsonNode,
        proc: JsonNode,
        ref: JsonNode,
        landed: Int,
        c0: Int,
        e: String,
    ) {
        val casterAnim = proc["caster_anim"]?.get("novite")?.asInt()
        val procs = run.recording.of("Conf1", "out").filter { it.cycle >= c0 && it.text.startsWith("anim ") && idOf(it.text) == casterAnim }
        val chance = proc["chance"]?.get("novite")?.asDouble()
        if (chance != null && landed >= 20) {
            val rate = procs.size.toDouble() / landed
            val sigma = Math.sqrt(chance * (1 - chance) / landed)
            // Novite: one proc blocks the next until its effect lands (boostedLeech); the observed rate may sit a little lower.
            val ok = Math.abs(rate - chance) <= 3 * sigma + 0.02
            run.add(Result(e, "proc rate over $landed landed hits", if (ok) Verdict.PASS else Verdict.FAIL, "%.3f (novite)".format(java.util.Locale.ENGLISH, chance), "%.3f (${procs.size} procs)".format(java.util.Locale.ENGLISH, rate)))
        }
        if (procs.isEmpty()) return
        val first = procs.first().cycle
        val casterGfx = run.recording.of("Conf1", "out").filter { it.cycle in first..first + 1 && it.text.startsWith("gfx ") }.map { idOf(it.text) }
        proc["caster_gfx"]?.let { g -> run.check(e, "proc caster graphic", casterGfx.firstOrNull(), "novite" to g["novite"]?.asInt(), "divergent" to g["divergent"]?.asInt()) }
        val projs = run.recording.events.filter { it.cycle in first..first + 1 && it.kind == "proj" }.map { idOf(it.text.replace("gfx=", "id=")) }
        run.check(e, "proc projectile", projs.firstOrNull(), "novite" to proc["projectile"]?.get("novite")?.asInt(), "divergent" to proc["projectile"]?.get("divergent")?.asInt())
        val targetGfx = run.recording.of("Conf2", "out").filter { it.cycle in first..first + 4 && it.text.startsWith("gfx ") }.map { idOf(it.text) }
        run.check(e, "proc target graphic", targetGfx.firstOrNull(), "novite" to proc["target_gfx"]?.get("novite")?.asInt(), "divergent" to proc["target_gfx"]?.get("divergent")?.asInt())
        // Stat varbits 6857 + stat = 30 + percent (Novite adjustStat): the client shows the curse bonus from these.
        val base = ref["stat_varbit_base"].asInt()
        val neutral = ref["stat_varbit_neutral"].asInt()
        val statIndex = mapOf("attack" to 0, "strength" to 1, "defence" to 2, "ranged" to 3, "magic" to 4)
        proc["stats"]?.forEach { s ->
            val idx = statIndex[s.asText()] ?: return@forEach
            val v = runCatching { bot.getVarbit(base + idx) }.getOrNull()
            val cap = proc["cap_percent"]?.get("novite")?.asInt()
            run.expectTrue(e, "stat varbit ${base + idx} (${s.asText()}) shows a bonus after ${procs.size} procs", v != null && v > neutral, "varbit=${v}", "> $neutral (30 = no bonus, novite adjustStat)")
            if (cap != null && v != null) run.expectTrue(e, "stat varbit ${base + idx} (${s.asText()}) within the cap", v <= neutral + cap, "varbit=$v", "<= ${neutral + cap}")
        }
        val said = messages(run, c0)
        run.expectTrue(e, "proc message to the caster", said.any { it.contains("drains", true) || it.contains("weakened", true) }, said.take(3).joinToString(" / ").ifEmpty { "(none)" }, "Novite: \"Your curse drains ... from the enemy, boosting your ...\"")
        proc["run_energy_transfer"]?.let { run.add(Result(e, "leech energy transfer", Verdict.NOT_TESTED, "${it["novite"].asInt()} % (novite)", "run energy of both bots is in the timeline")) }
        proc["special_transfer"]?.let { run.add(Result(e, "leech special transfer", Verdict.NOT_TESTED, "${it["novite"].asInt()} % (novite)", "special energy is in the timeline")) }
        if (target.getCurrentLifepoints() <= 0) run.note("target died during $e")
    }

    private fun deflectChecks(
        run: Conformance.Run,
        deflect: JsonNode,
        landed: List<Pair<Int, Int>>,
        c0: Int,
        e: String,
    ) {
        val reflected =
            run.recording.of("Conf2", "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") }
                .flatMap { Regex("type=(\\d+) dmg=(\\d+)").findAll(it.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
                .filter { it.first == gg.rsmod.plugins.api.HitType.REFLECTED.id }
        // The splat is after the PvP deflect reduction (60 % kept): a hit that could reflect (>= 10 before it) shows >= 6.
        val share = if (landed.isEmpty()) 0.0 else reflected.size.toDouble() / landed.count { it.second >= 6 }.coerceAtLeast(1)
        run.add(
            Result(
                // Owner balance decision 2026-09-18 (AncientCurses.DEFLECT_REFLECT_CHANCE_PCT): 15 % chance per attack, not Novite's every
                // hit - a share between 3 % and 35 % over the qualifying hits passes (combat run 2026-09-29).
                e, "reflected on damaging hits (>= 1 lifepoint of 10 %)", if (share in 0.03..0.35) Verdict.PASS else Verdict.FAIL,
                "about 15 % of the damaging hits reflect ${deflect["reflect_fraction"]?.get("novite")?.asDouble()} of their damage (owner 2026-09-18)",
                "${reflected.size} reflects on ${landed.count { it.second >= 6 }} hits (${"%.2f".format(java.util.Locale.ENGLISH, share)})",
            ),
        )
        val anim = deflect["anim"]?.get("novite")?.asInt()
        val defAnims = run.recording.of("Conf1", "out").filter { it.cycle >= c0 && it.text.startsWith("anim ") }.map { idOf(it.text) }
        if (reflected.isNotEmpty()) {
            run.expectTrue(e, "reflect animation $anim", anim in defAnims, "anims=${defAnims.distinct()}", "$anim (novite)")
            val defGfx = run.recording.of("Conf1", "out").filter { it.cycle >= c0 && it.text.startsWith("gfx ") }.map { idOf(it.text) }.distinct()
            run.check(e, "reflect graphic", defGfx.firstOrNull { it in 2228..2230 } ?: defGfx.firstOrNull(), "novite" to deflect["gfx"]?.get("novite")?.asInt(), "divergent" to deflect["gfx"]?.get("divergent")?.asInt())
        }
    }

    private fun soulSplitChecks(
        run: Conformance.Run,
        soul: JsonNode,
        c0: Int,
        e: String,
    ) {
        val heals =
            run.recording.of("Conf1", "state").filter { it.cycle >= c0 && it.text.startsWith("hp: ") }
                .mapNotNull { Regex("hp: (\\d+)/\\d+ -> (\\d+)/").find(it.text)?.let { m -> m.groupValues[2].toInt() - m.groupValues[1].toInt() } }
                .filter { it > 0 }
        val dealt = run.recording.of("Conf2", "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") }.flatMap { damages(it.text) }.filter { it > 0 }
        val fraction = soul["heal_fraction"]?.get("divergent")?.asDouble()
        if (dealt.isNotEmpty()) {
            val measured = heals.sum().toDouble() / dealt.sum()
            run.check(e, "soul split: healed share of damage dealt", "%.2f".format(java.util.Locale.ENGLISH, measured), "divergent" to fraction?.let { "%.2f".format(java.util.Locale.ENGLISH, it) }, same = { a, b -> a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 0.05 })
        }
        val projs = run.recording.events.filter { it.cycle >= c0 && it.kind == "proj" }.map { idOf(it.text.replace("gfx=", "id=")) }.distinct()
        run.add(Result(e, "soul split projectiles seen", if (projs.isNotEmpty()) Verdict.PASS else Verdict.FAIL, "a projectile per damaging hit", "$projs"))
    }

    private fun turmoilChecks(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        turmoil: JsonNode,
        ref: JsonNode,
        e: String,
    ) {
        val base = ref["stat_varbit_base"].asInt()
        val neutral = ref["stat_varbit_neutral"].asInt()
        // Novite increaseTurmoilBonus at a 99 target: attack floor(0.15*99)*100/99 = 14, defence 14, strength floor(9.9)*100/99 = 9.
        val expected = mapOf(0 to 14, 1 to 9, 2 to 14)
        for ((stat, pct) in expected) {
            run.check(e, "turmoil stat varbit ${base + stat} vs a level-99 target", runCatching { bot.getVarbit(base + stat) }.getOrNull(), "novite" to neutral + pct)
        }
        if (target.skills.getMaxLevel(Skills.ATTACK) != 99) run.note("turmoil target not 99")
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------------

    private fun arm(
        p: Client,
        style: String,
    ) {
        p.equipment.removeAll()
        when (style) {
            "range" -> {
                p.equipment[3] = Item(Items.MAGIC_SHORTBOW)
                p.equipment[13] = Item(Items.RUNE_ARROW, 5000)
            }
            "magic" -> p.equipment[3] = Item(Items.STAFF_OF_FIRE)
            else -> p.equipment[3] = Item(Items.ABYSSAL_WHIP)
        }
        p.addBlock(UpdateBlockType.APPEARANCE)
        // Combat run 2026-09-29: the weapon was put in the slot without the weapon-type refresh, so the bot kept fighting with its
        // fists (Deflect Missiles/Magic and Sap/Leech Ranger/Mage measured 0 procs against melee hits).
        p.sendWeaponComponentInformation()
        p.skills.setBaseLevel(Skills.MAGIC, 99)
        p.skills.setBaseLevel(Skills.RANGED, 99)
        p.setVarbit(gg.rsmod.plugins.content.magic.MagicSpells.INF_RUNES_VARBIT, 1)
    }

    /** A manual Fire Bolt on [target] as the client sends it (spell + manual target), when [style] is magic. */
    private fun castIfMagic(
        p: Client,
        target: Client,
        style: String,
    ) {
        if (style != "magic") return
        // The route a spell-on-player click takes (Autocast.markManualCast): spell, manual target, no autocast state.
        gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(p, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.values.firstOrNull { it.name.contains("FIRE_BOLT") } ?: gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.values.first(), target)
    }

    private fun active(
        p: Client,
        slot: Int,
        curse: Boolean,
    ): Boolean =
        if (!curse) {
            Prayer.values().firstOrNull { !it.curse && it.slot == slot }?.let { Prayers.isActive(p, it) } ?: false
        } else {
            when (slot) {
                AncientCurse.PROTECT_ITEM_SLOT -> Prayers.isActive(p, Prayer.PROTECT_ITEM)
                AncientCurse.TURMOIL_SLOT -> AncientCurses.isTurmoilActive(p)
                else -> AncientCurse.values().firstOrNull { it.slot == slot }?.let { AncientCurses.isCurseActive(p, it) } ?: false
            }
        }

    private fun offAll(
        p: Client,
        curse: Boolean,
    ) {
        if (curse) AncientCurses.deactivateAllCurses(p) else Prayers.deactivateAll(p)
        if (curse && Prayers.isActive(p, Prayer.PROTECT_ITEM)) Prayers.deactivateAll(p)
    }

    private fun messages(
        run: Conformance.Run,
        from: Int,
    ) = run.recording.of("Conf1", "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }.map { it.text }

    private fun text(msg: String) = Regex("message=(.*?)(, \\w+=|\\)$)").find(msg)?.groupValues?.get(1)

    private fun idOf(text: String): Int = Regex("\\bid=(-?\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: -1

    private fun damages(text: String): List<Int> = Regex("dmg=(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.toList()

    private fun gg.rsmod.game.model.entity.Pawn.getCombatTargetOrNull() = runCatching { getCombatTarget() }.getOrNull()
}
