package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.plugins.api.ext.getInteractingItemSlot
import gg.rsmod.plugins.api.ext.getInteractingNpc

/**
 * The OSRS pets ([Pets]): drop the item and it follows, Pick-up, Metamorphosis (npc and item), and the boss kill roll.
 * Talk-to is served by the generic transcript fallback (`npcs/talk_to_fallback.plugin.kts`) like every other npc.
 * The per-cycle follow, login / logout and the Summoning tab / orb buttons are wired in `familiar.plugin.kts`.
 */

val loadedPets = Pets.load()

Pets.variants.forEach { variant ->
    // The engine's Drop (OpHeld5Handler) asks this first; the follower comes out instead of the item landing on the floor.
    can_drop_item(item = variant.item) {
        Pets.drop(player, variant.item, player.attr[INTERACTING_ITEM_SLOT] ?: -1)
        false
    }
    val itemOptions = world.definitions.get(ItemDef::class.java, variant.item).inventoryMenu
    if (itemOptions.any { it.equals("metamorphosis", ignoreCase = true) }) {
        on_item_option(item = variant.item, option = "metamorphosis") {
            Pets.metamorphItem(player, variant.item, player.getInteractingItemSlot())
        }
    }
    val npcOptions = world.definitions.get(NpcDef::class.java, variant.npc).options
    if (npcOptions.any { it.equals("pick-up", ignoreCase = true) }) {
        on_npc_option(npc = variant.npc, option = "pick-up") {
            Pets.pickUp(player, player.getInteractingNpc())
        }
    }
    // OSRS names the op "Metamorphosis" on most pets and "Metamorph" on a few (Gull, Beef).
    npcOptions.filterNotNull().filter { it.equals("metamorphosis", ignoreCase = true) || it.equals("metamorph", ignoreCase = true) }.forEach { op ->
        on_npc_option(npc = variant.npc, option = op.lowercase()) {
            Pets.metamorph(player, player.getInteractingNpc())
        }
    }
}

on_npc_killed { killer, npc ->
    Pets.roll(killer, npc.id)
}

on_world_init {
    val problems = mutableListOf<String>()
    Pets.variants.forEach { v ->
        if (world.definitions.getNullable(ItemDef::class.java, v.item) == null) problems += "pet item ${v.item} (${v.name}) missing from the cache"
        if (world.definitions.getNullable(NpcDef::class.java, v.npc) == null) problems += "pet npc ${v.npc} (${v.name}) missing from the cache"
    }
    val pets = Pets.variants.map { it.pet }.toSet()
    pets.filter { it !in Pets.infos }.forEach { problems += "pet '$it' has no tier in pet_tiers.tsv" }
    Pets.infos.values.flatMap { i -> i.dropNpcs.map { i.pet to it } }.forEach { (pet, npc) ->
        if (world.definitions.getNullable(NpcDef::class.java, npc) == null) problems += "pet '$pet' drops from npc $npc which is not in the cache"
    }
    problems.forEach { p -> gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.SUMMON) { "pets: $p" } }
    println("OSRS pets: $loadedPets variants of ${pets.size} pets, ${Pets.infos.values.count { it.dropOnly }} boss-drop pets, ${problems.size} problems.")
}
