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

    /** OSRS Wiki "Deadman Mode": "Eating and drinking potions is blocked for 3 seconds (5 ticks) after banking or
     * un-noting outside of a safezone". */
    const val POST_BANK_CONSUME_BLOCK_TICKS = 5

    /** Closing a bank or deposit box outside every guarded zone holds the existing food, combo-food and potion gates
     * for at least [POST_BANK_CONSUME_BLOCK_TICKS]; every eat/drink route already checks those timers. */
    fun onBankClosed(player: Player) {
        if (GuardedZones.contains(player.tile)) return
        listOf(
            gg.rsmod.game.model.timer.FOOD_DELAY,
            gg.rsmod.game.model.timer.COMBO_FOOD_DELAY,
            gg.rsmod.game.model.timer.POTION_DELAY,
        ).forEach { key ->
            if (!player.timers.has(key) || player.timers[key] < POST_BANK_CONSUME_BLOCK_TICKS) {
                player.timers[key] = POST_BANK_CONSUME_BLOCK_TICKS
            }
        }
    }

    /** OSRS Wiki "Deadman Mode" (2 September 2026): "Players who have recently been in combat can no longer deposit
     * items worth 20,000 GP or more for 24 seconds, except when using a bank in a safe zone." 24 s = 40 ticks. */
    const val COMBAT_DEPOSIT_BLOCK_TICKS = 40
    const val COMBAT_DEPOSIT_VALUE_LIMIT = 20_000L

    val LAST_COMBAT_CYCLE_ATTR = gg.rsmod.game.model.attr.AttributeKey<Int>()

    /** Called from `Combat.postAttack` for every player on either side of an attack. */
    fun markCombat(player: Player) {
        player.attr[LAST_COMBAT_CYCLE_ATTR] = player.world.currentCycle
    }

    /** Whether depositing a stack worth [value] (guide price x amount) is refused right now. */
    fun blocksDeposit(
        player: Player,
        value: Long,
        now: Int = player.world.currentCycle,
    ): Boolean {
        if (value < COMBAT_DEPOSIT_VALUE_LIMIT || GuardedZones.contains(player.tile)) return false
        val last = player.attr[LAST_COMBAT_CYCLE_ATTR] ?: return false
        return now - last < COMBAT_DEPOSIT_BLOCK_TICKS
    }

    fun denyBank(player: Player): Boolean {
        if (!isBankBlocked(player)) return false
        player.message(CityGuards.GREETING.format(player.username))
        player.message("Skulled players can't use the banks of a guarded area.")
        return true
    }
}
