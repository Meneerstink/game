package gg.rsmod.plugins.content.magic

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message

/**
 * Bones to Bananas / Bones to Peaches, shared by the spells and the lectern tablets. RuneScape Wiki "Bones to Bananas": "Converts all
 * bones, up to big bones" - normal, burnt, bat, wolf, big, monkey and jogre bones. The old spell list also took babydragon, dragon,
 * zogre, ourg and dagannoth bones, so one cast destroyed a player's dragon bones.
 */
object BonesToFruit {
    val BONES: Set<Int> =
        setOf(
            Items.BONES, Items.BURNT_BONES, Items.BAT_BONES, Items.WOLF_BONES, Items.BIG_BONES, Items.MONKEY_BONES, Items.JOGRE_BONES,
        )

    fun holdsBones(player: Player): Boolean = player.inventory.rawItems.any { it != null && it.id in BONES }

    /** Turns every listed bone in the inventory into [produce], with the spell's animation 722 / graphic 141. */
    fun convert(player: Player, produce: Int) {
        player.animate(722)
        player.graphic(141, 96)
        BONES.forEach { bone ->
            val count = player.inventory.getItemCount(bone)
            if (count > 0 && player.inventory.remove(bone, count).hasSucceeded()) {
                player.inventory.add(produce, count)
            }
        }
    }

    const val NO_BONES = "You aren't holding any bones!"

    fun refuseWithoutBones(player: Player): Boolean {
        if (holdsBones(player)) return false
        player.message(NO_BONES)
        return true
    }
}
