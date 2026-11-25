package gg.rsmod.plugins.content.skills.herblore.mixing

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.doubleItemMessageBox
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.skills.herblore.HerbData
import gg.rsmod.plugins.content.skills.SkillcapePerks
import gg.rsmod.plugins.content.skills.Skillcapes

class CreateUnfinishedPotionAction {
    suspend fun mix(
        task: QueueTask,
        potion: HerbData,
        amount: Int,
        useGrimy: Boolean = false,
    ) {
        val player = task.player
        if (!canMix(task, potion, useGrimy)) {
            return
        }
        player.animate(Anims.MIX_POTION)
        player.playSound(Sfx.GRIND)
        task.wait(1)
        repeat(amount) {
            if (!canMix(task, potion, useGrimy)) {
                return
            }
            if (mixOne(player, potion, useGrimy)) {
                player.filterableMessage("You put the ${potion.name.lowercase()} into the vial of water.")
            }
            player.animate(Anims.MIX_POTION)
            player.playSound(Sfx.GRIND)
            task.wait(1)
        }
    }

    /** OSRS Wiki Herblore cape: worn cape permits grimy herbs, granting their cleaning XP. */
    fun mixOne(player: Player, potion: HerbData, useGrimy: Boolean = false): Boolean {
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < potion.levelRequirement ||
            (useGrimy && !SkillcapePerks.worn(player, Skillcapes.HERBLORE))
        ) return false
        val inventory = player.inventory
        val herbSlot = inventory.getItemIndex(if (useGrimy) potion.grimy else potion.clean, skipAttrItems = true)
        val vialSlot = inventory.getItemIndex(Items.VIAL_OF_WATER, skipAttrItems = true)
        if (herbSlot == -1 || vialSlot == -1) return false
        val herb = inventory[herbSlot] ?: return false
        val vial = inventory[vialSlot] ?: return false
        // Native herbs/vials are non-stackable. Reject malformed stacks rather than deleting their surplus.
        if (herb.amount != 1 || vial.amount != 1) return false
        inventory[herbSlot] = null
        inventory[vialSlot] = Item(potion.unf)
        if (useGrimy) player.addXp(Skills.HERBLORE, potion.experience)
        return true
    }

    private suspend fun canMix(
        task: QueueTask,
        potion: HerbData,
        useGrimy: Boolean,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < potion.levelRequirement) {
            val message = "You need a Herblore level of ${potion.levelRequirement} to make this potion."
            task.doubleItemMessageBox(message, item1 = Items.VIAL_OF_WATER, item2 = potion.clean)
            player.filterableMessage(message)
            return false
        }
        return inventory.contains(if (useGrimy) potion.grimy else potion.clean) && inventory.contains(Items.VIAL_OF_WATER) &&
            (!useGrimy || SkillcapePerks.worn(player, Skillcapes.HERBLORE))
    }
}
