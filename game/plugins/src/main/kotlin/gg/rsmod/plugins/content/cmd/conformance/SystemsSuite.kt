package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.message.impl.FriendListAddMessage
import gg.rsmod.game.message.impl.FriendListDeleteMessage
import gg.rsmod.game.message.impl.MessagePrivateMessage
import gg.rsmod.game.message.impl.MessagePublicMessage
import gg.rsmod.game.message.impl.OpLoc1Message
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.content.areas.poh.PlayerHouse
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.sendWeaponComponentInformation
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.game.tools.importer.CasinoInterfaceImportTool as CasinoLayout
import gg.rsmod.plugins.content.activity.casino.CasinoScreens
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.items.osrs.Burns
import gg.rsmod.plugins.content.items.osrs.MoonSets
import gg.rsmod.plugins.content.mechanics.hud.ClientHudSync
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.travel.FairyRings

/**
 * `conform systems` (owner run 2026-09-30): the runtime acceptance checks of that run, with headless bots on the live handlers.
 * Burn (one combined hit per cycle), poison / venom (progression, end, cure, HUD state), Eclipse atlatl speed, Abyssal tentacle
 * special, no guards in a player-owned house, the Trollheim / Wilderness cliffside, fairy rings and the Trinket of fairies, the
 * Colossal blade, and the social flows (friends, private messages, friends chat, clan / guest clan chat with the /p /f /c /g
 * prefixes, LootShare eligibility). References: OSRS Wiki / RuneScape Wiki / donor values quoted per check.
 */
object SystemsSuite : Conformance.Suite {
    private const val DUMMY_NPC = 1
    private const val DUMMY_LIFE = 1_000_000

    /** COLOSSAL_BLADE (23891): the generated constant lives in another writer's still-uncommitted Items.kt. */
    private const val COLOSSAL_BLADE = 23891

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val only = args.joinToString(" ").lowercase()
        suspend fun scenario(
            name: String,
            body: suspend QueueTask.() -> Unit,
        ) {
            if (Conformance.stopRequested || (only.isNotBlank() && !name.contains(only))) return
            run.progress(name)
            val start = run.world.currentCycle
            try {
                body()
            } catch (e: Throwable) {
                run.add(Conformance.Result(name, "scenario ran", Verdict.FAIL, "no exception", e.toString().take(200)))
            }
            run.window(name, start)
        }
        val arena = SummoningSuite.arenaFor(run, run.origin.transform(3, 0))
        scenario("burn") { burn(run, arena) }
        scenario("poison") { poison(run, arena) }
        scenario("venom") { venom(run, arena) }
        scenario("eclipse") { eclipse(run, arena) }
        scenario("tentacle") { tentacle(run, arena) }
        scenario("colossal") { colossal(run, arena) }
        scenario("poh") { poh(run) }
        scenario("cliffside") { cliffside(run) }
        scenario("fairy") { fairy(run, arena) }
        scenario("trinket") { trinket(run, arena) }
        scenario("social") { social(run, arena) }
        scenario("options") { options(run, arena) }
        scenario("screens") { screens(run, arena) }
        scenario("chattabs") { chatTabs(run, arena) }
        scenario("acceptaid") { with(AidScenarios) { acceptAid(run, arena) } }
        scenario("potionshare") { with(AidScenarios) { potionShare(run, arena) } }
        scenario("exchange") { exchangeState(run) }
        scenario("pestcontrol") { with(PestControlScenarios) { pestControl(run) } }
        scenario("pestdeparture") { with(PestControlScenarios) { pestDeparture(run) } }
        scenario("pestsolo") { with(PestControlScenarios) { pestSolo(run) } }
    }

    /**
     * exchange (bug search 2026-10-02, ROOT_CAUSES 133): the Grand Exchange book holds bare ids, so a used, partly degraded
     * item offered on it reached its buyer fully charged ("absent = full"). `ge_sell` on an Abyssal whip carrying charges is refused and
     * the whip keeps its charges (DGN 2026-10-21: was a Chaotic rapier, N464 2026-10-30: was a Zaryte bow - both removed and refused for that). Only the refusal is exercised - no offer is written to the book.
     */
    private suspend fun QueueTask.exchangeState(run: Conformance.Run) {
        val e = "exchange"
        val hall = Tile(3165, 3487, 0)
        val g = run.bot(hall, "GX")
        repeat(14) { wait(1) }
        g.inventory.removeAll()
        g.inventory.addPreservingAttr(Item(Items.ABYSSAL_WHIP).apply { attr[gg.rsmod.game.model.item.ItemAttribute.CHARGES] = 12 })
        wait(1)
        val c = run.world.currentCycle
        Bots.inject(g, gg.rsmod.game.message.impl.ClientCheatMessage(0, "ge_sell ${Items.ABYSSAL_WHIP} 1 1000"))
        repeat(3) { wait(1) }
        val said = run.recording.of("GX", "msg").filter { it.cycle >= c }.joinToString(" / ") { it.text.substringAfter("message=").take(100) }
        val kept = g.inventory.rawItems.count { it?.id == Items.ABYSSAL_WHIP && it.attr[gg.rsmod.game.model.item.ItemAttribute.CHARGES] == 12 }
        run.expectTrue(
            e, "ge_sell of a whip carrying charges: refused, the whip keeps them",
            kept == 1 && said.contains(gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.MSG_HOLDS_STATE), "kept=$kept msgs: $said",
        )
        g.inventory.removeAll()

        // A14/A15: real definitions + running service, every value surface uses OSRS-or-cache, including notes.
        val defs = run.world.definitions
        val exchange = run.world.getService(gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService::class.java)
        run.expectTrue(e, "A14 exchange service is running", exchange != null, "service=$exchange")
        val pouch = defs.getAllKeys(gg.rsmod.game.fs.def.ItemDef::class.java)
            .map { defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, it) }
            .firstOrNull { it.name == "Steel titan pouch" && !it.noted }
        run.expectTrue(e, "A14 667-only max-level pouch exists", pouch != null, "pouch=${pouch?.id}")
        val examples = listOf(4736, 4712, 22326) + listOfNotNull(pouch?.id)
        val risk = gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider(run.world)
        for (id in examples) {
            val def = defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, id)
            val osrs = gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices.osrsPrice(def)
            if (id == pouch?.id) run.expectTrue(e, "A14 pouch has no OSRS price", osrs == null, "osrs=$osrs cache=${def.cost}")
            val expected = osrs ?: def.cost
            val forms = listOf(id) + if (def.noteLinkId > 0 && !def.noted) listOf(def.noteLinkId) else emptyList()
            for (form in forms) {
                val item = Item(form, 2)
                val unit = gg.rsmod.plugins.content.mechanics.exchange.GePrice.unit(run.world, form)
                val stack = expected.toLong() * 2
                run.check(e, "A14 $form GE unit", unit, "OSRS hourly or cache" to expected)
                run.check(e, "A14 $form death/risk", risk.getValue(form), "same unit" to expected.toLong())
                run.check(e, "A14 $form price checker", gg.rsmod.plugins.content.inter.pricecheck.PriceChecker.value(g, item),
                    "same stack capped to varc" to stack.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                run.check(e, "A14 $form loot key", gg.rsmod.plugins.content.mechanics.pvp.LootKeys.value(run.world, listOf(item)), "same stack" to stack)
                run.check(e, "A15 $form trade", gg.rsmod.plugins.content.mechanics.trading.impl.TradeItems.value(item) {
                    gg.rsmod.plugins.content.mechanics.exchange.GePrice.unit(run.world, it)
                }, "same stack" to stack)
            }
        }
    }

    /** Hitsplats of [type] per cycle on [who] since [from]: cycle -> damages. */
    private fun splats(
        run: Conformance.Run,
        who: String,
        from: Int,
        type: Int,
    ): Map<Int, List<Int>> {
        val re = Regex("type=$type dmg=(\\d+)")
        return run.recording.of(who, "out")
            .filter { it.cycle >= from && it.text.startsWith("hits ") }
            .associate { ev -> ev.cycle to re.findAll(ev.text).map { it.groupValues[1].toInt() }.toList() }
            .filterValues { it.isNotEmpty() }
    }

    private fun full(p: Player) = p.setCurrentLifepoints(p.getMaximumLifepoints())

    // ---- 3. Burn --------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.burn(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val t = run.bot(arena, "BT")
        val src = run.bot(arena.transform(1, 0), "BS")
        wait(2)
        val burnType = gg.rsmod.plugins.api.HitType.BURN.id
        // Five stacks at once: ten cycles of ONE hit of 5.
        val c0 = run.world.currentCycle
        repeat(5) { Burns.apply(t, source = src) }
        run.expectTrue("burn", "a sixth stack is refused", !Burns.apply(t, source = src), "active=${Burns.active(t).size}")
        repeat(44) {
            full(t)
            wait(1)
        }
        val five = splats(run, "BT", c0, burnType)
        run.expectTrue("burn", "5 stacks: never more than one burn splat per cycle", five.values.all { it.size == 1 }, five.toString().take(200))
        run.check("burn", "5 stacks: damage per cycle", five.values.map { it.sum() }, "wiki: damage = stacks, 10 cycles" to List(10) { 5 })
        run.check("burn", "5 stacks: cycles 4 ticks apart", five.keys.sorted().zipWithNext { a, b -> b - a }.distinct(), "wiki: every 4 ticks" to listOf(4))
        // Staggered stacks: 1, then a second two ticks later - each keeps its own lifetime, still one splat per cycle.
        val c1 = run.world.currentCycle
        Burns.apply(t, source = src)
        wait(6) // the first cycle (tick 4) hits with one stack; the second stack joins the cycle after
        Burns.apply(t, source = src)
        repeat(50) {
            full(t)
            wait(1)
        }
        val staggered = splats(run, "BT", c1, burnType)
        run.expectTrue("burn", "staggered: one splat per cycle", staggered.values.all { it.size == 1 }, staggered.toString().take(200))
        run.check("burn", "staggered: 1 then 2 x 9 then 1", staggered.values.map { it.sum() }, "per-instance lifetime" to listOf(1) + List(9) { 2 } + listOf(1))
        // A pyrelord burn (10 dealt 5 per cycle) with a weapon burn: one splat of 6, 6, then 1s - never two splats in a tick.
        val c3 = run.world.currentCycle
        Burns.apply(t, damage = 10, source = src, perCycle = 5)
        Burns.apply(t, source = src)
        repeat(44) {
            full(t)
            wait(1)
        }
        val mixed = splats(run, "BT", c3, burnType)
        run.expectTrue("burn", "pyrelord + weapon burn: one splat per cycle", mixed.values.all { it.size == 1 }, mixed.toString().take(200))
        run.check("burn", "pyrelord + weapon burn damage", mixed.values.map { it.sum() }, "combined" to listOf(6, 6) + List(8) { 1 })
        // Kill attribution: every burn point is credited to the burning player.
        run.expectTrue("burn", "damage credited to the source", (t.damageMap[src]?.totalDamage ?: 0) >= 70, "credited=${t.damageMap[src]?.totalDamage}")
        // NPC target.
        val npc = run.npc(DUMMY_NPC, arena.transform(0, 3), "BN")
        npc.setCurrentLifepoints(DUMMY_LIFE)
        wait(1)
        val c2 = run.world.currentCycle
        repeat(3) { Burns.apply(npc, source = src) }
        repeat(20) { wait(1) }
        val onNpc = splats(run, "BN", c2, burnType)
        run.expectTrue("burn", "npc: one splat per cycle of 3", onNpc.values.all { it == listOf(3) } && onNpc.isNotEmpty(), onNpc.toString().take(200))
        // HUD state: stacks and countdown come from the server.
        Burns.apply(t, source = src)
        wait(1)
        val hud = ClientHudSync.timers(t)
        run.expectTrue("burn", "HUD tmr burn stacks / countdown", hud[9] == 1 && hud[10] in 1..4, hud.joinToString("|"))
        // Death clears every stack.
        Burns.clear(t)
        run.check("burn", "cleared on death", Burns.active(t).size, "C-07" to 0)
        run.remove(npc)
    }

    // ---- 4. Poison / Venom --------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.poison(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val t = run.bot(arena.transform(0, 1), "PT")
        wait(2)
        val c0 = run.world.currentCycle
        Poison.poisonSeverity(t, 7)
        val hud = ClientHudSync.timers(t)
        run.expectTrue("poison", "HUD: poison, next hit 2, countdown 30", hud[4] == 1 && hud[5] == 2 && hud[6] == 30, hud.joinToString("|"))
        repeat(7 * 30 + 5) {
            full(t)
            wait(1)
        }
        val hits = splats(run, "PT", c0, gg.rsmod.plugins.api.HitType.POISON.id)
        run.check("poison", "severity 7 hits", hits.values.flatten(), "wiki: ceil(severity/5), -1 per hit" to listOf(2, 2, 1, 1, 1, 1, 1))
        run.check("poison", "hits 30 ticks apart", hits.keys.sorted().zipWithNext { a, b -> b - a }.distinct(), "wiki: 30 ticks" to listOf(30))
        run.expectTrue("poison", "wears off by itself", !Poison.isPoisoned(t) && !t.timers.has(gg.rsmod.game.model.timer.POISON_TIMER), "poisoned=${Poison.isPoisoned(t)}")
        run.check("poison", "HUD clear after", ClientHudSync.timers(t)[4], "none" to 0)
        // Cure.
        Poison.poison(t, 6)
        Poison.cure(t)
        run.expectTrue("poison", "cure stops it", !Poison.isPoisoned(t), "poisoned=${Poison.isPoisoned(t)}")
    }

    private suspend fun QueueTask.venom(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val t = run.bot(arena.transform(1, 1), "VT")
        wait(2)
        val c0 = run.world.currentCycle
        Poison.poisonSeverity(t, 20)
        Venom.envenom(t)
        run.expectTrue("venom", "venom replaces poison (no second damage task)", !Poison.isPoisoned(t) && !t.timers.has(gg.rsmod.game.model.timer.POISON_TIMER), "poison=${Poison.isPoisoned(t)}")
        repeat(10 * 30 + 3) {
            full(t)
            wait(1)
        }
        val hits = splats(run, "VT", c0, gg.rsmod.plugins.api.HitType.VENOM.id)
        run.check("venom", "progression", hits.values.flatten(), "wiki: 6 +2 per hit, cap 20" to listOf(6, 8, 10, 12, 14, 16, 18, 20, 20, 20))
        run.check("venom", "hits 30 ticks (18 s) apart", hits.keys.sorted().zipWithNext { a, b -> b - a }.distinct(), "wiki: 18 s" to listOf(30))
        val poisonDuring = splats(run, "VT", c0, gg.rsmod.plugins.api.HitType.POISON.id)
        run.check("venom", "no poison splats while envenomed", poisonDuring.size, "Venom > Poison" to 0)
        val hud = ClientHudSync.timers(t)
        run.expectTrue("venom", "HUD: venom, next 20", hud[4] == 2 && hud[5] == 20 && hud[6] in 1..30, hud.joinToString("|"))
        // Antipoison: venom becomes poison; anti-venom: cured + immunity.
        Venom.downgradeToPoison(t)
        run.expectTrue("venom", "antipoison turns venom into poison", Poison.isPoisoned(t) && !t.attr.has(gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR), "poisoned=${Poison.isPoisoned(t)}")
        Poison.cure(t)
        Venom.envenom(t)
        Venom.cure(t, 100)
        run.expectTrue("venom", "anti-venom cures and immunises", !Venom.envenom(t), "venom re-applied during immunity")
        run.check("venom", "HUD antivenom immunity", ClientHudSync.timers(t)[8] > 0, "server VENOM_IMMUNITY" to true)
        // Death ends venom for good (the timer used to survive and restart venom at 6).
        t.timers.remove(gg.rsmod.game.model.timer.VENOM_IMMUNITY)
        Venom.envenom(t)
        Venom.cure(t, immunityTicks = 0, announce = false)
        val c1 = run.world.currentCycle
        repeat(35) {
            full(t)
            wait(1)
        }
        run.check("venom", "no venom hit after cure", splats(run, "VT", c1, gg.rsmod.plugins.api.HitType.VENOM.id).size, "cured" to 0)
    }

    // ---- 5. Eclipse atlatl ------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.eclipse(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val a = run.bot(arena.transform(2, 0), "EA")
        val b = run.bot(arena.transform(4, 0), "EB")
        val dummy = run.npc(DUMMY_NPC, arena.transform(2, 4), "ED")
        dummy.setCurrentLifepoints(DUMMY_LIFE)
        a.equipment[EquipmentType.WEAPON.id] = Item(Items.ECLIPSE_ATLATL)
        a.equipment[EquipmentType.AMMO.id] = Item(Items.ATLATL_DART, 1000)
        a.sendWeaponComponentInformation() // the equip route's weapon type (attack styles)
        wait(1)
        val byStyle = HashMap<WeaponStyle, Int>()
        for (style in 0..3) {
            a.setVarp(AttackTab.ATTACK_STYLE_VARP, style)
            byStyle[CombatConfigs.getAttackStyle(a)] = CombatConfigs.getAttackDelay(a)
        }
        run.check("eclipse", "Rapid", byStyle[WeaponStyle.RAPID], "wiki" to 3)
        run.check("eclipse", "Accurate", byStyle[WeaponStyle.ACCURATE], "wiki" to 4)
        run.check("eclipse", "Longrange", byStyle[WeaponStyle.LONG_RANGE], "wiki" to 4)
        // Full Eclipse set in PvP: the same 3 / 4 / 4 (it used to reach 2 on Rapid).
        MoonSets.MoonSet.ECLIPSE.pieces.forEach { piece ->
            val slot = piece.newId.let { id -> run.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).equipSlot }
            a.equipment[slot] = Item(piece.newId)
        }
        a.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR] = java.lang.ref.WeakReference(b)
        val pvp = HashMap<WeaponStyle, Int>()
        for (style in 0..3) {
            a.setVarp(AttackTab.ATTACK_STYLE_VARP, style)
            pvp[CombatConfigs.getAttackStyle(a)] = CombatConfigs.getAttackDelay(a)
        }
        a.attr.remove(gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR)
        run.check("eclipse", "full set PvP Rapid", pvp[WeaponStyle.RAPID], "owner/wiki" to 3)
        run.check("eclipse", "full set PvP Accurate", pvp[WeaponStyle.ACCURATE], "owner" to 4)
        // Real attacks on the dummy (Rapid, full set): swing cycles 3 apart, one hit per swing.
        a.setVarp(AttackTab.ATTACK_STYLE_VARP, (0..3).first { s -> a.setVarp(AttackTab.ATTACK_STYLE_VARP, s); CombatConfigs.getAttackStyle(a) == WeaponStyle.RAPID })
        val c0 = run.world.currentCycle
        a.attack(dummy)
        repeat(30) {
            dummy.setCurrentLifepoints(DUMMY_LIFE)
            if (a.getCombatTarget() == null) a.attack(dummy)
            wait(1)
        }
        a.resetInteractions()
        val swings = run.recording.of("EA", "out").filter { it.cycle >= c0 && it.text.startsWith("anim ") && !it.text.startsWith("anim id=-1") }.map { it.cycle }.distinct()
        run.check("eclipse", "Rapid swings 3 ticks apart (live)", swings.zipWithNext { x, y -> y - x }.distinct(), "wiki" to listOf(3))
        // Weapon hits only: the Eclipse set's burn splats (type 28) land on the dummy too.
        val hitCycles = run.recording.of("ED", "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") && !it.text.contains("type=28 ") }.map { it.cycle }
        run.expectTrue("eclipse", "no duplicate attacks (hits <= swings)", hitCycles.size <= swings.size + 1, "swings=${swings.size} hits=${hitCycles.size}")
        run.remove(dummy)
    }

    // ---- 6. Abyssal tentacle ----------------------------------------------------------------------------------------------

    private suspend fun QueueTask.tentacle(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val a = run.bot(arena.transform(-2, 0), "TA")
        val dummy = run.npc(DUMMY_NPC, arena.transform(-2, 1), "TD")
        dummy.setCurrentLifepoints(DUMMY_LIFE)
        a.equipment[EquipmentType.WEAPON.id] = Item(Items.ABYSSAL_TENTACLE)
        a.sendWeaponComponentInformation()
        AttackTab.setEnergy(a, 100)
        a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
        wait(1)
        val c0 = run.world.currentCycle
        a.attack(dummy)
        repeat(6) { wait(1) }
        a.resetInteractions()
        run.check("tentacle", "energy after the special", AttackTab.getEnergy(a), "wiki: 50%" to 50)
        run.expectTrue("tentacle", "target bound (frozen)", dummy.timers.has(FROZEN_TIMER), "frozen=${dummy.timers.has(FROZEN_TIMER)}")
        val gfx = run.recording.of("TD", "out").any { it.cycle >= c0 && it.text.startsWith("gfx id=341 ") }
        run.expectTrue("tentacle", "spotanim 341 on the target", gfx, "gameval SP_ATTACK_ABYSSAL_WHIP")
        val sound = run.recording.of("TA", "msg").any { it.cycle >= c0 && it.text.contains("2713") }
        run.expectTrue("tentacle", "sound 2713", sound, "wiki: Abyssal whip special attack")
        run.remove(dummy)
    }

    // ---- 11. Colossal blade -----------------------------------------------------------------------------------------------

    private suspend fun QueueTask.colossal(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val a = run.bot(arena.transform(-3, -2), "CA")
        val b = run.bot(arena.transform(-2, -2), "CB")
        a.equipment[EquipmentType.WEAPON.id] = Item(COLOSSAL_BLADE)
        a.sendWeaponComponentInformation()
        wait(1)
        run.check("colossal", "attack speed", CombatConfigs.getAttackDelay(a), "wiki" to 6)
        run.expectTrue("colossal", "no special attack", !gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks.hasSpecialAttack(COLOSSAL_BLADE), "registered")
        val defs = run.world.definitions
        fun npcOfSize(size: Int): Int? =
            (1..16000).firstOrNull { id ->
                runCatching { defs.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, id) }.getOrNull()?.let { it.size == size && it.options.any { o -> o.equals("Attack", true) } } == true
            }
        val results = HashMap<Int, Int>()
        for (size in listOf(1, 3, 5, 6)) {
            val id = npcOfSize(size) ?: continue
            val n = run.npc(id, arena.transform(-8 - size * 2, -8), "C$size")
            wait(1)
            val with = MeleeCombatFormula.getMaxHit(a, n, 1.0, 1.0).toInt()
            results[size] = with
            run.remove(n)
        }
        val base = results[1]?.minus(2)
        if (base != null) {
            val sizes = listOf(1, 3, 5, 6).filter { it in results }
            run.check("colossal", "bonus by npc size $sizes", sizes.map { s -> results.getValue(s) - base }, "wiki: 2 x min(size,5)" to sizes.map { 2 * minOf(it, 5) })
        }
        val vsPlayer = MeleeCombatFormula.getMaxHit(a, b, 1.0, 1.0).toInt()
        run.check("colossal", "PvP: a player is size 1 (+2)", base?.let { vsPlayer - it }, "wiki: player size 1" to 2)
    }

    // ---- 7. POH -----------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.poh(run: Conformance.Run) {
        val cityTile = listOf(Tile(3222, 3218, 0), Tile(3212, 3428, 0), Tile(2965, 3380, 0), Tile(3093, 3493, 0)).firstOrNull { GuardedZones.zoneAt(it) != null }
        val a = run.bot(cityTile ?: Tile(3222, 3218, 0), "HA")
        wait(2)
        val arrival = gg.rsmod.plugins.content.areas.poh.PlayerHouse.build(a)
        if (arrival == null) {
            run.add(Conformance.Result("poh", "house built", Verdict.FAIL, "a house instance", "null"))
            return
        }
        a.moveTo(arrival)
        wait(2)
        PvpSkull.applyTestSkull(a)
        repeat(25) { wait(1) }
        val map = run.world.instanceAllocator.getMap(a.tile)
        val guards = ArrayList<Npc>()
        run.world.npcs.forEach { n -> if (map != null && map.area.contains(n.tile) && n.def.name.contains("guard", ignoreCase = true)) guards += n }
        run.expectTrue("poh", "no Guard / Wizguard in the house (skulled owner, 25 ticks)", guards.isEmpty(), guards.joinToString { "${it.def.name}@${it.tile}" })
        run.expectTrue("poh", "the house is not a guard zone", !CityGuards.isGuardedZone(a.tile), "tile=${a.tile}")
        run.expectTrue("poh", "the house is still safe (PvP gate)", GuardedZones.contains(a.zoneTile()), "tile=${a.zoneTile()}")
        occultAltarSound(run, a)
        if (cityTile != null) {
            run.expectTrue("poh", "world city still guarded", CityGuards.isGuardedZone(cityTile), "tile=$cityTile")
            a.moveTo(cityTile)
            repeat(15) { wait(1) }
            val near = ArrayList<Npc>()
            run.world.npcs.forEach { n -> if (n.tile.isWithinRadius(a.tile, 6) && n.def.name.contains("guard", ignoreCase = true)) near += n }
            run.expectTrue("poh", "skulled in a world city: a guard comes", near.isNotEmpty(), "guards near=${near.size}")
        }
        a.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
    }

    /**
     * A24 (owner 2026-10-10 "altar of occult in poh maakt geen geluid"): the bot clicks the Altar of the Occult in its own
     * house the way the client does (multiloc 62924, the slot of the book's option on the variant it sees) and every switch
     * sends sound 2674 exactly once - 2009scape MagicAltarListener.swapSpellBook `playAudio(PRAYER_RECHARGE_2674)`.
     */
    private suspend fun QueueTask.occultAltarSound(
        run: Conformance.Run,
        a: Client,
    ) {
        val house = PlayerHouse
        val area = run.world.instanceAllocator.getMap(a.tile)?.area
        val altar =
            area?.let { ar ->
                (ar.bottomLeftX..ar.topRightX).asSequence().flatMap { x -> (ar.bottomLeftZ..ar.topRightZ).asSequence().map { z -> Tile(x, z, a.tile.height) } }
                    .mapNotNull { t ->
                        run.world.chunks.get(t, createIfNeeded = false)
                            ?.getEntities<gg.rsmod.game.model.entity.GameObject>(t, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                            ?.firstOrNull { it.id == house.OCCULT_ALTAR }
                    }.firstOrNull()
            }
        if (altar == null) {
            run.add(Conformance.Result("poh", "occult altar in the PvP/PvM room", Verdict.FAIL, "loc ${house.OCCULT_ALTAR}", "not found in the house"))
            return
        }
        a.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.MAGIC, 99)
        a.attr[gg.rsmod.plugins.content.unlocks.UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] = true
        a.attr[gg.rsmod.plugins.content.unlocks.UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED] = true
        Spellbooks.select(a, Spellbook.STANDARD)
        wait(2)
        for (target in listOf(Spellbook.LUNAR, Spellbook.ANCIENT, Spellbook.STANDARD)) {
            val current = a.getSpellbook()
            val name = target.name.lowercase()
            // the variant the client shows is the one without the current book's option
            val variant =
                house.OCCULT_ALTAR_VARIANTS.map { run.world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, it) }
                    .firstOrNull { d -> d.options.none { it?.lowercase() == current.name.lowercase() } }
            val slot = variant?.options?.indexOfFirst { it?.lowercase() == name }?.plus(1) ?: 0
            val c0 = run.world.currentCycle
            if (slot in 1..5) Bots.inject(a, opLocAt(slot, house.OCCULT_ALTAR, altar.tile))
            repeat(12) { wait(1) }
            val sounds = run.recording.of("HA", "msg").filter { it.cycle >= c0 && it.text.startsWith("SynthSoundMessage(sound=${Sfx.PRAYER_RECHARGE},") }
            run.check(
                "poh",
                "occult altar $current -> $target (option $slot): book + sound 2674 once",
                "${a.getSpellbook()} sounds=${sounds.size}",
                "2009scape MagicAltarListener: PRAYER_RECHARGE_2674" to "$target sounds=1",
            )
        }
    }

    private fun opLocAt(
        slot: Int,
        id: Int,
        tile: Tile,
    ): gg.rsmod.game.message.Message =
        when (slot) {
            1 -> OpLoc1Message(0, tile.x, id, tile.z)
            2 -> gg.rsmod.game.message.impl.OpLoc2Message(id, tile.x, tile.z, 0)
            3 -> gg.rsmod.game.message.impl.OpLoc3Message(id, tile.x, tile.z, 0)
            4 -> gg.rsmod.game.message.impl.OpLoc4Message(id, tile.x, tile.z, 0)
            else -> gg.rsmod.game.message.impl.OpLoc5Message(id, tile.x, tile.z, 0)
        }

    // ---- 8. Cliffside -----------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.cliffside(run: Conformance.Run) {
        // The reported click: Wilderness side (2954,3684) of loc 34878 at 2951,3681. Owner decision 2026-09-30: both ways - it climbs
        // back out of the Wilderness towards Trollheim.
        val a = run.bot(Tile(2954, 3684, 0), "KA")
        wait(2)
        val c0 = run.world.currentCycle
        Bots.inject(a, OpLoc1Message(0, 2951, 34878, 3681))
        repeat(14) { wait(1) }
        val msgs = run.recording.of("KA", "msg").filter { it.cycle >= c0 }.map { it.text }
        run.expectTrue("cliffside", "2954,3684 climbs (owner: both ways)", msgs.none { it.contains("Nothing interesting happens") } && a.tile.x < 2951, "at ${a.tile} ${msgs.filter { it.contains("MessageGame") && !it.contains("__RSPS_HUD__") }.joinToString(" / ").take(150)}")
        // Trollheim side 34889 at 2931,3680: 64 Agility, lands across the cliff.
        val b = run.bot(Tile(2929, 3680, 0), "KB")
        wait(2)
        val before = Tile(b.tile)
        Bots.inject(b, OpLoc1Message(0, 2931, 34889, 3680))
        repeat(14) { wait(1) }
        run.expectTrue("cliffside", "34889 climbs across", b.tile != before && gg.rsmod.plugins.content.skills.agility.isUsableLanding(run.world, b.tile), "from $before to ${b.tile}")
        // Every cliffside placement answers with a route (no unhandled option).
        val placements = listOf(Triple(3803, 2876, 3671), Triple(9303, 2855, 3663), Triple(9304, 2873, 3660), Triple(9305, 2894, 3672), Triple(9306, 2909, 3685), Triple(34877, 2876, 3619), Triple(34878, 2874, 3595))
        for ((id, x, z) in placements) {
            // From each side of the loc in turn until one climbs: a climb animation (740 / 1148 / 839) and a new tile.
            var climbed = false
            var detail = ""
            for ((n, start) in listOf(Tile(x - 2, z, 0), Tile(x + 4, z, 0), Tile(x, z - 2, 0), Tile(x, z + 4, 0)).withIndex()) {
                if (run.world.collision.isClipped(start)) continue
                val label = "K$id-$n"
                val p = run.bot(start, label)
                wait(2)
                val c = run.world.currentCycle
                Bots.inject(p, OpLoc1Message(0, x, id, z))
                repeat(16) { wait(1) }
                val anims = run.recording.of(label, "out").filter { it.cycle >= c && (it.text.startsWith("anim id=740 ") || it.text.startsWith("anim id=1148 ") || it.text.startsWith("anim id=839 ")) }
                val m = run.recording.of(label, "msg").filter { it.cycle >= c }.map { it.text }.filter { it.contains("MessageGame") && !it.contains("__RSPS_HUD__") }
                detail += "side$n: anim=${anims.size} at ${p.tile.x},${p.tile.z} ${m.joinToString(" / ").take(80)}; "
                if (anims.isNotEmpty()) {
                    // Owner 2026-09-30: a climb that ends in a walled-in pocket is a stuck player, not a climb - for every rock and
                    // cliff, the donor-sourced Trollheim chain (3803, 9303-9305) included: in this cache one side of each of those
                    // landed on a single walled-in tile (ShortcutSupport.chainLanding).
                    climbed = gg.rsmod.plugins.content.skills.agility.isUsableLanding(run.world, p.tile)
                    if (!climbed) detail += "landed in a pocket at ${p.tile.x},${p.tile.z}; "
                    break
                }
            }
            run.expectTrue("cliffside", "$id at $x,$z climbs", climbed, detail.take(250))
        }
    }

    // ---- 9 / 10. Fairy rings and the Trinket of fairies -------------------------------------------------------------------

    private suspend fun QueueTask.fairy(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val a = run.bot(arena.transform(3, 3), "FA")
        wait(2)
        run.expectTrue("fairy", "no staff: refused", !FairyRings.open(a, FairyRings.Source.RING), "opened without a staff")
        a.equipment[EquipmentType.WEAPON.id] = Item(Items.DRAMEN_STAFF)
        run.expectTrue("fairy", "dial + travel log open", FairyRings.open(a, FairyRings.Source.RING) && a.interfaces.isVisible(FairyRings.DIAL_INTERFACE) && a.interfaces.isVisible(FairyRings.TRAVEL_LOG_INTERFACE), "734/735")
        // Dial d k r (Edgeville): dial 1 a->d is one clockwise turn (values a d c b, Void rotate +1), dial 2 i->k and dial 3 p->r two.
        Bots.button(a, FairyRings.DIAL_INTERFACE, 23)
        Bots.button(a, FairyRings.DIAL_INTERFACE, 25)
        Bots.button(a, FairyRings.DIAL_INTERFACE, 25)
        Bots.button(a, FairyRings.DIAL_INTERFACE, 27)
        Bots.button(a, FairyRings.DIAL_INTERFACE, 27)
        wait(1)
        run.check("fairy", "dialled code", FairyRings.dialledCode(a), "buttons" to "dkr")
        Bots.button(a, FairyRings.DIAL_INTERFACE, FairyRings.TELEPORT_BUTTON)
        repeat(12) { wait(1) }
        run.check("fairy", "teleported to dkr", a.tile.isWithinRadius(FairyRings.CODES.getValue("dkr").tile, 2), "Void codes" to true)
        run.check("fairy", "last destination kept", a.attr[FairyRings.LAST_CODE], "service" to "dkr")
        run.expectTrue("fairy", "travel log has dkr", "dkr" in FairyRings.travelLog(a), FairyRings.travelLog(a).toString())
        FairyRings.open(a, FairyRings.Source.RING)
        run.check("fairy", "dials reopen on the last code", FairyRings.dialledCode(a), "last" to "dkr")
        a.closeInterface(FairyRings.DIAL_INTERFACE)
        // The real ring: "Use" on loc 14097 (Edgeville, 3129,3496) opens the dial through the object route.
        a.moveTo(Tile(3130, 3497, 0))
        wait(2)
        Bots.inject(a, OpLoc1Message(0, 3129, 14097, 3496))
        repeat(6) { wait(1) }
        run.expectTrue("fairy", "ring loc 14097 'Use' opens the dial", a.interfaces.isVisible(FairyRings.DIAL_INTERFACE), "734 visible")
        a.closeInterface(FairyRings.DIAL_INTERFACE)
        wait(1)
        run.expectTrue("fairy", "closing the dial gives the inventory back", !a.interfaces.isVisible(FairyRings.TRAVEL_LOG_INTERFACE), "735 still open")
        // An invalid code lands in Zanaris.
        FairyRings.travel(a, "bjp", FairyRings.Source.RING)
        repeat(12) { wait(1) }
        run.expectTrue("fairy", "invalid code -> Zanaris", a.tile.isWithinRadius(FairyRings.ZANARIS, 3), "at ${a.tile}")
    }

    private suspend fun QueueTask.trinket(
        run: Conformance.Run,
        arena: Tile,
    ) {
        run.expectTrue(
            "trinket", "sold in the Deadman shop",
            StoreCatalogue.entries(StoreCatalogue.Shop.DEADMAN).any { it.purchaseItem == Items.TRINKET_OF_FAIRIES }, "Deadman shop entries",
        )
        val a = run.bot(arena.transform(4, 3), "TT")
        a.inventory.add(Items.TRINKET_OF_FAIRIES, 1)
        wait(2)
        val slot = a.inventory.getItemIndex(Items.TRINKET_OF_FAIRIES, false)
        Bots.itemOption(a, slot, "Fairy-ring")
        wait(1)
        run.expectTrue("trinket", "Fairy-ring opens the real dial (no staff)", a.interfaces.isVisible(FairyRings.DIAL_INTERFACE), "734 visible")
        a.attr[FairyRings.LAST_CODE] = "cks"
        FairyRings.DIAL_VARBITS.forEachIndexed { i, v -> a.setVarbit(v, FairyRings.dialsFor("cks")!![i]) }
        Bots.button(a, FairyRings.DIAL_INTERFACE, FairyRings.TELEPORT_BUTTON)
        repeat(12) { wait(1) }
        run.expectTrue("trinket", "fairy ring teleport (cks)", a.tile.isWithinRadius(FairyRings.CODES.getValue("cks").tile, 2), "at ${a.tile}")
        // Last-destination.
        a.moveTo(arena.transform(4, 3))
        wait(2)
        Bots.itemOption(a, slot, "Last-destination")
        repeat(12) { wait(1) }
        run.expectTrue("trinket", "Last-destination", a.tile.isWithinRadius(FairyRings.CODES.getValue("cks").tile, 2), "at ${a.tile}")
        // Spirit tree: the real destination menu (interface 864 since 558f72624, owner 2026-10-02: interfaces, not chat menus); row 0.
        Bots.itemOption(a, slot, "Spirit-tree")
        wait(2)
        val treeMenu = gg.rsmod.plugins.content.mechanics.travel.SpiritTrees
        run.expectTrue("trinket", "Spirit-tree opens the destination menu", a.interfaces.isVisible(treeMenu.INTERFACE), "${treeMenu.INTERFACE} visible")
        Bots.button(a, treeMenu.INTERFACE, treeMenu.LIST, slot = 0)
        repeat(12) { wait(1) }
        run.expectTrue("trinket", "Spirit-tree teleport", a.tile.isWithinRadius(gg.rsmod.plugins.content.mechanics.travel.SpiritTrees.STATIONS[0].tile, 3), "at ${a.tile}")
        // Tool-leprechauns: a menu of the tool leprechauns standing in this world; option 1 lands next to one.
        val spots = gg.rsmod.plugins.content.mechanics.travel.leprechaunSpotsForTest(run.world)
        run.expectTrue("trinket", "Tool-leprechauns has destinations", spots.isNotEmpty(), "spots=${spots.size} ${spots.take(4)}")
        if (spots.isNotEmpty()) {
            Bots.itemOption(a, slot, "Tool-leprechauns")
            wait(2)
            Bots.answerOption(a, 1)
            repeat(12) { wait(1) }
            run.expectTrue("trinket", "Tool-leprechauns teleport (${spots[0].first})", a.tile.isWithinRadius(spots[0].second, 2), "at ${a.tile}, target ${spots[0].second}")
        }
        // Restrictions: level 31+ Wilderness and Tele Block refuse.
        val deep = Tile(3100, 3900, 0)
        a.moveTo(deep)
        wait(2)
        Bots.itemOption(a, slot, "Last-destination")
        repeat(12) { wait(1) }
        run.expectTrue("trinket", "refused above level 30 Wilderness (lvl ${deep.getWildernessLevel()})", a.tile.isWithinRadius(deep, 1), "at ${a.tile}")
        a.moveTo(arena.transform(4, 3))
        wait(2)
        a.timers[TELEBLOCK_TIMER] = 100
        Bots.itemOption(a, slot, "Last-destination")
        repeat(12) { wait(1) }
        run.expectTrue("trinket", "refused under Tele Block", a.tile.isWithinRadius(arena.transform(4, 3), 1), "at ${a.tile}")
        a.timers.remove(TELEBLOCK_TIMER)
    }

    // ---- 2. Social ---------------------------------------------------------------------------------------------------------

    private fun say(
        bot: Client,
        text: String,
    ) {
        val out = ByteArray(256)
        val len = bot.world.huffman.compress(text, out)
        Bots.inject(bot, MessagePublicMessage(0, 0, 0, text.length, out.copyOf(len)))
    }

    private fun received(
        run: Conformance.Run,
        who: String,
        from: Int,
        kind: String,
    ) = run.recording.of(who, "msg").count { it.cycle >= from && it.text.startsWith(kind) }

    private suspend fun QueueTask.social(
        run: Conformance.Run,
        arena: Tile,
    ) {
        // Real account names (the friend list only takes existing characters); bots are never saved. Only the agent's own test
        // accounts and never one that is online: on 2026-10-07 the sorted save list put a bot on the owner's account "anudd" and
        // his own login was refused with ALREADY_ONLINE while the suite ran.
        val online = run.world.players.entries.filterNotNull().map { it.username.lowercase() }.toSet()
        val names =
            java.io.File("data/saves").listFiles()?.map { it.name }
                ?.filter { !it.contains('.') && it.lowercase().startsWith("sumtest") && it.lowercase() !in online }?.sorted() ?: emptyList()
        if (names.size < 3) {
            run.notTested("social", "friends / chats", "needs three offline sumtest* test characters, found ${names.size}: $names")
            return
        }
        val a = run.bot(arena.transform(0, -3), names[0])
        val b = run.bot(arena.transform(1, -3), names[1])
        val c = run.bot(arena.transform(2, -3), names[2])
        run.recording.track(a, "SA")
        run.recording.track(b, "SB")
        run.recording.track(c, "SC")
        wait(2)
        // Friends: add, PM, delete.
        Bots.inject(a, FriendListAddMessage(b.username))
        wait(1)
        run.expectTrue("social", "Add Friend", a.friends.any { it.equals(b.username, true) }, a.friends.toString())
        val c0 = run.world.currentCycle
        val pm = ByteArray(256)
        val pmText = "hello"
        val pmLen = run.world.huffman.compress(pmText, pm)
        Bots.inject(a, MessagePrivateMessage(b.username, pm.copyOf(pmLen), pmText.length))
        wait(2)
        run.expectTrue("social", "PM delivered", received(run, "SB", c0, "MessagePrivateReceived") > 0, "B msgs")
        Bots.inject(a, FriendListDeleteMessage(b.username))
        wait(1)
        run.expectTrue("social", "Delete Friend", a.friends.none { it.equals(b.username, true) }, a.friends.toString())
        // Friends chat: A owns "Conf"; B joins, talks with "/" and "/f "; C (outside) receives nothing.
        val fc = run.world.friendsChat
        // Hermetic: the registries are private copies in conformance mode (SharedDataFiles), seeded from the owner's data - reset
        // whatever ranks the real accounts' channels carry so every run starts from the 667 defaults.
        fc.setEnterRank(a, gg.rsmod.game.model.social.FriendsChatRank.GUEST)
        fc.setTalkRank(a, gg.rsmod.game.model.social.FriendsChatRank.GUEST)
        fc.setKickRank(a, gg.rsmod.game.model.social.FriendsChatRank.OWNER)
        fc.setPrefix(a, "Conf")
        fc.joinWithMessages(a, a.username)
        run.expectTrue("social", "FC join", fc.joinWithMessages(b, a.username), "B joined")
        wait(1)
        val c1 = run.world.currentCycle
        say(b, "/one")
        say(b, "/f two")
        wait(2)
        run.check("social", "FC: '/' and '/f ' reach the owner", received(run, "SA", c1, "MessageFriendChannel"), "2 lines" to 2)
        run.check("social", "FC: outsider hears nothing", received(run, "SC", c1, "MessageFriendChannel"), "no leak" to 0)
        // Clan: A founds, B is invited and accepts; C listens as a guest.
        val clans = gg.rsmod.plugins.content.mechanics.clan.Clans
        listOf(b, c).forEach { if (clans.clanOf(it) != null) clans.leave(it) }
        val clanName = clans.clanOf(a) ?: "Conf${run.world.currentCycle % 100000}"
        if (clans.clanOf(a) != null || clans.create(a, clanName)) {
            clans.recruit(a, b)
            clans.takeInvite(b, a)?.let { clans.acceptInvite(b, it.name) }
            run.expectTrue("social", "clan invite accepted", clans.clanOf(b) == clanName, "B clan=${clans.clanOf(b)}")
            clans.setAllowGuests(a, true)
            clans.listen(c, clanName)
            wait(1)
            val c2 = run.world.currentCycle
            say(b, "//three")
            say(b, "/c four")
            wait(2)
            run.check("social", "clan: '//' and '/c ' reach A", received(run, "SA", c2, "MessageClanChannel"), "2 lines" to 2)
            run.check("social", "clan lines are not friends-chat lines", received(run, "SA", c2, "MessageFriendChannel"), "no leak" to 0)
            val c3a = run.world.currentCycle
            say(c, "/g refused")
            wait(2)
            run.expectTrue("social", "guest talk refused by default", received(run, "SA", c3a, "MessageClanChannel") == 0, "A msgs")
            clans.editSettings(a) { guestsCanTalk = true }
            val c3 = run.world.currentCycle
            say(c, "/g five")
            wait(2)
            run.expectTrue("social", "guest '/g ' reaches clan members", received(run, "SA", c3, "MessageClanChannel") >= 1, "A msgs")
            val c4 = run.world.currentCycle
            say(b, "/p six")
            wait(2)
            run.check("social", "'/p ' is public, not a channel", received(run, "SA", c4, "MessageClanChannel") + received(run, "SA", c4, "MessageFriendChannel"), "public" to 0)
            clans.stopListening(c)
            clans.leave(b)
        } else {
            run.notTested("social", "clan", "could not found a test clan")
        }
        // Friends chat ranks and permissions (1108 / FRIEND_SETRANK / FRIENDCHAT_KICK), all through the real packets or setup calls.
        Bots.inject(a, FriendListAddMessage(b.username))
        wait(1)
        Bots.inject(a, gg.rsmod.game.message.impl.FriendSetRankMessage(b.username, 3))
        wait(1)
        run.check("social", "FC: friend rank set (Sergeant)", fc.channelOf(b)?.rankOf(b), "FRIEND_SETRANK" to 3)
        // Private chat On / Friends / Off: what A's friend list shows for B (world 1 = online, 0 = offline).
        fun worldOfB(from: Int): String? =
            run.recording.of("SA", "msg").lastOrNull { it.cycle >= from && it.text.startsWith("UpdateFriendListMessage") && it.text.contains(b.username, true) }
                ?.text?.let { Regex("username=${Regex.escape(b.username)}, world=([0-9]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val filters = gg.rsmod.game.model.ChatFilterType.ON
        for ((setting, expected) in listOf(gg.rsmod.game.model.ChatFilterType.OFF to "0", gg.rsmod.game.model.ChatFilterType.FRIENDS to "0", gg.rsmod.game.model.ChatFilterType.ON to "1")) {
            val cf = run.world.currentCycle
            Bots.inject(b, gg.rsmod.game.message.impl.ChatFilterMessage(filters, setting, filters))
            wait(1)
            // FRIENDS: B has not added A, so A sees B offline.
            run.check("social", "privacy $setting: B's world on A's list", worldOfB(cf), "667 friend list" to expected)
        }
        fc.setEnterRank(a, 7)
        run.expectTrue("social", "FC: 'Only me' refuses a guest", !fc.join(c, a.username), "C joined")
        fc.setEnterRank(a, -1)
        run.expectTrue("social", "FC: 'Anyone' lets the guest in", fc.join(c, a.username), "C refused")
        fc.setTalkRank(a, 3)
        val c5 = run.world.currentCycle
        say(c, "/guest line")
        say(b, "/sergeant line")
        wait(2)
        run.check("social", "FC: talk rank Sergeant+ (guest muted, sergeant heard)", received(run, "SA", c5, "MessageFriendChannel"), "1 line" to 1)
        fc.setTalkRank(a, -1)
        Bots.inject(a, gg.rsmod.game.message.impl.FriendsChatKickMessage(c.username))
        wait(1)
        run.expectTrue("social", "FC: owner kicks the guest", fc.channelOf(c) == null, "C still in")
        run.expectTrue("social", "FC: kicked guest is banned from rejoining", !fc.join(c, a.username), "C rejoined")
        // Ignore: C ignores A, A's private message does not reach C.
        Bots.inject(c, gg.rsmod.game.message.impl.IgnoreListAddMessage(a.username, false))
        wait(1)
        val c6 = run.world.currentCycle
        val pm2 = ByteArray(256)
        val pm2Len = run.world.huffman.compress("ignored", pm2)
        Bots.inject(a, MessagePrivateMessage(c.username, pm2.copyOf(pm2Len), 7))
        wait(2)
        run.check("social", "Ignore blocks private messages", received(run, "SC", c6, "MessagePrivateReceived"), "ignore" to 0)
        // Clan: promote / demote / kick.
        val clans2 = gg.rsmod.plugins.content.mechanics.clan.Clans
        if (clans2.clanOf(a) != null) {
            clans2.recruit(a, b)
            clans2.takeInvite(b, a)?.let { clans2.acceptInvite(b, it.name) }
            clans2.setRank(a, b.username, gg.rsmod.plugins.content.mechanics.clan.ClanRank.CAPTAIN)
            run.check("social", "clan promote to Captain", clans2.rankOfPlayer(b), "ClanRank.CAPTAIN" to 4)
            clans2.setRank(a, b.username, gg.rsmod.plugins.content.mechanics.clan.ClanRank.CORPORAL)
            run.check("social", "clan demote to Corporal", clans2.rankOfPlayer(b), "ClanRank.CORPORAL" to 1)
            clans2.kickMember(a, b.username)
            wait(1)
            run.expectTrue("social", "clan kick", clans2.clanOf(b) == null, "B clan=${clans2.clanOf(b)}")
        }
        // CoinShare: an item worth over 100,000 is split as coins between the sharers - the item itself never spawns.
        val coinItem = Items.ABYSSAL_WHIP
        val coinDef = run.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, coinItem)
        if (coinDef.cost > gg.rsmod.plugins.content.inter.friends.LootShare.COIN_SHARE_MINIMUM) {
            fc.setCoinShare(a, true)
            var spawnedItem = 0
            fun coinPiles(): List<gg.rsmod.game.model.entity.GroundItem> =
                run.world.chunks.get(a.tile, createIfNeeded = true)!!
                    .getEntities<gg.rsmod.game.model.entity.GroundItem>(gg.rsmod.game.model.EntityType.GROUND_ITEM)
                    .filter { it.item == Items.COINS_995 && it.tile == a.tile }
            val before = coinPiles().size
            gg.rsmod.plugins.content.inter.friends.LootShare.share(run.world, a, listOf(a, b), Item(coinItem), a.tile) { _, _ -> spawnedItem++ }
            val coins = coinPiles()
            run.expectTrue("social", "CoinShare splits as coins, no item", spawnedItem == 0 && coins.size - before == 2, "item spawns=$spawnedItem coin piles=${coins.size - before}")
            run.check("social", "CoinShare split", coins.map { it.amount }.distinct(), "value / sharers" to listOf(coinDef.cost / 2))
            coins.forEach { run.world.remove(it) }
            fc.setCoinShare(a, false)
        }
        // LootShare points: 10 % decay per whole day, the part-day kept (RuneScape Wiki "LootShare", Mod Emilee 2009).
        val lsp = gg.rsmod.plugins.content.inter.friends.LootShare
        val now = System.currentTimeMillis()
        a.attr[lsp.POINTS] = 1000
        a.attr[lsp.POINTS_DECAYED_AT] = now - 2 * 86_400_000L - 3_600_000L
        lsp.decayPoints(a, now)
        run.check("social", "LSP decay after 2 days", a.attr[lsp.POINTS], "wiki: -10% per day" to 810)
        run.check("social", "LSP decay keeps the part-day", now - (a.attr[lsp.POINTS_DECAYED_AT] ?: 0L), "1 h left" to 3_600_000L)
        a.attr[lsp.POINTS] = 0
        b.attr[lsp.POINTS] = 0
        // LootShare eligibility: only members who damaged the npc share.
        val ls = gg.rsmod.plugins.content.inter.friends.LootShare
        listOf(a, b).forEach { it.setVarbit(ls.ACTIVE_VARBIT, 1) }
        val npc = run.npc(DUMMY_NPC, arena.transform(0, -1), "SN")
        npc.damageMap.add(a, 5)
        wait(1)
        npc.setCurrentLifepoints(0)
        val multi = npc.tile.isMulti(run.world)
        val onlyKiller = if (npc.isDead()) ls.sharers(a, npc.tile) else null
        npc.damageMap.add(b, 5)
        val both = if (npc.isDead()) ls.sharers(a, npc.tile) else null
        if (multi) {
            run.expectTrue("social", "LootShare: no share without B's damage", onlyKiller == null, "sharers=${onlyKiller?.map { it.username }}")
            run.expectTrue("social", "LootShare: both damaged -> both share", both?.size == 2, "sharers=${both?.map { it.username }}")
        } else {
            run.notTested("social", "LootShare eligibility", "arena is single-way")
        }
        run.remove(npc)
        listOf(a, b).forEach { ls.disable(it, quiet = true) }
        fc.leave(b)
        fc.setPrefix(a, null)
    }

    // ---- Every Friends Chat / Clan Chat / LootShare option, clicked as the 667 client sends it --------------------------------

    private suspend fun QueueTask.typeText(
        bot: Client,
        text: String,
    ) {
        // A real player types after the input box is on screen: the button's queue opens it the cycle after the click.
        wait(3)
        Bots.inject(bot, gg.rsmod.game.message.impl.ResumePStringDialogMessage(text))
        wait(2)
    }

    /** A number typed into an amount box (RESUME_P_COUNTDIALOG), as the client sends it. */
    private suspend fun QueueTask.typeNumber(
        bot: Client,
        value: Int,
    ) {
        wait(3)
        Bots.inject(bot, gg.rsmod.game.message.impl.ResumePCountDialogMessage(value))
        wait(2)
    }

    /**
     * Options tab (261) toggles, Chat Setup (982) colours and the chatbox filter tabs (751), clicked as the 667 client sends them;
     * every one must leave the var the client draws from on the server (saved with the player, pushed at login). References:
     * the components' varpTriggers (261) and CS2 83 / 175 / 184 (982, 751), decoded 2026-09-24 and 2026-10-01.
     */
    private suspend fun QueueTask.chatTabs(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val names = java.io.File("data/saves").listFiles()?.map { it.name }?.filter { !it.contains('.') }?.sorted() ?: emptyList()
        if (names.isEmpty()) {
            run.notTested("chattabs", "all", "needs a saved character")
            return
        }
        val a = run.bot(arena.transform(0, 4), names.last())
        run.recording.track(a, "TA")
        repeat(12) { wait(1) }
        val e = "chattabs"
        for ((component, varp) in listOf(8 to 2159, 16 to 1055, 22 to 1054)) {
            for (op in 2..4) {
                Bots.button(a, 751, component, option = op)
                wait(1)
                run.check(e, "751:$component op$op -> varp $varp", a.getVarp(varp), "CS2 184 mode" to op - 2)
            }
            Bots.button(a, 751, component, option = 2)
            wait(1)
        }
        Bots.button(a, 751, 31, option = 4)
        wait(1)
        run.check(e, "Game: Filter -> varbit 6161", a.getVarbit(6161), "CS2 184" to 1)
        Bots.button(a, 751, 31, option = 2)
        wait(1)
        run.check(e, "Game: All -> varbit 6161", a.getVarbit(6161), "CS2 184" to 0)
        // Options tab toggles (261): profanity 1438, chat effects 171, mouse buttons 170, accept aid 427.
        for ((component, varp) in listOf(3 to 1438, 4 to 171, 6 to 170, 7 to 427)) {
            val before = a.getVarp(varp)
            Bots.button(a, 261, component)
            wait(1)
            run.expectTrue(e, "261:$component toggles varp $varp", a.getVarp(varp) != before, "before=$before after=${a.getVarp(varp)}")
            Bots.button(a, 261, component)
            wait(1)
            run.check(e, "261:$component toggles back", a.getVarp(varp), "toggle" to before)
        }
        // Chat Setup (982): opened from 261:5, clan colour 982:20 (value 3), private split 982:52 (value 4), No split 982:41.
        Bots.button(a, 261, 5)
        wait(1)
        run.expectTrue(e, "261:5 opens Chat Setup 982", a.interfaces.isVisible(982), "982 visible")
        Bots.button(a, 982, 20)
        Bots.button(a, 982, 52)
        wait(1)
        run.check(e, "982:20 clan chat colour -> varbit 3612", a.getVarbit(3612), "CS2 83" to 3)
        run.check(e, "982:52 private chat colour -> varp 287", a.getVarp(287), "CS2 83" to 4)
        Bots.button(a, 982, 41)
        Bots.button(a, 982, 17)
        wait(1)
        run.check(e, "982:41 No split -> varp 287", a.getVarp(287), "CS2 2735" to 0)
        Bots.button(a, 982, 5)
        wait(1)
        run.expectTrue(e, "982:5 back to Options 261", a.interfaces.isVisible(261), "261 visible")
    }

    private suspend fun QueueTask.options(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val names = java.io.File("data/saves").listFiles()?.map { it.name }?.filter { !it.contains('.') }?.sorted() ?: emptyList()
        if (names.size < 3) {
            run.notTested("options", "all", "needs three saved characters")
            return
        }
        // Other accounts than the social scenario (whose kick-ban lasts an hour in memory).
        val pick = if (names.size >= 6) names.subList(3, 6) else names.subList(0, 3)
        val a = run.bot(arena.transform(0, 2), pick[0])
        val b = run.bot(arena.transform(1, 2), pick[1])
        val c = run.bot(arena.transform(2, 2), pick[2])
        run.recording.track(a, "OA")
        run.recording.track(b, "OB")
        run.recording.track(c, "OC")
        wait(2)
        repeat(10) { wait(1) } // the login-time queues (familiar, notes, gameframe) finish first, as for a real player
        val fc = run.world.friendsChat
        val e = "options"
        // -- Friends Chat tab (1109) "Open Settings" (33) -> setup 1108.
        Bots.button(a, 1109, 33)
        wait(2)
        run.expectTrue(e, "FC tab: Settings opens 1108", a.interfaces.isVisible(1108), "1108 visible")
        // Prefix (22): op1 Set prefix (typed), op2 Disable.
        Bots.button(a, 1108, 22, option = 1)
        typeText(a, "OptTest")
        run.check(e, "FC setup: Set prefix", fc.settingsOf(a.username)?.prefix, "typed" to "OptTest")
        Bots.button(a, 1108, 22, option = 2)
        wait(1)
        run.check(e, "FC setup: Disable", fc.settingsOf(a.username)?.prefix, "disabled" to null)
        Bots.button(a, 1108, 22, option = 1)
        typeText(a, "OptTest")
        // Enter (23) / Talk (24): op1..op9 -> Anyone(-1), Any friends(0), Recruit+..General+ (1..6), Only me (7).
        for (op in 1..9) {
            Bots.button(a, 1108, 23, option = op)
            Bots.button(a, 1108, 24, option = op)
            wait(1)
            run.check(e, "FC setup: Who can enter op$op", fc.settingsOf(a.username)?.enterRank, "1108 menu" to op - 2)
            run.check(e, "FC setup: Who can talk op$op", fc.settingsOf(a.username)?.talkRank, "1108 menu" to op - 2)
        }
        // Kick (25): op4 Corporal+ .. op9 Only me.
        for (op in 4..9) {
            Bots.button(a, 1108, 25, option = op)
            wait(1)
            run.check(e, "FC setup: Who can kick op$op", fc.settingsOf(a.username)?.kickRank, "1108 menu" to op - 2)
        }
        // LootShare (26): op1 No-one .. op8 General+.
        for (op in 1..8) {
            Bots.button(a, 1108, 26, option = op)
            wait(1)
            run.check(e, "FC setup: Who can share loot op$op", fc.settingsOf(a.username)?.lootShareRank, "1108 menu" to op - 2)
        }
        // CoinShare (33) toggles; Close (3).
        val coinBefore = fc.settingsOf(a.username)?.coinShare == true
        Bots.button(a, 1108, 33)
        wait(1)
        run.check(e, "FC setup: CoinShare toggle", fc.settingsOf(a.username)?.coinShare, "toggled" to !coinBefore)
        Bots.button(a, 1108, 33)
        Bots.button(a, 1108, 3)
        wait(1)
        run.expectTrue(e, "FC setup: Close", !a.interfaces.isVisible(1108), "1108 still open")
        // Open for everyone again; the owner joins, B joins and leaves through the tab's Join / Leave packet.
        fc.setEnterRank(a, -1)
        fc.setTalkRank(a, -1)
        fc.setKickRank(a, 7)
        fc.setLootShareRank(a, 0)
        Bots.inject(a, gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage(a.username))
        Bots.inject(b, gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage(a.username))
        wait(1)
        run.expectTrue(e, "FC tab: Join", fc.channelOf(b) != null, "B not in")
        Bots.inject(b, gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage(""))
        wait(1)
        run.expectTrue(e, "FC tab: Leave", fc.channelOf(b) == null, "B still in")
        Bots.inject(b, gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage(a.username))
        wait(1)
        run.expectTrue(e, "FC tab: Rejoin", fc.channelOf(b) != null, "B not in")
        // LootShare button (19): starts the 2-minute switch (loading varbit).
        Bots.inject(a, FriendListAddMessage(b.username))
        wait(1)
        Bots.button(b, 1109, 19)
        wait(1)
        run.check(e, "FC tab: LootShare button starts the 2-minute switch", b.getVarbit(gg.rsmod.plugins.content.inter.friends.LootShare.LOADING_VARBIT), "varbit 4072" to 1)
        gg.rsmod.plugins.content.inter.friends.LootShare.disable(b, quiet = true)
        // Kick (FRIENDCHAT_KICK from the member list): a rank below the kick rank is refused, the owner may.
        Bots.inject(c, gg.rsmod.game.message.impl.ClanJoinChatLeaveChatMessage(a.username))
        wait(1)
        Bots.inject(b, gg.rsmod.game.message.impl.FriendsChatKickMessage(c.username))
        wait(1)
        run.expectTrue(e, "FC: kick below the kick rank refused", fc.channelOf(c) != null, "C kicked")
        Bots.inject(a, gg.rsmod.game.message.impl.FriendsChatKickMessage(c.username))
        wait(1)
        run.expectTrue(e, "FC: owner kick", fc.channelOf(c) == null, "C still in")
        fc.leave(b)
        fc.leave(a)
        fc.setPrefix(a, null)

        // -- Clan Chat tab (1110).
        val clans = gg.rsmod.plugins.content.mechanics.clan.Clans
        val screens = gg.rsmod.plugins.content.mechanics.clan.ClanScreens
        val S = gg.rsmod.plugins.content.mechanics.clan.ClanSettingsInterface
        if (clans.clanOf(a) != null) clans.leave(a)
        if (clans.clanOf(b) != null) clans.leave(b)
        val clanName = "Opt${run.world.currentCycle % 100000}"
        // 85 without a clan: found one.
        Bots.button(a, 1110, 85)
        wait(3)
        Bots.answerOption(a, 1)
        typeText(a, clanName)
        run.check(e, "clan tab 85: found a clan", clans.clanOf(a), "typed" to clanName)
        // 90 Invite (player-targeted), invitation screen 1095 Accept (36) / Refuse (46).
        Bots.inject(a, gg.rsmod.game.message.impl.OpPlayerTMessage(b.index, (1110 shl 16) or 90, -1, 0))
        wait(2)
        run.expectTrue(e, "clan tab 90: Invite reaches B", b.attr[clans.PENDING_INVITE] != null, "no invite")
        // The invite chat line click (OPPLAYER9 with the inviter's index), as the client sends it.
        Bots.inject(b, gg.rsmod.game.message.impl.OpPlayer9Message(a.index))
        wait(4)
        run.expectTrue(e, "invitation screen 1095 opens", b.interfaces.isVisible(screens.INVITE), "1095")
        Bots.inject(b, gg.rsmod.game.message.impl.ResumePauseButtonMessage(screens.INVITE, screens.INVITE_ACCEPT, screens.INVITE_ACCEPT))
        wait(4)
        run.check(e, "invitation: Accept", clans.clanOf(b), "joined" to clanName)
        Bots.inject(a, gg.rsmod.game.message.impl.OpPlayerTMessage(c.index, (1110 shl 16) or 90, -1, 0))
        wait(2)
        Bots.inject(c, gg.rsmod.game.message.impl.OpPlayer9Message(a.index))
        wait(4)
        Bots.button(c, screens.INVITE, screens.INVITE_REFUSE.first())
        wait(2)
        run.expectTrue(e, "invitation: Refuse", clans.clanOf(c) == null && !c.interfaces.isVisible(screens.INVITE), "C clan=${clans.clanOf(c)}")
        // 76 Clan Details, 80 Clan Settings and its toggles / sub-screens.
        Bots.button(a, 1110, 76)
        wait(1)
        run.expectTrue(e, "clan tab 76: Clan Details opens", a.interfaces.isVisible(screens.DETAILS), "1107")
        a.closeInterface(screens.DETAILS)
        Bots.button(a, 1110, 80)
        wait(1)
        run.expectTrue(e, "clan tab 80: Clan Settings opens", a.interfaces.isVisible(S.INTERFACE), "1096")
        val g0 = clans.clanOfPlayer(a)?.allowGuests
        Bots.button(a, S.INTERFACE, S.GUESTS_ENTER)
        wait(1)
        run.check(e, "settings: guests may enter", clans.clanOfPlayer(a)?.allowGuests, "toggled" to (g0 != true))
        val t0 = clans.clanOfPlayer(a)?.guestsCanTalk
        Bots.button(a, S.INTERFACE, S.GUESTS_TALK)
        wait(1)
        run.check(e, "settings: guests may talk", clans.clanOfPlayer(a)?.guestsCanTalk, "toggled" to (t0 != true))
        val r0 = clans.clanOfPlayer(a)?.recruiting
        Bots.button(a, S.INTERFACE, S.RECRUITING)
        wait(1)
        run.check(e, "settings: recruiting", clans.clanOfPlayer(a)?.recruiting, "toggled" to (r0 != true))
        val ct0 = clans.clanOfPlayer(a)?.clanTime
        Bots.button(a, S.INTERFACE, S.CLAN_TIME)
        wait(1)
        run.check(e, "settings: clan time", clans.clanOfPlayer(a)?.clanTime, "toggled" to (ct0 != true))
        Bots.button(a, S.INTERFACE, S.SELECT_FLAG)
        wait(1)
        run.expectTrue(e, "settings: Select Flag opens 1089", a.interfaces.isVisible(screens.FLAGS), "1089")
        Bots.button(a, screens.FLAGS, screens.FLAG_CLOSE)
        wait(1)
        Bots.button(a, S.INTERFACE, S.EDIT_MOTIF)
        wait(1)
        run.expectTrue(e, "settings: Edit motif opens 1105", a.interfaces.isVisible(screens.MOTIF), "1105")
        a.closeInterface(screens.MOTIF)
        // The motif designer replaced 1096 on the main screen: reopen Clan Settings from the tab, as a player does.
        Bots.button(a, 1110, 80)
        wait(2)
        Bots.button(a, S.INTERFACE, S.EDIT_MOTTO)
        typeText(a, "We test everything")
        run.check(e, "settings: Edit motto (1096 open)", clans.clanOfPlayer(a)?.motto, "typed" to "We test everything")
        Bots.button(a, S.INTERFACE, S.EDIT_KEYWORDS)
        typeText(a, "pvm pvp")
        run.check(e, "settings: Edit keywords (1096 open)", clans.clanOfPlayer(a)?.keywords, "typed" to "pvm pvp")
        Bots.button(a, S.INTERFACE, S.EDIT_THREAD)
        typeText(a, "abc123")
        run.check(e, "settings: Edit thread (1096 open)", clans.clanOfPlayer(a)?.threadId, "typed" to "abc123")
        a.closeInterface(S.INTERFACE)
        // 95 guest channel: C types the clan name, a second click leaves.
        clans.editSettings(a) { allowGuests = true }
        Bots.button(c, 1110, 95)
        typeText(c, clanName)
        run.expectTrue(e, "clan tab 95: join as guest", clans.isListening(c), "C not listening")
        Bots.button(c, 1110, 95)
        wait(1)
        run.expectTrue(e, "clan tab 95: leave guest channel", !clans.isListening(c), "C still listening")
        // 100 Ban / 105 Unban by name.
        Bots.button(a, 1110, 100)
        typeText(a, c.username)
        run.expectTrue(e, "clan tab 100: Ban", clans.clanOfPlayer(a)?.bans?.any { it.equals(c.username, true) } == true, "bans=${clans.clanOfPlayer(a)?.bans}")
        clans.listen(c, clanName)
        run.expectTrue(e, "banned player cannot listen", !clans.isListening(c), "C listening")
        Bots.button(a, 1110, 105)
        typeText(a, c.username)
        run.expectTrue(e, "clan tab 105: Unban", clans.clanOfPlayer(a)?.bans?.none { it.equals(c.username, true) } == true, "bans=${clans.clanOfPlayer(a)?.bans}")
        // 115 Leave clan, confirmed.
        Bots.button(b, 1110, 115)
        wait(3)
        Bots.answerOption(b, 1)
        wait(1)
        run.expectTrue(e, "clan tab 115: Leave clan", clans.clanOf(b) == null, "B clan=${clans.clanOf(b)}")
        if (clans.clanOf(a) != null) clans.leave(a)
    }

    // ---- ROOT_CAUSES 92: every input asked while its own screen is open (casino, lectern, quiver, tool store) ------------------

    private suspend fun QueueTask.screens(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "screens"
        val a = run.bot(arena.transform(-1, -2), "SCR")
        repeat(10) { wait(1) } // the login-time queues finish first, as for a real player
        val min = gg.rsmod.plugins.content.activity.casino.CasinoWallet.MIN_WAGER
        val bet = min * 3
        // Dice: custom bet, custom target, client seed (fairness panel).
        CasinoScreens.Dice.open(a)
        wait(1)
        Bots.button(a, CasinoLayout.DICE_ID, CasinoLayout.Dice.BET_CUSTOM)
        typeNumber(a, (bet).toInt())
        run.check(e, "casino dice: custom bet (screen open)", CasinoScreens.bet(a), "typed" to bet)
        Bots.button(a, CasinoLayout.DICE_ID, CasinoLayout.Dice.TARGET_CUSTOM)
        typeNumber(a, 37)
        run.check(e, "casino dice: custom target (screen open)", CasinoScreens.target(a), "typed" to 37)
        CasinoScreens.openFairness(a, CasinoLayout.DICE_ID)
        wait(1)
        Bots.button(a, CasinoLayout.DICE_ID, CasinoLayout.Dice.FAIRNESS + CasinoLayout.Fair.SET_SEED)
        typeText(a, "conformseed")
        run.check(e, "casino: set client seed (screen open)", gg.rsmod.plugins.content.activity.casino.CasinoSeeds.clientSeed(a), "typed" to "conformseed")
        a.closeInterface(CasinoLayout.DICE_ID)
        wait(1)
        // Mines and Blackjack custom bets.
        CasinoScreens.Mines.open(a)
        wait(1)
        Bots.button(a, CasinoLayout.MINES_ID, CasinoLayout.Mines.BET_CUSTOM)
        typeNumber(a, (bet + min).toInt())
        run.check(e, "casino mines: custom bet (screen open)", CasinoScreens.bet(a), "typed" to bet + min)
        a.closeInterface(CasinoLayout.MINES_ID)
        wait(1)
        CasinoScreens.Blackjack.open(a)
        wait(1)
        Bots.button(a, CasinoLayout.BLACKJACK_ID, CasinoLayout.Blackjack.BET_CUSTOM)
        typeNumber(a, (bet + 2 * min).toInt())
        run.check(e, "casino blackjack: custom bet (screen open)", CasinoScreens.bet(a), "typed" to bet + 2 * min)
        a.closeInterface(CasinoLayout.BLACKJACK_ID)
        wait(1)
        // Flower poker: the stake of a match in its configuring stage.
        val b = run.bot(arena.transform(0, -2), "SCR2")
        repeat(10) { wait(1) }
        val fp = gg.rsmod.plugins.content.activity.casino.FlowerPokerMatch(a, b)
        a.attr[gg.rsmod.plugins.content.activity.casino.FLOWER_MATCH_ATTR] = fp
        b.attr[gg.rsmod.plugins.content.activity.casino.FLOWER_MATCH_ATTR] = fp
        CasinoScreens.Flower.open(a)
        wait(1)
        Bots.button(a, CasinoLayout.FLOWER_ID, CasinoLayout.Flower.STAKE_BUTTON)
        typeNumber(a, (bet).toInt())
        run.check(e, "flower poker: stake (screen open)", fp.stake, "typed" to bet)
        a.closeInterface(CasinoLayout.FLOWER_ID)
        a.attr.remove(gg.rsmod.plugins.content.activity.casino.FLOWER_MATCH_ATTR)
        b.attr.remove(gg.rsmod.plugins.content.activity.casino.FLOWER_MATCH_ATTR)
        wait(1)
        // POH lectern (400): Camelot teleport tablets, option 1 (one) and option 4 (X, typed 2) with the lectern open.
        a.inventory.add(Items.SOFT_CLAY, 5)
        a.inventory.add(Items.LAW_RUNE, 10)
        a.inventory.add(Items.AIR_RUNE, 50)
        a.openInterface(400, gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN)
        wait(1)
        Bots.button(a, 400, 5, option = 1)
        repeat(8) { wait(1) }
        run.check(e, "lectern: Make 1 (lectern open)", a.inventory.getItemCount(Items.CAMELOT_TELEPORT), "made" to 1)
        a.openInterface(400, gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN)
        wait(1)
        Bots.button(a, 400, 5, option = 4)
        typeNumber(a, 2)
        repeat(14) { wait(1) }
        run.check(e, "lectern: Make X = 2 (lectern open)", a.inventory.getItemCount(Items.CAMELOT_TELEPORT), "made" to 3)
        // Dizana's quiver: Store-X with the quiver screen open.
        val quiverId =
            listOf(Items.DIZANAS_QUIVER, Items.DIZANAS_QUIVER_UNCHARGED, Items.DIZANAS_QUIVER_L, Items.DIZANAS_QUIVER_L_UNCHARGED).firstOrNull { id ->
                run.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).inventoryMenu.any { it.equals("Open", true) }
            }
        if (quiverId != null) {
            a.inventory.add(quiverId, 1)
            a.inventory.add(Items.DRAGON_ARROW, 100)
            wait(1)
            // An uncharged add can come back as the other quiver variant: take whichever quiver landed.
            val quiverIds = setOf(Items.DIZANAS_QUIVER, Items.DIZANAS_QUIVER_UNCHARGED, Items.DIZANAS_QUIVER_L, Items.DIZANAS_QUIVER_L_UNCHARGED)
            val quiverSlot = (0 until a.inventory.capacity).first { a.inventory[it]?.id in quiverIds }
            Bots.itemOption(a, quiverSlot, "Open")
            wait(2)
            val arrowSlot = a.inventory.getItemIndex(Items.DRAGON_ARROW, false)
            Bots.button(a, 1125, 0, option = 2, slot = arrowSlot, item = Items.DRAGON_ARROW)
            typeNumber(a, 40)
            val quiver = a.inventory[quiverSlot]
            run.check(e, "Dizana's quiver: Store-X = 40 (quiver open)", gg.rsmod.plugins.content.items.osrs.DizanasQuiver.storedAmmo(quiver)?.amount, "typed" to 40)
            a.closeInterface(gg.rsmod.game.tools.importer.DizanasQuiverInterfaceImportTool.INTERFACE_ID)
        } else {
            run.notTested(e, "Dizana's quiver Store-X", "no quiver id with an Open option in this cache")
        }
        wait(1)
        // Farming tool store (125 + side 126): Store-X of buckets, Withdraw-X of some back.
        a.inventory.add(Items.BUCKET, 10)
        a.openInterface(125, gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN)
        a.openInterface(126, gg.rsmod.plugins.api.InterfaceDestination.TAB_AREA)
        wait(1)
        Bots.button(a, 126, 22, option = 4)
        typeNumber(a, 6)
        run.check(e, "tool store: Store-X buckets = 6 (store open)", a.getVarbit(1441), "typed" to 6)
        Bots.button(a, 125, 7, option = 4)
        typeNumber(a, 2)
        run.check(e, "tool store: Withdraw-X buckets = 2 (store open)", a.getVarbit(1441), "6 - 2" to 4)
        a.closeInterface(125)
    }
}
