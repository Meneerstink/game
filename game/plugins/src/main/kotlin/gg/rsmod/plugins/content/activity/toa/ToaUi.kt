package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.game.model.queue.QueueTask

/** Interface import seam: never send OSRS 481/482/771-778 IDs into an unconverted 667 cache. */
object ToaUi {
    data class State(val raidLevel: Int, val elapsedCycles: Int, val failedAttempts: Int,
        val completedPaths: Set<ToaPath>, val wardensUnlocked: Boolean, val finished: Boolean, val currentPath: Int = 0,
        val party: List<PartySlot?> = emptyList(), val self: Int = -1)

    /** One HUD party orb (OSRS %toa_client_p0..7 / name0..7): [status] 30 = dead, otherwise 1 + hitpoints scaled to 0..26. */
    data class PartySlot(val name: String, val status: Int)

    /** Cache2686 CS2 6584 toa_hud_statusmyself: min(26, scale(hp, max(1, base), 26)), at least 1 while hp > 0; the orb status is 1 + that. */
    fun hpStatus(hp: Int, max: Int): Int {
        val scaled = minOf(26, hp.coerceAtLeast(0) * 26 / max.coerceAtLeast(1))
        return 1 + if (hp > 0) maxOf(1, scaled) else 0
    }

    private val LAST = AttributeKey<State>()
    var draw: (Player, State?) -> Unit = { _, _ -> }

    /** N609 14-11: runs every raid tick before the 'changed state only' check (cheap; e.g. ToA Extended 'Hide HP Orbs'); true = redraw. */
    var everyTick: (Player) -> Boolean = { false }
    var showRewards: (Player) -> Unit = { player ->
        val token = ToaRewards.stored(player)?.raidToken
        player.queue {
            // Literal labels: OSRS cache2686 interface771:4/6 (30-interface-componenten.tsv).
            when (options("Take-all", "Bank-all", "Cancel", title = "Rewards")) {
                1 -> ToaRewards.take(player, expectedToken = token)
                2 -> ToaRewards.take(player, toBank = true, expectedToken = token)
            }
        }
    }
    var showSupplies: (Player, ToaRaid) -> Unit = { player, raid ->
        val stage = raid.checkpoints.stage
        player.queue {
            // OSRS interface777:5/8/11.
            val choice = when (options("Life", "Chaos", "Power", "Cancel", title = "Supplies")) {
                1 -> ToaSupplies.Choice.LIFE
                2 -> ToaSupplies.Choice.CHAOS
                3 -> ToaSupplies.Choice.POWER
                else -> null
            }
            if (choice != null && raid.checkpoints.stage == stage) raid.supplies.claim(player, choice)
        }
    }
    var showBag: (Player, ToaRaid, Int) -> Unit = { player, raid, inventorySlot ->
        val bag = player.inventory[inventorySlot]
        player.queue {
            val option = options("Withdraw 1", "Withdraw All", "Cancel", title = "Supplies")
            if (player.inventory[inventorySlot] === bag) when (option) {
                1 -> raid.supplies.withdraw(player, inventorySlot)
                2 -> raid.supplies.withdraw(player, inventorySlot, all = true)
            }
        }
    }

    fun refresh(player: Player, raid: ToaRaid) {
        val p = raid.progress
        // Draw only changed data; imported HUD scripts can interpolate the clock between updates.
        val region = ToaMap.source(raid.map, player.tile)?.regionId
        // Cache2686 CS2 6579: 0 Nexus; 1 Scabaras, 2 Het, 3 Apmeken, 4 Crondis; 5/6 hide both path panels.
        val path = ToaPath.values().firstOrNull { it.challengeEntry.regionId == region || it.bossEntry.regionId == region }
            ?.ordinal?.plus(1) ?: when (region) { 15184, 15696 -> 5; 14672 -> 6; else -> 0 }
        val state = State(raid.settings.raidLevel, p.elapsedCycles(raid.world.currentCycle), p.failedAttempts,
            p.completedPaths, p.wardensUnlocked, p.finished, path, raid.hudParty(), raid.roster.indexOf(player.uid))
        if (everyTick(player)) player.attr.remove(LAST)
        if (player.attr[LAST] == state) return
        player.attr[LAST] = state
        draw(player, state)
    }

    fun close(player: Player) { player.attr.remove(LAST); draw(player, null) }

    suspend fun confirmRewards(task: QueueTask): Boolean = task.options("Continue", "Cancel",
        // ONZEKER T30: warning semantics from Rewards Niche wiki; exact OSRS server wording is not in cache/CS2.
        title = "Your unclaimed rewards will be lost.") == 1

    suspend fun confirmSupplies(task: QueueTask): Boolean = task.options("Continue", "Cancel",
        // ONZEKER T30: Helpful Spirit wiki proves forfeiture; exact server wording needs an OSRS capture.
        title = "Your unclaimed supplies will be lost.") == 1
}
