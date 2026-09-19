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

    /** Owner 2026-09-19: "only 2 tick weapons should cancel the banking of every pker in a dangerous bank". */
    const val BANK_CLOSING_MAX_ATTACK_TICKS = 2

    private val BANKING_INTERFACES =
        setOf(
            gg.rsmod.plugins.content.inter.bank.Bank.BANK_INTERFACE_ID,
            gg.rsmod.plugins.content.inter.bank.Bank.DEPOSIT_BOX_INTERFACE_ID,
        )

    /**
     * Whether an attack by [attacker] on [victim] leaves the victim's bank (or deposit box) open. In a dangerous bank
     * (outside every guarded zone) a player's attack only closes it when the attacker's current attack delay is at most
     * [BANK_CLOSING_MAX_ATTACK_TICKS] (2-tick weapons, or a 3-tick weapon on rapid); slower attacks let the victim keep
     * banking. Npc attacks and any other open interface are unaffected (still closed on every attack).
     */
    fun keepsBankOpen(
        attacker: gg.rsmod.game.model.entity.Pawn,
        victim: Player,
        attackDelay: Int,
    ): Boolean =
        attacker is Player &&
            victim.interfaces.getModal() in BANKING_INTERFACES &&
            AreaState.isDangerous(victim.tile) &&
            attackDelay > BANK_CLOSING_MAX_ATTACK_TICKS

    fun denyBank(player: Player): Boolean {
        if (!isBankBlocked(player)) return false
        player.message(CityGuards.GREETING.format(player.username))
        player.message("Skulled players can't use the banks of a guarded area.")
        return true
    }
}
