package gg.rsmod.plugins.content.areas.tzhaar.fightcaves

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.content.mechanics.death.SafeDeath
import gg.rsmod.plugins.content.skills.summoning.Familiar

/**
 * TzHaar Fight Cave bindings: entrance/exit objects, per-tick session processing, wave npc deaths,
 * logout/login handling and the wave overlay.
 */
val FIGHT_CAVE_TIMER = TimerKey()

val WAVE_NPCS = intArrayOf(
    Npcs.TZKIH_2734, Npcs.TZKIH_2735, Npcs.TZKEK_2736, Npcs.TZKEK_2737, Npcs.TZKEK_2738,
    Npcs.TOKXIL_2739, Npcs.TOKXIL_2740, Npcs.YTMEJKOT, Npcs.YTMEJKOT_2742,
    Npcs.KETZEK, Npcs.KETZEK_2744, Npcs.TZTOKJAD, Npcs.YTHURKOT,
)

on_world_init {
    FightCaveWaves.load()
    SafeDeath.register { FightCaves.inCave(it) }
    world.timers[FIGHT_CAVE_TIMER] = 1
}

on_timer(FIGHT_CAVE_TIMER) {
    FightCaves.cycle(world)
    world.timers[FIGHT_CAVE_TIMER] = 1
}

on_obj_option(obj = Objs.CAVE_ENTRANCE_9356, option = "enter") {
    player.queue {
        val cooldown = FightCaves.isOnCooldown(player)
        if (cooldown > 0) {
            val seconds = cooldown * 600 / 1000
            val remaining = if (seconds >= 60) "${seconds / 60} minute${if (seconds / 60 == 1) "" else "s"}" else "$seconds second${if (seconds == 1) "" else "s"}"
            chatNpc("Hey, JalYt, you were in the cave only a moment ago.", "You wait $remaining before going in again.", npc = Npcs.TZHAARMEJJAL, facialExpression = FacialExpression.ANGRY)
            return@queue
        }
        if (Familiar.current(player) != null) {
            chatNpc("No Kimit-Zil in the cave! This is a fight for YOU,", "not your friends!", npc = Npcs.TZHAARMEJJAL, facialExpression = FacialExpression.ANGRY)
            return@queue
        }
        chatNpc("You're on your own now, JalYt.", "Prepare to fight for your life!", npc = Npcs.TZHAARMEJJAL, facialExpression = FacialExpression.ANGRY)
        FightCaves.enter(player, wave = 1, resume = false)
    }
}

on_obj_option(obj = Objs.CAVE_ENTRANCE_9357, option = "enter") {
    val session = FightCaves.session(player)
    if (session == null) {
        player.moveTo(FightCaves.OUTSIDE)
        return@on_obj_option
    }
    player.queue {
        val choice = options("Yes - really leave.", "No, I'll stay.", title = "Really leave?")
        if (choice == 1) {
            FightCaves.leave(session, defeatedJad = false, alreadyOutside = false)
        }
    }
}

WAVE_NPCS.forEach { id ->
    on_npc_death(id) {
        FightCaves.onNpcDeath(npc)
    }
}

on_npc_combat(*FightCaveCombatScripts.TzKih.ids) { npc.queue { FightCaveCombatScripts.TzKih.handleSpecialCombat(this) } }
on_npc_combat(*FightCaveCombatScripts.TokXil.ids) { npc.queue { FightCaveCombatScripts.TokXil.handleSpecialCombat(this) } }
on_npc_combat(*FightCaveCombatScripts.YtMejKot.ids) { npc.queue { FightCaveCombatScripts.YtMejKot.handleSpecialCombat(this) } }
on_npc_combat(*FightCaveCombatScripts.KetZek.ids) { npc.queue { FightCaveCombatScripts.KetZek.handleSpecialCombat(this) } }
on_npc_combat(*FightCaveCombatScripts.TzTokJad.ids) { npc.queue { FightCaveCombatScripts.TzTokJad.handleSpecialCombat(this) } }
on_npc_combat(*FightCaveCombatScripts.YtHurKot.ids) { npc.queue { FightCaveCombatScripts.YtHurKot.handleSpecialCombat(this) } }

/**
 * Logging in with a saved wave resumes the attempt inside a fresh instance.
 */
on_login {
    val wave = player.attr[FightCaves.WAVE_ATTR] ?: return@on_login
    if (wave in 1..FightCaveWaves.TOTAL_WAVES) {
        player.queue {
            wait(2)
            FightCaves.enter(player, wave = wave, resume = true)
        }
    } else {
        player.attr.remove(FightCaves.WAVE_ATTR)
    }
}

on_logout {
    val session = FightCaves.session(player) ?: return@on_logout
    // Progress is kept: the player resumes this wave on their next login.
    player.attr[FightCaves.WAVE_ATTR] = session.wave
    FightCaves.clearSession(player)
}

on_player_pre_death {
    val session = FightCaves.session(player) ?: return@on_player_pre_death
    player.message("You have been defeated!")
    player.attr[DIED_IN_CAVE] = true
    FightCaves.leave(session, defeatedJad = false, alreadyOutside = true)
}

val DIED_IN_CAVE = AttributeKey<Boolean>()

on_player_death {
    if (player.attr[DIED_IN_CAVE] == true) {
        player.attr.remove(DIED_IN_CAVE)
        player.moveTo(FightCaves.OUTSIDE)
    }
}
