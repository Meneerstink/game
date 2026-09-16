package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.message

/** Bank gate for the Deadman-style bank rule requested for this server. */
object BankSecurity {
    const val BANK_GUARD_ID = 9122
    /** 10 seconds at the server's 0.6s game cycle. */
    const val BANK_TIMER_TICKS = 17
    val BANK_ENTRY_TIMER = TimerKey()
    val BANK_ENTRY_MONITOR = TimerKey()
    val BANK_GUARD = AttributeKey<Boolean>()
    val WAS_IN_BANK = AttributeKey<Boolean>()

    fun carriesLootKey(player: Player): Boolean =
        player.inventory.rawItems.any { item ->
            item != null && item.id in setOf(Items.LOOT_KEY, Items.LOOT_KEY_23697, 23698, 23699, 23700)
        }

    fun isBankBlocked(player: Player): Boolean =
        player.hasSkullIcon(SkullIcon.RED) || carriesLootKey(player)

    fun denyBank(player: Player): Boolean {
        if (!isBankBlocked(player)) return false
        if (player.timers.has(BANK_ENTRY_TIMER)) {
            player.message("The bank guards are still watching you. Wait out the 10-second timer.")
            return true
        }
        player.timers[BANK_ENTRY_TIMER] = BANK_TIMER_TICKS
        player.attr[BANK_GUARD] = true
        player.message("We don't want your sort here, ${player.username}!")
        player.message("You must wait 10 seconds before banking.")
        return true
    }

    fun guardMayAttack(player: Player): Boolean = isBankBlocked(player)

    /** Detect entry into a cache-derived bank safe zone independently of the bank interface. */
    fun monitor(player: Player) {
        val inBank = BankZones.isSafe(player.tile)
        val wasInBank = player.attr[WAS_IN_BANK] ?: false
        player.attr[WAS_IN_BANK] = inBank
        if (!inBank || wasInBank || !isBankBlocked(player)) return

        // A player can arrive by teleport or another movement path while a bank modal is already
        // open. Guards alone are not enough: the queued interface actions would otherwise still
        // be able to deposit/withdraw after the blocked entry. Close both sides of the bank UI at
        // the same boundary that arms the guard.
        player.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
        player.closeInterface(dest = InterfaceDestination.TAB_AREA)
        player.timers[BANK_ENTRY_TIMER] = BANK_TIMER_TICKS
        player.attr[BANK_GUARD] = true
        player.message("We don't want your sort here, ${player.username}!")
        nearbyGuards(player).forEach { guard ->
            guard.forceChat("We don't want your sort here, ${player.username}!")
            if (guard.canEngageCombat(player)) guard.attack(player)
        }
    }

    private fun nearbyGuards(player: Player): List<Npc> {
        val found = ArrayList<Npc>()
        for (x in -12..12) {
            for (z in -12..12) {
                val tile = player.tile.transform(x, z)
                val chunk = player.world.chunks.get(tile, createIfNeeded = false) ?: continue
                chunk.getEntities<Npc>(tile, EntityType.NPC)
                    .filterTo(found) { it.id == BANK_GUARD_ID && it.isActive() }
            }
        }
        return found
    }
}
