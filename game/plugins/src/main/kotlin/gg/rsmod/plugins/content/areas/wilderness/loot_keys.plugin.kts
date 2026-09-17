package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.content.inter.bank.BankPin
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.content.mechanics.pvp.BEST_KILLSTREAK_ATTR
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys

/**
 * RCV-012 decision 3b: Skully, the Loot Chest (loc 62582 at 3138,3626) and the loot key items. Dialogue is the OSRS Wiki Skully
 * transcript verbatim; see [LootKeys] for the rules, ADAPTED parts and gaps. "With no player kills in the wilderness" is read as a best
 * killstreak of 0 (this server's only per-player Wilderness kill record).
 */

val LOOT_CHEST = 62582

// ---- Skully (and his renamed brothers, see mechanics/pvp/SkullyRoster) ----
gg.rsmod.plugins.content.mechanics.pvp.SkullyRoster.NPC_IDS.forEach { skullyId ->
    on_npc_option(npc = skullyId, option = "talk-to") {
        player.queue { skully(this) }
    }

    on_npc_option(npc = skullyId, option = "value") {
        player.queue { skullyClaimed(this, thenOptions = false) }
    }

    on_npc_option(npc = skullyId, option = "settings") {
        player.queue { skullySettings(this) }
    }

    // Deadman (owner 2026-09-17: "lootkeys can be opened at skully"): a key used on any Skully opens it.
    LootKeys.KEY_IDS.forEachIndexed { index, key ->
        on_item_on_npc(item = key, npc = skullyId) {
            openLootChest(player, index)
        }
    }
}

/** Opens the first loot key the player carries (or the first stored loot), the same as the Loot Chest. */
fun openFirstKey(player: Player): Boolean {
    val held = LootKeys.heldKeyIndexes(player)
    val index = held.firstOrNull() ?: LootKeys.KEY_IDS.indices.firstOrNull { LootKeys.slotItems(player, it).isNotEmpty() }
    if (index == null) {
        return false
    }
    openLootChest(player, index)
    return true
}

suspend fun skully(it: QueueTask) {
    it.chatNpc("Eyup. They call me Skully. I open loot keys. What can I do for you?", wrap = true)
    skullyOptions(it)
}

suspend fun skullyOptions(it: QueueTask) {
    when (it.options("Open a loot key.", "How do loot keys work?", "Can I change how these loot keys work?", "How much loot have I claimed?", "Goodbye.")) {
        1 -> {
            it.chatPlayer("I'd like to open a loot key.", wrap = true)
            if (!openFirstKey(it.player)) {
                it.chatNpc("You haven't got any loot keys on you. Come back when you've killed someone out there.", wrap = true)
            }
        }
        2 -> skullyHowItWorks(it)
        3 -> skullyChangeKeys(it)
        4 -> skullyClaimed(it, thenOptions = true)
        5 -> {
            it.chatPlayer("Goodbye.", wrap = true)
            it.chatNpc("I'll be here if you need anything.", wrap = true)
        }
    }
}

suspend fun skullyHowItWorks(it: QueueTask) {
    it.chatNpc(
        "Well, you know how when you kill someone all of their stuff just ends up in a pile on the floor and it's really hard to pick it all up quickly?",
        wrap = true,
    )
    if ((it.player.attr[BEST_KILLSTREAK_ATTR] ?: 0) <= 0) {
        it.chatPlayer("Actually, I've never killed anyone else.", wrap = true)
        it.chatNpc("...Oh. Well, when you do, all of their stuff just ends up in a pile on the floor and it's really hard to pick it all up quickly.", wrap = true)
    } else {
        it.chatPlayer("Yeah?", wrap = true)
    }
    it.chatNpc("So, I made an enchantment that teleports the loot to another dimension instead!", wrap = true)
    it.chatPlayer("How does that help?", wrap = true)
    it.chatNpc("Well, instead of getting a pile of rubbish, you get a single key. You just take the key to this chest and BAM! There's your loot!", wrap = true)
    it.chatPlayer("So the loot is safe in this other dimension?", wrap = true)
    it.chatNpc(
        "Eh, seems like it. As long as they've had the key, I've never had anyone unable to get the stuff back out. Though if you lose the key, you can't get the stuff out.",
        wrap = true,
    )
    it.chatNpc(
        "Oh, and be careful running about with too many keys on you. Other people will know you've got them, and I'm not gonna stop them opening the chest!",
        wrap = true,
    )
    it.chatPlayer("So if I kill someone and take their keys, I can use them myself?", wrap = true)
    it.chatNpc("...Eh, if you wanna think about it that way, sure.", wrap = true)
    it.chatNpc("I got nothing better to do, so I'll keep track of the value of the stuff you unlock.", wrap = true)
    it.chatNpc("And anything you don't want, you can have the chest destroy it.", wrap = true)
    it.chatNpc("Except the food. I'll eat that instead.", wrap = true)
    it.chatNpc("Every kill you make out there puts the loot in a key for you - one kill, one key, up to five. Bring them to me and I'll open them.", wrap = true)
    skullyOptions(it)
}

suspend fun skullyChangeKeys(it: QueueTask) {
    it.chatPlayer("Can I change how these loot keys work?", wrap = true)
    it.chatNpc("Well, I can turn the enchantment that creates the loot keys on and off for you. Won't cost you anything to turn it back on again, either.", wrap = true)
    it.chatNpc("If you'd like it so stuff like food and potions stay out of the keys so you can use them yourself, I can do that too.", wrap = true)
    it.chatNpc("And if you like the feeling of picking up valuables, I can make it so that anything above a certain value hits the floor as well.", wrap = true)
    skullySettings(it)
}

/** Skully's settings menu. The valuables options are BLOCKED (no sourced default threshold) and not offered. */
suspend fun skullySettings(it: QueueTask) {
    val player = it.player
    val enabled = LootKeys.receivesKeys(player)
    val foodToFloor = player.attr[LootKeys.FOOD_TO_FLOOR] == true
    val toggle = if (enabled) "Turn loot keys off" else "Turn loot keys on"
    val food = if (foodToFloor) "Send food to loot key" else "Drop food to floor"
    when (it.options(toggle, food, title = "Select an option")) {
        1 -> {
            player.attr[LootKeys.ENABLED] = !enabled
            if (enabled) {
                it.chatNpc(
                    "Ok, whenever you kill someone else now, their items will go to the floor like normal. Just remember, they could have loot keys on them, and they'll still go to your inventory!",
                    wrap = true,
                )
            } else {
                it.chatNpc("Ok, you'll now get loot keys when you kill another person in the Wilderness.", wrap = true)
            }
            skullySettings(it)
        }
        2 -> {
            player.attr[LootKeys.FOOD_TO_FLOOR] = !foodToFloor
            if (foodToFloor) {
                it.chatNpc("Ok, food and potions will now be sent to your loot keys.", wrap = true)
            } else {
                it.chatNpc("Ok, food and potions will now be dropped to the floor instead of stored in a loot key.", wrap = true)
            }
            skullySettings(it)
        }
    }
}

suspend fun skullyClaimed(
    it: QueueTask,
    thenOptions: Boolean,
) {
    val player = it.player
    if (thenOptions) it.chatPlayer("How much loot have I claimed?", wrap = true)
    val keys = LootKeys.counter(player, LootKeys.CLAIMED_KEYS)
    val claimed = "%,d".format(LootKeys.counter(player, LootKeys.CLAIMED_VALUE))
    val destroyed = LootKeys.counter(player, LootKeys.DESTROYED_VALUE)
    val total = LootKeys.counter(player, LootKeys.CLAIMED_VALUE)
    val line =
        when {
            keys == 0L -> "Well, seeing as you haven't claimed a key yet, I reckon you've claimed a total of 0gp worth of loot."
            keys == 1L && destroyed == 0L -> "You've claimed 1 key, containing loot worth about ${claimed}gp. I haven't seen you destroy anything of value yet, though."
            keys == 1L && total == 0L -> "You've claimed 1 key, containing loot worth about ${claimed}gp, and you destroyed everything of value."
            destroyed == 0L -> "You've claimed $keys keys, containing loot worth about ${claimed}gp. I haven't seen you destroy anything of value yet, though."
            total == 0L -> "You've claimed $keys keys, containing loot worth about ${claimed}gp, and you destroyed everything of value."
            else -> "You've claimed $keys keys, containing loot worth about ${claimed}gp. You've destroyed ${"%,d".format(destroyed)}gp worth of it."
        }
    it.chatNpc(line, wrap = true)
    if (thenOptions) skullyOptions(it)
}

suspend fun skullyWhoAreYou(it: QueueTask) {
    it.chatPlayer("Who are you, exactly?", wrap = true)
    it.chatNpc(
        "Ah, used to be an adventurer like you, I guess. Used to roam the world, killing monsters for a living. 'Cept then, I discovered the Wilderness.",
        wrap = true,
    )
    it.chatNpc("Used to take on anyone and everyone who stood in me way, and made myself a small fortune doing it too.", wrap = true)
    it.chatNpc("It's how I got the name, used to go anywhere and everywhere with a skull over my head!", wrap = true)
    it.chatPlayer("So how'd you end up like... this?", wrap = true)
    it.chatNpc("Ah, you live a life feeding on others, eventually something higher up the food chain finds you.", wrap = true)
    it.chatNpc(
        "I used to have a hidden outpost out near the Bone Yard, somewhere to keep my stuff in an emergency. One day all I come back to is a smouldering pile of rubble.",
        wrap = true,
    )
    it.chatNpc("Next thing I know, someone's snuck up behind me and has put a spear through my back.", wrap = true)
    it.chatPlayer("And you survived?!", wrap = true)
    it.chatNpc("Always carry a ring of life, friend! Never know when you'll need it.", wrap = true)
    it.chatNpc(
        "Still, the damage was done. Everything I ever owned up in smoke, and even though the wound's fully healed, I can still feel that spear in my back to this day.",
        wrap = true,
    )
    it.chatNpc("Thankfully, Ferox was kind enough to take me in, and I managed to repurpose some of my old storage magic for adventurers like you.", wrap = true)
    it.chatPlayer("But why come back to the Wilderness?", wrap = true)
    it.chatNpc("What, go life a normal life behind a shop counter so I can pedal swamp paste for a living instead? I'll pass!", wrap = true)
    it.chatNpc("Anyhow, enough about me. What can I do for you?", wrap = true)
    skullyOptions(it)
}

// ---- Loot Chest ----
fun openLootChest(
    player: Player,
    index: Int,
) {
    if (BankPin.required(player)) {
        BankPin.request(player) { verified -> LootKeys.openChest(verified, index) }
    } else {
        LootKeys.openChest(player, index)
    }
}

on_obj_option(obj = LOOT_CHEST, option = "loot") {
    // A key-less chest still opens loot left inside (OSRS Wiki "Loot Chest"); several keys open the first one (ADAPTED).
    if (!openFirstKey(player)) {
        player.message("You don't have any key.")
    }
}

LootKeys.KEY_IDS.forEachIndexed { index, key ->
    on_item_on_obj(obj = LOOT_CHEST, item = key) {
        openLootChest(player, index)
    }

    on_item_option(item = key, option = 10) {
        val value = LootKeys.value(world.definitions, LootKeys.slotItems(player, index))
        // Loot-key destruction is a location rule, unlike death loot/recovery which is now
        // cause-based. Keep the wilderness-only Skully restriction explicit rather than asking
        // DeathResolver to infer a missing killer from geography.
        val dangerous = BountyHunterHome.isDangerousWilderness(player)
        if (!LootKeys.canDestroyHere(value, dangerous)) {
            player.message(LootKeys.DESTROY_TOO_VALUABLE_MESSAGE)
            return@on_item_option
        }
        player.queue(TaskPriority.WEAK) {
            destroyItem(key)
            if (!player.inventory.contains(key)) LootKeys.destroyed(player, index)
        }
    }
}
