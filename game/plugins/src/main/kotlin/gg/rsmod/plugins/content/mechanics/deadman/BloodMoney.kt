package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.plugins.content.mechanics.death.PvpLootTimes
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill

/** V7: one physical blood money per VALID judged death; no new antifarm evaluation, breach/emblem exchange or DP wallet. */
object BloodMoney {
    private val PENDING = AttributeKey<Int>("dmm2_blood_money_pending")
    enum class Armour(val item: Dmm2Ids.ItemId, val key: String) {
        CHEST(Dmm2Ids.ItemId.CHEST, "dmm2_blood_chest"), LEGS(Dmm2Ids.ItemId.LEGS, "dmm2_blood_legs"), CAPE(Dmm2Ids.ItemId.CAPE, "dmm2_blood_cape");
        val unlocked = AttributeKey<Int>(key)
    }
    enum class Result { OK, NOT_READY, LOCKED, NO_MONEY, NO_SPACE, ALREADY_UNLOCKED }
    fun onJudgedKill(killer: Player, victim: Player, verdict: ValidPkKill.Verdict, release: Boolean = BreachRetirement.enabled) {
        if (!release || killer === victim || !verdict.valid) return
        val id = Dmm2Ids.ItemId.BLOOD_MONEY.local
        if (id != null) killer.world.spawn(PvpLootTimes.groundItem(id, 1, victim.tile, killer))
        else {
            val pending = killer.attr[PENDING] ?: 0
            check(pending < Int.MAX_VALUE) { "Blood money pending balance overflow" }
            killer.attr[PENDING] = pending + 1
            killer.persistNow()
        }
    }
    fun deliverPending(player: Player): Result {
        val pending = player.attr[PENDING] ?: return Result.OK
        val id = Dmm2Ids.ItemId.BLOOD_MONEY.local ?: return Result.NOT_READY
        if (pending <= 0) return Result.OK
        val inventory = ItemContainer(player.inventory)
        if (!DeadmanInventory.add(inventory, Item(id, pending))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory)
        player.attr.remove(PENDING); player.persistNow()
        return Result.OK
    }
    /** Blood money on Nigel buys an account unlock for 1 each. Lost unlocked armour is subsequently free to reclaim. */
    fun unlock(player: Player, armour: Armour): Result {
        val money = Dmm2Ids.ItemId.BLOOD_MONEY.local ?: return Result.NOT_READY
        val id = armour.item.local ?: return Result.NOT_READY
        if (player.attr[armour.unlocked] == 1) return Result.ALREADY_UNLOCKED
        val inventory = ItemContainer(player.inventory)
        if (!inventory.remove(money, 1).hasSucceeded()) return Result.NO_MONEY
        if (!DeadmanInventory.add(inventory, Item(id))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory)
        player.attr[armour.unlocked] = 1
        player.persistNow()
        return Result.OK
    }
    fun reclaim(player: Player, armour: Armour): Result {
        if (player.attr[armour.unlocked] != 1) return Result.LOCKED
        val id = armour.item.local ?: return Result.NOT_READY
        val inventory = ItemContainer(player.inventory)
        if (!DeadmanInventory.add(inventory, Item(id))) return Result.NO_SPACE
        DeadmanInventory.publish(inventory, player.inventory); player.persistNow()
        return Result.OK
    }
}
