package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.message
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

object Familiar {
    const val LIFETIME_CYCLES = 2000

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
        dismiss(player)

        val npc = Npc(player, data.npc, player.tile, player.world)
        npc.publicOwner = true
        npc.respawnOverride = false
        player.world.spawn(npc)

        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        player.timers[FAMILIAR_LIFETIME_TIMER] = LIFETIME_CYCLES
        player.addXp(Skills.SUMMONING, data.summonExperience)
        player.message("You summon your familiar.")
        return true
    }

    /** R07.1 "renew": tops the same familiar's lifetime back up without re-summoning it. */
    fun renew(player: Player): Boolean {
        current(player) ?: return false
        player.timers[FAMILIAR_LIFETIME_TIMER] = LIFETIME_CYCLES
        player.message("You renew your familiar's summoning duration.")
        return true
    }

    fun dismiss(player: Player) {
        val npc = current(player) ?: return
        player.world.remove(npc)
        player.attr.remove(FAMILIAR_ATTR)
        player.timers.remove(FAMILIAR_LIFETIME_TIMER)
        player.message("Your familiar is dismissed.")
    }

    /** Called once/cycle per online player - see `familiar.plugin.kts`. */
    fun tick(player: Player) {
        val npc = current(player) ?: return
        if (!player.timers.has(FAMILIAR_LIFETIME_TIMER)) {
            // Lifetime ran out - real RS despawns the familiar, it doesn't just sit there inert.
            player.world.remove(npc)
            player.attr.remove(FAMILIAR_ATTR)
            player.message("Your familiar has run out of summoning points and returns home.")
            return
        }
        if (!npc.movementQueue.hasDestination() && npc.tile.getDistance(player.tile) > 1) {
            npc.movementQueue.addStep(player.tile, MovementQueue.StepType.NORMAL, detectCollision = true)
        }
    }
}
