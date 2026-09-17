package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message

/**
 * Deadman bank rule (OSRS Wiki "Deadman Mode", owner instruction 2026-09-16): "being skulled will
 * prevent you from accessing the guarded areas, as level 1337 guards and Wizguards will immediately
 * attack the player on entry. This means that the skulled player has to utilise banks that are not
 * in safe-zones and these become hotspots for player killers."
 *
 * So a skulled player is refused only at banks inside a guarded city (where the guards are already
 * on them - [CityGuards.onZoneCheck]); every bank outside the guarded cities stays usable while
 * skulled. The earlier 10-second bank timer and the loot-key bank block were not Deadman rules and
 * are gone.
 */
object BankSecurity {
    fun isBankBlocked(player: Player): Boolean =
        PvpSkull.isSkulled(player) && GuardedZones.contains(player.tile)

    fun denyBank(player: Player): Boolean {
        if (!isBankBlocked(player)) return false
        player.message(CityGuards.GREETING.format(player.username))
        player.message("Skulled players can't use the banks of a guarded area.")
        return true
    }
}
