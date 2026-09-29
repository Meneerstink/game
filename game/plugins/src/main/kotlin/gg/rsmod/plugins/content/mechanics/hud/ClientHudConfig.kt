package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud

/**
 * The information overlays' settings of one account, saved with the account and pushed to the client (its RuneLite-style
 * sidebar shows and edits them). Owner 2026-09-29: "de instellingen voor de plugins moeten zelfde instellingen als
 * runelite kunnen doen" - the keys, types, options and (unless noted) defaults are RuneLite's own config items of the
 * Boosts Information, Opponent Information, Ground Items and Timers & Buffs plugins (runelite-client, master, 2026-09-29).
 *
 * Deliberate differences, each for a reason:
 *  - Not offered: Opponent "Lookup players on interaction" (a hiscore lookup - excluded by the owner's specification) and
 *    the Ground Items "group" part of the ownership filter (this server has no groups).
 *  - Defaults kept from the owner's specification where it asked for them: the Boosts panel (not infoboxes), the next
 *    debuff change, despawn timers in seconds, the price as GE value, and empty item lists (RuneLite hides coins and
 *    bones by default - on a Deadman server coin stacks matter).
 */
object ClientHudConfig {
    enum class Type { BOOL, INT, ENUM, COLOR }

    class Setting(
        val key: String,
        val type: Type,
        val default: String,
        val options: List<String> = emptyList(),
        val min: Int = 0,
        val max: Int = Int.MAX_VALUE,
    ) {
        fun valid(value: String): Boolean =
            when (type) {
                Type.BOOL -> value == "true" || value == "false"
                Type.INT -> value.toIntOrNull()?.let { it in min..max } == true
                Type.ENUM -> value in options
                Type.COLOR -> value.length == 6 && value.toIntOrNull(16) != null
            }
    }

    private fun bool(key: String, default: Boolean) = Setting(key, Type.BOOL, default.toString())

    private fun int(key: String, default: Int, min: Int = 0, max: Int = Int.MAX_VALUE) = Setting(key, Type.INT, default.toString(), min = min, max = max)

    private fun choice(key: String, default: String, vararg options: String) = Setting(key, Type.ENUM, default, options.toList())

    private fun color(key: String, default: String) = Setting(key, Type.COLOR, default)

    private val TIERS = arrayOf("OFF", "LOW", "MEDIUM", "HIGH", "INSANE")

    val SETTINGS: List<Setting> =
        listOf(
            // The four plugins' own switches (RuneLite's plugin list).
            bool("boosts.enabled", true),
            bool("opponent.enabled", true),
            bool("grounditems.enabled", true),
            bool("timers.enabled", true),
            // Boosts Information (BoostsConfig).
            choice("boosts.displayBoosts", "COMBAT", "NONE", "COMBAT", "NON_COMBAT", "BOTH"),
            bool("boosts.relativeBoost", false),
            bool("boosts.displayInfoboxes", false),
            bool("boosts.displayPanel", true),
            bool("boosts.compactDisplay", false),
            choice("boosts.displayNextBuffChange", "BOOSTED", "ALWAYS", "BOOSTED", "NEVER"),
            choice("boosts.displayNextDebuffChange", "BOOSTED", "ALWAYS", "BOOSTED", "NEVER"),
            int("boosts.boostThreshold", 0, 0, 99),
            bool("boosts.notifyOnBoost", false),
            // Opponent Information (OpponentInfoConfig).
            choice("opponent.hitpointsDisplayStyle", "HITPOINTS", "HITPOINTS", "PERCENTAGE", "BOTH"),
            bool("opponent.showOpponentsInMenu", false),
            bool("opponent.showOpponentHealthOverlay", true),
            // Ground Items (GroundItemsConfig).
            bool("grounditems.showHighlightedOnly", false),
            bool("grounditems.dontHideUntradeables", true),
            bool("grounditems.showMenuItemQuantities", true),
            bool("grounditems.recolorMenuHiddenItems", false),
            bool("grounditems.deprioritizeHiddenItems", false),
            bool("grounditems.highlightTiles", false),
            bool("grounditems.notifyHighlightedDrops", false),
            choice("grounditems.notifyTier", "OFF", *TIERS),
            choice("grounditems.priceDisplayMode", "GE", "HA", "GE", "BOTH", "OFF"),
            choice("grounditems.itemHighlightMode", "BOTH", "NONE", "OVERLAY", "MENU", "BOTH"),
            choice("grounditems.menuHighlightMode", "NAME", "OPTION", "NAME", "BOTH"),
            choice("grounditems.highlightValueCalculation", "HIGHEST", "HA", "GE", "HIGHEST"),
            int("grounditems.hideUnderValue", 0),
            color("grounditems.defaultColor", "ffffff"),
            color("grounditems.highlightedColor", "aa00ff"),
            color("grounditems.hiddenColor", "808080"),
            color("grounditems.lowValueColor", "66b2ff"),
            int("grounditems.lowValuePrice", 20_000),
            color("grounditems.mediumValueColor", "99ff99"),
            int("grounditems.mediumValuePrice", 100_000),
            color("grounditems.highValueColor", "ff9600"),
            int("grounditems.highValuePrice", 1_000_000),
            color("grounditems.insaneValueColor", "ff66b2"),
            int("grounditems.insaneValuePrice", 10_000_000),
            choice("grounditems.ownershipFilterMode", "ALL", "ALL", "TAKEABLE", "DROPS"),
            int("grounditems.doubleTapDelay", 250, 0, 1000),
            bool("grounditems.collapseEntries", false),
            choice("grounditems.groundItemTimers", "SECONDS", "OFF", "PIE", "TICKS", "SECONDS"),
            bool("grounditems.textOutline", false),
            bool("grounditems.showLootbeamForHighlighted", false),
            choice("grounditems.showLootbeamTier", "HIGH", *TIERS),
            choice("grounditems.lootbeamStyle", "MODERN", "MODERN", "LEGACY"),
            choice("grounditems.hotkey", "ALT", "ALT", "SHIFT", "CTRL"),
            // Timers & Buffs (TimersAndBuffsConfig), the PK subset this server tracks.
            bool("timers.showVengeance", true),
            bool("timers.showVengeanceActive", true),
            bool("timers.showTeleblock", true),
            bool("timers.showFreezes", true),
        )

    private val BY_KEY = SETTINGS.associateBy { it.key }

    val PLUGINS = listOf("boosts", "opponent", "grounditems", "timers")

    private val VALUES = AttributeKey<String>(persistenceKey = "hud_settings")
    private val HIGHLIGHT = AttributeKey<String>(persistenceKey = "hud_highlight")
    private val HIDDEN = AttributeKey<String>(persistenceKey = "hud_hidden")

    const val MAX_LIST = 60

    /** Longest list entry: keeps every entry far inside one packet (item names are well below this). */
    const val MAX_NAME = 50

    fun setting(key: String): Setting? = BY_KEY[key]

    /** The stored values that differ from the defaults (invalid or unknown ones are ignored). */
    private fun stored(player: Player): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        for (part in (player.attr[VALUES] ?: "").split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = part.substring(0, eq)
            val value = part.substring(eq + 1)
            if (BY_KEY[key]?.valid(value) == true) map[key] = value
        }
        return map
    }

    private fun store(
        player: Player,
        map: Map<String, String>,
    ) {
        if (map.isEmpty()) player.attr.remove(VALUES) else player.attr[VALUES] = map.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    fun get(
        player: Player,
        key: String,
    ): String = stored(player)[key] ?: BY_KEY.getValue(key).default

    fun bool(
        player: Player,
        key: String,
    ): Boolean = get(player, key) == "true"

    /** Sets [key]; false when the key or value is not valid (nothing changes then). */
    fun set(
        player: Player,
        key: String,
        value: String,
    ): Boolean {
        // Case-insensitive: typed or sent commands may arrive in lower case.
        val setting = BY_KEY[key] ?: SETTINGS.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: return false
        val normalised =
            when (setting.type) {
                Type.COLOR -> value.lowercase().removePrefix("#")
                Type.ENUM -> value.uppercase()
                Type.BOOL -> value.lowercase()
                Type.INT -> value
            }
        if (!setting.valid(normalised)) return false
        val map = stored(player)
        if (normalised == setting.default) map.remove(setting.key) else map[setting.key] = normalised
        store(player, map)
        return true
    }

    /** Back to the defaults: one plugin's settings, or everything (lists included) when [plugin] is null. */
    fun reset(
        player: Player,
        plugin: String? = null,
    ) {
        if (plugin == null) {
            player.attr.remove(VALUES)
            player.attr.remove(HIGHLIGHT)
            player.attr.remove(HIDDEN)
            return
        }
        val map = stored(player)
        map.keys.removeIf { it.startsWith("$plugin.") }
        store(player, map)
        if (plugin == "grounditems") {
            player.attr.remove(HIGHLIGHT)
            player.attr.remove(HIDDEN)
        }
    }

    fun list(
        player: Player,
        hidden: Boolean,
    ): List<String> = (player.attr[if (hidden) HIDDEN else HIGHLIGHT] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Adds [name] to a list (and takes it off the other one: an item is highlighted or hidden, never both). */
    fun add(
        player: Player,
        hidden: Boolean,
        name: String,
    ): Boolean {
        val entry = normalise(name)
        if (entry.isEmpty()) return false
        val list = list(player, hidden).toMutableList()
        if (entry in list) return true
        if (list.size >= MAX_LIST) return false
        list += entry
        player.attr[if (hidden) HIDDEN else HIGHLIGHT] = list.joinToString(",")
        val other = list(player, !hidden).toMutableList()
        if (other.remove(entry)) player.attr[if (hidden) HIGHLIGHT else HIDDEN] = other.joinToString(",")
        return true
    }

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

    /** Lower case; the separators (,;|=) and markup brackets (<>) removed; "*" stays as a wildcard. */
    fun normalise(name: String): String = name.lowercase().filterNot { it in ",;|=<>" }.trim().take(MAX_NAME)

    /** Pushes every setting (defaults included) and both lists, in as few packets as fit. */
    fun push(player: Player) {
        val line = StringBuilder()
        for (setting in SETTINGS) {
            val entry = "${setting.key}=${get(player, setting.key)}"
            if (line.isNotEmpty() && line.length + entry.length + 1 > ClientHud.MAX_PAYLOAD) {
                ClientHud.send(player, "cfg|$line")
                line.setLength(0)
            }
            if (line.isNotEmpty()) line.append(';')
            line.append(entry)
        }
        if (line.isNotEmpty()) ClientHud.send(player, "cfg|$line")
        pushList(player, "hl", list(player, false))
        pushList(player, "hide", list(player, true))
    }

    /** Tours and tests: every setting at its default on the client, without touching what the account saved. */
    fun pushDefaults(player: Player) {
        val line = StringBuilder()
        for (setting in SETTINGS) {
            val entry = "${setting.key}=${setting.default}"
            if (line.isNotEmpty() && line.length + entry.length + 1 > ClientHud.MAX_PAYLOAD) {
                ClientHud.send(player, "cfg|$line")
                line.setLength(0)
            }
            if (line.isNotEmpty()) line.append(';')
            line.append(entry)
        }
        if (line.isNotEmpty()) ClientHud.send(player, "cfg|$line")
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
