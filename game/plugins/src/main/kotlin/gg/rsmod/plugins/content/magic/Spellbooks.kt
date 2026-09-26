package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook

import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.switchSpellbook
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards

/**
 * Spellbook switching rules shared by the Ancient/Astral altars, the Ferox home altar menu and
 * the ::spellbook command. Level gates, animation and messages sourced from the 2009scape
 * MagicAltarListener (same-era content); Grand Exchange NPC unlock flags are enforced here so
 * altars, commands and other callers cannot bypass the NPC conversations.
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
        // Spellbook switching is an action. If it is performed while a Deadman portal,
        // transport, logout or teleport countdown is visible, the pending action must be
        // cancelled rather than allowed to complete after the switch.
        SevenSecondAction.cancel(player, "Your action was cancelled.")
        if (book == Spellbook.ANCIENT && player.attr[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] != true) {
            player.message("You need to complete Desert Treasure to use Ancient Magicks.")
            return false
        }
        if (book == Spellbook.LUNAR && player.attr[UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED] != true) {
            player.message("You need to complete Lunar Diplomacy to use the Lunar spellbook.")
            return false
        }
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
