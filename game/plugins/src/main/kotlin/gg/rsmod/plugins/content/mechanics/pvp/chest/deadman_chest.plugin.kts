package gg.rsmod.plugins.content.mechanics.pvp.chest

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach

/**
 * The Deadman's chest (owner 2026-10-17, dossier N101; OSRS wiki "Deadman's chest (scenery)"): a timed chest every 30 minutes at a
 * random dangerous spot (broadcast), a rare chest after the kill of an npc with 30+ Hitpoints, opened in 60 s (singles) / 30 s
 * (multi) without combat or inventory actions; the first opener gets ONE reward ([DeadmanChestLoot]). Rules: [DeadmanChest],
 * world handling: [DeadmanChestWorld]. Nothing is bound while the cache step is missing ([DeadmanChestIds]).
 */

on_world_init { DeadmanChestWorld.start(world) }

if (DeadmanChestIds.closedLoc > 0) {
    // The option is "Loot" (OSRS 60246), bound by name; the clicked chest is found by its tile.
    on_obj_option(obj = DeadmanChestIds.closedLoc, option = "loot") { DeadmanChestWorld.beginOpen(player, player.getInteractingGameObj().tile) }
}

on_npc_killed { killer, npc -> DeadmanChestWorld.onNpcKilled(world, killer, npc) }

if (DeadmanChestIds.guardianNpc > 0) {
    // OSRS: the chest's Zemouregal always drops a tier 5 Archaic emblem and nothing else (the breach death hook returns: he is no breach npc).
    on_npc_death(DeadmanChestIds.guardianNpc) { DeadmanChestWorld.onGuardianDeath(world, npc) }
}

// A player with PvP protection cannot fight the chest's Zemouregal (same rule as the breach monsters, owner 2026-09-23).
can_attack { attacker, target ->
    val npc = (target as? Npc)?.takeIf { DeadmanChestWorld.isGuardian(it) } ?: (attacker as? Npc)?.takeIf { DeadmanChestWorld.isGuardian(it) }
    val other = if (npc === target) attacker else target
    val player = DeadmanBreach.controllingPlayer(other)
    if (npc == null || player == null || DeadmanBreach.canFight(player)) {
        true
    } else {
        if (attacker !== npc && world.plugins.notifyAttackRefusal) player.filterableMessage("You can't attack the chest's guardian while you have PvP protection.")
        false
    }
}

// Admin: ::chest [spawn [part of a spot name]|here|list|close]
on_command("chest", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs().joinToString(" ").trim()
    val first = args.substringBefore(' ').lowercase()
    when (first) {
        "list" ->
            DeadmanChestWorld.spots(world).forEachIndexed { i, s -> player.message("${i + 1}. ${s.name} (${s.tile.x}, ${s.tile.z})") }
        "close" -> {
            DeadmanChestWorld.chests.toList().forEach { DeadmanChestWorld.remove(world, it, "admin close") }
            player.message("Every Deadman's chest removed.")
        }
        "here" -> {
            val spot = DeadmanChestWorld.Spot("your position", player.tile)
            val chest = if (DeadmanChestWorld.fits(world, player.tile)) DeadmanChestWorld.spawnTimed(world, spot) else null
            player.message(if (chest != null) "Chest spawned on your tile." else "Not possible here (safe zone, blocked tile, or the chest cache data is missing).")
        }
        else -> {
            val name = args.removePrefix("spawn").trim()
            val spot = if (name.isEmpty()) null else DeadmanChestWorld.spots(world).firstOrNull { it.name.contains(name, ignoreCase = true) || name == (DeadmanChestWorld.spots(world).indexOf(it) + 1).toString() }
            if (name.isNotEmpty() && spot == null) {
                player.message("No chest spot matches '$name'. Type ::chest list.")
            } else {
                val chest = DeadmanChestWorld.spawnTimed(world, spot)
                player.message(if (chest != null) "Chest spawned at ${chest.name} (${chest.tile.x}, ${chest.tile.z})." else "No chest spawned (cache data missing or no usable spot).")
            }
        }
    }
}
