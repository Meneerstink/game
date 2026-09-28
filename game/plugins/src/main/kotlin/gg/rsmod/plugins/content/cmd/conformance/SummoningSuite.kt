package gg.rsmod.plugins.content.cmd.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.cmd.Sweeps
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.skills.summoning.BeastOfBurden
import gg.rsmod.plugins.content.skills.summoning.FAMILIAR_LIFETIME_TIMER
import gg.rsmod.plugins.content.skills.summoning.FAMILIAR_SPECIAL_POINTS_ATTR
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.skills.summoning.FamiliarSpecialTarget
import gg.rsmod.plugins.content.skills.summoning.SummoningPouchData
import gg.rsmod.plugins.content.skills.summoning.SummoningScrollData
import gg.rsmod.plugins.content.skills.summoning.SummoningSpecialMoves
import java.io.File
import kotlin.math.max

/**
 * Summoning conformance (catalogue section 1). Every pouch the server has is driven through the real click path by a bot and
 * measured against C:\RSPS\reference\summoning\familiars.json (Void + Novite donors; tools\conformance\build_summoning_reference.py).
 * One entry per familiar: level gate, summon (cost, pouch, xp, npc, hitpoints, stats, spawn animation/graphic/tile, lifetime,
 * client varps, follower panel), follow (walk, run, teleport, call - every tick: overlap, walls, distance), combat (attack
 * animation, speed, projectile, max hit, defend animation), special move (scroll, points, animations, graphics, projectile,
 * sound, damage, boosts), beast of burden (capacity, refusal, withdraw, no item lost or made), dismiss (npc and varps gone).
 */
object SummoningSuite : Conformance.Suite {
    private const val REF = "C:/RSPS/reference/summoning/familiars.json"
    private const val DUMMY_NPC = 1
    private const val DUMMY_LIFE = 100000
    private const val ORB = 747
    private const val ORB_SPECIAL = 17
    private const val ORB_CALL = 10
    private const val ORB_DISMISS = 11
    private const val ORB_TAKE_BOB = 12
    private const val PANEL = 662
    private const val PANEL_ATTACK = 65
    private const val VARP_FOLLOWER_NAME = 448
    private const val VARP_FOLLOWER_CHATHEAD = 1174
    private const val VARBIT_SPECIAL_POINTS_NEEDED = 4288
    private const val VARBIT_MINUTES = 4534

    /** Per-familiar "summon xp gained / reference base xp" - the server xp rate must be the same for every familiar. */
    private val xpRatios = LinkedHashMap<String, Double>()

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val world = run.world
        val ref = ObjectMapper().readTree(File(REF))
        val refs = ref["familiars"]
        val common = ref["summoning"]
        val filter = args.joinToString(" ").lowercase()
        xpRatios.clear()
        val arena = nearestMulti(run, run.origin.transform(3, 0)) ?: run.origin
        run.note("arena ${arena.x},${arena.z},${arena.height} multi=${arena.isMulti(world)}")
        val bot = run.bot(arena, "Conf1")
        wait(2)
        run.expectTrue("harness", "inventory 679 open on the bot", bot.interfaces.isVisible(679), "visible=${bot.interfaces.isVisible(679)}")
        run.expectTrue("harness", "summoning orb 747 open on the bot", bot.interfaces.isVisible(ORB), "visible=${bot.interfaces.isVisible(ORB)}")
        // A second player 4 tiles away: what the owner sees and hears, a bystander must too (RS: nearby players hear combat sounds).
        run.bot(arena.transform(-4, -4), "Bystander")
        val dummy = run.npc(DUMMY_NPC, arena.transform(6, 0), "dummy")
        dummy.attr[Sweeps.SWEEP_DUMMY_ATTR] = true

        val pouches = SummoningPouchData.values().filter { filter.isEmpty() || it.name.lowercase().contains(filter) }
        for ((i, data) in pouches.withIndex()) {
            if (Conformance.stopRequested) break
            if (i % 5 == 0) run.progress("summoning ${i + 1}/${pouches.size} ${data.name.lowercase()}")
            val entry = "familiar ${data.name.lowercase()}"
            val from = world.currentCycle
            try {
                familiar(run, bot, dummy, arena, data, refs[data.pouch.toString()], common, entry)
            } catch (e: Throwable) {
                run.add(Result(entry, "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
            } finally {
                runCatching { Familiar.dismiss(bot) }
                run.window(entry, from)
            }
        }
        judgeXpRatios(run)
        if (filter.isEmpty() && !Conformance.stopRequested) {
            val from = world.currentCycle
            run.progress("summoning lifecycle (renew, expiry, drain, regen, heals, overhead, deaths)")
            lifecycle(run, bot, arena, refs, common)
            run.window("summoning lifecycle", from)
            val fromLevels = world.currentCycle
            combatLevels(run, arena)
            run.window("summoning combat level", fromLevels)
        }
        // Coverage both ways: donor familiars the server lacks, server familiars no donor has.
        val serverPouches = SummoningPouchData.values().map { it.pouch.toString() }.toSet()
        refs.fieldNames().asSequence().filter { it !in serverPouches }.forEach {
            run.add(Result("familiar ${refs[it]["name"].asText()}", "exists on the server", Verdict.FAIL, "in the donors (pouch $it)", "missing"))
        }
        notTestedCatalogue(run)
    }

    // ---- one familiar ------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.familiar(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        data: SummoningPouchData,
        ref: JsonNode?,
        common: JsonNode,
        e: String,
    ) {
        val world = run.world
        val v = ref?.get("void")
        val n = ref?.get("novite")
        if (ref == null) run.add(Result(e, "reference", Verdict.UNSOURCED, "no donor has pouch ${data.pouch}", "-"))
        reset(run, bot, arena)

        // 1. Level gate.
        if (data.level > 1) {
            bot.skills.setBaseLevel(Skills.SUMMONING, data.level - 1)
            bot.skills.setCurrentLevel(Skills.SUMMONING, data.level - 1)
            bot.inventory.add(data.pouch, 1)
            val c = world.currentCycle
            val clicked = Bots.itemOption(bot, bot.inventory.getItemIndex(data.pouch, false), "Summon")
            wait(2)
            run.expectTrue(e, "pouch has a Summon option", clicked, "menu=${runCatching { bot.inventory[0]?.getDef(world.definitions)?.inventoryMenu?.toList() }.getOrNull()}")
            run.expectTrue(e, "refused at level ${data.level - 1}", Familiar.current(bot) == null, "familiar=${Familiar.current(bot)?.id}", "no familiar")
            val said = gameMessages(run, "Conf1", c)
            run.expectTrue(e, "level refusal message", said.any { it.contains("level", true) }, said.joinToString(" / ").ifEmpty { "(no message)" }, "a message naming the level")
            run.expectTrue(e, "pouch kept on refusal", bot.inventory.getItemCount(data.pouch) == 1, "pouches=${bot.inventory.getItemCount(data.pouch)}", "1")
            runCatching { Familiar.dismiss(bot) }
            reset(run, bot, arena)
        }

        // 2. Summon.
        bot.inventory.add(data.pouch, 1)
        val points0 = bot.skills.getCurrentLevel(Skills.SUMMONING)
        val xp0 = bot.skills.getCurrentXp(Skills.SUMMONING)
        val c1 = world.currentCycle
        Bots.itemOption(bot, bot.inventory.getItemIndex(data.pouch, false), "Summon")
        wait(3)
        val fam = Familiar.current(bot)
        if (fam == null) {
            run.add(Result(e, "summon", Verdict.FAIL, "a familiar appears", "none (messages: ${gameMessages(run, "Conf1", c1).joinToString(" / ")})"))
            return
        }
        run.recording.track(fam, "familiar")
        sound(run, e, "summon", c1, world.currentCycle, "familiar", "Conf1")
        val size = runCatching { world.definitions.get(NpcDef::class.java, fam.id).size }.getOrDefault(1).coerceAtLeast(1)
        run.check(e, "npc id", fam.id, "void" to v?.get("npc")?.asInt())
        run.check(e, "summon points cost", points0 - bot.skills.getCurrentLevel(Skills.SUMMONING), "void" to v?.get("summon_points")?.asInt(), "novite" to n?.get("points")?.asInt())
        run.check(e, "pouch consumed", bot.inventory.getItemCount(data.pouch), "rule" to 0)
        val xpGained = bot.skills.getCurrentXp(Skills.SUMMONING) - xp0
        v?.get("summon_xp")?.asDouble()?.takeIf { it > 0 }?.let { xpRatios[e] = xpGained / it }
        run.expectTrue(e, "summon xp gained", xpGained > 0, "gained=$xpGained", "> 0")
        run.check(e, "hitpoints", fam.getMaximumLifepoints(), "void" to v?.get("hitpoints")?.asInt())
        run.check(e, "hitpoints full at summon", fam.getCurrentLifepoints(), "rule" to fam.getMaximumLifepoints())
        val stats = v?.get("stats")
        mapOf("att" to NpcSkills.ATTACK, "str" to NpcSkills.STRENGTH, "def" to NpcSkills.DEFENCE, "range" to NpcSkills.RANGED, "mage" to NpcSkills.MAGIC).forEach { (k, idx) ->
            run.check(e, "stat $k", fam.stats.getMaxLevel(idx), "void" to stats?.get(k)?.takeIf { !it.isNull }?.asInt())
        }
        val famOut = run.recording.of("familiar", "out").filter { it.cycle >= c1 }
        val spawnAnims = famOut.filter { it.text.startsWith("anim ") }.map { idOf(it.text) }
        run.check(e, "spawn animation", spawnAnims.firstOrNull(), "void" to v?.get("spawn_anim")?.takeIf { !it.isNull }?.asInt())
        val spawnGfx = famOut.filter { it.text.startsWith("gfx ") }.map { idOf(it.text) }
        run.check(e, "spawn graphic (size $size)", spawnGfx.firstOrNull(), "void" to common["summon_gfx_by_size"]?.get(size.toString())?.asInt())
        val g = gap(bot.tile, bot.getSize(), fam.tile, size)
        run.expectTrue(e, "spawn tile next to the owner", g == 1, "gap=$g owner=${bot.tile} familiar=${fam.tile}", "gap 1")
        run.expectTrue(e, "spawn tile free (not in a wall)", !clipped(run, fam.tile, size), "tile=${fam.tile}")
        val lifetime = if (bot.timers.has(FAMILIAR_LIFETIME_TIMER)) bot.timers[FAMILIAR_LIFETIME_TIMER] + (world.currentCycle - c1) else null
        run.check(e, "lifetime (ticks)", lifetime, "void" to v?.get("time_minutes")?.asInt()?.times(100), "novite" to n?.get("time_ms")?.asLong()?.div(600), same = ::within3)
        run.check(e, "varp 1174 follower chathead", bot.varps.getState(VARP_FOLLOWER_CHATHEAD), "void" to fam.id)
        run.expectTrue(e, "varp 448 follower name set", bot.varps.getState(VARP_FOLLOWER_NAME) != 0, "448=${bot.varps.getState(VARP_FOLLOWER_NAME)}", "non-zero")
        run.expectTrue(e, "varp 1174 sent to the client", sentVarp(run, "Conf1", c1, VARP_FOLLOWER_CHATHEAD), "no Varp message for 1174 since summon", "a varp message")
        run.check(e, "varbit 4288 special points needed", runCatching { bot.varps.getVarbit(world, VARBIT_SPECIAL_POINTS_NEEDED) }.getOrNull(), "void" to v?.get("scroll")?.get("special_points")?.asInt())
        run.check(e, "varbit 4534 minutes shown", runCatching { bot.varps.getVarbit(world, VARBIT_MINUTES) }.getOrNull(), "void" to v?.get("time_minutes")?.asInt(), same = ::within1)
        run.expectTrue(e, "follower details panel 662 open", bot.interfaces.isVisible(PANEL), "visible=${bot.interfaces.isVisible(PANEL)}", "open (Void opens familiar_details on summon)")

        // 3. Follow.
        follow(run, bot, fam, size, arena.transform(0, 8), false, e, "walk")
        follow(run, bot, fam, size, arena, true, e, "run")
        val tele = arena.transform(0, 30)
        val ct = world.currentCycle
        bot.moveTo(tele)
        var arrived = -1
        for (t in 1..4) {
            wait(1)
            if (arrived < 0 && gap(bot.tile, bot.getSize(), fam.tile, size) == 1) arrived = t
        }
        run.expectTrue(e, "follows a teleport (30 tiles)", arrived in 1..2, "next to the owner after ${if (arrived < 0) "never (4 ticks)" else "$arrived ticks"}; familiar=${fam.tile}", "next to the owner within 2 ticks")
        bot.moveTo(arena)
        wait(3)
        // Call follower: familiar placed 7 tiles away, orb "Call Follower".
        fam.moveTo(bot.tile.transform(-7, 0))
        wait(1)
        val cc = world.currentCycle
        Bots.button(bot, ORB, ORB_CALL)
        wait(2)
        sound(run, e, "call follower", cc, world.currentCycle, "familiar", "Conf1")
        val cg = gap(bot.tile, bot.getSize(), fam.tile, size)
        run.expectTrue(e, "call follower places it next to the owner", cg == 1, "gap=$cg", "gap 1")
        val callOut = run.recording.of("familiar", "out").filter { it.cycle >= cc }
        run.check(e, "call follower animation", callOut.firstOrNull { it.text.startsWith("anim ") }?.let { idOf(it.text) }, "void" to v?.get("spawn_anim")?.takeIf { !it.isNull }?.asInt())
        run.check(e, "call follower graphic", callOut.firstOrNull { it.text.startsWith("gfx ") }?.let { idOf(it.text) }, "void" to common["summon_gfx_by_size"]?.get(size.toString())?.asInt())
        if (ct < 0) run.note("unreachable")

        // 4. Combat.
        val attacks = v?.get("attacks")
        if (attacks != null && attacks.size() > 0) combat(run, bot, fam, dummy, arena, attacks, v, e)
        else run.add(Result(e, "combat", Verdict.UNSOURCED, "donors give no attack", "familiar ${fam.id}"))

        // 5. Special move.
        special(run, bot, fam, dummy, data, v, n, common, e)

        // 6. Beast of burden.
        val capacity = v?.get("bob_capacity")?.takeIf { !it.isNull }?.asInt() ?: n?.get("bob_size")?.asText()?.toIntOrNull()?.takeIf { it > 0 }
        if (capacity != null && Familiar.current(bot) != null) bob(run, bot, fam, capacity, v, n, e)

        // 7. Panel texts, invisible boosts, every npc option.
        if (Familiar.current(bot) != null) {
            val name = runCatching { world.definitions.get(NpcDef::class.java, fam.id).name }.getOrDefault("?")
            // The panel name comes from varp 448 through the cache enum (pouch -> npc); Void sets it to the enum key, the pouch id.
            run.check(e, "varp 448 names the familiar (pouch id, enum summoning_familiar_ids)", bot.varps.getState(VARP_FOLLOWER_NAME), "void" to data.pouch)
            if (name.isEmpty()) run.note("npc ${fam.id} has no name")
            val boost = v?.get("boost")?.takeIf { !it.isNull }
            val boostSkills = boost?.get("skills")?.map { it.asText() } ?: emptyList()
            for (skillName in (boostSkills + listOf("Fishing", "Woodcutting", "Mining", "Hunter", "Firemaking", "Farming")).distinct()) {
                val idx = skillIndex(skillName) ?: continue
                val expected =
                    if (skillName in boostSkills) boost!!["flat"].asInt() + bot.skills.getMaxLevel(idx) * boost["multiplier_percent"].asInt() / 100 else if (v == null) null else 0
                run.check(e, "invisible $skillName boost", gg.rsmod.plugins.content.skills.summoning.SummoningBoosts.invisibleBoost(bot, idx), "void" to expected)
            }
            npcOptions(run, bot, fam, e)
        }

        // 8. Dismiss.
        if (Familiar.current(bot) != null) {
            val cd = world.currentCycle
            Bots.button(bot, ORB, ORB_DISMISS)
            wait(1)
            val answered = Bots.answerOption(bot, 1)
            wait(6)
            sound(run, e, "dismiss", cd, world.currentCycle, "familiar", "Conf1")
            run.expectTrue(e, "dismiss asks to confirm", answered, "no options dialogue open after Dismiss")
            run.expectTrue(e, "dismissed", Familiar.current(bot) == null && !run.world.npcs.contains(fam), "still there=${Familiar.current(bot)?.id}")
            run.check(e, "varp 1174 cleared on dismiss", bot.varps.getState(VARP_FOLLOWER_CHATHEAD), "void" to 0)
            run.check(e, "varp 448 cleared on dismiss", bot.varps.getState(VARP_FOLLOWER_NAME), "void" to 0)
            run.expectTrue(e, "lifetime timer stopped on dismiss", !bot.timers.has(FAMILIAR_LIFETIME_TIMER), "timer=${if (bot.timers.exists(FAMILIAR_LIFETIME_TIMER)) bot.timers[FAMILIAR_LIFETIME_TIMER] else "-"}")
            if (cd < 0) run.note("unreachable")
        }
        run.recording.untrack(fam)

        // 9. Death of the familiar (summoned again): death animation, gone, varps cleared, BoB cargo to Death's Domain.
        death(run, bot, arena, data, v, capacity, e)
    }

    private suspend fun QueueTask.npcOptions(
        run: Conformance.Run,
        bot: Client,
        fam: Npc,
        e: String,
    ) {
        val options = runCatching { run.world.definitions.get(NpcDef::class.java, fam.id).options.toList() }.getOrDefault(emptyList())
        for ((i, option) in options.withIndex()) {
            if (option.isNullOrBlank() || option.equals("Examine", true) || i > 4) continue
            if (Familiar.current(bot) == null) break
            val c = run.world.currentCycle
            Bots.npcOption(bot, fam, i + 1)
            wait(3)
            val lines = run.recording.of("Conf1").filter { it.cycle >= c }
            val said = lines.filter { it.kind == "msg" && it.text.startsWith("MessageGameMessage") }.map { it.text }
            val reacted = lines.any { (it.kind == "msg" && (it.text.startsWith("IfOpenSub") || it.text.startsWith("MessageGameMessage") || it.text.startsWith("UpdateInv"))) || it.kind == "state" && !it.text.startsWith("tile") }
            val nothing = said.any { it.contains("Nothing interesting happens", true) }
            val crash = run.recording.events.any { it.cycle >= c && it.text.contains("PROBLEM crash") }
            run.expectTrue(e, "npc option '$option' does something", reacted && !nothing && !crash, if (nothing) "Nothing interesting happens" else if (crash) "crash" else if (!reacted) "no reaction in 3 ticks" else "ok")
            bot.interruptQueues()
            runCatching { bot.interfaces.closeModal() }
            (228..240 step 2).forEach { if (bot.interfaces.isVisible(it)) runCatching { bot.closeInterface(it) } }
            wait(1)
        }
    }

    private suspend fun QueueTask.death(
        run: Conformance.Run,
        bot: Client,
        arena: Tile,
        data: SummoningPouchData,
        v: JsonNode?,
        capacity: Int?,
        e: String,
    ) {
        reset(run, bot, arena)
        bot.inventory.add(data.pouch, 1)
        Bots.itemOption(bot, bot.inventory.getItemIndex(data.pouch, false), "Summon")
        wait(3)
        val fam = Familiar.current(bot) ?: return
        run.recording.track(fam, "familiar")
        val cargo = capacity != null && BeastOfBurden.activeContainer(bot) != null
        val office0 = BeastOfBurden.deathsDomainCount(bot)
        if (cargo) {
            bot.inventory.add(Items.BRONZE_BAR, 1)
            Bots.itemOnNpc(bot, bot.inventory.getItemIndex(Items.BRONZE_BAR, false), fam)
            wait(2)
        }
        val c = run.world.currentCycle
        fam.hit(fam.getCurrentLifepoints())
        wait(8)
        val anims = run.recording.of("familiar", "out").filter { it.cycle >= c && it.text.startsWith("anim ") }.map { idOf(it.text) }
        sound(run, e, "familiar death", c, run.world.currentCycle, "familiar")
        run.check(e, "death animation", anims.lastOrNull(), "void" to v?.get("death_anim")?.takeIf { !it.isNull }?.asInt())
        run.expectTrue(e, "gone after death", Familiar.current(bot) == null && !run.world.npcs.contains(fam), "still there=${run.world.npcs.contains(fam)}")
        run.check(e, "varp 1174 cleared after death", bot.varps.getState(VARP_FOLLOWER_CHATHEAD), "void" to 0)
        val said = gameMessages(run, "Conf1", c)
        run.expectTrue(e, "owner told the familiar died", said.isNotEmpty(), said.joinToString(" / ").ifEmpty { "(no message)" }, "a message")
        if (cargo) run.check(e, "BoB cargo rescued into Death's Domain (owner rule)", BeastOfBurden.deathsDomainCount(bot) - office0, "owner" to 1)
        run.recording.untrack(fam)
    }

    // ---- combat level with summoning -----------------------------------------------------------------------------------------

    /**
     * Combat level including Summoning over fixed and random level profiles. Each profile is reached through the real level-up
     * path: every skill is set to its level, then the last Summoning (or Attack) level is gained with 1 xp so the level-up
     * plugin recalculates. Measured: the player's combat level. References: Void CombatLevel.calculateCombatLevel (integer
     * arithmetic) and the RS2 formula floor((def + hp + floor(pray/2) + floor(summ/2))/4 + 0.325 * max(att+str, floor(1.5 rng),
     * floor(1.5 mag))).
     */
    private suspend fun QueueTask.combatLevels(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "summoning combat level"
        val bot = run.bot(arena.transform(-2, 4), "Conf3")
        wait(1)
        val skills = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.PRAYER, Skills.RANGED, Skills.MAGIC, Skills.SUMMONING)
        val fixed =
            listOf(
                intArrayOf(99, 99, 99, 99, 99, 99, 99, 99),
                intArrayOf(99, 99, 99, 99, 99, 99, 99, 1),
                intArrayOf(99, 99, 99, 99, 99, 99, 99, 8),
                intArrayOf(99, 99, 99, 99, 99, 99, 99, 50),
                intArrayOf(1, 1, 1, 10, 1, 1, 1, 1),
                intArrayOf(1, 1, 1, 10, 1, 1, 1, 99),
                intArrayOf(60, 70, 40, 55, 43, 30, 50, 33),
                intArrayOf(1, 1, 45, 60, 52, 99, 1, 71),
                intArrayOf(1, 1, 70, 80, 77, 1, 99, 88),
                intArrayOf(75, 99, 1, 90, 1, 1, 1, 23),
            )
        val random = java.util.Random(2026_09_28)
        val profiles = fixed + (0 until 30).map { IntArray(8) { i -> if (i == 3) 10 + random.nextInt(90) else 1 + random.nextInt(99) } }
        for (p in profiles) {
            if (Conformance.stopRequested) break
            val lv = skills.zip(p.toList()).toMap()
            val trigger = if (lv[Skills.SUMMONING]!! > 1) Skills.SUMMONING else Skills.ATTACK.takeIf { lv[Skills.ATTACK]!! > 1 } ?: Skills.CONSTITUTION
            for ((skill, level) in lv) {
                if (skill == trigger) {
                    bot.skills.setBaseXp(skill, gg.rsmod.game.model.skill.SkillSet.getXpForLevel(level) - 0.5)
                } else {
                    bot.skills.setBaseLevel(skill, level)
                }
            }
            bot.addXp(trigger, 1.0, modifiers = false, disableBonusExperience = true)
            wait(1)
            val att = lv[Skills.ATTACK]!!
            val str = lv[Skills.STRENGTH]!!
            val def = lv[Skills.DEFENCE]!!
            val hp = lv[Skills.CONSTITUTION]!!
            val pray = lv[Skills.PRAYER]!!
            val rng = lv[Skills.RANGED]!!
            val mag = lv[Skills.MAGIC]!!
            val summ = lv[Skills.SUMMONING]!!
            val voidLevel = ((max(att + str, max(rng * 3 / 2, mag * 3 / 2)) * 13 / 10) + def + hp + pray / 2 + summ / 2) / 4
            val rs2 = Math.floor((def + hp + pray / 2 + summ / 2) / 4.0 + 0.325 * max((att + str).toDouble(), max(Math.floor(1.5 * rng), Math.floor(1.5 * mag)))).toInt()
            run.check(e, "levels ${p.joinToString("/")} (att/str/def/hp/pray/rng/mag/summ)", bot.combatLevel, "void" to voidLevel, "rs2 formula" to rs2)
        }
        run.recording.untrack(bot)
    }

    // ---- once per run: lifecycle rules ------------------------------------------------------------------------------------

    private suspend fun QueueTask.lifecycle(
        run: Conformance.Run,
        bot: Client,
        arena: Tile,
        refs: JsonNode,
        common: JsonNode,
    ) {
        val e = "summoning lifecycle"
        fun data(name: String) = SummoningPouchData.values().firstOrNull { it.name.equals(name, true) }
        suspend fun summon(d: SummoningPouchData): Npc? {
            reset(run, bot, arena)
            bot.inventory.add(d.pouch, 2)
            Bots.itemOption(bot, bot.inventory.getItemIndex(d.pouch, false), "Summon")
            wait(3)
            return Familiar.current(bot)?.also { run.recording.track(it, "familiar") }
        }

        // Second summon while a familiar is out.
        data("SPIRIT_WOLF")?.let { d ->
            summon(d) ?: return@let
            val c = run.world.currentCycle
            Bots.itemOption(bot, bot.inventory.getItemIndex(d.pouch, false), "Summon")
            wait(2)
            run.check(e, "second summon refused (pouch kept)", bot.inventory.getItemCount(d.pouch), "rule" to 1)
            val said = gameMessages(run, "Conf1", c)
            run.check(e, "second summon message", said.lastOrNull()?.let { Regex("message=([^,)]*)").find(it)?.groupValues?.get(1) }, "void" to "You already have a follower.")
        }

        // Renew: pouch used, timer back to full, graphic + sound.
        data("SPIRIT_WOLF")?.let { d ->
            summon(d) ?: return@let
            bot.timers[FAMILIAR_LIFETIME_TIMER] = 120
            val c = run.world.currentCycle
            Bots.button(bot, ORB, 13)
            wait(1)
            Bots.answerOption(bot, 1)
            wait(3)
            val v = refs[d.pouch.toString()]?.get("void")
            run.check(e, "renew: pouch used", 2 - 1 - bot.inventory.getItemCount(d.pouch), "rule" to 1)
            run.check(e, "renew: lifetime back to full (ticks)", if (bot.timers.has(FAMILIAR_LIFETIME_TIMER)) bot.timers[FAMILIAR_LIFETIME_TIMER] else null, "void" to v?.get("time_minutes")?.asInt()?.times(100), same = ::within3)
            val gfx = run.recording.of("Conf1", "out").filter { it.cycle >= c && it.text.startsWith("gfx ") }.map { idOf(it.text) }
            run.check(e, "renew: player graphic", gfx.firstOrNull(), "void" to common["renew_gfx_player"]?.asInt())
            val snd = run.recording.of("Conf1", "msg").filter { it.cycle >= c && it.text.contains("Sound") }.map { it.text }
            run.expectTrue(e, "renew: sound ${common["renew_sound"]?.asInt()}", snd.any { it.contains("${common["renew_sound"]?.asInt()}") }, snd.joinToString(" / ").ifEmpty { "(none)" })
        }

        // Expiry: warnings at 1 minute and 30 seconds, then gone.
        data("SPIRIT_WOLF")?.let { d ->
            summon(d) ?: return@let
            val c = run.world.currentCycle
            bot.timers[FAMILIAR_LIFETIME_TIMER] = 105
            for (t in 0 until 115) {
                bot.skills.setCurrentLevel(Skills.SUMMONING, 99)
                wait(1)
            }
            val said = gameMessages(run, "Conf1", c)
            val lm = common["lifetime_messages"]
            run.expectTrue(e, "expiry: 1 minute warning", said.any { it.contains(lm["1min"].asText()) }, said.joinToString(" / ").ifEmpty { "(none)" }, lm["1min"].asText() + " (void)")
            run.expectTrue(e, "expiry: 30 seconds warning", said.any { it.contains(lm["30s"].asText()) }, said.joinToString(" / ").ifEmpty { "(none)" }, lm["30s"].asText() + " (void)")
            run.expectTrue(e, "expiry: familiar gone", Familiar.current(bot) == null, "familiar=${Familiar.current(bot)?.id}")
            run.check(e, "expiry: varp 1174 cleared", bot.varps.getState(VARP_FOLLOWER_CHATHEAD), "void" to 0)
        }

        // Points drain: total over the lifetime = level - cost + 1, measured over 300 ticks on the fastest drainers.
        val fastest =
            SummoningPouchData.values().mapNotNull { d ->
                val v = refs[d.pouch.toString()]?.get("void") ?: return@mapNotNull null
                val total = d.level - v["summon_points"].asInt() + 1
                val ticks = v["time_minutes"].asInt() * 100
                if (ticks <= 0) null else Triple(d, total, ticks)
            }.sortedByDescending { it.second.toDouble() / it.third }.take(2)
        for ((d, total, ticks) in fastest) {
            summon(d) ?: continue
            val p0 = bot.skills.getCurrentLevel(Skills.SUMMONING)
            wait(300)
            val drained = p0 - bot.skills.getCurrentLevel(Skills.SUMMONING)
            val expected = total * 300 / ticks
            run.check(e, "drain over 300 ticks: ${d.name.lowercase()} (total $total over $ticks ticks)", drained, "void" to expected, same = ::within1)
        }

        // Out of points: familiar vanishes with the message.
        fastest.firstOrNull()?.let { (d, _, _) ->
            summon(d) ?: return@let
            bot.skills.setCurrentLevel(Skills.SUMMONING, 1)
            val c = run.world.currentCycle
            wait(fastest.first().third / fastest.first().second + 5)
            val said = gameMessages(run, "Conf1", c)
            run.expectTrue(e, "out of points: familiar vanishes", Familiar.current(bot) == null, "familiar=${Familiar.current(bot)?.id} points=${bot.skills.getCurrentLevel(Skills.SUMMONING)}")
            val msg = common["lifetime_messages"]["no_points"].asText()
            run.expectTrue(e, "out of points: message", said.any { it.contains(msg) }, said.joinToString(" / ").ifEmpty { "(none)" }, "$msg (void)")
        }

        // Special points regenerate +15 every 30 seconds, capped at 60.
        data("SPIRIT_WOLF")?.let { d ->
            summon(d) ?: return@let
            bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = 0
            wait(52)
            val rg = common["special_points_regen"]
            run.check(e, "special points after 52 ticks from 0", bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR], "void" to rg["amount"].asInt())
            bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = 55
            wait(52)
            run.check(e, "special points capped", bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR], "void" to rg["cap"].asInt())
        }

        // Passive heals (void spinner, bunyip).
        for (name in listOf("VOID_SPINNER", "BUNYIP")) {
            val d = data(name) ?: continue
            val heal = refs[d.pouch.toString()]?.get("void")?.get("heal")?.takeIf { !it.isNull } ?: continue
            summon(d) ?: continue
            bot.setCurrentLifepoints(100)
            val c = run.world.currentCycle
            wait(27)
            run.check(e, "passive heal after 27 ticks: ${name.lowercase()}", bot.getCurrentLifepoints() - 100, "void" to heal["lifepoints"].asInt())
            heal["gfx"]?.takeIf { !it.isNull }?.asInt()?.let { gfx ->
                run.expectTrue(e, "passive heal graphic: ${name.lowercase()}", run.recording.of("Conf1", "out").any { it.cycle >= c && it.text.startsWith("gfx ") && idOf(it.text) == gfx }, "no gfx $gfx on the owner", "gfx $gfx (void)")
            }
            bot.setCurrentLifepoints(bot.getMaximumLifepoints())
        }

        // Overhead lines every 30 seconds.
        for (name in listOf("SPIRIT_WOLF", "DREADFOWL")) {
            val d = data(name) ?: continue
            val lines = refs[d.pouch.toString()]?.get("void")?.get("overhead")?.map { it.asText() }?.takeIf { it.isNotEmpty() } ?: continue
            summon(d) ?: continue
            val c = run.world.currentCycle
            wait(55)
            val said = run.recording.of("familiar", "out").filter { it.cycle >= c && it.text.startsWith("forcechat") }.map { it.text.substringAfter('"').substringBeforeLast('"') }
            run.expectTrue(e, "overhead line within 55 ticks: ${name.lowercase()}", said.any { it in lines }, said.joinToString(" / ").ifEmpty { "(none)" }, lines.joinToString(" | ") + " (void, RS Wiki transcripts)")
        }

        // Owner death: the familiar leaves.
        data("SPIRIT_WOLF")?.let { d ->
            summon(d) ?: return@let
            bot.hit(bot.getCurrentLifepoints())
            wait(8)
            run.expectTrue(e, "owner death removes the familiar", Familiar.current(bot) == null, "familiar=${Familiar.current(bot)?.id}")
            wait(4)
            bot.moveTo(arena)
            bot.setCurrentLifepoints(bot.getMaximumLifepoints())
        }
        runCatching { Familiar.dismiss(bot) }
    }

    // ---- follow ------------------------------------------------------------------------------------------------------------

    /**
     * The owner clicks [to] (walk or run); every tick until 4 ticks after arrival: the familiar never stands on the owner,
     * never in a wall, never more than 2 tiles behind; afterwards it stands next to the owner.
     */
    private suspend fun QueueTask.follow(
        run: Conformance.Run,
        bot: Client,
        fam: Npc,
        size: Int,
        to: Tile,
        running: Boolean,
        e: String,
        kind: String,
    ) {
        if (running) bot.varps.setState(173, 1) else bot.varps.setState(173, 0)
        Bots.walk(bot, to, running)
        val overlaps = ArrayList<String>()
        val walls = ArrayList<String>()
        var maxGap = 0
        var still = 0
        var t = 0
        while (t < 40 && still < 4) {
            wait(1)
            t++
            val g = gap(bot.tile, bot.getSize(), fam.tile, size)
            if (g == 0) overlaps += "t$t ${fam.tile.x},${fam.tile.z}"
            if (clipped(run, fam.tile, size)) walls += "t$t ${fam.tile.x},${fam.tile.z}"
            maxGap = max(maxGap, g)
            if (bot.tile.sameAs(to)) still++
        }
        run.expectTrue(e, "$kind: owner reached the click", bot.tile.sameAs(to), "owner=${bot.tile} target=$to")
        run.expectTrue(e, "$kind: never on the owner's tile", overlaps.isEmpty(), overlaps.joinToString(" ").ifEmpty { "-" })
        run.expectTrue(e, "$kind: never inside a wall", walls.isEmpty(), walls.joinToString(" ").ifEmpty { "-" })
        run.expectTrue(e, "$kind: never more than 2 tiles behind", maxGap <= 2, "max gap $maxGap")
        val end = gap(bot.tile, bot.getSize(), fam.tile, size)
        run.expectTrue(e, "$kind: next to the owner when stopped", end == 1, "gap=$end")
    }

    // ---- combat ------------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.combat(
        run: Conformance.Run,
        bot: Client,
        fam: Npc,
        dummy: Npc,
        arena: Tile,
        attacks: JsonNode,
        v: JsonNode,
        e: String,
    ) {
        val world = run.world
        val style = attacks.fieldNames().next()
        val a = attacks[style]
        bot.moveTo(arena)
        dummy.setCurrentLifepoints(DUMMY_LIFE)
        wait(1)
        val c = world.currentCycle
        // The follower panel's Attack is the real "order your familiar to attack" (the bot itself does not fight).
        Bots.targetNpc(bot, PANEL, PANEL_ATTACK, dummy)
        val distances = HashMap<Int, Int>()
        for (t in 0 until 80) {
            distances[world.currentCycle] = gap(fam.tile, runCatching { world.definitions.get(NpcDef::class.java, fam.id).size }.getOrDefault(1), dummy.tile, dummy.getSize())
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            bot.setCurrentLifepoints(bot.getMaximumLifepoints())
            wait(1)
        }
        val expectedAnim = a["anim"]?.takeIf { !it.isNull }?.asInt()
        val anims = run.recording.of("familiar", "out").filter { it.cycle >= c && it.text.startsWith("anim ") }
        val attackCycles = anims.filter { expectedAnim == null || idOf(it.text) == expectedAnim }.map { it.cycle }
        val mostFrequent = anims.groupBy { idOf(it.text) }.maxByOrNull { it.value.size }?.key
        run.check(e, "attack ($style) animation", mostFrequent, "void" to expectedAnim)
        attackCycles.firstOrNull()?.let { sound(run, e, "attack", it, it + 1, "familiar") }
        run.recording.of("dummy", "out").firstOrNull { it.cycle >= c && it.text.startsWith("hits ") }?.let { sound(run, e, "familiar hit lands", it.cycle, it.cycle, "dummy") }
        run.expectTrue(e, "attacks when ordered (80 ticks)", attackCycles.size >= 2, "attack animations=${attackCycles.size}", ">= 2")
        val intervals = attackCycles.zipWithNext { x, y -> y - x }
        val speed = intervals.groupBy { it }.maxByOrNull { it.value.size }?.key
        run.check(e, "attack speed (ticks)", speed, "void" to (a["speed"]?.takeIf { !it.isNull }?.asInt() ?: v["attack_speed"]?.takeIf { !it.isNull }?.asInt()))
        a["projectile"]?.takeIf { !it.isNull }?.asInt()?.let { p ->
            val projs = run.recording.events.filter { it.cycle >= c && it.kind == "proj" && it.text.contains("to=dummy") }.map { idOf(it.text.replace("gfx=", "id=")) }
            run.check(e, "attack projectile", projs.groupBy { it }.maxByOrNull { it.value.size }?.key, "void" to p)
        }
        val hits = run.recording.of("dummy", "out").filter { it.cycle >= c && it.text.startsWith("hits ") }.flatMap { damages(it.text) }
        val max = a["max"]?.takeIf { !it.isNull }?.asInt()
        if (max != null) {
            val observed = hits.maxOrNull() ?: 0
            run.expectTrue(e, "attack max hit never exceeded", observed <= max, "max observed $observed over ${hits.size} hits", "<= $max (void)")
            run.add(Result(e, "attack damage sample", Verdict.PASS, "max $max (void)", "${hits.size} hits, max ${hits.maxOrNull()}, mean ${if (hits.isEmpty()) 0 else hits.average().toInt()}"))
        }
        combatStats(run, fam, dummy, style, a, v, attackCycles, distances, c, e)
        // Defend animation: the dummy hits the familiar once.
        val cd = world.currentCycle
        fam.resetInteractions()
        fam.hit(1)
        wait(2)
        sound(run, e, "familiar blocks", cd, world.currentCycle, "familiar")
        run.check(e, "defend animation", run.recording.of("familiar", "out").firstOrNull { it.cycle >= cd && it.text.startsWith("anim ") }?.let { idOf(it.text) }, "void" to v["defend_anim"]?.takeIf { !it.isNull }?.asInt())
        fam.resetInteractions()
        gg.rsmod.plugins.content.combat.Combat.reset(fam)
        fam.setCurrentLifepoints(fam.getMaximumLifepoints())
    }

    /**
     * Familiar combat stats as measured in the 80-tick fight: attack range at the moment of each attack, hitsplat type per
     * style, hit rate against the RS npc accuracy formula with the donor stats (3 sigma + 5 % tolerance), and whether the
     * max hit is ever approached once enough hits landed.
     */
    private fun combatStats(
        run: Conformance.Run,
        fam: Npc,
        dummy: Npc,
        style: String,
        a: JsonNode,
        v: JsonNode,
        attackCycles: List<Int>,
        distances: Map<Int, Int>,
        from: Int,
        e: String,
    ) {
        val range = a["range"]?.takeIf { !it.isNull }?.asInt()
        val atAttack = attackCycles.mapNotNull { distances[it] ?: distances[it - 1] }
        if (atAttack.isNotEmpty() && range != null) {
            run.expectTrue(e, "attack range: every attack within $range tiles", atAttack.all { it in 1..range }, "distances at attack: ${atAttack.distinct().sorted()}", "1..$range (void)")
            if (style == "melee") run.expectTrue(e, "melee attacks only when adjacent", atAttack.all { it == 1 }, "distances ${atAttack.distinct()}", "1")
        }
        val splats =
            run.recording.of("dummy", "out").filter { it.cycle >= from && it.text.startsWith("hits ") }
                .flatMap { Regex("type=(\\d+) dmg=(\\d+)").findAll(it.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
        val expectedType =
            when (style) {
                "range" -> gg.rsmod.plugins.api.HitType.RANGE.id
                "magic" -> gg.rsmod.plugins.api.HitType.MAGIC.id
                else -> gg.rsmod.plugins.api.HitType.MELEE.id
            }
        val landed = splats.filter { it.second > 0 }
        if (landed.isNotEmpty()) {
            run.check(e, "hitsplat type for $style (${a["offense"]?.asText()})", landed.groupBy { it.first }.maxByOrNull { it.value.size }?.key, "667 hitmark ids" to expectedType)
        }
        val max = a["max"]?.takeIf { !it.isNull }?.asInt() ?: return
        val stats = v["stats"]
        val attLevel =
            when (style) {
                "range" -> stats["range"]?.asInt()
                "magic" -> stats["mage"]?.asInt()
                else -> stats["att"]?.asInt()
            } ?: return
        val defLevel =
            when (style) {
                "magic" -> dummy.stats.getMaxLevel(NpcSkills.MAGIC)
                else -> dummy.stats.getMaxLevel(NpcSkills.DEFENCE)
            }
        val attRoll = (attLevel + 9) * 64.0
        val defRoll = (defLevel + 9) * 64.0
        val p = if (attRoll > defRoll) 1 - (defRoll + 2) / (2 * (attRoll + 1)) else attRoll / (2 * (defRoll + 1))
        val expectedNonZero = p * max / (max + 1.0)
        val n = splats.size
        if (n >= 8) {
            val measured = landed.size.toDouble() / n
            val sigma = Math.sqrt(expectedNonZero * (1 - expectedNonZero) / n)
            val ok = Math.abs(measured - expectedNonZero) <= 3 * sigma + 0.05
            run.add(
                Result(
                    e, "accuracy: non-zero hit rate over $n attacks", if (ok) Verdict.PASS else Verdict.FAIL,
                    "%.2f (RS npc formula, void level $attLevel vs dummy $defLevel, bonuses 0 - unsourced)".format(java.util.Locale.ENGLISH, expectedNonZero),
                    "%.2f".format(java.util.Locale.ENGLISH, measured),
                ),
            )
        }
        if (landed.size >= 20) {
            val top = landed.maxOf { it.second }
            run.expectTrue(e, "max hit approached (20+ landed hits)", top >= max * 0.8, "highest $top of max $max over ${landed.size} landed", ">= 80 % of $max")
        }
        run.check(e, "combat level (npc definition)", runCatching { run.world.definitions.get(NpcDef::class.java, fam.id).combatLevel }.getOrNull(), "cache" to runCatching { run.world.definitions.get(NpcDef::class.java, v["npc"].asInt()).combatLevel }.getOrNull())
    }

    // ---- special move ------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.special(
        run: Conformance.Run,
        bot: Client,
        fam: Npc,
        dummy: Npc,
        data: SummoningPouchData,
        v: JsonNode?,
        n: JsonNode?,
        common: JsonNode,
        e: String,
    ) {
        val world = run.world
        val binding = SummoningSpecialMoves.resolveBinding(bot)
        val vs = v?.get("special")
        val refTarget = vs?.get("target")?.asText()
        val novType = n?.get("special_type")?.asText()?.substringAfter('.')
        run.check(e, "special target type", binding?.target?.name?.lowercase(), "void" to refTarget?.let { mapVoidTarget(it) }, "novite" to novType?.let { mapNoviteTarget(it) })
        val scroll = SummoningScrollData.values.firstOrNull { data.pouch in it.pouches }
        if (binding == null || scroll == null) {
            run.add(Result(e, "special move", Verdict.FAIL, "a special move for this familiar", "binding=$binding scroll=${scroll?.name}"))
            return
        }
        run.check(e, "scroll id", scroll.scroll, "novite" to n?.get("scroll")?.asInt(), "void" to v?.get("scroll")?.get("id")?.asInt())
        bot.inventory.add(scroll.scroll, 3)
        bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = 60
        bot.timers.remove(SummoningSpecialMoves.SPECIAL_MOVE_DELAY_TIMER)
        bot.setCurrentLifepoints(bot.getMaximumLifepoints() / 2) // heals must have something to heal
        dummy.setCurrentLifepoints(DUMMY_LIFE)
        val levels0 = (0 until bot.skills.maxSkills).map { bot.skills.getCurrentLevel(it) }
        val dummyHp0 = dummy.getCurrentLifepoints()
        wait(1)
        val c = world.currentCycle
        val cast =
            when (binding.target) {
                FamiliarSpecialTarget.INSTANT -> Bots.button(bot, ORB, ORB_SPECIAL)
                FamiliarSpecialTarget.NPC -> Bots.targetNpc(bot, ORB, ORB_SPECIAL, dummy)
                else -> {
                    run.notTested(e, "special move cast (${binding.target})", "item/object/player-target specials need a target scenario")
                    return
                }
            }
        run.expectTrue(e, "special click accepted by the input path", cast, "inject=$cast")
        for (t in 0 until 8) {
            dummy.setCurrentLifepoints(max(dummy.getCurrentLifepoints(), 1))
            wait(1)
        }
        val used = 3 - bot.inventory.getItemCount(scroll.scroll)
        run.check(e, "special: scrolls used", used, "rule" to 1)
        val pointsUsed = 60 - (bot.attr[FAMILIAR_SPECIAL_POINTS_ATTR] ?: 60)
        run.check(e, "special: special points cost", pointsUsed, "void" to v?.get("scroll")?.get("special_points")?.asInt(), "novite" to n?.get("special_points")?.asText()?.toIntOrNull())
        val famOut = run.recording.of("familiar", "out").filter { it.cycle >= c }
        val botOut = run.recording.of("Conf1", "out").filter { it.cycle >= c }
        val refAnims = ints(vs?.get("anims")) + ints(n?.get("special_anims"))
        val allAnims = (famOut + botOut).filter { it.text.startsWith("anim ") }.map { idOf(it.text) }
        run.check(e, "special: animations played", allAnims.filter { it in refAnims }.distinct().sorted().ifEmpty { allAnims.distinct() }, "void" to ints(vs?.get("anims")).distinct().sorted().ifEmpty { null }, "novite" to ints(n?.get("special_anims")).distinct().sorted().ifEmpty { null }, same = ::sameSet)
        val allGfx = (famOut + botOut + run.recording.of("dummy", "out").filter { it.cycle >= c }).filter { it.text.startsWith("gfx ") }.map { idOf(it.text) }
        val voidGfx = (ints(vs?.get("source_gfx")) + ints(vs?.get("player_gfx")) + ints(vs?.get("target_gfx"))).distinct().sorted()
        run.check(e, "special: graphics shown", allGfx.distinct().sorted(), "void" to voidGfx.ifEmpty { null }, "novite" to ints(n?.get("special_gfx")).distinct().sorted().ifEmpty { null }, same = ::sameSet)
        val projs = run.recording.events.filter { it.cycle >= c && it.kind == "proj" }.map { idOf(it.text.replace("gfx=", "id=")) }.distinct().sorted()
        run.check(e, "special: projectiles", projs, "void" to ints(vs?.get("projectiles")).distinct().sorted().ifEmpty { null }, "novite" to ints(n?.get("special_projectiles")).distinct().sorted().ifEmpty { null }, same = ::sameSet)
        sound(run, e, "special move", c, world.currentCycle, "familiar", "Conf1", "dummy")
        val sounds = run.recording.of("Conf1", "msg").filter { it.cycle >= c && it.text.contains("Sound") }.map { it.text }
        val castSound = common["special_cast_sound"]?.asInt()
        run.expectTrue(e, "special: cast sound $castSound sent", castSound == null || sounds.any { it.contains("$castSound") }, sounds.joinToString(" / ").ifEmpty { "(no sound message)" }, "sound $castSound (void summoning_special_cast)")
        val maxHit = ints(vs?.get("max_hit")).firstOrNull()
        if (maxHit != null) {
            val dealt = run.recording.of("dummy", "out").filter { it.cycle >= c && it.text.startsWith("hits ") }.flatMap { damages(it.text) }
            run.expectTrue(e, "special: damage within max $maxHit", dealt.all { it <= maxHit }, "hits=$dealt (dummy hp ${dummyHp0} -> ${dummy.getCurrentLifepoints()})", "<= $maxHit (void)")
            run.expectTrue(e, "special: a hit landed on the target", dealt.isNotEmpty(), "hitsplats=$dealt", "a hitsplat (0 allowed)")
        }
        vs?.get("boosts")?.forEach { b ->
            val skill = skillIndex(b[0].asText()) ?: return@forEach
            val amount = b[1].asText().trim().toIntOrNull() ?: return@forEach
            val delta = bot.skills.getCurrentLevel(skill) - levels0[skill]
            run.check(e, "special: ${b[0].asText()} boost", delta, "void" to amount)
        }
        run.add(Result(e, "special: effect detail (void code)", Verdict.NOT_TESTED, (vs?.get("code")?.asText() ?: n?.get("special_desc")?.asText() ?: "-").lines().joinToString(" ").take(160), "the measured state changes are in the timeline"))
    }

    // ---- beast of burden ---------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.bob(
        run: Conformance.Run,
        bot: Client,
        fam: Npc,
        capacity: Int,
        v: JsonNode?,
        n: JsonNode?,
        e: String,
    ) {
        run.check(e, "bob capacity (server storage)", BeastOfBurden.activeContainer(bot)?.capacity, "void" to v?.get("bob_capacity")?.takeIf { !it.isNull }?.asInt(), "novite" to n?.get("bob_size")?.asText()?.toIntOrNull()?.takeIf { it > 0 })
        bot.inventory.removeAll()
        val item = Items.BRONZE_BAR
        bot.inventory.add(item, 28)
        val c = run.world.currentCycle
        for (i in 0 until capacity + 1) {
            val slot = bot.inventory.getItemIndex(item, false)
            if (slot < 0) break
            Bots.itemOnNpc(bot, slot, fam)
            wait(1)
        }
        wait(1)
        val stored = BeastOfBurden.storedCount(bot)
        run.check(e, "bob: items stored when filled with ${capacity + 1}", stored, "reference" to capacity)
        run.check(e, "bob: no item lost or made while storing", stored + bot.inventory.getItemCount(item), "rule" to 28)
        val said = gameMessages(run, "Conf1", c)
        run.expectTrue(e, "bob: full message", said.any { it.contains("full", true) || it.contains("carry", true) || it.contains("space", true) }, said.joinToString(" / ").ifEmpty { "(no message)" }, "a message when it is full")
        Bots.button(bot, ORB, ORB_TAKE_BOB)
        wait(2)
        run.check(e, "bob: Take BoB returns everything", bot.inventory.getItemCount(item), "rule" to 28)
        run.check(e, "bob: empty after Take BoB", BeastOfBurden.storedCount(bot), "rule" to 0)
    }

    // ---- judgements across familiars ----------------------------------------------------------------------------------------

    private fun judgeXpRatios(run: Conformance.Run) {
        if (xpRatios.isEmpty()) return
        val median = xpRatios.values.sorted()[xpRatios.size / 2]
        run.note("summon xp rate (gained / donor base xp): median x${"%.3f".format(java.util.Locale.ENGLISH, median)}")
        xpRatios.forEach { (entry, ratio) ->
            val ok = Math.abs(ratio - median) <= median * 0.02
            run.add(Result(entry, "summon xp (same server rate as every familiar)", if (ok) Verdict.PASS else Verdict.FAIL, "x${"%.3f".format(java.util.Locale.ENGLISH, median)} of the donor xp", "x${"%.3f".format(java.util.Locale.ENGLISH, ratio)}"))
        }
    }

    private fun notTestedCatalogue(run: Conformance.Run) {
        listOf(
            "renew (pouch, timer reset, animation)",
            "lifetime warnings and expiry despawn",
            "points drain rate over the lifetime",
            "follow up/down stairs, ladders and through doors",
            "follow around obstacles and corners",
            "areas: wilderness, minigames, POH, banks, instances, Deadman safe zones",
            "familiar death (animation, message, BoB rule) and owner death",
            "logout/login and server restart persistence",
            "item/object/player-target special moves",
            "special moves in PvP",
            "familiar attacks in PvP",
            "interact options (Interact, Forage, Fish, Pick-up...) and chathead dialogues",
            "invisible skill boosts and passive effects (forage, heal)",
            "scroll storage in the pouch and the orb left-click option",
            "Follower Details panel texts and orb menu entries",
            "pouch/scroll creation, transformation, obelisk, trade-in, shard swap",
            "bystander bot sees and hears the same events; two owners side by side",
        ).forEach { run.notTested("summoning", it, "no scenario yet") }
    }

    // ---- sound -------------------------------------------------------------------------------------------------------------

    /**
     * Is [moment] audible? A moment has sound when an animation played by [who] in [from]..[to] carries audible frame sounds
     * in the revision-667 cache (the client plays those itself), or when the server sent a sound (SynthSound / area sound) to
     * the owner. Also: every sent sound id exists in the cache, and no sound is sent that the animation already plays (heard
     * twice).
     */
    private fun sound(
        run: Conformance.Run,
        e: String,
        moment: String,
        from: Int,
        to: Int,
        vararg who: String,
    ) {
        val world = run.world
        val anims =
            who.flatMap { w -> run.recording.of(w, "out").filter { it.cycle in from..to && it.text.startsWith("anim ") }.map { idOf(it.text) } }.filter { it >= 0 }.distinct()
        val frame =
            anims.associateWith { id ->
                runCatching { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, id) }.getOrNull()?.let { if (it.hasAudibleFrameSounds) it.firstAudibleFrameSound else null }
            }.filterValues { it != null }
        val sent =
            run.recording.of("Conf1", "msg").filter { it.cycle in from..to && it.text.contains("Sound") && !it.text.startsWith("SoundSongEnd") }
                .mapNotNull { Regex("(?:sound|id)=(\\d+)").find(it.text)?.groupValues?.get(1)?.toInt() } +
                run.recording.events.filter { it.cycle in from..to && it.kind == "areasound" }.map { idOf(it.text) }
        val detail = "anims=${anims.joinToString(",")} frameSounds=${frame.entries.joinToString(",") { "${it.key}:${it.value}" }.ifEmpty { "-" }} sent=${sent.joinToString(",").ifEmpty { "-" }}"
        run.expectTrue(e, "sound: $moment is audible", frame.isNotEmpty() || sent.isNotEmpty(), detail, "a frame sound or a sent sound")
        sent.filter { !world.soundExists(it) }.forEach { run.add(Result(e, "sound: $moment id $it exists in the 667 cache", Verdict.FAIL, "exists", "missing")) }
        // Volume and timing of every sound message the owner got; the bystander 4 tiles away must hear the moment too.
        val soundMsgs = run.recording.of("Conf1", "msg").filter { it.cycle in from..to && it.text.contains("Sound") && !it.text.startsWith("SoundSongEnd") }
        soundMsgs.forEach { m ->
            val vol = Regex("volume=(\\d+)").find(m.text)?.groupValues?.get(1)?.toInt()
            if (vol != null) run.expectTrue(e, "sound: $moment volume > 0", vol > 0, m.text.take(160))
        }
        val animCycles = who.flatMap { w -> run.recording.of(w, "out").filter { it.cycle in from..to && it.text.startsWith("anim ") }.map { it.cycle } }
        if (animCycles.isNotEmpty() && soundMsgs.isNotEmpty()) {
            val lag = soundMsgs.minOf { m -> animCycles.minOf { Math.abs(it - m.cycle) } }
            run.expectTrue(e, "sound: $moment on the animation tick", lag <= 1, "sound ${lag} ticks from the animation", "<= 1 tick")
        }
        val bystanderHeard =
            frame.isNotEmpty() || run.recording.of("Bystander", "msg").any { it.cycle in from..to && it.text.contains("Sound") && !it.text.startsWith("SoundSongEnd") }
        run.expectTrue(e, "sound: bystander 4 tiles away hears $moment", bystanderHeard || (frame.isEmpty() && sent.isEmpty()), if (bystanderHeard) "heard" else "only the owner got the sound (${sent.joinToString(",")})", "frame sound or a sound message to the bystander (RS: nearby players hear it)")
        val doubled = sent.filter { it in frame.values }
        run.expectTrue(e, "sound: $moment not heard twice", doubled.isEmpty(), if (doubled.isEmpty()) detail else "server also sends frame sound(s) $doubled", "no sent sound equal to the animation's own frame sound")
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------------

    private fun reset(
        run: Conformance.Run,
        bot: Client,
        arena: Tile,
    ) {
        runCatching { Familiar.dismiss(bot) }
        bot.resetInteractions()
        bot.moveTo(arena)
        bot.inventory.removeAll()
        bot.skills.setBaseLevel(Skills.SUMMONING, 99)
        bot.skills.setCurrentLevel(Skills.SUMMONING, 99)
        bot.setCurrentLifepoints(bot.getMaximumLifepoints())
        if (run.world.currentCycle < 0) run.note("unreachable")
    }

    private fun gameMessages(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }.map { it.text }

    private fun sentVarp(
        run: Conformance.Run,
        who: String,
        from: Int,
        id: Int,
    ) = run.recording.of(who, "msg").any { it.cycle >= from && it.text.startsWith("Varp") && Regex("\\bid=$id\\b").containsMatchIn(it.text) }

    private fun idOf(text: String): Int = Regex("\\bid=(-?\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: -1

    private fun damages(text: String): List<Int> = Regex("dmg=(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.toList()

    private fun ints(node: JsonNode?): List<Int> = node?.filter { it.isInt }?.map { it.asInt() } ?: emptyList()

    /** Chebyshev gap between two footprints: 0 = overlapping, 1 = touching. */
    fun gap(
        a: Tile,
        aSize: Int,
        b: Tile,
        bSize: Int,
    ): Int {
        if (a.height != b.height) return 99
        val dx = max(0, max(b.x - (a.x + aSize - 1), a.x - (b.x + bSize - 1)))
        val dz = max(0, max(b.z - (a.z + aSize - 1), a.z - (b.z + bSize - 1)))
        return max(dx, dz)
    }

    private fun clipped(
        run: Conformance.Run,
        tile: Tile,
        size: Int,
    ): Boolean {
        for (x in 0 until size) for (z in 0 until size) {
            if (runCatching { run.world.collision.isClipped(tile.transform(x, z)) }.getOrDefault(false)) return true
        }
        return false
    }

    private fun within3(
        a: Any?,
        b: Any?,
    ) = a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 3

    private fun within1(
        a: Any?,
        b: Any?,
    ) = a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 1

    private fun sameSet(
        a: Any?,
        b: Any?,
    ) = (a as? Collection<*>)?.toSet() == (b as? Collection<*>)?.toSet()

    private fun mapVoidTarget(t: String) =
        when (t) {
            "instant" -> "instant"
            "npc" -> "npc"
            "player" -> "player"
            "obj" -> "object"
            "item" -> "inventory_item"
            "floorItem" -> "floor_item"
            else -> t
        }

    private fun mapNoviteTarget(t: String) =
        when (t) {
            "CLICK" -> "instant"
            "ENTITY" -> "npc"
            "ITEM" -> "inventory_item"
            "OBJECT" -> "object"
            else -> t.lowercase()
        }

    private fun skillIndex(name: String): Int? =
        when (name.substringAfter('.')) {
            "Attack" -> Skills.ATTACK
            "Defence" -> Skills.DEFENCE
            "Strength" -> Skills.STRENGTH
            "Constitution" -> Skills.CONSTITUTION
            "Ranged" -> Skills.RANGED
            "Prayer" -> Skills.PRAYER
            "Magic" -> Skills.MAGIC
            "Agility" -> Skills.AGILITY
            "Thieving" -> Skills.THIEVING
            "Mining" -> Skills.MINING
            "Woodcutting" -> Skills.WOODCUTTING
            "Fishing" -> Skills.FISHING
            "Farming" -> Skills.FARMING
            "Hunter" -> Skills.HUNTER
            "Firemaking" -> Skills.FIREMAKING
            "Crafting" -> Skills.CRAFTING
            "Smithing" -> Skills.SMITHING
            "Herblore" -> Skills.HERBLORE
            else -> null
        }

    /** The nearest tile (rings of 8 outwards, up to 400) that is multi-way with room for the bot, a familiar and the dummy. */
    private fun nearestMulti(
        run: Conformance.Run,
        from: Tile,
    ): Tile? {
        val world = run.world
        fun free(t: Tile) = (0..6).all { dx -> (-2..10).all { dz -> !runCatching { world.collision.isClipped(t.transform(dx, dz)) }.getOrDefault(true) } }
        if (from.isMulti(world) && free(from)) return from
        for (r in 8..400 step 8) {
            for (dx in -r..r step 8) for (dz in listOf(-r, r)) {
                listOf(from.transform(dx, dz), from.transform(dz, dx)).forEach { t -> if (t.isMulti(world) && free(t)) return t }
            }
        }
        return null
    }
}
