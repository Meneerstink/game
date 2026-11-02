package gg.rsmod.plugins.content.areas.wilderness.bosses

/**
 * N522 bundle 3: the ways in and out of the OSRS Revenant Caves and its agility pillars ([RevenantCaves]).
 */

// The surface caverns and exit trapdoors are not on the rev-667 surface: the server places them on their OSRS tiles.
RevenantCaves.SURFACE.forEach { p -> spawn_obj(obj = p.id, x = p.tile.x, z = p.tile.z, type = 10, rot = p.rot) }

RevenantCaves.ENTRANCES.forEach { (cavern, arrival) ->
    on_obj_option(obj = cavern, option = "enter") {
        // N522: the OSRS entrance fee (100,000 coins, from the bank if need be) before the crawl - [RevenantCaveFee].
        RevenantCaveFee.enter(player) { RevenantCaves.crawl(player, arrival, "You enter the cave.") }
    }
}

RevenantCaves.EXITS.forEach { (stairs, exit) ->
    on_obj_option(obj = stairs, option = "climb-up") {
        RevenantCaves.crawl(player, exit, "You exit the cave.")
    }
}

// The tiles beside a pillar are blocked: the jump starts two tiles away (Kronos skipReachCheck).
on_obj_option(obj = RevenantCaves.PILLAR, option = "jump-to", lineOfSightDistance = 2) {
    val pillar = RevenantCaves.pillarAt(player.getInteractingGameObj().tile) ?: return@on_obj_option
    RevenantCaves.jump(player, pillar)
}
