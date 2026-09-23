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

/** Options that are a service the npc provides; the Talk-to fallback hands the player to it. */
val SERVICE_OPTIONS = setOf("trade", "shop", "trade-with", "open-store", "buy", "bank", "collect", "exchange", "sets")

val SERVICE_LINES =
    mapOf(
        "trade" to "Let's see what you have for sale.",
        "shop" to "Let's see what you have for sale.",
        "trade-with" to "Let's see what you have for sale.",
        "open-store" to "Let's see what you have for sale.",
        "buy" to "I'd like to buy something.",
        "bank" to "I'd like to access my bank account, please.",
        "collect" to "I'd like to collect my items.",
        "exchange" to "I'd like to make an exchange.",
        "sets" to "I'd like to see the item sets.",
    )

val SERVICE_GREETINGS =
    listOf(
        "Hello there. What can I do for you?",
        "Good day. How can I help you?",
        "Welcome! Anything I can help you with?",
    )

on_world_init {
    // Owner 2026-09-23 ("a lot of npcs just say 1 thing then stop ... fix this for every NPC"): an npc with no hand-written
    // dialogue plays its real sourced conversation (TranscriptDialogue); the lines below are only for npcs no transcript covers.
    val transcripts = TranscriptDialogue.load()
    var bound = 0
    world.definitions.getAllKeys(NpcDef::class.java).forEach { id ->
        val def = world.definitions.get(NpcDef::class.java, id)
        val slot = def.options.indexOfFirst { it?.lowercase() == "talk-to" }
        if (slot != -1 && (slot + 1) !in world.plugins.boundNpcOptions(id)) {
            // A service npc (shopkeeper, banker, ...) never answers with a greeting and a dead end: the fallback
            // offers the npc's own bound service, so Talk-to and the service option lead to the same place.
            val service =
                def.options.withIndex().firstOrNull { (i, o) -> o != null && o.lowercase() in SERVICE_OPTIONS && i != slot }
            on_npc_option(npc = id, option = "talk-to") {
                val bound = service != null && (service.index + 1) in world.plugins.boundNpcOptions(id)
                player.queue {
                    if (ServiceDialogues.play(this, player.getInteractingNpc())) return@queue
                    if (TranscriptDialogue.play(this, player.getInteractingNpc())) return@queue
                    if (!bound) {
                        chatNpc(genericGreetings.random())
                        return@queue
                    }
                    chatNpc(SERVICE_GREETINGS.random())
                    val option = service!!.value!!.lowercase()
                    if (options(SERVICE_LINES[option] ?: "I'd like to $option.", "Nothing, thanks.") == FIRST_OPTION) {
                        world.plugins.executeNpc(player, id, service.index + 1)
                    }
                }
            }
            bound++
        }
    }
    println("R04.5 talk_to_fallback: $bound npcs without hand-written dialogue; $transcripts sourced transcript conversations loaded.")
}
