package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * NOTE: Alycia
 *
 * It appears that the home teleport cooldown has indeed been
 * removed in an update on March 28th, 2011.
 *
 * I'll leave the code in-tact in case anyone wants to use it
 * on their project(s) in the future.
 *
 * Source: https://cdn.discordapp.com/attachments/1082330464787902576/1082330464926322738/image.png
 */
val HOME_TELEPORT_TIMER_ENABLED = false
val HOME_TELEPORT_TIMER_DELAY = 3000
val HOME_TELEPORT_TIMER = TimerKey(persistenceKey = "home_teleport_delay", tickOffline = true)

HomeTeleport.values.forEach { teleport ->

    on_magic_spell_button(teleport.spellName) {
        // N90/N91 (owner 2026-10-17: refused "at the moment" while running away; refused for ten seconds after npc combat
        // where he expected the Deadman timer): the
        // 2011scape-base refusals that stood here - no cast while walking or attacking, and the 10-second combat wait of the
        // 2011 lodestone cast (Darkan LodestoneAction.java:167-173, Novite HomeTeleport.java:62-69) - are gone. Home teleport
        // is an ordinary instant book teleport (owner 09-06 / 09-19), so the shared Deadman gate decides like for every
        // other teleport (canTeleport -> DeadmanTimerGate.teleportDecision): skulled -> the 7-second countdown; unskulled
        // and hit by a player / non-boss npc in the last 7 seconds -> "You must be out of combat for another X seconds to
        // teleport."; otherwise instant, also while running (player.teleport stops the walk). Guard: HomeTeleportDeadmanGateTests.
        if (HOME_TELEPORT_TIMER_ENABLED && player.timers.has(HOME_TELEPORT_TIMER) && player.privilege.id != 2) {
            val minutes = player.timers.getMinutesLeft(HOME_TELEPORT_TIMER)

            if (minutes != null) {
                player.message("You need to wait another ${minutes.appendToString("minute")} to cast this spell.")
            } else {
                player.message("You need to wait another couple of seconds to cast this spell.")
            }
            return@on_magic_spell_button
        }

        // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled
        // player's 7-second countdown complete this action automatically.
        player.canTeleport(TeleportType.MODERN) {
            // Owner 2026-09-19: the home teleports get their spellbook's teleport animation, graphic and sound (the same
            // route as every other teleport spell of that book) instead of the silent instant snap of 2026-09-06.
            player.teleport(teleport.endTile(world), teleport.bookType, startSound = false)
            world.spawn(AreaSound(player.tile, HOME_TELEPORT_SOUND, 10, 1))
            player.timers[HOME_TELEPORT_TIMER] = HOME_TELEPORT_TIMER_DELAY
        }
    }
}

/** Sound 200 (teleport_all): the sound every standard/Ancient/Lunar teleport spell of this server plays (teleport_spells). */
val HOME_TELEPORT_SOUND = 200

enum class HomeTeleport(
    val spellName: String,
    val endTile: World.() -> Tile,
    /**
     * The spellbook's own teleport (animation, graphic, delay). The 2011 staged 17-stage lodestone cast and the 09-06 silent
     * snap were removed with N90: every home teleport is this book teleport behind the shared Deadman gate.
     */
    val bookType: TeleportType,
) {
    // This project's home destination is Ferox Enclave. 2026-09-06 the owner asked for an instant teleport; 2026-09-19 the
    // owner asked for the same teleport animation and sound in every book, so each book uses its own teleport type.
    HOME("Home Teleport", { gameContext.home }, bookType = TeleportType.MODERN),
    // The same Ferox home teleport from the Ancient Magicks (193:48) and Lunar (430:39) books.
    ANCIENT_HOME("Edgeville Home Teleport", { gameContext.home }, bookType = TeleportType.ANCIENT),
    LUNAR_HOME("Lunar Home Teleport", { gameContext.home }, bookType = TeleportType.LUNAR),
    ;

    companion object {
        val values = enumValues<HomeTeleport>()
    }
}
