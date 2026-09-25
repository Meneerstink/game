package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.entity.zoneTile

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.pvp.AreaState

/*
 * Blighted overload: creation from Chitin and the drink effect, exactly as BlightedOverload documents (OSRS Wiki).
 * ADAPTED (no OSRS text found): the refusal message in a safe zone and the worn-off message. Re-drinking refreshes the effect.
 */

// ---- creation: chitin + super combat (4) + ranging (4) + magic (4) -> blighted overload (4), 83 Herblore, 125 xp ------------

fun hasIngredients(p: Player): Boolean =
    p.inventory.contains(BlightedOverload.CHITIN) && BlightedOverload.INGREDIENT_POTIONS.all { p.inventory.contains(it) }

fun startBlighted(
    player: Player,
    product: Int,
    amount: Int,
) {
    player.queue(TaskPriority.WEAK) {
        repeat(amount) {
            if (!hasIngredients(player)) return@queue
            val removed =
                player.inventory.remove(BlightedOverload.CHITIN, assureFullRemoval = true).hasSucceeded() &&
                    BlightedOverload.INGREDIENT_POTIONS.all { player.inventory.remove(it, assureFullRemoval = true).hasSucceeded() }
            if (!removed) return@queue
            player.animate(Anims.MIX_POTION)
            player.playSound(Sfx.GRIND)
            player.inventory.add(product)
            player.addXp(Skills.HERBLORE, BlightedOverload.EXPERIENCE)
            player.filterableMessage("You mix the chitin into your potions to create a blighted overload.")
            wait(BlightedOverload.MIX_TICKS)
        }
    }
}

(BlightedOverload.INGREDIENT_POTIONS.map { it to BlightedOverload.CHITIN } +
    listOf(
        BlightedOverload.INGREDIENT_POTIONS[0] to BlightedOverload.INGREDIENT_POTIONS[1],
        BlightedOverload.INGREDIENT_POTIONS[0] to BlightedOverload.INGREDIENT_POTIONS[2],
        BlightedOverload.INGREDIENT_POTIONS[1] to BlightedOverload.INGREDIENT_POTIONS[2],
    )).forEach { (a, b) ->
    on_item_on_item(item1 = a, item2 = b) {
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < BlightedOverload.LEVEL) {
            player.message("You need a Herblore level of ${BlightedOverload.LEVEL} to make a blighted overload.")
            return@on_item_on_item
        }
        if (!hasIngredients(player)) {
            player.message("You need a super combat potion (4), a ranging potion (4), a magic potion (4) and chitin to make a blighted overload.")
            return@on_item_on_item
        }
        player.queue(TaskPriority.STRONG) {
            produceItemBox(
                BlightedOverload.DOSES[0],
                option = SkillDialogueOption.MAKE,
                title = "Choose how many you wish to make, then<br>click on the item to begin.",
                logic = ::startBlighted,
            )
        }
    }
}

// ---- drinking -----------------------------------------------------------------------------------------------------------

val BLIGHTED_TICK = TimerKey()
val ticksLeft = AttributeKey<Int>()
val safeTicks = AttributeKey<Int>()

fun applyBoosts(p: Player) {
    listOf(Skills.ATTACK, Skills.STRENGTH, Skills.RANGED, Skills.MAGIC).forEach { skill ->
        p.skills.setCurrentLevel(skill, BlightedOverload.boosted(skill, p.skills.getMaxLevel(skill)))
    }
    val base = p.skills.getMaxLevel(Skills.DEFENCE)
    p.skills.setCurrentLevel(Skills.DEFENCE, BlightedOverload.drainedDefence(p.skills.getCurrentLevel(Skills.DEFENCE), base))
}

fun endEffect(p: Player) {
    p.timers.remove(BLIGHTED_TICK)
    p.attr.remove(ticksLeft)
    p.attr.remove(safeTicks)
}

BlightedOverload.DOSES.forEach { dose ->
    on_item_option(item = dose, option = "drink") {
        val slot = player.getInteractingItemSlot()
        if (player.timers.has(POTION_DELAY)) return@on_item_option
        if (!AreaState.isDangerous(player.zoneTile())) {
            player.message("You can only drink a blighted overload in a dangerous area.")
            return@on_item_option
        }
        if (!player.inventory.remove(item = dose, beginSlot = slot).hasSucceeded()) return@on_item_option
        player.inventory.add(item = BlightedOverload.afterSip(dose), beginSlot = slot)
        player.animate(Anims.EAT_FOOD)
        player.playSound(Sfx.LIQUID)
        player.timers[POTION_DELAY] = 3
        player.timers[FOOD_DELAY] = 3
        repeat(BlightedOverload.DAMAGE_HITS) { i -> player.hit(BlightedOverload.DAMAGE_PER_HIT, delay = i * BlightedOverload.DAMAGE_INTERVAL) }
        applyBoosts(player)
        player.attr[ticksLeft] = BlightedOverload.DURATION_TICKS
        player.attr[safeTicks] = 0
        player.timers[BLIGHTED_TICK] = 1
        player.filterableMessage("You drink some of your blighted overload potion.")
        val left = BlightedOverload.doseOf(dose) - 1
        player.filterableMessage(if (left == 0) "You have finished your potion." else "You have $left dose${if (left == 1) "" else "s"} of potion left.")
    }
}

on_timer(BLIGHTED_TICK) {
    val left = (player.attr[ticksLeft] ?: 0) - 1
    val inSafe = !AreaState.isDangerous(player.zoneTile())
    val safe = if (inSafe) (player.attr[safeTicks] ?: 0) + 1 else 0
    if (left <= 0 || safe > BlightedOverload.SAFE_ZONE_GRACE_TICKS) {
        endEffect(player)
        player.message("The effects of the blighted overload have worn off.")
        return@on_timer
    }
    player.attr[ticksLeft] = left
    player.attr[safeTicks] = safe
    if ((BlightedOverload.DURATION_TICKS - left) % BlightedOverload.REAPPLY_TICKS == 0) applyBoosts(player)
    player.timers[BLIGHTED_TICK] = 1
}

on_logout { endEffect(player) }
