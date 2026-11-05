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
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getPrayerBonus
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.refreshBonuses
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
    private val REF = gg.rsmod.game.RspsPaths.root("reference/prayer/curses.json")
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
            // N503 D1 (owner 2026-11-01): Protect Item no longer activates - the click is refused (protectItemDeath checks it).
            if (c["slot"].asInt() == AncientCurse.PROTECT_ITEM_SLOT) {
                protectItemRefused(run, bot, "curse ${name.lowercase()}", curse = true)
                run.window("curse ${name.lowercase()}", from)
                continue
            }
            try {
                activation(run, bot, c, ref, "curse ${name.lowercase()}", curse = true)
            } catch (e: Throwable) {
                run.add(Result("curse ${name.lowercase()}", "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
            }
            run.window("curse ${name.lowercase()}", from)
        }
        if (filter.isEmpty()) {
            val from = run.world.currentCycle
            pairs(run, bot, curses.map { it["slot"].asInt() to it["name"].asText() }, closesOf(ref["closes_rules_owner_n47"] ?: ref["closes_rules"]), "curse pairs", curse = true, source = if (ref.has("closes_rules_owner_n47")) "owner N47 (VRAGEN 11 = A): Sap and Leech never together" else "closes_rules: 2011 KB, owner 2026-09-18, Darkan")
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
            if (p == Prayer.PROTECT_ITEM) {
                protectItemRefused(run, bot, "prayer ${p.name.lowercase()}", curse = false)
                run.window("prayer ${p.name.lowercase()}", from)
                continue
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
            pairs(run, bot, Prayer.values().filter { !it.curse }.map { it.slot to it.name }, closesOf(normal["closes_darkan"]), "prayer pairs", curse = false, source = "Darkan activatePrayer close lists")
            run.window("prayer pairs", from)
        }
        // The scenarios that were NOT_TESTED until 2026-10-09 (the owner: "i saw ancient curses failures can we fix them").
        if (filter.isEmpty()) extras(run, bot, target, arena, ref)
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
            // The 667 cache's own line for this slot (struct param 738); Novite/Darkan's "... at least N to use this prayer." is theirs.
            val cacheText = (if (curse) ref["level_message_cache_curses"] else ref["normal_prayers"]["level_message_cache"])?.get("$slot")?.asText()
            run.check(e, "level refusal message text", said.lastOrNull()?.let { text(it) }, "cache struct 738" to cacheText)
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
        // An owner decision wins over Novite (Turmoil drains like Piety, balance plan 2026-10-31).
        val drain = c["drain_s_per_tenth"]
        val owner = drain?.get("owner_2026_10_31") != null
        (drain?.get("owner_2026_10_31") ?: drain?.get("novite"))?.asDouble()?.takeIf { it > 0 }?.let { rate ->
            // Points are whole numbers (varbit 9816); 30 s gives 1.6 to 15 points, judged within 1 point.
            val p0 = bot.getCurrentPrayerPoints()
            val ms = 30_000L
            wait((ms / world.gameContext.cycleTime).toInt())
            val points = p0 - bot.getCurrentPrayerPoints()
            val expected = ms / 1000.0 / rate / 10.0
            run.check(e, "drain in 30 s (prayer points, bonus 0)", points, (if (owner) "owner 2026-10-31" else "novite") to "%.1f".format(java.util.Locale.ENGLISH, expected), same = { a, b -> a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 1.0 })
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
        // The caster is the local player of his own animation and graphic: the client plays their frame sounds of every radius,
        // radius 0 included (client Static431; reference\curses-audio-research\FINDINGS.md). Only radius > 0 reaches others.
        val frame = seqs.mapNotNull { s -> runCatching { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, s) }.getOrNull()?.firstFrameSound?.takeIf { it >= 0 } }
        run.expectTrue(e, "sound: activation audible", sent.isNotEmpty() || frame.isNotEmpty(), "sent=$sent frameSounds=$frame (anims=$anims gfx=$gfx)", "a sent sound or a frame sound")
        // The synth belongs to a graphic's sequence. Only Protect Item, Berserker and Turmoil show that graphic on activation; the
        // Sap/Leech caster graphic plays on a proc, the Deflect graphic on a reflect and the Wrath ring on death, so their synth
        // is judged there (procChecks / deflectChecks: the graphic carrying it is sent) and not on the activation click.
        // Owner 2026-10-09: every curse plays Darkan's activation animation + graphic and keeps the owner's by-ear sound. For the
        // Saps and Deflects that graphic carries the cache synth too, and the owner wants both heard.
        c["activation_darkan"]?.takeIf { c["activation"] == null }?.let { dk ->
            run.check(e, "activation animation (owner 2026-10-09: Darkan)", anims.firstOrNull(), "darkan" to dk["anim"].asInt())
            if (!dk["gfx"].isNull) run.check(e, "activation graphic (owner 2026-10-09: Darkan)", gfx.firstOrNull(), "darkan" to dk["gfx"].asInt())
            run.expectTrue(e, "sound: the owner's own activation sound is sent", sent.isNotEmpty(), "sent=$sent", "a sent sound (owner by ear)")
            c["cache_sound"]?.takeIf { cs -> !dk["gfx"].isNull && dk["gfx"].asInt() == cs["gfx"].asInt() }?.let { cs ->
                val synth = cs["synth"].asInt()
                run.expectTrue(e, "sound: the activation graphic's synth $synth is heard as well", synth in frame, "frameSounds=$frame", "$synth (owner: both sounds)")
            }
        }
        c["cache_sound"]?.takeIf { cs -> c["activation"]?.get("gfx")?.get("divergent")?.asInt() == cs["gfx"].asInt() }?.let { cs ->
            val synth = cs["synth"].asInt()
            run.expectTrue(e, "sound: the cache's own synth $synth plays (gfx ${cs["gfx"].asInt()} seq ${cs["seq"].asInt()})", synth in frame || synth in sent, "sent=$sent frameSounds=$frame", "$synth (667 cache)")
            run.expectTrue(e, "sound: not heard twice", !(synth in frame && synth in sent), "sent=$sent frameSounds=$frame", "the frame sound is not also sent")
        }
        sent.filter { !world.soundExists(it) }.forEach { run.add(Result(e, "sound: id $it exists in the 667 cache", Verdict.FAIL, "exists", "missing")) }
    }

    /** slot -> the slots its activation switches off, from a reference object. */
    private fun closesOf(node: JsonNode): Map<Int, Set<Int>> = node.fields().asSequence().associate { it.key.toInt() to it.value.map { v -> v.asInt() }.toSet() }

    /**
     * Every ordered pair: A on, then B on - A must go off exactly when B's activation switches A off ([closes]). The former
     * reference treated Novite's closePrayers groups as "shared membership closes", which is not how Novite's switchPrayer works
     * (each slot closes a list of groups) - reference\prayer\curses.json _about.
     */
    /** N503 D1: a click on Protect Item leaves it off and says why (ProtectItemDisabled). */
    private suspend fun QueueTask.protectItemRefused(
        run: Conformance.Run,
        bot: Client,
        e: String,
        curse: Boolean,
    ) {
        offAll(bot, curse)
        bot.setCurrentPrayerPoints(99)
        val from = run.world.currentCycle
        Bots.button(bot, BOOK, BOOK_GRID, 1, if (curse) AncientCurse.PROTECT_ITEM_SLOT else Prayer.PROTECT_ITEM.slot)
        wait(2)
        run.check(e, "stays off (D1: disabled in Deadman)", Prayers.isActive(bot, Prayer.PROTECT_ITEM), "owner 2026-11-01" to false)
        // N543 (owner 2026-11-02): the reason opens in the chatbox dialogue (message box 210), not as a chat line.
        val said = bot.interfaces.isVisible(210) ||
            run.recording.of(bot.username, "msg").filter { it.cycle >= from }.any { gg.rsmod.plugins.content.mechanics.prayer.ProtectItemDisabled.MESSAGE in it.text }
        run.expectTrue(e, "says why", said, "chatbox dialogue '${gg.rsmod.plugins.content.mechanics.prayer.ProtectItemDisabled.MESSAGE}'")
        bot.closeInterface(210)
    }

    private suspend fun QueueTask.pairs(
        run: Conformance.Run,
        bot: Client,
        entries: List<Pair<Int, String>>,
        closes: Map<Int, Set<Int>>,
        e: String,
        curse: Boolean,
        source: String,
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
            val shouldClose = a in (closes[b] ?: emptySet())
            val stillOn = active(bot, a, curse)
            if (stillOn == shouldClose) wrong += "$an then $bn: $an ${if (stillOn) "stayed on" else "turned off"} (reference: ${if (shouldClose) "off" else "on"})"
        }
        offAll(bot, curse)
        run.expectTrue(e, "exclusive groups over $tested ordered pairs", wrong.isEmpty(), if (wrong.isEmpty()) "all as the reference" else "${wrong.size} differ: " + wrong.take(25).joinToString(" | "), source)
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
        // Owner 2026-10-09: switching a curse on plays Darkan's animation, the same one a proc casts (12569 Sap, 12575 Leech);
        // the fight is measured from two ticks later, so the switch-on is never counted as a proc.
        wait(2)
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
            for (p in listOf(bot, target)) {
                if (soul != null && p === bot) {
                    // Soul Split only heals a caster below full health, and the caster takes no hits here, so the caster's
                    // hitpoints are kept between 20 and 80 % (conform curses 2026-10-09: "healed share 0.00" at full health). The
                    // reset is a drop, so every rise in the caster's hitpoints is a Soul Split heal.
                    if (p.getCurrentLifepoints() > p.getMaximumLifepoints() * 8 / 10) p.setCurrentLifepoints(p.getMaximumLifepoints() / 5)
                } else if (p.getCurrentLifepoints() < p.getMaximumLifepoints() / 2) {
                    p.setCurrentLifepoints(p.getMaximumLifepoints())
                }
            }
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
        // Proc frequency: the owner's balance rule (AncientCurses, 2026-09-18 "owner values, not 2011 values") replaces Novite's 2011
        // per-hit chance - one proc per caster per cooldown (45 s; Leech Special Attack 60 s). Measured: the first landed hits proc
        // (the per-hit roll still applies) and no two procs closer than the cooldown.
        val cooldown =
            if (c["name"]?.asText()?.contains("special", ignoreCase = true) == true) AncientCurses.LEECH_SPECIAL_COOLDOWN_TICKS
            else AncientCurses.SAP_LEECH_COOLDOWN_TICKS
        if (landed >= 20) {
            run.expectTrue(e, "at least one proc over $landed landed hits", procs.isNotEmpty(), "${procs.size} procs", ">= 1 (owner rule)")
            val gaps = procs.zipWithNext { a, b -> b.cycle - a.cycle }
            val closest = gaps.minOrNull()
            run.expectTrue(
                e, "procs at least $cooldown ticks apart", closest == null || closest >= cooldown,
                closest?.let { "closest $it ticks" } ?: "single proc", ">= $cooldown ticks (owner rule)",
            )
        }
        if (procs.isEmpty()) return
        val first = procs.first().cycle
        val casterGfx = run.recording.of("Conf1", "out").filter { it.cycle in first..first + 1 && it.text.startsWith("gfx ") }.map { idOf(it.text) }
        // Darkan decides where it has a value (owner 2026-10-04 "darkan is the way"; Novite and Divergent disagree on Leech Energy /
        // Special and Sap Spirit), else Novite and Divergent both.
        fun refs(node: JsonNode?): Array<Pair<String, Any?>> =
            node?.get("darkan")?.asInt()?.let { arrayOf("darkan" to it) } ?: arrayOf("novite" to node?.get("novite")?.asInt(), "divergent" to node?.get("divergent")?.asInt())
        proc["caster_gfx"]?.let { g -> run.check(e, "proc caster graphic", casterGfx.firstOrNull(), *refs(g)) }
        val projs = run.recording.events.filter { it.cycle in first..first + 1 && it.kind == "proj" }.map { idOf(it.text.replace("gfx=", "id=")) }
        run.check(e, "proc projectile", projs.firstOrNull(), *refs(proc["projectile"]))
        val targetGfx = run.recording.of("Conf2", "out").filter { it.cycle in first..first + 4 && it.text.startsWith("gfx ") }.map { idOf(it.text) }
        run.check(e, "proc target graphic", targetGfx.firstOrNull(), *refs(proc["target_gfx"]))
        // Stat varbits 6857 + stat = 30 + percent (Novite adjustStat): the client shows the curse bonus from these.
        val base = ref["stat_varbit_base"].asInt()
        val neutral = ref["stat_varbit_neutral"].asInt()
        val statIndex = mapOf("attack" to 0, "strength" to 1, "defence" to 2, "ranged" to 3, "magic" to 4)
        // Varbits 6857+ are the player's OWN stat change (cache clientscripts 1711 / 1697: value - 30, shown in the curse tab;
        // Darkan PrayerManager.updateStatMod, Novite adjustStat). A Leech boosts the caster; a Sap boosts nobody and drains the
        // victim (Darkan Sap / Novite: the target's stat mod goes down), so a Sap is judged on the victim's tab.
        val sap = c["name"]?.asText()?.startsWith("SAP_") == true
        proc["stats"]?.forEach { s ->
            val idx = statIndex[s.asText()] ?: return@forEach
            val cap = proc["cap_percent"]?.get("novite")?.asInt()
            if (sap) {
                val v = runCatching { target.getVarbit(base + idx) }.getOrNull()
                run.expectTrue(e, "victim's stat varbit ${base + idx} (${s.asText()}) shows the drain after ${procs.size} procs", v != null && v < neutral, "varbit=${v}", "< $neutral (30 = no change; Darkan Sap / Novite: the target's stat mod)")
                if (cap != null && v != null) run.expectTrue(e, "victim's stat varbit ${base + idx} (${s.asText()}) within the cap", v >= neutral - cap, "varbit=$v", ">= ${neutral - cap}")
                // Owner curse table 2026-10-11: a Sap now boosts its caster (Sap Warrior Attack + Strength, Sap Ranger Ranged, Sap
                // Mage Magic, never Defence) and the caster's own tab shows exactly that boost in levels.
                val skill = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)[idx]
                val selfPct = if (AncientCurses.sapMultiplier(bot, skill) > 1.0) AncientCurses.SAP_SELF_BOOST_PCT else 0
                val expected = neutral + Math.round(selfPct * bot.skills.getMaxLevel(skill) / 100.0).toInt()
                val own = runCatching { bot.getVarbit(base + idx) }.getOrNull()
                run.expectTrue(e, "caster's own stat varbit ${base + idx} (${s.asText()}) shows the Sap's own boost", own == expected, "varbit=$own", "$expected (owner 2026-10-11: +$selfPct %)")
            } else {
                val v = runCatching { bot.getVarbit(base + idx) }.getOrNull()
                run.expectTrue(e, "stat varbit ${base + idx} (${s.asText()}) shows a bonus after ${procs.size} procs", v != null && v > neutral, "varbit=${v}", "> $neutral (30 = no bonus, novite adjustStat)")
                // Owner 2026-10-11: the varbit is levels (cs2 1711) and Leech Ranged / Magic carry the owner's Rigour / Augury
                // modifier, so the cap is AncientCurses' own (modifier above the client's 5 % + the +5 leeched levels).
                val skill = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)[idx]
                val ownCap = AncientCurses.leechStatVarbitMax(skill, bot.skills.getMaxLevel(skill))
                if (v != null) run.expectTrue(e, "stat varbit ${base + idx} (${s.asText()}) within the cap", v <= neutral + ownCap, "varbit=$v", "<= ${neutral + ownCap}")
            }
        }
        val said = messages(run, c0)
        run.expectTrue(e, "proc message to the caster", said.any { it.contains("drains", true) || it.contains("weakened", true) }, said.take(3).joinToString(" / ").ifEmpty { "(none)" }, "Novite: \"Your curse drains ... from the enemy, boosting your ...\"")
        // The Leech Energy / Leech Special Attack amounts are measured per proc in the "curse leech transfers" scenario.
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
        // The 667 client shows Turmoil from varbits 6844 / 6845 / 6846 (the opponent part in levels; clientscript 1710 adds
        // 15 / 23 / 15 % and 1714's level x 100 / own level while varbit 6839 is on, and never reads 6857+ then). Novite and Darkan
        // write it into 6857+ (their adjustStat / setStatMod), which this client does not read while Turmoil is on.
        // At a level-99 target: floor(0.15 x 99) = 14 attack, floor(0.10 x 99) = 9 strength, 14 defence (Novite, Darkan Turmoil).
        val levels = ref["turmoil_bonus_varbits_cache"]
        val expected = mapOf(0 to 14, 1 to 9, 2 to 14)
        for ((stat, bonus) in expected) {
            val varbit = levels[stat].asInt()
            run.check(e, "turmoil bonus varbit $varbit vs a level-99 target", runCatching { bot.getVarbit(varbit) }.getOrNull(), "cache cs2 1710" to bonus)
        }
        if (target.skills.getMaxLevel(Skills.ATTACK) != 99) run.note("turmoil target not 99")
    }

    // ---- the scenarios that were NOT_TESTED (conform curses 2026-10-09) ---------------------------------------------------

    private suspend fun QueueTask.scenario(
        run: Conformance.Run,
        e: String,
        block: suspend QueueTask.() -> Unit,
    ) {
        if (Conformance.stopRequested) return
        val from = run.world.currentCycle
        run.progress(e)
        try {
            block()
        } catch (t: Throwable) {
            run.add(Result(e, "scenario", Verdict.FAIL, "runs to the end", "threw $t"))
        }
        run.window(e, from)
    }

    private suspend fun QueueTask.extras(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
        ref: JsonNode,
    ) {
        // The bots know the curses already (the curse scenarios above); only the foundation quests may grant them.
        for (p in listOf(bot, target)) {
            p.skills.setBaseLevel(Skills.PRAYER, 99)
            p.setCurrentPrayerPoints(99)
        }
        scenario(run, "prayers headicons") { headicons(run, bot, ref) }
        scenario(run, "prayers quick prayers and quick curses") { quickPrayers(run, bot) }
        scenario(run, "curse berserker decay") { berserkerDecay(run, bot) }
        scenario(run, "prayers protect item on death") { protectItemDeath(run, bot) }
        scenario(run, "curse deflect summoning vs familiars") { deflectSummoning(run, bot) }
        scenario(run, "prayers prayer bonus and drain") { bonusDrain(run, bot) }
        scenario(run, "prayers stat boosts in the combat formulas") { formulaBoosts(run, bot, target) }
        scenario(run, "curse leech transfers") { leechTransfers(run, bot, target, arena) }
        scenario(run, "curse leech on a potion boost") { leechOnPotion(run, bot, target, arena) }
        scenario(run, "curses vs npcs") { cursesVsNpcs(run, bot, target, arena) }
        scenario(run, "prayers altar book switch") { altarSwitch(run, bot, arena) }
        scenario(run, "prayer smite") { smite(run, bot, target, arena) }
        scenario(run, "prayer redemption") { redemption(run, bot, target, arena) }
        // Last: the target dies here.
        scenario(run, "prayer retribution") { retribution(run, bot, target, arena) }
    }

    private suspend fun QueueTask.book(
        p: Client,
        ancient: Boolean,
    ) {
        val want = if (ancient) AncientCurses.PrayerBook.ANCIENT else AncientCurses.PrayerBook.NORMAL
        if (AncientCurses.getBook(p) != want) AncientCurses.switchBook(p, want)
        offAll(p, ancient)
        p.setCurrentPrayerPoints(99)
        wait(1)
    }

    private suspend fun QueueTask.click(
        p: Client,
        slot: Int,
    ) {
        Bots.button(p, BOOK, BOOK_GRID, 1, slot)
        wait(1)
    }

    /** Fighting position, no auto retaliation, combat state cleared. */
    private fun place(
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        for (p in listOf(bot, target)) {
            p.moveTo(if (p === bot) arena else arena.transform(1, 0))
            p.resetInteractions()
            Combat.reset(p)
            p.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        }
    }

    private fun unplace(vararg ps: Client) = ps.forEach { it.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0) }

    /** Head icon per overhead prayer / curse, alone and beside the Summoning protection (Novite getPrayerHeadIcon = the cache sheet). */
    private suspend fun QueueTask.headicons(
        run: Conformance.Run,
        bot: Client,
        ref: JsonNode,
    ) {
        val e = "prayers headicons"
        val icons = ref["headicons_novite"]
        for (ancient in listOf(false, true)) {
            book(bot, ancient)
            val single = icons[if (ancient) "curses" else "normal"]
            val combo = icons[if (ancient) "curses_with_deflect_summoning" else "normal_with_summoning"]
            val summoning = if (ancient) AncientCurse.DEFLECT_SUMMONING.slot else Prayer.PROTECT_FROM_SUMMONING.slot
            val book = if (ancient) "curse" else "prayer"
            for (f in single.fields()) {
                offAll(bot, ancient)
                wait(1)
                click(bot, f.key.toInt())
                run.check(e, "$book slot ${f.key} alone", bot.prayerIcon, "novite" to f.value.asInt())
            }
            for (f in combo.fields()) {
                offAll(bot, ancient)
                wait(1)
                click(bot, summoning)
                click(bot, f.key.toInt())
                run.check(e, "$book slot ${f.key} with slot $summoning", bot.prayerIcon, "novite" to f.value.asInt())
            }
            offAll(bot, ancient)
        }
    }

    /** Quick prayers / curses: the set obeys the same exclusions as activation, the orb turns exactly that set on, then off. */
    private suspend fun QueueTask.quickPrayers(
        run: Conformance.Run,
        bot: Client,
    ) {
        val e = "prayers quick prayers and quick curses"
        book(bot, false)
        Prayer.values().filter { !it.curse }.forEach { bot.setVarbit(it.qpVarbit, 0) }
        for (p in listOf(Prayer.INCREDIBLE_REFLEXES, Prayer.PROTECT_FROM_MELEE, Prayer.MYSTIC_MIGHT)) {
            Bots.button(bot, BOOK, 42, 1, p.slot)
            wait(1)
        }
        val chosen = Prayer.values().filter { !it.curse && Prayers.isQuickPrayerSelected(bot, it) }.toSet()
        // Darkan: Mystic Might closes the attack tiers, so choosing it drops Incredible Reflexes from the set.
        run.expectTrue(
            e, "choosing Mystic Might drops Incredible Reflexes from the set", chosen == setOf(Prayer.PROTECT_FROM_MELEE, Prayer.MYSTIC_MIGHT),
            "selected=$chosen", "Protect from Melee + Mystic Might (Darkan close lists)",
        )
        Bots.button(bot, BOOK, 43, 1)
        wait(1)
        Bots.button(bot, 749, 1, Prayers.QUICK_PRAYERS_TOGGLE_OPTION)
        wait(1)
        val on = Prayer.values().filter { !it.curse && Prayers.isActive(bot, it) }.toSet()
        run.expectTrue(e, "the orb turns on exactly the quick prayers", on == chosen, "active=$on")
        Bots.button(bot, 749, 1, Prayers.QUICK_PRAYERS_TOGGLE_OPTION)
        wait(1)
        run.expectTrue(e, "a second click turns them off", Prayer.values().none { !it.curse && Prayers.isActive(bot, it) }, "active=${Prayer.values().filter { Prayers.isActive(bot, it) }}")

        book(bot, true)
        (0..19).forEach { bot.setVarbit(AncientCurse.QUICK_VARBIT_BASE + it, 0) }
        for (slot in listOf(AncientCurse.SAP_WARRIOR.slot, AncientCurse.DEFLECT_MELEE.slot, AncientCurse.LEECH_ATTACK.slot)) {
            Bots.button(bot, BOOK, 42, 1, slot)
            wait(1)
        }
        val curses = AncientCurses.selectedQuickCurseSlots(bot).toSet()
        val expected = setOf(AncientCurse.DEFLECT_MELEE.slot, AncientCurse.LEECH_ATTACK.slot)
        run.expectTrue(e, "choosing Leech Attack drops Sap Warrior from the quick curses (same type, 2011 KB)", curses == expected, "selected=$curses", "$expected")
        Bots.button(bot, BOOK, 43, 1)
        wait(1)
        Bots.button(bot, 749, 1, Prayers.QUICK_PRAYERS_TOGGLE_OPTION)
        wait(1)
        val active = (0..19).filter { active(bot, it, true) }.toSet()
        run.expectTrue(e, "the orb turns on exactly the quick curses", active == curses, "active=$active")
        Bots.button(bot, 749, 1, Prayers.QUICK_PRAYERS_TOGGLE_OPTION)
        wait(1)
        run.expectTrue(e, "a second click turns them off", (0..19).none { active(bot, it, true) }, "active=${(0..19).filter { active(bot, it, true) }}")
        offAll(bot, true)
    }

    /** Berserker: a boosted combat stat drops one level every 100 ticks, 15 % slower (115) with Berserker (2011 Knowledge Base). */
    private suspend fun QueueTask.berserkerDecay(
        run: Conformance.Run,
        bot: Client,
    ) {
        val e = "curse berserker decay"
        book(bot, true)
        suspend fun interval(berserker: Boolean): Int? {
            offAll(bot, true)
            if (berserker) click(bot, AncientCurse.BERSERKER.slot)
            bot.skills.setCurrentLevel(Skills.ATTACK, bot.skills.getMaxLevel(Skills.ATTACK) + 10)
            var level = bot.skills.getCurrentLevel(Skills.ATTACK)
            val drops = ArrayList<Int>()
            var guard = 0
            while (drops.size < 2 && guard++ < 300) {
                bot.setCurrentPrayerPoints(99)
                wait(1)
                val now = bot.skills.getCurrentLevel(Skills.ATTACK)
                if (now < level) drops += run.world.currentCycle
                level = now
            }
            bot.skills.setCurrentLevel(Skills.ATTACK, bot.skills.getMaxLevel(Skills.ATTACK))
            return if (drops.size == 2) drops[1] - drops[0] else null
        }
        run.check(e, "ticks between boosted Attack drops, Berserker off", interval(false), "2011 KB" to 100)
        run.check(e, "ticks between boosted Attack drops, Berserker on", interval(true), "2011 KB (15 % longer)" to 115)
        offAll(bot, true)
    }

    /**
     * N503 D1/D2 (owner 2026-11-01, OSRS Deadman): Protect Item (prayer slot 10, curse slot 0) cannot be switched on and keeps nothing;
     * an unskulled monster death keeps 3 items, a skulled one none (DeathRules.keepCount).
     */
    private suspend fun QueueTask.protectItemDeath(
        run: Conformance.Run,
        bot: Client,
    ) {
        val e = "prayers protect item on death"
        bot.inventory.removeAll()
        bot.inventory.add(Item(Items.ABYSSAL_WHIP))
        bot.inventory.add(Item(Items.AMULET_OF_FURY))
        bot.inventory.add(Item(Items.DRAGON_CLAWS))
        bot.inventory.add(Item(Items.DRAGON_BOOTS))
        val rules = gg.rsmod.plugins.content.mechanics.death.DeathRules
        val value = rules.rankValue(run.world)
        fun kept(skulled: Boolean) =
            gg.rsmod.plugins.content.mechanics.death.DeathResolver.resolve(
                bot, null, skulled = skulled, valueProvider = value,
                contextOverride = gg.rsmod.plugins.content.mechanics.death.DeathContext.PVM_SAFE,
            ).itemRisk.protectedItemCount
        for (ancient in listOf(false, true)) {
            book(bot, ancient)
            val name = if (ancient) "curse" else "prayer"
            click(bot, if (ancient) AncientCurse.PROTECT_ITEM_SLOT else Prayer.PROTECT_ITEM.slot)
            run.check(e, "Protect Item stays off ($name book)", Prayers.isActive(bot, Prayer.PROTECT_ITEM), "D1: disabled in Deadman" to false)
            run.check(e, "monster death, unskulled ($name book)", kept(false), "D2: 3 most valuable" to rules.PVM_UNSKULLED_KEEP)
            run.check(e, "monster death, skulled ($name book)", kept(true), "D2: none" to 0)
            offAll(bot, ancient)
        }
        bot.inventory.removeAll()
    }

    /** Deflect Summoning stops a familiar's hits (FamiliarCombat.blockedBySummoningProtection: 2011 KB, Novite head icons). */
    private suspend fun QueueTask.deflectSummoning(
        run: Conformance.Run,
        bot: Client,
    ) {
        val e = "curse deflect summoning vs familiars"
        val familiar = gg.rsmod.plugins.content.skills.summoning.FamiliarCombat
        book(bot, true)
        run.expectTrue(e, "without it a familiar's hit can land", !familiar.blockedBySummoningProtection(bot), "blocked=${familiar.blockedBySummoningProtection(bot)}")
        click(bot, AncientCurse.DEFLECT_SUMMONING.slot)
        run.expectTrue(e, "Deflect Summoning stops a familiar's hit", familiar.blockedBySummoningProtection(bot), "icon=${bot.prayerIcon}")
        click(bot, AncientCurse.DEFLECT_MELEE.slot)
        run.expectTrue(e, "with Deflect Melee beside it (icon 16) it still does", familiar.blockedBySummoningProtection(bot) && bot.prayerIcon == 16, "icon=${bot.prayerIcon}")
        offAll(bot, true)
        book(bot, false)
        click(bot, Prayer.PROTECT_FROM_SUMMONING.slot)
        run.expectTrue(e, "Protect from Summoning (normal book) stops it too", familiar.blockedBySummoningProtection(bot), "icon=${bot.prayerIcon}")
        offAll(bot, false)
    }

    /** Prayer bonus slows the drain: resistance 60 + 2 x bonus (Void PrayerDrain) = drain / (1 + bonus / 30) (Darkan). */
    private suspend fun QueueTask.bonusDrain(
        run: Conformance.Run,
        bot: Client,
    ) {
        val e = "prayers prayer bonus and drain"
        book(bot, false)
        bot.equipment.removeAll()
        bot.equipment[2] = Item(Items.HOLY_SYMBOL)
        bot.refreshBonuses()
        bot.addBlock(UpdateBlockType.APPEARANCE)
        val bonus = bot.getPrayerBonus()
        run.expectTrue(e, "the holy symbol gives a prayer bonus", bonus > 0, "bonus=$bonus")
        click(bot, Prayer.PROTECT_FROM_MELEE.slot)
        val p0 = bot.getCurrentPrayerPoints()
        val ticks = 50
        wait(ticks)
        val points = p0 - bot.getCurrentPrayerPoints()
        val expected = ticks * Prayer.PROTECT_FROM_MELEE.drainEffect.toDouble() / ((60 + 2 * bonus) * 10)
        run.check(
            e, "Protect from Melee drain in 30 s at prayer bonus $bonus", points, "darkan / void" to "%.1f".format(java.util.Locale.ENGLISH, expected),
            same = { a, b -> a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 1.0 },
        )
        offAll(bot, false)
        bot.equipment.removeAll()
        bot.refreshBonuses()
    }

    /** The normal book's stat prayers reach the melee formulas: accuracy, max hit and the opponent's accuracy against Defence. */
    private suspend fun QueueTask.formulaBoosts(
        run: Conformance.Run,
        bot: Client,
        target: Client,
    ) {
        val e = "prayers stat boosts in the combat formulas"
        val f = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        book(bot, false)
        offAll(target, false)
        arm(bot, "melee")
        arm(target, "melee")
        val acc0 = f.getAccuracy(bot, target)
        val max0 = f.getMaxHit(bot, target)
        val hitMe0 = f.getAccuracy(target, bot)
        click(bot, Prayer.INCREDIBLE_REFLEXES.slot)
        val acc1 = f.getAccuracy(bot, target)
        offAll(bot, false)
        click(bot, Prayer.ULTIMATE_STRENGTH.slot)
        val max1 = f.getMaxHit(bot, target)
        offAll(bot, false)
        click(bot, Prayer.STEEL_SKIN.slot)
        val hitMe1 = f.getAccuracy(target, bot)
        offAll(bot, false)
        val en = java.util.Locale.ENGLISH
        run.expectTrue(e, "Incredible Reflexes (+15 % Attack, Novite) raises melee accuracy", acc1 > acc0, "%.4f -> %.4f".format(en, acc0, acc1))
        run.expectTrue(e, "Ultimate Strength (+15 % Strength, Novite) raises the max hit", max1 > max0, "%.2f -> %.2f".format(en, max0, max1))
        run.expectTrue(e, "Steel Skin (+15 % Defence, Novite) lowers the opponent's accuracy", hitMe1 < hitMe0, "%.4f -> %.4f".format(en, hitMe0, hitMe1))
    }

    /** Leech Energy / Leech Special Attack move 10 % per proc from the target to the caster (Novite, Darkan). */
    private suspend fun QueueTask.leechTransfers(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        val e = "curse leech transfers"
        book(bot, true)
        place(bot, target, arena)
        arm(bot, "melee")
        bot.attr[AncientCurses.SCENE_ALWAYS_PROC] = true
        val attackTab = gg.rsmod.plugins.content.inter.attack.AttackTab
        val within1 = { a: Any?, b: Any? -> a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 1.0 }
        try {
            for ((curse, label) in listOf(AncientCurse.LEECH_ENERGY to "run energy", AncientCurse.LEECH_SPECIAL_ATTACK to "special attack energy")) {
                offAll(bot, true)
                click(bot, curse.slot)
                fun energy(p: Client): Double = if (curse == AncientCurse.LEECH_ENERGY) p.runEnergy else attackTab.getEnergy(p).toDouble()
                for (p in listOf(bot, target)) {
                    p.runEnergy = 50.0
                    attackTab.setEnergy(p, 50)
                }
                bot.attack(target)
                var measured: Pair<Double, Double>? = null
                var guard = 0
                while (measured == null && guard++ < 60) {
                    val b0 = energy(bot)
                    val t0 = energy(target)
                    target.setCurrentLifepoints(target.getMaximumLifepoints())
                    if (bot.getCombatTargetOrNull() == null) bot.attack(target)
                    wait(1)
                    if (t0 - energy(target) >= 3.0) measured = (energy(bot) - b0) to (t0 - energy(target))
                }
                bot.resetInteractions()
                Combat.reset(bot)
                val name = curse.name.lowercase()
                // Leech Special Attack moves the owner's 5 % (balance rule 2026-09-18, AncientCurses.LEECH_SPECIAL_PCT) instead of
                // Novite's / Darkan's 10 %; Leech Energy keeps their 10 %.
                val amount = if (curse == AncientCurse.LEECH_SPECIAL_ATTACK) "owner 2026-09-18" to AncientCurses.LEECH_SPECIAL_PCT else "novite / darkan" to 10
                run.check(e, "$name: $label the target loses per proc", measured?.second?.let { Math.round(it).toInt() }, amount, same = within1)
                run.check(e, "$name: $label the caster gains per proc", measured?.first?.let { Math.round(it).toInt() }, amount, same = within1)
            }
        } finally {
            bot.attr.remove(AncientCurses.SCENE_ALWAYS_PROC)
            offAll(bot, true)
            unplace(bot, target)
        }
    }

    /**
     * Owner 2026-10-09 ("117 range, leech the range of the opponent, it needs to give 118"): every Leech proc is +1 level on top of
     * a potion boost, at most AncientCurses.LEECH_LEVEL_CAP (5) levels above the level at the first proc.
     */
    private suspend fun QueueTask.leechOnPotion(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        val e = "curse leech on a potion boost"
        book(bot, true)
        place(bot, target, arena)
        arm(bot, "range")
        bot.attr[AncientCurses.SCENE_ALWAYS_PROC] = true
        try {
            click(bot, AncientCurse.LEECH_RANGED.slot)
            val potted = bot.skills.getMaxLevel(Skills.RANGED) + 18
            bot.skills.setCurrentLevel(Skills.RANGED, potted)
            val levels = ArrayList<Int>()
            bot.attack(target)
            var guard = 0
            while (levels.size < 6 && guard++ < 200) {
                val before = bot.skills.getCurrentLevel(Skills.RANGED)
                target.setCurrentLifepoints(target.getMaximumLifepoints())
                bot.setCurrentPrayerPoints(99)
                if (bot.getCombatTargetOrNull() == null) bot.attack(target)
                wait(1)
                // A proc is a rise of the level (the switch-on plays the same animation as a proc, owner 2026-10-09).
                if (bot.skills.getCurrentLevel(Skills.RANGED) > before) levels += bot.skills.getCurrentLevel(Skills.RANGED)
            }
            bot.resetInteractions()
            Combat.reset(bot)
            val cap = AncientCurses.LEECH_LEVEL_CAP
            run.check(e, "potted Ranged ${potted} after the first Leech Ranged proc", levels.firstOrNull(), "owner 2026-10-09" to potted + 1)
            run.check(e, "highest Ranged after ${levels.size} procs", levels.maxOrNull(), "owner 2026-10-09 (+$cap at most)" to potted + cap)
        } finally {
            bot.attr.remove(AncientCurses.SCENE_ALWAYS_PROC)
            bot.skills.setCurrentLevel(Skills.RANGED, bot.skills.getMaxLevel(Skills.RANGED))
            offAll(bot, true)
            unplace(bot, target)
        }
    }

    /** Curses against an npc: a Sap procs and saps the npc's Attack; Deflect Melee stops the npc's melee hits. */
    private suspend fun QueueTask.cursesVsNpcs(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        val e = "curses vs npcs"
        book(bot, true)
        // The other bot may still be fighting back from the scenario before (auto retaliate): it is stopped and kept out of it,
        // so every hit the caster takes here is the npc's (run 2026-10-09 17:12: its whip hits were counted as the giant's).
        target.resetInteractions()
        Combat.reset(target)
        target.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        target.moveTo(arena.transform(-6, -6))
        bot.moveTo(arena)
        bot.resetInteractions()
        Combat.reset(bot)
        arm(bot, "melee")
        bot.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        val npc = run.npc(gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT, arena.transform(0, 2), "HillGiant")
        try {
            wait(1)
            click(bot, AncientCurse.SAP_WARRIOR.slot)
            wait(2)
            val c0 = run.world.currentCycle
            bot.attack(npc)
            fun procs() = run.recording.of("Conf1", "out").count { it.cycle >= c0 && it.text.startsWith("anim ") && idOf(it.text) == 12569 }
            var guard = 0
            while (procs() == 0 && guard++ < 200) {
                npc.setCurrentLifepoints(npc.getMaximumLifepoints())
                bot.setCurrentPrayerPoints(99)
                if (bot.getCombatTargetOrNull() == null) bot.attack(npc)
                wait(1)
            }
            run.expectTrue(e, "Sap Warrior procs against an npc", procs() > 0, "${procs()} procs in $guard ticks")
            val sapped = AncientCurses.drainMultiplier(npc, Skills.ATTACK)
            run.expectTrue(e, "the npc's Attack is sapped (10 % base drain; 2011 KB, Darkan Sap on npcs)", sapped < 1.0, "multiplier=$sapped")
            bot.resetInteractions()
            Combat.reset(bot)
            offAll(bot, true)
            click(bot, AncientCurse.DEFLECT_MELEE.slot)
            npc.resetInteractions()
            wait(4)
            val c1 = run.world.currentCycle
            npc.attack(bot)
            repeat(40) {
                bot.setCurrentLifepoints(bot.getMaximumLifepoints())
                bot.setCurrentPrayerPoints(99)
                wait(1)
            }
            val dmg =
                run.recording.of("Conf1", "out").filter { it.cycle >= c1 && it.text.startsWith("hits ") }
                    .flatMap { Regex("type=(\\d+) dmg=(\\d+)").findAll(it.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
                    .filter { it.first != gg.rsmod.plugins.api.HitType.REFLECTED.id }
            run.expectTrue(e, "Deflect Melee stops the npc's melee hits (protection prayers vs npcs)", dmg.isNotEmpty() && dmg.all { it.second == 0 }, "hits=${dmg.take(20)}", "every hit 0")
        } finally {
            npc.resetInteractions()
            run.remove(npc)
            bot.resetInteractions()
            Combat.reset(bot)
            offAll(bot, true)
            unplace(bot, target)
            target.moveTo(arena.transform(1, 0))
        }
    }

    /** The prayer book switch through a real altar loc (the Ferox Enclave altar's "Pray-at" menu). */
    private suspend fun QueueTask.altarSwitch(
        run: Conformance.Run,
        bot: Client,
        arena: Tile,
    ) {
        val e = "prayers altar book switch"
        book(bot, false)
        val id = gg.rsmod.plugins.content.areas.home.FeroxObjects.ALTAR
        val tile = arena.transform(0, -2)
        val altar = gg.rsmod.game.model.entity.DynamicObject(id, 10, 0, tile)
        run.world.spawn(altar)
        try {
            val option = run.world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).options.indexOfFirst { it?.equals("pray-at", true) == true }
            if (option !in 0..1) {
                run.add(Result(e, "altar menu", Verdict.FAIL, "Pray-at as option 1 or 2", "index $option"))
                return
            }
            for ((answer, want) in listOf(2 to AncientCurses.PrayerBook.ANCIENT, 1 to AncientCurses.PrayerBook.NORMAL)) {
                bot.moveTo(arena)
                wait(1)
                Bots.inject(bot, if (option == 0) gg.rsmod.game.message.impl.OpLoc1Message(0, tile.x, id, tile.z) else gg.rsmod.game.message.impl.OpLoc2Message(id, tile.x, tile.z, 0))
                var guard = 0
                while (guard++ < 20 && (228..240 step 2).none { bot.interfaces.isVisible(it) }) wait(1)
                Bots.answerOption(bot, answer)
                wait(2)
                run.check(e, "Pray-at, option $answer", AncientCurses.getBook(bot).name, "Ferox altar menu" to want.name)
            }
        } finally {
            run.world.remove(altar)
        }
    }

    /** Smite drains the target's prayer by a quarter of each hit (Novite hit / 4, Void the same on its x10 scale). */
    private suspend fun QueueTask.smite(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        val e = "prayer smite"
        book(bot, false)
        book(target, false)
        place(bot, target, arena)
        arm(bot, "melee")
        click(bot, Prayer.SMITE.slot)
        target.setCurrentPrayerPoints(99)
        val c0 = run.world.currentCycle
        bot.attack(target)
        repeat(40) {
            bot.setCurrentPrayerPoints(99)
            if (target.getCurrentLifepoints() < target.getMaximumLifepoints() / 2) target.setCurrentLifepoints(target.getMaximumLifepoints())
            if (bot.getCombatTargetOrNull() == null) bot.attack(target)
            wait(1)
        }
        bot.resetInteractions()
        Combat.reset(bot)
        wait(3)
        val hits = run.recording.of("Conf2", "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") }.flatMap { damages(it.text) }
        run.check(e, "target's prayer drained over ${hits.size} hits (sum of hit / 4)", 99 - target.getCurrentPrayerPoints(), "novite" to hits.sumOf { it / 4 })
        offAll(bot, false)
        unplace(bot, target)
    }

    /** Redemption: below 10 % health it heals 25 % of the Prayer level, empties the prayer points and switches off (Void). */
    private suspend fun QueueTask.redemption(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        val e = "prayer redemption"
        book(target, false)
        place(bot, target, arena)
        // Small hits, so one blow takes the target below 10 % without killing it.
        arm(bot, "melee")
        bot.skills.setBaseLevel(Skills.STRENGTH, 1)
        click(target, Prayer.REDEMPTION.slot)
        bot.attack(target)
        var fired = false
        var healedTo = -1
        var guard = 0
        while (!fired && guard++ < 120) {
            if (target.getCurrentPrayerPoints() > 0 && target.getCurrentLifepoints() > 10) target.setCurrentLifepoints(10)
            if (bot.getCombatTargetOrNull() == null) bot.attack(target)
            wait(1)
            if (target.getCurrentPrayerPoints() == 0) {
                fired = true
                healedTo = target.getCurrentLifepoints()
            }
        }
        bot.resetInteractions()
        Combat.reset(bot)
        bot.skills.setBaseLevel(Skills.STRENGTH, 99)
        val heal = (target.skills.getMaxLevel(Skills.PRAYER) * 0.25).toInt()
        run.expectTrue(e, "fires below 10 % health: prayer points emptied, Redemption off", fired && !Prayers.isActive(target, Prayer.REDEMPTION), "fired=$fired active=${Prayers.isActive(target, Prayer.REDEMPTION)}")
        run.expectTrue(e, "heals 25 % of the Prayer level ($heal)", healedTo >= heal, "health after=$healedTo", ">= $heal (void, osrs wiki)")
        target.setCurrentLifepoints(target.getMaximumLifepoints())
        target.setCurrentPrayerPoints(99)
        unplace(bot, target)
    }

    /** Retribution: the caster's death hits the adjacent killer for up to 25 % of the Prayer level (Void PlayerDeath). */
    private suspend fun QueueTask.retribution(
        run: Conformance.Run,
        bot: Client,
        target: Client,
        arena: Tile,
    ) {
        val e = "prayer retribution"
        book(target, false)
        place(bot, target, arena)
        arm(bot, "melee")
        click(target, Prayer.RETRIBUTION.slot)
        bot.setCurrentLifepoints(bot.getMaximumLifepoints())
        val c0 = run.world.currentCycle
        target.setCurrentLifepoints(1)
        bot.attack(target)
        var guard = 0
        while (!target.isDead() && guard++ < 60) {
            if (target.getCurrentLifepoints() > 1) target.setCurrentLifepoints(1)
            if (bot.getCombatTargetOrNull() == null) bot.attack(target)
            wait(1)
        }
        wait(4)
        bot.resetInteractions()
        Combat.reset(bot)
        val onBot = run.recording.of("Conf1", "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") }.flatMap { damages(it.text) }
        val max = (99 * 0.25).toInt()
        run.expectTrue(e, "the killer beside the caster is hit when the caster dies", onBot.isNotEmpty(), "caster died=${guard < 60} hits on the killer=$onBot")
        run.expectTrue(e, "the blast is at most 25 % of the Prayer level ($max)", onBot.isNotEmpty() && onBot.all { it in 0..max }, "hits=$onBot", "one hit 0..$max (void)")
        unplace(bot, target)
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

    /**
     * The chat lines the bot received from [from] on. The Deadman HUD rides on type-99 game messages (`__RSPS_HUD__:...`) that
     * the client never prints; they were taken for the refusal message (conform curses 2026-10-09: "measured __RSPS_HUD__:dmmt|"; 2026-11-01 the same
     * with `__RSPS_UI__:tip|` - every client hook line is skipped).
     */
    private fun messages(
        run: Conformance.Run,
        from: Int,
    ) = run.recording.of("Conf1", "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") && !it.text.contains("message=__RSPS_") }.map { it.text }

    private fun text(msg: String) = Regex("message=(.*?)(, \\w+=|\\)$)").find(msg)?.groupValues?.get(1)

    private fun idOf(text: String): Int = Regex("\\bid=(-?\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: -1

    private fun damages(text: String): List<Int> = Regex("dmg=(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.toList()

    private fun gg.rsmod.game.model.entity.Pawn.getCombatTargetOrNull() = runCatching { getCombatTarget() }.getOrNull()
}
