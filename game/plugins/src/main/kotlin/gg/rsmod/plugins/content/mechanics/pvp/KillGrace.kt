package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey

/**
 * Deadman PvP guards plan (owner-approved 2026-09-16): "killing someone in a single-combat zone
 * grants the killer a 1-minute period where they cannot be attacked; attacking, dying or logging
 * out ends it early." The single-combat-zone requirement gates the GRANT (prevents farming the
 * grace period in a multi-combat pile-up); the protection itself then applies globally for the
 * full minute regardless of subsequent movement, since the owner describes it as an unconditional
 * temporal grant once earned, not a location-bound one.
 */
object KillGrace {
    /** 1 minute, in 0.6s game cycles. */
    const val DURATION_CYCLES = 100

    /** resetOnDeath: the grace ends early when the killer themselves dies, per the owner spec -
     * no separate death hook needed, this is the same mechanism PVP_AGGRESSOR_ATTR/skull timers
     * already use for the same class of "ends on death" rule. */
    val GRACE_TIMER = TimerKey(resetOnDeath = true)

    /** The player whose death earned the current grace; hits still landing on them (the lethal hit's own action, the
     * trailing hits of claws/double hits, delayed projectiles) are not "attacking someone new" and must not end it. */
    private val KILLED_ATTR = gg.rsmod.game.model.attr.AttributeKey<java.lang.ref.WeakReference<Player>>()

    fun grant(killer: Player, victim: Player? = null) {
        killer.timers[GRACE_TIMER] = DURATION_CYCLES
        if (victim != null) killer.attr[KILLED_ATTR] = java.lang.ref.WeakReference(victim) else killer.attr.remove(KILLED_ATTR)
    }

    fun earnedFrom(killer: Player, victim: Player): Boolean = killer.attr[KILLED_ATTR]?.get() === victim

    fun isProtected(player: Player): Boolean = player.timers.has(GRACE_TIMER)

    fun cyclesLeft(player: Player): Int = if (player.timers.exists(GRACE_TIMER)) player.timers[GRACE_TIMER] else 0

    /** Call when [player] attacks another player, or logs out - the two remaining "ends it
     * early" triggers the TimerKey's own resetOnDeath does not already cover. */
    fun endEarly(player: Player) {
        player.timers.remove(GRACE_TIMER)
    }
}
