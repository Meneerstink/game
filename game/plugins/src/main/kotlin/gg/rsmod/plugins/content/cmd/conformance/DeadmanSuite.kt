package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.addPreservingAttr
import gg.rsmod.plugins.api.ext.calculateAndSetCombatLevel
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.activity.duel_arena.getDuelMatch
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.combat.getCombatTarget

/**
 * `conform deadman` - Deadman Mode PvP rules measured on headless bots (owner 2026-10-01: breaches, DMM guards, teleports, deaths
 * and hit/combat bugs, one scenario per fixed class). Scenarios:
 * - switch: a weapon switched while the attacker is still walking to the target fires the NEW weapon's attack, never the one
 *   armed before the walk (melee -> ranged and ranged -> melee).
 * - death: a real PvP kill (whip, real combat, real death action and hooks). Owner death rework 2026-09-26
 *   (agent-handoffs/2026-09-26-death-rework-brief.txt, "Keep rule"): everything is lost; Protect Item keeps exactly 1 item, skulled
 *   or not, the most valuable by guide price. The lost items go into ONE loot key for the killer, never also on the ground; nothing
 *   is duplicated or lost; the victim respawns in a guarded zone with full life points and no skull.
 * - teleport: Varrock Teleport pressed on the real spellbook button. DeadmanTimerGate (owner 2026-09-17, OSRS Deadman wiki
 *   wording): unskulled and not hit in 7 s -> instant; unskulled and hit by a player -> refused with "You must be out of combat for
 *   another X seconds to teleport."; skulled -> 7-second countdown, then the teleport; a hit, a Tele Block or a death during the
 *   countdown stops it for good.
 * - guards: CityGuards (OSRS Wiki "Guard (Deadman Mode)", "Wizguard") in Varrock square: an unskulled player is left alone; a
 *   skulled one is attacked with visible hitsplats, also through Protect from Melee; guards stop the moment he leaves the zone or
 *   loses the skull and never step out of the zone; an unskulled player cannot attack a guard ("You probably don't want to do
 *   that."); a skulled player killed by guards leaves public ground loot and nobody gets a loot key (owner 2026-09-26).
 * - single: OSRS Wiki "PJ timer": in single-way a player attacked by A cannot be attacked by B for 8 ticks after A's last attack
 *   (4.8 s; the 12 s PvP timer is not used on Deadman worlds), and can be after that.
 * - keys: loot keys over six real kills - one key per kill, a killed key carrier hands every key's loot to his killer (contents
 *   conserved), at five keys the next kill says the limit message and the loot lies on the ground for the killer.
 * - xlog: closing the client (Player.requestLogout, what a dropped channel does): unskulled and out of combat the body leaves at
 *   once; hit by a player the body stays and can still be killed for its loot; skulled it is held for the 7-second rule.
 * - pvm: killed by an npc the items go to the gravestone (no key, no ground loot); hit by a player and then killed by the npc it is
 *   still a PvP death - the player gets the loot key (DeathResolver aggressor window).
 * - veng: Vengeance Other on the real spell-on-player packet - the 30-second delay is the caster's (OSRS Wiki), an already venged
 *   target is refused, a hit rebounds 75% (rounded down, min 1), entering a player-owned house clears it.
 * - zones: the PvP gate end to end - a target inside a guarded zone is refused with the guards' message, a combat-level gap over 14
 *   is refused (Deadman bracket), the same bracket in danger attacks.
 * - teleblock: a real Tele Block cast - 500 ticks, 250 with Protect from Magic, a second cast does not refresh it.
 * - phantom: Morrigan's javelin Phantom Strike on a player - the bleed is 10 a tick until 75% of the special's damage (OSRS Wiki).
 * - remote: the shared remote-route gate (RemoteAccess, bug search 2026-10-02) on the real command packet - from a death zone
 *   `dungeon`, `deadmanstore`, `mines` and `pestcontrolnovice` are refused and nothing moves or opens; Tele Blocked in a safe
 *   zone `dungeon` is refused; from a safe zone `dungeon` travels and `deadmanstore` opens (the routes still work).
 * - duel: a staked duel through the native screens on real packets (Challenge option, 640, 631 accept, 626 accept) - the
 *   fight starts with the match and both stakes intact (bug search 2026-10-02: closing the confirmation screen declined the
 *   match first), a forfeit pays the winner both stakes with their attributes; a skulled challenger never enters the arena
 *   and both stakes come back.
 * - casino: a Mines board laid through the real START button in a safe zone; the START button is refused in a death zone; the
 *   player carries the open board into a death zone and is killed by a player - the board does not survive the death and the
 *   killer's loot holds the carried coins plus the board's cash-out value (bug search 2026-10-02: the stake survived death).
 * - dupe: the victim drops a godsword every tick while being killed (one drop falls on the killing blow's tick) - every godsword
 *   ends up exactly once: inventory, ground or loot key.
 */
object DeadmanSuite : Conformance.Suite {
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
            val spawned = run.mark()
            try {
                body()
            } catch (e: Throwable) {
                run.add(Conformance.Result(name, "scenario ran", Conformance.Verdict.FAIL, "no exception", e.toString().take(200)))
            }
            run.window(name, start)
            // No scenario's bots may fight on into the next one in the shared arena.
            run.cleanupSince(spawned)
            wait(1)
        }
        val arena = SummoningSuite.arenaFor(run, run.origin.transform(3, 0))
        scenario("switch") { switchOnApproach(run, arena) }
        scenario("death") { pvpDeath(run, arena) }
        scenario("teleport") { teleports(run, arena) }
        scenario("guards") { guards(run) }
        scenario("single") { singleWay(run, arena) }
        scenario("keys") { lootKeys(run, arena) }
        scenario("xlog") { xLog(run, arena) }
        scenario("pvm") { pvmDeath(run, arena) }
        scenario("veng") { vengeance(run, arena) }
        scenario("zones") { pvpGate(run, arena) }
        scenario("teleblock") { teleBlock(run, arena) }
        scenario("phantom") { phantomStrike(run, arena) }
        scenario("dupe") { dropOnDeath(run, arena) }
        scenario("skull") { skulls(run, arena) }
        scenario("hamstring") { hamstring(run, arena) }
        scenario("aoe") { areaAttacks(run, arena) }
        scenario("familiar") { familiarRules(run, arena) }
        scenario("corp") { corpCave(run) }
        scenario("corrupt") { corruptSpecials(run, arena) }
        scenario("vine") { vineCall(run, arena) }
        scenario("remote") { remoteRoutes(run) }
        scenario("duel") { stakedDuel(run) }
        scenario("casino") { casinoDeath(run, arena) }
    }

    /** Hitsplat types [who] received since [from], in order. */
    private fun hitTypes(
        run: Conformance.Run,
        who: String,
        from: Int,
    ): List<Int> {
        val re = Regex("type=(\\d+) dmg=")
        return run.recording.of(who, "out")
            .filter { it.cycle >= from && it.text.startsWith("hits ") }
            .flatMap { ev -> re.findAll(ev.text).map { it.groupValues[1].toInt() }.toList() }
    }

    /** Wields [item] the way a click does (EquipAction: weapon type, style tab, autocast sync) - a direct container write leaves the
     * player fighting unarmed (punch 422, unarmed max hit). */
    private fun wield(
        p: Client,
        item: Item,
    ): EquipAction.Result {
        p.inventory.add(item)
        return EquipAction.equip(p, item, p.inventory.getItemIndex(item.id, skipAttrItems = false))
    }

    private fun reset(
        p: Client,
        tile: Tile,
    ) {
        p.resetInteractions()
        Combat.reset(p)
        p.equipment.removeAll()
        p.inventory.removeAll()
        p.moveTo(tile)
        p.setCurrentLifepoints(p.getMaximumLifepoints())
    }

    // ---- switch: weapon switched on the approach ------------------------------------------------------------------------

    private suspend fun QueueTask.switchOnApproach(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "switch"
        val a = run.bot(arena, "WA")
        val b = run.bot(arena.transform(0, 14), "WB")
        wait(2)

        suspend fun approach(
            first: Item,
            second: Item?,
            label: String,
            expectRanged: Boolean,
        ) {
            reset(a, arena)
            reset(b, arena.transform(0, 14))
            wield(a, first)
            a.equipment[EquipmentType.AMMO.id] = Item(Items.DRAGON_ARROW, 500)
            second?.let { a.inventory.add(it) }
            wait(1)
            val c = run.world.currentCycle
            a.attack(b)
            wait(1) // the walk to the first weapon's range is under way (walkTo suspends the combat cycle)
            val result = second?.let { EquipAction.equip(a, it, a.inventory.getItemIndex(it.id, skipAttrItems = false)) } ?: EquipAction.Result.SUCCESS
            var firstHitAt: Tile? = null
            var types = emptyList<Int>()
            for (t in 0 until 24) {
                b.setCurrentLifepoints(b.getMaximumLifepoints())
                wait(1)
                types = hitTypes(run, "WB", c)
                if (types.isNotEmpty()) {
                    firstHitAt = a.tile
                    break
                }
            }
            // A hitsplat of any type (a 0 is a block splat) marks the first swing; arrows tell which weapon fired it.
            val arrowsUsed = 500 - (a.equipment[EquipmentType.AMMO.id]?.amount ?: 0)
            run.expectTrue(e, "$label: switch equipped", result == EquipAction.Result.SUCCESS, "result=$result")
            run.expectTrue(e, "$label: a swing lands within 24 ticks", types.isNotEmpty(), "splats=$types")
            if (expectRanged) {
                run.expectTrue(e, "$label: the swing is the bow's (arrows used)", arrowsUsed > 0, "arrows used=$arrowsUsed splats=$types")
                // The walk stops where the bow reaches (dark bow 10 tiles), not next to the target where the whip's route ended.
                run.expectTrue(e, "$label: shot from bow range, not from melee distance", firstHitAt != null && firstHitAt.getDistance(b.tile) > 1,
                    "from=$firstHitAt target=${b.tile}")
            } else {
                run.expectTrue(e, "$label: the swing is the melee weapon's (no arrows)", arrowsUsed == 0, "arrows used=$arrowsUsed splats=$types")
                run.expectTrue(e, "$label: melee swing from next to the target", firstHitAt != null && firstHitAt.getDistance(b.tile) <= 1,
                    "from=$firstHitAt target=${b.tile}")
            }
            a.resetInteractions()
            Combat.reset(a)
            b.resetInteractions()
            Combat.reset(b)
            wait(2)
        }

        approach(Item(Items.ABYSSAL_WHIP), null, "whip, no switch (control)", false)
        approach(Item(Items.ABYSSAL_WHIP), Item(Items.DARK_BOW), "whip -> dark bow", true)
        approach(Item(Items.DARK_BOW), Item(Items.ABYSSAL_WHIP), "dark bow -> whip", false)
    }

    // ---- death: a real PvP kill ------------------------------------------------------------------------------------------

    private suspend fun QueueTask.pvpDeath(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "death"
        val a = run.bot(arena, "DA")
        val b = run.bot(arena.transform(1, 0), "DB")
        wait(2)
        val prices = gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider(run.world)

        suspend fun kill(
            label: String,
            skulled: Boolean,
            protectItem: Boolean,
            expectedKept: Int,
        ) {
            for (p in listOf(a, b)) {
                reset(p, if (p === a) arena else arena.transform(1, 0))
                p.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
                gg.rsmod.plugins.content.mechanics.prayer.Prayers.deactivateAll(p)
                p.attr.remove(gg.rsmod.plugins.content.mechanics.pvp.LootKeys.SLOTS)
            }
            wield(a, Item(Items.ABYSSAL_WHIP))
            val gear = mapOf(
                EquipmentType.WEAPON to Items.ARMADYL_GODSWORD,
                EquipmentType.CHEST to Items.BANDOS_CHESTPLATE,
                EquipmentType.LEGS to Items.BANDOS_TASSETS,
                EquipmentType.AMULET to Items.AMULET_OF_FURY,
                EquipmentType.BOOTS to Items.DRAGON_BOOTS,
            )
            gear.forEach { (slot, id) -> b.equipment[slot.id] = Item(id) }
            b.inventory.add(Items.SHARK, 3)
            b.inventory.add(Items.COINS_995, 250_000)
            if (skulled) gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(b)
            if (protectItem) {
                b.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.PRAYER, 99)
                b.setCurrentPrayerPoints(99)
                gg.rsmod.plugins.content.mechanics.prayer.Prayers.activate(b, gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_ITEM)
            }
            wait(1)
            val before = (b.equipment.toList() + b.inventory.toList()).filterNotNull().map { it.id to it.amount }
            // Independent expectation: the N single items worth most by the guide price (stackables are never "one item" here).
            val singles = before.filter { it.second == 1 }.sortedByDescending { prices.getValue(it.first) }
            val expectKept = singles.take(expectedKept).map { it.first }.sorted()
            val deathTile = b.tile
            // State a PvP death must clear or restore (OSRS: Tele Block, venom, special attack, prayer, run energy).
            b.timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 500
            Venom.envenom(b)
            AttackTab.setEnergy(b, 0)
            b.runEnergy = 0.0
            b.setCurrentLifepoints(30)
            val c = run.world.currentCycle
            a.attack(b)
            var died = false
            for (t in 0 until 150) {
                a.setCurrentLifepoints(a.getMaximumLifepoints())
                wait(1)
                if (b.isDead()) died = true
                if (died && !b.isDead() && b.tile != deathTile) break
            }
            a.resetInteractions()
            Combat.reset(a)
            wait(2)
            run.expectTrue(e, "$label: the victim died and respawned", died && !b.isDead(), "dead=${b.isDead()} tile=${b.tile}")
            val keptNow = (b.equipment.toList() + b.inventory.toList()).filterNotNull().map { it.id to it.amount }
            run.check(e, "$label: items kept", keptNow.map { it.first }.sorted(), "owner 2026-09-26: Protect Item keeps 1, else 0" to expectKept)
            val keyIds = gg.rsmod.plugins.content.mechanics.pvp.LootKeys.heldKeyIndexes(a)
            val keyItems = keyIds.flatMap { gg.rsmod.plugins.content.mechanics.pvp.LootKeys.slotItems(a, it) }.map { it.id to it.amount }
            run.check(e, "$label: one loot key for the killer", keyIds.size, "1 per kill" to 1)
            val ground = mutableListOf<Pair<Int, Int>>()
            for (dx in -1..1) for (dz in -1..1) {
                val t = deathTile.transform(dx, dz)
                run.world.chunks.get(t)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                    ?.forEach { ground += it.item to it.amount }
            }
            run.check(e, "$label: no ground loot beside the key", ground, "masterplan: never with ground loot" to emptyList<Pair<Int, Int>>())
            fun total(list: List<Pair<Int, Int>>) = list.groupBy({ it.first }, { it.second.toLong() }).mapValues { it.value.sum() }.toSortedMap()
            run.check(e, "$label: nothing duplicated or lost (kept + key + ground)", total(keptNow + keyItems + ground).toString(),
                "items before the death" to total(before).toString())
            run.expectTrue(e, "$label: respawn in a guarded zone", gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(b.zoneTile()), "tile=${b.zoneTile()}")
            run.expectTrue(e, "$label: full life points after respawn", b.getCurrentLifepoints() == b.getMaximumLifepoints(),
                "${b.getCurrentLifepoints()}/${b.getMaximumLifepoints()}")
            // Respawned means out of combat: no "in combat" teleport refusal and no "under attack" state carried over from the fight.
            run.check(e, "$label: teleport decision right after the respawn", gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate.teleportDecision(b).name,
                "out of combat after death" to "INSTANT")
            run.expectTrue(e, "$label: not 'under attack' after the respawn", !b.timers.has(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER),
                "ACTIVE_COMBAT_TIMER=${if (b.timers.has(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER)) b.timers[gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER] else 0}")
            run.expectTrue(e, "$label: Tele Block gone after the death", !b.timers.has(gg.rsmod.game.model.timer.TELEBLOCK_TIMER), "still teleblocked")
            run.expectTrue(e, "$label: venom and poison gone after the death",
                !b.attr.has(gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR) && !gg.rsmod.plugins.content.mechanics.poison.Poison.isPoisoned(b), "still envenomed / poisoned")
            // OSRS Wiki "Special attacks": "The special attack energy cannot be regenerated by these methods: ... The player dies."
            run.expectTrue(e, "$label: special attack energy is not refilled by the death", AttackTab.getEnergy(b) < 100, "energy=${AttackTab.getEnergy(b)}",
                "wiki: dying does not restore it")
            run.note("$label: after the death run energy ${b.runEnergy.toInt()}, prayer ${b.getCurrentPrayerPoints()}/${b.getMaximumPrayerPoints()} (no source checked)")
            run.expectTrue(e, "$label: victim's skull gone", !gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(b), "skulled")
            run.expectTrue(e, "$label: the killer is skulled (attacked an unskulled player / holds a key)",
                gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(a), "not skulled")
            run.note("$label: death took ${run.world.currentCycle - c} cycles")
        }

        kill("unskulled", skulled = false, protectItem = false, expectedKept = 0)
        kill("unskulled + Protect Item", skulled = false, protectItem = true, expectedKept = 1)
        kill("skulled + Protect Item", skulled = true, protectItem = true, expectedKept = 1)
    }

    // ---- teleport: the Deadman teleport gate on the real spell button ---------------------------------------------------

    private suspend fun QueueTask.teleports(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "teleport"
        val t = run.bot(arena, "TT")
        val h = run.bot(arena.transform(1, 0), "TH")
        wait(2)
        val spell = gg.rsmod.plugins.content.magic.MagicSpells.metadataByName("Varrock Teleport")
        if (spell == null) {
            run.notTested(e, "all", "no Varrock Teleport spell button")
            return
        }
        val varrock = gg.rsmod.plugins.content.magic.teleports.TeleportSpell.VARROCK.endArea
        // The landing is the spell area's tile or, when that one is taken, a walkable tile up to Landing.SEARCH_RADIUS beside it.
        val near = gg.rsmod.game.model.collision.Landing.SEARCH_RADIUS
        fun inVarrock() = t.tile.x in varrock.bottomLeftX - near..varrock.topRightX + near && t.tile.z in varrock.bottomLeftZ - near..varrock.topRightZ + near

        suspend fun prepare(skulled: Boolean) {
            reset(t, arena)
            reset(h, arena.transform(1, 0))
            t.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
            t.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)
            t.attr.remove(gg.rsmod.plugins.content.mechanics.pvp.LootKeys.SLOTS)
            gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.cancel(t)
            t.inventory.add(Items.LAW_RUNE, 100)
            t.inventory.add(Items.AIR_RUNE, 300)
            t.inventory.add(Items.FIRE_RUNE, 100)
            if (skulled) gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(t)
            // Out of every earlier fight: the 7-second "attacked" window and the last player hit lie in the past.
            repeat(14) { wait(1) }
        }

        /** H lands real hits on T (a 0 counts: "attacked by a player"), then stops. */
        suspend fun hitByPlayer() {
            wield(h, Item(Items.ABYSSAL_WHIP))
            val c = run.world.currentCycle
            h.attack(t)
            for (i in 0 until 20) {
                t.setCurrentLifepoints(t.getMaximumLifepoints())
                wait(1)
                if (run.recording.of("TT", "out").any { it.cycle >= c && it.text.startsWith("hits ") }) break
            }
            h.resetInteractions()
            Combat.reset(h)
            t.resetInteractions()
            Combat.reset(t)
        }

        fun press() = Bots.button(t, spell.interfaceId, spell.component)

        suspend fun landedWithin(ticks: Int): Int? {
            for (i in 1..ticks) {
                wait(1)
                if (inVarrock()) return i
            }
            return null
        }

        fun messagesSince(c: Int) = run.recording.of("TT", "msg").filter { it.cycle >= c && it.text.startsWith("MessageGameMessage") }.joinToString(" / ") { it.text.take(90) }

        // 1. Unskulled, not attacked: instant (the spell's own animation delay only).
        prepare(skulled = false)
        press()
        val instant = landedWithin(10)
        run.expectTrue(e, "unskulled, out of combat: teleports at once (spell delay only)", instant != null && instant <= 6, "landed after $instant ticks")
        run.expectTrue(e, "unskulled, out of combat: no countdown", !gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.isActive(t), "countdown running")

        // 2. Unskulled, just attacked by a player: refused with the wiki message, no countdown.
        prepare(skulled = false)
        hitByPlayer()
        val c2 = run.world.currentCycle
        press()
        val blocked = landedWithin(8)
        run.expectTrue(e, "unskulled, attacked by a player: no teleport", blocked == null, "landed after $blocked ticks")
        run.expectTrue(e, "unskulled, attacked by a player: the out-of-combat message", messagesSince(c2).contains("You must be out of combat for another"),
            messagesSince(c2))
        run.expectTrue(e, "unskulled, attacked by a player: no countdown interface", !gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.isActive(t), "countdown running")

        // 3. Skulled: the 7-second countdown, then the teleport by itself.
        prepare(skulled = true)
        press()
        wait(1)
        run.expectTrue(e, "skulled: the countdown starts", gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.isActive(t), "no countdown")
        val counted = landedWithin(24)
        run.expectTrue(e, "skulled: teleports when the countdown ends (12 ticks + spell delay)", counted != null && counted >= 10 && counted <= 20,
            "landed after ${counted?.plus(1)} ticks")

        // 3b. Skulled, turning the camera with the arrow keys during the countdown (the client sends every key press): not an action
        // (OSRS Wiki "Deadman Mode": "must not perform any further actions or be attacked") - the teleport still happens.
        prepare(skulled = true)
        press()
        wait(2)
        repeat(4) { Bots.inject(t, gg.rsmod.game.message.impl.KeyTypedMessage(96)); wait(1) }
        val withKeys = landedWithin(20)
        run.expectTrue(e, "skulled, arrow keys during the countdown: still teleports", withKeys != null, "no teleport (the key presses cancelled it)")

        // 3c. Skulled, opening tabs during the countdown (fixed 548:99 inventory side, resizable 746:43): the client sends an IF_BUTTON
        // for a tab switch, OSRS sends nothing - not an action, the teleport still happens.
        prepare(skulled = true)
        press()
        wait(2)
        Bots.button(t, 548, 99); wait(1)
        Bots.button(t, 746, 43); wait(1)
        val withTabs = landedWithin(20)
        run.expectTrue(e, "skulled, switching tabs during the countdown: still teleports", withTabs != null, "no teleport (the tab clicks cancelled it)")

        // 4. Skulled, hit during the countdown: it stops and never fires later.
        prepare(skulled = true)
        press()
        wait(2)
        hitByPlayer()
        val afterHit = landedWithin(24)
        run.expectTrue(e, "skulled, hit during the countdown: no teleport", afterHit == null, "landed after $afterHit ticks")

        // 5. Skulled, Tele Blocked during the countdown: no teleport at the end.
        prepare(skulled = true)
        press()
        wait(2)
        t.timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 500
        val afterBlock = landedWithin(24)
        run.expectTrue(e, "skulled, Tele Blocked during the countdown: no teleport", afterBlock == null, "landed after $afterBlock ticks")
        t.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)

        // 6. Skulled, killed during the countdown: respawns, and the old countdown never teleports him afterwards.
        prepare(skulled = true)
        press()
        wait(2)
        t.hit(damage = t.getCurrentLifepoints() + 50)
        var respawned = false
        for (i in 0 until 40) {
            wait(1)
            if (!t.isDead() && t.tile != arena) { respawned = true; break }
        }
        val afterDeath = landedWithin(20)
        run.expectTrue(e, "skulled, killed during the countdown: respawned", respawned, "tile=${t.tile} dead=${t.isDead()}")
        run.expectTrue(e, "skulled, killed during the countdown: no teleport after the respawn", afterDeath == null, "landed after $afterDeath ticks")

        // 7. The Varrock teleport tablet ("Break") goes through the same gate.
        suspend fun tablet(skulled: Boolean, teleblocked: Boolean): Pair<Int?, Int> {
            prepare(skulled)
            if (teleblocked) t.timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 500
            t.inventory.add(Items.VARROCK_TELEPORT, 3)
            wait(1)
            Bots.itemOption(t, t.inventory.getItemIndex(Items.VARROCK_TELEPORT, skipAttrItems = false), "Break")
            val landed = landedWithin(24)
            val left = t.inventory.getItemCount(Items.VARROCK_TELEPORT)
            t.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)
            return landed to left
        }
        val (tabInstant, tabsLeft1) = tablet(skulled = false, teleblocked = false)
        run.expectTrue(e, "tablet, unskulled: teleports at once and uses one tablet", tabInstant != null && tabInstant <= 6 && tabsLeft1 == 2,
            "landed after $tabInstant ticks, tablets left $tabsLeft1")
        val (tabCounted, tabsLeft2) = tablet(skulled = true, teleblocked = false)
        run.expectTrue(e, "tablet, skulled: teleports after the 7-second countdown", tabCounted != null && tabCounted >= 10 && tabsLeft2 == 2,
            "landed after $tabCounted ticks, tablets left $tabsLeft2")
        val (tabBlocked, tabsLeft3) = tablet(skulled = false, teleblocked = true)
        run.expectTrue(e, "tablet, Tele Blocked: no teleport and the tablet is kept", tabBlocked == null && tabsLeft3 == 3,
            "landed after $tabBlocked ticks, tablets left $tabsLeft3")
    }

    // ---- guards: Deadman city guards in Varrock square --------------------------------------------------------------------

    private suspend fun QueueTask.guards(run: Conformance.Run) {
        val e = "guards"
        val zones = gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
        val guards = gg.rsmod.plugins.content.mechanics.pvp.CityGuards
        val square = Tile(3213, 3428, 0)
        if (!guards.isGuardedZone(square)) {
            run.notTested(e, "all", "$square is not a guarded zone")
            return
        }
        // The nearest walkable tile outside the zone, straight west of the square.
        val outside = (1..120).map { square.transform(-it, 0) }.firstOrNull { tile ->
            !zones.contains(tile) && !run.world.collision.isClipped(tile)
        }
        val g = run.bot(square, "GG")
        wait(2)
        val splat = Regex("type=(\\d+) dmg=(\\d+)")
        fun guardsOnG() = run.world.npcs.entries.filterNotNull().filter { guards.isGuard(it) && it.getCombatTarget() === g }
        fun hitsSince(c: Int) = run.recording.of("GG", "out").filter { it.cycle >= c && it.text.startsWith("hits ") }
            .flatMap { ev -> splat.findAll(ev.text).map { it.groupValues[2].toInt() }.toList() }
        suspend fun hold(ticks: Int) = repeat(ticks) {
            g.setCurrentLifepoints(g.getMaximumLifepoints())
            wait(1)
        }
        fun stopAll() {
            guards.resetDamageRamp(g)
            g.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
            g.attr.remove(gg.rsmod.plugins.content.mechanics.pvp.LootKeys.SLOTS)
        }

        // 1. Unskulled: left alone.
        stopAll()
        reset(g, square)
        val c1 = run.world.currentCycle
        hold(20)
        run.check(e, "unskulled in the zone: guard hits in 20 ticks", hitsSince(c1).size, "wiki: guards act only on PK-skulled players" to 0)

        // 2. Skulled: attacked, hits visible, Protect from Melee does not stop the damage.
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(g)
        g.skills.setBaseLevel(gg.rsmod.plugins.api.Skills.PRAYER, 99)
        g.setCurrentPrayerPoints(99)
        gg.rsmod.plugins.content.mechanics.prayer.Prayers.activate(g, gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_FROM_MELEE)
        val c2 = run.world.currentCycle
        var attackers = 0
        var wizguardCredited = false
        for (i in 0 until 16) {
            g.setCurrentLifepoints(g.getMaximumLifepoints())
            g.setCurrentPrayerPoints(99)
            wait(1)
            attackers = maxOf(attackers, guardsOnG().size)
            if (run.world.npcs.entries.filterNotNull().any { it.id == guards.WIZGUARD_ID && g.damageMap[it] != null }) wizguardCredited = true
        }
        val hits = hitsSince(c2)
        run.expectTrue(e, "skulled in the zone: guards attack within 16 ticks", attackers >= 1, "guards on him: $attackers")
        // Audit 2026-10-02: an uncredited Ice Barrage left a death to it without a guard killer - a safe PvM death.
        run.expectTrue(e, "skulled: the Wizguard's Ice Barrage is credited to the Wizguard (a death to it is a guard death)", wizguardCredited, "credited=$wizguardCredited")
        run.expectTrue(e, "skulled: guard hits show hitsplats", hits.isNotEmpty(), "splats=$hits")
        run.expectTrue(e, "skulled + Protect from Melee: guards still deal damage", hits.any { it > 0 }, "splats=$hits")
        gg.rsmod.plugins.content.mechanics.prayer.Prayers.deactivateAll(g)

        // 3. Leaves the zone: guards stop at once and stay inside the zone.
        if (outside == null) {
            run.notTested(e, "leaving the zone", "no walkable tile west of $square outside the zone")
        } else {
            val left = run.world.currentCycle
            g.setCurrentLifepoints(g.getMaximumLifepoints())
            g.moveTo(outside)
            wait(1)
            g.setCurrentLifepoints(g.getMaximumLifepoints())
            val still = guardsOnG().map { "${it.id}#${it.index}@${it.tile}" }
            run.expectTrue(e, "left the zone: no guard targets him a tick later", still.isEmpty(), "still: $still")
            hold(4) // an arrow or Ice Barrage already in flight may still land (projectile delay)
            run.note("hits landing in the 5 ticks after leaving: ${hitsSince(left)}")
            val c3 = run.world.currentCycle
            var strayGuard: String? = null
            for (i in 0 until 15) {
                g.setCurrentLifepoints(g.getMaximumLifepoints())
                wait(1)
                run.world.npcs.entries.filterNotNull().filter { guards.isGuard(it) && it.tile.isWithinRadius(outside, 20) }.forEach {
                    if (!zones.contains(it.tile)) strayGuard = "${it.id}@${it.tile}"
                }
            }
            run.check(e, "left the zone: guard hits afterwards", hitsSince(c3).size, "wiki: they stop the instant the player leaves" to 0)
            run.expectTrue(e, "left the zone: no guard steps outside the zone", strayGuard == null, "guard outside: $strayGuard")
        }

        // 4. Loses the skull inside the zone: guards stop.
        g.moveTo(square)
        hold(12)
        val unskulled = run.world.currentCycle
        g.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        g.setCurrentLifepoints(g.getMaximumLifepoints())
        wait(1)
        val stillOn = guardsOnG().map { "${it.id}#${it.index}" }
        run.expectTrue(e, "skull gone: no guard targets him a tick later", stillOn.isEmpty(), "still: $stillOn")
        g.setCurrentLifepoints(g.getMaximumLifepoints())
        wait(4)
        run.note("hits in the 5 ticks after the skull went: " + run.recording.of("GG", "out").filter { it.cycle >= unskulled && it.text.startsWith("hits ") }
            .joinToString { "+${it.cycle - unskulled}:${splat.findAll(it.text).joinToString("/") { m -> "t" + m.groupValues[1] + "=" + m.groupValues[2] }}" })
        val c4 = run.world.currentCycle
        hold(15)
        run.check(e, "skull gone in the zone: guard hits afterwards", hitsSince(c4).size, "wiki: they stop when the player loses the skull" to 0)

        // 5. Unskulled player attacks a guard: refused.
        stopAll()
        val target = run.world.npcs.entries.filterNotNull()
            .filter { guards.isGuard(it) && it.id != guards.WIZGUARD_ID && it.tile.isWithinRadius(square, 15) }
            .minByOrNull { it.tile.getDistance(square) }
        if (target == null) {
            run.notTested(e, "attack a guard", "no guard within 15 tiles of $square")
        } else {
            val c5 = run.world.currentCycle
            g.attack(target)
            hold(4)
            val msgs = run.recording.of("GG", "msg").filter { it.cycle >= c5 && it.text.startsWith("MessageGameMessage") }.joinToString(" / ") { it.text.take(120) }
            run.expectTrue(e, "unskulled attacks a guard: refused", msgs.contains(guards.ATTACK_REFUSED_MESSAGE), msgs)
            g.resetInteractions()
            Combat.reset(g)
        }

        // 6. Skulled player killed by guards: public ground loot, no loot key for anyone.
        stopAll()
        reset(g, square)
        g.inventory.add(Items.ARMADYL_GODSWORD, 1)
        fun keys() = run.world.players.entries.filterNotNull().sumOf { gg.rsmod.plugins.content.mechanics.pvp.LootKeys.heldKeyIndexes(it).size }
        val keysBefore = keys()
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(g)
        g.setCurrentLifepoints(5)
        var died = false
        var lastTile = g.tile
        for (i in 0 until 60) {
            wait(1)
            if (g.isDead()) died = true
            if (!died) lastTile = g.tile
            if (died && !g.isDead()) break
        }
        val found = mutableSetOf<String>()
        for (centre in listOf(lastTile, square)) for (dx in -6..6) for (dz in -6..6) {
            val tile = centre.transform(dx, dz)
            run.world.chunks.get(tile)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(tile, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                ?.forEach { if (it.item == Items.ARMADYL_GODSWORD) found += "${it.tile}#${System.identityHashCode(it)}" }
        }
        val ground = found.size
        run.note("guard kill: last tile $lastTile, godsword on the ground at $found")
        run.expectTrue(e, "killed by guards: died", died, "tile=${g.tile} last=$lastTile")
        run.check(e, "killed by guards: no loot key", keys() - keysBefore, "owner: no player killer, no key" to 0)
        run.check(e, "killed by guards: the godsword lies on the ground", ground, "owner: public ground loot" to 1)
        stopAll()

        // 7. Audit 2026-10-02: the guard that came follows a skulled intruder down to the AFK basement under the GE hall (another
        // map square, owner "safe zone"); before, it stayed upstairs "pursuing" and only the Wizguard came down.
        val basement = gg.rsmod.plugins.content.newplayer.AfkArea.BASEMENT_ARRIVAL
        reset(g, square)
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(g)
        hold(6)
        g.moveTo(basement)
        var followed: String? = null
        for (i in 0 until 12) {
            g.setCurrentLifepoints(g.getMaximumLifepoints())
            wait(1)
            followed = guardsOnG().firstOrNull { it.id != guards.WIZGUARD_ID && it.tile.isWithinRadius(g.tile, 3) }?.let { "${it.id}@${it.tile}" } ?: followed
        }
        run.expectTrue(e, "skulled in the AFK basement: a melee/ranged guard is beside him within 12 ticks", followed != null, "guard: $followed")
        stopAll()
        g.moveTo(square)
        wait(2)

        // 8. Death's Office is safe for PvP but has no guards (a private instance in OSRS, like the house).
        val office = gg.rsmod.plugins.content.areas.deathsoffice.DeathsOfficeArea.ARRIVAL
        reset(g, office)
        hold(5) // an arrow from scenario 7 still in flight lands first
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(g)
        val c8 = run.world.currentCycle
        var officeGuard: String? = null
        for (i in 0 until 14) {
            g.setCurrentLifepoints(g.getMaximumLifepoints())
            wait(1)
            run.world.npcs.entries.filterNotNull().firstOrNull { guards.isGuard(it) && it.tile.isWithinRadius(office, 20) }
                ?.let { officeGuard = "${it.id}@${it.tile}" }
        }
        run.expectTrue(e, "skulled in Death's Office: no guard or Wizguard appears", officeGuard == null, "guard: $officeGuard")
        run.check(e, "skulled in Death's Office: hits in 14 ticks", hitsSince(c8).size, "no guards in a private safe area" to 0)
        stopAll()
        reset(g, square)
    }

    // ---- single: the single-way PJ window --------------------------------------------------------------------------------

    private suspend fun QueueTask.singleWay(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "single"
        val world = run.world
        fun free(t: Tile) = (0..3).all { dx -> (0..1).all { dz -> !runCatching { world.collision.isClipped(t.transform(dx, dz)) }.getOrDefault(true) } }
        fun ok(t: Tile) = !t.isMulti(world) && !gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(t) &&
            !t.transform(3, 1).isMulti(world) && free(t)
        var spot: Tile? = null
        loop@ for (r in 8..240 step 8) for (dx in -r..r step 8) for (dz in listOf(-r, r)) {
            for (t in listOf(arena.transform(dx, dz), arena.transform(dz, dx))) if (ok(t)) { spot = t; break@loop }
        }
        val s = spot
        if (s == null) {
            run.notTested(e, "all", "no single-way dangerous spot within 240 tiles of $arena")
            return
        }
        val a = run.bot(s, "SA")
        val c = run.bot(s.transform(1, 0), "SC")
        val b = run.bot(s.transform(2, 0), "SB")
        wait(2)
        for (p in listOf(a, b, c)) reset(p, p.tile)
        wield(a, Item(Items.ABYSSAL_WHIP))
        wield(b, Item(Items.ABYSSAL_WHIP))
        // C must not fight back: A would auto-retaliate and every new swing re-arms C's PJ window.
        c.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        fun msgs(who: String, from: Int) = run.recording.of(who, "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }
            .joinToString(" / ") { it.text.substringAfter("message=").take(70) }
        fun swingsOnC(from: Int) = run.recording.of("SC", "out").count { it.cycle >= from && it.text.startsWith("hits ") }

        // A attacks C once, then stops.
        val start = world.currentCycle
        a.attack(c)
        var attackedAt = -1
        for (i in 0 until 10) {
            wait(1)
            if (a.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) { attackedAt = world.currentCycle; break }
        }
        a.resetInteractions()
        Combat.reset(a)
        c.resetInteractions()
        Combat.reset(c)
        if (attackedAt < 0) {
            run.expectTrue(e, "A attacks C", false, "no swing in 10 ticks")
            return
        }
        // 3 ticks after A's attack: B is refused.
        while (world.currentCycle < attackedAt + 3) wait(1)
        val t1 = world.currentCycle
        b.attack(c)
        wait(2)
        run.expectTrue(e, "single-way: 3 ticks after A's attack B may not attack C", msgs("SB", t1).contains("Someone is already fighting this player."),
            "B msgs: ${msgs("SB", t1)}")
        b.resetInteractions()
        Combat.reset(b)
        // Once 8 ticks (4.8 s) have passed since A's attack: B may attack C.
        while (world.currentCycle < attackedAt + Combat.PJ_TICKS + 1) wait(1)
        val t2 = world.currentCycle
        b.attack(c)
        var bSwung = false
        for (i in 0 until 6) {
            wait(1)
            c.setCurrentLifepoints(c.getMaximumLifepoints())
            if (b.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) bSwung = true
        }
        run.expectTrue(e, "single-way: 8 ticks after A's attack B may attack C (OSRS Wiki PJ timer 4.8 s; Deadman: no 12 s PvP timer)",
            bSwung && !msgs("SB", t2).contains("Someone is already fighting"), "B swung=$bSwung msgs: ${msgs("SB", t2)}")
        run.note("single: spot $s, A attacked at +${attackedAt - start}, hits on C ${swingsOnC(start)}")
        for (p in listOf(a, b, c)) { p.resetInteractions(); Combat.reset(p) }
        c.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
    }

    // ---- keys: loot keys over several real kills ---------------------------------------------------------------------------

    private suspend fun QueueTask.lootKeys(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "keys"
        val keys = gg.rsmod.plugins.content.mechanics.pvp.LootKeys
        val a = run.bot(arena, "KA")
        val b = run.bot(arena.transform(1, 0), "KB")
        wait(2)
        for (p in listOf(a, b)) {
            reset(p, p.tile)
            p.attr.remove(keys.SLOTS)
            p.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        }

        fun keyLoot(p: Player) = keys.heldKeyIndexes(p).flatMap { keys.slotItems(p, it) }.map { it.id to it.amount }

        /** [killer] kills [victim] in real combat; [victim] carries [items] (plus whatever keys he holds). Returns false when no death. */
        suspend fun kill(
            killer: Client,
            victim: Client,
            items: List<Item>,
        ): Boolean {
            for (p in listOf(killer, victim)) {
                p.resetInteractions()
                Combat.reset(p)
                p.equipment.removeAll()
                // Keys stay in the inventory; everything else goes.
                p.inventory.toList().filterNotNull().filterNot { keys.isKey(it.id) }.forEach { p.inventory.remove(it.id, it.amount) }
                p.setCurrentLifepoints(p.getMaximumLifepoints())
            }
            // A respawn teleport still pending from the previous death can land after these moves (it put the victim back home,
            // in a safe zone, for a whole kill: 2026-10-03 PID run, "five keys held" 4): place both until they really stand there.
            for (attempt in 0 until 6) {
                killer.moveTo(arena)
                victim.moveTo(arena.transform(1, 0))
                wait(2)
                if (killer.tile.sameAs(arena) && victim.tile.sameAs(arena.transform(1, 0))) break
            }
            wield(killer, Item(Items.ABYSSAL_WHIP))
            items.forEach { victim.inventory.add(it) }
            wait(1)
            victim.setCurrentLifepoints(20)
            killer.attack(victim)
            var died = false
            for (i in 0 until 150) {
                killer.setCurrentLifepoints(killer.getMaximumLifepoints())
                wait(1)
                if (victim.isDead()) died = true
                if (died && !victim.isDead()) break
            }
            killer.resetInteractions()
            Combat.reset(killer)
            wait(4)
            return died
        }

        // 1. A kills B twice: two keys, one per kill.
        kill(a, b, listOf(Item(Items.AMULET_OF_FURY)))
        kill(a, b, listOf(Item(Items.DRAGON_BOOTS)))
        run.check(e, "two kills: the killer holds two keys", keys.heldKeyIndexes(a).size, "1 key per kill" to 2)
        val aKeys = keyLoot(a)

        // 2. B kills A while A carries the two keys: B gets A's own loot in a key plus both keys' loot; A keeps no key.
        kill(b, a, listOf(Item(Items.BANDOS_TASSETS)))
        run.check(e, "killed key carrier: the killer holds 1 + 2 keys", keys.heldKeyIndexes(b).size, "own loot + every carried key" to 3)
        run.check(e, "killed key carrier: no key left on him", keys.heldKeyIndexes(a).size + keys.slots(a).count { it.isNotEmpty() }, "none" to 0)
        val expected = (aKeys + (Items.BANDOS_TASSETS to 1)).sortedBy { it.first }
        run.check(e, "killed key carrier: key loot conserved", keyLoot(b).sortedBy { it.first }.toString(), "his keys + his items" to expected.toString())

        // 3. B to five keys, then the sixth kill: the limit message and the loot on the ground for B.
        kill(b, a, listOf(Item(Items.AMULET_OF_FURY)))
        kill(b, a, listOf(Item(Items.DRAGON_BOOTS)))
        run.check(e, "five keys held", keys.heldKeyIndexes(b).size, "MAX_KEYS" to keys.MAX_KEYS)
        val c6 = run.world.currentCycle
        val deathTile = arena.transform(1, 0)
        kill(b, a, listOf(Item(Items.ARMADYL_GODSWORD)))
        val msgs = run.recording.of("KB", "msg").filter { it.cycle >= c6 && it.text.startsWith("MessageGameMessage") }.joinToString(" / ") { it.text.take(120) }
        run.check(e, "sixth kill: still five keys", keys.heldKeyIndexes(b).size, "MAX_KEYS" to keys.MAX_KEYS)
        run.expectTrue(e, "sixth kill: the limit message", msgs.contains(keys.LIMIT_MESSAGE), msgs)
        var owned = 0
        for (dx in -3..3) for (dz in -3..3) {
            val t = deathTile.transform(dx, dz)
            run.world.chunks.get(t)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                ?.forEach { if (it.item == Items.ARMADYL_GODSWORD && it.isOwnedBy(b)) owned++ }
        }
        run.check(e, "sixth kill: the godsword lies on the ground for the killer", owned, "owner 2026-09-17: ground loot when the keys are full" to 1)
        for (p in listOf(a, b)) {
            p.attr.remove(keys.SLOTS)
            keys.KEY_IDS.forEach { id -> p.inventory.remove(id, p.inventory.getItemCount(id)) }
        }
    }

    // ---- xlog: closing the client during a fight --------------------------------------------------------------------------

    private suspend fun QueueTask.xLog(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "xlog"
        val world = run.world
        fun online(p: Client) = world.players.contains(p)
        suspend fun goneWithin(p: Client, ticks: Int): Int? {
            for (i in 1..ticks) {
                wait(1)
                if (!online(p)) return i
            }
            return null
        }

        // 1. Unskulled and out of combat: the body leaves at once.
        val calm = run.bot(arena.transform(0, 3), "XC")
        wait(2)
        calm.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        calm.requestLogout()
        val calmGone = goneWithin(calm, 5)
        run.expectTrue(e, "unskulled, out of combat: X-log leaves within 3 ticks", calmGone != null && calmGone <= 3, "gone after $calmGone ticks")

        // 2. Hit by a player, then the client closes: the body stays, the attacker can still kill it and takes the loot key.
        val a = run.bot(arena, "XA")
        val v = run.bot(arena.transform(1, 0), "XV")
        wait(2)
        wield(a, Item(Items.ABYSSAL_WHIP))
        v.inventory.add(Items.AMULET_OF_FURY, 1)
        a.attack(v)
        val c2 = world.currentCycle
        for (i in 0 until 12) {
            wait(1)
            if (run.recording.of("XV", "out").any { it.cycle >= c2 && it.text.startsWith("hits ") }) break
        }
        v.requestLogout()
        wait(3)
        run.expectTrue(e, "hit by a player: the body stays after the client closed", online(v), "logged out at once")
        v.setCurrentLifepoints(10)
        var died = false
        for (i in 0 until 40) {
            a.setCurrentLifepoints(a.getMaximumLifepoints())
            wait(1)
            if (v.isDead()) died = true
            if (!online(v)) break
        }
        run.expectTrue(e, "hit by a player: the X-logged body can still be killed", died, "online=${online(v)} dead=${v.isDead()} lp=${v.getCurrentLifepoints()}")
        val keyLoot = gg.rsmod.plugins.content.mechanics.pvp.LootKeys.heldKeyIndexes(a).flatMap { gg.rsmod.plugins.content.mechanics.pvp.LootKeys.slotItems(a, it) }
        run.expectTrue(e, "hit by a player: the killer gets the X-logger's loot", keyLoot.any { it.id == Items.AMULET_OF_FURY }, "key loot=$keyLoot")
        val vGone = goneWithin(v, 30)
        run.expectTrue(e, "hit by a player: the body leaves after the death", vGone != null, "still online")
        a.resetInteractions()
        Combat.reset(a)
        a.attr.remove(gg.rsmod.plugins.content.mechanics.pvp.LootKeys.SLOTS)

        // 3. Skulled, out of combat: held for the 7-second rule (Player.requestLogout: 12 ticks), then gone.
        val s = run.bot(arena.transform(0, -3), "XS")
        wait(2)
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(s)
        s.requestLogout()
        wait(5)
        run.expectTrue(e, "skulled: the body stays after the client closed", online(s), "logged out at once")
        val sGone = goneWithin(s, 15)
        run.expectTrue(e, "skulled: the body leaves once the 7-second hold ran out", sGone != null, "still online 20 ticks later")

        // 4. The logout button (182:13) while skulled: the 7-second countdown, then out.
        val lb = run.bot(arena.transform(3, -3), "XL")
        wait(2)
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(lb)
        Bots.button(lb, 182, 13)
        wait(4)
        run.expectTrue(e, "logout button, skulled: still in the world during the countdown", online(lb), "logged out at once")
        val lbGone = goneWithin(lb, 14)
        run.expectTrue(e, "logout button, skulled: out after the countdown", lbGone != null, "still online")

        // 5. The logout button while skulled, then hit by a player during the countdown: the logout is cancelled.
        val hb = run.bot(arena.transform(4, -3), "XH")
        val hitter = run.bot(arena.transform(5, -3), "XK")
        wait(2)
        wield(hitter, Item(Items.ABYSSAL_WHIP))
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(hb)
        Bots.button(hb, 182, 13)
        wait(1)
        hitter.attack(hb)
        for (i in 0 until 20) {
            hb.setCurrentLifepoints(hb.getMaximumLifepoints())
            wait(1)
        }
        hitter.resetInteractions()
        Combat.reset(hitter)
        run.expectTrue(e, "logout button, hit during the countdown: he stays logged in", online(hb), "logged out")
    }

    // ---- pvm: deaths to an npc ---------------------------------------------------------------------------------------------

    private suspend fun QueueTask.pvmDeath(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "pvm"
        val keys = gg.rsmod.plugins.content.mechanics.pvp.LootKeys
        // A spot of its own (bots of earlier scenarios stand around the arena tile) with open ground for the three of them.
        fun open(t: Tile) = (-1..2).all { dx -> (-1..2).all { dz -> !runCatching { run.world.collision.isClipped(t.transform(dx, dz)) }.getOrDefault(true) } }
        val spot = (6..60 step 2).flatMap { r -> listOf(arena.transform(-r, r), arena.transform(r, r), arena.transform(-r, -r), arena.transform(r, -r)) }
            .firstOrNull { open(it) && !gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(it) } ?: arena.transform(-6, 6)
        val giant = run.npc(gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT, spot.transform(1, 0), "PN")
        val v = run.bot(spot, "PV")
        val a = run.bot(spot.transform(-1, 0), "PA")
        fun keyCount() = run.world.players.entries.filterNotNull().sumOf { keys.heldKeyIndexes(it).size }
        wait(2)

        suspend fun npcKills(label: String): Boolean {
            // Defence 1: the giant must land a hit well inside the 60-second PvP aggressor window (PvpSkull.AGGRESSOR_WINDOW_CYCLES).
            v.skills.setBaseLevel(Skills.DEFENCE, 1)
            v.setCurrentLifepoints(3)
            val c0 = run.world.currentCycle
            giant.attack(v)
            var died = false
            for (i in 0 until 120) {
                giant.setCurrentLifepoints(giant.getMaximumLifepoints())
                if (i % 10 == 9 && !died && giant.getCombatTarget() !== v) giant.attack(v)
                wait(1)
                if (v.isDead()) died = true
                if (died && !v.isDead()) break
            }
            giant.resetInteractions()
            Combat.reset(giant)
            wait(4)
            run.expectTrue(e, "$label: the npc killed him", died, "dead=${v.isDead()} lp=${v.getCurrentLifepoints()}")
            run.note("$label: death and respawn ${run.world.currentCycle - c0} cycles after the npc's attack order")
            v.skills.setBaseLevel(Skills.DEFENCE, 99)
            return died
        }
        fun groundNear(t: Tile, id: Int): Int {
            var n = 0
            for (dx in -3..3) for (dz in -3..3) {
                val g = t.transform(dx, dz)
                run.world.chunks.get(g)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(g, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                    ?.forEach { if (it.item == id) n++ }
            }
            return n
        }

        // 1. Killed by the npc alone: everything to the gravestone (owner 2026-09-26: PvM deaths are recoverable).
        reset(v, spot)
        giant.moveTo(spot.transform(1, 0))
        v.inventory.add(Items.ARMADYL_GODSWORD, 1)
        val tile1 = v.tile
        val keys0 = keyCount()
        val ground0 = groundNear(tile1, Items.ARMADYL_GODSWORD)
        if (npcKills("npc only")) {
            val grave = v.gravestone.toList().filterNotNull().map { it.id }
            run.expectTrue(e, "npc only: the godsword is in his gravestone", Items.ARMADYL_GODSWORD in grave, "gravestone=$grave")
            run.check(e, "npc only: nothing on the ground", groundNear(tile1, Items.ARMADYL_GODSWORD) - ground0, "gravestone, not ground loot" to 0)
            run.check(e, "npc only: no loot key", keyCount() - keys0, "no player killer" to 0)
        }
        gg.rsmod.plugins.content.mechanics.death.Gravestone.clear(v)
        v.gravestone.removeAll()

        // 2. Hit by a player first, the npc lands the kill: still a PvP death (DeathResolver: "the shared aggressor window").
        reset(v, spot)
        reset(a, spot.transform(-1, 0))
        giant.moveTo(spot.transform(1, 0))
        a.attr.remove(keys.SLOTS)
        wield(a, Item(Items.ABYSSAL_WHIP))
        v.inventory.add(Items.AMULET_OF_FURY, 1)
        a.attack(v)
        val c2 = run.world.currentCycle
        for (i in 0 until 12) {
            v.setCurrentLifepoints(v.getMaximumLifepoints())
            wait(1)
            if (run.recording.of("PV", "out").any { it.cycle >= c2 && it.text.startsWith("hits ") }) break
        }
        a.resetInteractions()
        Combat.reset(a)
        v.resetInteractions()
        Combat.reset(v)
        wait(2)
        if (npcKills("player hit, npc kill")) {
            val loot = keys.heldKeyIndexes(a).flatMap { keys.slotItems(a, it) }.map { it.id }
            run.expectTrue(e, "player hit, npc kill: the player gets the loot key", Items.AMULET_OF_FURY in loot, "key loot=$loot")
            run.expectTrue(e, "player hit, npc kill: nothing in the gravestone", v.gravestone.toList().filterNotNull().none { it.id == Items.AMULET_OF_FURY },
                "gravestone=${v.gravestone.toList().filterNotNull()}")
        }
        a.attr.remove(keys.SLOTS)
        keys.KEY_IDS.forEach { id -> a.inventory.remove(id, a.inventory.getItemCount(id)) }
        run.remove(giant)
    }

    // ---- veng: Vengeance / Vengeance Other -------------------------------------------------------------------------------

    private suspend fun QueueTask.vengeance(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "veng"
        val veng = gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance
        val a = run.bot(arena, "VA")
        val b = run.bot(arena.transform(1, 0), "VB")
        val c = run.bot(arena.transform(0, 1), "VC")
        val d = run.bot(arena.transform(1, 1), "VD")
        wait(2)
        for (p in listOf(a, b, c, d)) {
            reset(p, p.tile)
            veng.clear(p)
            p.timers.remove(gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN)
            gg.rsmod.plugins.content.magic.Spellbooks.select(p, gg.rsmod.plugins.api.Spellbook.LUNAR)
            p.inventory.add(Items.ASTRAL_RUNE, 100)
            p.inventory.add(Items.DEATH_RUNE, 100)
            p.inventory.add(Items.EARTH_RUNE, 300)
        }
        val component = gg.rsmod.plugins.content.magic.SpellbookData.VENGEANCE_OTHER.component
        fun vengeanceOther(caster: Client, target: Client) = Bots.inject(caster, gg.rsmod.game.message.impl.OpPlayerTMessage(target.index, (430 shl 16) or component, -1, 0))
        fun msgs(who: String, from: Int) = run.recording.of(who, "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }
            .joinToString(" / ") { it.text.substringAfter("message=").take(70) }

        // 1. A casts Vengeance Other on B: B is venged, the 30-second delay is A's, not B's.
        vengeanceOther(a, b)
        wait(3)
        run.expectTrue(e, "Vengeance Other: the target is venged", veng.isActive(b), "not active")
        run.expectTrue(e, "Vengeance Other: the caster gets the 30-second delay", veng.isOnCooldown(a), "no cooldown on the caster")
        run.expectTrue(e, "Vengeance Other: the target keeps his own Vengeance free", !veng.isOnCooldown(b),
            "OSRS Wiki: \"only be cast every 30 seconds by the same caster\"")

        // 2. A again at once, on C: refused by A's delay.
        val c2 = run.world.currentCycle
        vengeanceOther(a, c)
        wait(3)
        run.expectTrue(e, "the same caster within 30 seconds: refused", !veng.isActive(c) && msgs("VA", c2).contains("every 30 seconds"), msgs("VA", c2))

        // 3. D on the already venged B: refused.
        val c3 = run.world.currentCycle
        vengeanceOther(d, b)
        wait(3)
        run.expectTrue(e, "a target that is already venged: refused", !veng.isOnCooldown(d) && msgs("VD", c3).contains("already has vengeance"), msgs("VD", c3))

        // 4. C hits B: 75 % (rounded down, at least 1) comes back on C, and B's vengeance is used up.
        wield(c, Item(Items.ABYSSAL_WHIP))
        val splat = Regex("type=(\\d+) dmg=(\\d+)")
        val c4 = run.world.currentCycle
        c.attack(b)
        var dealt = -1
        for (i in 0 until 40) {
            b.setCurrentLifepoints(b.getMaximumLifepoints())
            c.setCurrentLifepoints(c.getMaximumLifepoints())
            wait(1)
            dealt = run.recording.of("VB", "out").filter { it.cycle >= c4 && it.text.startsWith("hits ") }
                .flatMap { ev -> splat.findAll(ev.text).map { it.groupValues[2].toInt() }.toList() }.firstOrNull { it > 0 } ?: -1
            if (dealt > 0) break
        }
        wait(2)
        c.resetInteractions()
        Combat.reset(c)
        b.resetInteractions()
        Combat.reset(b)
        val reflected = run.recording.of("VC", "out").filter { it.cycle >= c4 && it.text.startsWith("hits ") }
            .flatMap { ev -> splat.findAll(ev.text).filter { it.groupValues[1].toInt() == gg.rsmod.plugins.api.HitType.REFLECTED.id }.map { it.groupValues[2].toInt() }.toList() }
        if (dealt <= 0) {
            run.notTested(e, "vengeance rebound", "no damaging hit on B in 40 ticks")
        } else {
            run.check(e, "rebound on the attacker", reflected.firstOrNull(), "OSRS Wiki Vengeance: 75%, rounded down, min 1" to maxOf(1, dealt * 3 / 4))
            run.expectTrue(e, "vengeance used up by the hit", !veng.isActive(b), "still active")
        }

        // 5. Vengeance Group (spell button): everyone within 1 tile and the caster are venged, only the caster gets the delay,
        // and he cannot recast it at once.
        val group = gg.rsmod.plugins.content.magic.MagicSpells.metadataByName("Vengeance Group")
        if (group == null) {
            run.notTested(e, "Vengeance Group", "no spell button")
        } else {
            // End step 4's fight first: a hit on a freshly venged player would use his vengeance up before it is checked.
            for (p in listOf(a, b, c)) {
                p.resetInteractions()
                Combat.reset(p)
            }
            wait(4)
            for (p in listOf(a, b, c)) {
                veng.clear(p)
                p.timers.remove(gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN)
            }
            a.moveTo(arena.transform(0, 4))
            b.moveTo(arena.transform(1, 4))
            c.moveTo(arena.transform(0, 5))
            wait(2)
            Bots.button(a, group.interfaceId, group.component)
            wait(3)
            run.expectTrue(e, "Vengeance Group: caster and both neighbours venged", veng.isActive(a) && veng.isActive(b) && veng.isActive(c),
                "a=${veng.isActive(a)} b=${veng.isActive(b)} c=${veng.isActive(c)}")
            run.expectTrue(e, "Vengeance Group: only the caster gets the 30-second delay", veng.isOnCooldown(a) && !veng.isOnCooldown(b) && !veng.isOnCooldown(c),
                "a=${veng.isOnCooldown(a)} b=${veng.isOnCooldown(b)} c=${veng.isOnCooldown(c)}")
            veng.clear(b)
            val c5 = run.world.currentCycle
            Bots.button(a, group.interfaceId, group.component)
            wait(3)
            run.expectTrue(e, "Vengeance Group: no recast within 30 seconds", !veng.isActive(b) && msgs("VA", c5).contains("every 30 seconds"), msgs("VA", c5))
        }

        // 6. Entering a player-owned house clears it (OSRS Wiki "Vengeance": "when teleporting into a player-owned house").
        reset(d, arena.transform(1, 1))
        veng.activate(d)
        d.inventory.add(Items.TELEPORT_TO_HOUSE, 1)
        wait(1)
        val start = d.tile
        Bots.itemOption(d, d.inventory.getItemIndex(Items.TELEPORT_TO_HOUSE, skipAttrItems = false), "Break")
        var arrived = false
        for (i in 0 until 20) {
            wait(1)
            if (!d.tile.isWithinRadius(start, 30)) { arrived = true; break }
        }
        if (!arrived) {
            run.notTested(e, "house clears vengeance", "the house tablet did not move him (${msgs("VD", run.world.currentCycle - 25)})")
        } else {
            run.expectTrue(e, "entering the house clears vengeance", !veng.isActive(d), "still active at ${d.tile}")
        }
    }

    // ---- zones: the PvP gate (guarded zone, level bracket) ------------------------------------------------------------------

    // ---- remote: commands and remote services obey the shared gate -------------------------------------------------------

    private suspend fun QueueTask.remoteRoutes(run: Conformance.Run) {
        val e = "remote"
        val zones = gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
        val square = Tile(3213, 3428, 0)
        // A walkable death-zone tile straight west of the Varrock square (the same search the zones scenario uses).
        val danger = (1..120).map { square.transform(-it, 0) }.firstOrNull { t -> !zones.contains(t) && !run.world.collision.isClipped(t) }
        if (danger == null) {
            run.notTested(e, "all", "no walkable death-zone tile west of $square")
            return
        }
        val r = run.bot(square, "RA")
        wait(2)
        fun msgs(from: Int) = run.recording.of("RA", "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }
            .joinToString(" / ") { it.text.substringAfter("message=").take(90) }

        /** Types [command] exactly as the client sends it (ClientCheat packet) and returns what the bot was told. */
        suspend fun type(command: String): String {
            val c = run.world.currentCycle
            Bots.inject(r, gg.rsmod.game.message.impl.ClientCheatMessage(0, command))
            repeat(4) { wait(1) }
            return msgs(c)
        }

        suspend fun place(tile: Tile) {
            r.closeInterface(gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN)
            reset(r, tile)
            r.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)
            r.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
            r.timers.remove(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER)
            repeat(2) { wait(1) }
        }

        // 1. In a death zone every remote route refuses; nothing moves, nothing opens.
        place(danger)
        val dungeon = type("dungeon")
        run.expectTrue(e, "dungeon from a death zone: refused, not moved", r.tile.sameAs(danger) && dungeon.contains("from a safe zone"), "tile=${r.tile} msgs: $dungeon")
        val store = type("deadmanstore")
        run.expectTrue(e, "deadmanstore from a death zone: refused", !gg.rsmod.plugins.content.mechanics.store.StoreUi.isOpen(r) && store.contains("from a safe zone"), "msgs: $store")
        val mines = type("mines")
        run.expectTrue(
            e, "mines from a death zone: refused",
            !r.isInterfaceVisible(gg.rsmod.game.tools.importer.CasinoInterfaceImportTool.MINES_ID) && mines.contains("from a safe zone"), "msgs: $mines",
        )
        val pest = type("pestcontrolnovice")
        run.expectTrue(e, "pest control boarding from a death zone: refused", pest.contains("from a safe zone"), "msgs: $pest")

        // 2. A safe zone does not lift Tele Block.
        place(square)
        r.timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 100
        val blocked = type("dungeon")
        run.expectTrue(e, "dungeon while Tele Blocked: refused, not moved", r.tile.sameAs(square) && blocked.contains("magical force"), "tile=${r.tile} msgs: $blocked")

        // 3. The routes still work from where they are allowed.
        place(square)
        val opened = type("deadmanstore")
        run.expectTrue(e, "deadmanstore from a safe zone: opens", gg.rsmod.plugins.content.mechanics.store.StoreUi.isOpen(r), "msgs: $opened")
        place(square)
        type("dungeon")
        repeat(4) { wait(1) }
        run.expectTrue(e, "dungeon from a safe zone: travels", !r.tile.isWithinRadius(square, 64), "tile=${r.tile}")
        reset(r, square)
    }

    // ---- duel: a staked duel end to end through the native screens --------------------------------------------------------

    private suspend fun QueueTask.stakedDuel(run: Conformance.Run) {
        val e = "duel"
        val screens = gg.rsmod.plugins.content.activity.duel_arena.DuelArenaInterfaces
        val tileA = Tile(3367, 3275, 0)
        val tileB = Tile(3368, 3275, 0)
        if (!screens.inLobby(tileA) || !screens.inLobby(tileB)) {
            run.notTested(e, "all", "lobby tiles $tileA / $tileB are not in the lobby")
            return
        }
        val a = run.bot(tileA, "DA")
        val b = run.bot(tileB, "DB")
        repeat(4) { wait(1) }
        val charges = gg.rsmod.game.model.item.ItemAttribute.CHARGES
        fun probes(p: Client) = p.inventory.rawItems.count { it?.id == Items.ABYSSAL_WHIP && it.attr[charges] == 555 }

        /** Both in the lobby, A challenges B to a staked duel, B accepts by challenging back, both stake and accept the rules. */
        suspend fun duelToConfirmation(): gg.rsmod.plugins.content.activity.duel_arena.DuelArenaMatch? {
            for ((p, t) in listOf(a to tileA, b to tileB)) {
                reset(p, t)
                p.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
                p.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)
            }
            a.inventory.addPreservingAttr(Item(Items.ABYSSAL_WHIP).apply { attr[charges] = 555 }) // add(Item) would drop the charges
            b.inventory.add(Items.COINS_995, 5000)
            repeat(14) { wait(1) } // out of every earlier fight, lobby options refreshed
            Bots.inject(a, gg.rsmod.game.message.impl.OpPlayerExtraMessage(1, b.index)) // ClientProt OPPLAYER1: "Challenge" in the lobby
            repeat(3) { wait(1) }
            Bots.button(a, screens.CHALLENGE_SCREEN, screens.CHALLENGE_STAKE_BUTTONS.first())
            wait(1)
            Bots.button(a, screens.CHALLENGE_SCREEN, screens.CHALLENGE_SEND)
            wait(1)
            Bots.inject(b, gg.rsmod.game.message.impl.OpPlayerExtraMessage(1, a.index))
            repeat(3) { wait(1) }
            val match = a.getDuelMatch() ?: return null
            screens.addStake(match, a, a.inventory.getItemIndex(Items.ABYSSAL_WHIP, skipAttrItems = false), 1)
            screens.addStake(match, b, b.inventory.getItemIndex(Items.COINS_995, skipAttrItems = false), 5000)
            Bots.button(a, screens.STAKE_RULES, screens.STAKE_ACCEPT)
            Bots.button(b, screens.STAKE_RULES, screens.STAKE_ACCEPT)
            repeat(2) { wait(1) }
            return match
        }

        // 1. A normal staked duel: the fight starts with its match and stakes, a forfeit pays the winner.
        val match = duelToConfirmation()
        if (match == null) {
            run.add(Conformance.Result(e, "challenge from the lobby creates one match", Conformance.Verdict.FAIL, "a match for both", "no match"))
            return
        }
        run.expectTrue(e, "challenge from the lobby: both configure one match", b.getDuelMatch() === match, "b=${b.getDuelMatch()}")
        run.expectTrue(e, "both accepted the rules: the confirmation screen is up", match.confirming, "confirming=${match.confirming} stage=${match.stage}")
        Bots.button(a, screens.STAKE_CONFIRM, screens.STAKE_CONFIRM_ACCEPT)
        Bots.button(b, screens.STAKE_CONFIRM, screens.STAKE_CONFIRM_ACCEPT)
        repeat(2) { wait(1) }
        val stakeA = match.stakeOf(a).rawItems.count { it?.id == Items.ABYSSAL_WHIP && it.attr[charges] == 555 }
        val stakeB = match.stakeOf(b).getItemCount(Items.COINS_995)
        run.expectTrue(
            e, "confirmed: the fight starts with its match and both stakes",
            match.stage == gg.rsmod.plugins.content.activity.duel_arena.DuelStage.FIGHTING && a.getDuelMatch() === match && stakeA == 1 && stakeB == 5000,
            "stage=${match.stage} a.match=${a.getDuelMatch() != null} stakeA=$stakeA stakeB=$stakeB",
        )
        run.expectTrue(
            e, "confirmed: both players are in the arena",
            gg.rsmod.plugins.content.activity.duel_arena.DuelArenaLocations.inArena(a.tile) && gg.rsmod.plugins.content.activity.duel_arena.DuelArenaLocations.inArena(b.tile),
            "a=${a.tile} b=${b.tile}",
        )
        repeat(10) { wait(1) } // the 3-2-1 countdown
        Bots.inject(b, gg.rsmod.game.message.impl.ClientCheatMessage(0, "forfeit"))
        repeat(4) { wait(1) }
        run.check(e, "forfeit: the winner receives the loser's stake", a.inventory.getItemCount(Items.COINS_995), "rule" to 5000)
        run.check(e, "forfeit: the winner's own stake comes back with its charges", probes(a), "rule (attributes kept)" to 1)
        run.check(e, "forfeit: the loser keeps no stake", b.inventory.getItemCount(Items.COINS_995), "rule" to 0)
        run.expectTrue(e, "after the duel both are back in the lobby", screens.inLobby(a.tile) && screens.inLobby(b.tile), "a=${a.tile} b=${b.tile}")

        // 2. A skulled challenger is never moved into the arena: the duel is called off and both stakes come back.
        val second = duelToConfirmation()
        if (second == null) {
            run.add(Conformance.Result(e, "second challenge creates a match", Conformance.Verdict.FAIL, "a match", "no match"))
            return
        }
        gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.applyTestSkull(a)
        Bots.button(a, screens.STAKE_CONFIRM, screens.STAKE_CONFIRM_ACCEPT)
        Bots.button(b, screens.STAKE_CONFIRM, screens.STAKE_CONFIRM_ACCEPT)
        repeat(2) { wait(1) }
        run.expectTrue(
            e, "skulled: the fight does not start, nobody leaves the lobby",
            a.getDuelMatch() == null && screens.inLobby(a.tile) && screens.inLobby(b.tile),
            "a.match=${a.getDuelMatch() != null} a=${a.tile} b=${b.tile}",
        )
        run.check(e, "skulled: the stakes are back with their owners", probes(a) to b.inventory.getItemCount(Items.COINS_995), "rule" to (1 to 5000))
        a.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        reset(a, tileA)
        reset(b, tileB)
    }

    // ---- casino: a live casino round is at risk on death ---------------------------------------------------------------------

    private suspend fun QueueTask.casinoDeath(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "casino"
        val layout = gg.rsmod.game.tools.importer.CasinoInterfaceImportTool
        val screens = gg.rsmod.plugins.content.activity.casino.CasinoScreens
        val mines = gg.rsmod.plugins.content.activity.casino.MinesGame
        val square = Tile(3213, 3428, 0)
        val a = run.bot(arena, "CA")
        val b = run.bot(square, "CB")
        wait(2)
        reset(a, arena)
        reset(b, square)
        a.attr.remove(gg.rsmod.plugins.content.mechanics.pvp.LootKeys.SLOTS)
        b.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        b.inventory.add(Items.COINS_995, 1_000_000)
        repeat(14) { wait(1) } // out of every earlier fight

        // 1. The START button is refused in a death zone (the screen may still be open there).
        b.moveTo(arena.transform(1, 0))
        repeat(2) { wait(1) }
        gg.rsmod.plugins.content.activity.casino.CasinoScreens.Mines.open(b)
        screens.setBet(b, 200_000)
        wait(1)
        Bots.button(b, layout.MINES_ID, gg.rsmod.game.tools.importer.CasinoInterfaceImportTool.Mines.START_BUTTON)
        repeat(2) { wait(1) }
        run.expectTrue(e, "START in a death zone: no board, no coins taken", !mines.isPlaying(b) && b.inventory.getItemCount(Items.COINS_995) == 1_000_000,
            "playing=${mines.isPlaying(b)} coins=${b.inventory.getItemCount(Items.COINS_995)}")
        b.closeInterface(layout.MINES_ID)

        // 2. A board laid through the real screen in a safe zone.
        b.moveTo(square)
        repeat(2) { wait(1) }
        gg.rsmod.plugins.content.activity.casino.CasinoScreens.Mines.open(b)
        screens.setBet(b, 200_000)
        wait(1)
        Bots.button(b, layout.MINES_ID, gg.rsmod.game.tools.importer.CasinoInterfaceImportTool.Mines.START_BUTTON)
        repeat(2) { wait(1) }
        val board = mines.active(b)
        if (board == null) {
            run.add(Conformance.Result(e, "a board is laid from a safe zone", Conformance.Verdict.FAIL, "a live board", "none"))
            return
        }
        val owed = board.cashoutValue()
        val carried = b.inventory.getItemCount(Items.COINS_995)
        val bankCoins = b.bank.getItemCount(Items.COINS_995)
        b.closeInterface(layout.MINES_ID)

        // 3. Into the death zone with the open board, killed by a player.
        b.moveTo(arena.transform(1, 0))
        wield(a, Item(Items.ABYSSAL_WHIP))
        repeat(2) { wait(1) }
        val deathTile = b.tile
        b.setCurrentLifepoints(30)
        a.attack(b)
        var died = false
        for (t in 0 until 150) {
            a.setCurrentLifepoints(a.getMaximumLifepoints())
            wait(1)
            if (b.isDead()) died = true
            if (died && !b.isDead() && b.tile != deathTile) break
        }
        a.resetInteractions()
        Combat.reset(a)
        wait(2)
        run.expectTrue(e, "the player with the open board died and respawned", died && !b.isDead(), "dead=${b.isDead()} tile=${b.tile}")
        run.expectTrue(e, "the board did not survive the death", !mines.isPlaying(b), "playing=${mines.isPlaying(b)}")
        val keyCoins = gg.rsmod.plugins.content.mechanics.pvp.LootKeys.heldKeyIndexes(a)
            .flatMap { gg.rsmod.plugins.content.mechanics.pvp.LootKeys.slotItems(a, it) }
            .filter { it.id == Items.COINS_995 }.sumOf { it.amount.toLong() }
        var groundCoins = 0L
        for (dx in -1..1) for (dz in -1..1) {
            val t = deathTile.transform(dx, dz)
            run.world.chunks.get(t)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                ?.filter { it.item == Items.COINS_995 }?.forEach { groundCoins += it.amount }
        }
        run.check(e, "the killer's loot holds the carried coins and the board's value", keyCoins + groundCoins, "rule: carried + cash-out value" to carried + owed)
        run.check(e, "the victim kept no casino coins (inventory and bank)",
            b.inventory.getItemCount(Items.COINS_995).toLong() + b.bank.getItemCount(Items.COINS_995) - bankCoins, "rule" to 0L)
        reset(a, arena)
        reset(b, square)
    }

    private suspend fun QueueTask.pvpGate(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "zones"
        val zones = gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
        val square = Tile(3213, 3428, 0)
        // A dangerous tile right next to a guarded one, straight west of the Varrock square.
        val border = (1..120).map { square.transform(-it, 0) }.firstOrNull { t ->
            !zones.contains(t) && !run.world.collision.isClipped(t) && zones.contains(t.transform(1, 0)) && !run.world.collision.isClipped(t.transform(1, 0))
        }
        val a = run.bot(arena, "ZA")
        val b = run.bot(arena.transform(1, 0), "ZB")
        wait(2)
        wield(a, Item(Items.ABYSSAL_WHIP))
        fun msgs(from: Int) = run.recording.of("ZA", "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }
            .joinToString(" / ") { it.text.substringAfter("message=").take(80) }
        suspend fun tryAttack(): Pair<Boolean, String> {
            val c = run.world.currentCycle
            a.attack(b)
            var swung = false
            for (i in 0 until 6) {
                b.setCurrentLifepoints(b.getMaximumLifepoints())
                wait(1)
                if (a.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) swung = true
            }
            a.resetInteractions()
            Combat.reset(a)
            b.resetInteractions()
            Combat.reset(b)
            wait(2)
            return swung to msgs(c)
        }

        // 1. B inside the guarded zone, A on the dangerous tile next to it: refused with the guards' message.
        if (border == null) {
            run.notTested(e, "safe zone", "no walkable border tile west of $square")
        } else {
            a.moveTo(border)
            b.moveTo(border.transform(1, 0))
            wait(2)
            val (swung, said) = tryAttack()
            run.expectTrue(e, "target inside a guarded zone: refused with the guards' message",
                !swung && said.contains(gg.rsmod.plugins.content.mechanics.pvp.AreaState.SAFE_ZONE_ATTACK_MESSAGE), "swung=$swung msgs: $said")
        }

        // 2. Both in danger, B 15+ combat levels lower: refused (Deadman bracket +/-14).
        a.moveTo(arena)
        b.moveTo(arena.transform(1, 0))
        val combat = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC, Skills.PRAYER, Skills.SUMMONING)
        combat.forEach { b.skills.setBaseLevel(it, 60) }
        b.calculateAndSetCombatLevel()
        a.calculateAndSetCombatLevel()
        wait(2)
        val (swung2, said2) = tryAttack()
        run.expectTrue(e, "level gap ${a.combatLevel - b.combatLevel} > 14: refused",
            !swung2 && said2.contains("level difference"), "A=${a.combatLevel} B=${b.combatLevel} swung=$swung2 msgs: $said2")

        // 3. Control: same levels, both in danger - the attack goes out.
        combat.forEach { b.skills.setBaseLevel(it, 99) }
        b.calculateAndSetCombatLevel()
        wait(2)
        val (swung3, said3) = tryAttack()
        run.expectTrue(e, "same bracket, both in danger: the attack goes out", swung3, "A=${a.combatLevel} B=${b.combatLevel} msgs: $said3")
    }

    // ---- teleblock: Tele Block on a player --------------------------------------------------------------------------------

    private suspend fun QueueTask.teleBlock(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "teleblock"
        val tb = gg.rsmod.game.model.timer.TELEBLOCK_TIMER
        val a = run.bot(arena, "TA")
        val b = run.bot(arena.transform(3, 0), "TB")
        wait(2)

        /** Casts Tele Block on B until it lands (a splash is normal); the timer left the tick it is seen, or null. */
        suspend fun castUntilBlocked(): Int? {
            for (cast in 0 until 8) {
                gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(a, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.TELEPORT_BLOCK, b)
                a.attack(b)
                for (t in 0 until 10) {
                    b.setCurrentLifepoints(b.getMaximumLifepoints())
                    wait(1)
                    if (b.timers.has(tb)) {
                        val left = b.timers[tb]
                        a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
                        return left
                    }
                }
                a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
                wait(6)
            }
            return null
        }

        suspend fun prepare(protectMagic: Boolean) {
            reset(a, arena)
            reset(b, arena.transform(3, 0))
            b.timers.remove(tb)
            gg.rsmod.plugins.content.mechanics.prayer.Prayers.deactivateAll(b)
            gg.rsmod.plugins.content.magic.Spellbooks.select(a, gg.rsmod.plugins.api.Spellbook.STANDARD)
            a.inventory.add(Items.LAW_RUNE, 100)
            a.inventory.add(Items.CHAOS_RUNE, 100)
            a.inventory.add(Items.DEATH_RUNE, 100)
            if (protectMagic) {
                b.skills.setBaseLevel(Skills.PRAYER, 99)
                b.setCurrentPrayerPoints(99)
                gg.rsmod.plugins.content.mechanics.prayer.Prayers.activate(b, gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_FROM_MAGIC)
            }
            wait(2)
        }

        prepare(protectMagic = false)
        val full = castUntilBlocked()
        run.expectTrue(e, "Tele Block lasts 2:30 in Deadman (250 ticks)", full != null && full in 245..250, "timer=$full", "OSRS Deadman Autumn 2018: 150 seconds (N39)")
        // A second Tele Block on a blocked player does not refresh it.
        val before = b.timers[tb]
        if (full != null) {
            gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(a, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.TELEPORT_BLOCK, b)
            a.attack(b)
            repeat(8) { b.setCurrentLifepoints(b.getMaximumLifepoints()); wait(1) }
            a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
            run.expectTrue(e, "a second Tele Block does not refresh the timer", b.timers.has(tb) && b.timers[tb] < before, "before=$before now=${b.timers[tb]}")
        }

        prepare(protectMagic = true)
        val halved = castUntilBlocked()
        run.expectTrue(e, "Tele Block with Protect from Magic lasts 1:15 in Deadman (125 ticks)", halved != null && halved in 120..125, "timer=$halved", "OSRS Deadman Autumn 2018: 75 seconds (N39)")
        b.timers.remove(tb)
        gg.rsmod.plugins.content.mechanics.prayer.Prayers.deactivateAll(b)
    }

    // ---- phantom: Morrigan's javelin Phantom Strike bleed -----------------------------------------------------------------

    private suspend fun QueueTask.phantomStrike(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "phantom"
        val a = run.bot(arena.transform(-3, -6), "MA")
        val b = run.bot(arena.transform(0, -6), "MB")
        wait(2)
        reset(a, arena.transform(-3, -6))
        reset(b, arena.transform(0, -6))
        wield(a, Item(Items.MORRIGANS_JAVELIN, 200))
        val splat = Regex("type=(\\d+) dmg=(\\d+)")
        fun hitsOnB(from: Int) = run.recording.of("MB", "out").filter { it.cycle >= from && it.text.startsWith("hits ") }
            .flatMap { ev -> splat.findAll(ev.text).map { Triple(ev.cycle, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList() }
        var special = 0
        var c = 0
        for (attempt in 0 until 15) {
            AttackTab.setEnergy(a, 100)
            a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
            a.attack(b)
            // The special is the throw that spends the energy; stop A right there so no normal throw follows.
            var spentAt = -1
            for (t in 0 until 8) {
                b.setCurrentLifepoints(b.getMaximumLifepoints())
                wait(1)
                if (AttackTab.getEnergy(a) < 100) { spentAt = run.world.currentCycle; break }
            }
            a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
            if (spentAt >= 0) {
                c = spentAt - 1
                repeat(3) { b.setCurrentLifepoints(b.getMaximumLifepoints()); wait(1) }
                special = hitsOnB(c).firstOrNull { it.second == gg.rsmod.plugins.api.HitType.RANGE.id }?.third ?: 0
            }
            // A bleed worth measuring: at least 8 (6 of bleed); a smaller hit waits for its bleed to end and tries again.
            if (special >= 8) break
            special = 0
            repeat(6) { b.setCurrentLifepoints(b.getMaximumLifepoints()); wait(1) }
        }
        if (special <= 0) {
            run.notTested(e, "bleed", "no special hit of 8+ in 15 attempts")
            return
        }
        // Collect the bleed: regular-hit splats after the special.
        repeat(16) {
            b.setCurrentLifepoints(b.getMaximumLifepoints())
            wait(1)
        }
        val specialCycle = hitsOnB(c).first { it.second == gg.rsmod.plugins.api.HitType.RANGE.id && it.third == special }.first
        val bleed = hitsOnB(specialCycle).filter { it.second == gg.rsmod.plugins.api.HitType.REGULAR_HIT.id }
        run.check(e, "bleed total after a $special special hit", bleed.sumOf { it.third }, "OSRS Wiki: 75% of the special's damage" to special * 3 / 4)
        run.expectTrue(e, "bleed splats of 10 (the last one the rest)", bleed.dropLast(1).all { it.third == 10 } && (bleed.lastOrNull()?.third ?: 0) in 1..10,
            "bleed=${bleed.map { it.third }}")
        run.expectTrue(e, "one bleed splat per tick", bleed.zipWithNext().all { (x, y) -> y.first - x.first == 1 }, "cycles=${bleed.map { it.first - specialCycle }}")
        val said = run.recording.of("MB", "msg").filter { it.cycle >= specialCycle }.map { it.text }
        val starts = said.count { it.contains("You start to bleed as a result of the javelin strike.") }
        val continues = said.count { it.contains("You continue to bleed as a result of the javelin strike.") }
        run.expectTrue(e, "bleed messages: one 'start', then 'continue' per later splat", starts == 1 && continues == bleed.size - 1,
            "start=$starts continue=$continues splats=${bleed.size}", "OSRS Wiki Morrigan's javelin (Deadman Mode)")
    }

    // ---- dupe: dropping the loot on the tick of the killing blow ---------------------------------------------------------

    private suspend fun QueueTask.dropOnDeath(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "dupe"
        val keys = gg.rsmod.plugins.content.mechanics.pvp.LootKeys
        val spot = arena.transform(6, -6)
        val a = run.bot(spot, "UA")
        val b = run.bot(spot.transform(1, 0), "UB")
        wait(2)
        fun agsCount(): Int {
            var n = 0
            for (p in listOf(a, b)) {
                n += (p.inventory.toList() + p.equipment.toList()).filterNotNull().count { it.id == Items.ARMADYL_GODSWORD }
                n += keys.heldKeyIndexes(p).flatMap { keys.slotItems(p, it) }.count { it.id == Items.ARMADYL_GODSWORD }
            }
            for (dx in -4..4) for (dz in -4..4) {
                val t = spot.transform(dx, dz)
                run.world.chunks.get(t)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                    ?.forEach { if (it.item == Items.ARMADYL_GODSWORD) n += it.amount }
            }
            return n
        }
        for (round in 1..3) {
            for (p in listOf(a, b)) {
                reset(p, if (p === a) spot else spot.transform(1, 0))
                p.attr.remove(keys.SLOTS)
            }
            wield(a, Item(Items.ABYSSAL_WHIP))
            repeat(20) { b.inventory.add(Items.ARMADYL_GODSWORD, 1) }
            wait(1)
            val before = agsCount()
            b.skills.setBaseLevel(Skills.DEFENCE, 1)
            b.setCurrentLifepoints(6)
            a.attack(b)
            var died = false
            for (i in 0 until 60) {
                a.setCurrentLifepoints(a.getMaximumLifepoints())
                // B tries to drop the godsword every tick, so one try falls on the killing blow's tick.
                val slot = b.inventory.getItemIndex(Items.ARMADYL_GODSWORD, skipAttrItems = false)
                if (slot >= 0) Bots.itemOption(b, slot, "Drop")
                wait(1)
                if (b.isDead()) died = true
                if (died && !b.isDead()) break
            }
            a.resetInteractions(); Combat.reset(a)
            wait(8) // ground loot appears after the death animation
            run.check(e, "round $round: godswords after the fight (inventory + ground + keys)", agsCount(), "before the death" to before)
            b.skills.setBaseLevel(Skills.DEFENCE, 99)
            // Leave no godsword behind for the next round.
            for (dx in -4..4) for (dz in -4..4) {
                val t = spot.transform(dx, dz)
                run.world.chunks.get(t)?.getEntities<gg.rsmod.game.model.entity.GroundItem>(t, gg.rsmod.game.model.EntityType.GROUND_ITEM)
                    ?.filter { it.item == Items.ARMADYL_GODSWORD }?.forEach { run.world.remove(it) }
            }
            a.attr.remove(keys.SLOTS)
            keys.KEY_IDS.forEach { id -> a.inventory.remove(id, a.inventory.getItemCount(id)) }
        }
    }

    // ---- skull: who is skulled, retaliation, the reset and the pause ------------------------------------------------------

    private suspend fun QueueTask.skulls(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "skull"
        val skull = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
        val timer = gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
        val a = run.bot(arena.transform(-2, 3), "SA")
        val b = run.bot(arena.transform(0, 3), "SB")
        val c = run.bot(arena.transform(2, 3), "SC")
        wait(2)
        fun clear(vararg ps: Client) = ps.forEach {
            it.timers.remove(timer)
            it.timers.remove(gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER)
            it.attr.remove(gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR)
        }
        fun stop(vararg ps: Client) = ps.forEach { it.resetInteractions(); Combat.reset(it) }
        /** [p] attacks [t] until one hitsplat lands on [t] (or [ticks] pass); true when one did. */
        suspend fun hitOnce(p: Client, t: Client, ticks: Int = 8): Boolean {
            val from = run.world.currentCycle
            p.attack(t)
            for (i in 0 until ticks) {
                t.setCurrentLifepoints(t.getMaximumLifepoints())
                wait(1)
                if (hitTypes(run, if (t === a) "SA" else if (t === b) "SB" else "SC", from).isNotEmpty()) {
                    stop(p, t)
                    return true
                }
            }
            stop(p, t)
            return false
        }
        reset(a, arena.transform(-2, 3))
        reset(b, arena.transform(0, 3))
        reset(c, arena.transform(2, 3))
        clear(a, b, c)

        // 1. No hitsplat, no skull: a dark bow without arrows never fires (owner 2026-09-18).
        wield(a, Item(Items.DARK_BOW))
        a.attack(b)
        repeat(6) { wait(1) }
        stop(a, b)
        run.expectTrue(e, "an attack that never fires (no ammo): no skull", !skull.isSkulled(a), "skulled")

        // 2. The first hitsplat on an unskulled player skulls the attacker for 5:00; the victim stays clean.
        wield(a, Item(Items.ABYSSAL_WHIP))
        wield(b, Item(Items.ABYSSAL_WHIP))
        wield(c, Item(Items.ABYSSAL_WHIP))
        val landed = hitOnce(a, b)
        run.expectTrue(e, "the attacker is skulled when his hit lands", landed && skull.isSkulled(a), "landed=$landed")
        run.expectTrue(e, "the skull lasts 5:00 (owner 2026-09-16)", a.timers[timer] in 490..500, "timer=${a.timers[timer]}")
        run.expectTrue(e, "the victim is not skulled", !skull.isSkulled(b), "skulled")

        // 3. The victim hits back: a retaliation, no skull (OSRS Wiki "Skull (status)").
        val back = hitOnce(b, a)
        run.expectTrue(e, "the victim retaliates without a skull", back && !skull.isSkulled(b), "landed=$back skulled=${skull.isSkulled(b)}")

        // 4. A third player attacks the skulled attacker, who never attacked him: that is a fresh attack, he is skulled.
        wait(12) // the single-way PJ timer of the A-B fight runs out
        val third = hitOnce(c, a)
        run.expectTrue(e, "a third player attacking the skulled one is skulled", third && skull.isSkulled(c), "landed=$third skulled=${skull.isSkulled(c)}")

        // 5. Standing on one tile for a minute pauses the countdown; moving starts it again.
        clear(a)
        stop(a, b, c)
        skull.applyTestSkull(a)
        repeat(105) { wait(1) }
        val stalled = a.timers[timer]
        repeat(20) { wait(1) }
        val later = a.timers[timer]
        run.expectTrue(e, "standing still for a minute pauses the skull", stalled == later && stalled in 390..410, "after 105 ticks=$stalled, 20 ticks later=$later")
        a.moveTo(a.tile.transform(0, 1))
        repeat(10) { wait(1) }
        run.expectTrue(e, "moving again resumes the countdown", a.timers[timer] < later, "was $later now ${a.timers[timer]}")
        clear(a, b, c)
    }

    // ---- hamstring: Morrigan's throwing axe special -------------------------------------------------------------------

    private suspend fun QueueTask.hamstring(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "hamstring"
        val start = arena.transform(0, -6)
        val a = run.bot(arena.transform(-3, -6), "HA")
        val b = run.bot(start, "HB")
        wait(2)
        reset(a, arena.transform(-3, -6))
        reset(b, start)
        wield(a, Item(Items.MORRIGANS_THROWING_AXE, 100))
        var thrown = false
        var spent = 0
        var specialDelay = -1
        var thrownFrom = -1
        for (attempt in 0 until 6) {
            AttackTab.setEnergy(a, 100)
            a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
            val c0 = run.world.currentCycle
            a.attack(b)
            for (t in 0 until 8) {
                b.setCurrentLifepoints(b.getMaximumLifepoints())
                wait(1)
                if (AttackTab.getEnergy(a) < 100) {
                    thrown = true
                    spent = 100 - AttackTab.getEnergy(a)
                    specialDelay = if (a.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) a.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] else 0
                    thrownFrom = c0
                    break
                }
            }
            a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
            if (thrown) break
            wait(4)
        }
        if (!thrown) {
            run.notTested(e, "hamstring", "no special thrown in 6 attempts")
            return
        }
        val left = b.timers[gg.rsmod.game.model.timer.HAMSTRING_TIMER]
        run.expectTrue(e, "the target is hamstrung for a minute", b.attr.has(gg.rsmod.game.model.attr.HAMSTRING) && left in 90..100, "timer=$left",
            "OSRS Wiki Morrigan's throwing axe: for the next minute")
        run.expectTrue(e, "the target is told", run.recording.of("HB", "msg").any { it.text.contains("You've been hamstrung! For the next minute, your run energy will drain 6x faster.") },
            "no message", "OSRS Wiki Morrigan's throwing axe (Deadman Mode)")

        /** Run energy B spends on 5 ticks of running from [from] towards [to]. */
        suspend fun drainOver(from: Tile, to: Tile): Double {
            b.moveTo(from)
            wait(1)
            b.runEnergy = 100.0
            b.setVarp(gg.rsmod.plugins.api.cfg.Varps.RUN_STATE, 1)
            b.walkTo(to)
            repeat(5) { wait(1) }
            val spent = 100.0 - b.runEnergy
            b.stopMovement()
            return spent
        }
        val hamstrung = drainOver(start, start.transform(0, 14))
        b.attr.remove(gg.rsmod.game.model.attr.HAMSTRING)
        b.timers.remove(gg.rsmod.game.model.timer.HAMSTRING_TIMER)
        val normal = drainOver(start, start.transform(0, 14))
        val ratio = if (normal > 0) hamstrung / normal else 0.0
        run.expectTrue(e, "hamstrung run energy drains 6x faster", ratio in 5.5..6.5, "hamstrung=%.2f normal=%.2f ratio=%.2f".format(hamstrung, normal, ratio),
            "OSRS Wiki Morrigan's throwing axe: drain 6x faster")
        b.setVarp(gg.rsmod.plugins.api.cfg.Varps.RUN_STATE, 0)

        // Deadman Mode variant (owner 2026-10-01): cost, damage window, the faster next attack.
        run.check(e, "Hamstring costs 25% special energy", spent, "OSRS Wiki Morrigan's throwing axe (Deadman Mode)" to 25)
        repeat(3) { b.setCurrentLifepoints(b.getMaximumLifepoints()); wait(1) }
        val baseMax = gg.rsmod.plugins.content.combat.formula.RangedCombatFormula.getMaxHit(a, b, specialAttackMultiplier = 1.0)
        val specHit = hitsOn(run, "HB", thrownFrom).firstOrNull { it.second == gg.rsmod.plugins.api.HitType.RANGE.id }?.third
        if (specHit == null || specHit == 0) {
            run.notTested(e, "Hamstring damage 20-120% / next attack 1 tick faster", "the special missed")
        } else {
            val low = kotlin.math.ceil(baseMax * 0.2 - 1e-9).toInt()
            val high = kotlin.math.floor(baseMax * 1.2 + 1e-9).toInt()
            run.expectTrue(e, "Hamstring damage within 20-120% of the max hit", specHit in low..high, "hit $specHit, window $low..$high (max $baseMax)")
            // A normal throw sampled the same way (the first tick after the throw) is the reference.
            wait(8)
            a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 0)
            var normalDelay = -1
            fun delayLeft() = if (a.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) a.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] else 0
            var prev = delayLeft()
            a.attack(b)
            for (t in 0 until 10) {
                b.setCurrentLifepoints(b.getMaximumLifepoints())
                wait(1)
                val now = delayLeft()
                if (now > prev) { normalDelay = now; break }
                prev = now
            }
            a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
            if (normalDelay < 0) run.notTested(e, "next attack 1 tick faster", "no normal throw seen")
            else run.check(e, "a landed Hamstring makes the next attack 1 tick faster", specialDelay, "OSRS Wiki 12 March 2025 update (normal throw $normalDelay)" to normalDelay - 1)
        }
    }

    // ---- corrupt: Morrigan's javelin and throwing axe carry their special (owner 2026-10-01, Novite). Owner 2026-10-11 (OSRS
    // Deadman Mode, no corrupt versions): the corrupt copies became the normal items (DeadmanGear), so the normal ones are measured.

    private suspend fun QueueTask.corruptSpecials(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "corrupt"
        val a = run.bot(arena.transform(-3, -6), "KA")
        val b = run.bot(arena.transform(0, -6), "KB")
        wait(2)
        for ((weapon, cost) in listOf(Items.MORRIGANS_THROWING_AXE to 25, Items.MORRIGANS_JAVELIN to 50)) {
            reset(a, arena.transform(-3, -6))
            reset(b, arena.transform(0, -6))
            wield(a, Item(weapon, 100))
            var spent = 0
            for (attempt in 0 until 4) {
                AttackTab.setEnergy(a, 100)
                a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
                a.attack(b)
                for (t in 0 until 8) {
                    b.setCurrentLifepoints(b.getMaximumLifepoints())
                    wait(1)
                    if (AttackTab.getEnergy(a) < 100) { spent = 100 - AttackTab.getEnergy(a); break }
                }
                a.resetInteractions(); Combat.reset(a); b.resetInteractions(); Combat.reset(b)
                if (spent > 0) break
                wait(4)
            }
            run.check(e, "item $weapon performs its special", spent, "Novite PlayerCombat: Morrigan's thrown weapons' special" to cost)
            b.attr.remove(gg.rsmod.game.model.attr.HAMSTRING)
            b.attr.remove(gg.rsmod.game.model.attr.PHANTOM_STRIKE_BLEED)
            wait(12)
        }
    }

    // ---- vine: Abyssal vine whip Vine Call --------------------------------------------------------------------------------------

    private suspend fun QueueTask.vineCall(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "vine"
        val a = run.bot(arena.transform(-2, -8), "WA")
        val b = run.bot(arena.transform(-1, -8), "WB")
        wait(2)
        reset(a, arena.transform(-2, -8))
        reset(b, arena.transform(-1, -8))
        for (p in listOf(a, b)) p.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        wield(a, Item(Items.ABYSSAL_VINE_WHIP))
        var from = -1
        for (attempt in 0 until 4) {
            AttackTab.setEnergy(a, 100)
            a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
            val c0 = run.world.currentCycle
            a.attack(b)
            for (t in 0 until 6) {
                b.setCurrentLifepoints(b.getMaximumLifepoints())
                wait(1)
                if (AttackTab.getEnergy(a) < 100) { from = c0; break }
            }
            a.resetInteractions(); Combat.reset(a)
            if (from >= 0) break
        }
        if (from < 0) {
            run.notTested(e, "Vine Call", "no special in 4 attempts")
            return
        }
        run.check(e, "Vine Call costs 60%", 100 - AttackTab.getEnergy(a), "Darkan / Novite" to 60)
        repeat(14) { b.setCurrentLifepoints(b.getMaximumLifepoints()); wait(1) }
        b.resetInteractions(); Combat.reset(b)
        // Every roll of the call shows a splat: a landed melee hit (type 0) or a miss (block, type 8).
        val rolls = hitsOn(run, "WB", from).filter { it.second == gg.rsmod.plugins.api.HitType.MELEE.id || it.second == gg.rsmod.plugins.api.HitType.BLOCK.id }
        val melee = rolls.filter { it.second == gg.rsmod.plugins.api.HitType.MELEE.id }
        val cap = kotlin.math.floor(gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula.getMaxHit(a, b, specialAttackMultiplier = 0.33) + 1e-9).toInt()
        run.check(e, "11 rolls (the first + 10 vine hits)", rolls.size, "Darkan pre-EoC wiki" to 11)
        run.expectTrue(e, "one vine hit per tick", rolls.drop(1).zipWithNext().all { (x, y) -> y.first - x.first <= 1 }, "cycles ${rolls.map { it.first - from }}", "Novite 667: every tick")
        run.expectTrue(e, "every hit at most 33% of the max hit", melee.all { it.third <= cap }, "hits ${melee.map { it.third }} cap $cap")

        // Unwielding during the call stops it with the message.
        wait(4)
        AttackTab.setEnergy(a, 100)
        a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
        val c1 = run.world.currentCycle
        a.attack(b)
        for (t in 0 until 6) {
            b.setCurrentLifepoints(b.getMaximumLifepoints())
            wait(1)
            if (AttackTab.getEnergy(a) < 100) break
        }
        a.resetInteractions(); Combat.reset(a)
        wield(a, Item(Items.ABYSSAL_WHIP))
        repeat(3) { b.setCurrentLifepoints(b.getMaximumLifepoints()); wait(1) }
        run.expectTrue(e, "unwielded: the vine ignores the call",
            run.recording.of("WA", "msg").any { it.cycle >= c1 && it.text.contains("Since you no longer wield a vine whip, the vine ignores your call.") },
            "no message", "Darkan pre-EoC wiki")
        b.resetInteractions(); Combat.reset(b)
        for (p in listOf(a, b)) p.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
    }

    /** (cycle, hit type, damage) of every hitsplat on [who] since [from]. */
    private fun hitsOn(
        run: Conformance.Run,
        who: String,
        from: Int,
    ): List<Triple<Int, Int, Int>> {
        val splat = Regex("type=(\\d+) dmg=(\\d+)")
        return run.recording.of(who, "out").filter { it.cycle >= from && it.text.startsWith("hits ") }
            .flatMap { ev -> splat.findAll(ev.text).map { Triple(ev.cycle, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList() }
    }

    // ---- aoe: secondary victims of an area attack pass the PvP gate --------------------------------------------------------

    private suspend fun QueueTask.areaAttacks(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "aoe"
        val at = arena.transform(0, -4)
        val a = run.bot(at, "VA")
        val b = run.bot(at.transform(0, 1), "VB")
        val c = run.bot(at.transform(0, -1), "VC")
        val d = run.bot(at.transform(1, 0), "VD")
        // A Deadman guard outside its zone (never attackable there) and a banker (no Attack option), both next to A and B.
        val guard = run.npc(gg.rsmod.plugins.content.mechanics.pvp.CityGuards.MELEE_GUARD_ID, at.transform(-1, 1), "VG")
        val banker = run.npc(gg.rsmod.plugins.api.cfg.Npcs.BANKER_CLASSIC_MALE_PURPLE_44, at.transform(1, 1), "VN")
        wait(2)
        if (!at.isMulti(run.world)) {
            run.notTested(e, "spear wall", "$at is not multi-way")
            return
        }
        reset(a, at)
        reset(b, at.transform(0, 1))
        reset(c, at.transform(0, -1))
        reset(d, at.transform(1, 0))
        val combat = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC, Skills.PRAYER, Skills.SUMMONING)
        combat.forEach { c.skills.setBaseLevel(it, 60) }
        c.calculateAndSetCombatLevel()
        wield(a, Item(Items.VESTAS_SPEAR))
        var from = -1
        for (attempt in 0 until 4) {
            AttackTab.setEnergy(a, 100)
            a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
            val c0 = run.world.currentCycle
            a.attack(b)
            for (t in 0 until 6) {
                listOf(b, c, d).forEach { it.setCurrentLifepoints(it.getMaximumLifepoints()) }
                wait(1)
                if (AttackTab.getEnergy(a) < 100) { from = c0; break }
            }
            if (from >= 0) break
            a.resetInteractions(); Combat.reset(a)
        }
        repeat(3) { listOf(b, c, d).forEach { it.setCurrentLifepoints(it.getMaximumLifepoints()) }; wait(1) }
        a.resetInteractions(); Combat.reset(a); listOf(b, c, d).forEach { it.resetInteractions(); Combat.reset(it) }
        if (from < 0) {
            run.notTested(e, "spear wall", "no special in 4 attempts")
            return
        }
        run.expectTrue(e, "Spear Wall: the adjacent player in the level bracket is hit", hitTypes(run, "VD", from).isNotEmpty(), "no hitsplat on VD")
        run.expectTrue(e, "Spear Wall: the adjacent player ${a.combatLevel - c.combatLevel} levels lower (outside +/-14) is not hit",
            hitTypes(run, "VC", from).isEmpty(), "VC hit: ${hitTypes(run, "VC", from)}", "AreaState.canPlayersFight (Deadman bracket)")
        run.expectTrue(e, "Spear Wall: a guard the attacker may not attack is not hit", hitTypes(run, "VG", from).isEmpty(), "VG hit: ${hitTypes(run, "VG", from)}",
            "OSRS Wiki Guard (Deadman Mode): only attackable inside the guarded area while skulled")
        run.expectTrue(e, "Spear Wall: an npc without an Attack option is not hit", hitTypes(run, "VN", from).isEmpty(), "VN hit: ${hitTypes(run, "VN", from)}")

        // The same gate for a spell splash. A splash only takes victims of the primary's kind, so the primary is an npc here: an
        // Ice Barrage on a man next to the guard and the banker, from an unskulled A; a second man (VW) witnesses the splash.
        a.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        val man = run.npc(1, at.transform(0, 2), "VM")
        run.npc(1, at.transform(1, 2), "VW")
        // A Slayer monster in the splash (owner 2026-10-01: a splash skips what the attacker may not attack, Slayer level included).
        val slayerMonster = run.npc(gg.rsmod.plugins.api.cfg.Npcs.ABYSSAL_DEMON, at.transform(-1, 3), "VS")
        a.skills.setBaseLevel(Skills.SLAYER, 1)
        b.moveTo(at.transform(-2, -2))
        wait(12)
        gg.rsmod.plugins.content.magic.Spellbooks.select(a, gg.rsmod.plugins.api.Spellbook.ANCIENT)
        a.inventory.add(Items.WATER_RUNE, 100)
        a.inventory.add(Items.BLOOD_RUNE, 100)
        a.inventory.add(Items.DEATH_RUNE, 100)
        val cast = run.world.currentCycle
        var barraged = false
        for (attempt in 0 until 8) {
            gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(a, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.ICE_BARRAGE, man)
            a.attack(man)
            repeat(6) { man.setCurrentLifepoints(man.getMaximumLifepoints()); wait(1) }
            a.resetInteractions(); Combat.reset(a)
            if (hitTypes(run, "VW", cast).isNotEmpty()) { barraged = true; break }
        }
        if (!barraged) {
            run.notTested(e, "barrage splash", "no barrage splashed VW in 8 casts")
        } else {
            run.expectTrue(e, "Ice Barrage splash: a guard the attacker may not attack is not hit", hitTypes(run, "VG", cast).isEmpty(), "VG hit: ${hitTypes(run, "VG", cast)}")
            run.expectTrue(e, "Ice Barrage splash: an npc without an Attack option is not hit", hitTypes(run, "VN", cast).isEmpty(), "VN hit: ${hitTypes(run, "VN", cast)}")
            if (slayerMonster.combatDef.slayerReq <= 1) {
                run.notTested(e, "splash Slayer gate", "npc ${slayerMonster.id} has no Slayer requirement in its combat def")
            } else {
                run.expectTrue(e, "Ice Barrage splash: a monster above the attacker's Slayer level is not hit", hitTypes(run, "VS", cast).isEmpty(),
                    "VS hit: ${hitTypes(run, "VS", cast)}", "Combat.slayerLevelTooLow in SpecialAttackSupport.canSplash (owner 2026-10-01)")
            }
        }
        a.skills.setBaseLevel(Skills.SLAYER, 99)
    }

    // ---- familiar: a familiar obeys its owner's attack rules ------------------------------------------------------------------

    private suspend fun QueueTask.familiarRules(
        run: Conformance.Run,
        arena: Tile,
    ) {
        val e = "familiar"
        val fc = gg.rsmod.plugins.content.skills.summoning.FamiliarCombat
        val at = arena.transform(0, -2)
        val a = run.bot(at, "FA")
        val c = run.bot(at.transform(2, 2), "FC")
        wait(2)
        if (!at.isMulti(run.world)) {
            run.notTested(e, "familiar", "$at is not multi-way")
            return
        }
        reset(a, at)
        reset(c, at.transform(2, 2))
        val combat = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC, Skills.PRAYER, Skills.SUMMONING)
        combat.forEach { c.skills.setBaseLevel(it, 60) }
        c.calculateAndSetCombatLevel()
        val pouch = gg.rsmod.plugins.content.skills.summoning.SummoningPouchData.SPIRIT_WOLF.pouch
        a.inventory.add(pouch, 1)
        Bots.itemOption(a, a.inventory.getItemIndex(pouch, false), "Summon")
        wait(3)
        val familiar = gg.rsmod.plugins.content.skills.summoning.Familiar.current(a)
        if (familiar == null) {
            run.notTested(e, "familiar", "no familiar summoned")
            return
        }
        run.recording.track(familiar, "FF")
        val man = run.npc(1, at.transform(-2, 2), "FM")
        val guard = run.npc(gg.rsmod.plugins.content.mechanics.pvp.CityGuards.MELEE_GUARD_ID, at.transform(0, 3), "FG")
        wait(2)

        /** Orders the familiar at [t]; true when it fights [t] or any hitsplat lands on [label] within 8 ticks. */
        suspend fun order(t: gg.rsmod.game.model.entity.Pawn, label: String): Pair<Boolean, Boolean> {
            val from = run.world.currentCycle
            familiar.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
            fc.commandAttack(a, t)
            var fought = false
            repeat(8) {
                if (familiar.getCombatTarget() === t || familiar.timers.has(gg.rsmod.game.model.timer.ATTACK_DELAY)) fought = true
                (t as? gg.rsmod.game.model.entity.Npc)?.let { n -> n.setCurrentLifepoints(n.getMaximumLifepoints()) }
                (t as? Client)?.let { p -> p.setCurrentLifepoints(p.getMaximumLifepoints()) }
                wait(1)
            }
            val hit = hitTypes(run, label, from).isNotEmpty()
            familiar.resetInteractions(); Combat.reset(familiar)
            wait(2)
            return fought to hit
        }
        val (manFought, _) = order(man, "FM")
        run.expectTrue(e, "control: the familiar attacks a man on command", manFought, "never targeted the man")
        val (guardFought, guardHit) = order(guard, "FG")
        run.expectTrue(e, "an unskulled owner cannot send his familiar at a guard", !guardFought && !guardHit, "fought=$guardFought hit=$guardHit",
            "OSRS Wiki Guard (Deadman Mode): only attackable inside the guarded area while skulled")
        // A scroll special cast straight at a target (Spirit wolf Howl) goes through the same rules.
        familiar.resetInteractions(); Combat.reset(familiar)
        a.inventory.add(gg.rsmod.plugins.content.skills.summoning.SummoningScrollData.HOWL_SCROLL.scroll, 5)
        val howlGuard = gg.rsmod.plugins.content.skills.summoning.SummoningSpecialMoves.castOnNpc(a, guard)
        run.expectTrue(e, "a scroll special cannot be cast at a guard the owner may not attack", !howlGuard, "Howl accepted on the guard")
        wait(4)
        val howlMan = gg.rsmod.plugins.content.skills.summoning.SummoningSpecialMoves.castOnNpc(a, man)
        run.expectTrue(e, "control: the scroll special works on a man", howlMan, "Howl refused on the man")
        val (playerFought, playerHit) = order(c, "FC")
        run.expectTrue(e, "the familiar cannot attack a player outside the owner's level bracket (${a.combatLevel} vs ${c.combatLevel})",
            !playerFought && !playerHit, "fought=$playerFought hit=$playerHit", "AreaState.canPlayersFight")
        combat.forEach { c.skills.setBaseLevel(it, 99) }
        c.calculateAndSetCombatLevel()
        wait(2)
        val (pvpFought, pvpHit) = order(c, "FC")
        run.expectTrue(e, "control: the familiar attacks a player in the bracket, both in danger", pvpFought, "never fought (${a.combatLevel} vs ${c.combatLevel})")
        // Its hit is the owner's attack: the owner is skulled, the victim is not, and the victim may hit the owner back unskulled.
        val skull = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
        if (!pvpHit) {
            run.notTested(e, "familiar skull", "no familiar hitsplat on FC")
        } else {
            run.expectTrue(e, "a familiar's hit on an unskulled player skulls its owner", skull.isSkulled(a), "owner not skulled")
            run.expectTrue(e, "the familiar's victim is not skulled", !skull.isSkulled(c), "victim skulled")
            wield(c, Item(Items.ABYSSAL_WHIP))
            val back = run.world.currentCycle
            c.attack(a)
            var landed = false
            for (i in 0 until 8) { a.setCurrentLifepoints(a.getMaximumLifepoints()); wait(1); if (hitTypes(run, "FA", back).isNotEmpty()) { landed = true; break } }
            c.resetInteractions(); Combat.reset(c)
            run.expectTrue(e, "the victim hits the owner back without a skull (retaliation)", landed && !skull.isSkulled(c), "landed=$landed skulled=${skull.isSkulled(c)}")
        }
        gg.rsmod.plugins.content.skills.summoning.Familiar.current(a)?.let { run.world.remove(it) }
    }

    // ---- corp: the Corporeal Beast cave is no escape from a Tele Block -----------------------------------------------------

    private suspend fun QueueTask.corpCave(run: Conformance.Run) {
        val e = "corp"
        val id = gg.rsmod.plugins.api.cfg.Objs.ENTRANCE_37749
        val near = Tile(3214, 3782, 0)
        var entrance: gg.rsmod.game.model.entity.GameObject? = null
        for (dx in -12..12) for (dz in -12..12) {
            val t = near.transform(dx, dz)
            run.world.chunks.get(t)?.getEntities<gg.rsmod.game.model.entity.GameObject>(t, gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                // The placed loc is the varbit wrapper 38815/38816 that shows as 37749 (object_inventory.csv).
                ?.firstOrNull { it.id == id || it.id == 38815 || it.id == 38816 }?.let { if (entrance == null) entrance = it }
        }
        val obj = entrance
        if (obj == null) {
            run.notTested(e, "corp cave", "no loc $id / 38815 / 38816 within 12 tiles of $near")
            return
        }
        val option = run.world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).options.indexOfFirst { it?.equals("go-through", true) == true }
        val sentId = obj.id
        val start = listOf(obj.tile.transform(0, -1), obj.tile.transform(1, -1), obj.tile.transform(-1, -1), obj.tile.transform(0, 2), obj.tile.transform(2, 0), obj.tile.transform(-1, 0))
            .firstOrNull { !run.world.collision.isClipped(it) }
        if (option !in 0..1 || start == null) {
            run.notTested(e, "corp cave", "option index $option / no free start tile")
            return
        }
        suspend fun enter(label: String, teleblocked: Boolean): Pair<Boolean, String> {
            val p = run.bot(start, label)
            wait(2)
            if (teleblocked) p.timers[gg.rsmod.game.model.timer.TELEBLOCK_TIMER] = 500
            val c = run.world.currentCycle
            Bots.inject(p, if (option == 0) gg.rsmod.game.message.impl.OpLoc1Message(0, obj.tile.x, sentId, obj.tile.z) else gg.rsmod.game.message.impl.OpLoc2Message(sentId, obj.tile.x, obj.tile.z, 0))
            repeat(12) { wait(1) }
            val inside = p.tile.isWithinRadius(Tile(2885, 4372, 2), 20)
            val said = run.recording.of(label, "msg").filter { it.cycle >= c && it.text.startsWith("MessageGameMessage") }.joinToString(" / ") { it.text.substringAfter("message=").take(70) }
            p.timers.remove(gg.rsmod.game.model.timer.TELEBLOCK_TIMER)
            return inside to said
        }
        val (blockedIn, blockedSaid) = enter("CB", teleblocked = true)
        run.expectTrue(e, "tele-blocked: cannot enter the Corporeal Beast cave", !blockedIn && blockedSaid.contains("magical force"), "inside=$blockedIn msgs: $blockedSaid",
            "OSRS Wiki Tele Block: \"unable to enter the Corporeal Beast's cave\"")
        val (freeIn, freeSaid) = enter("CF", teleblocked = false)
        run.expectTrue(e, "control: without a Tele Block the entrance works", freeIn, "msgs: $freeSaid")
    }
}
