package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.magic.alchemy.Alchemy

/**
 * RuneLite-style information overlays (owner 2026-09-29): Ground Items, Timers & Buffs, Boosts Information, Opponent
 * Information. The client draws; this plugin feeds it the server's own state (ClientHudSync, Chunk ground-item entries)
 * and owns the `hud` settings command. Information only - nothing here acts for the player.
 */

// Ground Items value: the same player-facing valuation as the price checker (GE guide, else our seed, else cache cost).
on_world_init {
    ClientHud.itemValue = { player, id -> PriceChecker.value(player, Item(id, 1)) }
    // "HA" price: what the high alchemy spell really pays here.
    ClientHud.itemAlch = { id -> Alchemy.coins(world.definitions.get(ItemDef::class.java, id), true) }
    // End of every cycle (Player.postCycle), so a death, hit or npc heal of this cycle is in this cycle's update.
    ClientHud.tick = { player -> ClientHudSync.tick(player) }
}

on_login {
    ClientHudSync.reset(player)
    ClientHudConfig.push(player)
    // Ground Items' pie timer: a new drop's full despawn time on this server.
    ClientHud.send(player, "gdelay|${world.gameContext.gItemDespawnDelay}")
}

/**
 * `hud` / `plugins`: open the client's 78 Plugins sidebar (RuneLite-style). Every change there arrives here as a quiet
 * command and is saved with the account, then pushed back:
 *   hud set <key> <value>                      one setting (ClientHudConfig.SETTINGS; validated)
 *   hud list <highlight|hide> <add|remove> <name...>   hud list <highlight|hide> clear
 *   hud reset [boosts|opponent|grounditems|timers]
 * Typed shortcuts: hud status, hud <boosts|opponent|grounditems|timers> on|off.
 */
on_command("plugins") {
    ClientHud.send(player, "open")
}

fun status(player: gg.rsmod.game.model.entity.Player) {
    val on = ClientHudConfig.PLUGINS.joinToString(", ") { "$it ${if (ClientHudConfig.bool(player, "$it.enabled")) "on" else "off"}" }
    player.message("Plugins: $on. Settings: open the 78 Plugins sidebar (hud).")
    player.message("Highlighted: ${ClientHudConfig.list(player, false).joinToString(", ").ifEmpty { "none" }}.")
    player.message("Hidden: ${ClientHudConfig.list(player, true).joinToString(", ").ifEmpty { "none" }}.")
}

on_command("hud") {
    val raw = player.getCommandArgs().toList()
    val args = raw.map { it.lowercase() }
    when (args.getOrNull(0)) {
        null, "open" -> {
            ClientHud.send(player, "open")
            return@on_command
        }
        "status", "help" -> {
            status(player)
            return@on_command
        }
        "set" -> {
            // Keys are camelCase (RuneLite's): take them as typed.
            val key = raw.getOrNull(1) ?: return@on_command
            val value = raw.getOrNull(2) ?: return@on_command
            if (!ClientHudConfig.set(player, key, value)) player.message("hud: '$key' cannot be '$value'.")
        }
        "list" -> {
            val hidden =
                when (args.getOrNull(1)) {
                    "highlight" -> false
                    "hide" -> true
                    else -> return@on_command
                }
            val name = raw.drop(3).joinToString(" ")
            when (args.getOrNull(2)) {
                "add" -> if (!ClientHudConfig.add(player, hidden, name)) player.message("hud: '$name' could not be added (list full or empty name).")
                "remove" -> ClientHudConfig.remove(player, hidden, name)
                "clear" -> ClientHudConfig.clear(player, hidden)
                else -> return@on_command
            }
        }
        "reset" -> {
            val plugin = args.getOrNull(1)
            if (plugin != null && plugin !in ClientHudConfig.PLUGINS) return@on_command
            ClientHudConfig.reset(player, plugin)
        }
        in ClientHudConfig.PLUGINS -> {
            val plugin = args[0]
            val on = when (args.getOrNull(1)) {
                "on" -> true
                "off" -> false
                else -> !ClientHudConfig.bool(player, "$plugin.enabled")
            }
            ClientHudConfig.set(player, "$plugin.enabled", on.toString())
            player.message("$plugin ${if (on) "on" else "off"}.")
        }
        else -> {
            status(player)
            return@on_command
        }
    }
    ClientHudConfig.push(player)
}
