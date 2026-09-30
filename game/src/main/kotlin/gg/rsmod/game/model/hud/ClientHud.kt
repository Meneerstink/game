package gg.rsmod.game.model.hud

import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player

/**
 * Server -> client state for the client's RuneLite-style information overlays (owner 2026-09-29: Ground Items,
 * Timers & Buffs, Boosts Information, Opponent Information). The client only draws; every number it shows comes from
 * here, so the overlays follow this server's own mechanics.
 *
 * Carried on the existing RSPS side channel: a console-type game message (type 99) whose text starts with [PREFIX]. The
 * client strips it before the console ever sees it (ServerConnectionReader, next to `__RSPS_DEV__:`), so nothing shows in
 * chat and no packet id had to be added. One MESSAGE_GAME is a var-byte packet, so each payload stays under [MAX_PAYLOAD].
 *
 * Payloads (fields separated by '|'):
 *  - `cfg|<key>=<value>;...`                  overlay settings (RuneLite's config items, the plugin's ClientHudConfig)
 *  - `hl|clear`, `hl|add|<a>,<b>`, `hide|...` the highlighted / hidden item lists
 *  - `open[|<plugin>]`, `close`               open / close the client's 78 Plugins sidebar (at a plugin's settings)
 *  - `gdelay|<ticks>`                         a new drop's despawn delay (Ground Items' pie timer)
 *  - `boost|<combatDecay>|<restore>|<nonCombatDecay>`   ticks to the next step of each restoration cycle (always sent; the
 *                                             client's "next buff / debuff change" settings decide what shows); -1 = not running
 *  - `opp|n|<npcIndex>|<ratio>|<cur>|<max>`   the player's current opponent; ratio 0..255 like the hit mask
 *  - `opp|p|<playerIndex>|<ratio>`            (a player opponent: ratio only, as in the hit mask)
 *  - `opp|none`
 *  - `tmr|<vengActive>|<vengCd>|<teleblock>|<frozen>|<dot>|<dotDamage>|<dotNext>|<antiPoison>|<antiVenom>|<burns>|<burnNext>|0`
 *                                             remaining ticks, 0 = not active; dot 0 none / 1 poison / 2 venom with the damage
 *                                             of its next hit; burns = active burn stacks = the next combined burn hit
 *  - `gi|<entry>;<entry>...`                  ground items: `<level>,<x>,<z>,<id>,<despawnTicks>,<unitValue>,<unitAlch>,<flags>`
 *                                             per tile and item id the player can see; despawnTicks -1 = never despawns,
 *                                             -2 = none left; flags: 1 untradeable, 2 dropped for this player, 4 dropped
 *                                             for another player, 8 still private, 16 dropped this cycle
 */
object ClientHud {
    const val PREFIX = "__RSPS_HUD__:"
    const val MAX_PAYLOAD = 200

    /** Hard limit: 255 bytes minus the message header (type, int, flag byte), the prefix and the string terminator. */
    private const val MAX_WRITE = 255 - 6 - PREFIX.length - 1

    /** Unit value of an item for [player]: bound by the plugins to the price checker / GE guide (one price source). */
    @JvmStatic
    var itemValue: (Player, Int) -> Int = { _, _ -> 0 }

    /** Coins high alchemy pays for one item: bound by the plugins to the spell's own formula (Alchemy.coins). */
    @JvmStatic
    var itemAlch: (Int) -> Int = { 0 }

    const val FLAG_UNTRADEABLE = 1
    const val FLAG_MINE = 2
    const val FLAG_OTHERS = 4
    const val FLAG_PRIVATE = 8
    const val FLAG_FRESH = 16

    /** Per-player sync, run by [Player.postCycle] at the very end of every cycle; bound by the plugins (ClientHudSync). */
    @JvmStatic
    var tick: ((Player) -> Unit)? = null

    /** Conformance (`conform hud`): sees every payload exactly as it is written, since test bots discard their output. */
    @JvmStatic
    @Volatile
    var observer: ((Player, String) -> Unit)? = null

    fun send(
        player: Player,
        payload: String,
    ) {
        observer?.invoke(player, payload)
        if (payload.length > MAX_WRITE) {
            // MESSAGE_GAME is a var-byte packet: a longer text would break the whole packet, so it is dropped and named.
            Trace.problem(Trace.Cat.PLUGIN) { "hud payload of ${payload.length} chars dropped for ${player.username}: ${payload.take(60)}" }
            return
        }
        player.writeConsoleMessage(PREFIX + payload)
    }

    /**
     * Ground-item entry for the items with id [itemId] on [items]' tile that [player] can see: the soonest despawn among
     * them (the stack label shows the one that goes first) and the unit value.
     */
    fun groundEntry(
        world: World,
        player: Player,
        level: Int,
        x: Int,
        z: Int,
        itemId: Int,
        items: List<GroundItem>,
    ): String {
        var soonest = Int.MAX_VALUE
        var first: GroundItem? = null
        var fresh = false
        for (item in items) {
            if (item.item != itemId || !item.canBeViewedBy(player)) continue
            // World.cycle: an item despawns once currentCycle reaches gItemDespawnDelay; world spawns (respawnCycles set) never do.
            val remaining = if (item.respawnCycles != -1) Int.MAX_VALUE else maxOf(0, world.gameContext.gItemDespawnDelay - item.currentCycle)
            if (first == null || remaining < soonest) {
                soonest = remaining
                first = item
            }
            if (item.currentCycle == 0 && item.respawnCycles == -1) fresh = true
        }
        val shown = first ?: return "$level,$x,$z,$itemId,-2,0,0,0"
        val ticks = if (soonest == Int.MAX_VALUE) -1 else soonest
        var flags = 0
        if (!world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, itemId).tradeable) flags = flags or FLAG_UNTRADEABLE
        val owner = shown.droppedFor
        if (owner != null) flags = flags or (if (owner.value == player.uid.value) FLAG_MINE else FLAG_OTHERS)
        if (!shown.isPublic()) flags = flags or FLAG_PRIVATE
        if (fresh) flags = flags or FLAG_FRESH
        return "$level,$x,$z,$itemId,$ticks,${cachedValue(world, player, itemId)},${cachedAlch(world, itemId)},$flags"
    }

    // ---- 100-player load (owner 2026-09-29: "er gaan 100 players deze plugins gebruiken") -------------------------------

    /** Item values of this cycle: one price lookup per item id per cycle, not one per item per viewer. */
    private val valueCache = HashMap<Int, Int>()
    private val alchCache = HashMap<Int, Int>()
    private var valueCycle = -1

    private fun cachedAlch(
        world: World,
        itemId: Int,
    ): Int {
        if (valueCycle != world.currentCycle) {
            valueCache.clear()
            alchCache.clear()
            valueCycle = world.currentCycle
        }
        return alchCache.getOrPut(itemId) { itemAlch(itemId) }
    }

    private fun cachedValue(
        world: World,
        player: Player,
        itemId: Int,
    ): Int {
        if (valueCycle != world.currentCycle) {
            valueCache.clear()
            alchCache.clear()
            valueCycle = world.currentCycle
        }
        return valueCache.getOrPut(itemId) { itemValue(player, itemId) }
    }

    /** Ground entries waiting for this player's end-of-cycle flush, one per tile + item id (the newest wins). */
    private val PENDING = AttributeKey<LinkedHashMap<String, String>>()

    /**
     * Queues ground entries for [player]. They go out in [flushGround] at the end of the cycle (Player.postCycle), after
     * every obj packet of the cycle, as few packets as fit: a 28-item death pile seen by 100 players is 100 x 5 packets
     * instead of 100 x 28, and a tile that changes several times in one cycle is sent once.
     */
    fun queueGround(
        player: Player,
        entries: Collection<String>,
    ) {
        if (entries.isEmpty()) return
        val pending = player.attr[PENDING] ?: LinkedHashMap<String, String>().also { player.attr[PENDING] = it }
        for (entry in entries) {
            // Key = level,x,z,id (the first four fields).
            var commas = 0
            var end = entry.length
            for (i in entry.indices) {
                if (entry[i] == ',' && ++commas == 4) {
                    end = i
                    break
                }
            }
            val key = entry.substring(0, end)
            pending.remove(key)
            pending[key] = entry
        }
    }

    fun flushGround(player: Player) {
        val pending = player.attr[PENDING] ?: return
        if (pending.isEmpty()) return
        sendGround(player, pending.values.toList())
        pending.clear()
    }

    /** Work of the sync this cycle, for the load check and the TICK trace. */
    @JvmStatic
    var cycleNanos = 0L
    @JvmStatic
    var cyclePlayers = 0
    private var statsCycle = -1

    /** A cycle whose overlay sync took longer than this is traced as a PROBLEM (the whole tick budget is 600 ms). */
    const val SLOW_CYCLE_NANOS = 20_000_000L

    /** Called by Player.postCycle around the overlay work of one player. */
    fun account(
        world: World,
        nanos: Long,
    ) {
        if (statsCycle != world.currentCycle) {
            if (statsCycle >= 0 && cycleNanos > SLOW_CYCLE_NANOS) {
                val ms = cycleNanos / 1_000_000.0
                val players = cyclePlayers
                Trace.problem(Trace.Cat.TICK) { "hud overlay sync took ${"%.1f".format(ms)} ms for $players players in one cycle" }
            }
            lastCycleNanos = cycleNanos
            lastCyclePlayers = cyclePlayers
            cycleNanos = 0L
            cyclePlayers = 0
            statsCycle = world.currentCycle
        }
        cycleNanos += nanos
        cyclePlayers++
    }

    /** The previous cycle's totals (read by `conform hud load`). */
    @JvmStatic
    var lastCycleNanos = 0L
    @JvmStatic
    var lastCyclePlayers = 0

    /** Sends [entries] as few `gi|` payloads as fit the packet. */
    fun sendGround(
        player: Player,
        entries: List<String>,
    ) {
        val line = StringBuilder()
        for (entry in entries) {
            if (line.isNotEmpty() && line.length + entry.length + 1 > MAX_PAYLOAD) {
                send(player, "gi|$line")
                line.setLength(0)
            }
            if (line.isNotEmpty()) line.append(';')
            line.append(entry)
        }
        if (line.isNotEmpty()) send(player, "gi|$line")
    }
}
