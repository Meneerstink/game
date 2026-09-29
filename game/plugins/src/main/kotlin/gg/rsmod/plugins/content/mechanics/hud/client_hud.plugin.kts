package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker

/**
 * RuneLite-style information overlays (owner 2026-09-29): Ground Items, Timers & Buffs, Boosts Information, Opponent
 * Information. The client draws; this plugin feeds it the server's own state (ClientHudSync, Chunk ground-item entries)
 * and owns the `hud` settings command. Information only - nothing here acts for the player.
 */

// Ground Items value: the same player-facing valuation as the price checker (GE guide, else our seed, else cache cost).
on_world_init {
    ClientHud.itemValue = { player, id -> PriceChecker.value(player, Item(id, 1)) }
    // End of every cycle (Player.postCycle), so a death, hit or npc heal of this cycle is in this cycle's update.
    ClientHud.tick = { player -> ClientHudSync.tick(player) }
}

on_login {
    ClientHudSync.reset(player)
    ClientHudConfig.push(player)
}

fun parseAmount(text: String): Long? {
    val t = text.lowercase().replace(",", "")
    val mult =
        when {
            t.endsWith("b") -> 1_000_000_000L
            t.endsWith("m") -> 1_000_000L
            t.endsWith("k") -> 1_000L
            else -> 1L
        }
    val number = (if (mult == 1L) t else t.dropLast(1)).toDoubleOrNull() ?: return null
    if (number < 0) return null
    return (number * mult).toLong()
}

fun status(player: gg.rsmod.game.model.entity.Player) {
    val on = ClientHudConfig.SWITCHES.joinToString(", ") { "$it ${if (ClientHudConfig.flag(player, it)) "on" else "off"}" }
    player.message("HUD: $on.")
    val tiers = ClientHudConfig.tiers(player)
    player.message("Value tiers: " + ClientHudConfig.TIER_NAMES.indices.joinToString(", ") { "${ClientHudConfig.TIER_NAMES[it]} ${tiers[it]}" } + ".")
    player.message("Highlighted: ${ClientHudConfig.list(player, false).joinToString(", ").ifEmpty { "none" }}.")
    player.message("Hidden: ${ClientHudConfig.list(player, true).joinToString(", ").ifEmpty { "none" }}.")
    player.message("Use: hud <boosts|opponent|grounditems|timers|despawn|value> on/off, hud boosts all/changed, hud highlight <item>, " +
        "hud hide <item>, hud tier <low|medium|high|insane> <amount>, hud colour <name> <rrggbb>, hud reset.")
}

on_command("hud") {
    val args = player.getCommandArgs()
    if (args.isEmpty()) {
        status(player)
        return@on_command
    }
    val what = args[0].lowercase()
    val rest = args.drop(1)
    val feature =
        when (what) {
            "boosts", "boost" -> "boosts"
            "opponent", "opp" -> "opponent"
            "grounditems", "ground", "gi" -> "grounditems"
            "timers", "buffs" -> "timers"
            "despawn" -> "despawn"
            "value", "values" -> "value"
            else -> null
        }
    when {
        feature == "boosts" && rest.firstOrNull()?.lowercase() in setOf("all", "changed") -> {
            val all = rest[0].lowercase() == "all"
            ClientHudConfig.setFlag(player, "boostsall", all)
            player.message("Boosts Information now shows ${if (all) "all five combat stats" else "only changed stats"}.")
        }
        feature != null && rest.firstOrNull()?.lowercase() in setOf("on", "off") -> {
            val on = rest[0].lowercase() == "on"
            ClientHudConfig.setFlag(player, feature, on)
            player.message("HUD $feature ${if (on) "on" else "off"}.")
        }
        feature != null && rest.isEmpty() -> {
            val on = !ClientHudConfig.flag(player, feature)
            ClientHudConfig.setFlag(player, feature, on)
            player.message("HUD $feature ${if (on) "on" else "off"}.")
        }
        what == "highlight" || what == "hide" -> {
            val hidden = what == "hide"
            val name = rest.joinToString(" ")
            if (name.equals("clear", ignoreCase = true)) {
                ClientHudConfig.clear(player, hidden)
                player.message("${if (hidden) "Hidden" else "Highlighted"} list cleared.")
            } else if (ClientHudConfig.normalise(name).isEmpty()) {
                player.message("Use: hud $what <item name> (a * matches anything, e.g. dragon*), or hud $what clear.")
                return@on_command
            } else if (ClientHudConfig.listSize(player, hidden) >= ClientHudConfig.MAX_LIST &&
                ClientHudConfig.normalise(name) !in ClientHudConfig.list(player, hidden)
            ) {
                player.message("That list is full (${ClientHudConfig.MAX_LIST} entries).")
                return@on_command
            } else {
                val on = ClientHudConfig.toggle(player, hidden, name)
                player.message("${ClientHudConfig.normalise(name)} ${if (on) "added to" else "removed from"} the ${if (hidden) "hidden" else "highlighted"} items.")
            }
        }
        what == "tier" -> {
            val index = ClientHudConfig.TIER_NAMES.indexOf(rest.getOrNull(0)?.lowercase())
            val amount = rest.getOrNull(1)?.let { parseAmount(it) }
            if (index < 0 || amount == null) {
                player.message("Use: hud tier <low|medium|high|insane> <amount>, e.g. hud tier high 1m.")
                return@on_command
            }
            ClientHudConfig.setTier(player, index, amount)
            player.message("Value tier ${ClientHudConfig.TIER_NAMES[index]} set to $amount gp.")
        }
        what == "colour" || what == "color" -> {
            val index = ClientHudConfig.COLOUR_NAMES.indexOf(rest.getOrNull(0)?.lowercase())
            val rgb = rest.getOrNull(1)?.removePrefix("#")?.takeIf { it.length == 6 }?.toIntOrNull(16)
            if (index < 0 || rgb == null) {
                player.message("Use: hud colour <${ClientHudConfig.COLOUR_NAMES.joinToString("|")}> <rrggbb>, e.g. hud colour high ff9600.")
                return@on_command
            }
            ClientHudConfig.setColour(player, index, rgb)
            player.message("Colour ${ClientHudConfig.COLOUR_NAMES[index]} set to ${rest[1].removePrefix("#")}.")
        }
        what == "reset" -> {
            ClientHudConfig.reset(player)
            player.message("HUD settings reset to the defaults.")
        }
        else -> {
            status(player)
            return@on_command
        }
    }
    ClientHudConfig.push(player)
}
