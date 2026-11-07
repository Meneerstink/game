package gg.rsmod.plugins.content.activity.toa.het

/**
 * N462 Path of Het layouts. Owner question N462-HET (advice A, owner 07-11 01:3x 'alles wat je nodig hebt'): ONLY layouts proven by an
 * OSRS image - the server-side layout list is not public. Each layout is read tile by tile from the screenshot under [HetLayout.source]
 * with a projected OSRS tile grid (work: scratchpad het/grid.py, calibrated on the cache placements of the path barriers x3669/x3691
 * and the north pickaxe statue 45468 at 3679,5291), so every tile below is ONZEKER +-1 until compared in the test client.
 *
 * Fixed scenery (cache2686 square 14674): path barriers x3669 / x3691 (z5279-5281), entry from the east (ToaPath.HET 3697,5280), north and
 * south alcove statues 45468 at 3679,5291 / 3679,5267 behind fences z5290 / z5270.
 * Statues: Caster statue (Wiki map 3677,5281) west of the seal, Shielded statue (Wiki map 3684,5281) east of it, Het's Seal (Wiki map
 * 3681,5281); in the screenshot the three 3x3 objects are centred on z5280 at x3677 / x3680 / x3683 (room centre x3680 = between the
 * path barriers; the Wiki map pins sit one tile further east for the seal and the target) - ONZEKER T23, the screenshot is followed.
 *
 * Mirrors: the screenshot shows the SOLVED state (beam caster -> west mirror -> north-west -> north-east -> down onto the shielded
 * statue). Their start positions are not in any source (T23): they start on their solved tiles with the wrong rotation, so the party
 * must rotate (or pick up / place / push) them - the rest of the room is exactly the screenshot.
 */
object HetLayouts {
    private fun h(x1: Int, x2: Int, z: Int, worn: Boolean) = HetWall(HetPos(x1, z), HetPos(x2, z), worn)
    private fun v(x: Int, z1: Int, z2: Int, worn: Boolean) = HetWall(HetPos(x, z1), HetPos(x, z2), worn)

    /** OSRS Wiki File:Path_of_Het_-_energy_beam.png (Het's Seal / Caster statue / Shielded statue / Strategies, 1200x850). */
    val ENERGY_BEAM = HetLayout(
        name = "energy-beam",
        source = "https://oldschool.runescape.wiki/images/Path_of_Het_-_energy_beam.png",
        caster = HetFootprint(HetPos(3676, 5279)),
        casterFacing = HetDir.WEST,
        target = HetFootprint(HetPos(3682, 5279)),
        seal = HetFootprint(HetPos(3679, 5279)),
        walls = listOf(
            h(3676, 3680, 5285, worn = false), // smooth, north-west of the seal
            v(3682, 5282, 5286, worn = false), // smooth column north-east of the seal
            h(3674, 3678, 5282, worn = true),  // worn, just north of the caster
            h(3685, 3689, 5284, worn = true),  // worn, east
            h(3682, 3687, 5278, worn = false), // smooth, south-east of the seal
            v(3678, 5272, 5277, worn = false), // smooth column south of the caster (runs off the screenshot: south end ONZEKER)
            h(3671, 3675, 5277, worn = true),  // worn, south-west
            h(3680, 3685, 5275, worn = false), // smooth, south
        ),
        mirrors = listOf(
            // Solved: west 3673,5280 travel W->N; north-west 3673,5287 travel N->E; north-east 3684,5287 travel E->S (onto the target).
            HetMirrorSpec(HetPos(3673, 5280), rotation = 2, movable = true),
            HetMirrorSpec(HetPos(3673, 5287), rotation = 3, movable = true),
            HetMirrorSpec(HetPos(3684, 5287), rotation = 3, movable = true),
            // Fixed mirrors in the screenshot not on the beam: on the east wall and in the south-east corner (rotation unseen).
            HetMirrorSpec(HetPos(3690, 5286), rotation = 2, movable = false, dirtyInGroups = true),
            HetMirrorSpec(HetPos(3688, 5276), rotation = 3, movable = false, dirtyInGroups = true),
        ),
        minX = 3670, maxX = 3690, minZ = 5271, maxZ = 5289,
    )

    /** The solved rotations of [ENERGY_BEAM] (test + documentation). */
    val ENERGY_BEAM_SOLUTION = mapOf(HetPos(3673, 5280) to 0, HetPos(3673, 5287) to 1, HetPos(3684, 5287) to 2)

    val ALL = listOf(ENERGY_BEAM)
}
