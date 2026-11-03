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

// N503 D17: every pet's own pack (its items stay in it when it is picked up), saved with the account.
Pets.variants.mapNotNull { Pets.baseItem(it.pet) }.distinct().forEach { register_container_key(BeastOfBurden.petPackKey(it)) }
val loadedEmotes = PetEmotes.load()
val loadedVoices = PetVoices.load()
val loadedChatheads = PetChatheads.load()

Pets.variants.forEach { variant ->
    // N436: the pet's "Upgraded" item copy (a bound burden trinket) does everything the plain item does.
    val petItems = listOfNotNull(variant.item, Pets.upgradedItems.firstOrNull { Pets.baseItemOf(it) == variant.item })
    petItems.forEach { petItem ->
        // The engine's Drop (OpHeld5Handler) asks this first; the follower comes out instead of the item landing on the floor.
        can_drop_item(item = petItem) {
            Pets.drop(player, petItem, player.attr[INTERACTING_ITEM_SLOT] ?: -1)
            false
        }
        // N437 (owner 2026-10-30: "altijd maar 1 pet"): a pet item on the floor (the Deadman's chest with backpack and bank full) stays
        // there while the player has that pet anywhere else - one of each pet per account.
        set_ground_item_condition(item = petItem) {
            val owned = Pets.owns(player, variant.pet)
            if (owned) player.message("You already have this pet. You can only have one of each pet.")
            !owned
        }
        val itemOptions = world.definitions.get(ItemDef::class.java, petItem).inventoryMenu
        if (itemOptions.any { it.equals("metamorphosis", ignoreCase = true) }) {
            on_item_option(item = petItem, option = "metamorphosis") {
                Pets.metamorphItem(player, petItem, player.getInteractingItemSlot())
            }
        }
    }
    // N423: the Upgraded copy of the follower (a bound burden trinket) answers every op of its pet.
    listOfNotNull(variant.npc, Pets.upgradedNpc(variant.npc)).forEach { npcId ->
        val npcOptions = world.definitions.get(NpcDef::class.java, npcId).options
        if (npcOptions.any { it.equals("pick-up", ignoreCase = true) }) {
            on_npc_option(npc = npcId, option = "pick-up") {
                Pets.pickUp(player, player.getInteractingNpc())
            }
        }
        // N407 (owner 2026-10-31): the Clockwork cat's "Shoo" releases it like RS (Void ClockworkCat.kt); Perdu gives it back.
        if (npcOptions.any { it.equals("shoo", ignoreCase = true) }) {
            on_npc_option(npc = npcId, option = "shoo") {
                Pets.shoo(player, player.getInteractingNpc())
            }
        }
        // OSRS names the op "Metamorphosis" on most pets and "Metamorph" on a few (Gull, Beef).
        npcOptions.filterNotNull().filter { it.equals("metamorphosis", ignoreCase = true) || it.equals("metamorph", ignoreCase = true) }.forEach { op ->
            on_npc_option(npc = npcId, option = op.lowercase()) {
                Pets.metamorph(player, player.getInteractingNpc())
            }
        }
        if (PetEmotes.hasEmotes(npcId)) {
            if (npcOptions.any { it.equals("emote", ignoreCase = true) }) {
                on_npc_option(npc = npcId, option = "emote") {
                    PetEmotes.emote(player, player.getInteractingNpc())
                }
            }
            // Beef: Interact -> "Moo." or the emote (Transcript:Beef "selecting the "Moo." option"; OSRS Wiki Beef "Emotes").
            if (npcOptions.any { it.equals("interact", ignoreCase = true) }) {
                on_npc_option(npc = npcId, option = "interact") {
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
            on_npc_option(npc = npcId, option = "locking") {
                if (Pets.current(player) !== player.getInteractingNpc()) {
                    player.message("This isn't your pet.")
                } else {
                    PetMorphs.toggleRiftLock(player)
                }
            }
        }
        // N423: an Upgraded copy carries like a beast of burden - Store opens the Familiar Inventory, an item used on it is stored
        // (the items that change the pet still change it; a burden trinket has its own binding, burden_trinkets.plugin.kts).
        if (Pets.isUpgradedNpc(npcId)) {
            if (npcOptions.any { it.equals("store", ignoreCase = true) }) {
                on_npc_option(npc = npcId, option = "store") {
                    if (Pets.current(player) !== player.getInteractingNpc()) {
                        player.message("This isn't your pet.")
                    } else {
                        FamiliarInventory.open(player)
                    }
                }
            }
            on_any_item_on_npc(npc = npcId) {
                val npc = player.getInteractingNpc()
                val used = player.getInteractingItem()
                val morphs = variant.pet in PetMorphs.USES || variant.pet == "Gull (pet)"
                when {
                    Pets.current(player) !== npc -> player.message("This isn't your pet.")
                    morphs && PetMorphs.useItem(player, Pets.itemOf(player) ?: variant.item, used.id, onFollower = true) -> {}
                    else -> BeastOfBurden.deposit(player, used)
                }
            }
        } else if (variant.pet in PetMorphs.USES || variant.pet == "Gull (pet)") {
            // The items that change a pet (PetMorphs.USES, Gull's fish), used on the follower.
            on_any_item_on_npc(npc = npcId) {
                val npc = player.getInteractingNpc()
                if (Pets.current(player) !== npc) {
                    player.message("This isn't your pet.")
                } else if (!PetMorphs.useItem(player, Pets.itemOf(player) ?: variant.item, player.getInteractingItem().id, onFollower = true)) {
                    player.message("Nothing interesting happens.")
                }
            }
        }
    }
    // ... and on the pet item in the inventory where OSRS allows it (Rock golem: "while it is in the inventory or following").
    PetMorphs.USES[variant.pet].orEmpty().map { it.item }.distinct().forEach { useItem ->
        petItems.forEach { petItem ->
            on_item_on_item(item1 = useItem, item2 = petItem) {
                val petSlot = if (player.getInteractingItemId() == petItem) player.getInteractingItemSlot() else player.getInteractingOtherItemSlot()
                val used = if (player.getInteractingItemId() == petItem) player.getInteractingOtherItemId() else player.getInteractingItemId()
                PetMorphs.useItem(player, petItem, used, onFollower = false, slot = petSlot)
            }
        }
    }
}

// N301 / N303 (owner 2026-10-24): a Loyalty store pet's Talk-to is a conversation of its own; a 667 cat's Interact-with works. Bound
// here, so the generic Talk-to fallback (talk_to_fallback.plugin.kts) leaves these npcs alone - no second voice on Talk-to.
Pets.variants.filter { it.classic }.forEach { variant ->
    listOfNotNull(variant.npc, Pets.upgradedNpc(variant.npc)).forEach { npcId ->
        val npcOptions = world.definitions.get(NpcDef::class.java, npcId).options
        val chats = ClassicPetTalk.chatsFor(npcId)
        if (chats != null && npcOptions.any { it.equals("talk-to", ignoreCase = true) }) {
            on_npc_option(npc = npcId, option = "talk-to") {
                val npc = player.getInteractingNpc()
                player.queue { with(ClassicPetTalk) { talk(npc, chats) } }
            }
        }
        if (npcOptions.any { it.equals("interact-with", ignoreCase = true) }) {
            on_npc_option(npc = npcId, option = "interact-with") {
                val npc = player.getInteractingNpc()
                player.queue { with(ClassicPetTalk) { interact(npc) } }
            }
        }
    }
}

on_npc_killed { killer, npc ->
    // Scorpia's pet is rolled after her shard pre-roll; it cannot accompany a shard.
    if (npc.id == Npcs.SCORPIA) return@on_npc_killed
    if (npc.id == gg.rsmod.plugins.content.areas.wilderness.bosses.VenenatisCombat.NPC) return@on_npc_killed
    gg.rsmod.plugins.content.areas.wilderness.bosses.OldWildernessBosses.rewardNpc(npc.id)?.let { Pets.roll(killer, it) }
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
    // N423 test sweep: `petheads <from> <count> <ticks> up` shows every form as its Upgraded copy (a Pack yak burden trinket bound
    // to each pet on the spot - the test account only, no dialogue).
    val upgraded = args.getOrNull(3) == "up"
    val list = Pets.variants.drop(from).take(count)
    if (upgraded && player.inventory.getItemCount(BurdenTrinkets.Tier.PACK_YAK.item) < 1) player.inventory.add(BurdenTrinkets.Tier.PACK_YAK.item, 1)
    player.queue {
        var previous = -1
        list.forEachIndexed { i, variant ->
            if (upgraded) player.attr[BurdenTrinkets.BOUND_ATTR] = BurdenTrinkets.encode(mapOf(variant.pet to BurdenTrinkets.Tier.PACK_YAK.item))
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
            val shown = Pets.current(player)?.id ?: variant.npc
            player.setComponentNpcHead(interfaceId = 242, component = 2, npc = shown)
            player.setComponentAnim(interfaceId = 242, component = 2, anim = gg.rsmod.plugins.api.cfg.FacialExpression.HAPPY_TALKING.chatheadAnimation(player.world, shown))
            player.setComponentText(interfaceId = 242, component = 3, text = player.world.definitions.getNullable(NpcDef::class.java, shown)?.name ?: variant.name)
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
// N407 (2026-10-31): a real pet reward as from a boss kill (Pets.award: follower / backpack / bank, "Getting pet", the purple
// world broadcast, the Collection Log) - to test what a player and everyone online sees. Admin only.
on_command("petdrop", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val item = player.getCommandArgs().getOrNull(0)?.toIntOrNull()
    val variant = item?.let { Pets.variantOfItem(it) }
    if (item == null || variant == null) {
        player.message("Usage: ::petdrop <pet item id> - a pet reward as from a boss kill.")
        return@on_command
    }
    player.message("Pet reward: ${Pets.award(player, variant.pet, item, source = "test")}")
}

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
