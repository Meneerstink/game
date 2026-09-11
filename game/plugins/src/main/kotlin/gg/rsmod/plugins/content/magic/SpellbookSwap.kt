package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.switchSpellbook

/**
 * Lunar's "Spellbook Swap" spell opens the Ancient or Modern book without [Spellbooks.requiredLevel]'s
 * normal level gate, then reverts to Lunar once the next spell finishes casting - or after 2 minutes
 * (200 ticks) if nothing else is cast. Sourced from Void's
 * `content/skill/magic/book/lunar/SpellbookSwap.kt` (`swap`/`revertSpellbookSwap`/
 * `checkSpellbookSwapCast`), same duration and single-cast-reverts rule; Novite has no equivalent.
 */
object SpellbookSwap {
    val TIMER = TimerKey()
    private val ACTIVE = AttributeKey<Boolean>()

    fun isActive(player: Player): Boolean = player.attr[ACTIVE] == true

    fun start(
        player: Player,
        book: Spellbook,
    ) {
        player.attr[ACTIVE] = true
        player.switchSpellbook(book)
        player.timers[TIMER] = 200
    }

    fun revert(player: Player) {
        if (player.attr[ACTIVE] != true) {
            return
        }
        player.attr.remove(ACTIVE)
        player.timers.remove(TIMER)
        player.switchSpellbook(Spellbook.LUNAR)
        player.message("Your spellbook has changed back to the Lunar spellbook.")
    }

    /** Called from [MagicSpells.removeRunes] for every successful spell cast, including teleports. */
    fun onSpellCast(
        player: Player,
        spellId: Int,
    ) {
        if (!isActive(player) || spellId == SpellbookData.SPELLBOOK_SWAP.uniqueId) {
            return
        }
        player.queue {
            wait(1)
            revert(player)
        }
    }
}
