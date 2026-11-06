package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.game.model.queue.QueueTask

/** Interface import seam: never send OSRS 481/482/771-778 IDs into an unconverted 667 cache. */
object ToaUi {
    data class State(val raidLevel: Int, val elapsedCycles: Int, val failedAttempts: Int,
        val completedPaths: Set<ToaPath>, val wardensUnlocked: Boolean, val finished: Boolean)

    private val LAST = AttributeKey<State>()
    var draw: (Player, State?) -> Unit = { _, _ -> }
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
        val state = State(raid.settings.raidLevel, p.elapsedCycles(raid.world.currentCycle), p.failedAttempts,
            p.completedPaths, p.wardensUnlocked, p.finished)
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
