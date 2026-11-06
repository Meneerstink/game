package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.pvp.BankSecurity
import gg.rsmod.game.model.container.ItemContainer

/** The separate n462-toa-kern card installs the OSRS points/loot calculation here. All IDs returned are LOCAL. */
fun interface ToaRewardRoller {
    fun roll(context: ToaRewardContext): List<Item>
}

data class ToaRewardContext(
    val raidToken: String,
    val player: PlayerUID,
    val roster: List<PlayerUID>,
    val settings: ToaRaidSettings,
    val elapsedCycles: Int,
    val failedAttempts: Int,
)

/** Persistent personal escrow: opening, niche and reconnect all read the same roll, even when it was empty. */
data class ToaPersonalReward(val raidToken: String, val items: List<ToaRewardItem>) {
    init { require(raidToken.isNotBlank() && '|' !in raidToken) }
    val unclaimed: Boolean get() = items.isNotEmpty()
    fun encode(): String = raidToken + "|" + items.joinToString(";") { "${it.id}:${it.amount}" }

    /** The receiver reports the actual insertion, preserving anything that did not fit. */
    fun withdraw(accept: (Int, Int) -> Int): ToaPersonalReward {
        val remaining = mutableListOf<ToaRewardItem>()
        for (item in items) {
            val moved = accept(item.id, item.amount)
            check(moved in 0..item.amount)
            if (moved < item.amount) remaining += item.copy(amount = item.amount - moved)
        }
        return copy(items = remaining)
    }

    companion object {
        fun decode(text: String): ToaPersonalReward {
            val split = text.split('|', limit = 2)
            require(split.size == 2) { "Invalid ToA reward escrow" }
            val items = if (split[1].isEmpty()) emptyList() else split[1].split(';').map { entry ->
                val fields = entry.split(':')
                require(fields.size == 2)
                ToaRewardItem(fields[0].toInt(), fields[1].toInt())
            }
            return ToaPersonalReward(split[0], items)
        }

        fun open(previous: ToaPersonalReward?, token: String, roll: () -> List<ToaRewardItem>): ToaPersonalReward {
            if (previous?.raidToken == token) return previous
            check(previous?.unclaimed != true) { "Unclaimed ToA reward must be confirmed before entering a new raid" }
            return ToaPersonalReward(token, roll().toList())
        }
    }
}

data class ToaRewardItem(val id: Int, val amount: Int) {
    init { require(id >= 0 && amount > 0) }
}

object ToaRewards {
    // No invented rewards while the separate OSRS calculation card is pending.
    var roller: ToaRewardRoller = ToaRewardRoller { emptyList() }
    var claimed: (Player, List<Item>) -> Unit = { _, _ -> }
    val ESCROW = AttributeKey<String>(persistenceKey = "toa_personal_reward")

    fun stored(player: Player): ToaPersonalReward? = player.attr[ESCROW]?.let(ToaPersonalReward::decode)
    fun hasUnclaimed(player: Player): Boolean = stored(player)?.unclaimed == true

    fun open(player: Player, raid: ToaRaid): ToaPersonalReward? {
        if (!raid.progress.finished || !raid.contains(player)) return null
        val reward = ToaPersonalReward.open(stored(player), raid.rewardToken) {
            roller.roll(ToaRewardContext(raid.rewardToken, player.uid, raid.roster.toList(), raid.settings,
                raid.progress.elapsedCycles(raid.world.currentCycle), raid.progress.failedAttempts))
                .map { ToaRewardItem(it.id, it.amount) }
        }
        player.attr[ESCROW] = reward.encode()
        player.persistNow()
        return reward
    }

    fun take(player: Player, toBank: Boolean = false, expectedToken: String? = null) {
        val reward = stored(player) ?: return
        if (expectedToken != null && reward.raidToken != expectedToken) return
        if (toBank && BankSecurity.denyBank(player)) return
        val destination = if (toBank) player.bank else player.inventory
        val planned = ItemContainer(destination)
        val given = mutableListOf<Item>()
        val remaining = reward.withdraw { id, amount ->
            val moved = planned.add(id, amount, assureFullInsertion = false, applyCreationRedirect = false).completed
            if (moved > 0) given += Item(id, moved)
            moved
        }
        for (slot in 0 until destination.capacity) destination[slot] = planned[slot]?.let(::Item)
        player.attr[ESCROW] = remaining.encode()
        // Inventory/bank and escrow are one atomic player save; a reconnect cannot roll or claim again.
        try { if (given.isNotEmpty()) claimed(player, given) } finally { player.persistNow() }
    }

    /** Only called after the new raid's whole party passes entry/allocation and each warning is confirmed. */
    fun discard(player: Player) { player.attr.remove(ESCROW); player.persistNow() }
}
