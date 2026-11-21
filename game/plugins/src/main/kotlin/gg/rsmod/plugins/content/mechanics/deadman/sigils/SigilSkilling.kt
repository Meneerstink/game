package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.container.ItemTransaction
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.filterableMessage

/**
 * PROJECT48 SIGILS - the gathering sigils (OSRS Wiki Deadman: Armageddon/Sigils): S03 Enhanced Harvest (3x resources), S14 the Forager
 * (2x; no stacking with Enhanced Harvest), S43 Storage (90% of new resources go straight to the bank) and S58 Remote Storage (all of them).
 * Mining, Fishing, Woodcutting and Farming give their resource through [give] instead of `inventory.add`.
 */
object SigilSkilling {
    /** The multiplier of the gathered resource: 3 (Enhanced Harvest), 2 (the Forager) or 1. */
    fun multiplier(player: Player): Int =
        when {
            Sigils.effective(player, Sigil.ENHANCED_HARVEST) -> 3
            Sigils.effective(player, Sigil.THE_FORAGER) -> 2
            else -> 1
        }

    /** S10 Sigil of the Abyss: 5x as many runes when Runecrafting. */
    fun runecraftMultiplier(player: Player): Int = if (Sigils.effective(player, Sigil.THE_ABYSS)) 5 else 1

    /** Whether this gathered resource goes to the bank (S58 always, S43 in 90% of the cases). */
    fun toBank(player: Player): Boolean =
        Sigils.effective(player, Sigil.REMOTE_STORAGE) ||
            (Sigils.effective(player, Sigil.STORAGE) && player.world.randomDouble() < 0.90)

    /**
     * Gives [amount] of [itemId] as the gathered resource of the player: multiplied by the harvest sigils, then sent to the bank or the
     * inventory. Returns the inventory transaction (a bank delivery counts as a complete one); when the bank cannot take it the
     * inventory is used.
     */
    fun give(
        player: Player,
        itemId: Int,
        amount: Int = 1,
    ): ItemTransaction {
        if (player.attuned().isEmpty()) return player.inventory.add(itemId, amount)
        val total = amount * multiplier(player)
        if (toBank(player)) {
            val banked = player.bank.add(itemId, total)
            if (banked.hasSucceeded()) {
                player.filterableMessage("Your sigil sends the resources to your bank.")
                return ItemTransaction(total, total, emptyList())
            }
        }
        return player.inventory.add(itemId, total)
    }

    /** S42 Litheness: every 10 ticks of running, 2x the Agility level as Agility XP; none inside a safe zone. */
    val LITHENESS_TIMER = gg.rsmod.game.model.timer.TimerKey()
    const val LITHENESS_TICKS = 10

    fun lithenessTick(player: Player) {
        player.timers[LITHENESS_TIMER] = LITHENESS_TICKS
        if (!Sigils.effective(player, Sigil.LITHENESS)) return
        val moving = player.world.currentCycle - player.lastStepCycle <= 2
        if (!moving || !player.isRunning() || gg.rsmod.plugins.content.mechanics.death.SafeDeath.isSafe(player)) return
        player.addXp(gg.rsmod.plugins.api.Skills.AGILITY, 2.0 * player.skills.getMaxLevel(gg.rsmod.plugins.api.Skills.AGILITY))
    }

    private fun Player.attuned() = Sigils.attuned(this)
}

/** S12 Sigil of the Eternal Jeweller: teleporting with charged jewellery never uses a charge (the used item is kept as it is). */
object SigilJeweller {
    fun keep(
        player: Player,
        usedId: Int,
        replacement: Int,
    ): Int = if (Sigils.effective(player, Sigil.THE_ETERNAL_JEWELLER)) usedId else replacement
}
