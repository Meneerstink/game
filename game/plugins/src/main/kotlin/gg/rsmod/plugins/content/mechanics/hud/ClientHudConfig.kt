package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud

/**
 * The overlay settings of one account (the `hud` command), saved with the account and pushed to the client on login and
 * after every change. The client keeps no settings of its own, so they follow the account to any machine.
 *
 * Defaults follow RuneLite's own plugin defaults (the functional reference the owner named): value tiers 20K / 100K /
 * 1M / 10M with RuneLite's tier colours, the highlight colour purple, "show only changed stats" for Boosts.
 */
object ClientHudConfig {
    private val FLAGS = AttributeKey<String>(persistenceKey = "hud_flags")
    private val HIGHLIGHT = AttributeKey<String>(persistenceKey = "hud_highlight")
    private val HIDDEN = AttributeKey<String>(persistenceKey = "hud_hidden")
    private val TIERS = AttributeKey<String>(persistenceKey = "hud_tiers")
    private val COLOURS = AttributeKey<String>(persistenceKey = "hud_colours")
    private val HIDE_UNDER = AttributeKey<String>(persistenceKey = "hud_hide_under")

    /**
     * On/off switches, in client order (RspsHudConfig reads them by position): the four plugins and their settings -
     * Boosts: all five stats, the next-change timers; Opponent: exact npc life points; Ground Items: despawn timers, values;
     * Timers & Buffs: each of the four lines.
     */
    val SWITCHES =
        listOf(
            "boosts", "boostsall", "opponent", "grounditems", "despawn", "value", "timers",
            "boosttimers", "oppexact", "tveng", "tvengcd", "tteleblock", "tfrozen",
        )
    private val DEFAULT_FLAGS = SWITCHES.associateWith { it != "boostsall" }

    val TIER_NAMES = listOf("low", "medium", "high", "insane")
    private val DEFAULT_TIERS = listOf(20_000L, 100_000L, 1_000_000L, 10_000_000L)

    val COLOUR_NAMES = listOf("default", "low", "medium", "high", "insane", "highlight")
    private val DEFAULT_COLOURS = listOf(0xFFFFFF, 0x66B2FF, 0x99FF99, 0xFF9600, 0xFF66B2, 0xAA00FF)

    const val MAX_LIST = 60

    /** Longest list entry: keeps every entry far inside one packet (item names are well below this). */
    const val MAX_NAME = 50

    fun flag(
        player: Player,
        name: String,
    ): Boolean = flags(player)[name] ?: DEFAULT_FLAGS.getValue(name)

    private fun flags(player: Player): Map<String, Boolean> =
        (player.attr[FLAGS] ?: "").split(',').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            k to (v == "1")
        }

    fun setFlag(
        player: Player,
        name: String,
        on: Boolean,
    ) {
        val map = flags(player).toMutableMap()
        map[name] = on
        player.attr[FLAGS] = map.entries.joinToString(",") { "${it.key}=${if (it.value) 1 else 0}" }
    }

    /** Ground Items: stacks worth less than this are not labelled (0 = every stack; highlighted items always show). */
    fun hideUnder(player: Player): Long = player.attr[HIDE_UNDER]?.toLongOrNull() ?: 0L

    fun setHideUnder(
        player: Player,
        value: Long,
    ) {
        player.attr[HIDE_UNDER] = value.toString()
    }

    fun tiers(player: Player): List<Long> =
        player.attr[TIERS]?.split(',')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == DEFAULT_TIERS.size } ?: DEFAULT_TIERS

    fun setTier(
        player: Player,
        index: Int,
        value: Long,
    ) {
        val list = tiers(player).toMutableList()
        list[index] = value
        player.attr[TIERS] = list.joinToString(",")
    }

    fun colours(player: Player): List<Int> =
        player.attr[COLOURS]?.split(',')?.mapNotNull { it.toIntOrNull(16) }?.takeIf { it.size == DEFAULT_COLOURS.size } ?: DEFAULT_COLOURS

    fun setColour(
        player: Player,
        index: Int,
        rgb: Int,
    ) {
        val list = colours(player).toMutableList()
        list[index] = rgb and 0xFFFFFF
        player.attr[COLOURS] = list.joinToString(",") { Integer.toHexString(it) }
    }

    fun list(
        player: Player,
        hidden: Boolean,
    ): List<String> = (player.attr[if (hidden) HIDDEN else HIGHLIGHT] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Adds [name] to the list, or removes it when it is already there; returns true when it is now on the list. */
    fun toggle(
        player: Player,
        hidden: Boolean,
        name: String,
    ): Boolean {
        val entry = normalise(name)
        val list = list(player, hidden).toMutableList()
        val on = !list.remove(entry)
        if (on) {
            list.add(entry)
            // An item is either highlighted or hidden, never both (RuneLite behaves the same).
            val other = list(player, !hidden).toMutableList()
            if (other.remove(entry)) player.attr[if (hidden) HIGHLIGHT else HIDDEN] = other.joinToString(",")
        }
        player.attr[if (hidden) HIDDEN else HIGHLIGHT] = list.joinToString(",")
        return on
    }

    /** Removes [name] from the list; returns true when it was there. */
    fun remove(
        player: Player,
        hidden: Boolean,
        name: String,
    ): Boolean {
        val list = list(player, hidden).toMutableList()
        val removed = list.remove(normalise(name))
        player.attr[if (hidden) HIDDEN else HIGHLIGHT] = list.joinToString(",")
        return removed
    }

    fun clear(
        player: Player,
        hidden: Boolean,
    ) {
        player.attr.remove(if (hidden) HIDDEN else HIGHLIGHT)
    }

    fun reset(player: Player) {
        listOf(FLAGS, HIGHLIGHT, HIDDEN, TIERS, COLOURS, HIDE_UNDER).forEach { player.attr.remove(it) }
    }

    /** Lower case, commas (the list separator) and ';' removed; '*' stays as a wildcard. */
    fun normalise(name: String): String = name.lowercase().replace(",", "").replace(";", "").replace("|", "").trim().take(MAX_NAME)

    fun listSize(
        player: Player,
        hidden: Boolean,
    ): Int = list(player, hidden).size

    /** Pushes everything to the client: switches, tiers, colours, then both lists (chunked to the packet size). */
    fun push(player: Player) {
        val switches = SWITCHES.joinToString("") { if (flag(player, it)) "1" else "0" }
        ClientHud.send(player, "cfg|$switches|${tiers(player).joinToString(",")}|${colours(player).joinToString(",") { Integer.toHexString(it) }}|${hideUnder(player)}")
        pushList(player, "hl", list(player, false))
        pushList(player, "hide", list(player, true))
    }

    private fun pushList(
        player: Player,
        tag: String,
        names: List<String>,
    ) {
        ClientHud.send(player, "$tag|clear")
        val line = StringBuilder()
        for (name in names) {
            if (line.isNotEmpty() && line.length + name.length + 1 > ClientHud.MAX_PAYLOAD) {
                ClientHud.send(player, "$tag|add|$line")
                line.setLength(0)
            }
            if (line.isNotEmpty()) line.append(',')
            line.append(name)
        }
        if (line.isNotEmpty()) ClientHud.send(player, "$tag|add|$line")
    }
}
