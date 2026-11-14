package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * N415 Mage Arena II wiring: the enchanted symbol's Activate, its 45-minute rotation step, the three followers (combat
 * definitions, fight script, death) and Derwen's energy balls. Bindings resolve only through data/cfg/mage-arena/ids.tsv
 * (MageArenaBindingsTool): without the finalized import nothing is registered.
 */

MageArenaII.assets.symbol?.let { symbol ->
    on_item_option(item = symbol, option = "Activate") {
        if (!MageArenaIITracking.usableAt(player.tile)) {
            player.message(MageArenaIITracking.NOT_HERE)
            return@on_item_option
        }
        val gods = MageArenaII.trackable(player)
        if (gods.isEmpty()) return@on_item_option
        player.queue {
            if (MageArenaIITracking.locations(player) == null) {
                MageArenaIITracking.rotate(player)
            } else if (MageArenaIITracking.rotated(player)) {
                MageArenaIITracking.rotate(player)
                player.message(MageArenaIITracking.MOVED)
                messageBox(MageArenaIITracking.MOVED)
            }
            // Wiki: with a single god left the symbol activates without the prompt.
            val god = if (gods.size == 1) gods.single() else {
                val pick = options(*(gods.map { it.title } + "Cancel").toTypedArray(), title = "Select an option")
                gods.getOrNull(pick - 1) ?: return@queue
            }
            if (!player.inventory.contains(symbol) || player.isDead() || !MageArenaIITracking.usableAt(player.tile)) return@queue
            MageArenaIITracking.locate(player, god)
        }
    }
}

on_timer(MageArenaIITracking.STEP_TIMER) {
    MageArenaIITracking.step(player)
}

on_logout {
    MageArenaIIEncounter.dismissAll(player)
}

MageArenaIIEncounter.FOLLOWERS.forEach { follower ->
    val id = MageArenaIIEncounter.localNpc(follower.combat) ?: return@forEach
    val guardian = MageArenaIIGuardians.ALL.first { it.god == follower.god }
    MageArenaIIGuardians.definition(guardian)?.let { set_combat_def(id, it) }
    on_npc_combat(*intArrayOf(id)) { npc.queue { MageArenaIICombatScript.handleSpecialCombat(this) } }
    on_npc_death(id) {
        MageArenaIIEncounter.died(npc)
        // 14-11 23:5x: the table below was registered but never rolled (every boss rolls in its own death hook, like Artio) ->
        // Porazdir died without his heart. Only the summoner may attack (owner decision 23:5x), so the top damager is the owner.
        val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
        DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
    }
    // Wiki Porazdir / Derwen / Justiciar Zachariah drops: the remains, always (the 1/3 Looting bag is out of the game, 11-04).
    MageArenaII.assets.remains(follower.god)?.let { remains ->
        val table = DropTableFactory
        table.register(table.build { guaranteed { obj(remains) } }, id)
    }
}

// Derwen's energy ball: 20 Hitpoints, any style can destroy it (Wiki Derwen).
MageArenaIIEncounter.localNpc(MageArenaIIEncounter.HEALER)?.let { healer ->
    set_combat_def(healer) {
        configs { attackSpeed = 6; respawnDelay = 0 }
        stats { hitpoints = 200 }
        anims { death = -1 } // the OSRS energy ball has no sequences (static npc); an explicit none satisfies the builder
    }
}
