package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport

/**
 * N72 (owner 2026-10-31 20:0x: "home teleport is gratis die moet naar grand exchange gaan hij mag niet instant zijn net zoals
 * normale osrs lumby teleport met animatie en om de zoveel minuten"): the Home Teleport of all three books (Standard 192:24,
 * Ancient 193:48 "Edgeville Home Teleport", Lunar 430:39) is the OSRS Lumbridge Home Teleport:
 *  - free (no runes), to the home spot at the Grand Exchange (game.yml home: the Royal Hall at the GE, where players spawn);
 *  - not instant: the classic 17-stage cast (sequences 1722 .. 4852 with spotanims 775 .. 1713, one stage a tick after a 2-tick
 *    start - the same staged cast OSRS Lumbridge Home Teleport plays, as the 2011scape-base had it before 2026-09-06), about 11
 *    seconds; moving (a click elsewhere ends the queue), an attack or a hit taken stops it, nothing is spent;
 *  - once every 30 minutes (OSRS Wiki "Home Teleport": "can only be cast once every 30 minutes"), counted offline too:
 *    "You need to wait another N minutes to cast this spell.";
 *  - the shared Deadman gate decides first like every teleport (N90/N91: no 2011 refusals - skulled -> the 7-second countdown,
 *    then the cast; unskulled and hit by a player / non-boss npc in the last 7 seconds -> "You must be out of combat for another X
 *    seconds to teleport."). Pressing it while running stops the walk and starts the cast (it is never refused for running).
 * Guard: HomeTeleportDeadmanGateTests.
 */
val HOME_TELEPORT_TIMER_DELAY = 3000 // 30 minutes of 600 ms ticks
val HOME_TELEPORT_TIMER = TimerKey(persistenceKey = "home_teleport_delay", tickOffline = true)

val HOME_TELEPORT_ANIMATIONS = intArrayOf(1722, 1723, 1724, 1725, 2798, 2799, 2800, 3195, 4643, 4645, 4646, 4847, 4848, 4849, 4850, 4851, 4852)
val HOME_TELEPORT_GRAPHICS = intArrayOf(775, 800, 801, 802, 803, 804, 1703, 1704, 1705, 1706, 1707, 1708, 1709, 1710, 1711, 1712, 1713)

HomeTeleport.values.forEach { teleport ->

    on_magic_spell_button(teleport.spellName) {
        if (player.timers.has(HOME_TELEPORT_TIMER) && player.privilege.id != 2) {
            // N590 (owner 2026-11-05 'couple of seconds ... waarom'): OSRS Wiki Lumbridge Home Teleport - the player "is notified with
            // the remaining time in minutes". Whole minutes rounded UP (100 ticks = 1 minute), so the last minute reads '1 minute'
            // instead of the rsmod text 'a couple of seconds', and right after a cast it reads 30, not 29.
            val minutes = (player.timers[HOME_TELEPORT_TIMER] + 99) / 100
            player.message("You need to wait another ${minutes.appendToString("minute")} to cast this spell.")
            return@on_magic_spell_button
        }
        // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled player's 7-second countdown start
        // the cast by itself.
        player.canTeleport(TeleportType.MODERN) {
            player.stopMovement()
            player.queue(TaskPriority.STANDARD) {
                castHome(teleport.endTile(world))
            }
        }
    }
}

/** Ends a cast that was stopped: the player stands still again, the spell can be pressed again (no cooldown, nothing spent). */
val TERMINATE_HOME_TELEPORT: QueueTask.() -> Unit = {
    player.animate(-1)
    player.graphic(-1)
}

suspend fun QueueTask.castHome(endTile: Tile) {
    terminateAction = TERMINATE_HOME_TELEPORT
    fun combatTicks() = if (player.timers.exists(TELEPORT_COMBAT_TIMER)) player.timers[TELEPORT_COMBAT_TIMER] else 0
    var combat = combatTicks()
    /** A hit taken re-arms the teleport combat timer: then the cast stops (OSRS: interrupted by combat). */
    fun hit(): Boolean {
        val now = combatTicks()
        val hit = now > combat
        combat = now
        return hit
    }
    repeat(2) {
        wait(1)
        if (hit()) return terminate()
    }
    for (stage in HOME_TELEPORT_ANIMATIONS.indices) {
        player.animate(HOME_TELEPORT_ANIMATIONS[stage])
        player.graphic(HOME_TELEPORT_GRAPHICS[stage])
        wait(1)
        if (hit()) return terminate()
    }
    player.animate(-1)
    player.graphic(-1)
    player.moveTo(endTile)
    world.spawn(AreaSound(endTile, HOME_TELEPORT_SOUND, 10, 1))
    player.timers[HOME_TELEPORT_TIMER] = HOME_TELEPORT_TIMER_DELAY
}

/** Sound 200 (teleport_all): the arrival sound of every teleport spell of this server (teleport_spells). */
val HOME_TELEPORT_SOUND = 200

enum class HomeTeleport(
    val spellName: String,
    val endTile: World.() -> Tile,
) {
    // The home spot (game.yml home-x/z): the Royal Hall at the Grand Exchange, where players spawn (owner 2026-10-06 / 10-31).
    HOME("Home Teleport", { gameContext.home }),
    // The same Grand Exchange home teleport from the Ancient Magicks (193:48) and Lunar (430:39) books.
    ANCIENT_HOME("Edgeville Home Teleport", { gameContext.home }),
    LUNAR_HOME("Lunar Home Teleport", { gameContext.home }),
    ;

    companion object {
        val values = enumValues<HomeTeleport>()
    }
}
