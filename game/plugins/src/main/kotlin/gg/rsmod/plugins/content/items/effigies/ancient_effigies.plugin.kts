package gg.rsmod.plugins.content.items.effigies

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.getInteractingItemSlot
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options

/**
 * Q-056: Ancient effigies. Real ids confirmed present and sequential in this project's own
 * 667 cache (Items.kt 18778-18782: Starved/Nourished/Sated/Gorged Ancient Effigy, then
 * Dragonkin Lamp) - the whole reward chain genuinely exists in this revision, not a later-era
 * item as first suspected. Mechanics/values (stage levels, xp, skill pairs, flavour text, the
 * Dragonkin Lamp's cubic xp formula) sourced from Void's `content/activity/ancient_effigies/
 * AncientEffigies.kt` (https://runescape.wiki/w/Ancient_effigies, cited in that file).
 *
 * Simplified from Void, disclosed rather than silently dropped:
 *  - The Dragonkin Lamp's skill choice is a plain chat `options()` menu over the 8 skills the
 *    effigy stages themselves use, not the full 25-skill genie-lamp-style interface (1139) that
 *    already exists in this codebase (`items/lamps/lamp_interface.plugin.kts`) - that file is
 *    hardcoded to `Items.LAMP` specifically and reusing it here would mean editing a separate,
 *    already-working shared system rather than adapting this one gap.
 *  - The historian-NPC "trade an unopened effigy for a generic antique lamp" side path from
 *    Void is not ported - a secondary, non-essential alternative to the core investigate chain.
 *  - Void's Dragonkin Lamp xp formula branches below level 30 to a raw xp-table difference this
 *    session found no lookup function for; the cubic formula is used unconditionally instead
 *    (it still yields a small positive value at low levels, just not byte-identical to Void's
 *    piecewise version - only matters for a player rubbing the lamp on a sub-30 skill).
 */
private data class EffigyStage(val level: Int, val xp: Double, val next: Int)

private val STAGES =
    mapOf(
        Items.STARVED_ANCIENT_EFFIGY to EffigyStage(91, 15000.0, Items.NOURISHED_ANCIENT_EFFIGY),
        Items.NOURISHED_ANCIENT_EFFIGY to EffigyStage(93, 20000.0, Items.SATED_ANCIENT_EFFIGY),
        Items.SATED_ANCIENT_EFFIGY to EffigyStage(95, 25000.0, Items.GORGED_ANCIENT_EFFIGY),
        Items.GORGED_ANCIENT_EFFIGY to EffigyStage(97, 30000.0, Items.DRAGONKIN_LAMP),
    )

private val SKILL_PAIRS =
    listOf(
        Skills.AGILITY to Skills.CRAFTING,
        Skills.CONSTRUCTION to Skills.THIEVING,
        Skills.COOKING to Skills.FIREMAKING,
        Skills.FISHING to Skills.FARMING,
        Skills.FLETCHING to Skills.WOODCUTTING,
        Skills.HERBLORE to Skills.HUNTER,
        Skills.MINING to Skills.SMITHING,
        Skills.SUMMONING to Skills.RUNECRAFTING,
    )

private val EFFIGY_PAIR_CHOICE = AttributeKey<MutableMap<Int, Int>>()

fun Player.pairFor(itemId: Int): Pair<Int, Int> {
    val chosen = attr[EFFIGY_PAIR_CHOICE] ?: HashMap<Int, Int>().also { attr[EFFIGY_PAIR_CHOICE] = it }
    val index = chosen.getOrPut(itemId) { world.random(SKILL_PAIRS.size - 1) }
    return SKILL_PAIRS[index]
}

STAGES.keys.forEach { effigyId ->
    on_item_option(item = effigyId, option = "Investigate") {
        val stage = STAGES.getValue(effigyId)
        val slot = player.getInteractingItemSlot()
        val (first, second) = player.pairFor(effigyId)
        player.queue {
            player.message("You inspect the ancient effigy; it draws on your knowledge.")
            val firstName = Skills.getSkillName(world, first)
            val secondName = Skills.getSkillName(world, second)
            val choice = options(firstName, secondName, title = "Which images do you wish to focus on?")
            val skill = if (choice == 1) first else if (choice == 2) second else return@queue
            val skillName = Skills.getSkillName(world, skill)
            if (player.skills.getCurrentLevel(skill) < stage.level) {
                player.message("You require at least level ${stage.level} $skillName to investigate the ancient effigy further.")
                return@queue
            }
            if (player.inventory[slot]?.id != effigyId) {
                return@queue
            }
            player.inventory[slot] = Item(stage.next)
            player.attr[EFFIGY_PAIR_CHOICE]?.remove(effigyId)
            player.addXp(skill, stage.xp)
            player.message("You have gained ${stage.xp.toInt()} $skillName experience!")
            if (stage.next == Items.DRAGONKIN_LAMP) {
                player.message("The ancient effigy transforms into a Dragonkin lamp!")
            } else {
                player.message("The ancient effigy glows briefly; it seems changed somehow.")
            }
        }
    }
}

on_item_option(item = Items.DRAGONKIN_LAMP, option = "Rub") {
    val slot = player.getInteractingItemSlot()
    val skills = SKILL_PAIRS.flatMap { listOf(it.first, it.second) }.distinct()
    player.queue {
        val names = skills.map { Skills.getSkillName(world, it) }.toTypedArray()
        val choice = options(*names, title = "Choose a skill to focus your memories on")
        if (choice < 1 || choice > skills.size) return@queue
        val skill = skills[choice - 1]
        val skillName = Skills.getSkillName(world, skill)
        val level = player.skills.getMaxLevel(skill)
        val xp = (level.toDouble() * level * level - 2.0 * level * level + 100.0 * level) / 20.0
        if (player.inventory[slot]?.id != Items.DRAGONKIN_LAMP) return@queue
        player.inventory[slot] = null
        player.addXp(skill, xp)
        player.message("You have gained ${xp.toInt()} $skillName experience!")
    }
}
