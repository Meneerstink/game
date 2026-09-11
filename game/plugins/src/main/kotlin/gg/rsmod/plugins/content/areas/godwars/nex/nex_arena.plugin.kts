package gg.rsmod.plugins.content.areas.godwars.nex

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey

/**
 * Nex arena: entrance from the Ancient Prison landslide, per-tick encounter bookkeeping,
 * arena membership tracking and death hooks for Nex and her four minions.
 */
val NEX_CYCLE_TIMER = TimerKey()
val IN_NEX_ARENA = AttributeKey<Boolean>()

on_world_init {
    NexEncounter.world = world
    world.timers[NEX_CYCLE_TIMER] = 1
}

on_timer(NEX_CYCLE_TIMER) {
    NexEncounter.cycle()
    world.timers[NEX_CYCLE_TIMER] = 1
}

on_obj_option(obj = Objs.LANDSLIDE, option = "Climb-over") { enterNexArena(player) }

fun enterNexArena(player: Player) {
    player.queue {
        messageBox(
            "The room beyond this point is a prison!",
            "There is no way out other than death or teleport.",
            "Only those who endure dangerous encounters should proceed.",
        )
        val fighting = NexEncounter.players().size
        val option = options("Climb down.", "Stay here.", title = "Join $fighting ${if (fighting == 1) "person" else "people"} fighting?")
        if (option == 1) {
            player.moveTo(NexEncounter.ENTRY_TILE)
            trackArena(player)
        }
    }
}

fun trackArena(player: Player) {
    val inside = NexEncounter.inArena(player.tile)
    val was = player.attr[IN_NEX_ARENA] ?: false
    if (inside && !was) {
        player.attr[IN_NEX_ARENA] = true
        NexEncounter.playerEntered(player)
    } else if (!inside && was) {
        player.attr[IN_NEX_ARENA] = false
        NexEncounter.playerLeft(player)
    }
}

val ARENA_CHECK_TIMER = TimerKey()

on_login {
    player.timers[ARENA_CHECK_TIMER] = 1
}

on_timer(ARENA_CHECK_TIMER) {
    trackArena(player)
    player.timers[ARENA_CHECK_TIMER] = 1
}

on_logout {
    if (player.attr[IN_NEX_ARENA] == true) {
        player.attr[IN_NEX_ARENA] = false
        NexEncounter.playerLeft(player)
    }
}

on_player_death {
    if (player.attr[IN_NEX_ARENA] == true) {
        player.attr[IN_NEX_ARENA] = false
        NexEncounter.playerLeft(player)
    }
}

on_npc_combat(*NexCombatScript.ids) {
    npc.queue { NexCombatScript.handleSpecialCombat(this) }
}

on_npc_combat(*NexMinionCombatScript.ids) {
    npc.queue { NexMinionCombatScript.handleSpecialCombat(this) }
}

NexEncounter.MINION_IDS.forEachIndexed { index, id ->
    on_npc_death(id) {
        NexEncounter.onMinionDeath(index)
    }
}

on_npc_pre_death(Npcs.NEX) {
    NexCombatScript.reset()
    NexEncounter.onNexDeath(npc)
}
