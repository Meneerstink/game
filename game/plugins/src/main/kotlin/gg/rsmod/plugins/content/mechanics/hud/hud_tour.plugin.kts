package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance

/**
 * `hudtour` - visual proof of the information overlays in the real client, without touching mouse or keyboard (owner
 * 2026-09-29: "ik wil wel resultaten zien"). Like `visualtour` it sets up each situation on the admin's own character,
 * asks the RSPS client to save the game canvas (`__RSPS_SHOT__:` -> C:\RSPS\screens\<session>_hud_<n>_<step>.png) and
 * afterwards puts everything back: levels, life points, Vengeance, Teleblock, freeze, position and the account's own
 * hud settings. The monster and the ground items are removed again; the items are private to the admin.
 *
 * Steps: boosts / timers / vengeance proc (cooldown only) / opponent / ground items with everything together / the 78
 * Plugins panel / Boosts switched off / partial pickup / teleported away (no labels may remain) / back and cleaned up.
 *
 * From the command port (agents): tools\conformance\Send-GameCommand.ps1 hudtour [player]
 */

val TOUR_SPOT = Tile(3222, 3218, 0)

/** Grand Exchange home: never dangerous (owner rule), far enough for a real region change. */
val SAFE_AWAY = Tile(3165, 3487, 0)

fun shot(
    player: Player,
    name: String,
) = player.message("__RSPS_SHOT__:$name", type = ChatMessageType.CONSOLE)

fun startTour(
    world: World,
    player: Player,
): String {
    // Everything is read and changed on the game thread (the command port calls this from its own thread).
    world.queue {
        val start = player.tile
        val combat = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)
        val levels = combat.associateWith { player.skills.getCurrentLevel(it) }
        val life = player.getCurrentLifepoints()
        val savedTimers = listOf(TELEBLOCK_TIMER, FROZEN_TIMER, FREEZE_IMMUNITY_TIMER, VENGEANCE_COOLDOWN).associateWith { key: TimerKey -> if (player.timers.has(key)) player.timers[key] else 0 }
        val vengeance = player.attr[VENGEANCE_ACTIVE_ATTR] == true
        // Auto retaliate off for the tour: a boosted admin hitting back killed the tour's monster (2026-09-29, twice).
        val retaliateOff = player.getVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP)
        val startCycle = world.currentCycle
        val items = ArrayList<GroundItem>()
        var giant: Npc? = null

        fun restore() {
            items.forEach { if (world.isSpawned(it)) world.remove(it) }
            giant?.let { if (world.npcs.contains(it)) world.remove(it) }
            Combat.reset(player)
            player.resetInteractions()
            combat.forEach { player.skills.setCurrentLevel(it, levels.getValue(it)) }
            player.setCurrentLifepoints(life)
            // The player's own timers kept running during the tour.
            val passed = world.currentCycle - startCycle
            savedTimers.forEach { (key, left) -> if (left - passed > 0) player.timers[key] = left - passed else player.timers.remove(key) }
            if (vengeance) player.attr[VENGEANCE_ACTIVE_ATTR] = true else player.attr.remove(VENGEANCE_ACTIVE_ATTR)
            player.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, retaliateOff)
            ClientHudConfig.push(player)
        }

        try {
            player.message("HUD tour: about 45 seconds. Keep the game window visible and uncovered.")
            // Every overlay on for the tour, with RuneLite's default tiers/colours; the account's own settings come back at the end.
            ClientHud.send(player, "cfg|1011111|20000,100000,1000000,10000000|ffffff,66b2ff,99ff99,ff9600,ff66b2,aa00ff")
            ClientHud.send(player, "hl|clear")
            ClientHud.send(player, "hl|add|dragon bones")
            ClientHud.send(player, "hide|clear")
            player.moveTo(TOUR_SPOT)
            wait(10)

            // 1. Boosts: boosted and drained combat stats.
            player.skills.setCurrentLevel(Skills.ATTACK, 118)
            player.skills.setCurrentLevel(Skills.STRENGTH, 118)
            player.skills.setCurrentLevel(Skills.DEFENCE, 77)
            player.skills.setCurrentLevel(Skills.RANGED, 109)
            player.skills.setCurrentLevel(Skills.MAGIC, 94)
            wait(3)
            shot(player, "hud_1_boosts")
            wait(2)

            // 2. Timers & Buffs: Vengeance primed + cooldown, Teleblock, frozen.
            player.timers.remove(VENGEANCE_COOLDOWN)
            Vengeance.activate(player)
            player.timers[TELEBLOCK_TIMER] = 500
            player.timers[FROZEN_TIMER] = 40
            wait(3)
            shot(player, "hud_2_timers")
            wait(2)

            // 3. Vengeance used: ACTIVE goes, the cooldown keeps running.
            player.attr.remove(VENGEANCE_ACTIVE_ATTR)
            wait(2)
            shot(player, "hud_3_vengeance_cooldown")
            wait(2)

            // 4. Opponent: a hill giant next to the admin.
            player.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
            val npc = Npc(Npcs.HILL_GIANT, TOUR_SPOT.transform(1, 0), world)
            npc.respawnOverride = false
            npc.walkRadius = 0
            world.spawn(npc)
            giant = npc
            wait(1)
            // The giant becomes the admin's opponent without a real fight: a boosted admin kills a hill giant in one or two hits,
            // which ended the opponent step early and left its loot behind (tour 2026-09-29). Real attacks, damage, heals and
            // target switches are proven by `conform hud`; here only the picture matters.
            player.facePawn(npc)
            player.attr[COMBAT_TARGET_FOCUS_ATTR] = java.lang.ref.WeakReference(npc)
            npc.setCurrentLifepoints(npc.getMaximumLifepoints() * 2 / 3)
            fun keepAlive() {
                // It does not fight (no hits either way) and keeps a readable health bar.
                Combat.reset(npc)
                npc.resetInteractions()
                npc.setCurrentLifepoints(npc.getMaximumLifepoints() * 2 / 3)
                player.attr[COMBAT_TARGET_FOCUS_ATTR] = java.lang.ref.WeakReference(npc)
                player.facePawn(npc)
            }
            repeat(4) {
                keepAlive()
                wait(1)
            }
            shot(player, "hud_4_opponent")
            repeat(2) {
                keepAlive()
                wait(1)
            }

            // 5. Ground items (private to the admin) around him, with everything else still on screen.
            fun drop(
                id: Int,
                amount: Int,
                dx: Int,
                dz: Int,
            ) = GroundItem(id, amount, TOUR_SPOT.transform(dx, dz), player).also {
                world.spawn(it)
                items += it
            }
            drop(Items.ABYSSAL_WHIP, 1, -1, 1)
            drop(Items.DRAGON_DAGGER_P_5698, 1, 0, 2)
            repeat(6) { drop(Items.SHARK, 1, -2, 0) }
            drop(Items.COINS_995, 25_000, 1, 2)
            drop(Items.DRAGON_BONES, 1, -1, -1)
            drop(Items.THIRDAGE_PLATEBODY, 1, 2, 1)
            drop(Items.DRAGONFIRE_SHIELD, 1, -2, 2)
            repeat(3) {
                keepAlive()
                wait(1)
            }
            shot(player, "hud_5_everything")
            repeat(2) {
                keepAlive()
                wait(1)
            }

            // 5b. The 78 Plugins panel (what `hud` opens), then Boosts switched off: its panel must go, the rest stays.
            ClientHud.send(player, "open")
            repeat(2) {
                keepAlive()
                wait(1)
            }
            shot(player, "hud_5b_plugins_panel")
            ClientHud.send(player, "open|ground")
            repeat(2) {
                keepAlive()
                wait(1)
            }
            shot(player, "hud_5b2_ground_items_settings")
            ClientHud.send(player, "close")
            ClientHud.send(player, "cfg|0011111|20000,100000,1000000,10000000|ffffff,66b2ff,99ff99,ff9600,ff66b2,aa00ff")
            repeat(2) {
                keepAlive()
                wait(1)
            }
            shot(player, "hud_5c_boosts_switched_off")
            ClientHud.send(player, "cfg|1011111|20000,100000,1000000,10000000|ffffff,66b2ff,99ff99,ff9600,ff66b2,aa00ff")

            // 6. Partial pickup: three of the six sharks go, the label becomes x3.
            items.filter { it.item == Items.SHARK }.take(3).forEach { world.remove(it) }
            repeat(2) {
                keepAlive()
                wait(1)
            }
            shot(player, "hud_6_partial_pickup")
            repeat(2) {
                keepAlive()
                wait(1)
            }

            // 7. Combat stops and the admin teleports away (to the Grand Exchange home, which is never dangerous - a Deadman
            //    player must not be put into a dangerous zone by a test): no opponent panel, no labels of the tour spot.
            Combat.reset(player)
            player.resetInteractions()
            npc.resetInteractions()
            Combat.reset(npc)
            world.remove(npc)
            player.timers.remove(FROZEN_TIMER)
            player.moveTo(SAFE_AWAY)
            wait(8)
            shot(player, "hud_7_teleported_away")
            wait(2)

            // 8. Back, everything removed and restored: nothing of the tour may be left on screen.
            restore()
            player.moveTo(TOUR_SPOT)
            wait(8)
            shot(player, "hud_8_cleaned_up")
            wait(2)
        } finally {
            restore()
            player.moveTo(start)
            player.message("<col=178000>HUD tour done.</col> Screens: C:\\RSPS\\screens\\*_hud_*.png")
        }
    }
    return "HUD tour started for ${player.username}."
}

on_command("hudtour", Privilege.ADMIN_POWER) {
    player.message(startTour(world, player))
}

// The same from the command port (tools\conformance\Send-GameCommand.ps1 hudtour [player]), so an agent can take the shots
// while the owner only keeps the client open.
gg.rsmod.game.CommandServerCommands.handlers["hudtour"] = { w, a ->
    val name = a.joinToString(" ")
    val target =
        if (name.isNotBlank()) {
            w.getPlayerForName(name)
        } else {
            val online = ArrayList<Player>()
            w.players.forEach { online += it }
            online.singleOrNull()
        }
    if (target == null) "hudtour: name the player (one player must be online, or give the name)" else startTour(w, target)
}
