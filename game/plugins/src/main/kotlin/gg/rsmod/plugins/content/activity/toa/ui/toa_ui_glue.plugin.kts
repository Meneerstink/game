package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.plugins.content.activity.toa.ToaRewards
import gg.rsmod.plugins.content.activity.toa.ToaSupplies
import gg.rsmod.plugins.content.activity.toa.ToaUi

// N462: the ported OSRS ToA windows on the raid's UI seams (ToaUi). Each one only when its window is imported (data/cfg/toa/interfaces.tsv);
// otherwise the raid keeps its chatbox fallback (ToaUi defaults).

if (ToaInterfaces.of(ToaInterfaces.HUD) != null) {
    val previous = ToaUi.draw
    ToaUi.draw = { player, state ->
        if (state == null) {
            ToaHudUi.close(player)
        } else {
            ToaHudUi.open(player)
            ToaHudUi.drawRaid(player, state.raidLevel, state.elapsedCycles, currentPath = 0, pathLevels = IntArray(4))
        }
        previous(player, state)
    }
}

if (ToaInterfaces.of(ToaInterfaces.REWARDS) != null) {
    ToaUi.showRewards = { player ->
        val token = ToaRewards.stored(player)?.raidToken
        fun items() = ToaRewards.stored(player)?.items?.map { it.id to it.amount } ?: emptyList()
        ToaRewardsUi.open(player, items()) { action ->
            when (action) {
                ToaRewardsUi.Action.TAKE -> ToaRewards.take(player, expectedToken = token)
                ToaRewardsUi.Action.BANK -> ToaRewards.take(player, toBank = true, expectedToken = token)
                ToaRewardsUi.Action.DISCARD ->
                    player.queue {
                        if (ToaUi.confirmRewards(this) && ToaRewards.stored(player)?.raidToken == token) ToaRewards.discard(player)
                        ToaRewardsUi.refresh(player, items())
                    }
            }
            ToaRewardsUi.refresh(player, items())
        }
    }
}

if (ToaInterfaces.of(ToaInterfaces.SUPPLIES) != null) {
    ToaUi.showSupplies = { player, raid ->
        val choices = ToaSupplies.Choice.values()
        val stage = raid.checkpoints.stage
        val offers = raid.supplies.offers(player)
        ToaSuppliesUi.open(player, choices.map { choice -> offers[choice].orEmpty().map { it.id to it.amount } }) { index ->
            if (raid.checkpoints.stage == stage) raid.supplies.claim(player, choices[index])
        }
    }
}

if (ToaInterfaces.of(ToaInterfaces.BAG) != null) {
    ToaUi.showBag = { player, raid, inventorySlot ->
        val bagItem = player.inventory[inventorySlot]
        fun contents() = player.inventory[inventorySlot]?.let { raid.supplies.bag(player, it) }?.contents()?.map { id -> id?.let { it to 1 } }
            ?: emptyList()
        if (bagItem != null) {
            ToaBagUi.open(player, contents()) { slot, action ->
                if (player.inventory[inventorySlot] === bagItem) {
                    when (action) {
                        ToaBagUi.Action.WITHDRAW_1 -> raid.supplies.withdraw(player, inventorySlot, supplySlot = slot)
                        ToaBagUi.Action.WITHDRAW_5 -> repeat(5) { raid.supplies.withdraw(player, inventorySlot, supplySlot = slot) }
                        ToaBagUi.Action.WITHDRAW_ALL -> raid.supplies.withdraw(player, inventorySlot, all = true)
                        else -> Unit
                    }
                    ToaBagUi.refresh(player, contents())
                }
            }
        }
    }
}
