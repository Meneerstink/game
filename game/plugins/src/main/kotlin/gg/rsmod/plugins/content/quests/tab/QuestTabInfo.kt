package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.plugins.content.mechanics.pvp.Killstreaks
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.Block
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.number
import java.util.Locale

/**
 * The texts of the quest tab's Info window, one per Deadman block. Every number comes from the code that applies it:
 * the loyalty grants (daily.plugin.kts 500 per daily timer, store.plugin.kts 50 per 30 minutes online), the Deadman Points
 * per kill ([Killstreaks]), the emblem table ([DeadmanEmblem]) and the breach schedule and rewards ([DeadmanBreach],
 * [BreachLoot]). The stores and their keepers are [gg.rsmod.plugins.content.mechanics.store.StoreNpcs] (upstairs in the
 * mansion - the Royal Hall at the Grand Exchange).
 */
object QuestTabInfo {
    /** daily.plugin.kts: 500 Loyalty Points each time the 120,000-tick daily timer runs out (20 hours). */
    const val DAILY_LOYALTY = 500
    const val DAILY_HOURS = 20

    /** store.plugin.kts: LOYALTY_PLAYTIME_POINTS per LOYALTY_PLAYTIME_CYCLES (3,000 ticks = 30 minutes) online. */
    const val PLAYTIME_LOYALTY = 50
    const val PLAYTIME_MINUTES = 30

    private const val STORES = "upstairs in the mansion at the Grand Exchange"

    fun title(block: Block): String = block.title

    fun text(block: Block): String =
        when (block) {
            Block.LOYALTY ->
                "Loyalty Points reward the time you spend in the world.<br><br>" +
                    "You earn $PLAYTIME_LOYALTY for every $PLAYTIME_MINUTES minutes you are online, $DAILY_LOYALTY from your daily " +
                    "reward (every $DAILY_HOURS hours) and more for completing daily objectives.<br><br>" +
                    "Spend them in the Loyalty Store: talk to Xuan, $STORES."
            Block.DONATOR ->
                "Donator Points are the thanks for supporting the server with a donation. A member of staff adds them to " +
                    "your account.<br><br>" +
                    "Everything they buy is cosmetic.<br><br>" +
                    "Spend them in the Donator Store: talk to the npc called Donator Store, $STORES."
            Block.DEADMAN ->
                "Deadman Points are earned the hard way.<br><br>" +
                    "Every valid player kill gives ${Killstreaks.DEADMAN_POINTS_PER_KILL}, plus 1 for every 5 kills in your " +
                    "current killstreak (up to 10 extra). Cashing in a Deadman emblem also pays its value in Deadman Points.<br><br>" +
                    "Spend them in the Deadman Store: talk to the npc called Deadman Store, $STORES."
            Block.EMBLEM -> emblemText()
            Block.BREACH -> breachText()
        }

    private fun emblemText(): String {
        val values = (1..DeadmanEmblem.MAX_TIER).joinToString(", ") { "$it: ${number(DeadmanEmblem.points(it))}" }
        return "Shows the Deadman emblem you own - in your inventory, your bank or still waiting for you - and its value.<br><br>" +
            "Monsters with 150 or more hitpoints can drop a tier 1 emblem. Every valid player kill with the emblem in your " +
            "inventory raises it one tier, up to tier ${DeadmanEmblem.MAX_TIER}. When another player kills you, the emblem " +
            "always goes to your killer. You own one emblem at a time: a second one keeps the higher tier and cashes in the " +
            "other.<br><br>" +
            "Cash it in with the Deadman Store npc, $STORES, for Deadman Points or an experience lamp. " +
            "Points per tier - $values."
    }

    private fun breachText(): String {
        val plan = DeadmanBreach.schedule
        val days = plan.days.sorted().joinToString(" and ") { it.name.lowercase().replaceFirstChar { c -> c.titlecase(Locale.US) } }
        val hours = plan.hours.joinToString(", ") { String.format(Locale.US, "%02d:00", it) }
        return "Breaches open on $days at $hours (UTC).<br><br>" +
            "Breach monsters keep appearing for about 15 minutes; the ones left alive disappear about 30 minutes later. " +
            "Players with PvP protection cannot attack them. Perdu at the Grand Exchange tells you where the breach is.<br><br>" +
            "Monsters you help kill drop loot and give Breach Points for your damage, to spend with the Breach Trader, " +
            "$STORES. Dealing ${BreachLoot.EMBLEM_DAMAGE} or more damage to a breach monster also gives an Archaic emblem " +
            "(tier 5), once per breach."
    }
}
