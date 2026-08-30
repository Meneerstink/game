package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.attr.PROTECTION_FORFEITED_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER

/**
 * R14.23-R14.27: temporary beginner PvP protection.
 *
 * - R14.23: real protection, not just an icon - the single gate this reads from
 *   ([AreaState.canPlayersFight]) is the same one all real combat targeting already routes
 *   through (`Combat.kt`, `PawnExt.kt`, `PvpSkull.kt`), so it covers targeting in both
 *   directions and ongoing combat, not only the initial click.
 * - R14.24: exactly 60 minutes of ACTIVE play ([gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER]
 *   uses `tickOffline = false`), granted once at true first login, never reset by a later
 *   login or death.
 * - R14.25/R14.27 are implemented at the call sites: the explicit-consent confirmation lives
 *   in `combat.plugin.kts`'s `on_player_option("Attack")` (the one place a player *explicitly*
 *   initiates PvP, as opposed to auto-retaliation - matching `PvpSkull`'s own existing
 *   initiator-vs-retaliation distinction, reused rather than reinvented), and the offline-pause
 *   behaviour is a property of the timer key itself, not code here.
 *
 * Known gap: familiar combat and "indirect PvP support" (e.g. healing/buffing an ally who is
 * fighting) do not route through `canPlayersFight` and are not covered yet - see
 * OWNER_TASK_STATUS.md R14.23.
 */
object BeginnerProtection {
    /** 60 minutes at 100 cycles/minute (0.6s cycles) - matches PvpSkull's own cycle math. */
    const val PROTECTION_CYCLES = 6000

    fun isProtected(player: Player): Boolean =
        player.timers.has(NEW_PLAYER_PROTECTION_TIMER) && player.attr[PROTECTION_FORFEITED_ATTR] != true

    /** Call once, at true first login only (see `starter_kit.plugin.kts`'s NEW_ACCOUNT_ATTR gate). */
    fun grantOnFirstLogin(player: Player) {
        if (player.attr[NEW_ACCOUNT_ATTR] == true) {
            player.timers[NEW_PLAYER_PROTECTION_TIMER] = PROTECTION_CYCLES
        }
    }

    /** R14.25: permanently ends protection once the player has explicitly confirmed an attack. */
    fun forfeit(player: Player) {
        player.attr[PROTECTION_FORFEITED_ATTR] = true
    }
}
