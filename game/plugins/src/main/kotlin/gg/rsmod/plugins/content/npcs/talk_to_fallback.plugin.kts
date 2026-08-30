package gg.rsmod.plugins.content.npcs

import gg.rsmod.game.fs.def.NpcDef

/**
 * R04.5: the census (R04.2) shows 1216 currently-spawned npc types advertise "Talk-to" in the
 * cache with nothing bound to it at all - the client shows "Nothing interesting happens.",
 * which is the exact Talk-to failure the owner reported, not an isolated case. Real per-npc
 * dialogue (like `man_chat.plugin.kts`) always wins: this runs in `on_world_init`, which fires
 * after every plugin script's top-level body (including every `on_npc_option(..., "talk-to")`
 * call) has already registered, so this only fills npcs nobody has written dialogue for yet.
 * It only ever binds the Talk-to slot - Trade/Bank/Collect/etc. are untouched, so a real
 * service npc missing ITS actual service is still correctly reported as unimplemented, not
 * masked by this. "Suitable flavour dialogue is fine, generic greetings replacing services are
 * not" - this is flavour only, npcs that also need a real service still need that service
 * wired separately.
 */
val genericGreetings =
    listOf(
        "Hello there.",
        "Can't stop to chat, I'm quite busy.",
        "Nice day, isn't it?",
        "Good day to you.",
        "...yes? Can I help you?",
        "Hmm? Oh, hello.",
    )

on_world_init {
    var bound = 0
    world.definitions.getAllKeys(NpcDef::class.java).forEach { id ->
        val def = world.definitions.get(NpcDef::class.java, id)
        val slot = def.options.indexOfFirst { it?.lowercase() == "talk-to" }
        if (slot != -1 && (slot + 1) !in world.plugins.boundNpcOptions(id)) {
            on_npc_option(npc = id, option = "talk-to") {
                player.queue { chatNpc(genericGreetings.random()) }
            }
            bound++
        }
    }
    println("R04.5 talk_to_fallback: bound a generic greeting to $bound npcs with no real dialogue.")
}
