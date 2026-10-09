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
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.api.ext.stun
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.dealHit
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
 *
 * PvP tick rules (2026-10-03, owner "PvP perfectioneren"; each scenario's KDoc quotes its OSRS Wiki source):
 *   prayer flicking 1-tick flicks on the real prayer-book button: no drain, overhead every tick, npc melee fully blocked
 *   pid             same-tick swings: hitsplat tick per PID, who is skulled, PID re-randomised every 100-150 ticks
 *   combo eat       shark > brew > karambwan in one tick (all eaten, 54 healed), karambwan-first and double karambwan refused,
 *                   karambwan +2 attack delay
 *   hit delay       every non-low-tier combat spell (waves, surges, god spells, all Ancients) at distance 1/2/5/8:
 *                   1 + floor((1 + d) / 3), relative to the same pair's melee offset
 *   stun            Zamorakian spear Shove: 25 %, push back, 5-tick stun, damage stalled until it ends, 1 tick immunity
 *   damage responses Vengeance + ring of recoil on one hit (values, same tick, "Taste vengeance!", used up); Retribution on death
 *   venom           PvP venom from the toxic staff of the dead (6, 8, 10 every 30 ticks); Serpentine helm and Anti-venom+ immunity
 * Teleblock durations, death items, loot keys, skull timing, single/multi-way, PJ timer and level brackets: conform deadman.
 */
object CombatSuite : Conformance.Suite {
    private const val DUMMY_NPC = 1
    private const val DUMMY_LIFE = 1_000_000

    /** Prayer points varp (Prayers.PRAYER_POINTS_VARP, private there). */
    private const val PRAYER_POINTS_VARP = 2382

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
            // Hits still in flight land after a reset and start auto retaliation again (the dummy kept fighting C1 into the next
            // scenario and a manual cast was dropped for the wrong target): clear them, let a tick pass, reset once more.
            repeat(2) {
                for (p in listOf<gg.rsmod.game.model.entity.Pawn>(a, b, dummy)) {
                    p.clearHits()
                    p.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
                }
                for (p in listOf(a, b)) reset(p, if (p === a) arena else arena.transform(1, 0))
                dummy.resetInteractions()
                Combat.reset(dummy)
                dummy.moveTo(arena.transform(0, 3))
                wait(2)
            }
            b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
            run.window("combat $name", from)
        }
        scenario("eat delay") { eatDelay(run, a, dummy, arena) }
        scenario("special regen") { specialRegen(run, a) }
        scenario("freezes") { freezes(run, a, b, arena) }
        scenario("freeze range and seeds") { freezeRangeAndSeeds(run, a, b, arena) }
        scenario("miasmic") { miasmic(run, a, b, dummy, arena) }
        scenario("smoke poison") { smokePoison(run, a, dummy, arena) }
        scenario("packet validation") { packetValidation(run, a) }
        scenario("manual cast with a staff") { manualCastWithStaff(run, a, b, arena) }
        scenario("protection") { protection(run, a, b, dummy, arena) }
        scenario("retaliation") { retaliation(run, a, dummy, arena) }
        scenario("poison") { poison(run, a, dummy, arena) }
        scenario("prayer flicking") { prayerFlicking(run, a, b, dummy, arena) }
        scenario("pid") { pidOrder(run, a, b, arena) }
        scenario("combo eat") { comboEat(run, a, dummy, arena) }
        scenario("hit delay") { hitDelay(run, a, b, arena) }
        scenario("stun") { shoveStun(run, a, b, arena) }
        scenario("damage responses") { damageResponses(run, a, b, arena) }
        scenario("venom") { venom(run, a, b, arena) }
        // Covered by the deadman domain (DeadmanSuite.kt), not repeated here:
        run.note("death items kept/lost, loot key, skull timing: conform deadman - scenarios death, keys, dupe, skull")
        run.note("multi-way vs single-way attack rules, PJ timer: conform deadman - scenarios single, aoe, familiar")
        run.note("wilderness / Deadman combat level brackets: conform deadman - scenario zones")
        run.note("teleblock durations: conform deadman - scenario teleblock")
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
        val whipAnim = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(a)
        var swings = swings(run, "C1", c, whipAnim)
        var guard = 0
        while (swings.size < 2 && guard++ < 30) {
            keep(a, dummy)
            wait(1)
            swings = swings(run, "C1", c, whipAnim)
        }
        if (swings.size < 2) {
            run.add(Result(e, "setup: two whip swings", Verdict.FAIL, "2 swings", "${swings.size}"))
            return
        }
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 40) // 1:1 lifepoints (was a x10 figure)
        val ate = run.world.currentCycle
        Bots.itemOption(a, a.inventory.getItemIndex(Items.SHARK, false), "Eat")
        guard = 0
        while (swings(run, "C1", ate, whipAnim).isEmpty() && guard++ < 20) {
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            wait(1)
        }
        val next = swings(run, "C1", ate, whipAnim).firstOrNull()
        val gap = next?.minus(swings[1])
        run.check(e, "swing interval with a shark eaten in between (whip 4)", gap, "osrs wiki (food +3 ticks)" to 7)
        // Combo: shark then karambwan in the same tick both eat.
        a.resetInteractions()
        Combat.reset(a)
        wait(4)
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 50)
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
        // The target only takes the casts: with auto retaliate it started a fist fight with the caster (hits in flight re-trigger
        // it after every reset) and the caster's manual cast was never fired.
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
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
            // Wait for the freeze to land, then the target tries to walk every tick. A splash (a missed accuracy roll, "The spell has
            // no effect.") is normal: cast again, up to six casts.
            var frozenAt = -1
            var t = 0
            var casts = 0
            while (frozenAt < 0 && casts++ < 6) {
                manualCast(a, b, spell)
                a.attack(b)
                t = 0
                while (t++ < 12) {
                    keep(a, b)
                    wait(1)
                    if (b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) {
                        frozenAt = run.world.currentCycle
                        break
                    }
                }
                // The splash makes the target retaliate (auto retaliate); stop both so the next cast starts clean.
                for (p in listOf(a, b)) {
                    p.resetInteractions()
                    Combat.reset(p)
                }
                if (frozenAt < 0) wait(6)
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
            // Immunity (OSRS Wiki "Freeze": 5 ticks after the freeze ends): measured as the ticks the immunity outlives the freeze,
            // and a freeze attempt inside the window must fail. (The old recast check depended on each spell's hit delay.)
            val immuneAtThaw = b.timers.has(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER)
            val refused = !b.freeze(8, a) {}
            var immuneTicks = 0
            while (b.timers.has(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER) && immuneTicks < 40) {
                wait(1)
                immuneTicks++
            }
            // The walk is seen one tick after the thaw, so the window left here is one tick shorter than the full 5.
            run.expectTrue(e, "$name: immune for 5 ticks after the freeze ends", immuneAtThaw && refused && immuneTicks in 3..5, "immune=$immuneAtThaw refused=$refused left=$immuneTicks", "OSRS: 5 ticks of immunity")
        }
    }

    /**
     * Combat run 2026-09-29. OSRS Wiki "Freeze": a player's freeze on a player only holds "whilst both players remain within the
     * 10-tile maximum player weapon range" (FreezeLeash). Mithril seeds move a frozen player one tile; seeds clicked in one tick
     * move him only once (one plant per tick) and the freeze stays.
     */
    private suspend fun QueueTask.freezeRangeAndSeeds(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        // The target only takes the casts: with auto retaliate it started a fist fight with the caster (hits in flight re-trigger
        // it after every reset) and the caster's manual cast was never fired.
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        val e = "combat freeze range"
        a.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        a.setSpellbook(gg.rsmod.plugins.api.Spellbook.ANCIENT)
        reset(a, arena)
        reset(b, arena.transform(3, 0))
        b.timers.remove(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER)
        wait(1)
        val c = run.world.currentCycle
        var casts = 0
        while (!b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER) && casts++ < 6) {
            manualCast(a, b, CombatSpell.ICE_BARRAGE)
            a.attack(b)
            var t = 0
            while (t++ < 12 && !b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) {
                keep(a, b)
                wait(1)
            }
            for (p in listOf(a, b)) {
                p.resetInteractions()
                Combat.reset(p)
            }
            if (!b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) wait(6)
        }
        a.resetInteractions()
        Combat.reset(a)
        if (!b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)) {
            run.add(Result(e, "setup: Ice Barrage freezes the player", Verdict.FAIL, "frozen", "not frozen (${lastMessages(run, "C1", c)})"))
            return
        }
        val frozenTile = b.tile
        a.moveTo(frozenTile.transform(-10, 0))
        wait(3)
        run.expectTrue(e, "caster 10 tiles away: still frozen", b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER), "frozen=${b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)}", "OSRS: frozen within 10 tiles")
        a.moveTo(frozenTile.transform(-11, 0))
        wait(2)
        val broke = !b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)
        run.expectTrue(e, "caster 11 tiles away: freeze ends", broke, "frozen=${!broke}", "OSRS Wiki Freeze: 10-tile range")
        Bots.walk(b, frozenTile.transform(0, 4))
        wait(2)
        run.expectTrue(e, "the player walks after the freeze broke", !b.tile.sameAs(frozenTile), "tile=${b.tile}", "moved")

        val s = "combat mithril seeds"
        reset(b, arena.transform(3, 0))
        a.moveTo(arena)
        b.timers.remove(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER)
        b.inventory.add(Items.MITHRIL_SEEDS, 3)
        wait(1)
        b.freeze(32, a) {}
        val seedTile = b.tile
        repeat(3) { Bots.itemOption(b, b.inventory.getItemIndex(Items.MITHRIL_SEEDS, false), "Plant") }
        wait(2)
        val dist = Math.max(Math.abs(b.tile.x - seedTile.x), Math.abs(b.tile.z - seedTile.z))
        run.check(s, "tiles moved by three seeds clicked in one tick while frozen", dist, "one plant per tick" to 1)
        run.check(s, "seeds used", 3 - b.inventory.getItemCount(Items.MITHRIL_SEEDS), "one plant per tick" to 1)
        run.expectTrue(s, "still frozen after the seed move", b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER), "frozen=${b.timers.has(gg.rsmod.game.model.timer.FROZEN_TIMER)}", "OSRS Wiki Mithril seeds: moves a frozen player, the freeze stays")
        b.timers.remove(gg.rsmod.game.model.timer.FROZEN_TIMER)
        b.inventory.removeAll()
    }
    /**
     * Combat run 2026-09-29: a Miasmic spell doubles the target's melee and ranged attack delay (Void donor Combat.kt); casting is not
     * slowed (unit test AttackDelayTests). Measured: whip swing interval of the slowed player on the dummy.
     */
    private suspend fun QueueTask.miasmic(
        run: Conformance.Run,
        a: Client,
        b: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        // The target only takes the casts: with auto retaliate it started a fist fight with the caster (hits in flight re-trigger
        // it after every reset) and the caster's manual cast was never fired.
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        val e = "combat miasmic"
        a.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        a.setSpellbook(gg.rsmod.plugins.api.Spellbook.ANCIENT)
        reset(a, arena)
        reset(b, arena.transform(3, 0))
        a.equipment[3] = Item(Items.ZURIELS_STAFF)
        a.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        val slowed = { b.timers.has(gg.rsmod.game.model.timer.MIASMIC_TIMER) }
        val c0 = run.world.currentCycle
        var casts = 0
        while (!slowed() && casts++ < 6) {
            manualCast(a, b, CombatSpell.MIASMIC_RUSH)
            a.attack(b)
            var t = 0
            while (t++ < 12 && !slowed()) {
                keep(a, b)
                wait(1)
            }
            for (p in listOf(a, b)) {
                p.resetInteractions()
                Combat.reset(p)
            }
            if (!slowed()) wait(6)
        }
        if (!slowed()) {
            run.add(Result(e, "setup: Miasmic Rush slows the player", Verdict.FAIL, "slowed", "not slowed (${lastMessages(run, "C1", c0)})"))
            return
        }
        b.equipment[3] = Item(Items.ABYSSAL_WHIP)
        b.addBlock(UpdateBlockType.APPEARANCE)
        dummy.moveTo(b.tile.transform(0, 1))
        wait(1)
        val c = run.world.currentCycle
        b.attack(dummy)
        val whipAnim = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(b)
        var swings = swings(run, "C2", c, whipAnim)
        var guard = 0
        while (swings.size < 2 && guard++ < 30) {
            keep(b, dummy)
            wait(1)
            swings = swings(run, "C2", c, whipAnim)
        }
        b.resetInteractions()
        Combat.reset(b)
        val gap = if (swings.size >= 2) swings[1] - swings[0] else null
        run.check(e, "whip swing interval while slowed by Miasmic Rush (whip 4)", gap, "void donor (melee/ranged delay x2)" to 8)
        b.timers.remove(gg.rsmod.game.model.timer.MIASMIC_TIMER)
        b.timers.remove(gg.rsmod.game.model.timer.MIASMIC_IMMUNITY_TIMER)
    }
    /**
     * Combat run 2026-09-29. OSRS Wiki "Smoke Rush": "capable of poisoning a target at a 1/8 chance on hit". Casts Smoke Rush on the
     * dummy and counts the damaging hits that poisoned it (cured after each cast): 1/8, with a wide statistical window.
     */
    private suspend fun QueueTask.smokePoison(
        run: Conformance.Run,
        a: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat smoke poison"
        a.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        a.setSpellbook(gg.rsmod.plugins.api.Spellbook.ANCIENT)
        a.skills.setBaseLevel(Skills.MAGIC, 99)
        // Only the casts may hit the dummy: no punches from auto retaliation.
        a.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        dummy.moveTo(arena.transform(0, 3))
        var damaging = 0
        var poisoned = 0
        repeat(64) {
            gg.rsmod.plugins.content.mechanics.poison.Poison.cure(dummy)
            val c = run.world.currentCycle
            a.attr[Combat.CASTING_SPELL] = CombatSpell.SMOKE_RUSH
            a.attr[gg.rsmod.plugins.content.combat.magic.Autocast.MANUAL_TARGET] = java.lang.ref.WeakReference(dummy)
            a.attack(dummy)
            wait(6)
            a.resetInteractions()
            Combat.reset(a)
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            val hit = splats(run, "CD", c).any { it > 0 }
            if (hit) damaging++
            if (hit && gg.rsmod.plugins.content.mechanics.poison.Poison.isPoisoned(dummy)) poisoned++
        }
        gg.rsmod.plugins.content.mechanics.poison.Poison.cure(dummy)
        a.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
        run.expectTrue(e, "Smoke Rush poisons about 1 in 8 damaging hits ($damaging damaging casts)", damaging >= 30 && poisoned in 1..(damaging / 3),
            "poisoned $poisoned of $damaging", "OSRS Wiki: 1/8 on hit")
    }
    /**
     * Combat run 2026-09-29: the server never trusts a client item id. Live 02:22 (anudd): an inventory "Drop" packet with item -1
     * threw in IfButton1Handler (DefinitionSet.get). The same packet is injected here: it must be ignored, the bot stays online.
     */
    private suspend fun QueueTask.packetValidation(
        run: Conformance.Run,
        a: Client,
    ) {
        val e = "combat packet validation"
        val c = run.world.currentCycle
        Bots.button(a, 679, 0, 5, 23, -1)
        Bots.button(a, 679, 0, 5, 0, 999_999)
        wait(2)
        val inv = a.inventory.getItemCount(Items.SHARK)
        run.expectTrue(e, "drop packets with an unknown item id are ignored", a.isOnline && run.world.currentCycle > c, "online=${a.isOnline} sharks=$inv", "no crash, player online")
    }
    /**
     * Combat run 2026-09-29: a spell clicked on a player is a single cast, also with a staff in hand (OSRS: the caster stops, no
     * staff bash follows). Seen in the curses suite: after one manual Fire Bolt with a Staff of fire the caster kept hitting with
     * the staff. Measures the caster's animations for 10 ticks after the cast.
     */
    private suspend fun QueueTask.manualCastWithStaff(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat manual cast with a staff"
        reset(a, arena)
        reset(b, arena.transform(3, 0))
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        a.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        a.setSpellbook(gg.rsmod.plugins.api.Spellbook.STANDARD)
        a.equipment[3] = Item(Items.STAFF_OF_FIRE)
        a.addBlock(UpdateBlockType.APPEARANCE)
        a.attr[gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE] = a.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, Items.STAFF_OF_FIRE).weaponType
        wait(1)
        val c = run.world.currentCycle
        manualCast(a, b, CombatSpell.FIRE_BOLT)
        a.attack(b)
        var t = 0
        while (t++ < 14) {
            keep(a, b)
            wait(1)
        }
        val anims = run.recording.of("C1", "out").filter { it.cycle >= c && it.text.startsWith("anim ") }.map { it.text }
        val casts = anims.count { it.contains("id=${CombatSpell.FIRE_BOLT.castAnimation[1]} ") || it.contains("id=${CombatSpell.FIRE_BOLT.castAnimation[0]} ") }
        run.expectTrue(e, "one cast, then the caster stops (no staff swing)", casts == 1 && anims.size == 1, "anims=$anims", "exactly one cast animation")
        a.resetInteractions()
        Combat.reset(a)
    }
    private suspend fun QueueTask.protection(
        run: Conformance.Run,
        a: Client,
        b: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat protection"
        // PvM: the dummy's melee against Protect from Melee lands nothing. C2 stands well away: its auto retaliate from an earlier
        // scenario punched C1 here (PvP melee through protection), which read as npc damage.
        b.resetInteractions()
        Combat.reset(b)
        b.moveTo(arena.transform(12, 0))
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
            // 300 hits a side: with 80 the mean ratio swung between 0.46 and 0.85 run to run (combat run 2026-09-29).
            while (t++ < 1600 && splats(run, "C1", c2).size < 300) {
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
        // Only the whip swing counts: the block animation on the hit is not the retaliation.
        val whipAnim = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(a)
        var t = 0
        while (t++ < 20 && swings(run, "C1", c, whipAnim).isEmpty()) {
            keep(a, dummy)
            wait(1)
        }
        val firstHit = run.recording.of("C1", "out").firstOrNull { it.cycle >= c && it.text.startsWith("hits ") }?.cycle
        val swing = swings(run, "C1", c, whipAnim).firstOrNull()
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

    // ---- PvP tick rules (2026-10-03, owner "PvP perfectioneren") -------------------------------------------------------------

    /**
     * OSRS Wiki "Prayer flicking": "The game does not drain prayer for prayers on the tick they are activated"; 1-tick flicking (off and
     * on again inside one tick) keeps the overhead "visible at all times" and drains nothing; prayers active at the start of a tick
     * protect. Real clicks on the prayer book (271:8), two per tick, while the dummy hits with melee.
     */
    private suspend fun QueueTask.prayerFlicking(
        run: Conformance.Run,
        a: Client,
        b: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat prayer flicking"
        b.moveTo(arena.transform(12, 0))
        reset(a, arena)
        val slot = Prayer.PROTECT_FROM_MELEE.slot
        Bots.button(a, 271, 8, 1, slot)
        wait(2)
        if (!Prayers.isActive(a, Prayer.PROTECT_FROM_MELEE)) {
            run.add(Result(e, "setup: one click turns Protect from Melee on", Verdict.FAIL, "active", "inactive"))
            return
        }
        dummy.moveTo(arena.transform(0, 1))
        wait(1)
        val points0 = a.getVarp(PRAYER_POINTS_VARP)
        val c = run.world.currentCycle
        dummy.attack(a)
        var ticksWithoutOverhead = 0
        repeat(60) {
            keep(a, dummy)
            Bots.button(a, 271, 8, 1, slot) // off
            Bots.button(a, 271, 8, 1, slot) // on again, same tick
            wait(1)
            if (a.prayerIcon != gg.rsmod.plugins.api.PrayerIcon.PROTECT_FROM_MELEE.id) ticksWithoutOverhead++
        }
        dummy.resetInteractions()
        Combat.reset(dummy)
        val drained = points0 - a.getVarp(PRAYER_POINTS_VARP)
        run.check(e, "prayer points drained by 60 ticks of 1-tick flicking Protect from Melee", drained, "osrs wiki Prayer flicking (no drain on the activation tick)" to 0)
        run.check(e, "ticks without the Protect from Melee overhead while flicking", ticksWithoutOverhead, "osrs wiki Prayer flicking (overhead visible at all times)" to 0)
        val pvm = splats(run, "C1", c)
        run.expectTrue(e, "npc melee while flicking lands nothing (${pvm.size} attacks)", pvm.isNotEmpty() && pvm.all { it == 0 }, "damages=${pvm.filter { it > 0 }.take(10)}", "all 0 (osrs wiki: prayers active at the start of the tick protect)")
        Prayers.deactivateAll(a)
    }

    /**
     * OSRS Wiki "Player identification number": players are processed in PID order (lower first); PID "determines which player gets
     * skulled or hits first when both players simultaneously attack each other"; it is re-randomised every 100-150 ticks. OSRS Wiki
     * "Hit delay": melee hits have delay 0; "in the case of PvP, PID will be the determining factor whether or not processing order
     * delay will delay attacks by one tick". The engine's PID is the player index (players are cycled in index order).
     */
    private suspend fun QueueTask.pidOrder(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat pid"
        reset(a, arena)
        reset(b, arena.transform(1, 0))
        for (p in listOf(a, b)) {
            p.equipment[3] = Item(Items.ABYSSAL_WHIP)
            p.attr[gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE] = p.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, Items.ABYSSAL_WHIP).weaponType
            p.addBlock(UpdateBlockType.APPEARANCE)
            clearSkull(p)
        }
        wait(2)
        val whipAnim = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(a)
        val aFirst = pidRank(run, a) < pidRank(run, b)
        val c = run.world.currentCycle
        a.attack(b)
        b.attack(a)
        var t = 0
        while (t++ < 12 && (hitEvents(run, "C1", c).isEmpty() || hitEvents(run, "C2", c).isEmpty())) {
            keep(a, b)
            wait(1)
        }
        for (p in listOf(a, b)) {
            p.resetInteractions()
            Combat.reset(p)
        }
        val swingA = swings(run, "C1", c, whipAnim).firstOrNull()
        val swingB = swings(run, "C2", c, whipAnim).firstOrNull()
        val onB = hitEvents(run, "C2", c).firstOrNull()?.first
        val onA = hitEvents(run, "C1", c).firstOrNull()?.first
        run.expectTrue(e, "both players swing on the same tick", swingA != null && swingA == swingB, "C1=$swingA C2=$swingB", "same tick")
        run.check(e, "C1 (PID ${if (aFirst) "first" else "second"}): ticks from its swing to its hitsplat on C2", if (swingA != null && onB != null) onB - swingA else null,
            "osrs wiki Hit delay (melee 0, +1 when the target was processed first)" to (if (aFirst) 0 else 1))
        run.check(e, "C2 (PID ${if (aFirst) "second" else "first"}): ticks from its swing to its hitsplat on C1", if (swingB != null && onA != null) onA - swingB else null,
            "osrs wiki Hit delay (melee 0, +1 when the target was processed first)" to (if (aFirst) 1 else 0))
        val skulls = "C1=${gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.hasSkullTimer(a)} C2=${gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.hasSkullTimer(b)}"
        run.check(e, "simultaneous first attack: who is skulled", skulls, "osrs wiki PID (the player processed first skulls)" to (if (aFirst) "C1=true C2=false" else "C1=false C2=true"))
        for (p in listOf(a, b)) clearSkull(p)
        // PID shuffle: the processing order of the two players must change within 150 ticks.
        val order0 = pidRank(run, a) < pidRank(run, b)
        var changed = -1
        for (i in 1..160) {
            wait(1)
            if ((pidRank(run, a) < pidRank(run, b)) != order0) {
                changed = i
                break
            }
        }
        run.check(e, "processing order of two players re-randomised within 160 ticks", changed in 1..160, "osrs wiki PID (randomised every 100-150 ticks)" to true)
    }

    /**
     * OSRS Wiki "Cooked karambwan": "Karambwans may be consumed after other foods without delay; this does not work if the karambwan
     * is eaten first, and does not work when eating two karambwan in a row ... (food>brew>karambwan)"; it heals 18 and "karambwans add
     * a delay of two ticks before the player can attack again". Shark heals 20 (OSRS Wiki "Shark"); Saradomin brew heals 15 % of the
     * Hitpoints level + 2 (OSRS Wiki "Saradomin brew"): 16 at 99.
     */
    private suspend fun QueueTask.comboEat(
        run: Conformance.Run,
        a: Client,
        dummy: Npc,
        arena: Tile,
    ) {
        val e = "combat combo eat"
        reset(a, arena)
        a.inventory.add(Items.SHARK, 2)
        a.inventory.add(Items.SARADOMIN_BREW_4, 1)
        a.inventory.add(Items.COOKED_KARAMBWAN, 4)
        wait(4)
        fun slot(id: Int) = a.inventory.getItemIndex(id, false)
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 70)
        val hp0 = a.getCurrentLifepoints()
        Bots.itemOption(a, slot(Items.SHARK), "Eat")
        Bots.itemOption(a, slot(Items.SARADOMIN_BREW_4), "Drink")
        Bots.itemOption(a, slot(Items.COOKED_KARAMBWAN), "Eat")
        wait(2)
        val consumed = a.inventory.getItemCount(Items.SHARK) == 1 && a.inventory.getItemCount(Items.SARADOMIN_BREW_3) == 1 && a.inventory.getItemCount(Items.COOKED_KARAMBWAN) == 3
        run.expectTrue(e, "shark, brew sip and karambwan clicked in one tick are all consumed", consumed,
            "shark=${a.inventory.getItemCount(Items.SHARK)} brew3=${a.inventory.getItemCount(Items.SARADOMIN_BREW_3)} karambwan=${a.inventory.getItemCount(Items.COOKED_KARAMBWAN)}", "osrs wiki combo eating (food > brew > karambwan)")
        run.check(e, "Hitpoints healed by shark + brew sip + karambwan in one tick", a.getCurrentLifepoints() - hp0, "osrs wiki (shark 20 + brew 16 + karambwan 18)" to 54)
        wait(4)
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 70)
        Bots.itemOption(a, slot(Items.COOKED_KARAMBWAN), "Eat")
        Bots.itemOption(a, slot(Items.SHARK), "Eat")
        wait(2)
        run.expectTrue(e, "karambwan first: the shark in the same tick is refused", a.inventory.getItemCount(Items.COOKED_KARAMBWAN) == 2 && a.inventory.getItemCount(Items.SHARK) == 1,
            "karambwan=${a.inventory.getItemCount(Items.COOKED_KARAMBWAN)} shark=${a.inventory.getItemCount(Items.SHARK)}", "osrs wiki: does not work if the karambwan is eaten first")
        wait(4)
        Bots.itemOption(a, slot(Items.COOKED_KARAMBWAN), "Eat")
        Bots.itemOption(a, slot(Items.COOKED_KARAMBWAN), "Eat")
        wait(2)
        run.check(e, "two karambwans clicked in one tick: eaten", 2 - a.inventory.getItemCount(Items.COOKED_KARAMBWAN), "osrs wiki: not two karambwan in a row" to 1)
        // Attack delay of a karambwan eaten mid-fight: whip 4 + 2.
        wait(4)
        a.equipment[3] = Item(Items.ABYSSAL_WHIP)
        a.addBlock(UpdateBlockType.APPEARANCE)
        a.inventory.add(Items.COOKED_KARAMBWAN, 1)
        dummy.moveTo(arena.transform(0, 1))
        wait(1)
        val c = run.world.currentCycle
        a.attack(dummy)
        val whipAnim = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(a)
        var swings = swings(run, "C1", c, whipAnim)
        var guard = 0
        while (swings.size < 2 && guard++ < 30) {
            keep(a, dummy)
            wait(1)
            swings = swings(run, "C1", c, whipAnim)
        }
        if (swings.size < 2) {
            run.add(Result(e, "setup: two whip swings", Verdict.FAIL, "2 swings", "${swings.size}"))
            return
        }
        a.setCurrentLifepoints(a.getMaximumLifepoints() - 40)
        val ate = run.world.currentCycle
        Bots.itemOption(a, slot(Items.COOKED_KARAMBWAN), "Eat")
        guard = 0
        while (swings(run, "C1", ate, whipAnim).isEmpty() && guard++ < 20) {
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            wait(1)
        }
        val next = swings(run, "C1", ate, whipAnim).firstOrNull()
        run.check(e, "swing interval with a karambwan eaten in between (whip 4)", next?.minus(swings[1]), "osrs wiki Cooked karambwan (+2 ticks)" to 6)
        a.resetInteractions()
        Combat.reset(a)
    }

    /**
     * OSRS Wiki "Hit delay": MagicDelay = 1 + floor((1 + Distance) / 3) for every spell (barrages too); melee has delay 0. Measured per
     * spell and distance as (hitsplat tick - cast tick) minus the same pair's melee offset, so the processing-order term (PID, see the
     * pid scenario) cancels out. Low-tier spells (strike/bolt/blast) are out of scope (AGENTS.md 1a).
     */
    private suspend fun QueueTask.hitDelay(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat hit delay"
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        reset(a, arena)
        reset(b, arena.transform(1, 0))
        a.equipment[3] = Item(Items.ABYSSAL_WHIP)
        a.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        val whipAnim = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(a)
        val c0 = run.world.currentCycle
        a.attack(b)
        var t = 0
        while (t++ < 12 && hitEvents(run, "C2", c0).isEmpty()) {
            keep(a, b)
            wait(1)
        }
        a.resetInteractions()
        Combat.reset(a)
        val swing = swings(run, "C1", c0, whipAnim).firstOrNull()
        val hit = hitEvents(run, "C2", c0).firstOrNull()?.first
        if (swing == null || hit == null) {
            run.add(Result(e, "setup: melee offset C1 -> C2", Verdict.FAIL, "a whip hit", "swing=$swing hit=$hit"))
            return
        }
        val offset = hit - swing
        run.note("hit delay: melee offset C1 -> C2 = $offset tick(s) (OSRS melee 0); spell delays are measured relative to it")
        val gods = setOf("SARADOMIN_STRIKE", "CLAWS_OF_GUTHIX", "FLAMES_OF_ZAMORAK")
        val spells = CombatSpell.values.filter {
            it.damaging && (it.interfaceId == 193 || it.name in gods || it.name.endsWith("_WAVE") || it.name.endsWith("_SURGE"))
        }
        val ignored = setOf(HitType.POISON.id, HitType.VENOM.id, HitType.REFLECTED.id, HitType.HEAL.id)
        for (spell in spells) {
            for (d in intArrayOf(1, 2, 5, 8)) {
                reset(a, arena)
                reset(b, arena.transform(d, 0))
                b.skills.setBaseLevel(Skills.DEFENCE, 1)
                b.skills.setBaseLevel(Skills.MAGIC, 1)
                a.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
                a.setSpellbook(if (spell.interfaceId == 193) gg.rsmod.plugins.api.Spellbook.ANCIENT else gg.rsmod.plugins.api.Spellbook.STANDARD)
                // Miasmic: Zuriel's staff; god spells: their god staff (AutocastWeapons.GOD_SPELL_WEAPONS).
                (spell.requiredWeapons.firstOrNull() ?: gg.rsmod.plugins.content.combat.magic.AutocastWeapons.GOD_SPELL_WEAPONS[spell]?.firstOrNull())?.let { w ->
                    a.equipment[3] = Item(w)
                    a.attr[gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE] = a.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, w).weaponType
                    a.addBlock(UpdateBlockType.APPEARANCE)
                }
                wait(1)
                var measured: Int? = null
                var tries = 0
                var c = run.world.currentCycle
                while (measured == null && tries++ < 4) {
                    c = run.world.currentCycle
                    manualCast(a, b, spell)
                    a.attack(b)
                    t = 0
                    while (t++ < 10) {
                        keep(a, b)
                        wait(1)
                        if (hitEvents(run, "C2", c).any { it.second !in ignored }) break
                    }
                    for (p in listOf(a, b)) {
                        p.resetInteractions()
                        Combat.reset(p)
                    }
                    val cast = run.recording.of("C1", "out").firstOrNull { ev -> ev.cycle >= c && spell.castAnimation.any { ev.text.startsWith("anim id=$it ") } }?.cycle
                    val landed = hitEvents(run, "C2", c).firstOrNull { it.second !in ignored }?.first
                    if (cast != null && landed != null) measured = landed - cast - offset else wait(3)
                }
                if (measured == null) {
                    run.add(Result(e, "${spell.name} at distance $d", Verdict.FAIL, "a hitsplat", "no hit in 4 casts (${lastMessages(run, "C1", c)})"))
                    continue
                }
                run.check(e, "${spell.name} at distance $d: ticks from cast to hitsplat (minus the melee offset)", measured, "osrs wiki Hit delay 1+floor((1+d)/3)" to 1 + (1 + d) / 3)
            }
        }
        b.skills.setBaseLevel(Skills.DEFENCE, 99)
        b.skills.setBaseLevel(Skills.MAGIC, 99)
    }

    /**
     * OSRS Wiki "Dragon spear" (Zamorakian spear the same): Shove costs 25 % and "stuns them for 5 ticks (3.0 seconds)"; "the brief stun
     * causes all incoming damage to be stalled until it expires, after which all damage taken is applied at once"; "Stunned players are
     * also given a one tick (0.6 seconds) period of immunity after a stun wears off during which they cannot be stunned again".
     */
    private suspend fun QueueTask.shoveStun(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat stun"
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        reset(a, arena)
        reset(b, arena.transform(1, 0))
        b.timers.remove(gg.rsmod.game.model.timer.STUN_TIMER)
        a.equipment[3] = Item(Items.ZAMORAKIAN_SPEAR)
        a.attr[gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE] = a.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, Items.ZAMORAKIAN_SPEAR).weaponType
        a.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        val start = b.tile
        AttackTab.setEnergy(a, 100)
        a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
        val c = run.world.currentCycle
        a.attack(b)
        var stunAt = -1
        var t = 0
        while (t++ < 10) {
            wait(1)
            if (b.timers.has(gg.rsmod.game.model.timer.STUN_TIMER)) {
                stunAt = run.world.currentCycle
                break
            }
        }
        a.resetInteractions()
        Combat.reset(a)
        if (stunAt < 0) {
            run.add(Result(e, "setup: Zamorakian spear Shove stuns the player", Verdict.FAIL, "stunned", "not stunned (${lastMessages(run, "C1", c)})"))
            return
        }
        run.check(e, "Shove special energy", 100 - AttackTab.getEnergy(a), "osrs wiki Dragon spear" to 25)
        val before = Math.max(Math.abs(start.x - a.tile.x), Math.abs(start.z - a.tile.z))
        val after = Math.max(Math.abs(b.tile.x - a.tile.x), Math.abs(b.tile.z - a.tile.z))
        run.expectTrue(e, "the target is pushed back", after > before, "from $start to ${b.tile} (attacker ${a.tile})", "osrs wiki: pushes the target back")
        // A hit dealt during the stun is stalled until the stun ends.
        val dealt = run.world.currentCycle
        a.dealHit(target = b, maxHit = 10.0, landHit = true, delay = 1, hitType = HitType.MELEE)
        val pushed = b.tile
        var endAt = -1
        var moved = -1
        t = 0
        while (t++ < 15) {
            Bots.walk(b, pushed.transform(0, 5))
            wait(1)
            if (endAt < 0 && !b.timers.has(gg.rsmod.game.model.timer.STUN_TIMER)) endAt = run.world.currentCycle
            if (!b.tile.sameAs(pushed)) {
                moved = run.world.currentCycle - stunAt
                break
            }
        }
        run.check(e, "Shove: ticks until the stunned player can walk", moved, "osrs wiki Dragon spear" to 5,
            same = { x, y -> x != null && y != null && x.toString().toIntOrNull()?.let { Math.abs(it - y.toString().toInt()) <= 1 } == true })
        val landed = hitEvents(run, "C2", dealt).firstOrNull()?.first
        run.expectTrue(e, "a hit dealt during the stun lands when the stun ends", landed != null && endAt > 0 && landed >= endAt - 1 && landed <= endAt + 1,
            "dealt t$dealt (delay 1) landed t$landed stun ended t$endAt", "osrs wiki: incoming damage stalled until the stun expires")
        // One tick of immunity after the stun wears off.
        b.timers.remove(gg.rsmod.game.model.timer.STUN_TIMER)
        wait(3)
        b.stun(5) {}
        t = 0
        while (t++ < 12 && b.timers.has(gg.rsmod.game.model.timer.STUN_TIMER)) wait(1)
        val refusedAtEnd = !b.stun(5) {}
        if (!refusedAtEnd) b.timers.remove(gg.rsmod.game.model.timer.STUN_TIMER)
        wait(1)
        val acceptedNext = b.stun(5) {}
        b.timers.remove(gg.rsmod.game.model.timer.STUN_TIMER)
        run.expectTrue(e, "stun immunity: refused on the tick the stun ends, allowed one tick later", refusedAtEnd && acceptedNext, "refused=$refusedAtEnd next=$acceptedNext", "osrs wiki: one tick of immunity")
        b.moveTo(arena.transform(1, 0))
    }

    /**
     * One hit on a player with Vengeance primed and a ring of recoil worn: OSRS Wiki "Vengeance" (75 %, rounded down, minimum 1, "does
     * stack with the ring of recoil", "Taste vengeance!") and "Ring of recoil" (10 % + 1, rounded down). Then a killing hit on a player
     * praying Retribution with the killer adjacent: OSRS Wiki "Retribution" (up to 25 % of the Prayer level to adjacent enemies).
     * Deflect curses: conform curses (deflect reflection share and amount).
     */
    private suspend fun QueueTask.damageResponses(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat damage responses"
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        reset(a, arena)
        reset(b, arena.transform(1, 0))
        a.equipment[3] = Item(Items.ABYSSAL_WHIP)
        a.addBlock(UpdateBlockType.APPEARANCE)
        b.equipment[12] = Item(Items.RING_OF_RECOIL)
        b.attr.remove(gg.rsmod.game.model.attr.RING_OF_RECOIL_CHARGE_ATTR)
        b.addBlock(UpdateBlockType.APPEARANCE)
        gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance.clear(b)
        gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance.prime(b)
        wait(1)
        val c = run.world.currentCycle
        a.attack(b)
        var first: Triple<Int, Int, Int>? = null
        var t = 0
        while (t++ < 60 && first == null) {
            keep(a, b)
            wait(1)
            first = hitEvents(run, "C2", c).firstOrNull { it.third > 0 && it.second != HitType.REFLECTED.id }
        }
        a.resetInteractions()
        Combat.reset(a)
        repeat(3) {
            keep(a, b)
            wait(1)
        }
        if (first == null) {
            run.add(Result(e, "setup: a damaging whip hit on the venged player", Verdict.FAIL, "a hit", "none in 60 ticks"))
            return
        }
        val d = first.third
        val reflected = hitEvents(run, "C1", first.first - 1).filter { it.second == HitType.REFLECTED.id }
        run.check(e, "reflected hits on the attacker for one $d hit (Vengeance + ring of recoil)", reflected.map { it.third }.sorted(),
            "osrs wiki (Vengeance max(1, floor(75 %)), recoil floor(10 %) + 1)" to listOf(Math.max(1, d * 3 / 4), d / 10 + 1).sorted())
        run.expectTrue(e, "Vengeance and recoil land on the same tick", reflected.size == 2 && reflected.map { it.first }.distinct().size == 1, "ticks=${reflected.map { it.first }} hit t${first.first}", "one tick")
        val said = run.recording.of("C2", "out").any { it.cycle >= first.first - 1 && it.text.startsWith("forcechat") && it.text.contains("Taste vengeance!") }
        run.expectTrue(e, "the venged player says Taste vengeance!", said, "said=$said", "osrs wiki Vengeance")
        run.expectTrue(e, "Vengeance is used up by the hit", !gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance.isActive(b),
            "active=${gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance.isActive(b)}", "single use")
        // Retribution on the killing hit.
        reset(a, arena)
        reset(b, arena.transform(1, 0))
        a.equipment[3] = Item(Items.ABYSSAL_WHIP)
        a.addBlock(UpdateBlockType.APPEARANCE)
        Prayers.activate(b, Prayer.RETRIBUTION)
        wait(1)
        b.setCurrentLifepoints(1)
        val c2 = run.world.currentCycle
        a.attack(b)
        t = 0
        var killedAt = -1
        while (t++ < 60 && killedAt < 0) {
            a.setCurrentLifepoints(a.getMaximumLifepoints())
            wait(1)
            hitEvents(run, "C2", c2).firstOrNull { it.third > 0 }?.let { killedAt = it.first }
        }
        a.resetInteractions()
        Combat.reset(a)
        repeat(4) { wait(1) }
        val blast = hitEvents(run, "C1", maxOf(c2, killedAt)).filter { it.second != HitType.REFLECTED.id }
        run.expectTrue(e, "Retribution hits the adjacent killer once for 0..24 (99 Prayer)", killedAt > 0 && blast.size == 1 && blast[0].third in 0..24,
            "killed t$killedAt hits on killer=${blast.map { "t${it.first}:${it.third}" }}", "osrs wiki Retribution (25 % of the Prayer level)")
        // Let the death finish before the next scenario.
        repeat(16) { wait(1) }
    }

    /**
     * Venom through a real PvP weapon: the charged Toxic staff of the dead's melee attack (25 %, OSRS Wiki "Toxic staff of the dead";
     * powered-staff spells such as the trident's "cannot be cast upon other players", OSRS Wiki "Powered staff"). OSRS Wiki
     * "Venom": first damage 6, +2 per hit, every 18 seconds. Immunity: a charged Serpentine helm ("immunity to venom and poison") and an
     * Anti-venom+ sip.
     */
    private suspend fun QueueTask.venom(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
    ) {
        val e = "combat venom"
        b.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        reset(a, arena)
        reset(b, arena.transform(1, 0))
        gg.rsmod.plugins.content.mechanics.poison.Venom.cure(b, immunityTicks = 0, announce = false)
        b.timers.remove(gg.rsmod.game.model.timer.VENOM_IMMUNITY)
        val staff = gg.rsmod.plugins.content.items.osrs.StaffOfTheDead
        a.equipment[3] = staff.withScales(Item(Items.TOXIC_STAFF_OF_THE_DEAD), staff.MAX_SCALES)
        a.attr[gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE] = a.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, Items.TOXIC_STAFF_OF_THE_DEAD).weaponType
        a.addBlock(UpdateBlockType.APPEARANCE)
        b.skills.setBaseLevel(Skills.DEFENCE, 1)
        b.skills.setBaseLevel(Skills.MAGIC, 1)
        wait(1)
        val skip = setOf(HitType.POISON.id, HitType.VENOM.id, HitType.REFLECTED.id)
        fun envenomed() = b.attr.has(gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR)
        fun damaging(from: Int) = hitEvents(run, "C2", from).count { it.third > 0 && it.second !in skip }
        /** Attacks until [n] damaging staff hits landed (or 300 ticks); returns the damaging hits seen and whether venom took. */
        suspend fun attackFor(n: Int): Pair<Int, Boolean> {
            val c = run.world.currentCycle
            a.attack(b)
            var t = 0
            while (t++ < 300 && damaging(c) < n && !envenomed()) {
                keep(a, b)
                wait(1)
                if (a.getCombatTarget() == null) a.attack(b)
            }
            a.resetInteractions()
            Combat.reset(a)
            return damaging(c) to envenomed()
        }
        val c0 = run.world.currentCycle
        val (hits0, took) = attackFor(60)
        if (!took) {
            run.add(Result(e, "Toxic staff of the dead envenoms a player", Verdict.FAIL, "venom within 60 damaging hits (25 %)", "$hits0 damaging hits, no venom (${lastMessages(run, "C1", c0)})"))
        } else {
            val from = run.world.currentCycle
            repeat(100) {
                keep(b)
                wait(1)
            }
            val vh = hitEvents(run, "C2", from).filter { it.second == HitType.VENOM.id }
            run.check(e, "venom damage of the first three hits (PvP, toxic staff of the dead)", vh.take(3).map { it.third }, "osrs wiki Venom (6, +2 per hit)" to listOf(6, 8, 10))
            run.check(e, "ticks between venom hits", vh.zipWithNext { x, y -> y.first - x.first }.distinct(), "osrs wiki Venom (18 s)" to listOf(ticks(run, 18_000)))
        }
        gg.rsmod.plugins.content.mechanics.poison.Venom.cure(b, immunityTicks = 0, announce = false)
        // Serpentine helm immunity.
        b.equipment[0] = gg.rsmod.plugins.content.items.osrs.SerpentineHelm.withScales(Item(Items.SERPENTINE_HELM), gg.rsmod.plugins.content.items.osrs.SerpentineHelm.MAX_SCALES)
        b.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        val (hitsHelm, tookHelm) = attackFor(20)
        run.expectTrue(e, "charged Serpentine helm: no venom from $hitsHelm damaging staff hits", hitsHelm >= 20 && !tookHelm, "hits=$hitsHelm envenomed=$tookHelm", "osrs wiki Serpentine helm (venom immunity)")
        gg.rsmod.plugins.content.mechanics.poison.Venom.cure(b, immunityTicks = 0, announce = false)
        b.equipment[0] = null
        b.addBlock(UpdateBlockType.APPEARANCE)
        // Anti-venom+ immunity.
        b.inventory.add(Items.ANTI_VENOM_PLUS_4, 1)
        Bots.itemOption(b, b.inventory.getItemIndex(Items.ANTI_VENOM_PLUS_4, false), "Drink")
        wait(2)
        val (hitsAv, tookAv) = attackFor(20)
        run.expectTrue(e, "after an Anti-venom+ sip: no venom from $hitsAv damaging staff hits", hitsAv >= 20 && !tookAv,
            "hits=$hitsAv envenomed=$tookAv sipped=${b.inventory.getItemCount(Items.ANTI_VENOM_PLUS_3)}", "osrs wiki Anti-venom+ (venom immunity)")
        gg.rsmod.plugins.content.mechanics.poison.Venom.cure(b, immunityTicks = 0, announce = false)
        b.timers.remove(gg.rsmod.game.model.timer.VENOM_IMMUNITY)
        b.timers.remove(gg.rsmod.game.model.timer.POISON_IMMUNITY)
        b.skills.setBaseLevel(Skills.DEFENCE, 99)
        b.skills.setBaseLevel(Skills.MAGIC, 99)
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------

    /** Cycles of [who]'s attack animations ([anim]; any animation when null - eat and block animations are not swings). */
    private fun swings(
        run: Conformance.Run,
        who: String,
        from: Int,
        anim: Int? = null,
    ) = run.recording.of(who, "out")
        .filter { it.cycle >= from && it.text.startsWith("anim ") && (anim == null || it.text.startsWith("anim id=$anim ")) }
        .map { it.cycle }
        .distinct()

    private fun splats(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "out").filter { it.cycle >= from && it.text.startsWith("hits ") }
        .flatMap { ev -> Regex("type=(\\d+) dmg=(\\d+)").findAll(ev.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
        .filter { it.first != HitType.REFLECTED.id && it.first != HitType.POISON.id }
        .map { it.second }

    /**
     * A manual spell-on-player cast as the client sends it: the spell plus its manual target (Autocast.prepareAttack drops a
     * manual spell without MANUAL_TARGET - the freeze scenarios used to punch instead of casting), with 99 Magic.
     */
    private fun manualCast(
        a: Client,
        b: Pawn,
        spell: CombatSpell,
    ) {
        a.skills.setBaseLevel(Skills.MAGIC, 99)
        gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(a, spell, b)
    }

    /** Every hitmark on [who] from [from]: (cycle, hit type id, damage). */
    private fun hitEvents(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "out").filter { it.cycle >= from && it.text.startsWith("hits ") }
        .flatMap { ev -> Regex("type=(\\d+) dmg=(\\d+)").findAll(ev.text).map { m -> Triple(ev.cycle, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.toList() }

    /** The player's processing position this tick (the engine cycles players in index order - its PID). */
    @Suppress("UNUSED_PARAMETER")
    private fun pidRank(
        run: Conformance.Run,
        p: Client,
    ): Int = p.index

    private fun clearSkull(p: Client) {
        p.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        p.timers.remove(gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER)
        p.attr.remove(gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR)
    }

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
        p.timers.remove(gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER)
        gg.rsmod.plugins.content.mechanics.freeze.FreezeLeash.stop(p)
        // Poison/venom from an earlier scenario (smoke spells in hit delay) would land a tick inside the next one's measurement.
        gg.rsmod.plugins.content.mechanics.poison.Poison.cure(p)
        gg.rsmod.plugins.content.mechanics.poison.Venom.cure(p, immunityTicks = 0, announce = false)
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
