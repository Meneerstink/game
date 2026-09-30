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
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.sendWeaponComponentInformation
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
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
        val hitCycles = run.recording.of("ED", "out").filter { it.cycle >= c0 && it.text.startsWith("hits ") }.map { it.cycle }
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
        run.expectTrue("poh", "the house is still safe (PvP gate)", GuardedZones.contains(a.tile), "tile=${a.tile}")
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

    // ---- 8. Cliffside -----------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.cliffside(run: Conformance.Run) {
        // The reported click: Wilderness side (2954,3684) of loc 34878 at 2951,3681 - one-way, refused with the wiki message.
        val a = run.bot(Tile(2954, 3684, 0), "KA")
        wait(2)
        val c0 = run.world.currentCycle
        Bots.inject(a, OpLoc1Message(0, 2951, 34878, 3681))
        repeat(12) { wait(1) }
        val msgs = run.recording.of("KA", "msg").filter { it.cycle >= c0 }.map { it.text }
        run.expectTrue("cliffside", "2954,3684 answers (not 'Nothing interesting happens')", msgs.none { it.contains("Nothing interesting happens") } && msgs.any { it.contains("does not look safe") }, msgs.joinToString(" / ").take(250))
        // Trollheim side 34889 at 2931,3680: 64 Agility, lands across the cliff.
        val b = run.bot(Tile(2929, 3680, 0), "KB")
        wait(2)
        val before = Tile(b.tile)
        Bots.inject(b, OpLoc1Message(0, 2931, 34889, 3680))
        repeat(14) { wait(1) }
        run.expectTrue("cliffside", "34889 climbs across", b.tile != before && !run.world.collision.isClipped(b.tile), "from $before to ${b.tile}")
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
                    climbed = true
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
        // Spirit tree: menu of the five stations, option 1.
        Bots.itemOption(a, slot, "Spirit-tree")
        wait(2)
        Bots.answerOption(a, 1)
        repeat(12) { wait(1) }
        run.expectTrue("trinket", "Spirit-tree teleport", a.tile.isWithinRadius(gg.rsmod.plugins.content.mechanics.travel.SpiritTrees.STATIONS[0].tile, 3), "at ${a.tile}")
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
        // Real account names (the friend list only takes existing characters); bots are never saved.
        val names = java.io.File("data/saves").listFiles()?.map { it.name }?.filter { !it.contains('.') }?.sorted() ?: emptyList()
        if (names.size < 3) {
            run.notTested("social", "friends / chats", "needs three saved characters, found ${names.size}")
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
        val clanName = "Conf${run.world.currentCycle % 100000}"
        if (clans.create(a, clanName)) {
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
}
