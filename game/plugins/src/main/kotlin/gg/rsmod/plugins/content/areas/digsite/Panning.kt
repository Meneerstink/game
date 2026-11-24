package gg.rsmod.plugins.content.areas.digsite

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.bracket.TheDigSite

/** OSRS Wiki Panning tray / Panning point: tea permission, mud, 2 Fishing + Mining XP, forty-slot find table. */
object Panning {
    val PERMISSION = AttributeKey<Boolean>(persistenceKey = "digsite_panning_permission")
    val DELAY = TimerKey(tickOffline = false, resetOnDeath = true)
    val TEA = setOf(Items.CUP_OF_TEA_1978, Items.NETTLE_TEA, Items.NETTLE_TEA_4240,
        Items.CUP_OF_TEA_4242, Items.CUP_OF_TEA_4243, Items.CUP_OF_TEA_4245, Items.CUP_OF_TEA_4246)

    fun bribe(player: Player, slot: Int, held: Item): Boolean {
        if (player.attr[PERMISSION] == true || held.id !in TEA || player.inventory[slot] !== held) return false
        if (!player.inventory.remove(held.id, 1, beginSlot = slot).hasSucceeded()) return false
        player.attr[PERMISSION] = true
        return true
    }

    fun pan(player: Player, slot: Int = player.inventory.getItemIndex(Items.PANNING_TRAY, skipAttrItems = false)): Boolean {
        if (player.attr[PERMISSION] != true || player.timers.has(DELAY)) return false
        if (slot < 0 || player.inventory[slot]?.id != Items.PANNING_TRAY) return false
        player.inventory[slot] = Item(Items.PANNING_TRAY_679)
        action(player, 4593)
        player.addXp(Skills.FISHING, 2.0)
        player.addXp(Skills.MINING, 2.0)
        return true
    }

    fun reward(roll: Int, cupAllowed: Boolean): Int? {
        require(roll in 0..39)
        return when (roll) {
            in 0..3 -> Items.COINS_995
            in 4..7 -> Items.NUGGETS
            in 8..10 -> Items.OYSTER
            in 11..13 -> Items.UNCUT_OPAL
            in 14..16 -> Items.UNCUT_JADE
            in 17..19 -> Items.SPECIAL_CUP.takeIf { cupAllowed }
            else -> null
        }
    }

    fun search(player: Player, slot: Int, held: Item): Boolean {
        if (held.id != Items.PANNING_TRAY_679 || player.inventory[slot] !== held || player.timers.has(DELAY)) return false
        // Refuse before rolling: a full inventory must not reroll mud until a preferred stackable result fits.
        if (player.inventory.freeSlotCount == 0) return false
        val id = reward(player.world.random(39), !TheDigSite.isFinished(player))
        val found = id?.let { Item(it, if (it == Items.COINS_995) player.world.random(1..10) else 1) }
        if (found != null && !ItemContainer(player.inventory).add(found).hasSucceeded()) return false
        player.inventory[slot] = Item(Items.PANNING_TRAY)
        if (found != null) player.inventory.add(found)
        action(player, 4594)
        return true
    }

    fun exchangeNuggets(player: Player): Boolean {
        if (player.inventory.getItemCount(Items.NUGGETS) < 3) return false
        val check = ItemContainer(player.inventory)
        if (!check.remove(Items.NUGGETS, 3).hasSucceeded() || !check.add(Items.GOLD_ORE).hasSucceeded()) return false
        if (!player.inventory.remove(Items.NUGGETS, 3).hasSucceeded()) return false
        player.inventory.add(Items.GOLD_ORE)
        return true
    }

    private fun action(player: Player, animation: Int) {
        player.animate(animation) // 667 seq4593/4594 contain panning sfx2377; no duplicate sound packet.
        player.timers[DELAY] = player.world.definitions.get(AnimDef::class.java, animation).cycleLength.coerceAtLeast(1)
    }
}
