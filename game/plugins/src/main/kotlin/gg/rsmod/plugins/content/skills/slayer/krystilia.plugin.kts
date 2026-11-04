package gg.rsmod.plugins.content.skills.slayer

// Resolve Claude's cache import by its OSRS name. Never bind 7663: that is Fistandantilus in 667.
val krystiliaId = Krystilia.npcId(world)
if (krystiliaId == null) {
    println("[N541] PROBLEM: Krystilia OSRS npc 7663 is not imported; import it, preserving Talk-to/Assignment/Trade/Rewards.")
} else {
    spawn_npc(npc = krystiliaId, x = Krystilia.TILE.x, z = Krystilia.TILE.z, walkRadius = 0)
    on_npc_option(npc = krystiliaId, option = "talk-to") { KrystiliaDialogue.open(player) }
    on_npc_option(npc = krystiliaId, option = "assignment") {
        player.queue { KrystiliaDialogue.assignment(this, krystiliaId) }
    }
}
