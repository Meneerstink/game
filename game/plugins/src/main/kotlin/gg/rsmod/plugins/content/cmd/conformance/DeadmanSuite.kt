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
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.content.combat.Combat

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
            try {
                body()
            } catch (e: Throwable) {
                run.add(Conformance.Result(name, "scenario ran", Conformance.Verdict.FAIL, "no exception", e.toString().take(200)))
            }
            run.window(name, start)
        }
        val arena = SummoningSuite.arenaFor(run, run.origin.transform(3, 0))
        scenario("switch") { switchOnApproach(run, arena) }
        scenario("death") { pvpDeath(run, arena) }
        scenario("teleport") { teleports(run, arena) }
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
        fun inVarrock() = varrock.contains(t.tile)

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
    }
}
