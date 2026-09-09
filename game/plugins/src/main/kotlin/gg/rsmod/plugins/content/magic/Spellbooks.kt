package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook

import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.switchSpellbook

/**
 * Spellbook switching rules shared by the Ancient/Astral altars, the Ferox home altar menu and
 * the ::spellbook command. Level gates, animation and messages sourced from the 2009scape
 * MagicAltarListener (same-era content); quest requirements are out of this server's scope.
 */
object Spellbooks {
    const val SWITCH_ANIM = 645

    fun requiredLevel(book: Spellbook): Int =
        when (book) {
            Spellbook.ANCIENT -> 50
            Spellbook.LUNAR -> 65
            else -> 1
        }

    fun activationMessage(book: Spellbook): String =
        when (book) {
            Spellbook.ANCIENT -> "You feel a strange wisdom fill your mind..."
            Spellbook.LUNAR -> "Lunar spells activated!"
            else -> "Modern spells activated!"
        }

    /** Altar behaviour: a second use of the same altar returns the player to the standard book. */
    fun toggle(
        player: Player,
        book: Spellbook,
    ) {
        if (player.getSpellbook() == book) {
            select(player, Spellbook.STANDARD, ancientDrain = book == Spellbook.ANCIENT)
        } else {
            select(player, book)
        }
    }

    fun select(
        player: Player,
        book: Spellbook,
        ancientDrain: Boolean = false,
    ): Boolean {
        val level = requiredLevel(book)
        if (player.skills.getMaxLevel(Skills.MAGIC) < level) {
            player.message("You need a Magic level of at least $level in order to do this.")
            return false
        }
        player.animate(SWITCH_ANIM)
        player.switchSpellbook(book)
        player.message(if (ancientDrain) "You feel a strange drain upon your memory..." else activationMessage(book))
        return true
    }
}
