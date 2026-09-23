package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.content.items.armor.BarrowsRepair
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * The furniture the house gained on 2026-09-21 (owner: "we want nexus teleport and every teleport available in the
 * house put it in spirit tree obelisk fairy ring ... barrows armour stand"). The rooms themselves, and which object
 * stands where, are [PlayerHouse]'s; this file is only the behaviour.
 *
 * The spirit tree in the garden needs nothing here: `mechanics/travel/spirit_tree.plugin.kts` already binds loc 1293
 * and, when no spirit-tree station is within 30 tiles - which is always true inside an instanced house - offers all
 * five stations. Binding it a second time would throw out of `PluginRepository.init` and take the boot down.
 *
 * Every teleport goes through `canTeleport`, so house travel obeys the same wilderness/Deadman gates as the matching
 * item or spell.
 */

// ------------------------------------------------------------------ the nexus

/*
 * The teleport nexus. Revision 667 has no Portal Nexus loc - it is a much later OSRS object - so the house uses the
 * cache's own Scrying pool (13639), the one POH loc that exists to send a player somewhere of their choosing. Its
 * "Direct-portal" and "Scry" both open the full directory ([PohTeleports]), which already holds every teleport in the
 * game (owner 2026-09-19: "all the teleports in the game must be in the POH").
 */
listOf("direct-portal", "scry").forEach { option ->
    on_obj_option(obj = PlayerHouse.NEXUS, option = option) {
        player.queue {
            with(PohTeleports) { openDirectory(player) }
        }
    }
}

// ------------------------------------------------------------------ the garden

// Fairy ring: the full code network (PohGarden.FAIRY_RINGS, sourced from the 2009scape fairy-ring table).
on_obj_option(obj = PlayerHouse.FAIRY_RING, option = "use") {
    player.queue {
        val choice = chooseDestination(this, PohGarden.FAIRY_RINGS, "Fairy ring") ?: return@queue
        player.canTeleport(TeleportType.FAIRY) {
            player.teleport(choice.tile, TeleportType.FAIRY)
        }
    }
}

/*
 * The small obelisk in the garden needs nothing here. `skills/summoning/summoning_obelisks.plugin.kts` already binds
 * EVERY loc in the cache that carries a "Renew-points" option - all 39 of them, 5787 among them - and renews the
 * player's Summoning points with the proper obelisk graphic, animation and sound. Binding it a second time threw
 * "Object is already bound to a plugin: 5787 [opt=1]" out of `PluginRepository.init` and stopped the boot
 * (2026-09-21 15:42). Renewing Summoning points is also what a house obelisk does in RuneScape, so the garden
 * obelisk is complete as it stands; the house's travel lives on the nexus, the spirit tree and the fairy ring.
 */

// ------------------------------------------------------------------ the chapel

// Armour repair stand: the Barrows repair dialogue, at the stand's Smithing discount.
on_obj_option(obj = PlayerHouse.ARMOUR_STAND, option = "repair") {
    player.queue {
        BarrowsRepair.repairAtStand(this)
    }
}

// Jewellery box: every jewellery teleport in the directory, and nothing else.
on_obj_option(obj = PlayerHouse.JEWELLERY_BOX, option = "teleport") {
    player.queue {
        with(PohTeleports) { openBranch(player, "Jewellery") }
    }
}

/** Paged chooser over a flat destination list, in the same four-per-page shape [PohTeleports.choose] uses. */
suspend fun chooseDestination(
    task: QueueTask,
    destinations: List<PohGarden.Destination>,
    title: String,
): PohGarden.Destination? {
    var page = 0
    while (true) {
        val slice = destinations.drop(page * 4).take(4)
        if (slice.isEmpty()) {
            return null
        }
        val more = destinations.size > (page + 1) * 4
        val labels = slice.map { it.label } + (if (more) "More..." else "Cancel")
        val pick = task.options(*labels.toTypedArray(), title = title)
        if (pick < 1) {
            return null
        }
        if (pick <= slice.size) {
            return slice[pick - 1]
        }
        if (!more) {
            return null
        }
        page++
    }
}

// ------------------------------------------------------------------ the costume room

/*
 * Every piece of costume-room furniture opens the same store/withdraw dialogue over its own container
 * ([PohStorage.Family]). The cape rack's cache option is "Search" and the rest carry "Open"; both are bound so the
 * menu entry a player sees is the one the loc actually advertises.
 */
PohStorage.Family.values().forEach { family ->
    listOf("open", "search").forEach { option ->
        if (if_obj_has_option(obj = family.furniture, option = option)) {
            on_obj_option(obj = family.furniture, option = option) {
                player.queue { costumeRoom(this, family) }
            }
        }
    }
}

suspend fun costumeRoom(
    task: QueueTask,
    family: PohStorage.Family,
) {
    val player = task.player
    val held = player.inventory.rawItems.filterNotNull().map { it.id }.filter { PohStorage.accepts(family, it) }.distinct()
    val stored = PohStorage.stored(player, family)
    when (
        task.options(
            "Store an item (${held.size} in your inventory).",
            "Take an item out (${stored.size} stored).",
            "Nothing.",
            title = family.displayName,
        )
    ) {
        1 -> {
            if (held.isEmpty()) {
                player.message("You have nothing here that belongs in the ${family.displayName.lowercase()}.")
                return
            }
            val pick = chooseItem(task, held, "Store which item?") ?: return
            if (player.inventory.remove(pick).hasSucceeded()) {
                PohStorage.put(player, family, pick)
                player.message("You put your ${itemName(player, pick)} in the ${family.displayName.lowercase()}.")
            }
        }
        2 -> {
            if (stored.isEmpty()) {
                player.message("The ${family.displayName.lowercase()} is empty.")
                return
            }
            val pick = chooseItem(task, stored, "Take out which item?") ?: return
            if (player.inventory.hasSpace && PohStorage.take(player, family, pick)) {
                player.inventory.add(pick)
                player.message("You take your ${itemName(player, pick)} out of the ${family.displayName.lowercase()}.")
            } else if (!player.inventory.hasSpace) {
                player.message("You don't have room for that.")
            }
        }
    }
}

fun itemName(
    p: Player,
    id: Int,
): String = p.world.definitions.get(ItemDef::class.java, id).name

/** Paged item chooser, four per page, in the same shape the teleport directory uses. */
suspend fun chooseItem(
    task: QueueTask,
    ids: List<Int>,
    title: String,
): Int? {
    var page = 0
    while (true) {
        val slice = ids.drop(page * 4).take(4)
        if (slice.isEmpty()) {
            return null
        }
        val more = ids.size > (page + 1) * 4
        val labels = slice.map { itemName(task.player, it) } + (if (more) "More..." else "Cancel")
        val pick = task.options(*labels.toTypedArray(), title = title)
        if (pick < 1) {
            return null
        }
        if (pick <= slice.size) {
            return slice[pick - 1]
        }
        if (!more) {
            return null
        }
        page++
    }
}

// ------------------------------------------------------------------ the study

// The telescope and the bookcase are flavour for now; the lectern's tablet-making is a separate job.
on_obj_option(obj = Objs.TELESCOPE_13658, option = "observe") {
    player.message("You peer through the telescope at the night sky above Gielinor.")
}

on_obj_option(obj = Objs.BOOKCASE_13599, option = "search") {
    player.message("You search the bookcase, but find nothing you have not already read.")
}
