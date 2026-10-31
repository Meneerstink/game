package gg.rsmod.plugins.content.items.food

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.content.mechanics.run.RunEnergy

/** N11 G-register drinks. Item identity matters: keg names abbreviate mature ales, and all teas share a name.
 * OSRS Wiki per-item sources and source conflicts: docs/N11-bundle-5.md.
 */
object GeDrinks {
    private val melee = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE)

    /** Only real, unnoted keg ids; consecutive even ids are notes. Index follows the brewing table. */
    fun keg(id: Int): Pair<Int, Boolean>? {
        if (id !in 5771..5929 || id % 2 == 0) return null
        val group = (id - 5771) / 8
        return group % 10 to (group >= 10)
    }

    fun boost(p: Player, skill: Int, amount: Int) {
        val current = p.skills.getCurrentLevel(skill)
        val target = minOf(current + amount, p.skills.getMaxLevel(skill) + amount)
        if (target > current) p.skills.setCurrentLevel(skill, target)
    }

    /** OSRS alcohol drains use CURRENT levels, so successive drinks stack with diminishing percentage loss. */
    fun drain(p: Player, skill: Int, flat: Int, percent: Int = 0) {
        val current = p.skills.getCurrentLevel(skill)
        p.skills.setCurrentLevel(skill, maxOf(1, current - flat - current * percent / 100))
    }

    fun apply(p: Player, id: Int, wasInjured: Boolean): Boolean {
        val keg = keg(id)
        if (keg != null) {
            val (type, mature) = keg
            val extra = if (mature) 1 else 0
            when (type) {
                0, 2 -> {
                    if (type == 0) {
                        boost(p, Skills.MINING, 1 + extra)
                        boost(p, Skills.SMITHING, 1 + extra)
                    } else boost(p, Skills.HERBLORE, 1 + extra)
                    melee.forEach { drain(p, it, 2 + extra, 4) }
                }
                1, 4 -> {
                    boost(p, Skills.STRENGTH, 2 + extra)
                    drain(p, Skills.ATTACK, if (type == 1) 2 + extra else 2, 5)
                }
                3 -> {
                    boost(p, Skills.MAGIC, 2 + extra + p.skills.getMaxLevel(Skills.MAGIC) * 2 / 100)
                    melee.forEach { drain(p, it, 1 + extra, 5) }
                }
                5 -> Unit // Moonlight mead only heals; there is no combat drain.
                6, 7, 8, 9 -> {
                    val skill = when (type) {
                        6 -> Skills.WOODCUTTING
                        7 -> Skills.COOKING
                        8 -> Skills.SLAYER
                        else -> Skills.FARMING
                    }
                    val amount = when (type) {
                        7 -> 1 + extra + p.skills.getMaxLevel(skill) * 5 / 100
                        8 -> if (mature) 4 else 2
                        else -> 1 + extra
                    }
                    boost(p, skill, amount)
                    intArrayOf(Skills.ATTACK, Skills.STRENGTH).forEach {
                        drain(p, it, 2 + extra, if (type == 7) 5 else 2)
                    }
                }
            }
            return true
        }
        when (id) {
            4243, 4245, 4246 -> if (wasInjured) RunEnergy.renew(p, 5.0)
            7730, 7731 -> boost(p, Skills.CONSTRUCTION, 1)
            7733, 7734 -> boost(p, Skills.CONSTRUCTION, 2)
            7736, 7737 -> boost(p, Skills.CONSTRUCTION, 3)
            197 -> chalice(p, p.world.random(0..31))
            else -> return false
        }
        return true
    }

    /** Exact 32-way Wiki distribution; exposed roll makes every outcome testable without statistical assertions. */
    fun chalice(p: Player, roll: Int) {
        require(roll in 0..31)
        val hp = p.skills.getMaxLevel(Skills.CONSTITUTION)
        val message = when (roll) {
            in 0..8 -> {
                p.heal(hp * 15 / 100)
                boost(p, Skills.THIEVING, 1)
                "You feel a lot better."
            }
            in 9..16 -> {
                melee.forEach { drain(p, it, 1) }
                boost(p, Skills.CRAFTING, 1)
                "You feel a little strange."
            }
            in 17..23 -> "It has a slight taste of apricot."
            in 24..27 -> {
                p.heal(hp * 5 / 100)
                "It heals some health."
            }
            in 28..29 -> {
                chaliceDamage(p, hp * 5 / 100)
                "That tasted a bit dodgy. You feel a bit ill."
            }
            30 -> {
                chaliceDamage(p, hp / 2)
                melee.forEach { drain(p, it, p.world.random(1..3)) }
                "That tasted very dodgy. You feel very ill."
            }
            else -> {
                p.heal(hp * 30 / 100)
                melee.forEach { boost(p, it, 4) }
                "Wow! That was amazing! You feel really invigorated."
            }
        }
        p.filterableMessage(message)
    }

    fun damage(requested: Int, currentHp: Int): Int = minOf(requested, (currentHp - 4).coerceAtLeast(0))

    private fun chaliceDamage(p: Player, requested: Int) {
        val hit = p.hit(damage(requested, p.getCurrentLifepoints()))
        // Re-evaluate when queued hits resolve, as LocatorOrb does: a combat hit cannot turn this into a lethal drink.
        hit.setCancelIf {
            hit.hitmarks.single().damage = damage(requested, p.getCurrentLifepoints())
            p.isDead()
        }
    }
}
