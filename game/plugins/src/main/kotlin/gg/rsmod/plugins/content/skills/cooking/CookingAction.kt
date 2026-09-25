package gg.rsmod.plugins.content.skills.cooking

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.util.Misc

object CookingAction {
    suspend fun cook(
        task: QueueTask,
        data: CookingData,
        amount: Int,
        cookSecondary: Boolean = false,
    ) {
        val player = task.player
        val inventory = player.inventory
        val itemToCook = if (cookSecondary && data.secondaryCooked != null) data.secondaryCooked else data.cooked
        val usingFire =
            player.world.definitions
                .get(
                    ObjectDef::class.java,
                    player.getInteractingGameObj().id,
                ).name
                .contains("Fire")
        if (data.rangeOnly && usingFire) {
            player.message("You can't cook this on a fire; you need a range or oven.")
            return
        }
        val maxCount = minOf(amount, inventory.getItemCount(data.raw))
        task.wait(if (amount > 1) 2 else 1)
        repeat(maxCount) {
            if (!canCook(task, data)) {
                player.animate(Anims.RESET)
                return
            }

            player.animate(if (usingFire) Anims.COOK_FIRE else Anims.COOK_RANGE)
            player.playSound(Sfx.FRY)
            task.wait(1)
            val removeResult = inventory.remove(data.raw, assureFullRemoval = true)
            if (removeResult.hasFailed()) return

            // OSRS Wiki "Cooking cape": "When worn, food will never be burned while cooking" (the max cape carries it too).
            val success =
                gg.rsmod.plugins.content.skills.SkillcapePerks.worn(player, gg.rsmod.plugins.content.skills.Skillcapes.COOKING) ||
                    interpolate(data.lowChance, data.highChance, player.skills.getCurrentLevel(Skills.COOKING)) > RANDOM.nextInt(255)

            if (success) {
                inventory.add(itemToCook)
                player.addXp(Skills.COOKING, data.experience, checkBrawlingGloves = true)
            } else {
                inventory.add(data.burnt)
            }
            if (data.leftover != null) {
                inventory.add(data.leftover)
            }
            task.wait(2)
        }
    }

    private fun canCook(
        task: QueueTask,
        data: CookingData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory

        if (!player.world.isSpawned(player.getInteractingGameObj())) {
            return false
        }

        if (!inventory.contains(data.raw)) {
            return false
        }

        if (player.skills.getCurrentLevel(Skills.COOKING) < data.levelRequirement) {
            player.queue {
                val name =
                    player.world.definitions
                        .get(ItemDef::class.java, data.cooked)
                        .name
                        .lowercase()
                messageBox(
                    "You need a Cooking level of ${data.levelRequirement} to cook ${Misc.formatForVowel(name)} $name.",
                )
            }
            return false
        }

        return true
    }
}
