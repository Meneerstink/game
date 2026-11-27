package gg.rsmod.plugins.content.diaries

/**
 * DIARIES-OSRS: world content that some diary tasks need and that this server did not have yet.
 *  - Seers' church organ (loc 25818 @2691,3461): 'Play' (OSRS Wiki "Organ": interactable with 'Play') - Kandarin easy 4.
 *  - Muddy chest in the Lava Maze (loc 170 'Closed chest' @3089,3859; open form 171): OSRS Wiki "Muddy chest": opened with a
 *    muddy key that is used up, guaranteed loot Uncut ruby 1, Mithril bar 2 (noted), Law rune 5, Death rune 5, Chaos rune 15.
 *    The mobile-only/blighted rolls and Larran's key are not ported. Wilderness medium 11.
 */

on_obj_option(obj = 25818, option = "Play") {
    player.animate(3675) // same organ animation as the Lumbridge church organ (Void play_organ)
    player.playJingle(72)
    Diaries.event(player, "organ:seers")
}

on_obj_option(obj = 170, option = "Open") {
    val chest = player.getInteractingGameObj()
    if (chest.tile.x != 3089 || chest.tile.z != 3859) {
        player.message("The chest is locked.")
        return@on_obj_option
    }
    if (!player.inventory.contains(Items.MUDDY_KEY)) {
        player.message("The chest is locked.")
        return@on_obj_option
    }
    val loot = listOf(
        Item(Items.UNCUT_RUBY, 1), Item(Items.MITHRIL_BAR_NOTED, 2), Item(Items.LAW_RUNE, 5), Item(Items.DEATH_RUNE, 5), Item(Items.CHAOS_RUNE, 15),
    )
    val slotsNeeded = loot.count { player.inventory.getItemCount(it.id) == 0 } - 1 // the key's slot frees up
    if (player.inventory.freeSlotCount < slotsNeeded) {
        player.message("You don't have enough inventory space to take the treasure.")
        return@on_obj_option
    }
    player.lock()
    player.queue {
        if (player.inventory.remove(Items.MUDDY_KEY, 1).hasSucceeded()) {
            player.message("You unlock the chest with your key.")
            player.animate(536)
            world.spawnTemporaryObject(DynamicObject(171, chest.type, chest.rot, chest.tile), 4, DynamicObject(chest))
            wait(1)
            loot.forEach { player.inventory.add(it) }
            player.message("You find some treasure in the chest!")
            Diaries.event(player, "chest:muddy")
        }
        player.unlock()
    }
}

// DIARIES-OSRS D6: Teleport on the Kandarin headgear and Western banners (inventory + worn), see DiaryItems. Only where the cache has
// the option (headgear 1/2 have none; an unknown option fails the plugin load).
fun hasOp(id: Int, worn: Boolean): Boolean {
    val d = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id)
    return (if (worn) d.equipmentMenu.toList() else d.inventoryMenu.toList()).any { it?.lowercase() == "teleport" }
}
listOf(Diaries.Diary.KANDARIN, Diaries.Diary.WESTERN).forEach { diary ->
    DiaryRewards.items(world, diary).filter { it >= 0 }.forEach { id ->
        val go: (gg.rsmod.game.model.entity.Player) -> Unit =
            if (diary == Diaries.Diary.KANDARIN) { p -> DiaryItems.headgearTeleport(p, id) } else { p -> DiaryItems.bannerTeleport(p, id) }
        if (hasOp(id, false)) on_item_option(id, "teleport") { go(player) }
        if (hasOp(id, true)) on_equipment_option(id, "teleport") { go(player) }
    }
}

// DIARIES-OSRS: Rogues' Castle chests (RoguesChest; Wilderness elite 5 = steal from one). OSRS has 'Open' (springs a trap) and
// 'Search for traps' (the real unlock); the 667 loc 14773 only has 'Open', so 'Open' is the OSRS 'Search for traps' flow here
// (choice in dossier DIARIES-OSRS, nachtrun 27-11).
run {
    val ops = world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, RoguesChest.CHEST).options.map { it?.lowercase() }
    val op = listOf("search for traps", "open").firstOrNull { it in ops }
    if (op == null) println("[diaries] PROBLEM rogues chest ${RoguesChest.CHEST} has no usable option: $ops")
    else on_obj_option(obj = RoguesChest.CHEST, option = op) {
        val chest = player.getInteractingGameObj()
        if (!RoguesChest.canOpen(player)) {
            player.message("You need a Thieving level of ${RoguesChest.LEVEL} to open this chest.")
            return@on_obj_option
        }
        player.lockingQueue {
            player.message("You search the chest for traps.")
            player.animate(536)
            wait(3)
            player.message("You find a trap on the chest and disable it.")
            player.animate(536)
            wait(2)
            val loot = RoguesChest.roll(player)
            player.addXp(Skills.THIEVING, RoguesChest.XP)
            world.spawnTemporaryObject(DynamicObject(RoguesChest.CHEST + 1, chest.type, chest.rot, chest.tile), RoguesChest.RESET_TICKS, DynamicObject(chest))
            if (loot.isEmpty()) player.message("You open the chest, but find nothing of interest.")
            else { player.message("You open the chest and take some loot."); RoguesChest.give(player, loot) }
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.ITEM) { "rogues-chest ${player.username} loot=${loot.joinToString { "${it.id}x${it.amount}" }}" }
            Diaries.event(player, "chest:rogues")
            RoguesChest.rogues(player)
        }
    }
}

// Varrock easy 9 'Give a dog a bone!' - OSRS Wiki "Stray dog": "Feed - It can be fed bones ... When players feed it, it happily barks"
// (following the player for a while is not ported). The bone is used up.
listOf(gg.rsmod.plugins.api.cfg.Npcs.STRAY_DOG_5917, gg.rsmod.plugins.api.cfg.Npcs.STRAY_DOG_5918).forEach { dog ->
    on_item_on_npc(item = gg.rsmod.plugins.api.cfg.Items.BONES, npc = dog) {
        val npc = player.getInteractingNpc()
        if (!player.inventory.remove(gg.rsmod.plugins.api.cfg.Items.BONES, 1).hasSucceeded()) return@on_item_on_npc
        npc.forceChat("Woof!")
        Diaries.event(player, "dog_bone")
    }
}
