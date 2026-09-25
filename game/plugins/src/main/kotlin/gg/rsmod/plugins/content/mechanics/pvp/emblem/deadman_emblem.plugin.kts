package gg.rsmod.plugins.content.mechanics.pvp.emblem

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.mechanics.death.SafeDeath
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.emblem.EmblemDialogue.cashOut
import gg.rsmod.plugins.content.mechanics.store.StoreNpcs

/**
 * Deadman emblem routes (owner 2026-09-25); every rule lives in [DeadmanEmblem]. The death transfer is part of the death
 * pipeline (`DeathExecutor`), the HUD field part of `DeadmanHud`.
 */

// PvM: tier 1 drops from NPCs with 150+ HP. Breach monsters have their own reward (archaic emblem), guards and safe
// minigames give nothing.
on_npc_killed { killer, npc ->
    if (DeadmanBreach.isBreachNpc(npc) || CityGuards.isGuard(npc) || SafeDeath.isSafe(killer)) return@on_npc_killed
    DeadmanEmblem.onNpcKilled(world, killer, npc)
}

DeadmanEmblem.EMBLEM_IDS.forEach { id ->
    val tier = DeadmanEmblem.tierOf(id)

    on_item_option(item = id, option = "inspect") {
        EmblemDialogue.inspect(player, tier)
    }

    // Destroy: confirmed, logged; never a drop (the item has no Drop option, and a forged drop packet is refused below).
    on_item_option(item = id, option = "destroy") {
        player.queue(TaskPriority.WEAK) {
            if (!confirmItemAction(id, "Destroy your tier $tier Deadman emblem?", "It is gone for good - cash it in at the Emblem Trader instead.")) return@queue
            if (player.inventory.remove(id).hasSucceeded()) {
                DeadmanEmblem.destroyed(player, tier)
                player.playSound(Sfx.DESTROY_OBJECT)
            }
        }
    }

    can_drop_item(id) {
        player.message("You can't drop a Deadman emblem.")
        false
    }

    // An emblem on the floor (only ever its owner's) can't be picked up while another is owned.
    set_ground_item_condition(id) {
        if (DeadmanEmblem.ownedTier(player) > 0) {
            player.message("You can only own one Deadman emblem.")
            false
        } else {
            true
        }
    }

    on_item_on_npc(item = id, npc = StoreNpcs.DEADMAN_STORE) {
        player.queue { cashOut(StoreNpcs.DEADMAN_STORE) }
    }
}

on_login {
    // Integrity: never more than one emblem (cash the lower ones), then deliver emblems won while offline or dying.
    val held = DeadmanEmblem.holdings(player).sortedByDescending { it.tier }
    if (held.size > 1) {
        val best = held.first()
        held.drop(1).forEach { extra ->
            extra.container[extra.slot] = null
            DeadmanEmblem.receive(player, extra.tier, DeadmanEmblem.Source.PENDING, "login integrity (kept tier ${best.tier})")
        }
    }
    DeadmanEmblem.deliverPending(player)
}

on_command("emblem", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val tier = args.getOrNull(0)?.toIntOrNull()
    if (tier == null || tier !in 1..DeadmanEmblem.MAX_TIER) {
        player.message("Usage: emblem <1-6> [player name]  |  emblem info [player name]")
        if (args.getOrNull(0) != "info") return@on_command
    }
    val name = args.drop(1).joinToString(" ").replace('_', ' ').trim()
    val target = if (name.isEmpty()) player else world.getPlayerForName(name)
    if (target == null) {
        player.message("No player online called '$name'.")
        return@on_command
    }
    if (tier == null) {
        val held = DeadmanEmblem.holdings(target).joinToString { "T${it.tier}${if (it.inBank) " (bank)" else ""}" }.ifEmpty { "none" }
        player.message("${target.username}: emblems $held, pending ${DeadmanEmblem.Ledger.pendingFor(target.username)}")
        return@on_command
    }
    val after = DeadmanEmblem.adminCreate(player, target, tier)
    player.message("${target.username} now owns a tier $after Deadman emblem (logged as ADMIN_CREATE).")
}
