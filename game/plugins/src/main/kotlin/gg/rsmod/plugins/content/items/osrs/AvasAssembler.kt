package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT Ava's assembler - metal attraction and Commune (OSRS Wiki "Ava's assembler" / "Ava's device", raw wikitext
 * 2026-09-17c): "Approximately every 3.5 minutes, Ava's assembler will attract an assortment of mithril items, provided that the
 * player has moved at least three tiles since the last pickup"; the reward table below is the page's (rarities out of 2000);
 * "Players can commune with the assembler to stop it from gathering junk by right-clicking "Commune" on the item while it is worn
 * or in the inventory"; when the item fits nowhere: "Ava's contraption makes an odd burping sound."
 * Owner live report 2026-09-17c: "commune option avas assembler does nothing".
 *
 * Commune GUI (owner 2026-09-18: exact OSRS GUI, not a chat message): plain chatbox message -> "Ask it to stop/start gathering
 * junk?" Yes/No -> plain confirmation. Wording from the Zenyte OSRS server (RangingCape.java / MaxCape.java / NewMaxCapes.java, one
 * shared dialogue for every Ava's-type device), the only source with the text; the OSRS Wiki pages do not quote it. The 667 message
 * box does not auto-wrap, so the lines are pre-split. "approximately" 3.5 minutes is taken as 350 ticks.
 */
object AvasAssembler {
    class Commune(val intro: Array<String>, val question: String, val confirm: Array<String>) {
        companion object {
            val STOP =
                Commune(
                    arrayOf(
                        "The undead chicken can protect some of your ammunition while",
                        "you're ranging, and will also gather random metal items for you.",
                    ),
                    "Ask it to stop gathering junk?",
                    arrayOf(
                        "You somehow communicate your message to the undead chicken.",
                        "Henceforth it will no longer gather up random metal items",
                        "while you've got it equipped.",
                    ),
                )
            val START =
                Commune(
                    arrayOf(
                        "The undead chicken understands that you currently don't want it",
                        "to accumulate random metal items while you've got it equipped.",
                    ),
                    "Ask it to start gathering junk?",
                    arrayOf(
                        "You somehow communicate your message to the undead chicken.",
                        "Henceforth it will gather up random metal items while you've",
                        "got it equipped.",
                    ),
                )

            fun forState(gatheringStopped: Boolean): Commune = if (gatheringStopped) START else STOP
        }
    }

    const val INTERVAL_TICKS = 350
    const val MIN_TILES_MOVED = 3
    const val FULL_MESSAGE = "Ava's contraption makes an odd burping sound."

    /** True once the player communed to stop the gathering; persisted per account. */
    val GATHERING_STOPPED = AttributeKey<Boolean>(persistenceKey = "avas_assembler_gathering_stopped")

    /** Item id to weight, out of [TOTAL_WEIGHT]. */
    val ATTRACTION: List<Pair<Int, Int>> =
        listOf(
            Items.MITHRIL_ARROW to 1975,
            Items.MITHRIL_DART to 5,
            Items.MITHRIL_KNIFE to 5,
            Items.MITHRIL_ORE to 5,
            Items.MITHRIL_NAILS to 5,
            Items.ADAMANT_ARROW to 1,
            Items.BROKEN_ARROW to 1,
            Items.MITHRIL_ARROWTIPS to 1,
            Items.MITHRIL_MED_HELM to 1,
            Items.MITHRIL_BAR to 1,
        )

    val TOTAL_WEIGHT: Int = ATTRACTION.sumOf { it.second }

    /** The attracted item for [roll] in `0 until TOTAL_WEIGHT`. */
    fun attracted(roll: Int): Int {
        var left = roll
        for ((item, weight) in ATTRACTION) {
            if (left < weight) return item
            left -= weight
        }
        error("roll $roll outside 0 until $TOTAL_WEIGHT")
    }
}
