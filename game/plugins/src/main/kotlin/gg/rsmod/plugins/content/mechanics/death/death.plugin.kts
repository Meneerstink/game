package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.KILLER_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.trouver.Trouver

/**
 * Wires the shared death-resolution model ([DeathResolver] /
 * [DeathItemRiskCalculator]) and execution layer ([DeathExecutor]) into the
 * existing [gg.rsmod.game.action.PlayerDeathAction] pre-death hook. A PvM death's lost
 * items go to the victim's gravestone ([Gravestone]); Death's Office, the coffer and the
 * gravestone screens live in `deaths_office.plugin.kts`.
 *
 * `alwaysProtected` is wired to [Trouver.protectedFromDeath] - the only production caller of that
 * hook (`RSPS_DECISIONS.md` 2026-09-02 "STANDING OWNER AUTHORIZATION"). [Trouver.grantKillerCompensation]
 * runs after execution so it sees the same resolved [DeathResolutionResult] the item removal used.
 */
on_player_pre_death {
    val victim = player
    try {
        resolveDeath(victim)
    } finally {
        // Audit X-01: the familiar is despawned here, AFTER a PvP death handed its cargo to the killer
        // (a separate pre-death hook in familiar.plugin.kts had no defined order relative to this one).
        gg.rsmod.plugins.content.skills.summoning.Familiar.ownerDeath(victim)
    }
}

fun resolveDeath(victim: Player) {
    // A Deadman 7-second countdown (logout, teleport, a Death's Domain entrance) never completes during the death animation:
    // combat hits already cancel it, and poison, venom or scenery damage cancel it here.
    if (gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.isActive(victim)) {
        gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.cancel(victim, "Your action was cancelled.")
    }
    // Audit X-02: items "checked" on the price checker leave the inventory; put them back first so they
    // take part in the death like every other carried item.
    gg.rsmod.plugins.content.inter.pricecheck.PriceChecker.close(victim)
    if (SafeDeath.isSafe(victim)) {
        // Safe minigame death: no item loss, no recovery, no killer compensation.
        return
    }
    val world = victim.world
    // DamageMap normally contains the player (including familiar damage credited to its owner).
    // If an NPC/environmental hit lands after a live PvP hit, use the shared aggressor window so
    // the death remains player-caused instead of silently falling back to PvM recovery.
    // Audit X-04: a killer who is no longer online is never credited (DeathResolver.resolveKiller).
    val killer = DeathResolver.resolveKiller(victim)
    // Audit D-06: kill grace is granted once, by DeathExecutor, for a resolved PvP death only.

    val logger = world.getService(LoggerService::class.java, searchSubclasses = true)
    // Owner 2026-09-18: rank by Grand Exchange guide price, exactly like RuneScape (GuidePriceValueProvider).
    val valueProvider = GuidePriceValueProvider(world)

    // Audit D-09: a skulled player killed by a city guard is not rescued by Death's Domain - the items are
    // at risk exactly like a PvP death without a player killer (public ground loot).
    val killedBySkulledGuard =
        killer == null && gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(victim) &&
            (victim.attr[KILLER_ATTR]?.get() as? gg.rsmod.game.model.entity.Npc)
                ?.let { gg.rsmod.plugins.content.mechanics.pvp.CityGuards.isGuard(it) } == true
    val contextOverride = if (killedBySkulledGuard) DeathContext.WILDERNESS_PVP else null

    // Deadman emblems (owner 2026-09-25): kept on a PvM death, always lost on a PvP death - they are untradeable, so the
    // untradeable protection below must never reach them on a PvP death.
    val pvpDeath = (contextOverride ?: DeathResolver.resolveContext(victim, killer)) == DeathContext.WILDERNESS_PVP
    val resolved =
        DeathResolver.resolve(
            victim = victim,
            killer = killer,
            valueProvider = valueProvider,
            alwaysProtected = DeathRules.alwaysProtected(world.definitions, pvpDeath),
            contextOverride = contextOverride,
        )
    val (withoutGeneric, brokenInPlace) = UntradeableDeathProtection.splitGeneric(world.definitions, resolved)
    val (afterBreakables, breaking) = PvpDeathBreakables.split(withoutGeneric)
    val (result, droppedLostAmmo) = QuiverDeathRules.stripLost(afterBreakables)
    UntradeableDeathProtection.breakInPlace(victim, brokenInPlace)
    // OSRS: "In instanced areas, the Gravestone will aim to appear outside the instance where its owner can loot it."
    val graveTile = GraveLocations.graveTile(victim)
    val executed =
        DeathExecutor.execute(
            world = world,
            result = result,
            logger = logger,
            graveTile = graveTile,
            // Owner 2026-09-18 (#6): converted killer loot (broken-item coins, uncharged staves,
            // ornament kits, quiver ammo) joins the lost stacks in the SAME loot-key plan instead of
            // being spawned on the floor beside the key.
            extraPvpLoot = {
                val converted = mutableListOf<Item>()
                PvpDeathBreakables.execute(world, result, breaking) { converted += it }
                val droppedProtectedAmmo = QuiverDeathRules.stripProtected(victim, result)
                converted += droppedLostAmmo + droppedProtectedAmmo
                // Audit X-01: a beast of burden's cargo is unprotected PvP loot, not a free Death's Domain batch.
                converted += gg.rsmod.plugins.content.skills.summoning.BeastOfBurden.takeAllCargo(victim)
                converted
            },
        )
    if (executed) {
        // Compensation is part of the same exactly-once death transfer. If a duplicate
        // pre-death hook reaches this script after DeathExecutor's guard fired, paying here
        // again would duplicate the killer's reward even though no second loot transfer ran.
        Trouver.grantKillerCompensation(result, valueProvider)
        // Audit X-05: persist the transfer at once, like trades do - a crash before the next autosave
        // would otherwise give the victim everything back while the killer keeps the loot.
        victim.persistNow()
        killer?.persistNow()
    }
}

/*
 * Owner 2026-09-26: the old "reclaim" command is gone - like OSRS, items come back only from the gravestone or from Death
 * in Death's Office. Typing it only says where to go.
 */
on_command("reclaim") {
    player.message("Your items are in your gravestone, or with Death in Death's Office if the gravestone has collapsed.")
}
