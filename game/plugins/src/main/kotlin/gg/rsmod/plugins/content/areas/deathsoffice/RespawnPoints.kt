package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.areas.wilderness.FeroxRespawn
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones

/**
 * Respawn points (owner decisions 2026-09-26). The Grand Exchange home is the free default; Death sells the others once, for
 * [PRICE] each, and switches between owned points for free. Ferox moved here from Ferox himself (5m at Ferox -> 500k at
 * Death); players who already paid [FeroxRespawn.PAID] keep it.
 *
 * A respawn point is never allowed in a dangerous area: at boot every point looks for a standable tile inside a Deadman safe
 * zone ([GuardedZones], or the Ferox tile that `home_verify` proves) near its OSRS respawn tile ([verify]); a point without
 * one is not offered, and a player whose saved respawn is not a verified tile goes back to the home ([sanitize]).
 */
object RespawnPoints {
    const val PRICE = 500_000

    enum class Point(val label: String, val osrsTile: Tile) {
        EDGEVILLE("Edgeville", Tile(3094, 3469, 0)),
        FEROX("Ferox Enclave", FeroxRespawn.TILE),
        LUMBRIDGE("Lumbridge", Tile(3222, 3218, 0)),
        FALADOR("Falador", Tile(2971, 3340, 0)),

        /** OSRS Camelot respawn is in the castle, outside every Deadman safe zone; the Seers' Village bank is Camelot's one. */
        CAMELOT("Camelot", Tile(2725, 3493, 0)),
        ARDOUGNE("Ardougne", Tile(2661, 3305, 0)),
    }

    /** Bought points, one bit per [Point.ordinal]. */
    val OWNED = AttributeKey<Int>(persistenceKey = "respawn_points_owned")

    /** The verified respawn tile of every point that has one (filled at boot by [verify]). */
    private val tiles = HashMap<Point, Tile>()

    fun tile(point: Point): Tile? = tiles[point]

    fun available(point: Point): Boolean = tiles.containsKey(point)

    fun owns(
        player: Player,
        point: Point,
    ): Boolean = ((player.attr[OWNED] ?: 0) shr point.ordinal) and 1 == 1 || (point == Point.FEROX && player.attr[FeroxRespawn.PAID] == true)

    fun active(player: Player): Point? {
        val hash = player.attr[RESPAWN_TILE_ATTR] ?: return null
        return tiles.entries.firstOrNull { it.value.as30BitInteger == hash }?.key
    }

    fun activate(
        player: Player,
        point: Point,
    ) {
        val tile = tiles[point] ?: return
        player.attr[RESPAWN_TILE_ATTR] = tile.as30BitInteger
    }

    fun useHome(player: Player) {
        player.attr.remove(RESPAWN_TILE_ATTR)
    }

    /** A saved respawn that is not a verified point (moved, dangerous, removed) falls back to the Grand Exchange home. */
    fun sanitize(player: Player) {
        if (player.attr[RESPAWN_TILE_ATTR] != null && active(player) == null) useHome(player)
    }

    /**
     * Picks each point's tile: the OSRS tile, or the nearest tile within [SEARCH_RADIUS] that is standable and safe. Returns
     * one report line per point.
     */
    fun verify(world: World): List<String> {
        tiles.clear()
        fun standable(tile: Tile): Boolean = !Direction.NESW.all { world.collision.isBlocked(tile, it, projectile = false) }
        fun safe(tile: Tile): Boolean = GuardedZones.contains(tile) || tile == FeroxRespawn.TILE
        val lines = ArrayList<String>()
        for (point in Point.values()) {
            val base = point.osrsTile
            val found =
                (0..SEARCH_RADIUS).asSequence().flatMap { r ->
                    (-r..r).asSequence().flatMap { dx -> (-r..r).asSequence().map { dz -> base.transform(dx, dz) } }
                        .filter { maxOf(Math.abs(it.x - base.x), Math.abs(it.z - base.z)) == r }
                }.firstOrNull { safe(it) && standable(it) }
            if (found != null) {
                tiles[point] = found
                lines += "respawn_verify: ${point.label} -> $found (safe, standable)"
            } else {
                lines += "respawn_verify: ${point.label} NOT OFFERED - no safe, standable tile within $SEARCH_RADIUS of $base (Deadman dangerous area)"
            }
        }
        return lines
    }

    private const val SEARCH_RADIUS = 4

    /** Death: "Can I change where I respawn?" */
    suspend fun QueueTask.respawnDialogue() {
        chatPlayer("Can I change where I respawn?", wrap = true)
        val current = active(player)?.label ?: "the Grand Exchange"
        chatNpc("You currently return to the living at $current. For ${String.format(java.util.Locale.US, "%,d", PRICE)} coins, once, I can send you back somewhere else - and switch between the places you've paid for whenever you like.", wrap = true)
        val offered = Point.values().filter { available(it) }
        val labels =
            listOf("The Grand Exchange (free)") +
                offered.map { if (owns(player, it)) "${it.label} (owned)" else "${it.label} (${String.format(java.util.Locale.US, "%,d", PRICE)})" }
        val choice = pick(labels) ?: return
        if (choice == 0) {
            useHome(player)
            chatNpc("Very well. You will return at the Grand Exchange.", wrap = true)
            return
        }
        val point = offered[choice - 1]
        if (!owns(player, point)) {
            if (options("Pay ${String.format(java.util.Locale.US, "%,d", PRICE)} coins.", "No, thanks.", title = "Respawn at ${point.label}?") != 1) return
            if (!player.inventory.remove(Items.COINS_995, PRICE, assureFullRemoval = true).hasSucceeded()) {
                chatNpc("You don't have ${String.format(java.util.Locale.US, "%,d", PRICE)} coins with you.", wrap = true)
                return
            }
            player.attr[OWNED] = (player.attr[OWNED] ?: 0) or (1 shl point.ordinal)
        }
        activate(player, point)
        chatNpc("It is done. You will return to the living at ${point.label}.", wrap = true)
    }

    /** A paged option menu (the chatbox shows at most five lines). Returns the chosen index, or null. */
    private suspend fun QueueTask.pick(labels: List<String>): Int? {
        var page = 0
        while (true) {
            val start = page * 3
            val shown = labels.subList(start, minOf(labels.size, start + 3))
            val more = start + 3 < labels.size
            val entries = shown + (if (more) listOf("More...") else emptyList()) + "Never mind."
            val choice = options(*entries.toTypedArray(), title = "Where should you respawn?")
            when {
                choice in 1..shown.size -> return start + choice - 1
                more && choice == shown.size + 1 -> page++
                else -> return null
            }
        }
    }
}
