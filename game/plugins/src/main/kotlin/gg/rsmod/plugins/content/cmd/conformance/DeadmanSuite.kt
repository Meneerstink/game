package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
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
            run.expectTrue(e, "$label: respawn in a guarded zone", gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(b.tile), "tile=${b.tile}")
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
}
