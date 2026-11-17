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
    val FIRE_SPELLS = setOf(CombatSpell.FIRE_STRIKE, CombatSpell.FIRE_BOLT, CombatSpell.FIRE_BLAST, CombatSpell.FIRE_WAVE, CombatSpell.FIRE_SURGE)
    fun bonus(player: Player, spell: CombatSpell?): Int =
        if (spell in FIRE_SPELLS && ItemId.FIRE_AMULET.matches(player.getEquipment(EquipmentType.AMULET)?.id)) 2 else 0
    enum class Result { OK, NOT_READY, LEVEL, INGREDIENTS }
    /** Called after the altar queue's three ticks and location/interaction revalidation. Costs and result publish together. */
    fun craft(player: Player): Result {
        val ruby = ItemId.FIRE_RUBY.local ?: return Result.NOT_READY
        val amulet = ItemId.FIRE_AMULET.local ?: return Result.NOT_READY
        if (player.skills.getCurrentLevel(Skills.RUNECRAFTING) < 21) return Result.LEVEL
        val inventory = ItemContainer(player.inventory)
        for (item in listOf(Item(ruby), Item(Items.AMULET_OF_MAGIC), Item(Items.FIRE_RUNE, 100))) {
            if (!inventory.remove(item.id, item.amount).hasSucceeded()) return Result.INGREDIENTS
        }
        if (!DeadmanInventory.add(inventory, Item(amulet))) return Result.INGREDIENTS
        DeadmanInventory.publish(inventory, player.inventory)
        player.addXp(Skills.RUNECRAFTING, 125.0)
        player.persistNow()
        return Result.OK
    }
}
