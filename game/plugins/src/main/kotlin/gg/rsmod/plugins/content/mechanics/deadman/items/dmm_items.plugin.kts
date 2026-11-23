package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.content.items.osrs.SoulflameHorn

/*
 * PROJECT48 DMM-ITEMS group A: the item options of the nut items (cache options of the imported items).
 */

on_item_option(DmmItems.ROYAL_SEED_POD, "commune") { DmmItems.royalSeedPod(player) }
on_item_option(DmmItems.ROYAL_SEED_POD, "destroy") { player.message("You can get another one from King Narnode Shareen for 2 coins.") }

on_item_option(DmmItems.ESCAPE_CRYSTAL, "teleport") { DmmItems.escapeCrystal(player) }
val escapeAutoTimer = TimerKey()
on_login { if (DmmItems.autoTeleEnabled(player)) player.timers[escapeAutoTimer] = 1 }
on_timer(escapeAutoTimer) {
    DmmItems.autoTeleTick(player)
    if (DmmItems.autoTeleEnabled(player)) player.timers[escapeAutoTimer] = 1
}
on_item_option(DmmItems.ESCAPE_CRYSTAL, "auto-afk") {
    DmmItems.autoTeleToggle(player)
    if (DmmItems.autoTeleEnabled(player)) player.timers[escapeAutoTimer] = 1
}
on_item_option(DmmItems.ESCAPE_CRYSTAL, "configure") {
    player.queue { DmmItems.autoTeleSet(player, inputInt("Seconds of inactivity (2-300):")) }
}

/* Salve amulet (i)/(ei) 'Uncharge' (cache option; the OSRS Wiki lists it but not its effect): the imbue is permanent here. */
// Nothing is undone, so no shared "Are you sure" question first (ITEM-UNCHARGE-MELDING: after "Yes" the message never showed).
gg.rsmod.plugins.content.items.ItemActionGuard.selfConfirming("uncharge", 25485, 25486)
for (salve in intArrayOf(25485, 25486)) on_item_option(salve, "uncharge") { player.message("This amulet is permanently imbued; there is nothing to uncharge.") }

/* Ring of wealth (i) 'Features': OSRS opens the boss kill log + coin collection, which this server does not have. */
for (ring in DmmItems.RING_OF_WEALTH_I_CHARGED + DmmItems.RING_OF_WEALTH_I) {
    on_item_option(ring, "features") {
        player.message("Imbued: doubles the clue scroll chance from Wilderness monster kills and skilling. Rub to teleport (one charge each).")
    }
}

/* Soulflame horn Configure (OSRS Wiki "Soulflame horn"): the Entice radius (0-3 tiles) and the number of other players it affects (1-20). */
on_item_option(SoulflameHorn.ID, "configure") { player.queue { SoulflameHorn.configure(this, player) } }
run {
    val hornDef = world.definitions.get(ItemDef::class.java, SoulflameHorn.ID)
    if (hornDef.equipmentMenu.any { it.equals("configure", true) }) on_equipment_option(SoulflameHorn.ID, option = "configure") { player.queue { SoulflameHorn.configure(this, player) } }
}

on_item_option(DmmItems.CRYSTAL_OF_ECHOES, "teleport-back") { DmmItems.teleportBack(player) }
on_item_option(DmmItems.CRYSTAL_OF_ECHOES, "save-memory") { DmmItems.saveMemory(player) }
on_item_option(DmmItems.CRYSTAL_OF_ECHOES, "check-memories") { DmmItems.checkMemory(player) }

on_item_option(DmmItems.ETERNAL_TELEPORT_CRYSTAL, "lletya") { DmmItems.eternalCrystal(player) }
on_item_option(DmmItems.ETERNAL_TELEPORT_CRYSTAL, "prifddinas") { DmmItems.eternalCrystal(player) }

on_item_option(DmmItems.AMULET_OF_ETERNAL_GLORY, "rub") {
    player.queue {
        val names = DmmItems.ETERNAL_GLORY_TELEPORTS.keys.toList()
        val choice = options(*names.map { "$it." }.toTypedArray(), "Nowhere.")
        if (choice in 1..names.size) DmmItems.eternalGlory(player, DmmItems.ETERNAL_GLORY_TELEPORTS.getValue(names[choice - 1]))
    }
}

run {
    val def = world.definitions.get(ItemDef::class.java, DmmItems.AMULET_OF_ETERNAL_GLORY)
    DmmItems.ETERNAL_GLORY_TELEPORTS.forEach { (name, tile) ->
        if (def.equipmentMenu.any { it.equals(name, true) }) {
            on_equipment_option(DmmItems.AMULET_OF_ETERNAL_GLORY, option = name) { DmmItems.eternalGlory(player, tile) }
        }
    }
}

on_item_option(DmmItems.IMBUED_HEART, "invigorate") { DmmItems.invigorate(player, saturated = false) }
on_item_option(DmmItems.SATURATED_HEART, "invigorate") { DmmItems.invigorate(player, saturated = true) }

/* Ring of wealth (i): rub from the inventory / the worn options, one charge per teleport. */
DmmItems.RING_OF_WEALTH_I_CHARGED.forEach { ring ->
    on_item_option(ring, "rub") {
        player.queue {
            val names = gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.RING_OF_WEALTH.keys.toList()
            val choice = options(*names.map { "$it." }.toTypedArray(), "Nowhere.")
            if (choice in 1..names.size) {
                DmmItems.wealthTeleport(player, ring, gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.RING_OF_WEALTH.getValue(names[choice - 1]), equipped = false)
            }
        }
    }
    val ringDef = world.definitions.get(ItemDef::class.java, ring)
    gg.rsmod.plugins.content.magic.teleports.JewelleryDestinations.RING_OF_WEALTH.forEach { (name, tile) ->
        if (ringDef.equipmentMenu.any { it.equals(name, true) }) {
            on_equipment_option(ring, option = name) { DmmItems.wealthTeleport(player, ring, tile, equipped = true) }
        }
    }
}
on_item_option(DmmItems.RING_OF_WEALTH_I, "rub") { player.message("The ring has lost its charge. It will need to be recharged before you can use it again.") }

/* Ring of endurance (I-A05): charge with stamina potions, check, uncharge. */
RingOfEndurance.POTIONS.forEach { potion ->
    on_item_on_item(item1 = potion, item2 = DmmItems.RING_OF_ENDURANCE) { RingOfEndurance.charge(player, potion) }
    on_item_on_item(item1 = potion, item2 = DmmItems.RING_OF_ENDURANCE_UNCHARGED) { RingOfEndurance.charge(player, potion) }
}
on_item_option(DmmItems.RING_OF_ENDURANCE, "check") { RingOfEndurance.check(player, player.inventory[player.getInteractingItemSlot()]) }
on_item_option(DmmItems.RING_OF_ENDURANCE, "uncharge") { RingOfEndurance.uncharge(player, player.getInteractingItemSlot()) }

/* Ash sanctifier (I-A09). */
on_item_on_item(item1 = Items.DEATH_RUNE, item2 = DmmItems.ASH_SANCTIFIER) { AshSanctifier.charge(player) }
on_item_option(DmmItems.ASH_SANCTIFIER, "check") { AshSanctifier.check(player, player.inventory[player.getInteractingItemSlot()]) }
on_item_option(DmmItems.ASH_SANCTIFIER, "activity") { AshSanctifier.toggle(player) }
on_item_option(DmmItems.ASH_SANCTIFIER, "uncharge") { AshSanctifier.uncharge(player, player.getInteractingItemSlot()) }

/* Herb sack + Seed box (I-A07 / I-A08): Sacks.kt. Open/closed forms are separate items; the contents live on the player. */
for (id in intArrayOf(Sacks.HERB_SACK, Sacks.HERB_SACK_OPEN)) {
    on_item_option(id, "fill") { Sacks.fillHerbs(player) }
    on_item_option(id, "empty") { Sacks.emptyHerbs(player) }
    on_item_option(id, "check") { Sacks.checkHerbs(player) }
}
for (id in intArrayOf(Sacks.SEED_BOX, Sacks.SEED_BOX_OPEN)) {
    on_item_option(id, "fill") { Sacks.fillSeeds(player) }
    on_item_option(id, "empty") { Sacks.emptySeeds(player) }
    on_item_option(id, "check") { Sacks.checkSeeds(player) }
}
for (id in intArrayOf(Sacks.HERB_SACK, Sacks.SEED_BOX)) on_item_option(id, "open") { Sacks.toggle(player, player.getInteractingItemSlot(), open = true) }
for (id in intArrayOf(Sacks.HERB_SACK_OPEN, Sacks.SEED_BOX_OPEN)) on_item_option(id, "close") { Sacks.toggle(player, player.getInteractingItemSlot(), open = false) }
for (id in intArrayOf(Sacks.HERB_SACK, Sacks.HERB_SACK_OPEN, Sacks.SEED_BOX, Sacks.SEED_BOX_OPEN)) {
    on_item_option(id, "destroy") {
        val slot = player.getInteractingItemSlot()
        player.queue(TaskPriority.WEAK) {
            if (confirmItemAction(id, "Are you sure you want to destroy this item?", "Everything inside it is lost too.")) {
                if (player.inventory.remove(id, beginSlot = slot).hasSucceeded()) {
                    Sacks.destroyed(player, id)
                    player.message("You destroy it; whatever was inside is lost.")
                }
            }
        }
    }
}

/* An open sack / box takes what the player picks up from the floor. */
on_global_item_pickup {
    val ground = player.getInteractingGroundItem()
    Sacks.afterPickup(player, ground.item, ground.amount)
}

/* Wilderness sword 3 and 4: Teleport to the Fountain of Rune (sword 4 unlimited, sword 3 once a day). */
for (sword in intArrayOf(DmmItems.WILDERNESS_SWORD_3, DmmItems.WILDERNESS_SWORD_4)) {
    on_item_option(sword, "teleport") { DmmItems.wildernessSwordTeleport(player, sword) }
}

/* p48-gptb-rest. Reminisce and Old Memorial recharge await the owner's Kourend decision. */
on_item_option(BookOfTheDead.ITEM, "check") { BookOfTheDead.check(player) }
on_item_option(BookOfTheDead.ITEM, "read") { RoseMemoirs.book.open(player) }
on_item_option(BookOfTheDead.ITEM, "destroy") {
    val slot = player.getInteractingItemSlot()
    val expected = player.inventory[slot] ?: return@on_item_option
    player.queue(TaskPriority.WEAK) {
        if (confirmItemAction(BookOfTheDead.ITEM, "Are you sure you want to destroy this item?",
                "You can obtain another from Commander Fullore at Kourend Castle.")) {
            BookOfTheDead.destroy(player, slot, expected)
        }
    }
}

on_equipment_option(BookOfTheDead.ITEM, "check") { BookOfTheDead.check(player) }
