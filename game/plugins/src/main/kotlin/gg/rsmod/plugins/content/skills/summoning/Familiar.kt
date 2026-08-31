package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import java.lang.ref.WeakReference

/**
 * R07.1/R07.2: real summon/follow/renew/dismiss for [SummoningPouchData]'s already-existing
 * full pouch roster (Spirit Wolf through Steel Titan/Pack Yak/War Tortoise) - not just
 * registrations, an actually working core mechanic every specific familiar builds on.
 *
 * Familiar lifetime is a flat 20 minutes (2000 cycles, matching [gg.rsmod.plugins.content.mechanics.pvp.PvpSkull]'s
 * own established cycle-duration convention) - a conservative implementation default, not a
 * real per-familiar summoning-points-with-upkeep-cost economy (SummoningPouchData has no
 * upkeep-cost field to derive one from; inventing per-familiar costs would be exactly the kind
 * of unrequested precision the master spec warns against). Renewing re-arms the same timer
 * without re-summoning, matching real RS "renew" behaviour.
 *
 * Following reuses the real, already-existing [MovementQueue] every pawn's normal walking goes
 * through (the same system NPCs use to wander/chase) rather than a simplified teleport-style
 * step - real collision-respecting, animated movement, not a hack.
 */
val FAMILIAR_ATTR = AttributeKey<WeakReference<Npc>>()
val FAMILIAR_LIFETIME_TIMER = TimerKey(tickOffline = false, resetOnDeath = false)

/** Last time-remaining string pushed to the HUD, so [Familiar.tick] doesn't spam an
 *  [gg.rsmod.plugins.api.ext.setComponentText] packet every cycle - only when the displayed
 *  value actually changes. */
private val FAMILIAR_HUD_TEXT_ATTR = AttributeKey<String>()

object Familiar {
    const val LIFETIME_CYCLES = 2000

    /** R07 follower interface (see InterfaceDestination.SUMMONING_TAB for the evidence trail). */
    private const val HUD_INTERFACE = 662
    private const val HUD_TIME_COMPONENT_A = 43
    private const val HUD_TIME_COMPONENT_B = 48
    private const val HUD_BOB_BUTTON = 67

    /**
     * Pushes the confirmed-real duration text (components 43/48) and Take-BoB-items button
     * (67, hidden for non-BoB familiars) to interface 662. Deliberately does NOT touch the
     * "Summoning points remaining" placeholder (component 41/44) or the special-move/portrait
     * icons: [SummoningPouchData] has no upkeep-cost field and no verified sprite-per-familiar
     * mapping exists in this cache, so pushing either would be invented data, not audited data.
     */
    private fun updateHud(player: Player) {
        val npc = current(player)
        if (npc == null) {
            if (player.attr.has(FAMILIAR_HUD_TEXT_ATTR)) {
                player.setComponentText(HUD_INTERFACE, HUD_TIME_COMPONENT_A, "")
                player.setComponentText(HUD_INTERFACE, HUD_TIME_COMPONENT_B, "")
                player.setComponentHidden(HUD_INTERFACE, HUD_BOB_BUTTON, true)
                player.attr.remove(FAMILIAR_HUD_TEXT_ATTR)
            }
            return
        }
        val cycles = if (player.timers.has(FAMILIAR_LIFETIME_TIMER)) player.timers[FAMILIAR_LIFETIME_TIMER] else 0
        val totalSeconds = cycles * player.world.gameContext.cycleTime / 1000
        val text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        if (player.attr[FAMILIAR_HUD_TEXT_ATTR] != text) {
            player.attr[FAMILIAR_HUD_TEXT_ATTR] = text
            player.setComponentText(HUD_INTERFACE, HUD_TIME_COMPONENT_A, text)
            player.setComponentText(HUD_INTERFACE, HUD_TIME_COMPONENT_B, text)
            player.setComponentHidden(HUD_INTERFACE, HUD_BOB_BUTTON, !BeastOfBurden.isBobNpc(npc.id))
        }
    }

    fun current(player: Player): Npc? {
        val npc = player.attr[FAMILIAR_ATTR]?.get() ?: return null
        if (!player.world.npcs.contains(npc)) {
            player.attr.remove(FAMILIAR_ATTR)
            return null
        }
        return npc
    }

    fun summon(
        player: Player,
        data: SummoningPouchData,
    ): Boolean {
        if (player.skills.getMaxLevel(Skills.SUMMONING) < data.level) {
            player.message("You need a Summoning level of ${data.level} to summon this familiar.")
            return false
        }
        // R07.1 pouch-consumption fix: summon() never removed the pouch at all (root cause of
        // the owner-reported "pouch remains in inventory after summon" bug) - consume exactly
        // one pouch, and fail before touching XP/timer/familiar state if it's not actually there
        // (e.g. this same pouch triggered summon twice in one client tick).
        if (!player.inventory.remove(data.pouch, assureFullRemoval = true).hasSucceeded()) {
            return false
        }
        dismiss(player)

        val npc = Npc(player, data.npc, player.tile, player.world)
        npc.publicOwner = true
        npc.respawnOverride = false
        player.world.spawn(npc)

        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        player.timers[FAMILIAR_LIFETIME_TIMER] = LIFETIME_CYCLES
        player.addXp(Skills.SUMMONING, data.summonExperience)
        player.message("You summon your familiar.")
        updateHud(player)
        return true
    }

    /** R07.1 "renew": tops the same familiar's lifetime back up without re-summoning it. */
    fun renew(player: Player): Boolean {
        current(player) ?: return false
        player.timers[FAMILIAR_LIFETIME_TIMER] = LIFETIME_CYCLES
        player.message("You renew your familiar's summoning duration.")
        updateHud(player)
        return true
    }

    fun dismiss(player: Player) {
        val npc = current(player) ?: return
        player.world.remove(npc)
        player.attr.remove(FAMILIAR_ATTR)
        player.timers.remove(FAMILIAR_LIFETIME_TIMER)
        player.message("Your familiar is dismissed.")
        updateHud(player)
    }

    /**
     * "Call familiar" - instant recall, distinct from passive following. Reuses the real
     * [gg.rsmod.game.model.entity.Pawn.teleportNpc] respawn-style reposition API (sets
     * moved/teleported/invisible + clears movement queue) rather than inventing a new
     * adjacent-free-tile finder - no such utility exists anywhere in this codebase, and the
     * existing follow logic below already proves tile-sharing between player and npc works.
     */
    fun call(player: Player): Boolean {
        val npc = current(player) ?: return false
        npc.teleportNpc(player.tile)
        player.message("You call your familiar to your side.")
        return true
    }

    /** Called once/cycle per online player - see `familiar.plugin.kts`. */
    fun tick(player: Player) {
        val npc = current(player) ?: return
        if (!player.timers.has(FAMILIAR_LIFETIME_TIMER)) {
            // Lifetime ran out - real RS despawns the familiar, it doesn't just sit there inert.
            player.world.remove(npc)
            player.attr.remove(FAMILIAR_ATTR)
            player.message("Your familiar has run out of summoning points and returns home.")
            updateHud(player)
            return
        }
        val distance = npc.tile.getDistance(player.tile)
        if (npc.tile.height != player.tile.height || distance > Player.NORMAL_VIEW_DISTANCE) {
            // Plane change / region teleport put the familiar out of walking range - a normal
            // MovementQueue step can never catch up (or can't cross planes at all), so recover
            // it the same way `call()` does rather than leaving it stranded/left behind.
            npc.teleportNpc(player.tile)
        } else if (!npc.movementQueue.hasDestination() && distance > 1) {
            npc.movementQueue.addStep(player.tile, MovementQueue.StepType.NORMAL, detectCollision = true)
        }
        updateHud(player)
    }
}
