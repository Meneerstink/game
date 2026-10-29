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
import gg.rsmod.plugins.api.ext.chatheadAnimation
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getCommandArgs
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setComponentAnim
import gg.rsmod.plugins.api.ext.setComponentNpcHead
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * The OSRS pets ([Pets]): drop the item and it follows, Pick-up, Metamorphosis (npc and item, unlocked variants: [PetMorphs]), the
 * items that change a pet, Emote / Interact ([PetEmotes]), the rift guardian's Locking, and the boss kill roll.
 * Talk-to is served by the generic transcript fallback (`npcs/talk_to_fallback.plugin.kts`) like every other npc.
 * The per-cycle follow, login / logout and the Summoning tab / orb buttons are wired in `familiar.plugin.kts`.
 */

val loadedPets = Pets.load()
val loadedEmotes = PetEmotes.load()
val loadedVoices = PetVoices.load()
val loadedChatheads = PetChatheads.load()

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

// N301 / N303 (owner 2026-10-24): a Loyalty store pet's Talk-to is a conversation of its own; a 667 cat's Interact-with works. Bound
// here, so the generic Talk-to fallback (talk_to_fallback.plugin.kts) leaves these npcs alone - no second voice on Talk-to.
Pets.variants.filter { it.classic }.forEach { variant ->
    val npcOptions = world.definitions.get(NpcDef::class.java, variant.npc).options
    val chats = ClassicPetTalk.chatsFor(variant.npc)
    if (chats != null && npcOptions.any { it.equals("talk-to", ignoreCase = true) }) {
        on_npc_option(npc = variant.npc, option = "talk-to") {
            val npc = player.getInteractingNpc()
            player.queue { with(ClassicPetTalk) { talk(npc, chats) } }
        }
    }
    if (npcOptions.any { it.equals("interact-with", ignoreCase = true) }) {
        on_npc_option(npc = variant.npc, option = "interact-with") {
            val npc = player.getInteractingNpc()
            player.queue { with(ClassicPetTalk) { interact(npc) } }
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
    val pets = Pets.variants.filterNot { it.classic }.map { it.pet }.toSet()
    pets.filter { it !in Pets.infos }.forEach { problems += "pet '$it' has no tier in pet_tiers.tsv" }
    Pets.infos.values.flatMap { i -> i.dropNpcs.map { i.pet to it } }.forEach { (pet, npc) ->
        if (world.definitions.getNullable(NpcDef::class.java, npc) == null) problems += "pet '$pet' drops from npc $npc which is not in the cache"
    }
    PetEmotes.byNpc.forEach { (npc, list) ->
        if (Pets.variantOfNpc(npc) == null) problems += "pet emote npc $npc is no pet follower"
        list.flatMap { it.seqs }.filter { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, it) == null }.forEach { problems += "pet emote seq $it (npc $npc) missing from the cache" }
    }
    Pets.variants.filter { it.osrsItem < 0 && !it.classic }.forEach { problems += "pet ${it.name} (${it.item}) has no OSRS item id in pets.tsv" }
    problems.forEach { p -> gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.SUMMON) { "pets: $p" } }
    println("OSRS pets: $loadedPets variants (${Pets.variants.count { it.classic }} of them 667 pets) of ${pets.size} OSRS pets, ${Pets.infos.values.count { it.dropOnly }} boss-drop pets, $loadedEmotes emotes, $loadedVoices voiced followers, ${problems.size} problems.")
}

/**
 * Owner A4 2026-10-11 (OSRS pet heads overflow the chat dialogue and the Follower Details frame): `petheads [from] [count] [ticks]`
 * shows every pet variant in turn - out as the follower (Follower Details head) and in an npc dialogue (chat head), [ticks] (default 4) game ticks each - so the whole list can be checked in the client (the test client logs each head's largest box vs its frame, InterfaceManager.measureNpcHead; tools\cache667\chathead_extents.py turns that log into data\cfg\pets\pet_chathead_extents.tsv, read by NpcChatheadFit).
 */
on_command("petheads", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    // `petheads anim <npc> <anim> [anim ...]`: one npc's chat head with each given animation (-1 = none), 6 ticks each (N257).
    if (args.getOrNull(0) == "anim") {
        val id = args.getOrNull(1)?.toIntOrNull() ?: return@on_command
        player.queue {
            args.drop(2).mapNotNull { it.toIntOrNull() }.forEach { anim ->
                player.openInterface(interfaceId = 242, parent = 752, child = 13)
                player.setComponentNpcHead(interfaceId = 242, component = 2, npc = id)
                player.setComponentAnim(interfaceId = 242, component = 2, anim = anim)
                player.setComponentText(interfaceId = 242, component = 3, text = "npc $id anim $anim")
                player.setComponentText(interfaceId = 242, component = 4, text = "")
                player.setComponentText(interfaceId = 242, component = 5, text = "")
                wait(6)
            }
        }
        return@on_command
    }
    // `petheads npc <id> ...`: any npc's chat head, 12 ticks each (the native 667 heads the pets are compared with).
    if (args.getOrNull(0) == "npc") {
        player.queue {
            args.drop(1).mapNotNull { it.toIntOrNull() }.forEach { id ->
                player.openInterface(interfaceId = 242, parent = 752, child = 13)
                player.setComponentNpcHead(interfaceId = 242, component = 2, npc = id)
                player.setComponentAnim(interfaceId = 242, component = 2, anim = gg.rsmod.plugins.api.cfg.FacialExpression.HAPPY_TALKING.chatheadAnimation(player.world, id))
                player.setComponentText(interfaceId = 242, component = 3, text = "npc $id")
                player.setComponentText(interfaceId = 242, component = 4, text = "")
                player.setComponentText(interfaceId = 242, component = 5, text = "")
                wait(12)
            }
            player.closeInterface(242)
        }
        return@on_command
    }
    val from = args.getOrNull(0)?.toIntOrNull() ?: 0
    val count = args.getOrNull(1)?.toIntOrNull() ?: Pets.variants.size
    val ticks = (args.getOrNull(2)?.toIntOrNull() ?: 4).coerceIn(1, 100)
    val list = Pets.variants.drop(from).take(count)
    player.queue {
        var previous = -1
        list.forEachIndexed { i, variant ->
            // Dismiss puts the pet item back in the pack; take it out again so a long run never fills the inventory.
            if (Pets.current(player) != null) Pets.dismiss(player)
            if (previous != -1) player.inventory.remove(previous, 1)
            previous = variant.item
            if (player.inventory.add(variant.item, 1).hasSucceeded()) {
                val slot = player.inventory.getItemIndex(variant.item, false)
                Pets.drop(player, variant.item, slot)
            }
            player.message("petheads ${from + i}: ${variant.name} (npc ${variant.npc})", type = gg.rsmod.plugins.api.ChatMessageType.CONSOLE)
            player.openInterface(interfaceId = 242, parent = 752, child = 13)
            player.setComponentNpcHead(interfaceId = 242, component = 2, npc = variant.npc)
            player.setComponentAnim(interfaceId = 242, component = 2, anim = gg.rsmod.plugins.api.cfg.FacialExpression.HAPPY_TALKING.chatheadAnimation(player.world, variant.npc))
            player.setComponentText(interfaceId = 242, component = 3, text = variant.name)
            player.setComponentText(interfaceId = 242, component = 4, text = "petheads ${from + i}")
            player.setComponentText(interfaceId = 242, component = 5, text = "")
            wait(ticks)
        }
        if (Pets.current(player) != null) Pets.dismiss(player)
        if (previous != -1) player.inventory.remove(previous, 1)
        player.closeInterface(242)
    }
}

// Owner 2026-10-24 ("geef me spawn commands om alle pets te spawnen, makkelijk te kopieren"): `::pets <block>` empties the
// inventory and spawns block <block> (1-based, 28 pet items each) of every pet item - the OSRS pets first, then the 667 pets.
// N404 (owner 2026-10-24: "je kunt 2 van dezelfde pet hebben"): one item per pet - its first form, the one a boss drops or a shop
// sells - so a test inventory never holds two forms of one OSRS pet; the other forms come from Metamorphosis, as in OSRS. The 667
// pets have no Metamorphosis: every colour stays its own item.
on_command("pets", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val items = (Pets.variants.filter { !it.classic }.distinctBy { it.pet } + Pets.variants.filter { it.classic }).map { it.item }.distinct()
    val blocks = (items.size + 27) / 28
    val block = player.getCommandArgs().getOrNull(0)?.toIntOrNull()
    if (block == null || block !in 1..blocks) {
        player.message("Usage: ::pets <1-$blocks> - 28 pets per block (${items.size} pet items; OSRS first, then 667).")
        return@on_command
    }
    player.inventory.removeAll()
    items.drop((block - 1) * 28).take(28).forEach { player.inventory.add(it, 1) }
    player.message("Pets block $block of $blocks spawned.")
}
