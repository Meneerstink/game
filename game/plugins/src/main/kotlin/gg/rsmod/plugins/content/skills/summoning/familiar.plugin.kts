package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef

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
                // R07.3: the 3 real Beast of Burden familiars get an extra "Withdraw-all",
                // matching real RS's own BoB interact menu on top of the plain Renew/Dismiss.
                if (BeastOfBurden.isBobNpc(npc)) {
                    when (options("Renew", "Withdraw-all", "Dismiss", "Cancel")) {
                        1 -> Familiar.renew(player)
                        2 -> {
                            val withdrawn = BeastOfBurden.withdrawAll(player)
                            if (withdrawn > 0) player.message("You withdraw $withdrawn item(s) from your familiar.")
                        }
                        3 -> Familiar.dismiss(player)
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
