package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.items.potion.StaminaPotions
import gg.rsmod.plugins.content.mechanics.run.RunEnergy
import gg.rsmod.plugins.content.skills.SkillcapePerks.DailyUse

/**
 * OSRS Wiki Agility cape, Fletching cape and Herblore cape (checked 2026-11-25).
 * Daily limits belong to the account, shared by plain/trimmed capes and the plain max cape.
 * Cache menus are supplied separately by Claude; no guessed numeric option bindings.
 */
object SkillcapeFeatures {
    const val STAMINA_TICKS = 100 // one minute, 600 ms ticks

    fun stamina(player: Player): Boolean {
        if (!SkillcapePerks.wornOrCarried(player, Skillcapes.AGILITY)) return false
        if (!SkillcapePerks.takeDailyUse(player, DailyUse.STAMINA)) {
            player.message("You have already used the cape's stamina boost today.")
            return false
        }
        // This is a cape perk, not drinking a potion: do not consume Ring of endurance charges.
        RunEnergy.renew(player, 100.0)
        player.timers[StaminaPotions.STAMINA_TIMER] = STAMINA_TICKS
        return true
    }

    fun searchFletching(player: Player): Boolean {
        if (!SkillcapePerks.wornOrCarried(player, Skillcapes.FLETCHING)) return false
        if (SkillcapePerks.remainingToday(player, DailyUse.FLETCHING_SEARCH) == 0) {
            player.message("You have searched the cape ${DailyUse.FLETCHING_SEARCH.limit} times today already.")
            return false
        }
        // Each search gives BOTH items. Failed delivery must not use a daily charge or deliver half a pair.
        if (player.inventory.freeSlotCount < 2) {
            player.message("You don't have enough inventory space.")
            return false
        }
        if (!SkillcapePerks.takeDailyUse(player, DailyUse.FLETCHING_SEARCH)) return false
        val first = player.inventory.nextFreeSlot
        player.inventory[first] = gg.rsmod.game.model.item.Item(Items.MITH_GRAPPLE_9419)
        player.inventory[player.inventory.nextFreeSlot] = gg.rsmod.game.model.item.Item(Items.BRONZE_CROSSBOW)
        return true
    }

    fun searchHerblore(player: Player): Boolean {
        if (!SkillcapePerks.wornOrCarried(player, Skillcapes.HERBLORE)) return false
        if (player.inventory.add(Items.PESTLE_AND_MORTAR).hasFailed()) {
            player.message("You don't have enough inventory space.")
            return false
        }
        return true
    }
}
