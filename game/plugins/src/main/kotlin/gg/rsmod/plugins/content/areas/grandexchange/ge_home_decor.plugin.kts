package gg.rsmod.plugins.content.areas.grandexchange

/*
 * The Grand Exchange Home already has an architectural south gatehouse, but it visually read as three anonymous
 * booths. This source-backed dressing makes the gameplay boundary legible without replacing the map or narrowing its
 * two walkable approaches: warning signs face players while they are still in the dangerous strip; matching Varrock
 * statues, animated standing torches and Grand Exchange banners frame the first safe tiles.
 */
GeHomeDecor.SOUTH_GATE.forEach { placement ->
    spawn_obj(
        obj = placement.objectId,
        x = placement.tile.x,
        z = placement.tile.z,
        height = placement.tile.height,
        type = placement.type,
        rot = placement.rotation,
    )
}
