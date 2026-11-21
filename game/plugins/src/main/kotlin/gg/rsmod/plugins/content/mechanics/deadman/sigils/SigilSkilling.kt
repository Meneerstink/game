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

    /** S59 Woodcraft: the planks of a chopped log (price per plank in coins), or the log itself without the sigil / the coins / with an infernal axe. */
    private val PLANKS: Map<Int, Pair<Int, Int>> =
        mapOf(
            gg.rsmod.plugins.api.cfg.Items.LOGS to (gg.rsmod.plugins.api.cfg.Items.PLANK to 50),
            gg.rsmod.plugins.api.cfg.Items.OAK_LOGS to (gg.rsmod.plugins.api.cfg.Items.OAK_PLANK to 125),
            gg.rsmod.plugins.api.cfg.Items.TEAK_LOGS to (gg.rsmod.plugins.api.cfg.Items.TEAK_PLANK to 250),
            gg.rsmod.plugins.api.cfg.Items.MAHOGANY_LOGS to (gg.rsmod.plugins.api.cfg.Items.MAHOGANY_PLANK to 700),
        )

    private fun woodcraft(
        player: Player,
        log: Int,
    ): Int {
        val plank = PLANKS[log] ?: return log
        if (!Sigils.effective(player, Sigil.WOODCRAFT)) return log
        val weapon = player.getEquipment(gg.rsmod.plugins.api.EquipmentType.WEAPON)?.id
        if (weapon != null && player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, weapon).name.lowercase().contains("infernal axe")) return log
        if (!player.inventory.remove(gg.rsmod.plugins.api.cfg.Items.COINS_995, plank.second).hasSucceeded()) {
            player.filterableMessage("You need ${plank.second} coins in your inventory for your sigil to turn the log into a plank.")
            return log
        }
        return plank.first
    }

    /**
     * S32 Infernal Chef (a caught fish is cooked at once: Cooking XP, no level needed, never burnt) and S33 Infernal Smith (a mined ore that
     * needs no second ore becomes its bar at once: Smithing XP) - each a 20% chance.
     */
    private fun infernal(
        player: Player,
        item: Int,
    ): Int {
        if (Sigils.effective(player, Sigil.THE_INFERNAL_CHEF)) {
            val fish = gg.rsmod.plugins.content.skills.cooking.CookingData.values.firstOrNull { it.raw == item }
            if (fish != null && player.world.randomDouble() < 0.20) {
                player.addXp(gg.rsmod.plugins.api.Skills.COOKING, fish.experience)
                return fish.cooked
            }
        }
        if (Sigils.effective(player, Sigil.THE_INFERNAL_SMITH)) {
            val bar =
                gg.rsmod.plugins.content.skills.smithing.data.SmeltingData.values
                    .firstOrNull { it.primaryOre == item && it.secondaryCount == 0 }
            if (bar != null && player.world.randomDouble() < 0.20) {
                player.addXp(gg.rsmod.plugins.api.Skills.SMITHING, bar.experience)
                return bar.product
            }
        }
        return item
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
        gathered: Int,
        amount: Int = 1,
    ): ItemTransaction {
        if (player.attuned().isEmpty()) return player.inventory.add(gathered, amount)
        val itemId = infernal(player, woodcraft(player, gathered))
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
