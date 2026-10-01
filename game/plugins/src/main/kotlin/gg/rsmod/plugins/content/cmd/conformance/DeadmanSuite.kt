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
            a.equipment[EquipmentType.WEAPON.id] = first
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
}
