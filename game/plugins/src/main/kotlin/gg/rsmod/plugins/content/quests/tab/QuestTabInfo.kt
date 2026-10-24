package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.Killstreaks
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestConfig
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.quests.tab.DeadmanTabValues.number
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The Deadman Info window's pages (N274, owner design 2026-10-24 GOEDGEKEURD `screens\team\INFO\info-v1.png`; subjects N102 /
 * VRAGEN 40 = A). Per subject: a tag line, fact rows (label + value) or a table, a few lines of text and what is live for the
 * player at the bottom (with a map tile when there is a place to show). Every number comes from the code that applies it: the
 * loyalty grants (daily.plugin.kts 500 per daily timer, store.plugin.kts 50 per 30 minutes online), the Deadman Points per kill
 * ([Killstreaks]) and per task ([DeadmanTasks.DP]), the emblem table ([DeadmanEmblem]), the breach schedule and rewards
 * ([DeadmanBreach], [BreachLoot]), the chest rules ([DeadmanChestConfig]), [BeginnerProtection], [PvpSkull], [AnnihilationTier], the
 * attack range ([AreaState]) and the teleport gate ([SevenSecondAction]). The stores and their keepers are
 * [gg.rsmod.plugins.content.mechanics.store.StoreNpcs] (upstairs in the mansion - the Royal Hall at the Grand Exchange).
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

    /** One fact row: grey label left, value right (colour tags allowed). */
    class Fact(val label: String, val value: String)

    /**
     * One subject's page. [table] replaces the fact rows (first row = the heading, [highlight] = the player's own row); [bar] is the
     * live line at the bottom and [map] the tile "Show on map" opens (null: no button).
     */
    class Page(
        val tag: String,
        val facts: List<Fact> = emptyList(),
        val table: List<List<String>>? = null,
        val highlight: Int = -1,
        val text: String,
        val bar: String,
        val map: Tile? = null,
    )

    /** daily.plugin.kts: 500 Loyalty Points each time the 120,000-tick daily timer runs out (20 hours). */
    const val DAILY_LOYALTY = 500
    const val DAILY_HOURS = 20

    /** store.plugin.kts: LOYALTY_PLAYTIME_POINTS per LOYALTY_PLAYTIME_CYCLES (3,000 ticks = 30 minutes) online. */
    const val PLAYTIME_LOYALTY = 50
    const val PLAYTIME_MINUTES = 30

    private const val STORES = "upstairs in the mansion at the Grand Exchange"
    private const val GREEN = "56e156"
    private const val RED = "ff5252"
    private const val GOLD = "ffcf4a"
    private const val WHITE = "ffffff"

    private fun col(colour: String, text: String) = "<col=$colour>$text</col>"

    /** Width of the live line beside the "Show on map" button (window 1175: bar x 167, button x 393, 12 px of margins). */
    private const val BAR_WITH_MAP = 214

    /** Game ticks (0.6 s) as whole seconds / minutes. */
    private fun seconds(ticks: Int) = ticks * 6 / 10

    private fun minutes(ticks: Int) = ticks * 6 / 600

    fun title(subject: Subject): String = subject.title

    private fun balance(player: Player, currency: StoreCatalogue.Currency, name: String) =
        "Your balance: ${col(WHITE, number(DeadmanTabValues.balance(player, currency)))} $name"

    fun page(player: Player, subject: Subject): Page =
        when (subject) {
            Subject.LOYALTY ->
                Page(
                    "Rewards for the time you spend in the world",
                    listOf(
                        Fact("Playing", col(GOLD, "$PLAYTIME_LOYALTY") + " every $PLAYTIME_MINUTES minutes online"),
                        Fact("Daily reward", col(GOLD, "$DAILY_LOYALTY") + " every $DAILY_HOURS hours"),
                        Fact("Daily objectives", "extra points"),
                        Fact("Spend them", "Loyalty Store - talk to Xuan"),
                    ),
                    text = "Xuan keeps the Loyalty Store $STORES.",
                    bar = balance(player, StoreCatalogue.Currency.LOYALTY, "Loyalty Points"),
                )
            Subject.DONATOR ->
                Page(
                    "The thanks for supporting the server",
                    listOf(
                        Fact("You get them", "with a donation"),
                        Fact("Added by", "a member of staff"),
                        Fact("They buy", col(GOLD, "cosmetics only")),
                        Fact("Spend them", "Donator Store"),
                    ),
                    text = "Talk to the npc called Donator Store, $STORES.",
                    bar = balance(player, StoreCatalogue.Currency.DONATOR, "Donator Points"),
                )
            Subject.DEADMAN ->
                Page(
                    "Earned the hard way",
                    listOf(
                        Fact("Valid player kill", col(GOLD, "${Killstreaks.DEADMAN_POINTS_PER_KILL}")),
                        Fact("Killstreak", "+1 per 5 kills (up to +10)"),
                        Fact("Deadman Tasks", DeadmanTasks.Tier.values().joinToString(" / ") { "${DeadmanTasks.DP.getValue(it)}" } + " per task"),
                        Fact("Deadman emblem", "its value when cashed in"),
                        Fact("Spend them", "Deadman Store"),
                    ),
                    text = "Talk to the npc called Deadman Store, $STORES.",
                    bar = balance(player, StoreCatalogue.Currency.DEADMAN, "Deadman Points"),
                )
            Subject.EMBLEM -> emblemPage(player)
            Subject.BREACH -> breachPage(player)
            Subject.CHEST -> chestPage(player)
            Subject.PROTECTION -> protectionPage(player)
            Subject.SKULL -> skullPage(player)
            Subject.BRACKETS -> bracketPage(player)
            Subject.TELEPORTS -> teleportPage(player)
        }

    private fun emblemPage(player: Player): Page {
        val values = (1..DeadmanEmblem.MAX_TIER).joinToString(", ") { "$it: ${number(DeadmanEmblem.points(it))}" }
        val tier = runCatching { DeadmanEmblem.ownedTier(player) }.getOrDefault(0)
        return Page(
            "Grows with every kill you make",
            listOf(
                Fact("Drops from", "monsters with 150+ hitpoints (tier 1)"),
                Fact("Every valid kill", "+1 tier, up to tier ${DeadmanEmblem.MAX_TIER}"),
                Fact("Killed by a player", col(RED, "your killer gets it")),
                Fact("You can own", "one at a time"),
                Fact("Cash it in", "Deadman Store npc"),
            ),
            text = "Carry it in your inventory to raise it. A second emblem keeps the higher tier and cashes in the other. " +
                "Cashed in for Deadman Points or an experience lamp - points per tier $values.",
            bar = if (tier > 0) "Yours: ${col(WHITE, "tier $tier")} - worth ${col(WHITE, number(DeadmanEmblem.points(tier)))} points" else "You have no emblem",
        )
    }

    private fun breachPage(player: Player): Page {
        val plan = DeadmanBreach.schedule
        val days = plan.days.sorted().joinToString(" and ") { it.name.lowercase().replaceFirstChar { c -> c.titlecase(Locale.US) } }
        val hours = plan.hours.joinToString(", ") { String.format(Locale.US, "%02d", it) }
        val active = DeadmanBreach.active
        val bar: String
        val map: Tile?
        if (active != null) {
            val left = active.openedAtCycle + DeadmanBreach.SPAWN_WINDOW_TICKS - player.world.currentCycle
            bar = "${col(RED, "Active")}: ${N108Ui.fitP11(player.world, active.sites.joinToString(", ") { it.name }, 150)}" +
                if (left > 0) " - ${col(WHITE, QuestTab.clock(left))}" else ""
            map = active.sites.firstOrNull()?.spawners?.firstOrNull()
        } else {
            val seconds = Duration.between(ZonedDateTime.now(ZoneOffset.UTC), DeadmanBreach.nextOpening).seconds
            bar = "Next breach in ${col(WHITE, QuestTab.clock((seconds * 10 / 6).toInt().coerceAtLeast(0)))}"
            map = null
        }
        return Page(
            "Monsters break through on $days",
            listOf(
                Fact("Opens at (UTC)", "$hours:00"),
                Fact("Monsters appear", "for about 15 minutes"),
                Fact("Left alive", "gone about 30 minutes later"),
                Fact("${BreachLoot.EMBLEM_DAMAGE}+ damage", col(GOLD, "Archaic emblem (tier 5)") + ", once"),
                Fact("PvP protection", col(RED, "cannot attack them")),
            ),
            text = "Monsters you help kill drop loot and give Breach Points for your damage - spend them with the Breach Trader, " +
                "$STORES. Perdu at the Grand Exchange tells you where the breach is.",
            bar = bar,
            map = map,
        )
    }

    private fun chestPage(player: Player): Page {
        val chest = DeadmanChestWorld.activeTimed()
        val next = DeadmanChestWorld.ticksUntilTimed
        val bar =
            when {
                // N278: the place in full (every chest spot fits beside "Show on map"; it stays until the next one appears).
                chest != null -> "Now: ${col(WHITE, QuestTab.placeLabel(player.world, chest.name.replaceFirstChar { it.titlecase() }, BAR_WITH_MAP - 26))}"
                next >= 0 -> "Next chest in ${col(WHITE, QuestTab.clock(next))}"
                else -> "No chest right now"
            }
        return Page(
            "Loot for whoever dares to open it",
            listOf(
                Fact("Appears", "every ${minutes(DeadmanChestConfig.TIMED_INTERVAL_TICKS)} minutes"),
                Fact("Where", "a random dangerous spot"),
                Fact("Opening takes", col(GOLD, "${seconds(DeadmanChestConfig.OPEN_TICKS_SINGLES)} s (${seconds(DeadmanChestConfig.OPEN_TICKS_MULTI)} s in multi)")),
                Fact("You must risk", col(GOLD, "${number(DeadmanChestConfig.MIN_RISK.toInt())} coins")),
                Fact("PvP protection", col(RED, "not allowed")),
                Fact("Loot goes to", "the first to finish"),
                Fact("Bonus chest", "1 in ${DeadmanChestConfig.KILL_CHANCE} monster kills"),
            ),
            text = "Any fight (a hit taken or dealt) stops the opening: click again. A bonus chest stays " +
                "${minutes(DeadmanChestConfig.KILL_CHEST_TICKS)} minutes; a timed one stays until the next one appears.",
            bar = bar,
            map = chest?.tile,
        )
    }

    private fun protectionPage(player: Player): Page {
        val protectedNow = runCatching { BeginnerProtection.isProtected(player) }.getOrDefault(false)
        val left = if (protectedNow) runCatching { player.timers[NEW_PLAYER_PROTECTION_TIMER] }.getOrDefault(0) else 0
        return Page(
            "A safe start for new players",
            listOf(
                Fact("Lasts", col(GOLD, "${minutes(BeginnerProtection.PROTECTION_CYCLES)} minutes") + " of play"),
                Fact("Counts down", "only while you actively play"),
                Fact("Other players", col(GREEN, "cannot attack you")),
                Fact("You", "cannot attack players or breach monsters"),
                Fact("Attacking a player", col(RED, "ends it for good") + " (you confirm first)"),
            ),
            text = "The time does not run while you are logged out or standing idle.",
            bar = if (protectedNow) "Your protection: ${col(GREEN, QuestTab.clock(left))} left" else "You have no PvP protection",
        )
    }

    private fun skullPage(player: Player): Page {
        val skulled = PvpSkull.hasSkullTimer(player)
        val left = if (skulled) runCatching { player.timers[SKULL_ICON_DURATION_TIMER] }.getOrDefault(0) else 0
        val skull = minutes(PvpSkull.SKULL_DURATION_CYCLES)
        val gate = seconds(SevenSecondAction.DURATION_CYCLES)
        return Page(
            "Attack first and you carry the skull",
            listOf(
                Fact("You get it", "attacking someone who did not hit you"),
                Fact("Lasts", col(GOLD, "$skull minutes")),
                Fact("Every new attack", "back to $skull minutes"),
                Fact("Fighting back", col(GREEN, "never skulls you")),
                Fact("Paused", "in an instance or AFK ~1 minute"),
                Fact("Teleport while skulled", col(RED, "$gate s countdown")),
            ),
            text = "You can keep playing while the countdown runs. Hit in the last $gate seconds? Then you cannot teleport until that " +
                "time has passed.",
            bar = if (skulled) "Your skull: ${col(RED, QuestTab.clock(left))} left" else "You are not skulled",
        )
    }

    private fun bracketPage(player: Player): Page {
        val mine = AnnihilationTier.of(player)
        val rows = listOf(listOf("Level", "Combat XP", "Skills XP", "Drops")) +
            AnnihilationTier.values().map { listOf(if (it == mine) "${it.label}  (you)" else it.label, rate(it.combatXp), rate(it.skillingXp), rate(it.dropRate)) }
        val range = AreaState.bracketOf(player.combatLevel)
        return Page(
            "Your combat level sets your rates",
            table = rows,
            highlight = 1 + AnnihilationTier.values().indexOf(mine),
            text = "Prayer and Hitpoints count as combat skills. Your rates never go down again.<br><br>" +
                "You can only fight players up to ${AreaState.MAX_COMBAT_LEVEL_DIFFERENCE} levels above or below you.",
            bar = "You: combat ${col(WHITE, "${player.combatLevel}")} - you fight ${col(WHITE, "${range.first} - ${range.last}")}",
        )
    }

    private fun teleportPage(player: Player): Page {
        val gate = seconds(SevenSecondAction.DURATION_CYCLES)
        val skulled = PvpSkull.hasSkullTimer(player)
        return Page(
            "When you can get away",
            listOf(
                Fact("Unskulled, out of combat", col(GREEN, "at once")),
                Fact("Skulled", col(RED, "$gate s countdown")),
                Fact("Hit in the last $gate s", "wait until that time passes"),
                Fact("Boss hits in a boss area", col(GREEN, "never stop it")),
                Fact("A player's hit", col(RED, "always stops it")),
            ),
            text = "The countdown lets you keep playing while it runs.",
            bar = if (skulled) "You are skulled: teleports take ${col(RED, "$gate s")}" else "You are not skulled",
        )
    }

    private fun rate(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()}x" else "${value}x"
}
