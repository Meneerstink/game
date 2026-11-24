package gg.rsmod.plugins.content.areas.morytania

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.items.food.Food

/*
 * GHAST-POUCH (SIGILS-QUESTS-REST, owner 24-11 20:1x): the Mort Myre ghasts were only spawned - the invisible ghast 1052 had no combat
 * definition, so it never attacked, and the Void attack row (npc-attacks.json 1052: skip 20 / miss 7 / rot 3 / energy 3 / reveal 3)
 * never ran because its conditions were not ported.
 * Sources:
 *  - OSRS Wiki "Ghast": 7/10 a complete miss, 3/10 a rot attack with food in the inventory or 1-3 damage without (Mod Ash); a filled
 *    druid pouch makes the ghast visible and attackable (the visible ghast = our 1053, OSRS 946, combat 30); ghasts cannot rot drinks,
 *    healing potions or raw food that cannot be eaten (only Food entries rot here).
 *  - Transcript:Nature Spirit: "The druid pouch makes the ghast visible." / "You have nothing left in your druid pouch!".
 *  - Owner 24-11 20:1x + Void row: "You feel something attacking your backpack, and smell a terrible stench."; the Void row keeps its
 *    other lines, says, anim 9466 and sounds ("An attacking Ghast just misses you.", "A supernatural force draws energy from you.").
 *  - Druid pouch charges: druid_pouch_charges (o10_item_options.plugin.kts, Fill = 1 charge per blossomed item).
 * NO SOURCE (kept minimal): the visible ghast leaves again when its player is gone (15+ tiles / logged out) or after 100 ticks out of
 * combat; the invisible ghast it came from is out of the world while it lives (see [reveal]).
 */

val GHAST = Npcs.GHAST
val VISIBLE_GHAST = Npcs.GHAST_1053
val POUCH_CHARGES = AttributeKey<Int>("druid_pouch_charges")
val ROTTABLE = Food.values.map { it.item }.toSet()

fun charges(p: Player) = p.attr[POUCH_CHARGES] ?: 0
fun hasFilledPouch(p: Player) = p.inventory.contains(Items.DRUID_POUCH_2958) && charges(p) > 0
fun rottableSlot(p: Player): Int? = (0 until p.inventory.capacity).filter { p.inventory[it]?.id in ROTTABLE }.randomOrNull()

set_combat_def(GHAST) {
    configs {
        attackSpeed = 4
        respawnDelay = 60
    }
    stats {
        hitpoints = 450
        attack = 22
        strength = 22
        defence = 18
    }
    // The invisible ghast cannot be attacked, so it never dies; its swing animation is the Void row's 9466.
    anims {
        attack = 9466
        block = -1
        death = -1
    }
    aggro {
        radius = 4
        alwaysAggro()
    }
}

// Void npcCondition registrations for the ghast row (has_druid_pouch / has_no_druid_pouch / has_food / has_no_food).
NpcAttacks.condition("has_druid_pouch") { _, t -> t is Player && hasFilledPouch(t) }
NpcAttacks.condition("has_no_druid_pouch") { _, t -> t is Player && !hasFilledPouch(t) }
NpcAttacks.condition("has_food") { _, t -> t is Player && !hasFilledPouch(t) && rottableSlot(t) != null }
NpcAttacks.condition("has_no_food") { _, t -> t is Player && !hasFilledPouch(t) && rottableSlot(t) == null }

// The Void energy section hits 10-20 (1-2 hp); the wiki (Mod Ash) says 1-3 - the same section with max 30.
// In on_world_init: the attack table is loaded by npc_attacks.plugin.kts, whose load order is not fixed.
on_world_init { NpcAttacks.rowFor(GHAST)?.let { row ->
    NpcAttacks.register(
        NpcAttacks.Row(
            id = row.id, name = row.name, voidKey = row.voidKey, combatDef = row.combatDef, height = row.height, attackRange = row.attackRange,
            attacks = row.attacks.map { a ->
                if (a.id != "energy") a else NpcAttacks.Attack(
                    id = a.id, chance = a.chance, range = a.range, condition = a.condition, say = a.say, anim = a.anim,
                    targetSounds = a.targetSounds, impactRegardless = a.impactRegardless, message = a.message,
                    hits = listOf(NpcAttacks.HitDef(offense = "damage", defence = "damage", min = 10, max = 30, accuracyRoll = false)),
                )
            },
        ),
    )
} }

NpcAttacks.onImpact("ghast", "rot") { _, t ->
    val p = t as? Player ?: return@onImpact false
    val slot = rottableSlot(p) ?: return@onImpact false
    val food = p.inventory[slot] ?: return@onImpact false
    if (p.inventory.remove(food.id, 1, beginSlot = slot).hasSucceeded()) p.inventory.add(Items.ROTTEN_FOOD, 1, beginSlot = slot)
    true // the row's message follows
}

NpcAttacks.onImpact("ghast", "reveal") { npc, t ->
    val p = t as? Player ?: return@onImpact false
    if (!hasFilledPouch(p)) return@onImpact false
    val left = charges(p) - 1
    p.attr[POUCH_CHARGES] = left
    p.message("The druid pouch makes the ghast visible.")
    if (left <= 0) {
        val slot = p.inventory.getItemIndex(Items.DRUID_POUCH_2958, skipAttrItems = false)
        if (slot != -1 && p.inventory.remove(Items.DRUID_POUCH_2958, beginSlot = slot).hasSucceeded()) p.inventory.add(Items.DRUID_POUCH, beginSlot = slot)
        p.message("You have nothing left in your druid pouch!")
    }
    reveal(npc, p)
    false
}

/**
 * Mort Myre is single-combat: while the invisible ghast keeps the player as its target, neither the player nor the visible ghast can
 * attack ("I'm already under attack!", testserver 24-11 20:2x). So the invisible ghast leaves the world while its visible form lives
 * and comes back on its own spawn after the respawn delay (60 ticks, its combat definition) once the visible one is gone.
 */
fun reveal(ghast: Npc, p: Player) {
    val tile = ghast.tile
    val home = ghast.spawnTile
    val radius = ghast.walkRadius
    world.remove(ghast)
    val visible = Npc(VISIBLE_GHAST, tile, world)
    visible.respawns = false
    world.spawn(visible)
    world.queue {
        // OSRS Wiki Ghast infobox: the level-30 ghast (946) is not aggressive - the player attacks it or walks past.
        var idle = 0
        while (visible.isActive()) {
            wait(5)
            idle = if (visible.timers.has(ACTIVE_COMBAT_TIMER)) 0 else idle + 5
            if (!p.isOnline || p.tile.getDistance(visible.tile) > 15 || idle >= 100) {
                if (visible.isActive()) world.remove(visible)
                break
            }
        }
        wait(60)
        val back = Npc(GHAST, home, world)
        back.walkRadius = radius
        back.respawns = true
        world.spawn(back)
    }
}
