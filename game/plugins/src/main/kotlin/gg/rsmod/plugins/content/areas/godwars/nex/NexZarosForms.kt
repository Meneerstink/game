package gg.rsmod.plugins.content.areas.godwars.nex

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * RCV-011 Q-043-c: Nex's Zaros-phase prayer forms. The port showed one static Soul Split icon for the whole phase
 * (server-side only; an npc's overhead is drawn from its definition, so the client never saw it) and healed from every
 * hit.
 *
 * Sources: the revision-667 cache (`NexCacheProbeTests`) gives each Nex form its own overhead — 13447 none, 13448 Soul
 * Split (headIcon 20), 13449 Deflect Melee (12), 13450 Wrath (19). Novite 667 `Nex.processNPC` cycles
 * 13447 → 13448 → 13449 → 13447 in the Zaros phase (counter reset to 35, one transform when it reaches 0, so each form
 * lasts 36 ticks and the first transform happens on the phase's first tick), `NexCombat.sendSoulSplit` heals only in
 * 13448, `Nex.handleIngoingHit` deflects melee in 13449, and `Nex.sendDeath` turns her into 13450.
 *
 * SOURCE_BLOCKED (not ported): Novite's random 15776 ranged-deflect form (15776 is not a 667 npc) and its x1.23 damage
 * factor in the Zaros phase (no second source).
 */
object NexZarosForms {
    /** Novite `resetPrayerTicks` for 13447..13449. */
    const val FORM_TICKS = 35

    val CYCLE = intArrayOf(Npcs.NEX, Npcs.NEX_13448, Npcs.NEX_13449)
    const val DEATH_FORM = Npcs.NEX_13450

    fun next(form: Int): Int {
        val index = CYCLE.indexOf(form)
        return if (index < 0) CYCLE[1] else CYCLE[(index + 1) % CYCLE.size]
    }

    /** Novite tick: count down, or transform and reset the counter. Returns the form and the remaining ticks. */
    fun step(
        form: Int,
        ticks: Int,
    ): Pair<Int, Int> = if (ticks > 0) form to ticks - 1 else next(form) to FORM_TICKS

    /** The overhead of [form], equal to its 667 cache headIcon; drives the server-side protection checks. */
    fun prayerIcon(form: Int): Int =
        when (form) {
            Npcs.NEX_13448 -> PrayerIcon.SOUL_SPLIT.id
            Npcs.NEX_13449 -> PrayerIcon.DEFLECT_MELEE.id
            DEATH_FORM -> PrayerIcon.WRATH.id
            else -> -1
        }

    fun soulSplitActive(form: Int): Boolean = form == Npcs.NEX_13448

    fun formOf(npc: Npc): Int = if (npc.getTransmogId() >= 0) npc.getTransmogId() else npc.id

    /** Shows [form] to clients (transmog, always an explicit id) and sets the matching server-side overhead. */
    fun apply(
        npc: Npc,
        form: Int,
    ) {
        npc.setTransmogId(form)
        npc.prayerIcon = prayerIcon(form)
    }
}
