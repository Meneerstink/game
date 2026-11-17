package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId

/** Wiki rev15396852 (2026-11-08): base +2 before gear/weakness, three-tick Fire Altar recipe. */
object FireAmulet {
    const val CRAFT_TICKS = 3
    // Wiki File:Creating amulet of fire.webp rev15329402 shows the level-3 amulet enchant, not runecrafting.
    const val CRAFT_ANIMATION_SOURCE = 721
    const val CRAFT_GRAPHIC_SOURCE = 116
    // A requirement summary, not a claim about the unrecorded OSRS server refusal wording.
    const val REQUIREMENTS = "Runecraft: 21. Fire ruby: 1. Amulet of magic: 1. Fire rune: 100."
    val FIRE_SPELLS = setOf(CombatSpell.FIRE_STRIKE, CombatSpell.FIRE_BOLT, CombatSpell.FIRE_BLAST, CombatSpell.FIRE_WAVE, CombatSpell.FIRE_SURGE)
    fun bonus(player: Player, spell: CombatSpell?): Int =
        if (spell in FIRE_SPELLS && ItemId.FIRE_AMULET.matches(player.getEquipment(EquipmentType.AMULET)?.id)) 2 else 0
    enum class Result { OK, NOT_READY, LEVEL, INGREDIENTS }
    /** Validate before presenting the action; repeat at completion because inventory/boosts can change while queued. */
    fun check(player: Player): Result = prepare(player).first
    private fun prepare(player: Player): Pair<Result, ItemContainer?> {
        val ruby = ItemId.FIRE_RUBY.local ?: return Result.NOT_READY to null
        val amulet = ItemId.FIRE_AMULET.local ?: return Result.NOT_READY to null
        if (player.skills.getCurrentLevel(Skills.RUNECRAFTING) < 21) return Result.LEVEL to null
        val inventory = ItemContainer(player.inventory)
        for (item in listOf(Item(ruby), Item(Items.AMULET_OF_MAGIC), Item(Items.FIRE_RUNE, 100))) {
            if (!inventory.remove(item.id, item.amount).hasSucceeded()) return Result.INGREDIENTS to null
        }
        if (!DeadmanInventory.add(inventory, Item(amulet))) return Result.INGREDIENTS to null
        return Result.OK to inventory
    }
    /** Called after the altar queue's three ticks and location/interaction revalidation. Costs and result publish together. */
    fun craft(player: Player): Result {
        val (result, inventory) = prepare(player)
        if (result != Result.OK) return result
        DeadmanInventory.publish(requireNotNull(inventory), player.inventory)
        player.addXp(Skills.RUNECRAFTING, 125.0)
        player.persistNow()
        return Result.OK
    }
}
