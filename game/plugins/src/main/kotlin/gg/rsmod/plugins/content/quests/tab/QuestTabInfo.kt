package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.Killstreaks
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestConfig
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.number
import java.util.Locale

/**
 * The subjects of the quest tab's Info window (N102, VRAGEN 40 = A: points, emblem, breach, chest, protection, skull, brackets,
 * teleport). Every number comes from the code that applies it: the loyalty grants (daily.plugin.kts 500 per daily timer,
 * store.plugin.kts 50 per 30 minutes online), the Deadman Points per kill ([Killstreaks]), the emblem table ([DeadmanEmblem]), the
 * breach schedule and rewards ([DeadmanBreach], [BreachLoot]), the chest rules ([DeadmanChestConfig]), [BeginnerProtection],
 * [PvpSkull], [AnnihilationTier], the attack range ([AreaState]) and the teleport gate ([SevenSecondAction]). The stores and their
 * keepers are [gg.rsmod.plugins.content.mechanics.store.StoreNpcs] (upstairs in the mansion - the Royal Hall at the Grand Exchange).
 */
object QuestTabInfo {
    enum class Subject(val title: String) {
        LOYALTY("Loyalty Points"),
        DONATOR("Donator Points"),
        DEADMAN("Deadman Points"),
        EMBLEM("Archaic Emblem"),
        BREACH("Breaches"),
        CHEST("Deadman's Chest"),
        PROTECTION("PvP protection"),
        SKULL("Skull"),
        BRACKETS("Brackets"),
        TELEPORTS("Teleporting"),
    }

    /** daily.plugin.kts: 500 Loyalty Points each time the 120,000-tick daily timer runs out (20 hours). */
    const val DAILY_LOYALTY = 500
    const val DAILY_HOURS = 20

    /** store.plugin.kts: LOYALTY_PLAYTIME_POINTS per LOYALTY_PLAYTIME_CYCLES (3,000 ticks = 30 minutes) online. */
    const val PLAYTIME_LOYALTY = 50
    const val PLAYTIME_MINUTES = 30

    private const val STORES = "upstairs in the mansion at the Grand Exchange"

    /** Game ticks (0.6 s) as whole seconds / minutes. */
    private fun seconds(ticks: Int) = ticks * 6 / 10

    private fun minutes(ticks: Int) = ticks * 6 / 600

    fun title(subject: Subject): String = subject.title

    fun text(subject: Subject): String =
        when (subject) {
            Subject.LOYALTY ->
                "Loyalty Points reward the time you spend in the world.<br><br>" +
                    "You earn $PLAYTIME_LOYALTY for every $PLAYTIME_MINUTES minutes you are online, $DAILY_LOYALTY from your daily " +
                    "reward (every $DAILY_HOURS hours) and more for completing daily objectives.<br><br>" +
                    "Spend them in the Loyalty Store: talk to Xuan, $STORES."
            Subject.DONATOR ->
                "Donator Points are the thanks for supporting the server with a donation. A member of staff adds them to " +
                    "your account.<br><br>" +
                    "Everything they buy is cosmetic.<br><br>" +
                    "Spend them in the Donator Store: talk to the npc called Donator Store, $STORES."
            Subject.DEADMAN ->
                "Deadman Points are earned the hard way.<br><br>" +
                    "Every valid player kill gives ${Killstreaks.DEADMAN_POINTS_PER_KILL}, plus 1 for every 5 kills in your " +
                    "current killstreak (up to 10 extra). Cashing in a Deadman emblem also pays its value in Deadman Points.<br><br>" +
                    "Spend them in the Deadman Store: talk to the npc called Deadman Store, $STORES."
            Subject.EMBLEM -> emblemText()
            Subject.BREACH -> breachText()
            Subject.CHEST -> chestText()
            Subject.PROTECTION ->
                "New players are protected from other players for their first ${minutes(BeginnerProtection.PROTECTION_CYCLES)} " +
                    "minutes of play. The time only runs while you are actively playing: not while you are logged out or " +
                    "standing idle.<br><br>" +
                    "While protected, other players cannot attack you and you cannot attack them, nor breach monsters.<br><br>" +
                    "Attacking a player yourself asks you to confirm first: confirming ends your protection for good."
            Subject.SKULL ->
                "Attacking a player who did not attack you first gives you a skull for " +
                    "${minutes(PvpSkull.SKULL_DURATION_CYCLES)} minutes. Every new hit you start sets it back to " +
                    "${minutes(PvpSkull.SKULL_DURATION_CYCLES)} minutes. Fighting back never skulls you.<br><br>" +
                    "The skull timer pauses while you are in an instance or stand on the same spot for about a minute.<br><br>" +
                    "While skulled, every teleport takes a ${seconds(SevenSecondAction.DURATION_CYCLES)} second countdown."
            Subject.BRACKETS -> bracketText()
            Subject.TELEPORTS ->
                "Unskulled and out of combat, you teleport at once.<br><br>" +
                    "Skulled, a teleport starts a ${seconds(SevenSecondAction.DURATION_CYCLES)} second countdown; you can keep " +
                    "playing while it runs.<br><br>" +
                    "Hit in the last ${seconds(SevenSecondAction.DURATION_CYCLES)} seconds, you cannot teleport until that time " +
                    "has passed. Inside a boss area hits from the boss never stop a teleport; a hit from a player always does."
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

    private fun chestText(): String =
        "Every ${minutes(DeadmanChestConfig.TIMED_INTERVAL_TICKS)} minutes a Deadman's Chest appears at a random dangerous spot, " +
            "announced in the chat. It stays until the next one appears.<br><br>" +
            "Opening it takes ${seconds(DeadmanChestConfig.OPEN_TICKS_SINGLES)} seconds without being hit " +
            "(${seconds(DeadmanChestConfig.OPEN_TICKS_MULTI)} seconds in multi-combat). You must risk at least " +
            "${number(DeadmanChestConfig.MIN_RISK.toInt())} coins of items and have no PvP protection. Only the first " +
            "player to finish gets the loot.<br><br>" +
            "Killing a monster in a dangerous area can also make a chest appear (1 in ${DeadmanChestConfig.KILL_CHANCE}); " +
            "it lasts ${minutes(DeadmanChestConfig.KILL_CHEST_TICKS)} minutes."

    private fun bracketText(): String {
        val rows = AnnihilationTier.values().joinToString("<br>") {
            "Level ${it.label}: combat ${rate(it.combatXp)}, skills ${rate(it.skillingXp)}, drops ${rate(it.dropRate)}"
        }
        return "Your combat level sets your experience and drop rates:<br>$rows<br><br>" +
            "Prayer and Hitpoints count as combat skills. Your rates never go down again.<br><br>" +
            "Separately, you can only fight players up to ${AreaState.MAX_COMBAT_LEVEL_DIFFERENCE} combat levels above or " +
            "below you."
    }

    private fun rate(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()}x" else "${value}x"
}
