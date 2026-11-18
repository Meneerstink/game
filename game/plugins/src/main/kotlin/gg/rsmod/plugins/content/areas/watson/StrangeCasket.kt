package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.attr.AttributeKey

/**
 * Owner answer Q10: the Strange casket upstairs in Watson's house (OSRS loc 34733 -> local 62743, option "Search"). OSRS Wiki
 * Transcript:Strange casket (2026-09-14): talking to it opts the player in to or out of Mimic challenges (disabled by default); a player
 * carrying a Mimic casket is offered the fight. The opt-in is what the elite casket's 1/35 Mimic roll will read.
 * Encounter bindings live in mimic_encounter.plugin.kts; arena import/timing evidence gates are in the CLUE handoff.
 */
object StrangeCasket {
    const val LOC = 62743
    const val NAME = "<col=0000ff>Strange casket</col>"

    /** OSRS Wiki "Mimic" item page: opening the Mimic before the fight. */
    const val OPEN_BEFORE_FIGHT = "Visit the Strange Casket, upstairs in Watson's house in Hosidius, to attempt the Mimic's challenge."

    /** Player opted in to Mimic challenges (persistent). */
    val MIMIC_CHALLENGES = AttributeKey<Boolean>(persistenceKey = "mimic_challenges")
}
