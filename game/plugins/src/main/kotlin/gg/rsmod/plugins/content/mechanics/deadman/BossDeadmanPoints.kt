package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue

/**
 * Deadman Points per boss kill (DMM-ARMAGEDDON D4, owner 20-11-2026).
 *
 * Source: OSRS Wiki "Deadman: Armageddon/Points", table "Points per kill" (column 1-75): "Each boss is assigned a fixed number of
 * points ... The very first kill of any boss will award 10 times the regular number of points". Instead of the wiki's own 76+
 * column the owner's rule applies: "Once a player obtains 100 killcount at any boss that awards Points, all future killcount will
 * reward 50% as many points" (kill 101 onward, rounded down). The owner approved the table and, for our bosses that are not in
 * the OSRS table, Claude's proposal (Kalphite King 25, Queen Black Dragon 20, Glacor / Wildywyrm / Revenant maledictus 10,
 * Tormented demon / Bork / Calvar'ion 5; the Mage Arena II guardians nothing).
 *
 * Breach monsters never pay here (they pay per damage, [gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints]).
 */
object BossDeadmanPoints {
    const val FIRST_KILL_MULTIPLIER = 10

    /** From this kill count on (exclusive) a kill pays half. */
    const val FULL_POINTS_KILLS = 100

    /** Lower-case boss name (as the Drop Viewer / Collection Log names it) to points per kill. */
    val PER_KILL: Map<String, Int> =
        mapOf(
            // OSRS Wiki Deadman: Armageddon/Points.
            "tztok-jad" to 100,
            "corporeal beast" to 35,
            "kree'arra" to 23,
            "general graardor" to 20,
            "commander zilyana" to 20,
            "kalphite queen" to 20,
            "k'ril tsutsaroth" to 17,
            "dagannoth rex" to 15,
            "dagannoth prime" to 15,
            "dagannoth supreme" to 15,
            "giant mole" to 12,
            "chaos elemental" to 10,
            "artio" to 10,
            "spindel" to 10,
            "the mimic" to 10,
            "callisto" to 5,
            "venenatis" to 5,
            "vet'ion" to 5,
            "scorpia" to 5,
            "king black dragon" to 5,
            "nex" to 5,
            "chaos fanatic" to 4,
            "crazy archaeologist" to 4,
            // Our bosses outside the OSRS table (owner-approved proposal 20-11).
            "kalphite king" to 25,
            "queen black dragon" to 20,
            "glacor" to 10,
            "wildywyrm" to 10,
            "revenant maledictus" to 10,
            "tormented demon" to 5,
            "bork" to 5,
            "calvar'ion" to 5,
        )

    /** Barrows: per chest opened. */
    const val BARROWS = 10

    /** Chambers of Xeric: per completion. */
    const val COX = 120

    /** Tombs of Amascut per completion by raid level: Entry (< 150) 100, Normal (150-299) 120, Expert (300+) 150. */
    fun toa(raidLevel: Int): Int =
        when {
            raidLevel >= 300 -> 150
            raidLevel >= 150 -> 120
            else -> 100
        }

    /** Points per kill for a boss by its display name ("The Mimic (elite)" counts as "the mimic"); 0 = none. */
    fun perKill(name: String): Int {
        val key = name.lowercase().trim()
        PER_KILL[key]?.let { return it }
        return if (key.startsWith("the mimic")) PER_KILL.getValue("the mimic") else 0
    }

    /** The points for kill number [killCount] (already counted) of a boss worth [perKill]. */
    fun pointsFor(
        perKill: Int,
        killCount: Int,
    ): Int =
        when {
            perKill <= 0 || killCount <= 0 -> 0
            killCount == 1 -> perKill * FIRST_KILL_MULTIPLIER
            killCount > FULL_POINTS_KILLS -> perKill / 2
            else -> perKill
        }

    /** Pays [player] for kill number [killCount] of [bossName] worth [perKill]; returns the points given. */
    fun award(
        player: Player,
        bossName: String,
        perKill: Int,
        killCount: Int,
    ): Int {
        val points = pointsFor(perKill, killCount)
        if (points <= 0) return 0
        val key = StoreCatalogue.Currency.DEADMAN.attr
        val before = PointBalance.of(player.attr, key)
        val given = PointBalance.add(player.attr, key, points) - before
        if (given <= 0) {
            player.message("Your Deadman Points are at the maximum.")
            return 0
        }
        val why =
            when {
                killCount == 1 -> ", first kill bonus"
                killCount > FULL_POINTS_KILLS -> ", half points after $FULL_POINTS_KILLS kills"
                else -> ""
            }
        player.message("You earn ${"%,d".format(given)} Deadman Points ($bossName$why).")
        return given
    }

    /** A boss npc kill, by its Drop Viewer source name and kill count. */
    fun onBossKill(
        player: Player,
        bossName: String,
        killCount: Int,
    ): Int = award(player, bossName, perKill(bossName), killCount)
}
