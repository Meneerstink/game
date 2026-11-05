package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.combat.isBeingAttacked

val WYRM_RISING = AttributeKey<Boolean>()

/**
 * WildyWyrm (3334).
 *
 * Spawn: Novite npcspawns.json places it at 3093,10123 (Forinthry Dungeon, Wilderness level 26 underground).
 * Combat definition: lifepoints 10,000, strength 715, anims 12791 / 12792 / 12793, attack speed 4, death
 * delay 5, respawn 60 from the Matrix-sourced bulk row; attack bonuses 500 and defence bonuses 300 from
 * Novite npcbonuses.json.
 *
 * PROVISIONAL (not source data): neither donor carries attack, ranged or magic levels for this npc - Matrix
 * rolls npc accuracy from bonuses alone - so the three offensive levels reuse the row's strength level so
 * that the target's level-based accuracy formulas give the boss a meaningful roll. Replace when a
 * revision-667 level source is found.
 *
 * Attack logic: [gg.rsmod.plugins.content.combat.scripts.impl.WildyWyrmCombatScript].
 */
/*
 * N527 (owner 2026-11-05 12:5x choice A): the WildyWyrm lies under a Mound (2417, 'Investigate') on the surface, on the twelve real RS
 * spawn tiles of npc 2417 (Darkan developer-information/shaunySpawns.json, plane 0, Wilderness levels 35-40 west of the Lava Maze).
 * RS Wiki 'Update:Attack of the WildyWyrms!': players stomp on its hiding place and the wyrm comes up. The stomp follows the donor
 * mound pattern (Novite Strykewyrm.handleStomping / processNPC, the same 2011 strykewyrm mounds): player anim 4278 and a 2-tick lock,
 * one tick later the mound plays 12795 and becomes the wyrm, which targets the stomper the tick after; a wyrm out of combat sinks back
 * (anim 12796) and is a mound again. After a kill the mound returns on its tile after the wyrm's respawn delay (60 ticks).
 */
val WYRM_MOUND_TILES =
    listOf(
        3022 to 3822, 3022 to 3832, 3030 to 3826, 3033 to 3822, 3034 to 3832, 3039 to 3818,
        3047 to 3812, 3051 to 3806, 3052 to 3798, 3053 to 3792, 3059 to 3797, 3061 to 3802,
    )
val WYRM_STOMP_ANIM = 4278
val WYRM_EMERGE_ANIM = 12795
val WYRM_SINK_ANIM = 12796
val WYRM_MOUND_RESPAWN = 60
/** Ticks without combat before a risen wyrm sinks back (Novite: as soon as it is no longer 'under combat'). */
val WYRM_IDLE_SINK = 10

WYRM_MOUND_TILES.forEach { (x, z) -> spawn_npc(npc = Npcs.MOUND, x = x, z = z, height = 0, walkRadius = 0, direction = Direction.SOUTH, static = true) }

fun spawnMound(world: gg.rsmod.game.model.World, tile: Tile) {
    world.spawn(Npc(Npcs.MOUND, tile, world).also { it.respawns = false })
}

on_npc_option(npc = Npcs.MOUND, option = "investigate") {
    val mound = player.getInteractingNpc()
    if (mound.attr.has(WYRM_RISING)) return@on_npc_option
    mound.attr[WYRM_RISING] = true
    player.animate(WYRM_STOMP_ANIM)
    player.lock = gg.rsmod.game.model.LockState.DELAY_ACTIONS
    val stomper = player
    world.queue {
        wait(1)
        stomper.unlock()
        val tile = mound.tile
        world.remove(mound)
        val wyrm = Npc(Npcs.WILDYWYRM, tile, world).also { it.respawns = false }
        world.spawn(wyrm)
        wyrm.animate(WYRM_EMERGE_ANIM)
        wait(1)
        if (stomper.isOnline && !stomper.isDead()) wyrm.attack(stomper)
        // Out of combat for WYRM_IDLE_SINK ticks: back under the ground as a mound.
        var idle = 0
        while (wyrm.isSpawned() && !wyrm.isDead()) {
            wait(1)
            idle = if (wyrm.isBeingAttacked() || wyrm.isAttacking()) 0 else idle + 1
            if (idle >= WYRM_IDLE_SINK) {
                wyrm.animate(WYRM_SINK_ANIM)
                wait(2)
                if (wyrm.isSpawned() && !wyrm.isDead()) {
                    world.remove(wyrm)
                    spawnMound(world, tile)
                }
                return@queue
            }
        }
    }
}

on_npc_death(Npcs.WILDYWYRM) {
    val home = npc.spawnTile
    world.queue {
        wait(WYRM_MOUND_RESPAWN)
        spawnMound(world, home)
    }
}


set_combat_def(npc = Npcs.WILDYWYRM) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.RANGED
        respawnDelay = 60
        deathDelay = 5
    }
    stats {
        hitpoints = 10000
        attack = 715
        strength = 715
        defence = 1
        magic = 715
        ranged = 715
    }
    bonuses {
        attackStab = 500
        attackSlash = 500
        attackCrush = 500
        attackMagic = 500
        attackRanged = 500
        defenceStab = 300
        defenceSlash = 300
        defenceCrush = 300
        defenceMagic = 300
        defenceRanged = 300
    }
    anims {
        attack = 12791
        block = 12792
        death = 12793
    }
    aggro {
        radius = 8
    }
}
