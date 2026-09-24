package gg.rsmod.plugins.content.items.food

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.COMBO_FOOD_DELAY
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.content.mechanics.run.RunEnergy
import java.io.File
import kotlin.math.ceil

/*
 * Every food and drink the 667 cache offers "Eat" / "Drink" on that had no handler (item_option_census.csv 2026-09-24: ales, spirits,
 * cocktails, tea, pies, gnome and Stealing Creation food, fruit and vegetables...). One shared path over one table,
 * `data/cfg/consumables.tsv` (heal, leftover item, eat ticks, combo, message - the 667 Void donor's items.toml keys), bound late and
 * only where no plugin already handles the option, so the hand-made foods ([Food], potions, kebabs) always win.
 *
 * Flow as Void's Eating.kt: food 3 ticks (pies 1, combo food its own clock), drinks 2 ticks on the drink clock; eating mid-fight adds its
 * ticks to the attack delay, drinking does not; default message "You eat/drink the <name>.". Drink effects from Void's Ale.kt,
 * MatureAle.kt, Cocktails.kt, Bottled.kt and Tea.kt ([effect]).
 */
data class Consumable(
    val id: Int,
    val drink: Boolean,
    val healMin: Int,
    val healMax: Int,
    val leftover: Int,
    val ticks: Int,
    val combo: Boolean,
    val message: String,
)

val CONSUMABLES: List<Consumable> =
    File("./data/cfg/consumables.tsv").takeIf { it.exists() }?.readLines().orEmpty()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val c = line.split('\t')
            val drink = c[1] == "drink"
            Consumable(
                id = c[0].toInt(),
                drink = drink,
                healMin = c[2].toInt(),
                healMax = c[3].toInt(),
                leftover = c[4].toInt(),
                ticks = c[5].toIntOrNull() ?: if (c[6] == "1") 1 else if (drink) 2 else 3,
                combo = c[6] == "1",
                message = c[7],
            )
        }

on_world_init_late {
    CONSUMABLES.forEach { food ->
        val def = world.definitions.getNullable(ItemDef::class.java, food.id) ?: return@forEach
        val verb = if (food.drink) "Drink" else "Eat"
        val index = def.inventoryMenu.indexOfFirst { it.equals(verb, ignoreCase = true) }
        if (index < 0 || world.plugins.hasItemOption(food.id, index + 1)) return@forEach
        on_item_option(item = food.id, option = verb) {
            consume(player, food, def.name)
        }
    }
}

fun consume(player: Player, food: Consumable, name: String) {
    val clock = if (food.combo) COMBO_FOOD_DELAY else if (food.drink) POTION_DELAY else FOOD_DELAY
    if (player.timers.has(clock)) return
    val restriction = if (food.drink) gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.DRINK else gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.EAT
    if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(player, restriction)) return
    val slot = player.getInteractingSlot()
    if (player.inventory[slot]?.id != food.id) return
    if (!player.inventory.remove(food.id, 1, beginSlot = slot).hasSucceeded()) return
    if (food.leftover != -1) player.inventory.add(food.leftover, 1, beginSlot = slot)
    player.timers[clock] = food.ticks
    if (!food.drink && player.timers.has(ATTACK_DELAY)) {
        player.timers[ATTACK_DELAY] = player.timers[ATTACK_DELAY] + food.ticks
    }
    player.animate(Anims.EAT_FOOD)
    player.playSound(if (food.drink) Sfx.DRINK else Sfx.EAT)
    player.filterableMessage(food.message.ifEmpty { "You ${if (food.drink) "drink" else "eat"} the ${name.lowercase()}." })
    val heal = (if (food.healMax > food.healMin) world.random(food.healMin..food.healMax) else food.healMin) / Food.LEDGER_UNITS_PER_HITPOINT
    if (heal > 0) player.heal(heal)
    effect(player, name)
}

/** Void Levels.boost: +amount + max*multiplier, never above max + that boost, never lowering a higher level. */
fun boost(player: Player, skill: Int, amount: Int, multiplier: Double = 0.0) {
    val max = player.skills.getMaxLevel(skill)
    val boost = amount + (max * multiplier).toInt()
    val current = player.skills.getCurrentLevel(skill)
    val target = minOf(current + boost, max + boost)
    if (target > current) player.skills.setCurrentLevel(skill, target)
}

/** Void Levels.drain (stacking): -(amount + max*multiplier), never below 1 (0 for Prayer). */
fun drain(player: Player, skill: Int, amount: Int, multiplier: Double = 0.0) {
    val drain = amount + (player.skills.getMaxLevel(skill) * multiplier).toInt()
    val floor = if (skill == Skills.PRAYER) 0 else 1
    player.skills.setCurrentLevel(skill, maxOf(floor, player.skills.getCurrentLevel(skill) - drain))
}

fun effect(player: Player, itemName: String) {
    val name = itemName.lowercase()
    val mature = name.endsWith("(m)") || name.startsWith("mature ")
    val base = name.replace(Regex(""" \((m|\d)\)$"""), "").removePrefix("mature ")
    when (base) {
        "beer" -> { boost(player, Skills.STRENGTH, 1, 0.02); drain(player, Skills.ATTACK, 1, 0.06) }
        "keg of beer" -> { boost(player, Skills.STRENGTH, 2, 0.10); drain(player, Skills.ATTACK, 5, 0.50) }
        "grog" -> { boost(player, Skills.STRENGTH, 3); drain(player, Skills.ATTACK, 6) }
        "bandit's brew" -> {
            boost(player, Skills.THIEVING, 1); boost(player, Skills.ATTACK, 1)
            drain(player, Skills.STRENGTH, 3, 0.06); drain(player, Skills.DEFENCE, 3, 0.06)
        }
        "asgarnian ale", "dragon bitter" -> { boost(player, Skills.STRENGTH, if (mature) 3 else 2); drain(player, Skills.ATTACK, if (mature) 6 else 4) }
        "axeman's folly" -> {
            boost(player, Skills.WOODCUTTING, if (mature) 2 else 1)
            drain(player, Skills.ATTACK, if (mature) 4 else 3); drain(player, Skills.STRENGTH, if (mature) 4 else 3)
        }
        "chef's delight" -> {
            boost(player, Skills.COOKING, ceil((player.skills.getMaxLevel(Skills.COOKING) + if (mature) 1 else 0) * 0.05).toInt())
            drain(player, Skills.ATTACK, if (mature) 3 else 2); drain(player, Skills.STRENGTH, if (mature) 3 else 2)
        }
        "cider" -> {
            boost(player, Skills.FARMING, if (mature) 2 else 1)
            drain(player, Skills.ATTACK, if (mature) 5 else 2); drain(player, Skills.STRENGTH, if (mature) 5 else 2)
        }
        "dwarven stout" -> {
            boost(player, Skills.SMITHING, if (mature) 2 else 1); boost(player, Skills.MINING, if (mature) 2 else 1)
            listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE).forEach { drain(player, it, if (mature) 7 else 2) }
        }
        "greenman's ale" -> {
            boost(player, Skills.HERBLORE, if (mature) 2 else 1)
            drain(player, Skills.ATTACK, if (mature) 2 else 3); drain(player, Skills.STRENGTH, if (mature) 2 else 3)
        }
        "slayer's respite" -> { boost(player, Skills.SLAYER, if (mature) 4 else 2); drain(player, Skills.ATTACK, 2); drain(player, Skills.STRENGTH, 2) }
        "wmb" -> { // "Mature wmb"
            boost(player, Skills.MAGIC, (if (player.skills.getMaxLevel(Skills.MAGIC) < 50) 2 else 3) + 1)
            drain(player, Skills.ATTACK, 5); drain(player, Skills.STRENGTH, 5)
        }
        "wizard blizzard" -> { boost(player, Skills.STRENGTH, 6); drain(player, Skills.ATTACK, 4) }
        "short green guy" -> { boost(player, Skills.STRENGTH, 4); drain(player, Skills.ATTACK, 3) }
        "drunk dragon" -> { boost(player, Skills.STRENGTH, 5); drain(player, Skills.ATTACK, 4) }
        "choc saturday" -> { boost(player, Skills.STRENGTH, 7); drain(player, Skills.ATTACK, 4) }
        "blurberry special" -> { boost(player, Skills.STRENGTH, 6); drain(player, Skills.ATTACK, 4) }
        "karamjan rum" -> { boost(player, Skills.STRENGTH, 5); drain(player, Skills.ATTACK, 4) }
        "vodka", "gin", "brandy", "whisky" -> { boost(player, Skills.STRENGTH, 1, 0.05); drain(player, Skills.ATTACK, 3, 0.02) }
        "bottle of wine" -> drain(player, Skills.ATTACK, 3)
        "braindeath 'rum'" -> {
            boost(player, Skills.STRENGTH, 3); boost(player, Skills.MINING, 1)
            drain(player, Skills.DEFENCE, 0, 0.10)
            listOf(Skills.ATTACK, Skills.PRAYER, Skills.RANGED, Skills.MAGIC, Skills.AGILITY, Skills.HERBLORE).forEach { drain(player, it, 0, 0.05) }
        }
        "cup of tea" -> boost(player, Skills.ATTACK, 3)
        "nettle tea" -> RunEnergy.renew(player, 5.0)
        "guthix rest" -> {
            RunEnergy.renew(player, 5.0)
            player.heal(5, 5) // Void: +50 lifepoints, up to 50 above the maximum
        }
    }
}
