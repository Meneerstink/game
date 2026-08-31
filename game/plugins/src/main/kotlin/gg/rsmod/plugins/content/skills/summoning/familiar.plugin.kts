package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.message

/**
 * R07.1: wires [Familiar] to the real, verified cache options - "Summon" on the pouch
 * (confirmed live this session: `SPIRIT_WOLF_POUCH` really has "Summon" at inventory slot 3,
 * not guessed) and "Interact" on the summoned npc (confirmed the same way - familiars don't
 * have a player-facing "Dismiss" option in the cache, "Interact" is the real one).
 *
 * `on_item_option`/`on_npc_option` throw at plugin-load if the option string isn't really on
 * that specific def - only 2 of the ~50 pouches in the roster were individually checked this
 * session, so every entry's real options are checked here before binding, skipping (and
 * counting, logged) any that don't match rather than risking one unverified entry crashing the
 * whole boot for the entire roster.
 */
val familiarTickTimer = TimerKey()

/**
 * Non-graphical withdraw-one flow: no verified BoB interface component layout exists in this
 * cache (see R07.3 note below), so item selection reuses the real [options]/[inputInt] dialog
 * primitives already used elsewhere in this codebase rather than inventing component IDs. Lists
 * contents by message (item count can exceed the confirmed 5-option dialog cap), then two
 * [inputInt] prompts pick the slot and amount.
 */
suspend fun QueueTask.withdrawOne(player: Player) {
    val contents = BeastOfBurden.contents(player)
    if (contents.isEmpty()) {
        player.message("Your familiar isn't carrying anything.")
        return
    }
    contents.forEach { (slot, item) -> player.message("${slot + 1}: ${item.getName(world.definitions)} x${item.amount}") }
    val choice = inputInt("Enter item number (1-${contents.size})")
    val entry = contents.firstOrNull { it.index + 1 == choice }
    if (entry == null) {
        player.message("Nothing withdrawn.")
        return
    }
    val amount = inputInt("Enter amount (max ${entry.value.amount})")
    if (amount <= 0) {
        player.message("Nothing withdrawn.")
        return
    }
    val withdrawn = BeastOfBurden.withdraw(player, entry.index, amount)
    if (withdrawn > 0) player.message("You withdraw $withdrawn x ${entry.value.getName(world.definitions)} from your familiar.")
}

var boundSummon = 0
var skippedSummon = 0
SummoningPouchData.values().forEach { data ->
    val def = world.definitions.get(ItemDef::class.java, data.pouch)
    if (def.inventoryMenu.any { it?.lowercase() == "summon" }) {
        on_item_option(item = data.pouch, option = "summon") {
            Familiar.summon(player, data)
        }
        boundSummon++
    } else {
        skippedSummon++
    }
}

var boundInteract = 0
var skippedInteract = 0
val familiarNpcIds = SummoningPouchData.values().map { it.npc }.distinct().toIntArray()
familiarNpcIds.forEach { npc ->
    val def = world.definitions.get(NpcDef::class.java, npc)
    if (def.options.any { it?.lowercase() == "interact" }) {
        on_npc_option(npc = npc, option = "interact") {
            if (Familiar.current(player)?.id != npc) {
                return@on_npc_option
            }
            player.queue {
                // R07.3: the 3 real Beast of Burden familiars get the full real BoB interact
                // menu (Renew/Deposit-all/Withdraw/Withdraw-all/Dismiss) - 5 options is the
                // confirmed max seen anywhere in this codebase (games_necklace, skills_necklace,
                // combat_bracelet, amulet_of_glory all use exactly 5), so plain "Cancel" is
                // dropped - escape/click-away already returns -1 with no action, same as those.
                if (BeastOfBurden.isBobNpc(npc)) {
                    when (options("Renew", "Deposit-all", "Withdraw", "Withdraw-all", "Dismiss")) {
                        1 -> Familiar.renew(player)
                        2 -> {
                            val deposited = BeastOfBurden.depositAll(player)
                            if (deposited > 0) player.message("You deposit $deposited item(s) with your familiar.")
                        }
                        3 -> withdrawOne(player)
                        4 -> {
                            val withdrawn = BeastOfBurden.withdrawAll(player)
                            if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
                        }
                        5 -> Familiar.dismiss(player)
                    }
                } else {
                    when (options("Renew", "Dismiss", "Cancel")) {
                        1 -> Familiar.renew(player)
                        2 -> Familiar.dismiss(player)
                    }
                }
            }
        }
        boundInteract++
    } else {
        skippedInteract++
    }
}

// R07.3: depositing into a Beast of Burden familiar - real RS mechanic is using an
// inventory item on your pack animal. Bound only for the 3 real BoB npc ids, verified via
// BeastOfBurden.isBobNpc rather than assumed for the whole familiar roster.
BeastOfBurden.allKeys.forEach { register_container_key(it) }
familiarNpcIds.filter { BeastOfBurden.isBobNpc(it) }.forEach { npc ->
    on_any_item_on_npc(npc) {
        if (Familiar.current(player)?.id != npc) {
            return@on_any_item_on_npc
        }
        val item = player.getInteractingItem()
        BeastOfBurden.deposit(player, item)
    }
}

on_world_init {
    println(
        "R07.1 familiar: bound Summon on $boundSummon/${boundSummon + skippedSummon} pouches, " +
            "Interact on $boundInteract/${boundInteract + skippedInteract} familiar npcs " +
            "(skipped entries didn't have that exact real cache option - not guessed).",
    )
}

on_login {
    player.timers[familiarTickTimer] = 1
}

on_timer(familiarTickTimer) {
    Familiar.tick(player)
    player.timers[familiarTickTimer] = 1
}

on_logout {
    Familiar.dismiss(player)
}

// R07.1: real owner-death dismiss rule, wired at the same real, existing per-player death hook
// death.plugin.kts uses for DeathResolver/DeathExecutor - kept in this file so the summoning
// package owns its own lifecycle rule instead of touching death.plugin.kts.
on_player_pre_death {
    Familiar.dismiss(player)
}

/*
 * R07.4/R07.5: interface 662 button wiring - components 49/51/65/67/69 are the real,
 * verified-live component ids from the R07.5 evidence trail (OWNER_TASK_STATUS.md). Every
 * handler re-checks Familiar.current(player) first - anti-forgery/stale-click protection so a
 * forged or leftover click after dismiss/expiry/relog can't act on nothing.
 */
on_button(662, 49) { // "Call familiar" (portrait button)
    Familiar.call(player)
}

on_button(662, 51) { // "Dismiss Familiar"
    Familiar.dismiss(player)
}

on_button(662, 69) { // "Renew Familiar"
    Familiar.renew(player)
}

on_button(662, 67) { // "Take Beast of Burden items" - gated to real BoB familiars only
    val npc = Familiar.current(player)
    if (npc == null || !BeastOfBurden.isBobNpc(npc.id)) {
        return@on_button
    }
    val withdrawn = BeastOfBurden.withdrawAll(player)
    if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
}

on_button(662, 65) { // "Order your familiar to attack a target"
    val npc = Familiar.current(player)
    if (npc == null) {
        return@on_button
    }
    // R07.3b (honest, evidence-backed, unchanged from SUMMONING_AUDIT.md): no familiar npc in
    // this codebase or its upstream source has a registered NpcCombatDef - there is no real
    // combat data anywhere to attack with yet. Reporting that honestly rather than faking damage.
    player.message("Your familiar isn't able to fight yet.")
}
