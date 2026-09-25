package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.getInteractingItemSlot
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.playSound

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Potions {
    private const val TICK_DELAY = 3

    private fun canDrink(player: Player): Boolean = !player.timers.has(POTION_DELAY)

    fun drink(
        player: Player,
        potion: Potion,
    ) {
        drinkAt(player, potion, player.getInteractingItemSlot())
    }

    /** Same potion path for non-inventory controls such as the HP orb's Cure option. */
    fun drinkAt(
        player: Player,
        potion: Potion,
        slot: Int,
    ) {
        if (!canDrink(player) ||
            gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.DRINK,
            ) ||
            !potion.potionType.canDrink(player)
        ) {
            return
        }
        if (player.inventory.remove(item = potion.item, beginSlot = slot).hasSucceeded()) {
            if (potion.replacement != -1) {
                player.inventory.add(item = potion.replacement, beginSlot = slot)
            }
            val anim =
                if (player.hasEquipped(
                        EquipmentType.WEAPON,
                        Items.SLED,
                    )
                ) {
                    Anims.EAT_FOOD_ON_SLED
                } else {
                    Anims.EAT_FOOD
                }
            player.animate(anim)
            player.playSound(Sfx.LIQUID)
            // Owner live report 2026-09-17c ("super combat if u drink it doesnt give stats"): the path reads correct, so the levels around
            // the effect are traced - the next live sip shows whether the boost is applied and something undoes it, or it never applies.
            val combat = intArrayOf(0, 1, 2, 4, 6)
            val before = combat.map { player.skills.getCurrentLevel(it) }
            try {
                potion.potionType.apply(player)
            } finally {
                // Audit C-01: the dose is already consumed, so the delays must hold even when an effect throws;
                // otherwise a failing effect allowed several drinks in the same tick.
                player.timers[POTION_DELAY] = TICK_DELAY
                player.timers[FOOD_DELAY] = TICK_DELAY
            }
            gg.rsmod.game.model.AvTrace.log {
                "potion drink item=${potion.item} type=${potion.potionType} att/def/str/rng/mag before=$before after=${combat.map { player.skills.getCurrentLevel(it) }}"
            }
            // 667 barbarian mixes: the base potion's effect, then the mix heal and the OSRS mix message (BarbarianMixes).
            if (potion.mixHeal > 0) {
                player.heal(potion.mixHeal)
                player.filterableMessage(BarbarianMixes.MESSAGE)
                return
            }
            val potionName =
                player.world.definitions
                    .get(ItemDef::class.java, potion.item)
                    .name
            // OSRS-imported potions are named without a space before the dose ("Super combat potion(4)").
            var message = "You drink some of your ${potionName.replace(Regex(" ?\\(([1234])\\)$"), "").lowercase()}."
            if (potion.potionType.message.isNotEmpty()) {
                message = potion.potionType.message
                player.filterableMessage(message)
                return
            }
            player.filterableMessage(message)
            if (potion.replacement == Items.VIAL || potion.replacement == Items.BEER_GLASS) {
                player.filterableMessage("You have finished your potion.")
            } else {
                val num = potionName.substringAfter("(").substringBefore(")").toInt() - 1
                val dosesLeftMessage = "You have $num doses of potion left."
                player.filterableMessage(dosesLeftMessage)
            }
        }
    }
}
