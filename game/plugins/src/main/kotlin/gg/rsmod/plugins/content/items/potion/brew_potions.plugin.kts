package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.attr.AGGRESSOR
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.content.combat.*
import java.lang.ref.WeakReference

/**
 * OSRS-IMPORT potions-brews timers and the Forgotten brew recipe (rules and sources in [BrewPotions]). ADAPTED: recipe messages.
 */

on_timer(BrewPotions.MENAPHITE_TIMER) {
    if (BrewPotions.menaphiteTick(player)) player.timers[BrewPotions.MENAPHITE_TIMER] = BrewPotions.MENAPHITE_INTERVAL
}

on_timer(BrewPotions.PRAYER_REGEN_TIMER) {
    if (BrewPotions.prayerRegenerationTick(player)) player.timers[BrewPotions.PRAYER_REGEN_TIMER] = BrewPotions.PRAYER_REGEN_INTERVAL
}

on_timer(BrewPotions.SURGE_COOLDOWN) {
    player.message(BrewPotions.SURGE_READY_MESSAGE)
}

/*
 * Goading potion (OSRS Wiki): "any non-aggressive monsters within line of sight and a 9x9 tiled area around the player" gain aggression;
 * "a cycle of 6 ticks (3.6 seconds) starting one tick after drinking"; "In single-way combat, only one (non-aggressive) monster will start
 * targeting the player once they are out of combat"; it "does not affect monsters who are already targeting another player or entity, nor
 * ... monsters the player does not have the Slayer level to kill".
 */
on_timer(BrewPotions.GOADING_TIMER) {
    if (!BrewPotions.goadingTick(player)) return@on_timer
    player.timers[BrewPotions.GOADING_TIMER] = BrewPotions.GOADING_INTERVAL
    if (player.isDead() || !player.isOnline) return@on_timer
    val multi = player.tile.isMulti(world)
    if (!multi && (player.timers.has(ACTIVE_COMBAT_TIMER) || player.getAggressor() != null)) return@on_timer
    val radius = BrewPotions.GOADING_RADIUS
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            val tile = player.tile.transform(x, z)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
            for (npc in chunk.getEntities<Npc>(tile, EntityType.NPC)) {
                if (npc.timers.has(ACTIVE_COMBAT_TIMER) || npc.getCombatTarget() != null || !npc.lock.canAttack()) continue
                if (npc.aggroCheck?.invoke(npc, player) == true) continue
                if (npc.combatDef.slayerReq > player.skills.getMaxLevel(Skills.SLAYER)) continue
                if (npc.tile.height != player.tile.height || !world.collision.raycast(npc.tile, player.tile, projectile = true)) continue
                if (!npc.canEngageCombat(player)) continue
                if (player.getAggressor() == null) player.attr[AGGRESSOR] = WeakReference(npc)
                npc.attack(player)
                if (!multi) return@on_timer
            }
        }
    }
}

// "A forgotten brew is a potion made by using 20 ancient essence per dose on an ancient brew, requiring 91 Herblore" (Recipe templates).
val ancientToForgotten =
    mapOf(
        Items.ANCIENT_BREW_4 to Pair(Items.FORGOTTEN_BREW_4, 4),
        Items.ANCIENT_BREW_3 to Pair(Items.FORGOTTEN_BREW_3, 3),
        Items.ANCIENT_BREW_2 to Pair(Items.FORGOTTEN_BREW_2, 2),
        Items.ANCIENT_BREW_1 to Pair(Items.FORGOTTEN_BREW_1, 1),
    )

ancientToForgotten.forEach { (ancient, product) ->
    val (forgotten, doses) = product
    on_item_on_item(item1 = Items.ANCIENT_ESSENCE, item2 = ancient) {
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < 91) {
            player.message("You need a Herblore level of 91 to make a forgotten brew.")
            return@on_item_on_item
        }
        val essence = 20 * doses
        if (player.inventory.getItemCount(Items.ANCIENT_ESSENCE) < essence) {
            player.message("You need $essence ancient essence to do that.")
            return@on_item_on_item
        }
        if (!player.inventory.contains(ancient)) return@on_item_on_item
        player.inventory.remove(Items.ANCIENT_ESSENCE, essence)
        player.inventory.remove(ancient, 1)
        player.inventory.add(forgotten, 1)
        player.addXp(Skills.HERBLORE, BrewPotions.FORGOTTEN_BREW_EXPERIENCE.getValue(doses))
    }
}
