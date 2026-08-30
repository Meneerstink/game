package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items

/** New-account rewards whose values are confirmed product requirements. */
object NewPlayerStart {
    const val STARTER_CASH = 50_000

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
}
