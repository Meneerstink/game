package gg.rsmod.plugins.content.npcs

/*
 * PROJECT48 O02 (run 4): small dead right-click options of spawned npcs (O01 scan) with their OSRS behaviour.
 *  - Doug Deeping "Steal-from" (The Dig Site; OSRS Wiki "Doug Deeping": "will result in him immediately catching and scolding the
 *    player"; lines from "Transcript:Doug Deeping").
 *  - Ali the Camel "Pet" (wiki "Ali the Camel": the player says one of four lines; the camel only answers with a camulet, which
 *    we do not have, so no answer).
 *  - Stray dog "Shoo-away" (wiki "Stray dog": player "Thbbbbt!", dog "Whine!" and runs away). The OSRS "Pet" option is not on
 *    these 667 dog ids (5917/5918 only have Shoo-away), so it is not wired.
 * Plague cow "Pet": the wiki gives no text or effect for it, so it is left unwired (BUGLIJST question).
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
        // Run up to 4 tiles away from the player: the free straight line that points most away from him (a blind diagonal
        // stopped after one tile against the Varrock houses, bot 22-11 18:0x).
        val ax = npc.tile.x - player.tile.x
        val az = npc.tile.z - player.tile.z
        val run =
            Direction.ANGLED_ORDER
                .sortedByDescending { it.getDeltaX() * ax + it.getDeltaZ() * az }
                .map { dir ->
                    var steps = 0
                    var t = npc.tile
                    while (steps < 4 && world.collision.canStep(t, dir)) {
                        t = t.step(dir)
                        steps++
                    }
                    t to steps
                }.firstOrNull { it.second >= 2 }
        if (run != null) {
            // Next tick, in the dog's own queue: the interaction that fired this option holds and turns the npc this tick.
            npc.queue {
                wait(1)
                npc.resetFacePawn()
                npc.walkMask = npc.def.walkMask
                npc.walkTo(run.first, gg.rsmod.game.model.MovementQueue.StepType.FORCED_RUN)
            }
        }
    }
}
