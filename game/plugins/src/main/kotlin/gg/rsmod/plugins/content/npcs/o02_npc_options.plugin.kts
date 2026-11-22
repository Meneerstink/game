package gg.rsmod.plugins.content.npcs

/*
 * PROJECT48 O02 (run 4): small dead right-click options of spawned npcs (O01 scan) with their OSRS behaviour.
 *  - Doug Deeping "Steal-from" (The Dig Site; OSRS Wiki "Doug Deeping": "will result in him immediately catching and scolding the
 *    player"; lines from "Transcript:Doug Deeping").
 *  - Ali the Camel "Pet" (wiki "Ali the Camel": the player says one of four lines; the camel only answers with a camulet, which
 *    we do not have, so no answer).
 *  - Stray dog "Shoo-away" (wiki "Stray dog": player "Thbbbbt!", dog "Whine!" and runs away). The OSRS "Pet" option is not on
 *    these 667 dog ids (5917/5918 only have Shoo-away), so it is not wired.
 * Plague cow "Pet" (O22): "Transcript:Plague cow" - the player says one of two lines at random, no effect.
 */

on_npc_option(Npcs.DOUG_DEEPING, "Steal-from") {
    player.queue {
        chatNpc("Hey! Trying to steal from me are you? What do you think I am, stupid or something?!", facialExpression = FacialExpression.ANGRY)
        chatPlayer("Err... Sorry.", facialExpression = FacialExpression.SAD)
    }
}

val ALI_PET_LINES =
    listOf(
        "That beast would probably bite my fingers off if I tried to pet it.",
        "I'm not going to pet that! It will probably bite my hand off, that filthy beast.",
        "Mmmm... you'd make a tasty kebab.",
        "I'm not going to pet that nasty creature! I might get fleas... or worse!",
    )

on_npc_option(Npcs.ALI_THE_CAMEL, "Pet") {
    player.message(ALI_PET_LINES.random())
}

listOf(Npcs.STRAY_DOG_5917, Npcs.STRAY_DOG_5918).forEach { dog ->
    on_npc_option(dog, "Shoo-away") {
        val npc = player.getInteractingNpc()
        player.forceChat("Thbbbbt!")
        npc.forceChat("Whine!")
        // Run up to 4 tiles straight away from the player: only directions that do not point toward him (dot >= 0), the longest
        // free line first, the most "away" one on a tie. The steps are queued one by one along that line: walkTo planned a route
        // through the player's own tile and the npc entity-block refused it (bot 22-11 19:07, breachtrace step.refused), and a
        // longer line that pointed back at him used to win over a short one pointing away.
        val ax = npc.tile.x - player.tile.x
        val az = npc.tile.z - player.tile.z
        val line =
            Direction.ANGLED_ORDER
                .filter { it.getDeltaX() * ax + it.getDeltaZ() * az >= 0 }
                .map { dir ->
                    val tiles = mutableListOf<Tile>()
                    var t = npc.tile
                    while (tiles.size < 4 && world.collision.canStep(t, dir) && !t.step(dir).sameAs(player.tile)) {
                        t = t.step(dir)
                        tiles += t
                    }
                    Triple(dir, tiles, dir.getDeltaX() * ax + dir.getDeltaZ() * az)
                }.filter { it.second.isNotEmpty() }
                .maxWithOrNull(compareBy<Triple<Direction, MutableList<Tile>, Int>>({ minOf(it.second.size, 2) }, { it.third }))
        if (line != null) {
            // Next tick, in the world queue: the interaction that fired this option stops and turns the npc this tick (the dog's own
            // queue never ran the block, bot 22-11 18:2x p170).
            world.queue {
                wait(1)
                npc.resetFacePawn()
                npc.movementQueue.clear()
                line.second.forEach { npc.movementQueue.addStep(it, gg.rsmod.game.model.MovementQueue.StepType.FORCED_RUN, true) }
            }
        }
    }
}

val PLAGUE_COW_PET_LINES =
    listOf(
        "Awww, look at the poor sick cow. We'd never make a burger out of you.",
        "Don't worry cow, I'm sure somebody will find a cure for this plague soon.",
    )

listOf(Npcs.PLAGUE_COW, Npcs.PLAGUE_COW_1999, Npcs.PLAGUE_COW_2000).forEach { cow ->
    on_npc_option(cow, "Pet") {
        player.queue { chatPlayer(PLAGUE_COW_PET_LINES.random()) }
    }
}
