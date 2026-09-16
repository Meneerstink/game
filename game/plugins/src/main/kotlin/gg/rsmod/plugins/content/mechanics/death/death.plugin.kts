package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.DEATH_RECOVERY_FEE_ATTR
import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.attr.PVP_AGGRESSOR_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.PVP_AGGRESSOR_WINDOW_TIMER
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.trouver.Trouver
import gg.rsmod.plugins.content.items.helios.CrownOfHelios

/**
 * Wires the shared death-resolution model ([DeathResolver] /
 * [DeathItemRiskCalculator]) and execution layer ([DeathExecutor]) into the
 * existing [gg.rsmod.game.action.PlayerDeathAction] pre-death hook, and
 * exposes a `::reclaim` command so a PvM/safe death's recovered items can be
 * reclaimed. Uses [DeathRecoveryConfig.PLACEHOLDER] and
 * [ItemDefCostValueProvider] - see their docs for why these are not final
 * production values/pricing.
 *
 * `alwaysProtected` is wired to [Trouver.protectedFromDeath] - the only production caller of that
 * hook (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION"). [Trouver.grantKillerCompensation]
 * runs after execution so it sees the same resolved [DeathResolutionResult] the item removal used.
 */
on_player_pre_death {
    val victim = player
    if (SafeDeath.isSafe(victim)) {
        // Safe minigame death: no item loss, no recovery, no killer compensation.
        return@on_player_pre_death
    }
    val world = victim.world
    // DamageMap normally contains the player (including familiar damage credited to its owner).
    // If an NPC/environmental hit lands after a live PvP hit, use the shared aggressor window so
    // the death remains player-caused instead of silently falling back to PvM recovery.
    val directKiller = victim.attr[KILLER_ATTR]?.get() as? Player
    val killer =
        directKiller ?:
            if (victim.timers.has(PVP_AGGRESSOR_WINDOW_TIMER)) {
                victim.attr[PVP_AGGRESSOR_ATTR]?.get()
            } else {
                null
            }
    // Deadman PvP guards plan (2026-09-16): "killing someone in a single-combat zone grants the
    // killer a 1-minute period where they cannot be attacked". Gated on the same real per-tile
    // multicombat flag every other single/multi rule in this codebase already uses.
    if (killer is Player && killer !== victim && !victim.tile.isMulti(world)) {
        gg.rsmod.plugins.content.mechanics.pvp.KillGrace.grant(killer)
    }

    val logger = world.getService(LoggerService::class.java, searchSubclasses = true)
    val valueProvider = ItemDefCostValueProvider(world.definitions)

    val resolved =
        DeathResolver.resolve(
            victim = victim,
            killer = killer,
            valueProvider = valueProvider,
            alwaysProtected = { itemId ->
                itemId == CrownOfHelios.ITEM || Trouver.protectedFromDeath(itemId)
            },
        )
    val (result, breaking) = PvpDeathBreakables.split(resolved)
    val executed =
        DeathExecutor.execute(
            world = world,
            result = result,
            recoveryConfig = DeathRecoveryConfig.PLACEHOLDER,
            logger = logger,
        )
    if (executed) {
        PvpDeathBreakables.execute(world, result, breaking)
        // Compensation is part of the same exactly-once death transfer. If a duplicate
        // pre-death hook reaches this script after DeathExecutor's guard fired, paying here
        // again would duplicate the killer's reward even though no second loot transfer ran.
        Trouver.grantKillerCompensation(result, valueProvider)
    }
}

on_command("reclaim") {
    val logger = player.world.getService(LoggerService::class.java, searchSubclasses = true)
    when (val outcome = DeathRecoveryService.reclaim(player, logger = logger)) {
        is DeathReclaimOutcome.NothingToReclaim -> player.message("You have no items to reclaim.")
        is DeathReclaimOutcome.Expired -> player.message("Your death recovery expired and its items were forfeited.")
        is DeathReclaimOutcome.InsufficientFunds -> {
            val fee = player.attr[DEATH_RECOVERY_FEE_ATTR] ?: DeathRecoveryConfig.PLACEHOLDER.reclaimFee
            player.message("You need $fee coins to reclaim your items.")
        }
        is DeathReclaimOutcome.Reclaimed -> player.message(
            "You have reclaimed ${outcome.itemCount} item(s) for ${outcome.feePaid} coins.",
        )
    }
}
