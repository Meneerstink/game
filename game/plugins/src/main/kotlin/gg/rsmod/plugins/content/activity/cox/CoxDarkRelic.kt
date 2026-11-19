package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options

/**
 * N640: Dark relic (CoX reward, OSRS item 21027). OSRS Wiki "Dark relic": Commune = "A dark power emanates from the relic. You
 * sense that this power can be directed." then the player picks a skill and gets experience = 150 x level (Raids skills: combat
 * skills, Mining, Woodcutting, Herblore, Farming, Hunter, Cooking, Fishing, Thieving, Firemaking, Agility) or 50 x level (other
 * skills, Construction included). Destroy asks "Are you sure you wish to destroy it? You'll have to earn another in the Chambers of
 * Xeric."
 * ONZEKER (no source): the exact skill-choice dialogue and whether the relic is used up by communing - it is consumed here (the
 * Destroy text says the player has to earn another one). Summoning/Dungeoneering (667-only skills) are not offered.
 */
object CoxDarkRelic {
    const val OSRS_ITEM = 21027
    const val COMMUNE_MESSAGE = "A dark power emanates from the relic. You sense that this power can be directed."
    const val DESTROY_QUESTION = "Are you sure you wish to destroy it? You'll have to earn another in the Chambers of Xeric."

    private val RAID_SKILLS: Set<Int> =
        setOf(
            Skills.ATTACK, Skills.DEFENCE, Skills.STRENGTH, Skills.CONSTITUTION, Skills.RANGED, Skills.PRAYER, Skills.MAGIC,
            Skills.MINING, Skills.WOODCUTTING, Skills.HERBLORE, Skills.FARMING, Skills.HUNTER, Skills.COOKING, Skills.FISHING,
            Skills.THIEVING, Skills.FIREMAKING, Skills.AGILITY,
        )

    /** The skills of OSRS, in the order of the OSRS skill tab. */
    val CHOICES: List<Int> =
        listOf(
            Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.PRAYER, Skills.MAGIC, Skills.RUNECRAFTING,
            Skills.CONSTRUCTION, Skills.CONSTITUTION, Skills.AGILITY, Skills.HERBLORE, Skills.THIEVING, Skills.CRAFTING,
            Skills.FLETCHING, Skills.SLAYER, Skills.HUNTER, Skills.MINING, Skills.SMITHING, Skills.FISHING, Skills.COOKING,
            Skills.FIREMAKING, Skills.WOODCUTTING, Skills.FARMING,
        )

    fun xpFor(skill: Int, level: Int): Double = (if (skill in RAID_SKILLS) 150 else 50) * level.toDouble()

    fun commune(p: Player, slot: Int, item: Int) {
        p.message(COMMUNE_MESSAGE)
        p.queue {
            val names = CHOICES.map { Skills.getSkillName(p.world, it) }
            val pick = options(*names.toTypedArray(), title = "Which skill should receive the power?")
            if (pick < 1) return@queue
            val skill = CHOICES[pick - 1]
            if (p.inventory[slot]?.id != item) return@queue
            if (!p.inventory.remove(item, 1, beginSlot = slot).hasSucceeded()) return@queue
            val xp = xpFor(skill, p.skills.getMaxLevel(skill))
            p.addXp(skill, xp)
            p.message("You commune with the relic and receive ${xp.toLong()} ${names[pick - 1]} experience.")
        }
    }

    fun destroy(p: Player, slot: Int, item: Int) {
        p.queue {
            val yes = options("Yes.", "No.", title = DESTROY_QUESTION)
            if (yes != 1) return@queue
            if (p.inventory[slot]?.id != item) return@queue
            p.inventory.remove(item, 1, beginSlot = slot)
        }
    }
}
