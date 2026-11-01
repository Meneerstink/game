package gg.rsmod.plugins.content.areas.wilderness.bosses

/**
 * N522 bundle 2: the OSRS Volcanic Forge on the Wilderness volcano - use an odium or malediction shard on it with all three shards
 * of that kind to forge the ward ([VolcanicForge]).
 */

VolcanicForge.TILES.forEach { t -> spawn_obj(obj = VolcanicForge.FORGE, x = t.x, z = t.z, type = 10, rot = VolcanicForge.ROTATION) }

VolcanicForge.SHARDS.forEach { (shard, ward) ->
    on_item_on_obj(obj = VolcanicForge.FORGE, item = shard) {
        VolcanicForge.forge(player, ward)
    }
}
