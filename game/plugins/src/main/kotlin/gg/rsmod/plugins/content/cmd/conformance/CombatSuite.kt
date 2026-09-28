package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * Combat core conformance (catalogue section 3) - the tick rules every fight uses, OSRS as the reference (owner rule):
 *
 *   eat delay       eating mid-fight adds 3 ticks to the attack timer (OSRS Wiki "Food"); shark + karambwan combo same tick
 *   special regen   10 % special energy per 30 s (OSRS Wiki "Special attacks")
 *   freezes         bind/snare/entangle 5/10/15 s, ice rush/burst/blitz/barrage 5/10/15/20 s on a player, then a 3 s
 *                   immunity (OSRS Wiki "Freeze") - measured as the first tick the target can walk again
 *   protection      protect from melee blocks 100 % of npc melee damage and 40 % of player melee damage (OSRS Wiki "Protect from Melee")
 *   retaliation     a hit player with auto retaliate strikes back after ceil(speed / 2) ticks (OSRS Wiki "Auto Retaliate")
 *   poison          dragon dagger(p++) poison: first damage 6, every 30 ticks (OSRS Wiki "Poison")
 */
object CombatSuite : Conformance.Suite {
    private const val DUMMY_NPC = 1
    private const val DUMMY_LIFE = 1_000_000

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val arena = SummoningSuite.arenaFor(run, run.origin.transform(3, 0))
        run.note("arena ${arena.x},${arena.z},${arena.height}")
        val a = run.bot(arena, "C1")
        val b = run.bot(arena.transform(1, 0), "C2")
        val dummy = run.npc(DUMMY_NPC, arena.transform(0, 3), "CD")
        dummy.attr[gg.rsmod.plugins.content.cmd.Sweeps.SWEEP_DUMMY_ATTR] = true
        wait(2)
        val only = args.joinToString(" ").lowercase()
        suspend fun scenario(
            name: String,
            block: suspend QueueTask.() -> Unit,
        ) {
            if (Conformance.stopRequested || (only.isNotEmpty() && !name.contains(only))) return
            val from = run.world.currentCycle
            run.progress("combat: $name")
            try {
                block()
            } catch (e: Throwable) {
                run.add(Result("combat $name", "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
            }
            for (p in listOf(a, b)) reset(p, if (p === a) arena else arena.transform(1, 0))
            dummy.resetInteractions()
            Combat.reset(dummy)
            dummy.moveTo(arena.transform(0, 3))
            wait(2)
            run.window("combat $name", from)
        }
        scenario("eat delay") { eatDelay(run, a, dummy, arena) }
        scenario("special regen") { specialRegen(run, a) }
        scenario("freezes") { freezes(run, a, b, arena) }
        scenario("protection") { protection(run, a, b, dummy, arena) }
        scenario("retaliation") { retaliation(run, a, dummy, arena) }
        scenario("poison") { poison(run, a, dummy, arena) }
        listOf(
            "PID order and same-tick hits between two players",
            "hit delay per style and distance for every spell",
            "stun, teleblock durations",
            "venom damage pattern and immunity",
            "vengeance, recoil, deflect and retribution ordering in one tick",
            "1-tick prayer flicking",
            "combo eat with a potion (sip) and karambwan",
            "death: items kept/lost, loot key, gravestone, skull timing",
            "multi-way vs single-way attack rules, PJ timer",
            "wilderness combat level brackets",
        ).forEach { run.notTested("combat", it, "no scenario yet") }
    }

    // ---- scenarios ----------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.eatDelay(
        run: Conformance.Run,
        a: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat eat delay"
        a.equipment[3] = Item(Items.ABYSSAL_WHIP)
        a.addBlock(UpdateBlockType.APPEARANCE)
        a.inventory.add(Items.SHARK, 1)
        a.inventory.add(Items.COOKED_KARAMBWAN, 1)
        a.inventory.add(Items.SHARK, 1)
        dummy.moveTo(arena.transform(0, 1))
        wait(1)
        val c = run.world.currentCycle
        a.attack(dummy)
        // Two swings, then eat one tick after the second.
        var swings = swings(run, "C1", c)
        var guard = 0
        while (swings.size < 2 && guard++ < 30) {
            keep(a, dummy)
            wait(1)
            swings = swings(run, "C1", c)
        }
        if (swings.size < 2) {
            run.add(Result(e, "setup: two whip swings", Verdict.FAIL, "2 swings", "${swings.size}"))
            return
        }
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 400)
        val ate = run.world.currentCycle
        Bots.itemOption(a, a.inventory.getItemIndex(Items.SHARK, false), "Eat")
        guard = 0
        while (swings(run, "C1", ate).isEmpty() && guard++ < 20) {
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            wait(1)
        }
        val next = swings(run, "C1", ate).firstOrNull()
        val gap = next?.minus(swings[1])
        run.check(e, "swing interval with a shark eaten in between (whip 4)", gap, "osrs wiki (food +3 ticks)" to 7)
        // Combo: shark then karambwan in the same tick both eat.
        a.resetInteractions()
        Combat.reset(a)
        wait(4)
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 500)
        val hp0 = a.getCurrentLifepoints()
        Bots.itemOption(a, a.inventory.getItemIndex(Items.SHARK, false), "Eat")
        Bots.itemOption(a, a.inventory.getItemIndex(Items.COOKED_KARAMBWAN, false), "Eat")
        wait(2)
        run.expectTrue(e, "shark + karambwan in the same tick both eaten", a.inventory.getItemCount(Items.SHARK) == 0 && a.inventory.getItemCount(Items.COOKED_KARAMBWAN) == 0, "shark=${a.inventory.getItemCount(Items.SHARK)} karambwan=${a.inventory.getItemCount(Items.COOKED_KARAMBWAN)} healed ${a.getCurrentLifepoints() - hp0}", "both gone (OSRS combo eating)")
    }

    private suspend fun QueueTask.specialRegen(
        run: Conformance.Run,
        a: Client,
    ) {
        val e = "combat special regen"
        AttackTab.setEnergy(a, 0)
        wait(ticks(run, 30_600))
        run.check(e, "special energy 30.6 s after 0", AttackTab.getEnergy(a), "osrs wiki" to 10)
        AttackTab.setEnergy(a, 95)
        wait(ticks(run, 30_600))
        run.check(e, "special energy capped at 100", AttackTab.getEnergy(a), "osrs wiki" to 100)
    }

    private suspend fun QueueTask.freezes(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat freezes"
        a.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        val cases =
            listOf(
                "BIND" to 5_000, "SNARE" to 10_000, "ENTANGLE" to 15_000,
                "ICE_RUSH" to 5_000, "ICE_BURST" to 10_000, "ICE_BLITZ" to 15_000, "ICE_BARRAGE" to 20_000,
            )
        for ((name, ms) in cases) {
            val spell = CombatSpell.values.firstOrNull { it.name == name }
            if (spell == null) {
                run.add(Result(e, "$name exists", Verdict.FAIL, "a combat spell", "missing"))
                continue
            }
            reset(a, arena)
            reset(b, arena.transform(3, 0))
            b.timers.remove(gg.rsmod.game.model.timer.FROZEN_TIMER)
            a.setSpellbook(if (spell.interfaceId == 193) gg.rsmod.plugins.api.Spellbook.ANCIENT else gg.rsmod.plugins.api.Spellbook.STANDARD)
            wait(1)
            val c = run.world.currentCycle
            a.attack(b)
            a.attr[Combat.CASTING_SPELL] = spell
            // Wait for the freeze to land, then the target tries to walk every tick.
            var frozenAt = -1
            var t = 0
            while (t++ < 12) {
                keep(a, b)
                wait(1)
                if (b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) {
                    frozenAt = run.world.currentCycle
                    break
                }
            }
            a.resetInteractions()
            Combat.reset(a)
            if (frozenAt < 0) {
                run.add(Result(e, "$name freezes a player", Verdict.FAIL, "frozen", "not frozen in 12 ticks (${lastMessages(run, "C1", c)})"))
                continue
            }
            val start = b.tile
            var moved = -1
            t = 0
            val limit = ticks(run, ms.toLong()) + 12
            while (t++ < limit) {
                Bots.walk(b, start.transform(0, 6))
                wait(1)
                if (!b.tile.sameAs(start)) {
                    moved = run.world.currentCycle - frozenAt
                    break
                }
            }
            run.check(e, "$name: ticks until the player can walk", moved, "osrs wiki" to ticks(run, ms.toLong()), same = { x, y -> x != null && y != null && Math.abs(x.toString().toInt() - y.toString().toInt()) <= 2 })
            // Immunity: recast at once - no freeze for 3 s.
            b.moveTo(start)
            b.resetInteractions()
            a.attack(b)
            a.attr[Combat.CASTING_SPELL] = spell
            var refrozen = false
            t = 0
            while (t++ < ticks(run, 2_400)) {
                keep(a, b)
                wait(1)
                if (b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) refrozen = true
            }
            a.resetInteractions()
            Combat.reset(a)
            run.expectTrue(e, "$name: immune for 3 s after the freeze ends", !refrozen, "frozen again=$refrozen", "OSRS: 5 ticks of immunity")
        }
    }

    private suspend fun QueueTask.protection(
        run: Conformance.Run,
        a: Client,
        b: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat protection"
        // PvM: the dummy's melee against Protect from Melee lands nothing.
        dummy.moveTo(arena.transform(0, 1))
        Prayers.activate(a, Prayer.PROTECT_FROM_MELEE)
        val c = run.world.currentCycle
        dummy.attack(a)
        repeat(60) {
            keep(a, dummy)
            a.setCurrentPrayerPoints(99)
            wait(1)
        }
        dummy.resetInteractions()
        Combat.reset(dummy)
        val pvm = splats(run, "C1", c)
        run.expectTrue(e, "npc melee vs Protect from Melee lands nothing (${pvm.size} attacks)", pvm.isNotEmpty() && pvm.all { it == 0 }, "damages=${pvm.filter { it > 0 }.take(10)}", "all 0 (osrs wiki)")
        Prayers.deactivateAll(a)
        dummy.moveTo(arena.transform(0, 3))
        // PvP: mean damage with and without protection -> 0.6.
        b.equipment[3] = Item(Items.ABYSSAL_WHIP)
        b.addBlock(UpdateBlockType.APPEARANCE)
        val means = ArrayList<Double>()
        for (protect in listOf(false, true)) {
            reset(a, arena)
            b.moveTo(arena.transform(1, 0))
            if (protect) Prayers.activate(a, Prayer.PROTECT_FROM_MELEE)
            val c2 = run.world.currentCycle
            b.attack(a)
            var t = 0
            while (t++ < 400 && splats(run, "C1", c2).size < 80) {
                keep(a, b)
                a.setCurrentPrayerPoints(99)
                wait(1)
                if (b.getCombatTarget() == null) b.attack(a)
            }
            b.resetInteractions()
            Combat.reset(b)
            val d = splats(run, "C1", c2)
            means += if (d.isEmpty()) 0.0 else d.average()
            Prayers.deactivateAll(a)
        }
        val ratio = if (means[0] > 0) means[1] / means[0] else Double.NaN
        run.check(e, "PvP melee damage with / without Protect from Melee", "%.2f".format(java.util.Locale.ENGLISH, ratio), "osrs wiki" to "0.60", same = { x, y -> x != null && y != null && x.toString().toDoubleOrNull()?.let { Math.abs(it - y.toString().toDouble()) <= 0.15 } == true })
    }

    private suspend fun QueueTask.retaliation(
        run: Conformance.Run,
        a: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat retaliation"
        a.equipment[3] = Item(Items.ABYSSAL_WHIP)
        a.addBlock(UpdateBlockType.APPEARANCE)
        a.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
        dummy.moveTo(arena.transform(0, 1))
        wait(2)
        val c = run.world.currentCycle
        dummy.attack(a)
        var t = 0
        while (t++ < 20 && swings(run, "C1", c).isEmpty()) {
            keep(a, dummy)
            wait(1)
        }
        val firstHit = run.recording.of("C1", "out").firstOrNull { it.cycle >= c && it.text.startsWith("hits ") }?.cycle
        val swing = swings(run, "C1", c).firstOrNull()
        run.check(e, "ticks from the first hit taken to the retaliation swing (whip 4)", if (firstHit != null && swing != null) swing - firstHit else null, "osrs wiki (ceil(speed/2))" to 2)
        dummy.resetInteractions()
        Combat.reset(dummy)
    }

    private suspend fun QueueTask.poison(
        run: Conformance.Run,
        a: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat poison"
        a.equipment[3] = Item(Items.DRAGON_DAGGER_P_5698)
        a.addBlock(UpdateBlockType.APPEARANCE)
        dummy.moveTo(arena.transform(0, 1))
        wait(1)
        val c = run.world.currentCycle
        a.attack(dummy)
        var poisonedAt = -1
        var t = 0
        while (t++ < 200) {
            keep(a, dummy)
            wait(1)
            if (run.recording.of("CD", "out").any { it.cycle >= c && it.text.startsWith("hits ") && it.text.contains("type=${HitType.POISON.id} ") }) {
                poisonedAt = run.world.currentCycle
                break
            }
        }
        a.resetInteractions()
        Combat.reset(a)
        if (poisonedAt < 0) {
            run.add(Result(e, "dragon dagger(p++) poisons the target", Verdict.FAIL, "a poison hitsplat within 200 ticks", "none"))
            return
        }
        repeat(70) {
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            wait(1)
        }
        val poison =
            run.recording.of("CD", "out").filter { it.cycle >= c && it.text.startsWith("hits ") }
                .flatMap { ev -> Regex("type=(\\d+) dmg=(\\d+)").findAll(ev.text).map { m -> Triple(ev.cycle, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.toList() }
                .filter { it.second == HitType.POISON.id }
        val scale = if (a.getMaximumLifepoints() > 200) 10 else 1
        run.check(e, "first poison damage (x$scale)", poison.firstOrNull()?.third, "osrs wiki (p++ starts at 6)" to 6 * scale)
        if (poison.size >= 2) run.check(e, "ticks between poison hits", poison[1].first - poison[0].first, "osrs wiki" to ticks(run, 18_000))
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------

    private fun swings(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "out").filter { it.cycle >= from && it.text.startsWith("anim ") }.map { it.cycle }.distinct()

    private fun splats(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "out").filter { it.cycle >= from && it.text.startsWith("hits ") }
        .flatMap { ev -> Regex("type=(\\d+) dmg=(\\d+)").findAll(ev.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
        .filter { it.first != HitType.REFLECTED.id && it.first != HitType.POISON.id }
        .map { it.second }

    private fun keep(
        vararg p: Pawn,
    ) {
        p.forEach { if (it is Npc) it.setCurrentLifepoints(DUMMY_LIFE) else it.setCurrentLifepoints(it.getMaximumLifepoints()) }
    }

    private fun reset(
        p: Client,
        tile: Tile,
    ) {
        p.resetInteractions()
        Combat.reset(p)
        Prayers.deactivateAll(p)
        p.timers.remove(gg.rsmod.game.model.timer.FROZEN_TIMER)
        p.equipment.removeAll()
        p.inventory.removeAll()
        p.moveTo(tile)
        p.setCurrentLifepoints(p.getMaximumLifepoints())
        p.skills.setBaseLevel(Skills.PRAYER, 99)
        p.setCurrentPrayerPoints(99)
        p.addBlock(UpdateBlockType.APPEARANCE)
    }

    private fun ticks(
        run: Conformance.Run,
        ms: Long,
    ): Int = (ms / run.world.gameContext.cycleTime).toInt()

    private fun lastMessages(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }.takeLast(2).joinToString(" / ") { it.text.take(80) }
}
