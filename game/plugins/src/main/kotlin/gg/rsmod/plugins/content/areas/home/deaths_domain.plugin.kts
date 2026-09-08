package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.plugins.content.mechanics.death.DeathReclaimOutcome
import gg.rsmod.plugins.content.mechanics.death.DeathRecoveryConfig
import gg.rsmod.plugins.content.mechanics.death.DeathRecoveryService
import gg.rsmod.plugins.content.mechanics.death.ItemsKeptOnDeath

// The imported entrance is usable with the existing revision-667 recovery service.
// The OSRS office interior is not present in this cache; do not teleport into a void.
// The "Visit the prayer altar" shortcut previously here was removed (2026-09-06 owner human
// retest: "appears to teleport elsewhere is not acceptable") - the real, physical Ferox altar
// (FeroxObjects.ALTAR, objs/prayeraltar/prayer_altar.plugin.kts) already has its own working
// Pray-at interaction with full Normal<->Ancient book switching; players walk to it like any
// other object instead of being moved there from an unrelated menu.
on_obj_option(FeroxObjects.DEATHS_DOMAIN, "enter") {
    player.queue {
        val fee = player.attr[DEATH_RECOVERY_FEE_ATTR] ?: DeathRecoveryConfig.PLACEHOLDER.reclaimFee
        when (options("Reclaim my items ($fee coins).", "Items kept on death.", "Leave.")) {
            1 -> {
                val logger = player.world.getService(LoggerService::class.java, searchSubclasses = true)
                when (val result = DeathRecoveryService.reclaim(player, logger = logger)) {
                    DeathReclaimOutcome.NothingToReclaim -> player.message("You have no items to reclaim.")
                    DeathReclaimOutcome.InsufficientFunds -> player.message("You need $fee coins to reclaim your items.")
                    is DeathReclaimOutcome.Reclaimed -> player.message("You reclaimed ${result.itemCount} item(s) for ${result.feePaid} coins. Any remaining items are held until you have space.")
                }
            }
            2 -> ItemsKeptOnDeath.open(player)
        }
    }
}
