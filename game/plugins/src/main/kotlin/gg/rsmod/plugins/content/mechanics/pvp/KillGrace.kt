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
 *
 * Audit D-06: only a kill judged [ValidPkKill.Verdict.VALID] earns it ([grantForKill]) - a staged kill on a naked alt
 * (low risk, same address, repeat victim, fed kill ...) no longer buys a minute of immunity.
 * Audit D-05: every attack the holder makes afterwards ends it ([PvpSkull.onHitRegistered]), except the trailing hits
 * of the attack that made the kill ([isTrailingHit]).
 */
object KillGrace {
    /** 1 minute, in 0.6s game cycles. */
    const val DURATION_CYCLES = 100

    /**
     * Audit D-05: how long after the grant a hit on the killed player still counts as a trailing hit of the lethal
     * attack (claws/double hits, a projectile already in flight). The victim is dead (0 hitpoints) for the whole
     * death sequence, which is longer than this, and hits on a dead player are dropped by the engine; the window is
     * only a safety margin for the grant cycle itself.
     */
    const val TRAILING_HIT_CYCLES = 2

    /** resetOnDeath: the grace ends early when the killer themselves dies, per the owner spec -
     * no separate death hook needed, this is the same mechanism PVP_AGGRESSOR_ATTR/skull timers
     * already use for the same class of "ends on death" rule. */
    val GRACE_TIMER = TimerKey(resetOnDeath = true)

    /** The player whose death earned the current grace; only the trailing hits of the lethal attack on them (see
     * [isTrailingHit]) are not "attacking someone new". */
    private val KILLED_ATTR = gg.rsmod.game.model.attr.AttributeKey<java.lang.ref.WeakReference<Player>>()

    /** Audit D-05: the world cycle the current grace was granted on. */
    private val GRANTED_CYCLE_ATTR = gg.rsmod.game.model.attr.AttributeKey<Int>()

    /**
     * Starts the grace unconditionally. Production code goes through [grantForKill], which applies the Audit D-06 rules
     * first; this stays public for tests and admin tooling.
     */
    fun grant(killer: Player, victim: Player? = null) {
        killer.timers[GRACE_TIMER] = DURATION_CYCLES
        killer.attr[GRANTED_CYCLE_ATTR] = killer.world.currentCycle
        if (victim != null) killer.attr[KILLED_ATTR] = java.lang.ref.WeakReference(victim) else killer.attr.remove(KILLED_ATTR)
    }

    /**
     * Audit D-06: whether a kill with [verdict] earns the grace - only a valid PK kill, never a self-kill, never in
     * multi-combat ([multiCombat] = the victim's tile is multi-way).
     */
    fun earnsGrace(verdict: ValidPkKill.Verdict, self: Boolean, multiCombat: Boolean): Boolean = verdict.valid && !self && !multiCombat

    /**
     * Audit D-06: the one production entry point. Call once per resolved PvP death with the kill's [ValidPkKill] verdict
     * (the same verdict the emblem and killstreak rewards use - `DeadmanEmblem.onPvpDeath` does exactly that). Returns
     * whether the grace was granted.
     */
    fun grantForKill(killer: Player, victim: Player, verdict: ValidPkKill.Verdict, multiCombat: Boolean): Boolean {
        if (!earnsGrace(verdict, killer === victim, multiCombat)) return false
        grant(killer, victim)
        return true
    }

    fun earnedFrom(killer: Player, victim: Player): Boolean = killer.attr[KILLED_ATTR]?.get() === victim

    /**
     * Audit D-05: a hit by [killer] on [victim] that still belongs to the attack that earned the grace: [victim] is the
     * player whose death earned it and is still dead, or the hit registers within [TRAILING_HIT_CYCLES] of the grant.
     * Any other hit - a retaliation, another player, or the same victim after their respawn - is a new attack.
     */
    fun isTrailingHit(killer: Player, victim: Player): Boolean {
        if (!isProtected(killer) || !earnedFrom(killer, victim)) return false
        if (victim.isDead()) return true
        val granted = killer.attr[GRANTED_CYCLE_ATTR] ?: return false
        return killer.world.currentCycle - granted <= TRAILING_HIT_CYCLES
    }

    fun isProtected(player: Player): Boolean = player.timers.has(GRACE_TIMER)

    fun cyclesLeft(player: Player): Int = if (player.timers.exists(GRACE_TIMER)) player.timers[GRACE_TIMER] else 0

    /** Call when [player] attacks another player, or logs out - the two remaining "ends it
     * early" triggers the TimerKey's own resetOnDeath does not already cover. */
    fun endEarly(player: Player) {
        player.timers.remove(GRACE_TIMER)
        player.attr.remove(KILLED_ATTR)
        player.attr.remove(GRANTED_CYCLE_ATTR)
    }
}
