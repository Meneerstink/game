package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerKey

/**
 * Owner A7 2026-10-10 / decision 2026-10-11: a chat line when a timer ends - the skull, Tele Block and every buff timer RuneLite's
 * Timers plugin shows (not the combat / PJ timer). Where OSRS has its own text it is used word for word with its colour; where OSRS
 * shows nothing, a red line in the same style ([RED], the OSRS game-message red of "You have been frozen!").
 *
 * Sources (night run 2026-10-12): RuneLite TimersPlugin chat constants (runelite-parent 1.5.30 - 1.7.0) - stamina
 * `<col=8f4808>Your stamina potion has expired.</col>`, Magic Imbue `Your Magic Imbue charge has ended.`, Charge
 * `<col=ef1020>Your magical charge fades away.</col>`, Staff of the dead "Your protection fades away"; OSRS Wiki "Update: PvP
 * Improvements and W45 Changes" (13 June 2019) - Tele Block "will expire in 30 seconds" / "has expired", purple as its cast message
 * `<col=4f006f>`; antifire texts in [gg.rsmod.plugins.content.items.potion.AntifirePotions]. OSRS shows nothing for the skull, the
 * poison / venom immunity of antipoisons and antivenoms, the Vengeance cooldown and the divine potions: red lines.
 * Not here: the home teleport cooldown (set, but not enforced on this server: HOME_TELEPORT_TIMER_ENABLED), freeze (a debuff).
 */
object TimerExpiry {
    const val RED = "ef1020"

    private fun red(text: String) = "<col=$RED>$text</col>"

    // OSRS texts.
    const val STAMINA_EXPIRED = "<col=8f4808>Your stamina potion has expired.</col>"
    const val TELEBLOCK_WARNING = "<col=4f006f>Your Tele Block will expire in 30 seconds.</col>"
    const val TELEBLOCK_EXPIRED = "<col=4f006f>Your Tele Block has expired.</col>"
    const val MAGIC_IMBUE_EXPIRED = "Your Magic Imbue charge has ended."
    const val CHARGE_EXPIRED = "<col=ef1020>Your magical charge fades away.</col>"
    // Green like the Staff of the dead activation message (OSRS Wiki); the exact hex is not published.
    const val SOTD_EXPIRED = "<col=006000>Your protection fades away.</col>"

    // No OSRS text: red lines in the same style (owner A7).
    val SKULL_EXPIRED = red("Your skull has worn off.")
    val POISON_IMMUNITY_EXPIRED = red("Your resistance to poison has worn off.")
    val VENOM_IMMUNITY_EXPIRED = red("Your resistance to venom has worn off.")
    val VENGEANCE_READY = red("You can cast Vengeance again.")
    val DIVINE_EXPIRED = red("The effect of your divine potion has worn off.")

    /** OSRS: the Tele Block warning 30 seconds (50 ticks) before the block ends. */
    const val TELEBLOCK_WARNING_TICKS = 50

    /** The warning's own timer: ticks offline and persists exactly like [TELEBLOCK_TIMER], so a relog keeps both in step. */
    val TELEBLOCK_WARNING_TIMER = TimerKey(persistenceKey = "teleblock_warning", tickOffline = true, resetOnDeath = true)

    /** Every route that teleblocks a player: the block and its 30-second warning together. */
    fun teleblock(
        player: Player,
        ticks: Int,
    ) {
        player.timers[TELEBLOCK_TIMER] = ticks
        if (ticks > TELEBLOCK_WARNING_TICKS) {
            player.timers[TELEBLOCK_WARNING_TIMER] = ticks - TELEBLOCK_WARNING_TICKS
        } else {
            player.timers.remove(TELEBLOCK_WARNING_TIMER)
        }
    }
}
