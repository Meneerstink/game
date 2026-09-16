package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.combat.isBeingAttacked
import gg.rsmod.plugins.content.magic.MagicSpells.on_magic_spell_button
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER

val TERMINATE_HOME_TELEPORT_NEUTRAL: QueueTask.() -> Unit = {
    player.animate(Anims.RESET)
    player.graphic(Gfx.RESET)
}

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
        if (player.hasMoveDestination()) {
            player.message("You can't use that teleport at the moment.")
            return@on_magic_spell_button
        } else if (player.isAttacking()) {
            player.message("You can't use that teleport at the moment.")
            return@on_magic_spell_button
        }

        if (HOME_TELEPORT_TIMER_ENABLED && player.timers.has(HOME_TELEPORT_TIMER) && player.privilege.id != 2) {
            val minutes = player.timers.getMinutesLeft(HOME_TELEPORT_TIMER)

            if (minutes != null) {
                player.message("You need to wait another ${minutes.appendToString("minute")} to cast this spell.")
            } else {
                player.message("You need to wait another couple of seconds to cast this spell.")
            }
            return@on_magic_spell_button
        }

        if (player.timers.has(ACTIVE_COMBAT_TIMER)) {
            player.message("You can't home teleport until 10 seconds after the end of combat.")
            return@on_magic_spell_button
        }

        // Deadman PvP guards plan (2026-09-16): the two-arg canTeleport overload makes a skulled
        // player's 7-second countdown complete this action automatically.
        player.canTeleport(TeleportType.MODERN) {
            player.queue(TaskPriority.STRONG) {
                if (teleport.instant) {
                    instantTeleport(teleport.endTile(world))
                } else {
                    teleport(teleport.endTile(world))
                }
            }
        }
    }
}

val animations =
    arrayOf(Anims.HOME_TELEPORT_STAGE_1, Anims.HOME_TELEPORT_STAGE_2, Anims.HOME_TELEPORT_STAGE_3, Anims.HOME_TELEPORT_STAGE_4,
        Anims.HOME_TELEPORT_STAGE_5, Anims.HOME_TELEPORT_STAGE_6, Anims.HOME_TELEPORT_STAGE_7, Anims.HOME_TELEPORT_STAGE_8,
        Anims.HOME_TELEPORT_STAGE_9, Anims.HOME_TELEPORT_STAGE_10, Anims.HOME_TELEPORT_STAGE_11, Anims.HOME_TELEPORT_STAGE_12,
        Anims.HOME_TELEPORT_STAGE_13, Anims.HOME_TELEPORT_STAGE_14, Anims.HOME_TELEPORT_STAGE_15, Anims.HOME_TELEPORT_STAGE_16,
        Anims.HOME_TELEPORT_STAGE_17)
val graphics = arrayOf(Gfx.HOME_TELEPORT_STAGE_1, Gfx.HOME_TELEPORT_STAGE_2, Gfx.HOME_TELEPORT_STAGE_3, Gfx.HOME_TELEPORT_STAGE_4,
    Gfx.HOME_TELEPORT_STAGE_5, Gfx.HOME_TELEPORT_STAGE_6, Gfx.HOME_TELEPORT_STAGE_7, Gfx.HOME_TELEPORT_STAGE_8,
    Gfx.HOME_TELEPORT_STAGE_9, Gfx.HOME_TELEPORT_STAGE_10, Gfx.HOME_TELEPORT_STAGE_11, Gfx.HOME_TELEPORT_STAGE_12,
    Gfx.HOME_TELEPORT_STAGE_13, Gfx.HOME_TELEPORT_STAGE_14, Gfx.HOME_TELEPORT_STAGE_15, Gfx.HOME_TELEPORT_STAGE_16,
    Gfx.HOME_TELEPORT_STAGE_17)

suspend fun QueueTask.teleport(endTile: Tile) {
    waitAndCheckCombat(2)
    terminateAction = TERMINATE_HOME_TELEPORT_NEUTRAL
    repeat(17) { cycle ->
        player.animate(animations[cycle])
        player.graphic(graphics[cycle])
        waitAndCheckCombat(1)
    }
    player.animate(Anims.RESET)
    // moveTo() only flags a real teleport (instant snap) when the destination is beyond normal
    // view distance - casting Home Teleport from just outside/near Ferox (well within that
    // distance of the arrival tile) rendered as a walk/glide into place instead of a teleport.
    // teleportTo() always snaps, matching every other teleport spell (see magic/PawnExt.kt).
    player.teleportTo(endTile)
    player.timers[HOME_TELEPORT_TIMER] = HOME_TELEPORT_TIMER_DELAY
}

/**
 * 2026-09-06 owner human retest: the Ferox Home Teleport destination is correct and must stay
 * correct, but casting it must be INSTANT - no standard 17-stage animation/graphic sequence, no
 * casting-time delay, no glide. Skips [teleport]'s whole cycle loop and its 2-tick pre-cast
 * combat-interrupt window entirely rather than shortening either, since an instant teleport has
 * no casting time for combat to interrupt in the first place - it simply fires.
 */
suspend fun QueueTask.instantTeleport(endTile: Tile) {
    player.animate(Anims.RESET)
    player.graphic(Gfx.RESET)
    player.teleportTo(endTile)
    player.timers[HOME_TELEPORT_TIMER] = HOME_TELEPORT_TIMER_DELAY
}

suspend fun QueueTask.waitAndCheckCombat(cycles: Int): Boolean {
    for (i in 0 until cycles) {
        wait(1)
        if (player.isBeingAttacked()) {
            terminate()
            return false
        }
    }
    return true
}

enum class HomeTeleport(
    val spellName: String,
    val endTile: World.() -> Tile,
    /** True for a teleport that must fire with no animation/casting delay - see [instantTeleport]. */
    val instant: Boolean = false,
) {
    // 2026-09-06 owner human retest: "the teleport must become INSTANT" - this project's home
    // destination is Ferox Enclave, so this is the Ferox Home Teleport specifically.
    HOME("Home Teleport", { gameContext.home }, instant = true),
    // The same Ferox home teleport from the Ancient Magicks (193:48) and Lunar (430:39) books.
    ANCIENT_HOME("Edgeville Home Teleport", { gameContext.home }, instant = true),
    LUNAR_HOME("Lunar Home Teleport", { gameContext.home }, instant = true),
    ;

    companion object {
        val values = enumValues<HomeTeleport>()
    }
}
