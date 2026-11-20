package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
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
        DONATOR("78 Tokens"),
        DEADMAN("Deadman Points"),
        EMBLEM("Deadman Emblem"),
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
                    bar = balance(player, StoreCatalogue.Currency.DONATOR, "78 Tokens"),
                )
            Subject.DEADMAN ->
                Page(
                    "Earned the hard way",
                    listOf(
                        Fact("Valid player kill", col(GOLD, "${Killstreaks.DEADMAN_POINTS_PER_KILL}")),
                        Fact("Killstreak", "+1 per 5 kills (up to +10)"),
                        // DMM-ARMAGEDDON D3 + D4 (owner 20-11-2026).
                        Fact("Boss kill", "4-150, first kill 10x, half after 100"),
                        Fact("Breach damage", "1 per damage (max ${number(gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints.DP_CAP)})"),
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

    /**
     * N285 / N288 (the Deadman tab's emblem row opens this page): first the player's own emblem - its tier, what it is worth now,
     * what the next tier is worth and where it is (inventory, bank, gravestone or Death's Office, or waiting to be delivered) -
     * then the rules. Every value from [DeadmanEmblem].
     */
    private fun emblemPage(player: Player): Page {
        val values = (1..DeadmanEmblem.MAX_TIER).joinToString(", ") { "$it: ${number(DeadmanEmblem.points(it))}" }
        val tier = runCatching { DeadmanEmblem.ownedTier(player) }.getOrDefault(0)
        val mine =
            if (tier > 0) {
                listOf(
                    Fact("Your emblem", col(WHITE, "tier $tier of ${DeadmanEmblem.MAX_TIER}")),
                    Fact("Worth now", col(GOLD, "${number(DeadmanEmblem.points(tier))} Deadman Points")),
                    Fact("Next tier", if (tier < DeadmanEmblem.MAX_TIER) col(GOLD, "${number(DeadmanEmblem.points(tier + 1))} Deadman Points") else col(WHITE, "highest tier")),
                    Fact("Where", col(WHITE, emblemPlace(player, tier))),
                )
            } else {
                listOf(Fact("Your emblem", "none yet"))
            }
        return Page(
            "Grows with every kill you make",
            mine +
                listOf(
                    Fact("Drops from", "monsters with 150+ hitpoints (tier 1)"),
                    Fact("Every valid kill", "+1 tier, up to tier ${DeadmanEmblem.MAX_TIER}"),
                    Fact("Killed by a player", col(RED, "your killer gets it")),
                ),
            text = "Carry it in your inventory to raise it; you can own one at a time - a second keeps the higher tier and cashes in " +
                "the other. Cash it in at the Deadman Store npc for Deadman Points or an experience lamp - points per tier $values.",
            bar = if (tier > 0) "Yours: ${col(WHITE, "tier $tier")} - worth ${col(WHITE, number(DeadmanEmblem.points(tier)))} points" else "You have no emblem",
        )
    }

    /** Where the player's emblem of [tier] is: inventory, bank, gravestone / Death's Office, or waiting for delivery. */
    fun emblemPlace(player: Player, tier: Int): String {
        val held = runCatching { DeadmanEmblem.holdings(player) }.getOrDefault(emptyList()).filter { it.tier == tier }
        val h = held.firstOrNull { !it.inBank } ?: held.firstOrNull()
        return when {
            h == null -> "waiting to be delivered"
            h.container === player.inventory -> "in your inventory"
            h.container === player.bank -> "in your bank"
            h.container === player.gravestone -> "in your gravestone"
            else -> "at Death's Office"
        }
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
            listOfNotNull(
                Fact("Opens at (UTC)", "$hours:00"),
                Fact("Monsters appear", "for about 15 minutes"),
                Fact("Left alive", "gone about 30 minutes later"),
                // DMM-ARMAGEDDON D3 (owner 20-11-2026): MvP + the next 15 by damage get loot and Deadman Points; only the MvP the unique.
                Fact("Top ${BreachLoot.ELIGIBLE} damage", col(GOLD, "loot + Deadman Points")),
                Fact("PvP protection", col(RED, "cannot attack them")),
            ),
            text = "The player with the most damage (MvP) can also get a unique drop - the only way to get corrupted weapons. " +
                "Below the top ${BreachLoot.ELIGIBLE} you get nothing. Perdu at the Grand Exchange tells you where the breach is.",
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
                Fact("Attacking a player", col(RED, "ends it for good")),
            ),
            // N319: "(you confirm first)" left the fact row, where it touched its label; it is said here.
            text = "The time does not run while you are logged out or standing idle. Before your first attack on a player you are asked to confirm.",
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
            text = "Being hit or doing anything else (eating, attacking back) cancels the countdown - start it again.",
            bar = if (skulled) "Your skull: ${col(RED, QuestTab.clock(left))} left" else "You are not skulled",
        )
    }

    private fun bracketPage(player: Player): Page {
        val mine = AnnihilationTier.of(player)
        val rows = listOf(listOf("Level", "Combat XP", "Skills XP", "Drops")) +
            AnnihilationTier.values().map { listOf(if (it == mine) "${it.label}  (you)" else it.label, rate(it.combatXp), rate(it.skillingXp), rate(it.dropRate)) }
        // DMM-ARMAGEDDON D8: the player's bracket never goes down, so it is the reached one (not the bare combat level).
        val range = gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.of(player).levels
        return Page(
            "Your combat level sets your rates",
            table = rows,
            highlight = 1 + AnnihilationTier.values().indexOf(mine),
            text = "Prayer and Hitpoints count as combat skills. Your rates never go down again. Quests, lamps and other " +
                "rewards give their exact XP.<br><br>" +
                "You only fight players of your own combat bracket (see below).",
            bar = "You: combat ${col(WHITE, "${player.combatLevel}")} - you fight ${col(WHITE, "${range.first} - ${range.last}")}",
        )
    }

    private fun teleportPage(player: Player): Page {
        val gate = seconds(SevenSecondAction.DURATION_CYCLES)
        // DMM-ARMAGEDDON D1: the windows count from the attack, the wiki wording one tick less (5.4 s player, 2.4 s monster).
        val pvpWait = String.format(Locale.ROOT, "%.1f", (DeadmanTimerGate.PVP_HIT_TELEPORT_BLOCK_CYCLES - 1) * 0.6)
        val npcWait = String.format(Locale.ROOT, "%.1f", (DeadmanTimerGate.NPC_HIT_TELEPORT_BLOCK_CYCLES - 1) * 0.6)
        val skulled = PvpSkull.hasSkullTimer(player)
        return Page(
            "When you can get away",
            listOf(
                Fact("Unskulled, out of combat", col(GREEN, "at once")),
                Fact("Skulled", col(RED, "$gate s countdown")),
                Fact("Hit by a player", "wait $pvpWait s"),
                Fact("Hit by a monster or boss", "wait $npcWait s"),
                Fact("Monster hits in minigames", col(GREEN, "never stop it")),
            ),
            text = "Being hit or doing anything else cancels the countdown. Logging out works the same way.",
            bar = if (skulled) "You are skulled: teleports take ${col(RED, "$gate s")}" else "You are not skulled",
        )
    }

    private fun rate(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()}x" else "${value}x"
}
