package gg.rsmod.game.model.hud

import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
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
 *  - `cfg|<config>`                           the player's overlay settings (see the plugin's ClientHudConfig)
 *  - `boost|<decayTicks>|<restoreTicks>`      ticks until the next boosted combat stat drops / lowered one returns; -1 = none
 *  - `opp|n|<npcIndex>|<ratio>|<cur>|<max>`   the player's current opponent; ratio 0..255 like the hit mask
 *  - `opp|p|<playerIndex>|<ratio>`            (a player opponent: ratio only, as in the hit mask)
 *  - `opp|none`
 *  - `tmr|<vengActive>|<vengCd>|<teleblock>|<frozen>`   remaining ticks, 0 = not active
 *  - `gi|<entry>;<entry>...`                  ground items: `<level>,<x>,<z>,<id>,<despawnTicks>,<unitValue>` per tile and
 *                                             item id the player can see; despawnTicks -1 = never despawns, -2 = none left
 */
object ClientHud {
    const val PREFIX = "__RSPS_HUD__:"
    const val MAX_PAYLOAD = 200

    /** Hard limit: 255 bytes minus the message header (type, int, flag byte), the prefix and the string terminator. */
    private const val MAX_WRITE = 255 - 6 - PREFIX.length - 1

    /** Unit value of an item for [player]: bound by the plugins to the price checker / GE guide (one price source). */
    @JvmStatic
    var itemValue: (Player, Int) -> Int = { _, _ -> 0 }

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
        var any = false
        for (item in items) {
            if (item.item != itemId || !item.canBeViewedBy(player)) continue
            any = true
            // World.cycle: an item despawns once currentCycle reaches gItemDespawnDelay; world spawns (respawnCycles set) never do.
            val remaining = if (item.respawnCycles != -1) Int.MAX_VALUE else maxOf(0, world.gameContext.gItemDespawnDelay - item.currentCycle)
            soonest = minOf(soonest, remaining)
        }
        val ticks = if (!any) -2 else if (soonest == Int.MAX_VALUE) -1 else soonest
        val value = if (any) itemValue(player, itemId) else 0
        return "$level,$x,$z,$itemId,$ticks,$value"
    }

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
