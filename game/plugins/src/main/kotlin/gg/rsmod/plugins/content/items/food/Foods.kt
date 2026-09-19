package gg.rsmod.plugins.content.items.food

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.COMBO_FOOD_DELAY
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.playSound
import kotlin.math.max
import kotlin.random.Random

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Foods {
    /** "approximately 18 seconds" (OSRS Wiki "Strange fruit"). */
    const val STRANGE_FRUIT_IMMUNITY_TICKS = 30

    fun canEat(
        p: Player,
        food: Food,
    ): Boolean = !p.timers.has(if (food.comboFood) COMBO_FOOD_DELAY else FOOD_DELAY)

    fun eat(
        p: Player,
        food: Food,
    ) {
        val delay = if (food.comboFood) COMBO_FOOD_DELAY else FOOD_DELAY
        val anim = if (p.hasEquipped(EquipmentType.WEAPON, Items.SLED)) Anims.EAT_FOOD_ON_SLED else Anims.EAT_FOOD

        val kebabEffect: Pair<Int, String>? =
            when {
                food == Food.KEBAB -> calculateKebabEffect(p)
                else -> null
            }

        // Both the food table and the kebab roll are on the x10 ledger unit; lifepoints are 1:1.
        val heal =
            when {
                food == Food.KEBAB -> (kebabEffect?.first ?: 0) / Food.LEDGER_UNITS_PER_HITPOINT
                else -> food.hitpoints
            }

        // capValue is an allowance ABOVE the maximum (Player.alterLifepoints). The old value,
        // current level + 10, let a rocktail raise HP to nearly twice the maximum.
        val overHeal =
            when (food) {
                Food.ROCKTAIL -> Food.ROCKTAIL_OVERHEAL_HITPOINTS
                else -> 0
            }

        val oldHp = p.skills.getCurrentLevel(Skills.CONSTITUTION)
        val foodName =
            p.world.definitions
                .get(ItemDef::class.java, food.item)
                .name

        p.animate(anim)
        p.playSound(Sfx.EAT)
        if (heal > 0) {
            p.heal(heal, if (food.overheal) overHeal else 0)
        }

        p.timers[delay] = food.tickDelay
        // OSRS: eating mid-attack delays the next attack by the food's delay on top of what is
        // left; out of combat it adds no attack delay (OSRS Wiki Food; Void Eating.consume + tests).
        // It used to overwrite the timer, which shortened a longer remaining attack delay.
        if (p.timers.has(ATTACK_DELAY)) {
            p.timers[ATTACK_DELAY] = p.timers[ATTACK_DELAY] + food.tickDelay
        }

        if (food == Food.KARAMBWAN) {
            // Eating Karambwans also blocks drinking potions.
            p.timers[POTION_DELAY] = 3
        }

        if (food == Food.STRANGE_FRUIT) {
            // 18 seconds = 30 ticks of poison and venom immunity after the cure (OSRS Wiki "Strange fruit").
            gg.rsmod.plugins.content.mechanics.poison.Venom.cure(p, immunityTicks = STRANGE_FRUIT_IMMUNITY_TICKS, announce = false)
            gg.rsmod.plugins.content.mechanics.poison.Poison.cure(p)
            p.timers[gg.rsmod.game.model.timer.POISON_IMMUNITY] = maxOf(if (p.timers.has(gg.rsmod.game.model.timer.POISON_IMMUNITY)) p.timers[gg.rsmod.game.model.timer.POISON_IMMUNITY] else 0, STRANGE_FRUIT_IMMUNITY_TICKS)
            p.timers[gg.rsmod.game.model.timer.VENOM_IMMUNITY] = maxOf(if (p.timers.has(gg.rsmod.game.model.timer.VENOM_IMMUNITY)) p.timers[gg.rsmod.game.model.timer.VENOM_IMMUNITY] else 0, STRANGE_FRUIT_IMMUNITY_TICKS)
            gg.rsmod.plugins.content.mechanics.run.RunEnergy.renew(p, 30.0)
        }

        if (food == Food.CAVEFISH) {
            val combatSkills = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)
            val selectedSkill = combatSkills[Random.nextInt(combatSkills.size)]

            // Apply a boost of 2 points to the randomly selected combat skill
            val currentLevel = p.skills.getCurrentLevel(selectedSkill)
            val maxLevel = p.skills.getMaxLevel(selectedSkill)
            val newLevel = minOf(currentLevel + 2, maxLevel + 2) // Ensure that the boost doesn't exceed the maximum allowed level

            p.skills.setCurrentLevel(selectedSkill, newLevel)
        }

        var message = "You eat the ${foodName.lowercase()}."

        if (food.message.isNotEmpty()) {
            message = food.message
        }

        if (food == Food.KEBAB) {
            p.filterableMessage("You eat the kebab.")
            message = kebabEffect?.second ?: ""
        }

        p.filterableMessage(message)
        if (p.skills.getCurrentLevel(Skills.CONSTITUTION) > oldHp && food != Food.KEBAB) {
            p.filterableMessage("It heals some health.")
        }
    }

    // Effect source from RS Wiki October 2011: https://runescape.wiki/w/Kebab?oldid=4868155
    fun calculateKebabEffect(player: Player): Pair<Int, String> {
        val randomNumber = Random.nextDouble(100.0)
        return when {
            randomNumber < 8.71 -> {
                // Rare: No effect
                Pair(0, "That kebab didn't seem to do a lot.")
            }
            randomNumber < 69.95 -> {
                // Common: Heals 10% of total health (x10 ledger: 10% of max*10 = the max level)
                val healAmount = player.skills.getMaxLevel(Skills.CONSTITUTION)
                Pair(healAmount, "It restores some health.")
            }
            randomNumber < 91.07 -> {
                // Uncommon: Heals 100-200 health
                val healAmount = Random.nextInt(100, 201)
                Pair(healAmount, "That was a good kebab. You feel a lot better.")
            }
            randomNumber < 94.72 -> {
                // Rare: Heals up to 300 health and raises Attack, Strength, and Defence by 2-3 levels
                val boost = Random.nextInt(2, 4)
                listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE).forEach { skill ->
                    player.skills.setCurrentLevel(skill, player.skills.getCurrentLevel(skill) + boost)
                }
                Pair(300, "Wow, that was an amazing kebab! You feel really invigorated.")
            }
            else -> {
                // Rare: Lowers a random skill (including non-combat skills) by a few levels
                val randomSkill = (0..24).filter { it != Skills.CONSTITUTION }.random()
                player.skills.setCurrentLevel(
                    randomSkill,
                    max(
                        0,
                        player.skills.getCurrentLevel(randomSkill) - Random.nextInt(1, 4),
                    ),
                )
                Pair(0, "That tasted a bit dodgy. You feel a bit ill.")
            }
        }
    }
}
