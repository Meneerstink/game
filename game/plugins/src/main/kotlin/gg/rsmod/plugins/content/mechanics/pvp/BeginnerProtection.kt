package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.attr.PROTECTION_FORFEITED_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER

/**
 * R14.23-R14.27: temporary beginner PvP protection.
 *
 * - R14.23: real protection, not just an icon - the single gate this reads from
 *   ([AreaState.canPlayersFight]) is the same one all real combat targeting already routes
 *   through (`Combat.kt`, `PawnExt.kt`, `PvpSkull.kt`), so it covers targeting in both
 *   directions and ongoing combat, not only the initial click. Also wired into
 *   `WildernessBreach.kt` (R14.26).
 * - R14.24: 60 minutes of budget, decremented only while ACTUALLY ACTIVE, not merely online -
 *   see [tickAfkGuard]. "Online but AFK" is a real, distinct state from "online and playing";
 *   pausing only on full logout (`tickOffline = false` alone) would not have been true AFK
 *   detection, which is why [tickAfkGuard] exists on top of it.
 * - R14.25/R14.27 are implemented at the call sites: the explicit-consent confirmation lives
 *   in `combat.plugin.kts`'s `on_player_option("Attack")` (the one place a player *explicitly*
 *   initiates PvP, as opposed to auto-retaliation - matching `PvpSkull`'s own existing
 *   initiator-vs-retaliation distinction, reused rather than reinvented), and the logout-pause
 *   behaviour is `tickOffline = false` on the timer key itself.
 *
 * Known gap: familiar combat and "indirect PvP support" (e.g. healing/buffing an ally who is
 * fighting) do not exist as live combat paths in this codebase yet (Summoning combat is
 * unimplemented, R07) - there is nothing to gate. Revisit when familiar combat is built.
 */
object BeginnerProtection {
    /** 60 minutes at 100 cycles/minute (0.6s cycles) - matches PvpSkull's own cycle math. */
    const val PROTECTION_CYCLES = 6000

    /**
     * How long since the last real client packet before a player counts as AFK, in cycles.
     * ~100 cycles = 1 minute. Conservative implementation default per R14.24's own explicit
     * allowance ("AFK definition ... are explicit conservative implementation defaults, not
     * owner-approved exact values") - not tuned against real play data.
     */
    const val AFK_THRESHOLD_CYCLES = 100

    fun isProtected(player: Player): Boolean =
        player.timers.has(NEW_PLAYER_PROTECTION_TIMER) && player.attr[PROTECTION_FORFEITED_ATTR] != true

    /**
     * N212 (owner 2026-10-22: hide and pause it during the intro): the 60 minutes start when a new player's introduction is over -
     * finished, skipped or interrupted ([gg.rsmod.plugins.content.quests.foundation.TutorIntro.finish], first time only) - so the
     * intro neither shows a "Protected" row nor uses up protection time.
     */
    fun grantAfterIntro(player: Player) {
        player.timers[NEW_PLAYER_PROTECTION_TIMER] = PROTECTION_CYCLES
    }

    /**
     * N23 (VRAGEN 19 = A): an Attack click by a protected [attacker] asks him to forfeit his protection when the attack would
     * otherwise be allowed ([wouldFight] = the PvP rules without his own protection); otherwise the click is refused as usual.
     */
    fun needsForfeitConfirmation(
        attacker: Player,
        target: Player,
        wouldFight: (Player, Player) -> Boolean = AreaState::canFightIgnoringOwnProtection,
    ): Boolean = isProtected(attacker) && wouldFight(attacker, target)

    /** R14.25: permanently ends protection once the player has explicitly confirmed an attack. */
    fun forfeit(player: Player) {
        player.attr[PROTECTION_FORFEITED_ATTR] = true
    }

    private fun isActivelyPlaying(
        player: Player,
        world: World,
    ): Boolean {
        val last = player.attr[LAST_ACTIVE_CYCLE_ATTR] ?: return true
        return world.currentCycle - last <= AFK_THRESHOLD_CYCLES
    }

    /**
     * Call once per cycle for every online player (see `beginner_protection.plugin.kts`).
     * [gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER]'s own `tickOffline = false`
     * already stops it counting down while fully logged out; this additionally refunds the
     * cycle a genuinely AFK-but-still-connected player would otherwise lose, so the budget only
     * truly depletes during active play - "online" and "actively playing" are not the same
     * thing, and R14.24 asks specifically for the latter.
     */
    fun tickAfkGuard(
        player: Player,
        world: World,
    ) {
        if (!player.timers.has(NEW_PLAYER_PROTECTION_TIMER)) return
        if (!isActivelyPlaying(player, world)) {
            player.timers[NEW_PLAYER_PROTECTION_TIMER] = player.timers[NEW_PLAYER_PROTECTION_TIMER] + 1
        }
    }
}
