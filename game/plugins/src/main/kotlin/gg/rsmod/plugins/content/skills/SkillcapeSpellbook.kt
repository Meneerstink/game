package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.content.magic.SpellbookSwap
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.skills.SkillcapePerks.DailyUse

/**
 * K03 N410 (owner 2026-10-29: "jawel op maxcape magic en normale magic cape moet je dus 5 keer kunen switchen"): the "Spellbook"
 * option of the Magic cape, the Magic cape (t) and the plain max cape. OSRS Wiki "Magic cape": it changes the player's spellbook
 * "up to five times per day" to "any spellbook the player has unlocked", from the inventory or while worn; it cannot be used while
 * Spellbook Swap is active; "Check" shows the uses left. The switch itself is [Spellbooks.select] (its quest and Magic level gates,
 * animation, sound and message), and a use is only spent when the book really changed. Uses reset at 00:00 UTC ([SkillcapePerks]).
 */
object SkillcapeSpellbook {
    fun bookName(book: Spellbook): String = book.name.lowercase().replaceFirstChar(Char::uppercase)

    fun left(player: Player): Int = SkillcapePerks.remainingToday(player, DailyUse.SPELLBOOK)

    fun leftMessage(left: Int): String = "Your cape has $left spellbook ${if (left == 1) "change" else "changes"} left for today."

    /** The books [player] could change to: every unlocked one except the current. */
    fun choices(player: Player): List<Spellbook> = Spellbook.values.filter { it != player.getSpellbook() && Spellbooks.hasAccess(player, it) }

    fun choose(player: Player) {
        if (SpellbookSwap.isActive(player)) {
            player.message("You can't change your spellbook while Spellbook Swap is active.")
            return
        }
        if (left(player) == 0) {
            player.message("You have used all ${DailyUse.SPELLBOOK.limit} of today's spellbook changes.")
            return
        }
        val books = choices(player)
        if (books.isEmpty()) {
            player.message("You have not unlocked any other spellbook.")
            return
        }
        player.queue {
            val choice = options(*(books.map(::bookName) + "Cancel").toTypedArray(), title = "Select a spellbook (${left(player)} left today)")
            val book = books.getOrNull(choice - 1) ?: return@queue
            if (left(player) == 0 || !Spellbooks.select(player, book)) return@queue
            SkillcapePerks.takeDailyUse(player, DailyUse.SPELLBOOK)
            player.message("<col=ef1020>${leftMessage(left(player))}</col>")
        }
    }

    fun check(player: Player) = player.message(leftMessage(left(player)))
}
