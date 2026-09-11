package gg.rsmod.plugins.content.mechanics.clan

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Full (customizable-rules) Clan Wars, as distinct from the already-ported Clan Wars
 * Free-For-All (`areas/wilderness/clan_wars_ffa.plugin.kts`). Source: Novite
 * `minigames/clanwars/{ClanWars,ClanWarsTimer}.java`, `AreaType.java`.
 *
 * Real donor architecture (`RegionBuilder.copyAllPlanesMap`/`findEmptyChunkBound`/`destroyMap`)
 * dynamically clones a fresh copy of one of 5 template arenas per war, so many wars can run at
 * once. This engine has no instance/zone manager (same documented limitation as this project's
 * Dungeoneering/Construction entries) - ported here as a deliberate scoped-down substitute: one
 * shared war at a time, played directly at the real static template coordinates instead of a
 * runtime copy. Not an oversight; see the plugin file's header comment.
 *
 * [Rules] config ids (Novite: NO_FOOD=5288, NO_POTIONS=5289, NO_PRAYER=5290, NO_MELEE=5284,
 * NO_RANGE=5285, NO_FAMILIARS=5287, ITEMS_LOST=5283, magic count=5286, request-state=5291-5293)
 * were checked against this project's real 667 cache via `runConfigDefProbeTool ... varbit
 * 5282..5293` - **none of them exist** in this cache (only 5279/5280/5281/5294/5295 do, and
 * 5280/5281 are independently confirmed real by bit-width: varp 1305 bits 0-3 and 4-7, exactly
 * matching Novite's own 0-15 victory-type and 0-12 time-limit value ranges). Per this project's
 * "never guess cache mappings" rule, the 8 rule toggles are therefore configured via a chat menu
 * and tracked in server-side state only, not via live varbit-driven interface checkboxes -
 * interfaces 265 (overlay)/790 (results)/791 (challenge screen) from Novite's own numbering are
 * NOT used here either, for the same reason (no probe tool exists for interface defs, and
 * Novite's own cache revision is not established to number interfaces the same as this project's
 * real 667 cache the way item/npc/object ids usually do).
 */
enum class ClanWarRule(val displayName: String) {
    NO_FOOD("No food"),
    NO_POTIONS("No potions"),
    NO_PRAYER("No prayer"),
    NO_MELEE("No melee"),
    NO_RANGE("No ranged"),
    NO_MAGIC("No magic"),
    NO_FAMILIARS("No familiars"),
    ITEMS_LOST("Items lost on death"),
}

/** Real values from Novite's `ClanWars.sendVictoryConfiguration` - kill target, or -1 knockout, or -2 most-kills. */
val CLAN_WAR_VICTORY_TYPES = intArrayOf(-1, -2, 25, 50, 100, 200, 400, 750, 1_000, 2_500, 5_000, 10_000)

/** Real values (game ticks) from Novite's `ClanWars.sendTimeConfiguration` - -1 is unlimited. */
val CLAN_WAR_TIME_LIMITS = intArrayOf(-1, 500, 1_000, 3_000, 6_000, 9_000, 12_000, 15_000, 18_000, 24_000, 30_000, 36_000, 48_000)

/** Real template arena data, `Novite AreaType.java` (south-west tile, first/second team spawn offsets). Not instanced - see class doc. */
enum class ClanWarArena(val southWest: Tile, val firstSpawnOffset: Pair<Int, Int>, val secondSpawnOffset: Pair<Int, Int>) {
    CLASSIC_AREA(Tile(2752, 5888, 0), 35 to 10, -36 to -9),
    PLATEAU(Tile(2831, 5888, 0), 59 to 12, -38 to -10),
    FORSAKEN_QUARRY(Tile(2880, 5504, 0), 11 to 11, -10 to -10),
    BLASTED_FOREST(Tile(2880, 5632, 0), 12 to 9, -12 to -9),
    TURRETS(Tile(2689, 5505, 0), 42 to 7, -40 to -5),
    ;

    fun firstSpawn(): Tile = southWest.transform(firstSpawnOffset.first, firstSpawnOffset.second, 0)

    fun secondSpawn(): Tile = southWest.transform(secondSpawnOffset.first, secondSpawnOffset.second, 0)
}

val CLAN_WAR_ACCEPTED_TERMS = AttributeKey<Boolean>()
val CLAN_WAR_OPPONENT = AttributeKey<String>()

/**
 * One active full Clan Wars match. Single shared instance (see class doc) - `ClanWarsMatch.active`
 * is null when no war is running or being negotiated.
 */
class ClanWarsMatch(
    val firstClan: String,
    val secondClan: String,
    val arena: ClanWarArena,
) {
    val rules: MutableSet<ClanWarRule> = ConcurrentHashMap.newKeySet()
    var victoryType: Int = -1
    var timeLimit: Int = -1

    var started = false
    var preWarTicks = 200
    var ticksLeft = 0

    val firstPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    val secondPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    var firstKills = 0
    var secondKills = 0

    fun teamOf(player: Player): Int = if (firstPlayers.contains(player)) 1 else if (secondPlayers.contains(player)) 2 else 0

    fun isKnockout() = victoryType == -1

    fun isMostKills() = victoryType == -2

    companion object {
        @Volatile var active: ClanWarsMatch? = null
    }
}
