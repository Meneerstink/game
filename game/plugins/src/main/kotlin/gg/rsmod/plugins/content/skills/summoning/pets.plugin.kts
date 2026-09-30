package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.plugins.api.ext.getInteractingItem
import gg.rsmod.plugins.api.ext.getInteractingItemId
import gg.rsmod.plugins.api.ext.getInteractingItemSlot
import gg.rsmod.plugins.api.ext.getInteractingNpc
import gg.rsmod.plugins.api.ext.getInteractingOtherItemId
import gg.rsmod.plugins.api.ext.getInteractingOtherItemSlot
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options

/**
 * The OSRS pets ([Pets]): drop the item and it follows, Pick-up, Metamorphosis (npc and item, unlocked variants: [PetMorphs]), the
 * items that change a pet, Emote / Interact ([PetEmotes]), the rift guardian's Locking, and the boss kill roll.
 * Talk-to is served by the generic transcript fallback (`npcs/talk_to_fallback.plugin.kts`) like every other npc.
 * The per-cycle follow, login / logout and the Summoning tab / orb buttons are wired in `familiar.plugin.kts`.
 */

val loadedPets = Pets.load()
val loadedEmotes = PetEmotes.load()

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
    if (PetEmotes.hasEmotes(variant.npc)) {
        if (npcOptions.any { it.equals("emote", ignoreCase = true) }) {
            on_npc_option(npc = variant.npc, option = "emote") {
                PetEmotes.emote(player, player.getInteractingNpc())
            }
        }
        // Beef: Interact -> "Moo." or the emote (Transcript:Beef "selecting the "Moo." option"; OSRS Wiki Beef "Emotes").
        if (npcOptions.any { it.equals("interact", ignoreCase = true) }) {
            on_npc_option(npc = variant.npc, option = "interact") {
                val npc = player.getInteractingNpc()
                player.queue {
                    when (options("Moo.", "Emote.")) {
                        1 -> PetEmotes.moo(player, npc)
                        2 -> PetEmotes.beefEmote(player, npc)
                    }
                }
            }
        }
    }
    if (variant.pet == PetMorphs.RIFT_GUARDIAN && npcOptions.any { it.equals("locking", ignoreCase = true) }) {
        on_npc_option(npc = variant.npc, option = "locking") {
            if (Pets.current(player) !== player.getInteractingNpc()) {
                player.message("This isn't your pet.")
            } else {
                PetMorphs.toggleRiftLock(player)
            }
        }
    }
    // The items that change a pet (PetMorphs.USES, Gull's fish), used on the follower.
    if (variant.pet in PetMorphs.USES || variant.pet == "Gull (pet)") {
        on_any_item_on_npc(npc = variant.npc) {
            val npc = player.getInteractingNpc()
            if (Pets.current(player) !== npc) {
                player.message("This isn't your pet.")
            } else if (!PetMorphs.useItem(player, Pets.itemOf(player) ?: variant.item, player.getInteractingItem().id, onFollower = true)) {
                player.message("Nothing interesting happens.")
            }
        }
    }
    // ... and on the pet item in the inventory where OSRS allows it (Rock golem: "while it is in the inventory or following").
    PetMorphs.USES[variant.pet].orEmpty().map { it.item }.distinct().forEach { useItem ->
        on_item_on_item(item1 = useItem, item2 = variant.item) {
            val petSlot = if (player.getInteractingItemId() == variant.item) player.getInteractingItemSlot() else player.getInteractingOtherItemSlot()
            val used = if (player.getInteractingItemId() == variant.item) player.getInteractingOtherItemId() else player.getInteractingItemId()
            PetMorphs.useItem(player, variant.item, used, onFollower = false, slot = petSlot)
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
    PetEmotes.byNpc.forEach { (npc, list) ->
        if (Pets.variantOfNpc(npc) == null) problems += "pet emote npc $npc is no pet follower"
        list.flatMap { it.seqs }.filter { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, it) == null }.forEach { problems += "pet emote seq $it (npc $npc) missing from the cache" }
    }
    Pets.variants.filter { it.osrsItem < 0 }.forEach { problems += "pet ${it.name} (${it.item}) has no OSRS item id in pets.tsv" }
    problems.forEach { p -> gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.SUMMON) { "pets: $p" } }
    println("OSRS pets: $loadedPets variants of ${pets.size} pets, ${Pets.infos.values.count { it.dropOnly }} boss-drop pets, $loadedEmotes emotes, ${problems.size} problems.")
}
