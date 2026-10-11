package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.plugins.api.cfg.Items

/** New-account rewards whose values are confirmed product requirements. */
object NewPlayerStart {
    /** Owner 2026-10-10: 1m (was 50k) - the same as one vote. */
    const val STARTER_CASH = 1_000_000

    private val CASH_GRANTED_THIS_LOGIN = AttributeKey<Boolean>()

    /**
     * Grants the confirmed starter cash once to a genuinely new account.
     * NEW_ACCOUNT_ATTR is transient and only armed by account creation; the
     * second guard also makes a duplicate callback in the same login harmless.
     */
    fun grantStarterCash(player: Player): Boolean {
        if (player.attr[NEW_ACCOUNT_ATTR] != true || player.attr[CASH_GRANTED_THIS_LOGIN] == true) {
            return false
        }
 val currentCash = player.bank.getItemCount(Items.COINS_995)
 val transactionSucceeded = when {
 currentCash < STARTER_CASH ->
 player.bank.add(
 Items.COINS_995,
 STARTER_CASH - currentCash,
 assureFullInsertion = true,
 ).hasSucceeded()
 currentCash > STARTER_CASH ->
 player.bank.remove(Items.COINS_995, currentCash - STARTER_CASH).hasSucceeded()
 else -> true
 }
 if (!transactionSucceeded || player.bank.getItemCount(Items.COINS_995) != STARTER_CASH) {
 return false
 }
 player.attr[CASH_GRANTED_THIS_LOGIN] = true
 return true
    }

    /** The Deadman starter pack has been handed out (saved: replaying the introduction never gives a second one). */
    val STARTER_PACK_GIVEN = AttributeKey<Boolean>(persistenceKey = "deadman_starter_pack_given")

    /**
     * Owner 2026-10-08: a new player starts with an empty inventory and gets only the Deadman starter pack (OSRS item 22330,
     * here [NewPlayerConfig.starterChestItem]) once the introduction is over - finished, skipped or interrupted.
     */
    fun grantStarterPack(player: Player) {
        if (player.attr[STARTER_PACK_GIVEN] == true) return
        val pack = NewPlayerConfig.current.starterChestItem
        if (!player.inventory.add(pack).hasSucceeded() && !player.bank.add(pack).hasSucceeded()) return
        player.attr[STARTER_PACK_GIVEN] = true
    }

    /** "Open": the pack becomes its contents ([NewPlayerConfig.starterChestContents]), all or nothing. */
    fun openStarterPack(player: Player) {
        val config = NewPlayerConfig.current
        if (!player.inventory.contains(config.starterChestItem)) return
        val slots =
            config.starterChestContents.sumOf { (item, amount) ->
                val def = player.world.definitions.get(ItemDef::class.java, item)
                if (def.stackable || def.noted) 1 else amount
            }
        if (player.inventory.freeSlotCount + 1 < slots) {
            player.message("You need $slots free inventory spaces to open this.")
            return
        }
        if (!player.inventory.remove(config.starterChestItem, assureFullRemoval = true).hasSucceeded()) return
        config.starterChestContents.forEach { (item, amount) -> player.inventory.add(item, amount) }
    }
}
