package gg.rsmod.plugins.content.areas.varrock

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * PROJECT48 run 4 O17 (The Dig Site unlock): the Varrock Museum specimen cleaning activity. Shared state for
 * varrock_museum_cleaning.plugin.kts and the ruby-necklace enchant (Lvl-3 Enchant + Enchant ruby tablet).
 *
 * Source: OSRS Wiki "Clean necklace": showing the clean necklace to a Varrock Museum archaeologist teaches the player to enchant ruby
 * necklaces into digsite pendants; before that, Lvl-3 Enchant / the ruby tablet on a ruby necklace gives the message
 * "You have not yet discovered the secret to enchanting ruby necklaces." Ruby amulets are not affected.
 */
object VarrockMuseum {
    /** Set when an archaeologist has studied the clean necklace (OSRS: the pendant spell is learnt). */
    val RUBY_PENDANT_LEARNT = AttributeKey<Boolean>(persistenceKey = "museum_ruby_pendant")

    const val NOT_LEARNT_MESSAGE = "You have not yet discovered the secret to enchanting ruby necklaces."

    fun learnt(player: Player): Boolean = player.attr[RUBY_PENDANT_LEARNT] == true

    /** True when [item] may be enchanted now; sends the OSRS refusal for a ruby necklace before the necklace was shown. */
    fun mayEnchant(player: Player, item: Int): Boolean {
        if (item != Items.RUBY_NECKLACE || learnt(player)) return true
        player.message(NOT_LEARNT_MESSAGE)
        return false
    }
}
