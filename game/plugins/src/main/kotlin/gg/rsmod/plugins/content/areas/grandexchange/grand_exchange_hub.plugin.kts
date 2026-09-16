package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.plugins.content.mechanics.pvp.BankSecurity
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.objs.bank_locs.BankObjects
import gg.rsmod.plugins.content.mechanics.trouver.Trouver
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry

/* GE service hub and bank-side Skully roster. Bank coordinates are taken from the existing
 * revision-667 banker spawn files; chest id 62582 is the already-wired Loot Keys chest. */
val geSkullySites = listOf(
    Triple(3164, 3487, 0), // Grand Exchange
    Triple(3258, 3419, 0), // Varrock east
    Triple(3178, 3439, 0), // Varrock west
    Triple(3094, 3491, 0), // Edgeville
    Triple(2720, 3494, 0), // Camelot
    Triple(3008, 3354, 0), // Falador
    Triple(2943, 3367, 0), // Draynor
    Triple(3265, 3166, 0), // Al Kharid
    Triple(2805, 3443, 0), // Catherby
    Triple(3207, 3222, 0), // Lumbridge
)

geSkullySites.forEach { (x, z, height) ->
    spawn_npc(npc = Npcs.SKULLY, x = x, z = z, height = height, static = true)
    spawn_obj(obj = 62582, x = x + 1, z = z, height = height, type = 10, rot = 0)
}

// GE services requested by the owner. Existing global handlers provide their real functions.
spawn_npc(npc = Npcs.PERDU, x = 3160, z = 3487, direction = Direction.EAST)
spawn_npc(npc = Npcs.BOB, x = 3160, z = 3490, direction = Direction.EAST)
spawn_npc(npc = Npcs.PIKKUPSTIX, x = 3160, z = 3493, direction = Direction.EAST)
spawn_npc(npc = Npcs.KURADAL, x = 3168, z = 3487, direction = Direction.WEST)
spawn_npc(npc = Npcs.MAX, x = 3170, z = 3490, direction = Direction.WEST)
spawn_npc(npc = Npcs.MANDRITH, x = 3170, z = 3493, direction = Direction.WEST)
spawn_npc(npc = Npcs.SIR_TIFFY_CASHIEN, x = 3175, z = 3487, direction = Direction.WEST)
spawn_npc(npc = Npcs.PARTY_PETE, x = 3175, z = 3490, direction = Direction.WEST)
spawn_npc(npc = Npcs.EVIL_DAVE, x = 3175, z = 3493, direction = Direction.WEST)
spawn_npc(npc = Npcs.WISE_OLD_MAN, x = 3180, z = 3487, direction = Direction.WEST)
spawn_npc(npc = Npcs.TOOL_LEPRECHAUN, x = 3180, z = 3490, walkRadius = 8, direction = Direction.WEST)
spawn_npc(npc = Npcs.DRUNKEN_DWARF, x = 3180, z = 3493, walkRadius = 8, direction = Direction.WEST)
spawn_npc(npc = Npcs.PING, x = 3168, z = 3500, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.PONG, x = 3172, z = 3500, direction = Direction.SOUTH)
// The cache's named penguin variants are used; the exact Raktuber colour variant is not renamed
// globally because NpcDef names are shared by every instance of an id.
spawn_npc(npc = Npcs.PENGUIN_5428, x = 3176, z = 3500, walkRadius = 8, direction = Direction.SOUTH)

// A cache-backed obelisk definition with the existing Infuse-pouch/Renew-points handlers.
spawn_obj(obj = 50205, x = 3164, z = 3497, type = 10, rot = 0)

// A dedicated market-guard cache id prevents changing ordinary guards elsewhere in the world.
val bankGuardId = BankSecurity.BANK_GUARD_ID
set_combat_def(bankGuardId) {
    configs {
        // Owner spec (Deadman PvP guards plan, 2026-09-16): 1.2s attack speed = 2 game cycles.
        attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
        respawnDelay = 50
    }
    aggro {
        radius = 8
        searchDelay = 1
        alwaysAggro()
    }
    stats {
        hitpoints = 220
        attack = 17
        strength = 18
        defence = 13
        magic = 1
        ranged = 1
    }
    bonuses {
        attackStab = 9
        attackCrush = 7
        defenceStab = 24
        defenceSlash = 14
        defenceCrush = 19
        defenceMagic = 4
        defenceRanged = 16
    }
    anims {
        attack = Anims.ATTACK_SLASH
        death = Anims.HUMAN_DEATH
        block = Anims.BLOCK_SHIELD
    }
}

/**
 * Ranged guard variant (Deadman PvP guards plan, 2026-09-16). Stats/bonuses mirror the melee
 * guard's power level; anims reuse Anims.ATTACK_CROSSBOW/836/424 - the same real, cache-sourced
 * ranged-human-guard animation set already used by the Falador crossbow guards
 * (`npcs/definitions/humanoids/guard_level_21.plugin.kts`), not invented ids.
 */
set_combat_def(CityGuards.RANGED_GUARD_ID) {
    configs {
        attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
        attackStyle = gg.rsmod.game.model.combat.StyleType.RANGED
        respawnDelay = 50
    }
    aggro {
        radius = 8
        searchDelay = 1
        alwaysAggro()
    }
    stats {
        hitpoints = 220
        attack = 1
        strength = 1
        defence = 13
        magic = 1
        ranged = 18
    }
    bonuses {
        attackRanged = 10
        rangedStrengthBonus = 10
        defenceStab = 24
        defenceSlash = 14
        defenceCrush = 19
        defenceMagic = 4
        defenceRanged = 16
    }
    anims {
        attack = Anims.ATTACK_CROSSBOW
        death = 836
        block = 424
    }
}

/** Wizguard freeze-flavour spawn (Deadman PvP guards plan, 2026-09-16): CityGuards.spawnWizguardFreeze
 * spawns and immediately despawns this NPC in the same tick, so it never actually enters combat -
 * this def exists only so combatDef/aggro lookups elsewhere never see a missing definition. */
set_combat_def(CityGuards.WIZGUARD_ID) {
    configs {
        // NpcCombatBuilder requires attackSpeed even though this npc never actually attacks
        // (spawned and despawned in the same tick) - caught by an actual server boot.
        attackSpeed = CityGuards.ATTACK_SPEED_CYCLES
        respawnDelay = 50
    }
    stats {
        // NpcCombatDsl.stats requires real-HP-times-ten (750 = 75 real hitpoints); a bare 75
        // failed the boot-time validation and crashed plugin loading - caught by an actual
        // server boot, not by compile or unit tests alone.
        hitpoints = 750
        defence = 13
        magic = 20
    }
    bonuses {
        defenceMagic = 10
    }
    anims {
        death = Anims.HUMAN_DEATH
    }
}

// Add one dedicated melee + one dedicated ranged 1337 guard to every real bank cluster in the
// loaded map, not only the ten Skully showcase banks above. A live boot-time diagnostic (M1
// Batch 3, 2026-09-16) confirmed all ten owner-named guarded cities - including the Warrior
// Guild, which turned out to have a real bank chest in this cache at region 11575 (matching
// Donors/Novite's WARRIORS_GUILD tile exactly) - are covered by this generic real-bank-cluster
// scan; no per-city hardcoding needed. Nearby booths/chests are grouped so a large bank does not
// receive a guard pair per individual booth.
on_world_init {
    val guardedBanks = ArrayList<gg.rsmod.game.model.Tile>()

    fun spawnGuardPair(anchor: gg.rsmod.game.model.Tile) {
        val guardTiles =
            Direction.NESW
                .map { anchor.step(it) }
                .filter { !world.collision.isClipped(it) }
        val meleeTile = guardTiles.getOrNull(0) ?: return
        val rangedTile = guardTiles.getOrNull(1) ?: meleeTile
        val melee =
            Npc(bankGuardId, meleeTile, world).also {
                it.respawns = true
                it.walkRadius = 4
            }
        world.spawn(melee)
        melee.setCombatLevel(CityGuards.DISPLAYED_COMBAT_LEVEL)
        val ranged =
            Npc(CityGuards.RANGED_GUARD_ID, rangedTile, world).also {
                it.respawns = true
                it.walkRadius = 4
            }
        world.spawn(ranged)
        ranged.setCombatLevel(CityGuards.DISPLAYED_COMBAT_LEVEL)
    }

    world.chunks.allChunks().forEach { chunk ->
        chunk.getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            .filter { it.id in BankObjects.ALL }
            .forEach { bankObject ->
                if (guardedBanks.any { kotlin.math.abs(it.x - bankObject.tile.x) <= 12 && kotlin.math.abs(it.z - bankObject.tile.z) <= 12 && it.height == bankObject.tile.height }) {
                    return@forEach
                }
                guardedBanks += bankObject.tile
                spawnGuardPair(bankObject.tile)
            }
    }
    println("CityGuards: spawned melee+ranged guard pairs at ${guardedBanks.size} real bank clusters")
}

on_global_npc_spawn {
    if (npc.id == bankGuardId) {
        npc.aggroCheck = { _, player -> BankSecurity.guardMayAttack(player) }
    }
}

on_npc_option(npc = Npcs.PERDU, option = "talk-to") {
    player.queue {
        chatNpc("I can repair and protect eligible equipment. Use a Trouver parchment on an eligible item, then return to me for its repair service.", wrap = true)
    }
}

// Perdu now hosts the generic Trouver engine instead of the old command-only unlock path.
TrouverRegistry.all().forEach { lockable ->
    on_item_on_npc(item = lockable.lockedItemId, npc = Npcs.PERDU) {
        when (Trouver.unlock(player, player.getInteractingItem())) {
            Trouver.UnlockResult.Success -> {}
            Trouver.UnlockResult.NotLocked -> player.message("That item isn't locked.")
            Trouver.UnlockResult.ItemNotHeld -> player.message("You don't have that item.")
            Trouver.UnlockResult.InventoryFull -> {}
        }
    }
}
